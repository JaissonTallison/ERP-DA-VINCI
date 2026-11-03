package br.com.davinci.erp.tenantregistry.events;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.Clock;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Publica no Kafka os eventos gravados no outbox. Entrega pelo menos uma vez: se o processo cair entre o envio e a
 * marcação, o evento é reenviado e o consumidor deduplica pelo {@code id}.
 */
@Component
public class OutboxRelay {

    private static final Logger log = LoggerFactory.getLogger(OutboxRelay.class);

    private final OutboxRepository outbox;
    private final KafkaTemplate<String, String> kafka;
    private final OutboxProperties properties;
    private final Clock clock;

    public OutboxRelay(
            OutboxRepository outbox,
            KafkaTemplate<String, String> kafka,
            OutboxProperties properties,
            Clock clock,
            MeterRegistry meters) {
        this.outbox = outbox;
        this.kafka = kafka;
        this.properties = properties;
        this.clock = clock;
        Gauge.builder("outbox.pending", outbox, OutboxRepository::countPending)
                .description("Eventos aguardando publicação no Kafka")
                .register(meters);
    }

    /** Publica um lote. Para no primeiro erro, preservando a ordem; o que já foi enviado fica marcado. */
    @Scheduled(fixedDelayString = "${tenant-registry.outbox.poll-interval}")
    @Transactional
    public int publishPending() {
        int published = 0;
        for (OutboxRepository.PendingEvent event : outbox.lockPending(properties.batchSize())) {
            try {
                kafka.send(event.topic(), event.key(), event.payload())
                        .get(properties.sendTimeout().toMillis(), TimeUnit.MILLISECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            } catch (ExecutionException | TimeoutException e) {
                log.warn(
                        "Falha ao publicar evento do outbox (sequence={}); nova tentativa no próximo ciclo",
                        event.sequence(),
                        e);
                break;
            }
            outbox.markPublished(event.sequence(), clock.instant());
            published++;
        }
        return published;
    }

    @Scheduled(cron = "${tenant-registry.outbox.cleanup-cron}")
    @Transactional
    public void deleteExpired() {
        int deleted = outbox.deletePublishedBefore(clock.instant().minus(properties.retention()));
        if (deleted > 0) {
            log.info("Removidos {} eventos publicados do outbox", deleted);
        }
    }
}
