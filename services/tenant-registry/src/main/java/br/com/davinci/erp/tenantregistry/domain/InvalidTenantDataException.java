package br.com.davinci.erp.tenantregistry.domain;

/** Dado de entrada que viola uma regra do domínio. */
public final class InvalidTenantDataException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    private final String field;

    public InvalidTenantDataException(String field, String message) {
        super(message);
        this.field = field;
    }

    public String field() {
        return field;
    }
}
