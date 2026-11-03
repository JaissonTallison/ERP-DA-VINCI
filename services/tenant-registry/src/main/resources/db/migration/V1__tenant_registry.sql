-- Tenant Registry: catálogo de células, tenants, outbox de eventos e registros de idempotência.
-- Dados de plataforma (Control Plane): não contém dados pessoais nem transacionais de tenants.

CREATE TABLE cell (
    id           text        PRIMARY KEY,
    region       text        NOT NULL,
    isolation    text        NOT NULL CHECK (isolation IN ('pooled', 'bridge', 'silo')),
    capacity     integer     NOT NULL CHECK (capacity BETWEEN 1 AND 100000),
    tenant_count integer     NOT NULL DEFAULT 0,
    status       text        NOT NULL CHECK (status IN ('OPEN', 'CLOSED')),
    created_at   timestamptz NOT NULL,
    CONSTRAINT cell_tenant_count_within_capacity CHECK (tenant_count BETWEEN 0 AND capacity),
    CONSTRAINT cell_silo_is_dedicated CHECK (isolation <> 'silo' OR capacity = 1)
);

CREATE INDEX cell_allocation_idx ON cell (region, isolation, status, tenant_count);

CREATE TABLE tenant (
    id            uuid        PRIMARY KEY,
    slug          text        NOT NULL,
    display_name  text        NOT NULL,
    region        text        NOT NULL,
    isolation     text        NOT NULL,
    cell_id       text        NOT NULL REFERENCES cell (id),
    status        text        NOT NULL CHECK (status IN ('PROVISIONING', 'ACTIVE', 'SUSPENDED')),
    status_reason text,
    created_at    timestamptz NOT NULL,
    updated_at    timestamptz NOT NULL,
    version       bigint      NOT NULL,
    CONSTRAINT tenant_slug_key UNIQUE (slug)
);

CREATE INDEX tenant_region_status_idx ON tenant (region, status, slug);
CREATE INDEX tenant_cell_idx ON tenant (cell_id);

-- Transactional Outbox (ADR-0008): gravado na mesma transação do estado e publicado pelo relay.
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

-- Idempotência de comandos de criação (cabeçalho Idempotency-Key).
CREATE TABLE idempotency_record (
    operation       text        NOT NULL,
    idempotency_key text        NOT NULL,
    request_hash    text        NOT NULL,
    resource_id     uuid        NOT NULL,
    created_at      timestamptz NOT NULL,
    PRIMARY KEY (operation, idempotency_key)
);
