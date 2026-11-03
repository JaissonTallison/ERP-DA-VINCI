package br.com.davinci.erp.tenantregistry.application;

import br.com.davinci.erp.platform.kernel.TenantId;

public final class TenantNotFoundException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public TenantNotFoundException(TenantId id) {
        super("Tenant não encontrado: " + id);
    }
}
