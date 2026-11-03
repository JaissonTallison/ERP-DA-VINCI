package br.com.davinci.erp.tenantregistry.api;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

/** Cursor opaco de paginação: o último slug da página, em Base64 URL-safe. */
final class Cursor {

    private Cursor() {}

    static String encode(String slug) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(slug.getBytes(StandardCharsets.UTF_8));
    }

    static String decode(String cursor) {
        try {
            return new String(Base64.getUrlDecoder().decode(cursor), StandardCharsets.UTF_8);
        } catch (IllegalArgumentException e) {
            throw new InvalidCursorException();
        }
    }

    static final class InvalidCursorException extends RuntimeException {

        private static final long serialVersionUID = 1L;

        InvalidCursorException() {
            super("Cursor de paginação inválido");
        }
    }
}
