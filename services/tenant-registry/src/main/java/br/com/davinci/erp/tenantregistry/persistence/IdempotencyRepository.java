package br.com.davinci.erp.tenantregistry.persistence;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
public class IdempotencyRepository {

    private final JdbcClient jdbc;

    public IdempotencyRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    public Optional<Record> find(String operation, String key) {
        return jdbc.sql("SELECT request_hash, resource_id FROM idempotency_record"
                        + " WHERE operation = ? AND idempotency_key = ?")
                .params(operation, key)
                .query((rs, row) -> new Record(rs.getString("request_hash"), rs.getObject("resource_id", UUID.class)))
                .optional();
    }

    /** Grava a chave. Uma requisição concorrente com a mesma chave viola a chave primária e é desfeita. */
    public void insert(String operation, String key, String requestHash, UUID resourceId, Instant now) {
        jdbc.sql("INSERT INTO idempotency_record (operation, idempotency_key, request_hash, resource_id, created_at)"
                        + " VALUES (?, ?, ?, ?, ?)")
                .params(operation, key, requestHash, resourceId, TenantRepository.timestamp(now))
                .update();
    }

    public record Record(String requestHash, UUID resourceId) {}
}
