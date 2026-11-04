package br.com.davinci.erp.organization.corporate.domain;

import br.com.davinci.erp.organization.shared.domain.InvalidValueException;
import org.jspecify.annotations.Nullable;

final class Texts {

    private Texts() {}

    static String required(String field, @Nullable String value, int max) {
        String trimmed = value == null ? "" : value.strip();
        if (trimmed.isEmpty() || trimmed.length() > max) {
            throw new InvalidValueException(field, field + " deve ter de 1 a " + max + " caracteres");
        }
        return trimmed;
    }

    static @Nullable String optional(String field, @Nullable String value, int max) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return required(field, value, max);
    }
}
