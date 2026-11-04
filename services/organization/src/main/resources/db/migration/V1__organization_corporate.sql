-- Organization Management, etapa 2: projeção do tenant, grupos econômicos e entidades jurídicas.
-- Modelo: docs/domains/organization/ORGANIZATION_DATA_MODEL.md (ADR-0017, 0019, 0020, 0022).
--
-- Executado pelo papel dono (migração). A aplicação usa ${appRole}, sem privilégio de dono:
-- o Row-Level Security vale para ela, e o FORCE vale até para o dono.

CREATE EXTENSION IF NOT EXISTS btree_gist;

-- Tenant da transação. Sem SET LOCAL app.tenant_id, a função falha: consultas sem tenant não retornam nada.
CREATE FUNCTION current_tenant() RETURNS uuid
    LANGUAGE sql STABLE
    AS $$ SELECT current_setting('app.tenant_id')::uuid $$;

-- Projeção do tenant (eventos do Tenant Registry)
CREATE TABLE tenant (
    tenant_id  uuid        PRIMARY KEY,
    status     text        NOT NULL CHECK (status IN ('PROVISIONING', 'ACTIVE', 'SUSPENDED')),
    version    bigint      NOT NULL,
    updated_at timestamptz NOT NULL
);

CREATE TABLE economic_group (
    id                 uuid        NOT NULL,
    tenant_id          uuid        NOT NULL REFERENCES tenant,
    code               text        NOT NULL,
    name               text        NOT NULL,
    reference_currency char(3)     NOT NULL,
    reference_timezone text        NOT NULL,
    status             text        NOT NULL CHECK (status IN ('ACTIVE', 'CLOSED')),
    closed_on          date,
    created_at         timestamptz NOT NULL,
    updated_at         timestamptz NOT NULL,
    version            bigint      NOT NULL,
    PRIMARY KEY (tenant_id, id),
    CONSTRAINT economic_group_code_key UNIQUE (tenant_id, code)
);

CREATE TABLE legal_entity (
    id                      uuid        NOT NULL,
    tenant_id               uuid        NOT NULL REFERENCES tenant,
    code                    text        NOT NULL,
    legal_name              text        NOT NULL,
    trade_name              text,
    country                 char(2)     NOT NULL,
    subdivision             text,
    legal_form              text,
    functional_currency     char(3)     NOT NULL,
    timezone                text        NOT NULL,
    default_locale          text        NOT NULL,
    fiscal_year_start_month smallint    NOT NULL CHECK (fiscal_year_start_month BETWEEN 1 AND 12),
    registered_address      jsonb       NOT NULL,
    incorporated_on         date,
    closed_on               date,
    status                  text        NOT NULL CHECK (status IN ('PLANNED', 'ACTIVE', 'SUSPENDED', 'CLOSED')),
    created_at              timestamptz NOT NULL,
    updated_at              timestamptz NOT NULL,
    version                 bigint      NOT NULL,
    PRIMARY KEY (tenant_id, id),
    CONSTRAINT legal_entity_code_key UNIQUE (tenant_id, code)
);
CREATE INDEX legal_entity_country_idx ON legal_entity (tenant_id, country, code);

-- Pertença ao grupo econômico, com vigência [valid_from, valid_to): no máximo um grupo por data.
CREATE TABLE legal_entity_group_assignment (
    tenant_id         uuid NOT NULL,
    legal_entity_id   uuid NOT NULL,
    economic_group_id uuid NOT NULL,
    valid_from        date NOT NULL,
    valid_to          date,
    CHECK (valid_to IS NULL OR valid_to > valid_from),
    FOREIGN KEY (tenant_id, legal_entity_id)   REFERENCES legal_entity (tenant_id, id),
    FOREIGN KEY (tenant_id, economic_group_id) REFERENCES economic_group (tenant_id, id),
    CONSTRAINT legal_entity_group_assignment_no_overlap
        EXCLUDE USING gist (tenant_id WITH =, legal_entity_id WITH =, daterange(valid_from, valid_to) WITH &&)
);
CREATE INDEX legal_entity_group_assignment_group_idx
    ON legal_entity_group_assignment (tenant_id, economic_group_id, valid_from);

