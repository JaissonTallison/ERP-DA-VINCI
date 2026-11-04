package br.com.davinci.erp.governance.access.api;

import br.com.davinci.erp.governance.access.application.AccessService;
import br.com.davinci.erp.governance.access.domain.Assignment;
import br.com.davinci.erp.governance.access.domain.Role;
import br.com.davinci.erp.governance.access.domain.SodRule;
import br.com.davinci.erp.governance.shared.web.Versions;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.net.URI;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
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
class AccessController {

    private final AccessService service;

    AccessController(AccessService service) {
        this.service = service;
    }

    record CreateRoleRequest(
            @NotBlank @Size(max = 40) String code,
            @NotBlank @Size(max = 200) String name,
            @NotEmpty @Size(max = 500) Set<String> permissions) {}

    record UpdateRoleRequest(
            @Nullable @Size(max = 200) String name,
            @Nullable @Size(min = 1, max = 500) Set<String> permissions) {}

    record RoleResponse(
            UUID id,
            String code,
            String name,
            Set<String> permissions,
            String status,
            Instant createdAt,
            Instant updatedAt,
            long version) {

        static RoleResponse from(Role r) {
            return new RoleResponse(
                    r.id(),
                    r.code().value(),
                    r.name(),
                    new TreeSet<>(r.permissions()),
                    r.active() ? "ACTIVE" : "INACTIVE",
                    r.createdAt(),
                    r.updatedAt(),
                    r.version());
        }
    }

    record RoleList(List<RoleResponse> items) {}

    record GrantRequest(
            @NotBlank @Size(max = 200) String subject,
            @NotNull UUID roleId,
            @NotNull Assignment.ScopeType scopeType,
            @Nullable UUID scopeId,
            @Nullable LocalDate validFrom) {}

    record RevokeRequest(@Nullable LocalDate effectiveOn) {}

    record AssignmentResponse(
            UUID id,
            String subject,
            UUID roleId,
            String roleCode,
            Set<String> permissions,
            String scopeType,
            @Nullable UUID scopeId,
            LocalDate validFrom,
            @Nullable LocalDate validTo,
            String grantedBy) {

        static AssignmentResponse from(Assignment a, Role r) {
            return new AssignmentResponse(
                    a.id(),
                    a.subject(),
                    a.roleId(),
                    r.code().value(),
                    new TreeSet<>(r.permissions()),
                    a.scopeType().name(),
                    a.scopeId(),
                    a.period().validFrom(),
                    a.period().validTo(),
                    a.grantedBy());
        }
    }

    record AssignmentList(List<AssignmentResponse> items) {}

    record CreateSodRequest(
            @NotBlank @Size(max = 40) String code,
            @NotBlank @Size(max = 500) String description,
            @NotBlank String permissionA,
            @NotBlank String permissionB) {}

    record SodResponse(
            UUID id, String code, String description, String permissionA, String permissionB, boolean active) {

        static SodResponse from(SodRule s) {
            return new SodResponse(
                    s.id(), s.code().value(), s.description(), s.permissionA(), s.permissionB(), s.active());
        }
    }

    record SodList(List<SodResponse> items) {}

    @PostMapping("/v1/roles")
    ResponseEntity<RoleResponse> createRole(@Valid @RequestBody CreateRoleRequest body) {
        Role r = service.createRole(body.code(), body.name(), body.permissions());
        return ResponseEntity.created(URI.create("/v1/roles/" + r.id()))
                .eTag(Versions.etag(r.version()))
                .body(RoleResponse.from(r));
    }

    @GetMapping("/v1/roles")
    RoleList roles() {
        return new RoleList(service.roles().stream().map(RoleResponse::from).toList());
    }

    @GetMapping("/v1/roles/{id}")
    ResponseEntity<RoleResponse> role(@PathVariable UUID id) {
        return respond(service.role(id));
    }

    @PatchMapping(
            path = "/v1/roles/{id}",
            consumes = {"application/merge-patch+json", "application/json"})
    ResponseEntity<RoleResponse> updateRole(
            @PathVariable UUID id,
            @RequestHeader("If-Match") String ifMatch,
            @Valid @RequestBody UpdateRoleRequest body) {
        return respond(service.updateRole(id, Versions.fromIfMatch(ifMatch), body.name(), body.permissions()));
    }

    @PostMapping("/v1/roles/{id}/deactivate")
    ResponseEntity<RoleResponse> deactivateRole(@PathVariable UUID id) {
        return respond(service.deactivateRole(id));
    }

    @PostMapping("/v1/assignments")
    ResponseEntity<AssignmentResponse> grant(@Valid @RequestBody GrantRequest body) {
        Assignment a = service.grant(new AccessService.Grant(
                body.subject(), body.roleId(), body.scopeType(), body.scopeId(), body.validFrom()));
        return ResponseEntity.created(URI.create("/v1/assignments/" + a.id()))
                .body(AssignmentResponse.from(a, service.role(a.roleId())));
    }

    @PostMapping("/v1/assignments/{id}/revoke")
    AssignmentResponse revoke(@PathVariable UUID id, @RequestBody(required = false) @Nullable RevokeRequest body) {
        Assignment a = service.revoke(id, body == null ? null : body.effectiveOn());
        return AssignmentResponse.from(a, service.role(a.roleId()));
    }

    @GetMapping("/v1/assignments")
    AssignmentList assignments(@RequestParam @Nullable UUID roleId, @RequestParam @Nullable LocalDate activeOn) {
        return list(service.assignments(roleId, activeOn));
    }

    @GetMapping("/v1/subjects/{subject}/access")
    AssignmentList access(@PathVariable String subject, @RequestParam @Nullable LocalDate asOf) {
        return list(service.accessOf(subject, asOf));
    }

    @PostMapping("/v1/sod-rules")
    ResponseEntity<SodResponse> createSod(@Valid @RequestBody CreateSodRequest body) {
        SodRule s = service.createSod(body.code(), body.description(), body.permissionA(), body.permissionB());
        return ResponseEntity.created(URI.create("/v1/sod-rules/" + s.id())).body(SodResponse.from(s));
    }

    @GetMapping("/v1/sod-rules")
    SodList sodRules() {
        return new SodList(service.sodRules().stream().map(SodResponse::from).toList());
    }

    @PostMapping("/v1/sod-rules/{id}/deactivate")
    ResponseEntity<Void> deactivateSod(@PathVariable UUID id) {
        service.deactivateSod(id);
        return ResponseEntity.noContent().build();
    }

    private static AssignmentList list(List<AccessService.EffectiveGrant> grants) {
        return new AssignmentList(grants.stream()
                .map(g -> AssignmentResponse.from(g.assignment(), g.role()))
                .toList());
    }

    private static ResponseEntity<RoleResponse> respond(Role r) {
        return ResponseEntity.ok().eTag(Versions.etag(r.version())).body(RoleResponse.from(r));
    }
}
