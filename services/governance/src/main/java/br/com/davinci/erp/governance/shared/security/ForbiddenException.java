package br.com.davinci.erp.governance.shared.security;

/** O usuário não tem a permissão no escopo pedido. */
public final class ForbiddenException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public ForbiddenException(Permission permission) {
        super("Sem permissão: "
                + permission.name().toLowerCase(java.util.Locale.ROOT).replace('_', '.'));
    }
}
