package br.com.davinci.erp.organization.reorganization.api;

import br.com.davinci.erp.organization.reorganization.application.ReorganizationService;
import br.com.davinci.erp.organization.reorganization.domain.Reorganization;
import br.com.davinci.erp.organization.reorganization.domain.ReorganizationStatus;
import br.com.davinci.erp.organization.reorganization.domain.Step;
import br.com.davinci.erp.organization.shared.domain.InvalidValueException;
import br.com.davinci.erp.organization.shared.web.Versions;
import br.com.davinci.erp.organization.structure.persistence.TreeNode;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.net.URI;
import java.time.Instant;
import java.time.LocalDate;
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
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/v1/reorganizations")
@Validated
class ReorganizationController {

    private final ReorganizationService service;

    ReorganizationController(ReorganizationService service) {
        this.service = service;
    }

    record CreateRequest(
            @NotNull UUID structureId,
            @NotBlank @Size(max = 500) String description,
            @NotNull LocalDate effectiveOn) {}

    record UpdateRequest(
            @Nullable @Size(max = 500) String description,
            @Nullable LocalDate effectiveOn) {}

    record StepBody(
            @NotBlank String operation,
            @Nullable UUID unitId,
            @Nullable UUID parentUnitId,
            @Nullable @Size(max = 200) String name,
            @Nullable UUID costCenterId,
            @Nullable UUID responsibleUnitId) {

        Step toStep() {
            Step.Operation op;
            try {
                op = Step.Operation.valueOf(operation.toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException e) {
                throw new InvalidValueException("operation", "Operação inválida: " + operation);
            }
            return new Step(op, unitId, parentUnitId, name, costCenterId, responsibleUnitId);
        }

        static StepBody from(Step s) {
            return new StepBody(
                    s.operation().name(),
                    s.unitId(),
                    s.parentUnitId(),
                    s.name(),
                    s.costCenterId(),
                    s.responsibleUnitId());
        }
    }

    record StepsBody(
            @NotNull @Size(max = Reorganization.MAX_STEPS) List<@Valid StepBody> steps) {}

    record DecisionBody(@Nullable @Size(max = 500) String note) {}

    record ReorganizationResponse(
            UUID id,
            UUID structureId,
            String description,
            LocalDate effectiveOn,
            String status,
            String createdBy,
            @Nullable String submittedBy,
            @Nullable String decidedBy,
            @Nullable String decisionNote,
            Instant createdAt,
            Instant updatedAt,
            long version) {

        static ReorganizationResponse from(Reorganization r) {
            return new ReorganizationResponse(
                    r.id(),
                    r.structureId(),
                    r.description(),
                    r.effectiveOn(),
                    r.status().name(),
                    r.createdBy(),
                    r.submittedBy(),
                    r.decidedBy(),
                    r.decisionNote(),
                    r.createdAt(),
                    r.updatedAt(),
                    r.version());
        }
    }

    record ReorganizationList(List<ReorganizationResponse> items) {}

    record SimulationResponse(
            boolean valid,
            @Nullable Integer failedStep,
            @Nullable String code,
            @Nullable String message,
            List<TreeNode> preview) {}

    @PostMapping
    ResponseEntity<ReorganizationResponse> create(@Valid @RequestBody CreateRequest body) {
        Reorganization r = service.create(body.structureId(), body.description(), body.effectiveOn());
        return ResponseEntity.created(URI.create("/v1/reorganizations/" + r.id()))
                .eTag(Versions.etag(r.version()))
                .body(ReorganizationResponse.from(r));
    }

    @GetMapping
    ReorganizationList list(
            @RequestParam @Nullable UUID structureId, @RequestParam @Nullable ReorganizationStatus status) {
        return new ReorganizationList(service.list(structureId, status).stream()
                .map(ReorganizationResponse::from)
                .toList());
    }

    @GetMapping("/{id}")
    ResponseEntity<ReorganizationResponse> get(@PathVariable UUID id) {
        return respond(service.get(id));
    }

    @PatchMapping(
            path = "/{id}",
            consumes = {"application/merge-patch+json", "application/json"})
    ResponseEntity<ReorganizationResponse> update(
            @PathVariable UUID id, @RequestHeader("If-Match") String ifMatch, @Valid @RequestBody UpdateRequest body) {
        return respond(service.edit(id, Versions.fromIfMatch(ifMatch), body.description(), body.effectiveOn()));
    }

    @GetMapping("/{id}/steps")
    StepsBody steps(@PathVariable UUID id) {
        return new StepsBody(service.steps(id).stream().map(StepBody::from).toList());
    }

    @PutMapping("/{id}/steps")
    ResponseEntity<ReorganizationResponse> replaceSteps(
            @PathVariable UUID id, @RequestHeader("If-Match") String ifMatch, @Valid @RequestBody StepsBody body) {
        List<Step> steps = body.steps().stream().map(StepBody::toStep).toList();
        return respond(service.replaceSteps(id, Versions.fromIfMatch(ifMatch), steps));
    }

    @PostMapping("/{id}/simulation")
    SimulationResponse simulate(@PathVariable UUID id) {
        var s = service.simulate(id);
        return new SimulationResponse(s.valid(), s.failedStep(), s.code(), s.message(), s.preview());
    }

    @PostMapping("/{id}/submit")
    ResponseEntity<ReorganizationResponse> submit(@PathVariable UUID id, @RequestHeader("If-Match") String ifMatch) {
        return respond(service.submit(id, Versions.fromIfMatch(ifMatch)));
    }

    @PostMapping("/{id}/approve")
    ResponseEntity<ReorganizationResponse> approve(
            @PathVariable UUID id,
            @RequestHeader("If-Match") String ifMatch,
            @Valid @RequestBody(required = false) @Nullable DecisionBody body) {
        return respond(service.approve(id, Versions.fromIfMatch(ifMatch), body == null ? null : body.note()));
    }

    @PostMapping("/{id}/reject")
    ResponseEntity<ReorganizationResponse> reject(
            @PathVariable UUID id,
            @RequestHeader("If-Match") String ifMatch,
            @Valid @RequestBody(required = false) @Nullable DecisionBody body) {
        return respond(service.reject(id, Versions.fromIfMatch(ifMatch), body == null ? null : body.note()));
    }

    @PostMapping("/{id}/cancel")
    ResponseEntity<ReorganizationResponse> cancel(@PathVariable UUID id, @RequestHeader("If-Match") String ifMatch) {
        return respond(service.cancel(id, Versions.fromIfMatch(ifMatch)));
    }

    private static ResponseEntity<ReorganizationResponse> respond(Reorganization r) {
        return ResponseEntity.ok().eTag(Versions.etag(r.version())).body(ReorganizationResponse.from(r));
    }
}
