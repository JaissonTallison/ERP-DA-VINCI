package br.com.davinci.erp.organization.shared.domain;

/** Operação incompatível com o estado atual. {@code code} é estável (campo {@code code} do Problem). */
public class ConflictException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    private final String code;

    public ConflictException(String code, String message) {
        super(message);
        this.code = code;
    }

    public String code() {
        return code;
    }
}
