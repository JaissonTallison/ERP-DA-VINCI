package br.com.davinci.erp.tenantregistry.domain;

/** Transição de ciclo de vida não permitida a partir do estado atual. */
public final class InvalidTransitionException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public InvalidTransitionException(TenantStatus from, String action) {
        super("Não é possível " + action + " um tenant em " + from);
    }
}
