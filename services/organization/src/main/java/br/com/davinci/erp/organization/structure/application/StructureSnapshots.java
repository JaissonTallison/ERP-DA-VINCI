package br.com.davinci.erp.organization.structure.application;

import br.com.davinci.erp.organization.shared.domain.LocalizedText;
import br.com.davinci.erp.organization.structure.domain.OrganizationalStructure;
import br.com.davinci.erp.organization.structure.domain.OrganizationalUnit;
import java.time.LocalDate;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

/** Estado da unidade para eventos (contracts/events/organization/v1/unit-event.schema.json) e auditoria. */
@Component
class StructureSnapshots {

    private final JsonMapper json;

    StructureSnapshots(JsonMapper json) {
        this.json = json;
    }

    ObjectNode unit(OrganizationalUnit u, LocalDate effectiveOn, @Nullable Placement placement) {
        ObjectNode node = json.createObjectNode()
                .put("unitId", u.id().toString())
                .put("tenantId", u.tenantId().toString())
                .put("code", u.code().value())
                .put("unitTypeId", u.unitTypeId().toString())
                .put(
                        "legalEntityId",
                        u.legalEntityId() == null ? null : u.legalEntityId().toString())
                .put(
                        "establishmentId",
                        u.establishmentId() == null ? null : u.establishmentId().toString())
                .put("status", u.status().name())
                .put("validFrom", u.validFrom().toString())
                .put("closedOn", u.closedOn() == null ? null : u.closedOn().toString())
                .put("effectiveOn", effectiveOn.toString());
        node.set("name", name(u.name()));
        if (placement == null) {
            node.putNull("placement");
        } else {
            node.putObject("placement")
                    .put("structureId", placement.structure().id().toString())
                    .put("structureCode", placement.structure().code().value())
                    .put(
                            "previousParentUnitId",
                            placement.previousParent() == null
                                    ? null
                                    : placement.previousParent().toString())
                    .put(
                            "parentUnitId",
                            placement.parent() == null
                                    ? null
                                    : placement.parent().toString())
                    .put("inStructure", placement.inStructure());
        }
        node.put("version", u.version());
        return node;
    }

    ObjectNode name(LocalizedText text) {
        ObjectNode node = json.createObjectNode().put("text", text.text());
        ObjectNode translations = node.putObject("translations");
        text.translations().forEach(translations::put);
        return node;
    }

    /** Posição da unidade numa estrutura depois de um movimento ou retirada. */
    record Placement(
            OrganizationalStructure structure,
            @Nullable UUID previousParent,
            @Nullable UUID parent,
            boolean inStructure) {}
}
