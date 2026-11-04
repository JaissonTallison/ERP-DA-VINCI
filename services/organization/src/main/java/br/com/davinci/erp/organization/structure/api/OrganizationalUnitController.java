package br.com.davinci.erp.organization.structure.api;

import br.com.davinci.erp.organization.shared.domain.InvalidValueException;
import br.com.davinci.erp.organization.shared.domain.LocalizedText;
import br.com.davinci.erp.organization.shared.web.Cursor;
import br.com.davinci.erp.organization.shared.web.LifecycleBody;
import br.com.davinci.erp.organization.shared.web.Versions;
import br.com.davinci.erp.organization.structure.api.StructureRepresentations.CreateUnitRequest;
import br.com.davinci.erp.organization.structure.api.StructureRepresentations.UnitPage;
import br.com.davinci.erp.organization.structure.api.StructureRepresentations.UnitResponse;
import br.com.davinci.erp.organization.structure.application.OrganizationalUnitService;
import br.com.davinci.erp.organization.structure.application.StructureCommands;
import br.com.davinci.erp.organization.structure.domain.OrganizationalUnit;
import br.com.davinci.erp.organization.structure.domain.UnitStatus;
import br.com.davinci.erp.organization.structure.persistence.OrganizationalUnitRepository;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import java.net.URI;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import tools.jackson.databind.JsonNode;

@RestController
@RequestMapping("/v1/organizational-units")
@Validated
class OrganizationalUnitController {

    private static final Set<String> PATCHABLE =
            Set.of("name", "nameTranslations", "unitTypeId", "legalEntityId", "establishmentId");

    private final OrganizationalUnitService service;

    OrganizationalUnitController(OrganizationalUnitService service) {
        this.service = service;
    }

    @PostMapping
    ResponseEntity<UnitResponse> create(@Valid @RequestBody CreateUnitRequest body) {
        OrganizationalUnit unit = service.create(new StructureCommands.CreateUnit(
                Objects.requireNonNull(body.code()),
                StructureRepresentations.text(body.name(), body.nameTranslations()),
                Objects.requireNonNull(body.unitTypeId()),
                body.legalEntityId(),
                body.establishmentId(),
                body.validFrom()));
        return ResponseEntity.created(URI.create("/v1/organizational-units/" + unit.id()))
                .eTag(Versions.etag(unit.version()))
                .body(UnitResponse.from(unit));
    }

    @GetMapping
    UnitPage list(
            @RequestParam @Nullable UUID unitTypeId,
            @RequestParam @Nullable UUID legalEntityId,
            @RequestParam @Nullable UnitStatus status,
            @RequestParam @Size(max = 100) @Nullable String q,
            @RequestParam(defaultValue = "50") @Min(1) @Max(200) int limit,
            @RequestParam @Nullable String cursor) {
        var filter = new OrganizationalUnitRepository.Filter(unitTypeId, legalEntityId, status, q);
        List<OrganizationalUnit> found = service.page(filter, cursor == null ? null : Cursor.decode(cursor), limit + 1);
        List<OrganizationalUnit> page = found.size() > limit ? found.subList(0, limit) : found;
        String next = found.size() > limit ? Cursor.encode(page.getLast().code().value()) : null;
        return new UnitPage(page.stream().map(UnitResponse::from).toList(), next);
    }

    @GetMapping("/{id}")
    ResponseEntity<UnitResponse> get(@PathVariable UUID id) {
        return respond(service.get(id));
    }

    @PatchMapping(
            path = "/{id}",
            consumes = {"application/merge-patch+json", "application/json"})
    ResponseEntity<UnitResponse> update(
            @PathVariable UUID id, @RequestHeader("If-Match") String ifMatch, @RequestBody JsonNode body) {
        if (!body.isObject()) {
            throw new InvalidValueException("body", "O corpo deve ser um objeto JSON");
        }
        for (String field : body.propertyNames()) {
            if (!PATCHABLE.contains(field)) {
                throw new InvalidValueException(field, "Campo não pode ser alterado: " + field);
            }
        }
        LocalizedText name = null;
        if (body.has("name") || body.has("nameTranslations")) {
            OrganizationalUnit current = service.get(id);
            String text = body.has("name")
                    ? body.get("name").asString()
                    : current.name().text();
            Map<String, String> translations =
                    new LinkedHashMap<>(current.name().translations());
            if (body.has("nameTranslations")) {
                translations.clear();
                body.get("nameTranslations")
                        .properties()
                        .forEach(e -> translations.put(e.getKey(), e.getValue().asString()));
            }
            name = new LocalizedText(text, translations);
        }
        return respond(service.update(
                id,
                Versions.fromIfMatch(ifMatch),
                new StructureCommands.UpdateUnit(
                        name,
                        uuid(body, "unitTypeId"),
                        body.has("legalEntityId"),
                        uuid(body, "legalEntityId"),
                        body.has("establishmentId"),
                        uuid(body, "establishmentId"))));
    }

    @PostMapping("/{id}/{transition:deactivate|reactivate|close}")
    ResponseEntity<UnitResponse> transition(
            @PathVariable UUID id,
            @PathVariable String transition,
            @Valid @RequestBody(required = false) @Nullable LifecycleBody body) {
        return respond(service.transition(
                id,
                OrganizationalUnitService.Transition.valueOf(transition.toUpperCase(Locale.ROOT)),
                body == null ? null : body.effectiveOn()));
    }

    private static @Nullable UUID uuid(JsonNode body, String field) {
        JsonNode node = body.get(field);
        if (node == null || node.isNull()) {
            return null;
        }
        try {
            return UUID.fromString(node.asString());
        } catch (IllegalArgumentException e) {
            throw new InvalidValueException(field, field + " deve ser um UUID");
        }
    }

    private static ResponseEntity<UnitResponse> respond(OrganizationalUnit unit) {
        return ResponseEntity.ok().eTag(Versions.etag(unit.version())).body(UnitResponse.from(unit));
    }
}
