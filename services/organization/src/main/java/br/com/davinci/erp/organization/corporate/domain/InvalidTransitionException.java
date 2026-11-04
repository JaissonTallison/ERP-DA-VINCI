package br.com.davinci.erp.organization.corporate.domain;

import br.com.davinci.erp.organization.shared.domain.ConflictException;

public final class InvalidTransitionException extends ConflictException {

    private static final long serialVersionUID = 1L;

    public InvalidTransitionException(String what, Enum<?> from) {
        super("invalid-transition", "Não é possível " + what + " a partir de " + from);
    }
}
