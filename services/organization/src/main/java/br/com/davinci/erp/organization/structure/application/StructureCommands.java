package br.com.davinci.erp.organization.structure.application;

import br.com.davinci.erp.organization.shared.domain.LocalizedText;
import java.time.LocalDate;
import java.util.Set;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

public final class StructureCommands {

    private StructureCommands() {}

    public record CreateUnitType(String code, LocalizedText name) {}

    public record SetNesting(Set<UUID> allowedParentTypeIds) {}

    public record CreateUnit(
            String code,
            LocalizedText name,
            UUID unitTypeId,
            @Nullable UUID legalEntityId,
            @Nullable UUID establishmentId,
            @Nullable LocalDate validFrom) {}

    /** Merge-patch: ausente = não muda; {@code legalEntityId}/{@code establishmentId} aceitam limpar. */
    public record UpdateUnit(
            @Nullable LocalizedText name,
            @Nullable UUID unitTypeId,
            boolean legalEntityPresent,
            @Nullable UUID legalEntityId,
            boolean establishmentPresent,
            @Nullable UUID establishmentId) {}

    public record CreateStructure(String code, LocalizedText name, String purpose) {}

    public record Move(UUID unitId, @Nullable UUID parentUnitId, LocalDate effectiveOn) {}
}
