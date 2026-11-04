package br.com.davinci.erp.organization.structure.domain;

import br.com.davinci.erp.organization.shared.domain.EffectivePeriod;
import java.util.Objects;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/** Posição de uma unidade numa estrutura durante um período: pai ({@code null} = raiz). */
public record StructureLink(UUID unitId, @Nullable UUID parentUnitId, EffectivePeriod period) {

    public StructureLink {
        Objects.requireNonNull(unitId, "unitId");
        Objects.requireNonNull(period, "period");
        if (unitId.equals(parentUnitId)) {
            throw new IllegalArgumentException("Unidade não pode ser pai de si mesma");
        }
    }
}
