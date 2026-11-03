package br.com.davinci.erp.identity.keycloak;

import java.util.List;
import java.util.Map;

/**
 * Estado desejado de uma organização no Keycloak.
 *
 * @param alias identificador imutável (slug do tenant)
 * @param name nome único no realm (também o slug: o nome de exibição pode repetir entre tenants)
 * @param attributes atributos copiados para o token (claim {@code organization}), entre eles o {@code tenant_id}
 */
public record OrganizationSpec(
        String alias, String name, String description, boolean enabled, Map<String, List<String>> attributes) {

    public static final String TENANT_ID = "tenant_id";

    public String tenantId() {
        return attributes.get(TENANT_ID).getFirst();
    }
}
