package br.com.davinci.erp.organization.corporate.api;

import br.com.davinci.erp.organization.corporate.api.Representations.AddressRequest;
import br.com.davinci.erp.organization.corporate.api.Representations.CreateLegalEntityRequest;
import br.com.davinci.erp.organization.corporate.api.Representations.GroupAssignmentRequest;
import br.com.davinci.erp.organization.corporate.api.Representations.LegalEntityPage;
import br.com.davinci.erp.organization.corporate.api.Representations.LegalEntityResponse;
import br.com.davinci.erp.organization.corporate.api.Representations.LifecycleRequest;
import br.com.davinci.erp.organization.corporate.api.Representations.TaxIdentifierRequest;
import br.com.davinci.erp.organization.corporate.application.Commands;
import br.com.davinci.erp.organization.corporate.application.LegalEntityService;
import br.com.davinci.erp.organization.corporate.application.LegalEntityView;
import br.com.davinci.erp.organization.corporate.domain.LegalEntityStatus;
import br.com.davinci.erp.organization.corporate.persistence.LegalEntityRepository;
import br.com.davinci.erp.organization.shared.domain.InvalidValueException;
import br.com.davinci.erp.organization.shared.web.Cursor;
import br.com.davinci.erp.organization.shared.web.Versions;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import java.net.URI;
import java.time.LocalDate;
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
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

@RestController
@RequestMapping("/v1/legal-entities")
@Validated
class LegalEntityController {

    private static final Set<String> PATCHABLE = Set.of(
            "legalName",
            "tradeName",
            "legalForm",
            "functionalCurrency",
            "timezone",
            "defaultLocale",
            "fiscalYearStartMonth",
            "registeredAddress");

    private final LegalEntityService service;
    private final JsonMapper json;

    LegalEntityController(LegalEntityService service, JsonMapper json) {
        this.service = service;
        this.json = json;
    }

    @PostMapping
    ResponseEntity<LegalEntityResponse> create(
            @RequestHeader("Idempotency-Key") @Size(min = 1, max = 100) String idempotencyKey,
            @Valid @RequestBody CreateLegalEntityRequest body) {
        String status = body.status() == null ? "PLANNED" : body.status();
        if (!Set.of("PLANNED", "ACTIVE").contains(status)) {
            throw new InvalidValueException("status", "Na criação, status deve ser PLANNED ou ACTIVE");
        }
        List<Commands.NewTaxIdentifier> taxes = body.taxIdentifiers() == null
                ? List.of()
                : body.taxIdentifiers().stream()
                        .map(LegalEntityController::taxIdentifier)
                        .toList();
        LegalEntityView view = service.create(
                idempotencyKey,
                new Commands.CreateLegalEntity(
                        Objects.requireNonNull(body.code()),
                        Objects.requireNonNull(body.legalName()),
                        body.tradeName(),
                        Objects.requireNonNull(body.country()).toUpperCase(Locale.ROOT),
                        body.subdivision(),
                        body.legalForm(),
                        Objects.requireNonNull(body.functionalCurrency()),
                        Objects.requireNonNull(body.timezone()),
                        Objects.requireNonNull(body.defaultLocale()),
                        body.fiscalYearStartMonth() == null ? 1 : body.fiscalYearStartMonth(),
                        Objects.requireNonNull(body.registeredAddress()).toDomain(),
                        taxes,
                        body.economicGroupId(),
                        "ACTIVE".equals(status),
                        body.effectiveOn()));
        return ResponseEntity.created(
                        URI.create("/v1/legal-entities/" + view.entity().id()))
                .eTag(Versions.etag(view.entity().version()))
                .body(LegalEntityResponse.from(view));
    }

