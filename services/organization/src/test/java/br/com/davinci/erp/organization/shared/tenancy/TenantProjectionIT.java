package br.com.davinci.erp.organization.shared.tenancy;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.davinci.erp.organization.support.IntegrationTest;
import br.com.davinci.erp.platform.kernel.TenantId;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.core.KafkaAdmin;
import org.springframework.kafka.core.KafkaTemplate;

class TenantProjectionIT extends IntegrationTest {

    @Autowired
    private KafkaTemplate<String, String> kafka;

    @Autowired
    private TenantProjection projection;

    @Autowired
    private KafkaAdmin admin;

    @Test
    void eventoAtrasadoOuRepetidoNaoVoltaOStatus() throws Exception {
        UUID tenant = activeTenant();
        suspendTenant(tenant);

        // Aplicação direta, determinística: versão antiga e versão repetida são descartadas.
        projection.apply(TenantId.parse(tenant.toString()), "ACTIVE", 1);
        projection.apply(TenantId.parse(tenant.toString()), "ACTIVE", 2);

        try (var c = superuser();
                var st = c.prepareStatement("SELECT status, version FROM tenant WHERE tenant_id = ?")) {
            st.setObject(1, tenant);
            try (var rs = st.executeQuery()) {
                assertThat(rs.next()).isTrue();
                assertThat(rs.getString(1)).isEqualTo("SUSPENDED");
                assertThat(rs.getLong(2)).isEqualTo(2);
            }
        }
    }

    @Test
    void mensagemForaDoContratoVaiParaADeadLetterDesteConsumidor() {
        // O serviço declara o tópico na subida (em produção a auto-criação está desligada no broker).
        assertThat(admin.describeTopics("tenant-registry.tenant.v1.organization-dlt"))
                .containsKey("tenant-registry.tenant.v1.organization-dlt");
        String key = UUID.randomUUID().toString();
        kafka.send("tenant-registry.tenant.v1", key, "não é json").join();

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
            consumer.subscribe(List.of("tenant-registry.tenant.v1.organization-dlt"));
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
}
