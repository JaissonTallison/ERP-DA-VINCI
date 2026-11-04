package br.com.davinci.erp.organization.structure.api;

import br.com.davinci.erp.organization.shared.domain.LocalizedText;
import br.com.davinci.erp.organization.structure.domain.OrganizationalStructure;
import br.com.davinci.erp.organization.structure.domain.OrganizationalUnit;
import br.com.davinci.erp.organization.structure.domain.UnitType;
import br.com.davinci.erp.organization.structure.persistence.TreeNode;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/** Corpos de requisição e resposta v1 do módulo de estrutura. */
final class StructureRepresentations {

    private StructureRepresentations() {}

    static LocalizedText text(@Nullable String name, @Nullable Map<String, String> translations) {
        return new LocalizedText(String.valueOf(name), translations == null ? Map.of() : translations);
    }

    record CreateUnitTypeRequest(
            @NotBlank @Nullable String code,
            @NotBlank @Nullable String name,
            @Size(max = 20) @Nullable Map<String, String> nameTranslations) {}

    record UnitTypeResponse(
            UUID id, String code, String name, Map<String, String> nameTranslations, Instant createdAt, long version) {

        static UnitTypeResponse from(UnitType t) {
            return new UnitTypeResponse(
                    t.id(), t.code().value(), t.name().text(), t.name().translations(), t.createdAt(), t.version());
        }
    }

    record UnitTypePage(List<UnitTypeResponse> items) {}

    record NestingRequest(
            @NotNull @Size(max = 100) @Nullable Set<UUID> allowedParentTypeIds) {}

    record NestingResponse(UUID unitTypeId, Set<UUID> allowedParentTypeIds) {}

    record CreateUnitRequest(
            @NotBlank @Nullable String code,
            @NotBlank @Nullable String name,
            @Size(max = 20) @Nullable Map<String, String> nameTranslations,
            @NotNull @Nullable UUID unitTypeId,
            @Nullable UUID legalEntityId,
            @Nullable UUID establishmentId,
            @Nullable LocalDate validFrom) {}

    record UnitResponse(
            UUID id,
            String code,
            String name,
            Map<String, String> nameTranslations,
            UUID unitTypeId,
            @Nullable UUID legalEntityId,
            @Nullable UUID establishmentId,
            String status,
            LocalDate validFrom,
            @Nullable LocalDate closedOn,
            Instant createdAt,
            Instant updatedAt,
            long version) {

        static UnitResponse from(OrganizationalUnit u) {
            return new UnitResponse(
                    u.id(),
                    u.code().value(),
                    u.name().text(),
                    u.name().translations(),
                    u.unitTypeId(),
                    u.legalEntityId(),
                    u.establishmentId(),
                    u.status().name(),
                    u.validFrom(),
                    u.closedOn(),
                    u.createdAt(),
                    u.updatedAt(),
                    u.version());
        }
    }

    record UnitPage(List<UnitResponse> items, @Nullable String nextCursor) {}

    record CreateStructureRequest(
            @NotBlank @Nullable String code,
            @NotBlank @Nullable String name,
            @Size(max = 20) @Nullable Map<String, String> nameTranslations,
            @NotBlank @Nullable String purpose) {}

    record StructureResponse(
            UUID id,
            String code,
            String name,
            Map<String, String> nameTranslations,
            String purpose,
            Instant createdAt,
            long version) {

        static StructureResponse from(OrganizationalStructure s) {
            return new StructureResponse(
                    s.id(),
                    s.code().value(),
                    s.name().text(),
                    s.name().translations(),
                    s.purpose().name(),
                    s.createdAt(),
                    s.version());
        }
    }

    record StructurePage(List<StructureResponse> items) {}

    record MoveRequest(
            @NotNull @Nullable UUID unitId,
            @Nullable UUID parentUnitId,
            @NotNull @Nullable LocalDate effectiveOn) {}

    record RemovalRequest(
            @NotNull @Nullable UUID unitId,
            @NotNull @Nullable LocalDate effectiveOn) {}

    record TreeNodeResponse(
            UUID unitId,
            String code,
            String name,
            Map<String, String> nameTranslations,
            String unitTypeCode,
            String status,
            @Nullable UUID parentUnitId,
            int depth,
            boolean hasChildren) {

        static TreeNodeResponse from(TreeNode n) {
            return new TreeNodeResponse(
                    n.unitId(),
                    n.code(),
                    n.name().text(),
                    n.name().translations(),
                    n.unitTypeCode(),
                    n.status(),
                    n.parentUnitId(),
                    n.depth(),
                    n.hasChildren());
        }
    }

    record TreeResponse(LocalDate asOf, List<TreeNodeResponse> items, boolean truncated) {

        static TreeResponse of(LocalDate asOf, List<TreeNode> nodes, boolean truncated) {
            return new TreeResponse(
                    asOf, nodes.stream().map(TreeNodeResponse::from).toList(), truncated);
        }
    }
}
