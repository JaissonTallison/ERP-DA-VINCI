package br.com.davinci.erp.organization.support;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.JwtRequestPostProcessor;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.kafka.KafkaContainer;
import org.testcontainers.postgresql.PostgreSQLContainer;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * PostgreSQL e Kafka reais. A aplicação conecta como {@code organization_app} (sem privilégio de dono, sujeita ao
 * RLS); as migrações rodam como dono. Cada teste usa tenants próprios, ativados por eventos do Tenant Registry.
 */
@SpringBootTest(
        properties = {
            "organization.outbox.poll-interval=100",
            "organization.consumer.retry-interval=100ms",
            "organization.consumer.retry-attempts=1",
            "management.tracing.export.enabled=false",
            "management.otlp.metrics.export.enabled=false"
        })
@AutoConfigureMockMvc
@ActiveProfiles("local")
public abstract class IntegrationTest {

    protected static final String APP_PASSWORD = "organization_app_test";

    protected static final PostgreSQLContainer POSTGRES =
            new PostgreSQLContainer("postgres:18.6").withInitScript("organization-test-roles.sql");

    @ServiceConnection
    protected static final KafkaContainer KAFKA = new KafkaContainer("apache/kafka:4.2.2");

    static {
        POSTGRES.start();
        KAFKA.start();
    }

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", () -> "organization_app");
        registry.add("spring.datasource.password", () -> APP_PASSWORD);
        registry.add("spring.flyway.user", POSTGRES::getUsername);
        registry.add("spring.flyway.password", POSTGRES::getPassword);
    }

    @Autowired
    protected MockMvc mvc;

    @Autowired
    protected JsonMapper json;

    @Autowired
    private KafkaTemplate<String, String> kafka;

    protected static JwtRequestPostProcessor admin(UUID tenant) {
        return token(tenant, "tenant-admin");
    }

    /** Administrador com outro {@code sub}, para regras de segregação de funções. */
    protected static JwtRequestPostProcessor secondAdmin(UUID tenant) {
        JwtAuthenticationToken authentication = authentication(tenant, "tenant-admin", "outro-admin");
        return jwt().jwt(authentication.getToken()).authorities(authentication.getAuthorities());
    }

    protected static JwtRequestPostProcessor user(UUID tenant) {
        return token(tenant, "tenant-user");
    }

    private static JwtRequestPostProcessor token(UUID tenant, String role) {
        JwtAuthenticationToken authentication = authentication(tenant, role);
        return jwt().jwt(authentication.getToken()).authorities(authentication.getAuthorities());
    }

    /** O mesmo usuário de {@link #user(UUID)}, para chamadas diretas aos beans, sem passar pelo MockMvc. */
    protected static JwtAuthenticationToken authentication(UUID tenant, String role) {
        return authentication(tenant, role, "user-" + role);
    }

    private static JwtAuthenticationToken authentication(UUID tenant, String role, String subject) {
        Jwt jwt = Jwt.withTokenValue("teste")
                .header("alg", "none")
                .subject(subject)
                .claim(
                        "organization",
                        Map.of(
                                "t" + tenant.toString().substring(0, 8),
                                Map.of("id", UUID.randomUUID().toString(), "tenant_id", List.of(tenant.toString()))))
                .build();
        return new JwtAuthenticationToken(jwt, List.of(new SimpleGrantedAuthority("ROLE_" + role)));
    }

    /** Novo tenant ativo, recebido pelo consumidor como num ambiente real. */
    protected UUID activeTenant() {
        UUID tenant = UUID.randomUUID();
        publishTenant(tenant, "registered", "PROVISIONING", 0);
        publishTenant(tenant, "activated", "ACTIVE", 1);
        await(() -> tenantStatus(tenant).filter("ACTIVE"::equals));
        return tenant;
    }

    protected void suspendTenant(UUID tenant) {
        publishTenant(tenant, "suspended", "SUSPENDED", 2);
        await(() -> tenantStatus(tenant).filter("SUSPENDED"::equals));
    }

    protected void publishTenant(UUID tenant, String fact, String status, long version) {
        String event = """
                {"specversion":"1.0","id":"%s","source":"/control-plane/tenant-registry",
                 "type":"br.com.davinci.tenantregistry.tenant.%s.v1","time":"2026-11-03T12:00:00Z",
                 "datacontenttype":"application/json","dataschema":"urn:davinci:contracts:events:tenant-registry:v1:tenant-event",
                 "subject":"%s","tenantid":"%s","cellid":"br-1-pooled-01","correlationid":"teste",
                 "data":{"tenantId":"%s","slug":"t%s","displayName":"Teste","region":"br-1","isolation":"pooled",
                         "cellId":"br-1-pooled-01","status":"%s","statusReason":null,"version":%d}}
                """.formatted(
                        UUID.randomUUID(),
                        fact,
                        tenant,
                        tenant,
                        tenant,
                        tenant.toString().substring(0, 8),
                        status,
                        version);
        kafka.send("tenant-registry.tenant.v1", tenant.toString(), event).join();
    }

    /** Conexão de superusuário (ignora RLS): só para conferir o estado nos testes. */
    protected static Connection superuser() throws SQLException {
        return DriverManager.getConnection(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
    }

    /** Conexão com o papel da aplicação (sujeita ao RLS), para provar o isolamento direto no banco. */
    protected static Connection appRole() throws SQLException {
        return DriverManager.getConnection(POSTGRES.getJdbcUrl(), "organization_app", APP_PASSWORD);
    }

    private static Optional<String> tenantStatus(UUID tenant) {
        try (Connection c = superuser();
                var st = c.prepareStatement("SELECT status FROM tenant WHERE tenant_id = ?")) {
            st.setObject(1, tenant);
            try (var rs = st.executeQuery()) {
                return rs.next() ? Optional.of(rs.getString(1)) : Optional.empty();
            }
        } catch (SQLException e) {
            throw new IllegalStateException(e);
        }
    }

    /** Eventos do tópico com a chave dada, validados contra o contrato, até chegarem {@code expected}. */
    protected List<JsonNode> events(String stream, UUID key, int expected) {
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
            consumer.subscribe(List.of("organization." + stream + ".v1"));
            long deadline = System.nanoTime() + Duration.ofSeconds(20).toNanos();
            while (found.size() < expected && System.nanoTime() < deadline) {
                for (ConsumerRecord<String, String> record : consumer.poll(Duration.ofMillis(250))) {
                    if (key.toString().equals(record.key())) {
                        Contracts.assertEvent(stream, record.value());
                        found.add(json.readTree(record.value()));
                    }
                }
            }
        }
        return found;
    }

    protected static <T> T await(Supplier<Optional<T>> probe) {
        long deadline = System.nanoTime() + Duration.ofSeconds(30).toNanos();
        while (System.nanoTime() < deadline) {
            Optional<T> value = probe.get();
            if (value.isPresent()) {
                return value.get();
            }
            try {
                Thread.sleep(200);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException(e);
            }
        }
        throw new AssertionError("Condição não atingida em 30 s");
    }

    protected JsonNode read(String body) {
        return json.readTree(body);
    }
}
