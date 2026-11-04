package br.com.davinci.erp.organization.shared.domain;

import java.util.Locale;
import java.util.function.Predicate;
import org.jspecify.annotations.Nullable;

/**
 * Catálogo de esquemas de identificador fiscal (ADR-0022). Cada esquema normaliza e valida o valor; esquemas sem regra
 * completa validam só o formato. Um país novo = uma entrada aqui, com testes de referência.
 */
public enum TaxScheme {
    /** CNPJ (Brasil), com dígitos verificadores, incluindo o formato alfanumérico. */
    BR_CNPJ("BR", CnpjValidator::isValid),
    /** EIN (Estados Unidos): só formato. */
    US_EIN("US", value -> value.matches("\\d{9}")),
    /** Número de IVA intracomunitário (UE): prefixo do país + 2 a 12 caracteres; só formato. */
    EU_VAT(null, value -> value.matches("[A-Z]{2}[A-Z0-9]{2,12}"));

    private final @Nullable String country;
    private final Predicate<String> rule;

    TaxScheme(@Nullable String country, Predicate<String> rule) {
        this.country = country;
        this.rule = rule;
    }

    /** País do esquema; nulo para esquemas supranacionais. */
    public @Nullable String country() {
        return country;
    }

    public static TaxScheme of(String code) {
        try {
            return valueOf(code);
        } catch (IllegalArgumentException e) {
            throw new InvalidValueException(
                    "taxIdentifiers.scheme", "invalid-tax-identifier", "Esquema fiscal desconhecido: " + code);
        }
    }

    /** Remove pontuação e espaços e passa para maiúsculas. */
    public String normalize(String raw) {
        return raw.replaceAll("[\\s./-]", "").toUpperCase(Locale.ROOT);
    }

    public boolean accepts(String normalized) {
        return rule.test(normalized);
    }
}
