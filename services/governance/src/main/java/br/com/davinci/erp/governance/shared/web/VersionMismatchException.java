package br.com.davinci.erp.governance.shared.web;

/** O recurso mudou desde a leitura do cliente ({@code If-Match} desatualizado). Vira 412. */
public final class VersionMismatchException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public VersionMismatchException(long expected, long actual) {
        super("Versão desatualizada: esperada " + expected + ", atual " + actual
                + ". Releia o recurso e tente de novo.");
    }
}
