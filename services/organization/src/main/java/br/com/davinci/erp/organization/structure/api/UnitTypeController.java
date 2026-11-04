package br.com.davinci.erp.organization.structure.api;

import br.com.davinci.erp.organization.structure.api.StructureRepresentations.CreateUnitTypeRequest;
import br.com.davinci.erp.organization.structure.api.StructureRepresentations.NestingRequest;
import br.com.davinci.erp.organization.structure.api.StructureRepresentations.NestingResponse;
import br.com.davinci.erp.organization.structure.api.StructureRepresentations.UnitTypePage;
import br.com.davinci.erp.organization.structure.api.StructureRepresentations.UnitTypeResponse;
import br.com.davinci.erp.organization.structure.application.StructureCommands;
import br.com.davinci.erp.organization.structure.application.UnitTypeService;
import br.com.davinci.erp.organization.structure.domain.UnitType;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.Objects;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/v1/unit-types")
class UnitTypeController {

    private final UnitTypeService service;

    UnitTypeController(UnitTypeService service) {
        this.service = service;
    }

    @PostMapping
    ResponseEntity<UnitTypeResponse> create(@Valid @RequestBody CreateUnitTypeRequest body) {
        UnitType type = service.create(new StructureCommands.CreateUnitType(
                Objects.requireNonNull(body.code()),
                StructureRepresentations.text(body.name(), body.nameTranslations())));
        return ResponseEntity.created(URI.create("/v1/unit-types/" + type.id())).body(UnitTypeResponse.from(type));
    }

    @GetMapping
    UnitTypePage list() {
        return new UnitTypePage(
                service.list().stream().map(UnitTypeResponse::from).toList());
    }

    @GetMapping("/{id}/nesting")
    NestingResponse nesting(@PathVariable UUID id) {
        return new NestingResponse(id, service.allowedParents(id));
    }

    @PutMapping("/{id}/nesting")
    NestingResponse setNesting(@PathVariable UUID id, @Valid @RequestBody NestingRequest body) {
        return new NestingResponse(
                id,
                service.setNesting(
                        id, new StructureCommands.SetNesting(Objects.requireNonNull(body.allowedParentTypeIds()))));
    }
}
