package br.com.davinci.erp.organization.shared.config;

import br.com.davinci.erp.organization.shared.events.EventPublisher;
import br.com.davinci.erp.organization.shared.events.OutboxProperties;
import br.com.davinci.erp.organization.shared.tenancy.InvalidTenantEventException;
import java.time.Clock;
import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.common.TopicPartition;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.kafka.config.TopicBuilder;
import org.springframework.kafka.core.KafkaOperations;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.util.backoff.FixedBackOff;

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

    /** Tópicos dos contratos de eventos; a auto-criação está desligada no broker e o produtor os declara. */
    @Bean
    NewTopic economicGroupTopic(OutboxProperties outbox) {
        return topic(EventPublisher.topic("economic-group"), outbox);
    }

    @Bean
    NewTopic legalEntityTopic(OutboxProperties outbox) {
        return topic(EventPublisher.topic("legal-entity"), outbox);
    }

    @Bean
    NewTopic establishmentTopic(OutboxProperties outbox) {
        return topic(EventPublisher.topic("establishment"), outbox);
    }

    @Bean
    NewTopic tenantDeadLetterTopic(OutboxProperties outbox) {
        return topic(DEAD_LETTER_TOPIC, outbox);
    }

    /**
     * Dead letter própria deste consumidor ({@code <tópico>.<grupo>-dlt}): falhas de serviços diferentes no mesmo
     * tópico de origem não se misturam.
     */
    static final String DEAD_LETTER_TOPIC = "tenant-registry.tenant.v1.organization-dlt";

    @Bean
    DefaultErrorHandler kafkaErrorHandler(KafkaOperations<?, ?> kafka, ConsumerProperties consumer) {
        var recoverer = new DeadLetterPublishingRecoverer(
                kafka, (record, exception) -> new TopicPartition(DEAD_LETTER_TOPIC, record.partition()));
        var handler = new DefaultErrorHandler(
                recoverer, new FixedBackOff(consumer.retryInterval().toMillis(), consumer.retryAttempts()));
        handler.addNotRetryableExceptions(InvalidTenantEventException.class);
        return handler;
    }

    private static NewTopic topic(String name, OutboxProperties outbox) {
        return TopicBuilder.name(name)
                .partitions(outbox.topicPartitions())
                .replicas(outbox.topicReplicas())
                .build();
    }
}
