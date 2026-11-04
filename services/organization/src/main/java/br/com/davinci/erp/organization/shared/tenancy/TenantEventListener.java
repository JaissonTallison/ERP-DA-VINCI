package br.com.davinci.erp.organization.shared.tenancy;

import br.com.davinci.erp.platform.kernel.TenantId;
import java.util.Set;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/** Consome {@code tenant-registry.tenant.v1} (contrato em contracts/events/tenant-registry/v1). */
@Component
class TenantEventListener {

    static final String TOPIC = "tenant-registry.tenant.v1";
    private static final String TYPE_PREFIX = "br.com.davinci.tenantregistry.tenant.";
    private static final Set<String> STATUSES = Set.of("PROVISIONING", "ACTIVE", "SUSPENDED");

    private final TenantProjection projection;
    private final JsonMapper json;

    TenantEventListener(TenantProjection projection, JsonMapper json) {
        this.projection = projection;
        this.json = json;
    }

    @KafkaListener(topics = TOPIC)
    void onEvent(String payload) {
        JsonNode event;
        try {
            event = json.readTree(payload);
        } catch (JacksonException e) {
            throw new InvalidTenantEventException("Evento não é JSON válido", e);
        }
        if (!event.path("type").asString("").startsWith(TYPE_PREFIX)) {
            return;
        }
        JsonNode data = event.path("data");
        String status = data.path("status").asString("");
        long version = data.path("version").asLong(-1);
        if (!STATUSES.contains(status) || version < 0) {
            throw new InvalidTenantEventException("Evento fora do contrato: status ou versão inválidos", null);
        }
        try {
            projection.apply(TenantId.parse(data.path("tenantId").asString("")), status, version);
        } catch (IllegalArgumentException e) {
            throw new InvalidTenantEventException("Evento fora do contrato: tenantId inválido", e);
        }
    }
}
