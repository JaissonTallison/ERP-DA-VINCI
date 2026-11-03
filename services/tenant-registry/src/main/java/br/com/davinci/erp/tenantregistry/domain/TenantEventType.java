package br.com.davinci.erp.tenantregistry.domain;

/** Fatos do ciclo de vida publicados como eventos (contracts/events/tenant-registry/v1). */
public enum TenantEventType {
    REGISTERED("registered"),
    ACTIVATED("activated"),
    SUSPENDED("suspended"),
    REACTIVATED("reactivated");

    private final String code;

    TenantEventType(String code) {
        this.code = code;
    }

    public String code() {
        return code;
    }
}
