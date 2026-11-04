package br.com.davinci.erp.organization.shared.tenancy;

import br.com.davinci.erp.organization.shared.domain.ConflictException;

/** Tenant suspenso ou ainda não ativo: a estrutura fica somente leitura. */
public final class TenantNotActiveException extends ConflictException {

    private static final long serialVersionUID = 1L;

    public TenantNotActiveException() {
        super("tenant-not-active", "O tenant não está ativo: a estrutura organizacional está somente leitura");
    }
}
