package br.com.davinci.erp.organization.shared.domain;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Pattern;
import org.jspecify.annotations.Nullable;

/**
 * Endereço internacional (campos no estilo UPU/libaddressinput). Formato de código postal validado para os países já
 * atendidos; os demais aceitam até 20 caracteres.
 */
public record PostalAddress(
        CountryCode country,
        List<String> addressLines,
        String locality,
        @Nullable String administrativeArea,
        @Nullable String postalCode) {

    private static final Map<String, Pattern> POSTAL_CODES = Map.of(
            "BR", Pattern.compile("\\d{5}-\\d{3}"),
            "US", Pattern.compile("\\d{5}(-\\d{4})?"),
            "PT", Pattern.compile("\\d{4}-\\d{3}"));

    public PostalAddress {
        Objects.requireNonNull(country, "country");
        addressLines = List.copyOf(Objects.requireNonNull(addressLines, "addressLines"));
        if (addressLines.isEmpty() || addressLines.size() > 4) {
            throw new InvalidValueException("registeredAddress.addressLines", "Informe de 1 a 4 linhas de endereço");
        }
        for (String line : addressLines) {
            if (line.isBlank() || line.length() > 200) {
                throw new InvalidValueException(
                        "registeredAddress.addressLines", "Linha de endereço vazia ou longa demais");
            }
        }
        if (locality.isBlank() || locality.length() > 120) {
            throw new InvalidValueException("registeredAddress.locality", "Cidade obrigatória (até 120 caracteres)");
        }
        postalCode = normalizePostalCode(country, postalCode);
    }

    private static @Nullable String normalizePostalCode(CountryCode country, @Nullable String raw) {
        if (raw == null || raw.isBlank()) {
            if (POSTAL_CODES.containsKey(country.value())) {
                throw new InvalidValueException(
                        "registeredAddress.postalCode", "Código postal obrigatório para " + country);
            }
            return null;
        }
        String value = raw.strip();
        if ("BR".equals(country.value()) && value.matches("\\d{8}")) {
            value = value.substring(0, 5) + "-" + value.substring(5);
        }
        Pattern format = POSTAL_CODES.get(country.value());
        if ((format != null && !format.matcher(value).matches()) || value.length() > 20) {
            throw new InvalidValueException("registeredAddress.postalCode", "Código postal inválido para " + country);
        }
        return value;
    }
}
