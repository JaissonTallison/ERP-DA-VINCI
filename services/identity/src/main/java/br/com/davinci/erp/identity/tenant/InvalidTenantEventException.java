package br.com.davinci.erp.identity.tenant;

/** Mensagem que não segue o contrato. Não adianta repetir: vai direto para a dead letter. */
public final class InvalidTenantEventException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public InvalidTenantEventException(String message, Throwable cause) {
        super(message, cause);
    }

    public InvalidTenantEventException(String message) {
        super(message);
    }
}
