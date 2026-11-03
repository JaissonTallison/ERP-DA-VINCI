package br.com.davinci.erp.tenantregistry.application;

/** Conflito com o estado atual do registro. {@code code} é estável e vai para o campo {@code code} do Problem. */
public final class ConflictException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    private final String code;

    public ConflictException(String code, String message) {
        super(message);
        this.code = code;
    }

    public String code() {
        return code;
    }

    static ConflictException slugTaken(String slug) {
        return new ConflictException("slug-taken", "Já existe um tenant com o slug " + slug);
    }

    static ConflictException noCellCapacity(String region, String isolation) {
        return new ConflictException(
                "no-cell-capacity", "Nenhuma célula " + isolation + " aberta com vaga na região " + region);
    }

    static ConflictException cellExists(String cellId) {
        return new ConflictException("cell-exists", "Célula já registrada: " + cellId);
    }

    static ConflictException idempotencyKeyReused() {
        return new ConflictException(
                "idempotency-key-reused", "Idempotency-Key já usada em uma requisição com conteúdo diferente");
    }

    public static ConflictException concurrentRequest() {
        return new ConflictException(
                "concurrent-request", "Requisição concorrente com a mesma Idempotency-Key ou o mesmo slug; repita");
    }
}
