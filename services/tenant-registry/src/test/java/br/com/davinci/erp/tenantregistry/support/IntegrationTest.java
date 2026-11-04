package br.com.davinci.erp.tenantregistry.support;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.JwtRequestPostProcessor;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.kafka.KafkaContainer;
import org.testcontainers.postgresql.PostgreSQLContainer;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Base dos testes de integração: PostgreSQL e Kafka reais (mesmas versões do compose.yaml), um único par de
 * contêineres para toda a suíte. Cada teste usa região e slug próprios para não depender dos demais.
 */
@SpringBootTest(
        properties = {
            "tenant-registry.outbox.poll-interval=100",
            "management.tracing.export.enabled=false",
            "management.otlp.metrics.export.enabled=false"
        })
@ActiveProfiles("local")
@AutoConfigureMockMvc
public abstract class IntegrationTest {

    @ServiceConnection
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:18.6");

    @ServiceConnection
    static final KafkaContainer KAFKA = new KafkaContainer("apache/kafka:4.2.2");

    static {
        POSTGRES.start();
        KAFKA.start();
    }

    private static final AtomicInteger SEQUENCE = new AtomicInteger();

    @Autowired
    protected MockMvc mvc;

    @Autowired
    protected JsonMapper json;

    @Autowired
    private JdbcClient jdbc;

    protected static JwtRequestPostProcessor writer() {
        return jwt().authorities(new SimpleGrantedAuthority("SCOPE_tenant-registry.write"));
    }

    protected static JwtRequestPostProcessor reader() {
        return jwt().authorities(new SimpleGrantedAuthority("SCOPE_tenant-registry.read"));
    }

    /** Região exclusiva do teste (formato do RegionCode: duas letras, hífen, número). */
    protected static String uniqueRegion() {
        int n = SEQUENCE.getAndIncrement();
        return "q" + (char) ('a' + n % 26) + "-" + (1 + n / 26);
    }

    protected static String uniqueSlug() {
        return "t" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
    }

    /** Eventos gravados no outbox para a chave (publicados ou não). */
    protected long outboxCount(String key) {
        return jdbc.sql("SELECT count(*) FROM outbox_event WHERE message_key = ?")
                .param(key)
                .query(Long.class)
                .single();
    }

    protected JsonNode read(String body) {
        return json.readTree(body);
    }

    /** Eventos publicados no tópico para a chave informada, aguardando até {@code expected} chegarem. */
    protected List<JsonNode> eventsFor(String key, int expected) {
        Map<String, Object> config = Map.of(
                ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG,
                KAFKA.getBootstrapServers(),
                ConsumerConfig.GROUP_ID_CONFIG,
                "test-" + UUID.randomUUID(),
                ConsumerConfig.AUTO_OFFSET_RESET_CONFIG,
                "earliest",
                ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG,
                StringDeserializer.class,
                ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG,
                StringDeserializer.class);
        List<JsonNode> found = new ArrayList<>();
        try (KafkaConsumer<String, String> consumer = new KafkaConsumer<>(config)) {
            consumer.subscribe(List.of("tenant-registry.tenant.v1"));
            long deadline = System.nanoTime() + Duration.ofSeconds(20).toNanos();
            while (found.size() < expected && System.nanoTime() < deadline) {
                for (ConsumerRecord<String, String> record : consumer.poll(Duration.ofMillis(250))) {
                    if (key.equals(record.key())) {
                        Contracts.assertMatchesEventSchema(record.value());
                        found.add(json.readTree(record.value()));
                    }
                }
            }
        }
        return found;
    }
}