-- Identificadores fiscais da entidade (ADR-0022). Estabelecimentos entram na etapa 3.
CREATE TABLE tax_identifier (
    id              uuid    NOT NULL,
    tenant_id       uuid    NOT NULL,
    legal_entity_id uuid    NOT NULL,
    scheme          text    NOT NULL,
    value           text    NOT NULL,
    is_primary      boolean NOT NULL,
    valid_from      date    NOT NULL,
    valid_to        date,
    PRIMARY KEY (tenant_id, id),
    CHECK (valid_to IS NULL OR valid_to > valid_from),
    FOREIGN KEY (tenant_id, legal_entity_id) REFERENCES legal_entity (tenant_id, id),
    -- o mesmo identificador não fica vigente duas vezes no tenant
    CONSTRAINT tax_identifier_unique_value
        EXCLUDE USING gist (tenant_id WITH =, scheme WITH =, value WITH =, daterange(valid_from, valid_to) WITH &&),
    -- no máximo um principal por entidade em cada data
    CONSTRAINT tax_identifier_one_primary
        EXCLUDE USING gist (tenant_id WITH =, legal_entity_id WITH =, daterange(valid_from, valid_to) WITH &&)
        WHERE (is_primary)
);
CREATE INDEX tax_identifier_entity_idx ON tax_identifier (tenant_id, legal_entity_id);

CREATE TABLE idempotency_record (
    tenant_id       uuid        NOT NULL,
    operation       text        NOT NULL,
    idempotency_key text        NOT NULL,
    request_hash    text        NOT NULL,
    resource_id     uuid        NOT NULL,
    created_at      timestamptz NOT NULL,
    PRIMARY KEY (tenant_id, operation, idempotency_key)
);

-- Trilha de auditoria append-only: a aplicação só insere e lê.
CREATE TABLE audit_log (
    id             bigint      GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    tenant_id      uuid        NOT NULL,
    aggregate_type text        NOT NULL,
    aggregate_id   uuid        NOT NULL,
    action         text        NOT NULL,
    actor          text        NOT NULL,
    correlation_id text        NOT NULL,
    occurred_at    timestamptz NOT NULL,
    before         jsonb,
    after          jsonb
);
CREATE INDEX audit_log_aggregate_idx ON audit_log (tenant_id, aggregate_type, aggregate_id, occurred_at);

-- Outbox (ADR-0008). Sem RLS: lido pelo relay, para todos os tenants, e nunca por requisições de usuário.
CREATE TABLE outbox_event (
    sequence     bigint      GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    event_id     uuid        NOT NULL UNIQUE,
    topic        text        NOT NULL,
    message_key  text        NOT NULL,
    payload      jsonb       NOT NULL,
    created_at   timestamptz NOT NULL,
    published_at timestamptz
);
CREATE INDEX outbox_event_pending_idx ON outbox_event (sequence) WHERE published_at IS NULL;

-- Isolamento por tenant em todas as tabelas de negócio.
DO $$
DECLARE t text;
BEGIN
    FOREACH t IN ARRAY ARRAY['tenant', 'economic_group', 'legal_entity', 'legal_entity_group_assignment',
                             'tax_identifier', 'idempotency_record', 'audit_log']
    LOOP
        EXECUTE format('ALTER TABLE %I ENABLE ROW LEVEL SECURITY', t);
        EXECUTE format('ALTER TABLE %I FORCE ROW LEVEL SECURITY', t);
        EXECUTE format('CREATE POLICY tenant_isolation ON %I USING (tenant_id = current_tenant()) '
                       || 'WITH CHECK (tenant_id = current_tenant())', t);
    END LOOP;
END $$;

-- Permissões do papel da aplicação (sem DDL, sem dono).
GRANT USAGE ON SCHEMA public TO ${appRole};
GRANT EXECUTE ON FUNCTION current_tenant() TO ${appRole};
GRANT SELECT, INSERT, UPDATE ON tenant, economic_group, legal_entity, legal_entity_group_assignment,
    tax_identifier, idempotency_record TO ${appRole};
GRANT DELETE ON idempotency_record TO ${appRole};
-- Só para corrigir uma mudança de grupo no mesmo dia em que começava; encerramentos usam UPDATE de valid_to.
GRANT DELETE ON legal_entity_group_assignment TO ${appRole};
GRANT SELECT, INSERT ON audit_log TO ${appRole};
GRANT SELECT, INSERT, UPDATE, DELETE ON outbox_event TO ${appRole};
GRANT USAGE ON ALL SEQUENCES IN SCHEMA public TO ${appRole};
