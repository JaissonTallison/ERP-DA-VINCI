package br.com.davinci.erp.identity.tenant;

import br.com.davinci.erp.identity.sync.TenantOrganizationSync;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
class TenantEventListener {

    static final String TOPIC = "tenant-registry.tenant.v1";

    private static final Logger log = LoggerFactory.getLogger(TenantEventListener.class);

    private final TenantEventParser parser;
    private final TenantOrganizationSync sync;

    TenantEventListener(TenantEventParser parser, TenantOrganizationSync sync) {
        this.parser = parser;
        this.sync = sync;
    }

    @KafkaListener(topics = TOPIC)
    void onEvent(String payload) {
        parser.parse(payload)
                .ifPresentOrElse(sync::apply, () -> log.debug("Tipo de evento sem efeito no Identity; ignorado"));
    }
}
