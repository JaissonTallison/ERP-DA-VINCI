package br.com.davinci.erp.organization.shared.domain;

/** Valor que viola uma regra de formato ou de domínio. Vira 400 com o campo afetado. */
public class InvalidValueException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    private final String field;
    private final String code;

    public InvalidValueException(String field, String message) {
        this(field, "invalid-field", message);
    }

    public InvalidValueException(String field, String code, String message) {
        super(message);
        this.field = field;
        this.code = code;
    }

    public String field() {
        return field;
    }

    public String code() {
        return code;
    }
}
