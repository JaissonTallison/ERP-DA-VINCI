package br.com.davinci.erp.organization.shared.web;

import br.com.davinci.erp.organization.shared.domain.InvalidValueException;

/** ETag forte com a versão do agregado ({@code "3"}) e leitura do {@code If-Match}. */
public final class Versions {

    private Versions() {}

    public static String etag(long version) {
        return "\"" + version + "\"";
    }

    public static long fromIfMatch(String ifMatch) {
        String value = ifMatch.strip();
        if (value.startsWith("W/")) {
            value = value.substring(2);
        }
        value = value.replace("\"", "");
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException e) {
            throw new InvalidValueException("If-Match", "If-Match deve conter a versão devolvida no ETag");
        }
    }
}
