package br.com.davinci.erp.tenantregistry.api;

import br.com.davinci.erp.tenantregistry.domain.Tenant;
import java.time.Instant;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/** Representação v1 do tenant. Contrato: components.schemas.Tenant. */
public record TenantResponse(
        UUID id,
        String slug,
        String displayName,
        String region,
        String isolation,
        String cellId,
        String status,
        @Nullable String statusReason,
        Instant createdAt,
        Instant updatedAt,
        long version) {

    static TenantResponse from(Tenant tenant) {
        return new TenantResponse(
                tenant.id().value(),
                tenant.slug().value(),
                tenant.displayName(),
                tenant.region().value(),
                tenant.isolation().code(),
                tenant.cellId().toString(),
                tenant.status().name(),
                tenant.statusReason(),
                tenant.createdAt(),
                tenant.updatedAt(),
                tenant.version());
    }
}
