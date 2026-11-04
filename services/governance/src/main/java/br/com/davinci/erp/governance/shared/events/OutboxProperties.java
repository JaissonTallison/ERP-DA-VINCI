package br.com.davinci.erp.governance.shared.events;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * @param batchSize eventos por ciclo do relay
 * @param sendTimeout espera máxima pela confirmação do Kafka por evento
 * @param retention por quanto tempo eventos publicados ficam no outbox
 * @param topicPartitions partições dos tópicos produzidos
 * @param topicReplicas réplicas dos tópicos (1 local, 3 em produção)
 */
@ConfigurationProperties("governance.outbox")
public record OutboxProperties(
        int batchSize, Duration sendTimeout, Duration retention, int topicPartitions, short topicReplicas) {}
