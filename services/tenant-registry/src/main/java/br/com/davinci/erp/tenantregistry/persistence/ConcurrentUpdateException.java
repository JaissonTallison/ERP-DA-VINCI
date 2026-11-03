package br.com.davinci.erp.tenantregistry.persistence;

import br.com.davinci.erp.platform.kernel.TenantId;

/** Outra transação alterou o tenant entre a leitura e a gravação. O cliente pode repetir a operação. */
public final class ConcurrentUpdateException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public ConcurrentUpdateException(TenantId id) {
        super("Tenant " + id + " foi alterado por outra operação; repita a requisição");
    }
}
