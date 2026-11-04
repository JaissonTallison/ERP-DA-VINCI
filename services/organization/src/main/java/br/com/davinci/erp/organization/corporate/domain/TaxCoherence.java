package br.com.davinci.erp.organization.corporate.domain;

import br.com.davinci.erp.organization.shared.domain.InvalidValueException;
import br.com.davinci.erp.organization.shared.domain.TaxIdentifier;
import br.com.davinci.erp.organization.shared.domain.TaxScheme;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/**
 * Coerência entre o identificador fiscal do estabelecimento e o da entidade, por esquema (ADR-0022). Brasil: o CNPJ
 * da filial tem a mesma raiz (8 primeiros caracteres) do CNPJ da entidade.
 */
public final class TaxCoherence {

    private TaxCoherence() {}

    public static void requireCoherent(
            TaxIdentifier establishmentId,
            LegalEntity entity,
            List<TaxIdentifierRecord> entityIdentifiers,
            LocalDate on) {
        if (establishmentId.scheme() != TaxScheme.BR_CNPJ) {
            requireSchemeOfCountry(establishmentId, entity);
            return;
        }
        Optional<TaxIdentifierRecord> entityCnpj = entityIdentifiers.stream()
                .filter(t -> t.primary()
                        && t.identifier().scheme() == TaxScheme.BR_CNPJ
                        && t.period().contains(on))
                .findFirst();
        if (entityCnpj.isEmpty()) {
            throw new InvalidValueException(
                    "taxIdentifiers", "tax-identifier-mismatch", "A entidade não tem CNPJ principal vigente em " + on);
        }
        String root = entityCnpj.get().identifier().value().substring(0, 8);
        if (!establishmentId.value().startsWith(root)) {
            throw new InvalidValueException(
                    "taxIdentifiers",
                    "tax-identifier-mismatch",
                    "O CNPJ do estabelecimento deve ter a raiz " + root + " da entidade");
        }
    }

    private static void requireSchemeOfCountry(TaxIdentifier id, LegalEntity entity) {
        String country = id.scheme().country();
        if (country != null && !country.equals(entity.country().value())) {
            throw new InvalidValueException(
                    "taxIdentifiers", "invalid-tax-identifier", id.scheme() + " não se aplica a " + entity.country());
        }
    }
}
