package br.com.davinci.erp.tenantregistry.api;

import br.com.davinci.erp.platform.kernel.IsolationModel;
import br.com.davinci.erp.platform.kernel.RegionCode;
import br.com.davinci.erp.platform.kernel.TenantId;
import br.com.davinci.erp.tenantregistry.application.RegisterTenantCommand;
import br.com.davinci.erp.tenantregistry.application.TenantService;
import br.com.davinci.erp.tenantregistry.domain.Tenant;
import br.com.davinci.erp.tenantregistry.domain.TenantSlug;
import br.com.davinci.erp.tenantregistry.domain.TenantStatus;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import java.net.URI;
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
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/v1/tenants")
@Validated
class TenantController {

    private final TenantService service;

    TenantController(TenantService service) {
        this.service = service;
    }

    @PostMapping
    ResponseEntity<TenantResponse> register(
            @RequestHeader("Idempotency-Key") @Size(min = 1, max = 100) String idempotencyKey,
            @Valid @RequestBody Requests.RegisterTenant body) {
        Tenant tenant = service.register(new RegisterTenantCommand(
                idempotencyKey,
                new TenantSlug(Objects.requireNonNull(body.slug())),
                Objects.requireNonNull(body.displayName()),
                new RegionCode(Objects.requireNonNull(body.region())),
                IsolationModel.fromCode(Objects.requireNonNull(body.isolation()))));
        return ResponseEntity.created(URI.create("/v1/tenants/" + tenant.id())).body(TenantResponse.from(tenant));
    }

    @GetMapping
    TenantPageResponse list(
            @RequestParam @Nullable String region,
            @RequestParam @Nullable TenantStatus status,
            @RequestParam(defaultValue = "50") @Min(1) @Max(200) int limit,
            @RequestParam @Nullable String cursor) {
        // Busca um item a mais para saber se existe próxima página sem uma segunda consulta.
        List<Tenant> found = service.page(
                region == null ? null : new RegionCode(region),
                status,
                cursor == null ? null : Cursor.decode(cursor),
                limit + 1);
        List<Tenant> page = found.size() > limit ? found.subList(0, limit) : found;
        String next = found.size() > limit ? Cursor.encode(page.getLast().slug().value()) : null;
        return new TenantPageResponse(page.stream().map(TenantResponse::from).toList(), next);
    }

    @GetMapping("/{tenantId}")
    TenantResponse get(@PathVariable UUID tenantId) {
        return TenantResponse.from(service.get(new TenantId(tenantId)));
    }

    @PostMapping("/{tenantId}/activate")
    TenantResponse activate(@PathVariable UUID tenantId) {
        return TenantResponse.from(service.activate(new TenantId(tenantId)));
    }

    @PostMapping("/{tenantId}/suspend")
    TenantResponse suspend(@PathVariable UUID tenantId, @Valid @RequestBody Requests.SuspendTenant body) {
        return TenantResponse.from(service.suspend(new TenantId(tenantId), Objects.requireNonNull(body.reason())));
    }

    @PostMapping("/{tenantId}/reactivate")
    TenantResponse reactivate(@PathVariable UUID tenantId) {
        return TenantResponse.from(service.reactivate(new TenantId(tenantId)));
    }
}
