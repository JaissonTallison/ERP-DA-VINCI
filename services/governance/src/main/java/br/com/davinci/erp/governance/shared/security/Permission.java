package br.com.davinci.erp.governance.shared.security;

/** Permissões do próprio Governance. Quem administra acessos segue o papel do token, para não depender de si mesmo. */
public enum Permission {
    ACCESS_READ(true),
    ACCESS_MANAGE(false),
    HISTORY_READ(false);

    private final boolean readOnly;

    Permission(boolean readOnly) {
        this.readOnly = readOnly;
    }

    public boolean readOnly() {
        return readOnly;
    }
}
