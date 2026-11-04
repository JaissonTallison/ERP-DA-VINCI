package br.com.davinci.erp.organization.shared.tenancy;

import br.com.davinci.erp.platform.kernel.TenantId;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.jspecify.annotations.Nullable;

/**
 * Tenant do token: claim {@code organization} do realm davinci, com exatamente uma organização que tenha
 * {@code tenant_id} (ADR-0016). Qualquer outra forma é recusada.
 */
public final class TenantClaims {

    private TenantClaims() {}

    public static Optional<TenantId> tenantOf(@Nullable Object organizationClaim) {
        if (!(organizationClaim instanceof Map<?, ?> organizations)) {
            return Optional.empty();
        }
        List<TenantId> tenants = organizations.values().stream()
                .map(TenantClaims::tenantIdOf)
                .flatMap(Optional::stream)
                .toList();
        return tenants.size() == 1 ? Optional.of(tenants.getFirst()) : Optional.empty();
    }

    private static Optional<TenantId> tenantIdOf(@Nullable Object organization) {
        if (!(organization instanceof Map<?, ?> attributes)) {
            return Optional.empty();
        }
        Object value = attributes.get("tenant_id");
        Object first = value instanceof List<?> list && !list.isEmpty() ? list.getFirst() : value;
        if (!(first instanceof String raw)) {
            return Optional.empty();
        }
        try {
            return Optional.of(TenantId.parse(raw));
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }
    }
}
