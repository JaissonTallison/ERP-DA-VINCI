package br.com.davinci.erp.organization.corporate.api;

import br.com.davinci.erp.organization.corporate.api.Representations.AddressRequest;
import br.com.davinci.erp.organization.corporate.api.Representations.EstablishmentPage;
import br.com.davinci.erp.organization.corporate.api.Representations.EstablishmentResponse;
import br.com.davinci.erp.organization.corporate.api.Representations.OpenEstablishmentRequest;
import br.com.davinci.erp.organization.corporate.api.Representations.TaxIdentifierRequest;
import br.com.davinci.erp.organization.corporate.application.Commands;
import br.com.davinci.erp.organization.corporate.application.EstablishmentService;
import br.com.davinci.erp.organization.corporate.application.EstablishmentView;
import br.com.davinci.erp.organization.corporate.domain.EstablishmentStatus;
import br.com.davinci.erp.organization.corporate.domain.EstablishmentType;
import br.com.davinci.erp.organization.corporate.persistence.EstablishmentRepository;
import br.com.davinci.erp.organization.shared.domain.InvalidValueException;
import br.com.davinci.erp.organization.shared.web.Cursor;
import br.com.davinci.erp.organization.shared.web.LifecycleBody;
import br.com.davinci.erp.organization.shared.web.Versions;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import java.net.URI;
import java.util.List;
import java.util.Locale;
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
import tools.jackson.databind.json.JsonMapper;

@RestController
@RequestMapping("/v1/establishments")
@Validated
class EstablishmentController {

    private static final Set<String> PATCHABLE = Set.of("name", "type", "address", "timezone");

    private final EstablishmentService service;
    private final JsonMapper json;

    EstablishmentController(EstablishmentService service, JsonMapper json) {
        this.service = service;
        this.json = json;
    }

    @PostMapping
    ResponseEntity<EstablishmentResponse> open(
            @RequestHeader("Idempotency-Key") @Size(min = 1, max = 100) String idempotencyKey,
            @Valid @RequestBody OpenEstablishmentRequest body) {
        List<Commands.NewTaxIdentifier> taxes = body.taxIdentifiers() == null
                ? List.of()
                : body.taxIdentifiers().stream()
                        .map(EstablishmentController::taxIdentifier)
                        .toList();
        EstablishmentView view = service.open(
                idempotencyKey,
                new Commands.OpenEstablishment(
                        Objects.requireNonNull(body.legalEntityId()),
                        Objects.requireNonNull(body.code()),
                        Objects.requireNonNull(body.name()),
                        Objects.requireNonNull(body.type()),
                        Objects.requireNonNull(body.address()).toDomain(),
                        body.timezone(),
                        taxes,
                        body.openedOn()));
        return ResponseEntity.created(
                        URI.create("/v1/establishments/" + view.establishment().id()))
                .eTag(Versions.etag(view.establishment().version()))
                .body(EstablishmentResponse.from(view));
    }

    @GetMapping
    EstablishmentPage list(
            @RequestParam @Nullable UUID legalEntityId,
            @RequestParam @Nullable String type,
            @RequestParam @Nullable EstablishmentStatus status,
            @RequestParam @Nullable String country,
            @RequestParam(defaultValue = "50") @Min(1) @Max(200) int limit,
            @RequestParam @Nullable String cursor) {
        String afterCode = null;
        UUID afterId = null;
        if (cursor != null) {
            String[] parts = Cursor.decode(cursor).split("\u0000", 2);
            if (parts.length != 2) {
                throw new InvalidValueException("cursor", "invalid-cursor", "Cursor de paginação inválido");
            }
            afterCode = parts[0];
            afterId = parseId(parts[1]);
        }
        var filter =
                new EstablishmentRepository.Filter(legalEntityId, type == null ? null : type(type), status, country);
        List<EstablishmentView> found = service.page(filter, afterCode, afterId, limit + 1);
        List<EstablishmentView> page = found.size() > limit ? found.subList(0, limit) : found;
        String next = found.size() > limit
                ? Cursor.encode(page.getLast().establishment().code().value() + "\u0000"
                        + page.getLast().establishment().id())
                : null;
        return new EstablishmentPage(
                page.stream().map(EstablishmentResponse::from).toList(), next);
    }

    @GetMapping("/{id}")
    ResponseEntity<EstablishmentResponse> get(@PathVariable UUID id) {
        return respond(service.get(id));
    }

    @PatchMapping(
            path = "/{id}",
            consumes = {"application/merge-patch+json", "application/json"})
    ResponseEntity<EstablishmentResponse> update(
            @PathVariable UUID id, @RequestHeader("If-Match") String ifMatch, @RequestBody JsonNode body) {
        MergePatch patch = new MergePatch(body, PATCHABLE);
        JsonNode address = patch.node("address");
        return respond(service.update(
                id,
                Versions.fromIfMatch(ifMatch),
                new Commands.UpdateEstablishment(
                        patch.text("name"),
                        patch.text("type"),
                        address == null || address.isNull()
                                ? null
                                : json.treeToValue(address, AddressRequest.class)
                                        .toDomain(),
                        patch.text("timezone"))));
    }

    @PostMapping("/{id}/{transition:suspend|reactivate|close}")
    ResponseEntity<EstablishmentResponse> transition(
            @PathVariable UUID id,
            @PathVariable String transition,
            @Valid @RequestBody(required = false) @Nullable LifecycleBody body) {
        return respond(service.transition(
                id,
                EstablishmentService.Transition.valueOf(transition.toUpperCase(Locale.ROOT)),
                body == null ? null : body.effectiveOn()));
    }

    @PostMapping("/{id}/tax-identifiers")
    ResponseEntity<EstablishmentResponse> addTaxIdentifier(
            @PathVariable UUID id, @Valid @RequestBody TaxIdentifierRequest body) {
        return respond(service.addTaxIdentifier(id, taxIdentifier(body)));
    }

    @PostMapping("/{id}/tax-identifiers/{taxIdentifierId}/end")
    ResponseEntity<EstablishmentResponse> endTaxIdentifier(
            @PathVariable UUID id,
            @PathVariable UUID taxIdentifierId,
            @Valid @RequestBody(required = false) @Nullable LifecycleBody body) {
        return respond(service.endTaxIdentifier(id, taxIdentifierId, body == null ? null : body.effectiveOn()));
    }

    private static Commands.NewTaxIdentifier taxIdentifier(TaxIdentifierRequest t) {
        return new Commands.NewTaxIdentifier(
                Objects.requireNonNull(t.scheme()),
                Objects.requireNonNull(t.value()),
                Boolean.TRUE.equals(t.primary()),
                t.validFrom());
    }

    private static EstablishmentType type(String raw) {
        try {
            return EstablishmentType.valueOf(raw.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new InvalidValueException("type", "Tipo de estabelecimento inválido: " + raw);
        }
    }

    private static UUID parseId(String raw) {
        try {
            return UUID.fromString(raw);
        } catch (IllegalArgumentException e) {
            throw new InvalidValueException("cursor", "invalid-cursor", "Cursor de paginação inválido");
        }
    }

    private static ResponseEntity<EstablishmentResponse> respond(EstablishmentView view) {
        return ResponseEntity.ok()
                .eTag(Versions.etag(view.establishment().version()))
                .body(EstablishmentResponse.from(view));
    }
}
