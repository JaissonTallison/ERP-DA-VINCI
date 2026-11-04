package br.com.davinci.erp.organization.corporate.domain;

import br.com.davinci.erp.organization.shared.domain.ConflictException;
import br.com.davinci.erp.organization.shared.domain.CountryCode;
import br.com.davinci.erp.organization.shared.domain.TaxScheme;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/** Identificador fiscal principal exigido para uma entidade ficar ativa, por país (ADR-0022). */
public final class TaxRequirements {

    private static final Map<String, TaxScheme> REQUIRED = Map.of("BR", TaxScheme.BR_CNPJ);

    private TaxRequirements() {}

    public static void requireForActivation(CountryCode country, List<TaxIdentifierRecord> identifiers, LocalDate on) {
        TaxScheme required = REQUIRED.get(country.value());
        if (required == null) {
            return;
        }
        boolean present = identifiers.stream()
                .anyMatch(t -> t.primary()
                        && t.identifier().scheme() == required
                        && t.period().contains(on));
        if (!present) {
            throw new ConflictException(
                    "missing-tax-identifier",
                    "Entidade de " + country + " precisa de " + required + " principal vigente em " + on
                            + " para ficar ativa");
        }
    }
}
