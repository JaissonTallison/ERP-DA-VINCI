package br.com.davinci.erp.organization.shared.web;

import br.com.davinci.erp.organization.shared.domain.InvalidValueException;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

/** Cursor opaco de paginação: o último código da página, em Base64 URL-safe. */
public final class Cursor {

    private Cursor() {}

    public static String encode(String code) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(code.getBytes(StandardCharsets.UTF_8));
    }

    public static String decode(String cursor) {
        try {
            return new String(Base64.getUrlDecoder().decode(cursor), StandardCharsets.UTF_8);
        } catch (IllegalArgumentException e) {
            throw new InvalidValueException("cursor", "invalid-cursor", "Cursor de paginação inválido");
        }
    }
}
