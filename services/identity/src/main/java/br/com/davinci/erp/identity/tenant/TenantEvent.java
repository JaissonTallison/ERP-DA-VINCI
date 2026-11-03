package br.com.davinci.erp.identity.tenant;

import br.com.davinci.erp.platform.kernel.CellId;
import br.com.davinci.erp.platform.kernel.TenantId;
import java.util.UUID;

/** Evento de ciclo de vida de tenant, já validado, com o estado completo do tenant após o fato. */
public record TenantEvent(
        UUID eventId,
        String type,
        TenantId tenantId,
        String slug,
        String displayName,
        String region,
        CellId cellId,
        String isolation,
        TenantStatus status,
        long version) {

    public enum TenantStatus {
        PROVISIONING,
        ACTIVE,
        SUSPENDED
    }
}
