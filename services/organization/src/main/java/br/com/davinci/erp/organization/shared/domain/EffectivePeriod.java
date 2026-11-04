package br.com.davinci.erp.organization.shared.domain;

import java.time.LocalDate;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

/** Vigência {@code [validFrom, validTo)}; {@code validTo} nulo = sem fim (ADR-0020). */
public record EffectivePeriod(LocalDate validFrom, @Nullable LocalDate validTo) {

    public EffectivePeriod {
        Objects.requireNonNull(validFrom, "validFrom");
        if (validTo != null && !validTo.isAfter(validFrom)) {
            throw new InvalidValueException("validTo", "O fim da vigência deve ser posterior ao início");
        }
    }

    public static EffectivePeriod from(LocalDate start) {
        return new EffectivePeriod(start, null);
    }

    public boolean contains(LocalDate date) {
        return !date.isBefore(validFrom) && (validTo == null || date.isBefore(validTo));
    }

    public boolean isOpen() {
        return validTo == null;
    }

    public EffectivePeriod endingOn(LocalDate end) {
        return new EffectivePeriod(validFrom, end);
    }
}
