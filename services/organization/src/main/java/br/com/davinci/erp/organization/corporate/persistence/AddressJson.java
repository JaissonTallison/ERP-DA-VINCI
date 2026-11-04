package br.com.davinci.erp.organization.corporate.persistence;

import br.com.davinci.erp.organization.shared.domain.CountryCode;
import br.com.davinci.erp.organization.shared.domain.PostalAddress;
import java.util.ArrayList;
import java.util.List;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

/** Endereço gravado como {@code jsonb} (os campos variam por país; a validação é do domínio). */
@Component
class AddressJson {

    private final JsonMapper json;

    AddressJson(JsonMapper json) {
        this.json = json;
    }

    String write(PostalAddress address) {
        ObjectNode node =
                json.createObjectNode().put("country", address.country().value());
        ArrayNode lines = node.putArray("addressLines");
        address.addressLines().forEach(lines::add);
        node.put("locality", address.locality());
        node.put("administrativeArea", address.administrativeArea());
        node.put("postalCode", address.postalCode());
        return json.writeValueAsString(node);
    }

    PostalAddress read(String raw) {
        JsonNode node = json.readTree(raw);
        List<String> lines = new ArrayList<>();
        node.path("addressLines").forEach(line -> lines.add(line.asString()));
        return new PostalAddress(
                new CountryCode(node.path("country").asString()),
                lines,
                node.path("locality").asString(),
                textOrNull(node, "administrativeArea"),
                textOrNull(node, "postalCode"));
    }

    private static @Nullable String textOrNull(JsonNode node, String field) {
        JsonNode value = node.path(field);
        return value.isString() ? value.asString() : null;
    }
}
