package br.com.davinci.erp.identity.sync;

import br.com.davinci.erp.identity.tenant.TenantEvent;
import br.com.davinci.erp.platform.kernel.TenantId;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.OptionalLong;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
class TenantOrganizationRepository {

    private final JdbcClient jdbc;

    TenantOrganizationRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    /** Versão já aplicada do tenant, travando a linha até o fim da transação. */
    OptionalLong lockAppliedVersion(TenantId tenantId) {
        return jdbc.sql("SELECT version FROM tenant_organization WHERE tenant_id = ? FOR UPDATE")
                .param(tenantId.value())
                .query(Long.class)
                .optional()
                .map(OptionalLong::of)
                .orElseGet(OptionalLong::empty);
    }

    void save(TenantEvent event, String organizationId, Instant now) {
        jdbc.sql("""
                        INSERT INTO tenant_organization (tenant_id, organization_id, alias, status, version, updated_at)
                        VALUES (?, ?, ?, ?, ?, ?)
                        ON CONFLICT (tenant_id) DO UPDATE SET
                            organization_id = EXCLUDED.organization_id,
                            status = EXCLUDED.status,
                            version = EXCLUDED.version,
                            updated_at = EXCLUDED.updated_at
                        """)
                .params(
                        event.tenantId().value(),
                        organizationId,
                        event.slug(),
                        event.status().name(),
                        event.version(),
                        OffsetDateTime.ofInstant(now, ZoneOffset.UTC))
                .update();
    }
}
