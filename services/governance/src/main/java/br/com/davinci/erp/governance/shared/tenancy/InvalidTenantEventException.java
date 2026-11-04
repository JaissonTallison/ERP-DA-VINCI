package br.com.davinci.erp.governance.shared.tenancy;

import org.jspecify.annotations.Nullable;

/** Mensagem fora do contrato: vai direto para a dead letter, sem novas tentativas. */
public final class InvalidTenantEventException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public InvalidTenantEventException(String message, @Nullable Throwable cause) {
        super(message, cause);
    }
}
