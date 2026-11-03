package br.com.davinci.erp.identity.tenant;

import br.com.davinci.erp.platform.kernel.CellId;
import br.com.davinci.erp.platform.kernel.TenantId;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Lê o CloudEvent do Tenant Registry. Tipos desconhecidos de uma versão compatível são ignorados (o produtor pode
 * acrescentar eventos novos sem quebrar este consumidor); mensagens fora do contrato são rejeitadas.
 */
@Component
public class TenantEventParser {

    static final String TYPE_PREFIX = "br.com.davinci.tenantregistry.tenant.";
    static final String TYPE_SUFFIX = ".v1";

    private final JsonMapper json;

    public TenantEventParser(JsonMapper json) {
        this.json = json;
    }

    public Optional<TenantEvent> parse(String payload) {
        JsonNode event;
        try {
            event = json.readTree(payload);
        } catch (JacksonException e) {
            throw new InvalidTenantEventException("Evento não é JSON válido", e);
        }
        String type = text(event, "type");
        if (!type.startsWith(TYPE_PREFIX) || !type.endsWith(TYPE_SUFFIX)) {
            return Optional.empty();
        }
        JsonNode data = event.path("data");
        try {
            return Optional.of(new TenantEvent(
                    UUID.fromString(text(event, "id")),
                    type.substring(TYPE_PREFIX.length(), type.length() - TYPE_SUFFIX.length()),
                    TenantId.parse(text(data, "tenantId")),
                    text(data, "slug"),
                    text(data, "displayName"),
                    text(data, "region"),
                    CellId.parse(text(data, "cellId")),
                    text(data, "isolation"),
                    TenantEvent.TenantStatus.valueOf(text(data, "status")),
                    data.path("version").asLong(-1)));
        } catch (IllegalArgumentException e) {
            throw new InvalidTenantEventException("Evento fora do contrato: " + e.getMessage(), e);
        }
    }

    private static String text(JsonNode node, String field) {
        JsonNode value = node.path(field);
        if (!value.isString() || value.asString().isBlank()) {
            throw new InvalidTenantEventException("Campo obrigatório ausente: " + field);
        }
        return value.asString();
    }
}
