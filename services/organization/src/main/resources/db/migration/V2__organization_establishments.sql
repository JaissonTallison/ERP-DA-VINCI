-- Etapa 3: estabelecimentos (matriz, filiais, fábricas, lojas, depósitos) de uma entidade jurídica.
-- Identificadores fiscais em tabela própria: a matriz usa o mesmo CNPJ da entidade, e a unicidade vale entre
-- estabelecimentos (ADR-0022).

CREATE TABLE establishment (
    id              uuid        NOT NULL,
    tenant_id       uuid        NOT NULL,
    legal_entity_id uuid        NOT NULL,
    code            text        NOT NULL,
    name            text        NOT NULL,
    type            text        NOT NULL CHECK (type IN ('HEADQUARTERS', 'BRANCH', 'PLANT', 'STORE', 'WAREHOUSE', 'OFFICE', 'OTHER')),
    address         jsonb       NOT NULL,
    timezone        text        NOT NULL,
    status          text        NOT NULL CHECK (status IN ('ACTIVE', 'SUSPENDED', 'CLOSED')),
    opened_on       date        NOT NULL,
    closed_on       date,
    created_at      timestamptz NOT NULL,
    updated_at      timestamptz NOT NULL,
    version         bigint      NOT NULL,
    PRIMARY KEY (tenant_id, id),
    CONSTRAINT establishment_code_key UNIQUE (tenant_id, legal_entity_id, code),
    CHECK (closed_on IS NULL OR closed_on >= opened_on),
    FOREIGN KEY (tenant_id, legal_entity_id) REFERENCES legal_entity (tenant_id, id)
);
-- no máximo uma matriz não encerrada por entidade
CREATE UNIQUE INDEX establishment_one_headquarters
    ON establishment (tenant_id, legal_entity_id) WHERE type = 'HEADQUARTERS' AND status <> 'CLOSED';
CREATE INDEX establishment_entity_idx ON establishment (tenant_id, legal_entity_id, status);

CREATE TABLE establishment_tax_identifier (
    id               uuid    NOT NULL,
    tenant_id        uuid    NOT NULL,
    establishment_id uuid    NOT NULL,
    scheme           text    NOT NULL,
    value            text    NOT NULL,
    is_primary       boolean NOT NULL,
    valid_from       date    NOT NULL,
    valid_to         date,
    PRIMARY KEY (tenant_id, id),
    CHECK (valid_to IS NULL OR valid_to > valid_from),
    FOREIGN KEY (tenant_id, establishment_id) REFERENCES establishment (tenant_id, id),
    CONSTRAINT establishment_tax_identifier_unique_value
        EXCLUDE USING gist (tenant_id WITH =, scheme WITH =, value WITH =, daterange(valid_from, valid_to) WITH &&),
    CONSTRAINT establishment_tax_identifier_one_primary
        EXCLUDE USING gist (tenant_id WITH =, establishment_id WITH =, daterange(valid_from, valid_to) WITH &&)
        WHERE (is_primary)
);
CREATE INDEX establishment_tax_identifier_owner_idx ON establishment_tax_identifier (tenant_id, establishment_id);

DO $$
DECLARE t text;
BEGIN
    FOREACH t IN ARRAY ARRAY['establishment', 'establishment_tax_identifier']
    LOOP
        EXECUTE format('ALTER TABLE %I ENABLE ROW LEVEL SECURITY', t);
        EXECUTE format('ALTER TABLE %I FORCE ROW LEVEL SECURITY', t);
        EXECUTE format('CREATE POLICY tenant_isolation ON %I USING (tenant_id = current_tenant()) '
                       || 'WITH CHECK (tenant_id = current_tenant())', t);
    END LOOP;
END $$;

GRANT SELECT, INSERT, UPDATE ON establishment, establishment_tax_identifier TO ${appRole};
