package br.com.davinci.erp.organization.shared.persistence;

import br.com.davinci.erp.organization.shared.events.EventPublisher;
import br.com.davinci.erp.organization.shared.tenancy.CurrentUser;
import br.com.davinci.erp.platform.kernel.TenantId;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.slf4j.MDC;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/** Trilha de auditoria append-only: quem, quando, de onde (correlação) e o estado antes e depois. */
@Component
public class AuditLog {

    private final JdbcClient jdbc;
    private final CurrentUser user;
    private final JsonMapper json;
    private final Clock clock;

    public AuditLog(JdbcClient jdbc, CurrentUser user, JsonMapper json, Clock clock) {
        this.jdbc = jdbc;
        this.user = user;
        this.json = json;
        this.clock = clock;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void record(
            TenantId tenant,
            String aggregateType,
            UUID aggregateId,
            String action,
            @Nullable JsonNode before,
            JsonNode after) {
        String correlation = MDC.get(EventPublisher.CORRELATION_MDC_KEY);
        jdbc.sql("""
                        INSERT INTO audit_log (tenant_id, aggregate_type, aggregate_id, action, actor, correlation_id,
                                               occurred_at, before, after)
                        VALUES (?, ?, ?, ?, ?, ?, ?, CAST(? AS jsonb), CAST(? AS jsonb))
                        """)
                .params(
                        tenant.value(),
                        aggregateType,
                        aggregateId,
                        action,
                        user.actor(),
                        correlation == null ? "-" : correlation,
                        OffsetDateTime.ofInstant(clock.instant(), ZoneOffset.UTC),
                        before == null ? null : json.writeValueAsString(before),
                        json.writeValueAsString(after))
                .update();
    }
}
