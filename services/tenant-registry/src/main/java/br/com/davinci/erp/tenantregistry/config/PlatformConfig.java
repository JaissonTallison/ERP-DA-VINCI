package br.com.davinci.erp.tenantregistry.config;

import br.com.davinci.erp.tenantregistry.events.OutboxProperties;
import br.com.davinci.erp.tenantregistry.events.TenantEventPublisher;
import java.time.Clock;
import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.kafka.config.TopicBuilder;

@Configuration(proxyBeanMethods = false)
class PlatformConfig {

    /** Estático: roda antes dos demais beans (e da conexão com o banco). */
    @Bean
    static RequiredSettings requiredSettings(Environment environment) {
        return new RequiredSettings(environment);
    }

    @Bean
    Clock clock() {
        return Clock.systemUTC();
    }

    /** Tópico do contrato de eventos. A auto-criação está desligada no broker; o dono do tópico o declara. */
    @Bean
    NewTopic tenantLifecycleTopic(OutboxProperties outbox) {
        return TopicBuilder.name(TenantEventPublisher.TOPIC)
                .partitions(outbox.topicPartitions())
                .replicas(outbox.topicReplicas())
                .build();
    }
}
