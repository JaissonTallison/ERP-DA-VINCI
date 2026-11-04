package br.com.davinci.erp.organization.shared.domain;

import java.util.Locale;
import java.util.Objects;
import java.util.regex.Pattern;

/** Código de negócio legível, único no escopo definido por quem o usa. Sempre maiúsculo. */
public record BusinessCode(String value) {

    private static final Pattern FORMAT = Pattern.compile("[A-Z0-9][A-Z0-9._-]{0,39}");

    public BusinessCode {
        Objects.requireNonNull(value, "value");
        if (!FORMAT.matcher(value).matches()) {
            throw new InvalidValueException(
                    "code", "Código inválido (1 a 40 caracteres: A-Z, 0-9, ponto, hífen, sublinhado)");
        }
    }

    public static BusinessCode of(String raw) {
        return new BusinessCode(raw.strip().toUpperCase(Locale.ROOT));
    }

    @Override
    public String toString() {
        return value;
    }
}
