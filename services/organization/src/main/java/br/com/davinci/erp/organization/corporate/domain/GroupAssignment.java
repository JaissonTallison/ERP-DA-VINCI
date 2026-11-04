package br.com.davinci.erp.organization.corporate.domain;

import br.com.davinci.erp.organization.shared.domain.EffectivePeriod;
import java.util.Objects;
import java.util.UUID;

/** Pertença de uma entidade jurídica a um grupo econômico, com vigência. */
public record GroupAssignment(UUID economicGroupId, EffectivePeriod period) {

    public GroupAssignment {
        Objects.requireNonNull(economicGroupId, "economicGroupId");
        Objects.requireNonNull(period, "period");
    }
}
