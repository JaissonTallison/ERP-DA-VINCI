package br.com.davinci.erp.organization.shared.tenancy;

import br.com.davinci.erp.platform.kernel.TenantId;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.OptionalLong;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** Cópia local do status do tenant, alimentada pelos eventos do Tenant Registry (só a versão mais nova vale). */
@Component
public class TenantProjection {

    private static final Logger log = LoggerFactory.getLogger(TenantProjection.class);

    private final JdbcClient jdbc;
    private final TenantSession session;
    private final Clock clock;

    public TenantProjection(JdbcClient jdbc, TenantSession session, Clock clock) {
        this.jdbc = jdbc;
        this.session = session;
        this.clock = clock;
    }

    @Transactional
    public void apply(TenantId tenant, String status, long version) {
        session.bind(tenant);
        OptionalLong applied = jdbc.sql("SELECT version FROM tenant WHERE tenant_id = ? FOR UPDATE")
                .param(tenant.value())
                .query(Long.class)
                .optional()
                .map(OptionalLong::of)
                .orElseGet(OptionalLong::empty);
        if (applied.isPresent() && applied.getAsLong() >= version) {
            return;
        }
        jdbc.sql("""
                        INSERT INTO tenant (tenant_id, status, version, updated_at) VALUES (?, ?, ?, ?)
                        ON CONFLICT (tenant_id) DO UPDATE
                           SET status = EXCLUDED.status, version = EXCLUDED.version, updated_at = EXCLUDED.updated_at
                        """)
                .params(tenant.value(), status, version, OffsetDateTime.ofInstant(clock.instant(), ZoneOffset.UTC))
                .update();
        log.info("Tenant {} → {} (versão {})", tenant, status, version);
    }
}
