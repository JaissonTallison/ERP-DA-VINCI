package br.com.davinci.erp.tenantregistry.events;

import br.com.davinci.erp.tenantregistry.domain.Tenant;
import br.com.davinci.erp.tenantregistry.domain.TenantEventType;
import java.time.Instant;
import java.util.UUID;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

/**
 * Converte transições do tenant em CloudEvents (modo estruturado) e grava no outbox, na transação do chamador.
 * Contrato: contracts/events/tenant-registry/v1/tenant-event.schema.json.
 */
@Component
public class TenantEventPublisher {

    public static final String TOPIC = "tenant-registry.tenant.v1";
    static final String SOURCE = "/control-plane/tenant-registry";
    static final String TYPE_PREFIX = "br.com.davinci.tenantregistry.tenant.";
    static final String DATA_SCHEMA = "urn:davinci:contracts:events:tenant-registry:v1:tenant-event";
    public static final String CORRELATION_MDC_KEY = "correlationId";

    private final OutboxRepository outbox;
    private final JsonMapper json;

    public TenantEventPublisher(OutboxRepository outbox, JsonMapper json) {
        this.outbox = outbox;
        this.json = json;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void publish(Tenant tenant, TenantEventType type, Instant now) {
        UUID eventId = UUID.randomUUID();
        String correlationId = MDC.get(CORRELATION_MDC_KEY);

        ObjectNode event = json.createObjectNode()
                .put("specversion", "1.0")
                .put("id", eventId.toString())
                .put("source", SOURCE)
                .put("type", TYPE_PREFIX + type.code() + ".v1")
                .put("time", now.toString())
                .put("datacontenttype", "application/json")
                .put("dataschema", DATA_SCHEMA)
                .put("subject", tenant.id().toString())
                .put("tenantid", tenant.id().toString())
                .put("cellid", tenant.cellId().toString())
                .put("correlationid", correlationId == null ? eventId.toString() : correlationId);
        event.putObject("data")
                .put("tenantId", tenant.id().toString())
                .put("slug", tenant.slug().value())
                .put("displayName", tenant.displayName())
                .put("region", tenant.region().value())
                .put("isolation", tenant.isolation().code())
                .put("cellId", tenant.cellId().toString())
                .put("status", tenant.status().name())
                .put("statusReason", tenant.statusReason())
                .put("version", tenant.version());

        outbox.append(eventId, TOPIC, tenant.id().toString(), json.writeValueAsString(event), now);
    }
}
