package br.com.davinci.erp.organization.shared.persistence;

import br.com.davinci.erp.platform.kernel.TenantId;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/** Chaves de idempotência de criação, por tenant (RLS). Requisição concorrente com a mesma chave perde na PK. */
@Repository
public class IdempotencyRepository {

    private final JdbcClient jdbc;

    public IdempotencyRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    public Optional<Entry> find(String operation, String key) {
        return jdbc.sql(
                        "SELECT request_hash, resource_id FROM idempotency_record WHERE operation = ? AND idempotency_key = ?")
                .params(operation, key)
                .query((rs, row) -> new Entry(rs.getString("request_hash"), rs.getObject("resource_id", UUID.class)))
                .optional();
    }

    public void insert(
            TenantId tenant, String operation, String key, String requestHash, UUID resourceId, Instant now) {
        jdbc.sql(
                        "INSERT INTO idempotency_record (tenant_id, operation, idempotency_key, request_hash, resource_id, created_at)"
                                + " VALUES (?, ?, ?, ?, ?, ?)")
                .params(
                        tenant.value(),
                        operation,
                        key,
                        requestHash,
                        resourceId,
                        OffsetDateTime.ofInstant(now, ZoneOffset.UTC))
                .update();
    }

    public record Entry(String requestHash, UUID resourceId) {}
}
