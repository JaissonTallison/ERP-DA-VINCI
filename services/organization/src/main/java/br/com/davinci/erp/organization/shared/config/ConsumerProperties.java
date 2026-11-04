package br.com.davinci.erp.organization.shared.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * @param retryInterval espera entre tentativas de um evento que falhou
 * @param retryAttempts tentativas antes da dead letter
 */
@ConfigurationProperties("organization.consumer")
public record ConsumerProperties(Duration retryInterval, long retryAttempts) {}
