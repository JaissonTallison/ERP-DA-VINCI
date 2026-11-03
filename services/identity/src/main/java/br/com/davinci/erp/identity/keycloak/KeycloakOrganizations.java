package br.com.davinci.erp.identity.keycloak;

import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

/** Organizações do realm na API administrativa do Keycloak. Operações idempotentes por alias. */
@Component
public class KeycloakOrganizations {

    private final RestClient admin;
    private final JsonMapper json;

    public KeycloakOrganizations(@Qualifier("keycloakAdmin") RestClient admin, JsonMapper json) {
        this.admin = admin;
        this.json = json;
    }

    /** Cria ou atualiza a organização e devolve o id dela no Keycloak. */
    public String upsert(OrganizationSpec spec) {
        Optional<String> existing = findIdByAlias(spec.alias());
        if (existing.isEmpty()) {
            return create(spec);
        }
        String id = existing.get();
        ObjectNode current = (ObjectNode)
                admin.get().uri("/organizations/{id}", id).retrieve().body(JsonNode.class);
        if (current == null) {
            throw new IllegalStateException("Organização " + id + " sumiu durante a atualização");
        }
        String currentTenant = current.path("attributes")
                .path(OrganizationSpec.TENANT_ID)
                .path(0)
                .asString("");
        if (!currentTenant.equals(spec.tenantId())) {
            throw new OrganizationConflictException(spec.alias(), spec.tenantId());
        }
        // PUT substitui a representação inteira: parte da atual para preservar domínios e campos que não gerimos.
        current.put("name", spec.name());
        current.put("description", spec.description());
        current.put("enabled", spec.enabled());
        ObjectNode attributes = current.putObject("attributes");
        spec.attributes().forEach((key, values) -> attributes.set(key, json.valueToTree(values)));
        admin.put().uri("/organizations/{id}", id).body(current).retrieve().toBodilessEntity();
        return id;
    }

    public Optional<String> findIdByAlias(String alias) {
        JsonNode found = admin.get()
                .uri(uri -> uri.path("/organizations")
                        .queryParam("search", alias)
                        .queryParam("exact", true)
                        .build())
                .retrieve()
                .body(JsonNode.class);
        if (found == null) {
            return Optional.empty();
        }
        for (JsonNode organization : found) {
            if (alias.equals(organization.path("alias").asString(""))) {
                return Optional.of(organization.path("id").asString());
            }
        }
        return Optional.empty();
    }

    private String create(OrganizationSpec spec) {
        Map<String, Object> body = Map.of(
                "alias", spec.alias(),
                "name", spec.name(),
                "description", spec.description(),
                "enabled", spec.enabled(),
                "attributes", spec.attributes(),
                "domains", List.of());
        URI location = admin.post()
                .uri("/organizations")
                .body(body)
                .retrieve()
                .toBodilessEntity()
                .getHeaders()
                .getLocation();
        if (location == null) {
            return findIdByAlias(spec.alias())
                    .orElseThrow(() -> new IllegalStateException("Keycloak não devolveu a organização criada"));
        }
        String path = location.getPath();
        return path.substring(path.lastIndexOf('/') + 1);
    }
}
