package br.com.davinci.erp.organization.costing.api;

import br.com.davinci.erp.organization.costing.application.CostCenterService;
import br.com.davinci.erp.organization.costing.domain.CostCenter;
import br.com.davinci.erp.organization.costing.domain.CostCenterStatus;
import br.com.davinci.erp.organization.costing.persistence.CostCenterRepository;
import br.com.davinci.erp.organization.shared.web.LifecycleBody;
import br.com.davinci.erp.organization.shared.web.Versions;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.net.URI;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Locale;
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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Validated
class CostCenterController {

    private final CostCenterService service;

    CostCenterController(CostCenterService service) {
        this.service = service;
    }

    record CreateRequest(
            @NotNull UUID legalEntityId,
            @NotBlank @Size(max = 40) String code,
            @NotBlank @Size(max = 200) String name,
            @NotBlank String kind,
            @NotBlank @Size(max = 60) String category,
            @Nullable UUID responsibleUnitId,
            @Nullable UUID parentId,
            @Nullable LocalDate validFrom) {}

    record UpdateRequest(
            @Nullable @Size(max = 200) String name,
            @Nullable @Size(max = 60) String category) {}

    record ResponsibleRequest(@Nullable UUID responsibleUnitId) {}

    record MoveRequest(@Nullable UUID parentId, @Nullable LocalDate effectiveOn) {}

    record CostCenterResponse(
            UUID id,
            UUID legalEntityId,
            String code,
            String name,
            String kind,
            String category,
            @Nullable UUID responsibleUnitId,
            String status,
            LocalDate validFrom,
            @Nullable LocalDate closedOn,
            Instant createdAt,
            Instant updatedAt,
            long version) {

        static CostCenterResponse from(CostCenter c) {
            return new CostCenterResponse(
                    c.id(),
                    c.legalEntityId(),
                    c.code().value(),
                    c.name(),
                    c.kind().name(),
                    c.category(),
                    c.responsibleUnitId(),
                    c.status().name(),
                    c.validFrom(),
                    c.closedOn(),
                    c.createdAt(),
                    c.updatedAt(),
                    c.version());
        }
    }

    record CostCenterList(List<CostCenterResponse> items) {}

    record TreeResponse(LocalDate asOf, List<CostCenterRepository.Node> items) {}

    @PostMapping("/v1/cost-centers")
    ResponseEntity<CostCenterResponse> create(@Valid @RequestBody CreateRequest body) {
        CostCenter c = service.create(new CostCenterService.Create(
                body.legalEntityId(),
                body.code(),
                body.name(),
                body.kind(),
                body.category(),
                body.responsibleUnitId(),
                body.parentId(),
                body.validFrom()));
        return ResponseEntity.created(URI.create("/v1/cost-centers/" + c.id()))
                .eTag(Versions.etag(c.version()))
                .body(CostCenterResponse.from(c));
    }

    @GetMapping("/v1/legal-entities/{entityId}/cost-centers")
    CostCenterList list(@PathVariable UUID entityId, @RequestParam @Nullable CostCenterStatus status) {
        return new CostCenterList(service.list(entityId, status).stream()
                .map(CostCenterResponse::from)
                .toList());
    }

    @GetMapping("/v1/legal-entities/{entityId}/cost-centers/tree")
    TreeResponse tree(@PathVariable UUID entityId, @RequestParam @Nullable LocalDate asOf) {
        LocalDate date = asOf == null ? LocalDate.now(ZoneOffset.UTC) : asOf;
        return new TreeResponse(date, service.tree(entityId, date));
    }

    @GetMapping("/v1/cost-centers/{id}")
    ResponseEntity<CostCenterResponse> get(@PathVariable UUID id) {
        return respond(service.get(id));
    }

    @PatchMapping(
            path = "/v1/cost-centers/{id}",
            consumes = {"application/merge-patch+json", "application/json"})
    ResponseEntity<CostCenterResponse> update(
            @PathVariable UUID id, @RequestHeader("If-Match") String ifMatch, @Valid @RequestBody UpdateRequest body) {
        return respond(service.update(
                id,
                Versions.fromIfMatch(ifMatch),
                new CostCenterService.Update(body.name(), body.category(), false, null)));
    }

    @PostMapping("/v1/cost-centers/{id}/responsible")
    ResponseEntity<CostCenterResponse> responsible(@PathVariable UUID id, @RequestBody ResponsibleRequest body) {
        return respond(service.reassign(id, body.responsibleUnitId()));
    }

    @PostMapping("/v1/cost-centers/{id}/moves")
    ResponseEntity<CostCenterResponse> move(@PathVariable UUID id, @RequestBody MoveRequest body) {
        return respond(service.move(id, body.parentId(), body.effectiveOn()));
    }

    @PostMapping("/v1/cost-centers/{id}/{transition:block|unblock|close}")
    ResponseEntity<CostCenterResponse> transition(
            @PathVariable UUID id,
            @PathVariable String transition,
            @Valid @RequestBody(required = false) @Nullable LifecycleBody body) {
        return respond(service.transition(
                id,
                CostCenterService.Transition.valueOf(transition.toUpperCase(Locale.ROOT)),
                body == null ? null : body.effectiveOn()));
    }

    private static ResponseEntity<CostCenterResponse> respond(CostCenter c) {
        return ResponseEntity.ok().eTag(Versions.etag(c.version())).body(CostCenterResponse.from(c));
    }
}
