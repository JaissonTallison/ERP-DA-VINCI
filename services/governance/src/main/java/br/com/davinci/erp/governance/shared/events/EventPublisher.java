package br.com.davinci.erp.governance.shared.events;

import br.com.davinci.erp.platform.kernel.TenantId;
import java.time.Clock;
import java.util.UUID;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

/** Monta o CloudEvent (modo estruturado) e grava no outbox, na transação do chamador. */
@Component
public class EventPublisher {

    public static final String CORRELATION_MDC_KEY = "correlationId";
    static final String SOURCE = "/data-plane/governance";

    private final OutboxRepository outbox;
    private final JsonMapper json;
    private final Clock clock;

    public EventPublisher(OutboxRepository outbox, JsonMapper json, Clock clock) {
        this.outbox = outbox;
        this.json = json;
        this.clock = clock;
    }

    /**
     * @param stream família do evento, ex. {@code legal-entity}; define tópico, tipo e schema
     * @param fact fato, ex. {@code activated}
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public void publish(String stream, String fact, TenantId tenant, UUID aggregateId, ObjectNode data) {
        UUID eventId = UUID.randomUUID();
        String correlationId = MDC.get(CORRELATION_MDC_KEY);
        String schema = "urn:davinci:contracts:events:governance:v1:" + stream + "-event";
        ObjectNode event = json.createObjectNode()
                .put("specversion", "1.0")
                .put("id", eventId.toString())
                .put("source", SOURCE)
                .put("type", "br.com.davinci.governance." + stream + "." + fact + ".v1")
                .put("time", clock.instant().toString())
                .put("datacontenttype", "application/json")
                .put("dataschema", schema)
                .put("subject", aggregateId.toString())
                .put("tenantid", tenant.toString())
                .put("correlationid", correlationId == null ? eventId.toString() : correlationId);
        event.set("data", data);
        outbox.append(eventId, topic(stream), aggregateId.toString(), json.writeValueAsString(event), clock.instant());
    }

    public static String topic(String stream) {
        return "governance." + stream + ".v1";
    }
}
