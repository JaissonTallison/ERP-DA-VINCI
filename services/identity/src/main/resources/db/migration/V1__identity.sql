-- Projeção local dos tenants (alimentada pelos eventos do Tenant Registry) e vínculo com a organização no Keycloak.
CREATE TABLE tenant_organization (
    tenant_id       uuid        PRIMARY KEY,
    organization_id text        NOT NULL,
    alias           text        NOT NULL,
    status          text        NOT NULL CHECK (status IN ('PROVISIONING', 'ACTIVE', 'SUSPENDED')),
    version         bigint      NOT NULL,
    updated_at      timestamptz NOT NULL,
    CONSTRAINT tenant_organization_alias_key UNIQUE (alias),
    CONSTRAINT tenant_organization_org_key UNIQUE (organization_id)
);
