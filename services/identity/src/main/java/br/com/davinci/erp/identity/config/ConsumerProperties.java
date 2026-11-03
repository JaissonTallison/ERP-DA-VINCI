package br.com.davinci.erp.identity.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * @param retryInterval espera entre tentativas de um evento que falhou (ex.: Keycloak indisponível)
 * @param retryAttempts tentativas antes de enviar para a dead letter
 * @param deadLetterPartitions partições do tópico de dead letter (as mesmas do tópico de origem)
 * @param deadLetterReplicas réplicas do tópico de dead letter (1 local, 3 em produção)
 */
@ConfigurationProperties("identity.consumer")
public record ConsumerProperties(
        Duration retryInterval, long retryAttempts, int deadLetterPartitions, short deadLetterReplicas) {}
