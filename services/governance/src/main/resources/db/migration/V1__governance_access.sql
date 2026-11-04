-- Governance: projeção do tenant, papéis, atribuições com escopo e vigência, segregação de funções.
-- Executado pelo papel dono. A aplicação usa ${appRole}, sujeita ao Row-Level Security (ADR-0004).

CREATE EXTENSION IF NOT EXISTS btree_gist;

CREATE FUNCTION current_tenant() RETURNS uuid
    LANGUAGE sql STABLE
    AS $$ SELECT current_setting('app.tenant_id')::uuid $$;

CREATE TABLE tenant (
    tenant_id  uuid        PRIMARY KEY,
    status     text        NOT NULL CHECK (status IN ('PROVISIONING', 'ACTIVE', 'SUSPENDED')),
    version    bigint      NOT NULL,
    updated_at timestamptz NOT NULL
);

CREATE TABLE role (
    id          uuid        NOT NULL,
    tenant_id   uuid        NOT NULL REFERENCES tenant (tenant_id),
    code        text        NOT NULL,
    name        text        NOT NULL,
    permissions jsonb       NOT NULL,
    status      text        NOT NULL CHECK (status IN ('ACTIVE', 'INACTIVE')),
    created_at  timestamptz NOT NULL,
    updated_at  timestamptz NOT NULL,
    version     bigint      NOT NULL,
    PRIMARY KEY (tenant_id, id),
    UNIQUE (id),
    UNIQUE (tenant_id, code)
);

CREATE TABLE role_assignment (
    id         uuid        NOT NULL,
    tenant_id  uuid        NOT NULL,
    subject    text        NOT NULL,
    role_id    uuid        NOT NULL,
    scope_type text        NOT NULL CHECK (scope_type IN ('TENANT', 'ECONOMIC_GROUP', 'LEGAL_ENTITY', 'ESTABLISHMENT', 'UNIT')),
    scope_id   uuid,
    valid_from date        NOT NULL,
    valid_to   date,
    granted_by text        NOT NULL,
    created_at timestamptz NOT NULL,
    PRIMARY KEY (tenant_id, id),
    UNIQUE (id),
    CHECK (valid_to IS NULL OR valid_to > valid_from),
    CHECK ((scope_type = 'TENANT') = (scope_id IS NULL)),
    -- ninguém concede acesso a si mesmo
    CHECK (granted_by <> subject),
    FOREIGN KEY (tenant_id, role_id) REFERENCES role (tenant_id, id),
    EXCLUDE USING gist (tenant_id WITH =, subject WITH =, role_id WITH =, scope_type WITH =,
                        COALESCE(scope_id, '00000000-0000-0000-0000-000000000000'::uuid) WITH =,
                        daterange(valid_from, valid_to) WITH &&)
);
CREATE INDEX role_assignment_subject_idx ON role_assignment (tenant_id, subject, valid_from);
CREATE INDEX role_assignment_role_idx ON role_assignment (tenant_id, role_id);

CREATE TABLE sod_rule (
    id           uuid        NOT NULL,
    tenant_id    uuid        NOT NULL REFERENCES tenant (tenant_id),
    code         text        NOT NULL,
    description  text        NOT NULL,
    permission_a text        NOT NULL,
    permission_b text        NOT NULL,
    active       boolean     NOT NULL,
    created_at   timestamptz NOT NULL,
    PRIMARY KEY (tenant_id, id),
    UNIQUE (id),
    UNIQUE (tenant_id, code),
    CHECK (permission_a <> permission_b)
);

CREATE TABLE idempotency_record (
    tenant_id       uuid        NOT NULL,
    operation       text        NOT NULL,
    idempotency_key text        NOT NULL,
    request_hash    text        NOT NULL,
    resource_id     uuid        NOT NULL,
    created_at      timestamptz NOT NULL,
    PRIMARY KEY (tenant_id, operation, idempotency_key)
);

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
CREATE INDEX audit_log_period_idx ON audit_log (tenant_id, occurred_at);

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

DO $$
DECLARE t text;
BEGIN
    FOREACH t IN ARRAY ARRAY['tenant', 'role', 'role_assignment', 'sod_rule', 'idempotency_record', 'audit_log']
    LOOP
        EXECUTE format('ALTER TABLE %I ENABLE ROW LEVEL SECURITY', t);
        EXECUTE format('ALTER TABLE %I FORCE ROW LEVEL SECURITY', t);
        EXECUTE format('CREATE POLICY tenant_isolation ON %I USING (tenant_id = current_tenant()) '
                       || 'WITH CHECK (tenant_id = current_tenant())', t);
    END LOOP;
END $$;

GRANT USAGE ON SCHEMA public TO ${appRole};
GRANT EXECUTE ON FUNCTION current_tenant() TO ${appRole};
GRANT SELECT, INSERT, UPDATE ON tenant, role, role_assignment, sod_rule, idempotency_record TO ${appRole};
GRANT DELETE ON idempotency_record TO ${appRole};
GRANT SELECT, INSERT ON audit_log TO ${appRole};
GRANT SELECT, INSERT, UPDATE, DELETE ON outbox_event TO ${appRole};
GRANT USAGE ON ALL SEQUENCES IN SCHEMA public TO ${appRole};
