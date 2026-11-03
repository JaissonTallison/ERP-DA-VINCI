package br.com.davinci.erp.tenantregistry.events;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * @param batchSize eventos por ciclo do relay
 * @param sendTimeout espera máxima pela confirmação do Kafka por evento
 * @param retention por quanto tempo eventos já publicados ficam no outbox (auditoria e reprocessamento)
 * @param topicPartitions partições do tópico criado na inicialização
 * @param topicReplicas réplicas do tópico (1 local, 3 em produção)
 */
@ConfigurationProperties("tenant-registry.outbox")
public record OutboxProperties(
        int batchSize, Duration sendTimeout, Duration retention, int topicPartitions, short topicReplicas) {}
