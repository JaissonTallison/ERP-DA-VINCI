package br.com.davinci.erp.organization.shared.domain;

import java.util.Locale;
import java.util.Objects;
import java.util.Set;

/** País ISO 3166-1 alfa-2. */
public record CountryCode(String value) {

    private static final Set<String> ISO = Locale.getISOCountries(Locale.IsoCountryCode.PART1_ALPHA2);

    public CountryCode {
        Objects.requireNonNull(value, "value");
        if (!ISO.contains(value)) {
            throw new InvalidValueException("country", "País inválido (ISO 3166-1 alfa-2): " + value);
        }
    }

    @Override
    public String toString() {
        return value;
    }
}
