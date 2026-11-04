package br.com.davinci.erp.organization.shared.domain;

import java.util.Objects;

/** Identificador fiscal normalizado e validado pelo seu esquema. */
public record TaxIdentifier(TaxScheme scheme, String value) {

    public TaxIdentifier {
        Objects.requireNonNull(scheme, "scheme");
        Objects.requireNonNull(value, "value");
        if (!scheme.accepts(value)) {
            throw new InvalidValueException(
                    "taxIdentifiers.value", "invalid-tax-identifier", "Identificador fiscal inválido para " + scheme);
        }
    }

    public static TaxIdentifier of(String scheme, String raw) {
        TaxScheme taxScheme = TaxScheme.of(scheme);
        return new TaxIdentifier(taxScheme, taxScheme.normalize(raw));
    }
}
