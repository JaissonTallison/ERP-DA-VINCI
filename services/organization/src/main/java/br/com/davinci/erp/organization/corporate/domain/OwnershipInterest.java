package br.com.davinci.erp.organization.corporate.domain;

import br.com.davinci.erp.organization.shared.domain.EffectivePeriod;
import br.com.davinci.erp.organization.shared.domain.InvalidValueException;
import br.com.davinci.erp.platform.kernel.TenantId;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Objects;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/** Participação direta de um titular (entidade do tenant ou terceiro) no capital de uma entidade, com vigência. */
public record OwnershipInterest(
        UUID id,
        TenantId tenantId,
        UUID ownedLegalEntityId,
        Holder holder,
        BigDecimal capitalPercentage,
        BigDecimal votingPercentage,
        @Nullable String shareClass,
        EffectivePeriod period,
        Instant createdAt) {

    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);

    /** Titular: entidade jurídica do tenant ou parte externa. */
    public record Holder(HolderType type, UUID id) {

        public Holder {
            Objects.requireNonNull(type, "type");
            Objects.requireNonNull(id, "id");
        }
    }

    public enum HolderType {
        LEGAL_ENTITY,
        EXTERNAL_PARTY
    }

    public OwnershipInterest {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(ownedLegalEntityId, "ownedLegalEntityId");
        Objects.requireNonNull(holder, "holder");
        if (capitalPercentage.signum() <= 0 || capitalPercentage.compareTo(HUNDRED) > 0) {
            throw new InvalidValueException("capitalPercentage", "Participação no capital deve estar em (0, 100]");
        }
        if (votingPercentage.signum() < 0 || votingPercentage.compareTo(HUNDRED) > 0) {
            throw new InvalidValueException("votingPercentage", "Participação em votos deve estar em [0, 100]");
        }
        if (capitalPercentage.scale() > 4 || votingPercentage.scale() > 4) {
            throw new InvalidValueException("capitalPercentage", "No máximo 4 casas decimais");
        }
        if (holder.type() == HolderType.LEGAL_ENTITY && holder.id().equals(ownedLegalEntityId)) {
            throw new InvalidValueException("ownerLegalEntityId", "Uma entidade não participa de si mesma");
        }
        Objects.requireNonNull(period, "period");
    }

    public boolean activeOn(LocalDate date) {
        return period.contains(date);
    }

    public OwnershipInterest endingOn(LocalDate on) {
        if (!on.isAfter(period.validFrom())) {
            throw new InvalidValueException("effectiveOn", "O fim deve ser depois do início da participação");
        }
        return new OwnershipInterest(
                id,
                tenantId,
                ownedLegalEntityId,
                holder,
                capitalPercentage,
                votingPercentage,
                shareClass,
                period.endingOn(on),
                createdAt);
    }
}
