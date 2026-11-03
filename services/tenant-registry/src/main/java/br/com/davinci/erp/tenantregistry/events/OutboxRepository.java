package br.com.davinci.erp.tenantregistry.events;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
public class OutboxRepository {

    private final JdbcClient jdbc;

    public OutboxRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    public void append(UUID eventId, String topic, String key, String payload, Instant now) {
        jdbc.sql("INSERT INTO outbox_event (event_id, topic, message_key, payload, created_at)"
                        + " VALUES (?, ?, ?, CAST(? AS jsonb), ?)")
                .params(eventId, topic, key, payload, at(now))
                .update();
    }

    /**
     * Próximos eventos pendentes, em ordem, travados para esta transação. {@code SKIP LOCKED} permite várias
     * instâncias do relay sem publicar o mesmo lote em paralelo.
     */
    public List<PendingEvent> lockPending(int limit) {
        return jdbc.sql("""
                        SELECT sequence, topic, message_key, payload::text AS payload
                        FROM outbox_event
                        WHERE published_at IS NULL
                        ORDER BY sequence
                        LIMIT ?
                        FOR UPDATE SKIP LOCKED
                        """)
                .param(limit)
                .query((rs, row) -> new PendingEvent(
                        rs.getLong("sequence"),
                        rs.getString("topic"),
                        rs.getString("message_key"),
                        rs.getString("payload")))
                .list();
    }

    public void markPublished(long sequence, Instant now) {
        jdbc.sql("UPDATE outbox_event SET published_at = ? WHERE sequence = ?")
                .params(at(now), sequence)
                .update();
    }

    public long countPending() {
        return jdbc.sql("SELECT count(*) FROM outbox_event WHERE published_at IS NULL")
                .query(Long.class)
                .single();
    }

    public int deletePublishedBefore(Instant cutoff) {
        return jdbc.sql("DELETE FROM outbox_event WHERE published_at < ?")
                .param(at(cutoff))
                .update();
    }

    private static OffsetDateTime at(Instant instant) {
        return OffsetDateTime.ofInstant(instant, ZoneOffset.UTC);
    }

    public record PendingEvent(long sequence, String topic, String key, String payload) {}
}