    @GetMapping
    LegalEntityPage list(
            @RequestParam @Nullable String country,
            @RequestParam @Nullable LegalEntityStatus status,
            @RequestParam @Nullable UUID economicGroupId,
            @RequestParam @Size(max = 100) @Nullable String q,
            @RequestParam @Nullable LocalDate asOf,
            @RequestParam(defaultValue = "50") @Min(1) @Max(200) int limit,
            @RequestParam @Nullable String cursor) {
        LocalDate date = asOf == null ? service.todayUtc() : asOf;
        var filter = new LegalEntityRepository.Filter(country, status, economicGroupId, date, q);
        List<LegalEntityView> found = service.page(filter, cursor == null ? null : Cursor.decode(cursor), limit + 1);
        List<LegalEntityView> page = found.size() > limit ? found.subList(0, limit) : found;
        String next = found.size() > limit
                ? Cursor.encode(page.getLast().entity().code().value())
                : null;
        return new LegalEntityPage(page.stream().map(LegalEntityResponse::from).toList(), next);
    }

    @GetMapping("/{id}")
    ResponseEntity<LegalEntityResponse> get(@PathVariable UUID id, @RequestParam @Nullable LocalDate asOf) {
        return respond(service.get(id, asOf));
    }

    @PatchMapping(
            path = "/{id}",
            consumes = {"application/merge-patch+json", "application/json"})
    ResponseEntity<LegalEntityResponse> update(
            @PathVariable UUID id, @RequestHeader("If-Match") String ifMatch, @RequestBody JsonNode body) {
        MergePatch patch = new MergePatch(body, PATCHABLE);
        JsonNode address = patch.node("registeredAddress");
        return respond(service.update(
                id,
                Versions.fromIfMatch(ifMatch),
                new Commands.UpdateLegalEntity(
                        patch.text("legalName"),
                        patch.has("tradeName"),
                        patch.text("tradeName"),
                        patch.has("legalForm"),
                        patch.text("legalForm"),
                        patch.text("functionalCurrency"),
                        patch.text("timezone"),
                        patch.text("defaultLocale"),
                        patch.integer("fiscalYearStartMonth"),
                        address == null || address.isNull()
                                ? null
                                : json.treeToValue(address, AddressRequest.class)
                                        .toDomain())));
    }

    @PostMapping("/{id}/{transition:activate|suspend|reactivate|close}")
    ResponseEntity<LegalEntityResponse> transition(
            @PathVariable UUID id,
            @PathVariable String transition,
            @Valid @RequestBody(required = false) @Nullable LifecycleRequest body) {
        return respond(service.transition(
                id,
                LegalEntityService.Transition.valueOf(transition.toUpperCase(Locale.ROOT)),
                body == null ? null : body.effectiveOn()));
    }

    @PutMapping("/{id}/economic-group")
    ResponseEntity<LegalEntityResponse> assignGroup(
            @PathVariable UUID id, @Valid @RequestBody GroupAssignmentRequest body) {
        return respond(service.assignGroup(id, body.economicGroupId(), Objects.requireNonNull(body.effectiveOn())));
    }

    @PostMapping("/{id}/tax-identifiers")
    ResponseEntity<LegalEntityResponse> addTaxIdentifier(
            @PathVariable UUID id, @Valid @RequestBody TaxIdentifierRequest body) {
        return respond(service.addTaxIdentifier(id, taxIdentifier(body)));
    }

    @PostMapping("/{id}/tax-identifiers/{taxIdentifierId}/end")
    ResponseEntity<LegalEntityResponse> endTaxIdentifier(
            @PathVariable UUID id,
            @PathVariable UUID taxIdentifierId,
            @Valid @RequestBody(required = false) @Nullable LifecycleRequest body) {
        return respond(service.endTaxIdentifier(id, taxIdentifierId, body == null ? null : body.effectiveOn()));
    }

    private static Commands.NewTaxIdentifier taxIdentifier(TaxIdentifierRequest t) {
        return new Commands.NewTaxIdentifier(
                Objects.requireNonNull(t.scheme()),
                Objects.requireNonNull(t.value()),
                Boolean.TRUE.equals(t.primary()),
                t.validFrom());
    }

    private static ResponseEntity<LegalEntityResponse> respond(LegalEntityView view) {
        return ResponseEntity.ok().eTag(Versions.etag(view.entity().version())).body(LegalEntityResponse.from(view));
    }
}
