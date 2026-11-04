package br.com.davinci.erp.organization.shared.security;

/** Catálogo de permissões do Organization (docs/domains/organization/ORGANIZATION_SECURITY.md). */
public enum Permission {
    ECONOMIC_GROUP_READ(true),
    ECONOMIC_GROUP_MANAGE(false),
    LEGAL_ENTITY_READ(true),
    LEGAL_ENTITY_MANAGE(false),
    LEGAL_ENTITY_LIFECYCLE(false),
    ESTABLISHMENT_READ(true),
    ESTABLISHMENT_MANAGE(false),
    UNIT_READ(true),
    UNIT_MANAGE(false),
    STRUCTURE_MOVE(false),
    REORGANIZATION_READ(true),
    REORGANIZATION_MANAGE(false),
    REORGANIZATION_APPROVE(false),
    COST_CENTER_READ(true),
    COST_CENTER_MANAGE(false),
    OWNERSHIP_READ(true),
    OWNERSHIP_MANAGE(false),
    HISTORY_READ(false);

    private final boolean readOnly;

    Permission(boolean readOnly) {
        this.readOnly = readOnly;
    }

    public boolean readOnly() {
        return readOnly;
    }
}
