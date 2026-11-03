package br.com.davinci.erp.platform.kernel;

import java.util.Objects;
import java.util.regex.Pattern;

/**
 * Região de residência de dados (Regional Data Plane), por exemplo {@code br-1} ou {@code eu-1}.
 *
 * <p>Formato: duas letras minúsculas de jurisdição, hífen e número sequencial.
 */
public record RegionCode(String value) {

    private static final Pattern FORMAT = Pattern.compile("[a-z]{2}-[1-9][0-9]?");

    public RegionCode {
        Objects.requireNonNull(value, "value");
        if (!FORMAT.matcher(value).matches()) {
            throw new IllegalArgumentException("RegionCode inválido: " + value);
        }
    }

    @Override
    public String toString() {
        return value;
    }
}
