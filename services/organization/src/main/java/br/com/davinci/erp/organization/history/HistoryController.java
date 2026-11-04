package br.com.davinci.erp.organization.history;

import br.com.davinci.erp.organization.shared.domain.InvalidValueException;
import br.com.davinci.erp.organization.shared.security.Permission;
import br.com.davinci.erp.organization.shared.security.PolicyDecisionPoint;
import br.com.davinci.erp.organization.shared.tenancy.TenantScope;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Histórico (etapa 8): {@code GET /v1/history/{tipo}/{id}} lista as mudanças de um registro; {@code GET /v1/changes}
 * lista as mudanças do tenant num período. Fonte: {@code audit_log}, que só aceita inserção.
 */
@RestController
@Validated
class HistoryController {

    private static final String COLUMNS = """
            id, aggregate_type, aggregate_id, action, actor, correlation_id, occurred_at, before::text AS before,
            after::text AS after""";

    private final JdbcClient jdbc;
    private final JsonMapper json;
    private final TenantScope scope;
    private final PolicyDecisionPoint policy;

    HistoryController(JdbcClient jdbc, JsonMapper json, TenantScope scope, PolicyDecisionPoint policy) {
        this.jdbc = jdbc;
        this.json = json;
        this.scope = scope;
        this.policy = policy;
    }

    record Entry(
            long sequence,
            String aggregateType,
            UUID aggregateId,
            String action,
            String actor,
            String correlationId,
            Instant occurredAt,
            @Nullable JsonNode before,
            @Nullable JsonNode after) {}

    record EntryPage(List<Entry> items, @Nullable Long nextAfter) {}

    @GetMapping("/v1/history/{aggregateType}/{id}")
    @Transactional(readOnly = true)
    public EntryPage history(@PathVariable String aggregateType, @PathVariable UUID id) {
        scope.read();
        policy.require(Permission.HISTORY_READ);
        List<Entry> items = jdbc.sql("SELECT " + COLUMNS + " FROM audit_log"
                        + " WHERE aggregate_type = ? AND aggregate_id = ? ORDER BY id LIMIT 1000")
                .params(aggregateType, id)
                .query(this::entry)
                .list();
        return new EntryPage(items, null);
    }

    @GetMapping("/v1/changes")
    @Transactional(readOnly = true)
    public EntryPage changes(
            @RequestParam OffsetDateTime from,
            @RequestParam OffsetDateTime to,
            @RequestParam @Nullable String aggregateType,
            @RequestParam @Nullable Long after,
            @RequestParam(defaultValue = "100") @Min(1) @Max(1000) int limit) {
        if (!to.isAfter(from)) {
            throw new InvalidValueException("to", "O fim do período deve ser depois do início");
        }
        scope.read();
        policy.require(Permission.HISTORY_READ);
        List<Entry> items = jdbc.sql("SELECT " + COLUMNS + " FROM audit_log"
                        + " WHERE occurred_at >= :from AND occurred_at < :to"
                        + " AND (CAST(:type AS text) IS NULL OR aggregate_type = :type)"
                        + " AND id > :after ORDER BY id LIMIT :limit")
                .param("from", from)
                .param("to", to)
                .param("type", aggregateType)
                .param("after", after == null ? 0L : after)
                .param("limit", limit + 1)
                .query(this::entry)
                .list();
        boolean more = items.size() > limit;
        List<Entry> page = more ? items.subList(0, limit) : items;
        return new EntryPage(page, more ? page.getLast().sequence() : null);
    }

    private Entry entry(ResultSet rs, int row) throws SQLException {
        String before = rs.getString("before");
        String after = rs.getString("after");
        return new Entry(
                rs.getLong("id"),
                rs.getString("aggregate_type"),
                rs.getObject("aggregate_id", UUID.class),
                rs.getString("action"),
                rs.getString("actor"),
                rs.getString("correlation_id"),
                rs.getObject("occurred_at", OffsetDateTime.class).toInstant(),
                before == null ? null : json.readTree(before),
                after == null ? null : json.readTree(after));
    }
}
