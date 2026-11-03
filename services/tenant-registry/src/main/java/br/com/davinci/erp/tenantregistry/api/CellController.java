package br.com.davinci.erp.tenantregistry.api;

import br.com.davinci.erp.platform.kernel.CellId;
import br.com.davinci.erp.platform.kernel.IsolationModel;
import br.com.davinci.erp.platform.kernel.RegionCode;
import br.com.davinci.erp.tenantregistry.application.CellService;
import br.com.davinci.erp.tenantregistry.domain.Cell;
import jakarta.validation.Valid;
import java.util.Objects;
import org.jspecify.annotations.Nullable;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/v1/cells")
class CellController {

    private final CellService service;

    CellController(CellService service) {
        this.service = service;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    CellResponse register(@Valid @RequestBody Requests.RegisterCell body) {
        CellId id = new CellId(
                new RegionCode(Objects.requireNonNull(body.region())),
                IsolationModel.fromCode(Objects.requireNonNull(body.isolation())),
                Objects.requireNonNull(body.name()));
        Cell cell = service.register(id, Objects.requireNonNull(body.capacity()));
        return CellResponse.from(cell);
    }

    @GetMapping
    CellResponse.Page list(@RequestParam @Nullable String region) {
        return new CellResponse.Page(service.list(region == null ? null : new RegionCode(region)).stream()
                .map(CellResponse::from)
                .toList());
    }
}
