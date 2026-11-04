package br.com.davinci.erp.organization.shared.security;

/** Catálogo de permissões do Organization (docs/domains/organization/ORGANIZATION_SECURITY.md). */
public enum Permission {
    ECONOMIC_GROUP_READ(true),
    ECONOMIC_GROUP_MANAGE(false),
    LEGAL_ENTITY_READ(true),
    LEGAL_ENTITY_MANAGE(false),
    LEGAL_ENTITY_LIFECYCLE(false);

    private final boolean readOnly;

    Permission(boolean readOnly) {
        this.readOnly = readOnly;
    }

    public boolean readOnly() {
        return readOnly;
    }
}
