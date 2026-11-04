package br.com.davinci.erp.organization.corporate.api;

import br.com.davinci.erp.organization.corporate.api.Representations.CreateEconomicGroupRequest;
import br.com.davinci.erp.organization.corporate.api.Representations.EconomicGroupPage;
import br.com.davinci.erp.organization.corporate.api.Representations.EconomicGroupResponse;
import br.com.davinci.erp.organization.corporate.api.Representations.LegalEntityPage;
import br.com.davinci.erp.organization.corporate.api.Representations.LegalEntityResponse;
import br.com.davinci.erp.organization.corporate.api.Representations.LifecycleRequest;
import br.com.davinci.erp.organization.corporate.application.Commands;
import br.com.davinci.erp.organization.corporate.application.EconomicGroupService;
import br.com.davinci.erp.organization.corporate.application.LegalEntityService;
import br.com.davinci.erp.organization.corporate.domain.EconomicGroup;
import br.com.davinci.erp.organization.corporate.domain.EconomicGroupStatus;
import br.com.davinci.erp.organization.shared.web.Cursor;
import br.com.davinci.erp.organization.shared.web.Versions;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import java.net.URI;
import java.time.LocalDate;
import java.util.List;
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
@RequestMapping("/v1/economic-groups")
@Validated
class EconomicGroupController {

    private static final Set<String> PATCHABLE = Set.of("name", "referenceCurrency", "referenceTimezone");

    private final EconomicGroupService groups;
    private final LegalEntityService entities;

    EconomicGroupController(EconomicGroupService groups, LegalEntityService entities) {
        this.groups = groups;
        this.entities = entities;
    }

    @PostMapping
    ResponseEntity<EconomicGroupResponse> create(
            @RequestHeader("Idempotency-Key") @Size(min = 1, max = 100) String idempotencyKey,
            @Valid @RequestBody CreateEconomicGroupRequest body) {
        EconomicGroup group = groups.create(
                idempotencyKey,
                new Commands.CreateEconomicGroup(
                        Objects.requireNonNull(body.code()), Objects.requireNonNull(body.name()),
                        Objects.requireNonNull(body.referenceCurrency()),
                                Objects.requireNonNull(body.referenceTimezone())));
        return ResponseEntity.created(URI.create("/v1/economic-groups/" + group.id()))
                .eTag(Versions.etag(group.version()))
                .body(EconomicGroupResponse.from(group));
    }

    @GetMapping
    EconomicGroupPage list(
            @RequestParam @Nullable EconomicGroupStatus status,
            @RequestParam(defaultValue = "50") @Min(1) @Max(200) int limit,
            @RequestParam @Nullable String cursor) {
        List<EconomicGroup> found = groups.page(status, cursor == null ? null : Cursor.decode(cursor), limit + 1);
        List<EconomicGroup> page = found.size() > limit ? found.subList(0, limit) : found;
        String next = found.size() > limit ? Cursor.encode(page.getLast().code().value()) : null;
        return new EconomicGroupPage(
                page.stream().map(EconomicGroupResponse::from).toList(), next);
    }

    @GetMapping("/{id}")
    ResponseEntity<EconomicGroupResponse> get(@PathVariable UUID id) {
        EconomicGroup group = groups.get(id);
        return ResponseEntity.ok().eTag(Versions.etag(group.version())).body(EconomicGroupResponse.from(group));
    }

    @PatchMapping(
            path = "/{id}",
            consumes = {"application/merge-patch+json", "application/json"})
    ResponseEntity<EconomicGroupResponse> update(
            @PathVariable UUID id, @RequestHeader("If-Match") String ifMatch, @RequestBody JsonNode body) {
        MergePatch patch = new MergePatch(body, PATCHABLE);
        EconomicGroup group = groups.update(
                id,
                Versions.fromIfMatch(ifMatch),
                new Commands.UpdateEconomicGroup(
                        patch.text("name"), patch.text("referenceCurrency"), patch.text("referenceTimezone")));
        return ResponseEntity.ok().eTag(Versions.etag(group.version())).body(EconomicGroupResponse.from(group));
    }

    @PostMapping("/{id}/close")
    ResponseEntity<EconomicGroupResponse> close(
            @PathVariable UUID id, @Valid @RequestBody(required = false) @Nullable LifecycleRequest body) {
        EconomicGroup group = groups.close(id, body == null ? null : body.effectiveOn());
        return ResponseEntity.ok().eTag(Versions.etag(group.version())).body(EconomicGroupResponse.from(group));
    }

    @GetMapping("/{id}/legal-entities")
    LegalEntityPage legalEntities(@PathVariable UUID id, @RequestParam @Nullable LocalDate asOf) {
        return new LegalEntityPage(
                entities.ofGroup(id, asOf, 1000).stream()
                        .map(LegalEntityResponse::from)
                        .toList(),
                null);
    }
}
