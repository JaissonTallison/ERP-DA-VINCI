package br.com.davinci.erp.tenantregistry.domain;

/** Estados do ciclo de vida de um tenant. Encerramento (DECOMMISSIONED) entra com a saga de desprovisionamento. */
public enum TenantStatus {
    PROVISIONING,
    ACTIVE,
    SUSPENDED
}
