package br.com.davinci.erp.identity.sync;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import br.com.davinci.erp.identity.keycloak.KeycloakOrganizations;
import br.com.davinci.erp.identity.support.TenantEvents;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.web.client.RestClient;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.kafka.KafkaContainer;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.MountableFile;
import tools.jackson.databind.JsonNode;

/**
 * PostgreSQL, Kafka e Keycloak reais. O Keycloak sobe com o mesmo realm do ambiente local
 * (infra/local/keycloak/davinci-realm.json), o que também valida o realm e o menor privilégio da conta de serviço.
 */
@SpringBootTest(
        properties = {
            "identity.consumer.retry-interval=200ms",
            "identity.consumer.retry-attempts=1",
            "management.tracing.export.enabled=false",
            "management.otlp.metrics.export.enabled=false"
        })
class IdentitySyncIT {

    @ServiceConnection
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:18.6");

    @ServiceConnection
    static final KafkaContainer KAFKA = new KafkaContainer("apache/kafka:4.2.2");

    static final GenericContainer<?> KEYCLOAK = new GenericContainer<>("quay.io/keycloak/keycloak:26.8.1")
            .withCommand("start-dev", "--import-realm")
            .withEnv("JAVA_OPTS_KC_HEAP", "-Xms256m -Xmx768m")
            .withCopyFileToContainer(
                    MountableFile.forClasspathResource("davinci-realm.json"),
                    "/opt/keycloak/data/import/davinci-realm.json")
            .withExposedPorts(8080)
            .waitingFor(Wait.forHttp("/realms/davinci").forPort(8080).withStartupTimeout(Duration.ofMinutes(3)));

    static {
        POSTGRES.start();
        KAFKA.start();
        KEYCLOAK.start();
    }

    @DynamicPropertySource
    static void keycloak(DynamicPropertyRegistry registry) {
        String url = "http://" + KEYCLOAK.getHost() + ":" + KEYCLOAK.getMappedPort(8080);
        registry.add("identity.keycloak.base-url", () -> url);
        registry.add(
                "spring.security.oauth2.client.provider.keycloak.token-uri",
                () -> url + "/realms/davinci/protocol/openid-connect/token");
    }

    @Autowired
    private KafkaTemplate<String, String> kafka;

    @Autowired
    private JdbcClient jdbc;

    @MockitoSpyBean
    private KeycloakOrganizations organizations;

    @Test
    void tenantRegistradoViraOrganizacaoQueSoHabilitaQuandoAtivo() {
        UUID tenant = UUID.randomUUID();
        String slug = slug();

        send(tenant, TenantEvents.event(tenant, slug, "registered", "PROVISIONING", 0));
        JsonNode created = await(() -> organization(slug));
        assertThat(created.path("enabled").asBoolean()).isFalse();
        assertThat(created.path("description").asString()).isEqualTo("Empresa " + slug);
        assertThat(created.path("attributes").path("tenant_id").path(0).asString())
                .isEqualTo(tenant.toString());

        send(tenant, TenantEvents.event(tenant, slug, "activated", "ACTIVE", 1));
        await(() -> organization(slug).filter(org -> org.path("enabled").asBoolean()));

        send(tenant, TenantEvents.event(tenant, slug, "suspended", "SUSPENDED", 2));
        await(() -> organization(slug).filter(org -> !org.path("enabled").asBoolean()));
    }

    @Test
    void eventosRepetidosOuAtrasadosSaoDescartados() {
        UUID tenant = UUID.randomUUID();
        String slug = slug();

        send(tenant, TenantEvents.event(tenant, slug, "registered", "PROVISIONING", 0));
        send(tenant, TenantEvents.event(tenant, slug, "activated", "ACTIVE", 1));
        send(tenant, TenantEvents.event(tenant, slug, "activated", "ACTIVE", 1));
        send(tenant, TenantEvents.event(tenant, slug, "registered", "PROVISIONING", 0));
        send(tenant, TenantEvents.event(tenant, slug, "suspended", "SUSPENDED", 2));

        // Mesma chave = mesma partição: quando a versão 2 é aplicada, os eventos anteriores já foram processados.
        await(() -> appliedVersion(tenant).filter(version -> version == 2));
        verify(organizations, times(3)).upsert(argThat(spec -> spec.alias().equals(slug)));
        assertThat(organization(slug).orElseThrow().path("enabled").asBoolean()).isFalse();
    }

    @Test
    void mensagemForaDoContratoVaiParaDeadLetterSemTravarAFila() {
        String key = UUID.randomUUID().toString();
        kafka.send("tenant-registry.tenant.v1", key, "não é json");

        Map<String, Object> config = Map.of(
                ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG,
                KAFKA.getBootstrapServers(),
                ConsumerConfig.GROUP_ID_CONFIG,
                "teste-" + key,
                ConsumerConfig.AUTO_OFFSET_RESET_CONFIG,
                "earliest",
                ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG,
                StringDeserializer.class,
                ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG,
                StringDeserializer.class);
        try (KafkaConsumer<String, String> consumer = new KafkaConsumer<>(config)) {
            consumer.subscribe(List.of("tenant-registry.tenant.v1-dlt"));
            ConsumerRecord<String, String> dead = await(() -> {
                for (ConsumerRecord<String, String> record : consumer.poll(Duration.ofMillis(250))) {
                    if (key.equals(record.key())) {
                        return Optional.of(record);
                    }
                }
                return Optional.empty();
            });
            assertThat(dead.value()).isEqualTo("não é json");
        }
    }

    private void send(UUID tenant, String event) {
        kafka.send("tenant-registry.tenant.v1", tenant.toString(), event).join();
    }

    private Optional<Long> appliedVersion(UUID tenant) {
        return jdbc.sql("SELECT version FROM tenant_organization WHERE tenant_id = ?")
                .param(tenant)
                .query(Long.class)
                .optional();
    }

    private static String slug() {
        return "t" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
    }

    /** Consulta como a própria conta de serviço do Identity (só tem permissão sobre organizações). */
    private static Optional<JsonNode> organization(String alias) {
        String base = "http://" + KEYCLOAK.getHost() + ":" + KEYCLOAK.getMappedPort(8080);
        var form = new LinkedMultiValueMap<String, String>();
        form.add("grant_type", "client_credentials");
        form.add("client_id", "identity-service");
        form.add("client_secret", "identity_service_dev_secret");
        RestClient http = RestClient.create(base);
        JsonNode token = http.post()
                .uri("/realms/davinci/protocol/openid-connect/token")
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .body(form)
                .retrieve()
                .body(JsonNode.class);
        String bearer = "Bearer " + token.path("access_token").asString();
        JsonNode found = http.get()
                .uri("/admin/realms/davinci/organizations?search={alias}&exact=true", alias)
                .header("Authorization", bearer)
                .retrieve()
                .body(JsonNode.class);
        for (JsonNode org : found) {
            if (alias.equals(org.path("alias").asString())) {
                return Optional.ofNullable(http.get()
                        .uri(
                                "/admin/realms/davinci/organizations/{id}",
                                org.path("id").asString())
                        .header("Authorization", bearer)
                        .retrieve()
                        .body(JsonNode.class));
            }
        }
        return Optional.empty();
    }

    private static <T> T await(Supplier<Optional<T>> probe) {
        long deadline = System.nanoTime() + Duration.ofSeconds(30).toNanos();
        while (System.nanoTime() < deadline) {
            Optional<T> value = probe.get();
            if (value.isPresent()) {
                return value.get();
            }
            try {
                Thread.sleep(250);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException(e);
            }
        }
        throw new AssertionError("Condição não atingida em 30 s");
    }
}
