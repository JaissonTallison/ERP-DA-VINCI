package br.com.davinci.erp.organization.structure.api;

import br.com.davinci.erp.organization.structure.api.StructureRepresentations.CreateStructureRequest;
import br.com.davinci.erp.organization.structure.api.StructureRepresentations.MoveRequest;
import br.com.davinci.erp.organization.structure.api.StructureRepresentations.RemovalRequest;
import br.com.davinci.erp.organization.structure.api.StructureRepresentations.StructurePage;
import br.com.davinci.erp.organization.structure.api.StructureRepresentations.StructureResponse;
import br.com.davinci.erp.organization.structure.api.StructureRepresentations.TreeResponse;
import br.com.davinci.erp.organization.structure.api.StructureRepresentations.UnitResponse;
import br.com.davinci.erp.organization.structure.application.StructureCommands;
import br.com.davinci.erp.organization.structure.application.StructureService;
import br.com.davinci.erp.organization.structure.domain.Hierarchy;
import br.com.davinci.erp.organization.structure.domain.OrganizationalStructure;
import br.com.davinci.erp.organization.structure.persistence.TreeNode;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.net.URI;
import java.time.LocalDate;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Estruturas e leituras da árvore por data. Sem {@code asOf}, vale hoje (UTC). */
@RestController
@RequestMapping("/v1/structures")
@Validated
class StructureController {

    static final int MAX_NODES = 50_000;

    private final StructureService service;

    StructureController(StructureService service) {
        this.service = service;
    }

    @PostMapping
    ResponseEntity<StructureResponse> create(@Valid @RequestBody CreateStructureRequest body) {
        OrganizationalStructure structure = service.create(new StructureCommands.CreateStructure(
                Objects.requireNonNull(body.code()),
                StructureRepresentations.text(body.name(), body.nameTranslations()),
                Objects.requireNonNull(body.purpose())));
        return ResponseEntity.created(URI.create("/v1/structures/" + structure.id()))
                .body(StructureResponse.from(structure));
    }

    @GetMapping
    StructurePage list() {
        return new StructurePage(
                service.list().stream().map(StructureResponse::from).toList());
    }

    @GetMapping("/{id}")
    StructureResponse get(@PathVariable UUID id) {
        return StructureResponse.from(service.get(id));
    }

    @PostMapping("/{id}/moves")
    UnitResponse move(@PathVariable UUID id, @Valid @RequestBody MoveRequest body) {
        return UnitResponse.from(service.move(
                id,
                new StructureCommands.Move(
                        Objects.requireNonNull(body.unitId()),
                        body.parentUnitId(),
                        Objects.requireNonNull(body.effectiveOn()))));
    }

    @PostMapping("/{id}/removals")
    UnitResponse remove(@PathVariable UUID id, @Valid @RequestBody RemovalRequest body) {
        return UnitResponse.from(
                service.remove(id, Objects.requireNonNull(body.unitId()), Objects.requireNonNull(body.effectiveOn())));
    }

    @GetMapping("/{id}/roots")
    TreeResponse roots(@PathVariable UUID id, @RequestParam @Nullable LocalDate asOf) {
        LocalDate date = service.date(asOf);
        return TreeResponse.of(date, service.roots(id, date), false);
    }

    @GetMapping("/{id}/units/{unitId}/children")
    TreeResponse children(@PathVariable UUID id, @PathVariable UUID unitId, @RequestParam @Nullable LocalDate asOf) {
        LocalDate date = service.date(asOf);
        return TreeResponse.of(date, service.children(id, unitId, date), false);
    }

    @GetMapping("/{id}/units/{unitId}/subtree")
    TreeResponse subtree(
            @PathVariable UUID id,
            @PathVariable UUID unitId,
            @RequestParam @Nullable LocalDate asOf,
            @RequestParam(defaultValue = "64") @Min(0) @Max(Hierarchy.MAX_DEPTH) int maxDepth,
            @RequestParam(defaultValue = "10000") @Min(1) @Max(MAX_NODES) int limit) {
        LocalDate date = service.date(asOf);
        List<TreeNode> nodes = service.subtree(id, unitId, date, maxDepth, limit + 1);
        boolean truncated = nodes.size() > limit;
        return TreeResponse.of(date, truncated ? nodes.subList(0, limit) : nodes, truncated);
    }

    @GetMapping("/{id}/units/{unitId}/ancestors")
    TreeResponse ancestors(@PathVariable UUID id, @PathVariable UUID unitId, @RequestParam @Nullable LocalDate asOf) {
        LocalDate date = service.date(asOf);
        return TreeResponse.of(date, service.ancestors(id, unitId, date), false);
    }
}
