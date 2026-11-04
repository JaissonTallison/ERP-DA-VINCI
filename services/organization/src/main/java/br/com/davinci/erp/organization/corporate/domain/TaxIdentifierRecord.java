package br.com.davinci.erp.organization.corporate.domain;

import br.com.davinci.erp.organization.shared.domain.EffectivePeriod;
import br.com.davinci.erp.organization.shared.domain.TaxIdentifier;
import java.util.Objects;
import java.util.UUID;

/** Identificador fiscal registrado para uma entidade, com vigência. */
public record TaxIdentifierRecord(UUID id, TaxIdentifier identifier, boolean primary, EffectivePeriod period) {

    public TaxIdentifierRecord {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(identifier, "identifier");
        Objects.requireNonNull(period, "period");
    }
}
