package br.com.davinci.erp.organization.shared.domain;

import java.time.ZoneId;
import java.util.Currency;
import java.util.IllformedLocaleException;
import java.util.Locale;

/** Validação de moeda (ISO 4217), fuso (IANA) e idioma (BCP 47). */
public final class RegionalSettings {

    private RegionalSettings() {}

    public static Currency currency(String field, String code) {
        try {
            if (!code.equals(code.toUpperCase(Locale.ROOT))) {
                throw new IllegalArgumentException(code);
            }
            return Currency.getInstance(code);
        } catch (IllegalArgumentException e) {
            throw new InvalidValueException(field, "Moeda inválida (ISO 4217): " + code);
        }
    }

    /** Só fusos com nome de região (ex.: America/Sao_Paulo); deslocamentos fixos ignoram horário de verão. */
    public static ZoneId timezone(String field, String id) {
        boolean fixedOffset =
                id.startsWith("Etc/") || id.startsWith("GMT") || (id.startsWith("UTC") && !"UTC".equals(id));
        if (fixedOffset || !ZoneId.getAvailableZoneIds().contains(id)) {
            throw new InvalidValueException(field, "Fuso inválido (use um nome IANA, ex.: America/Sao_Paulo): " + id);
        }
        return ZoneId.of(id);
    }

    public static Locale locale(String field, String tag) {
        try {
            Locale locale = new Locale.Builder().setLanguageTag(tag).build();
            if (locale.getLanguage().isEmpty()) {
                throw new IllformedLocaleException(tag);
            }
            return locale;
        } catch (IllformedLocaleException e) {
            throw new InvalidValueException(field, "Idioma inválido (BCP 47, ex.: pt-BR): " + tag);
        }
    }
}
