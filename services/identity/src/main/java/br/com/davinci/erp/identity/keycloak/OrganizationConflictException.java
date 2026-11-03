package br.com.davinci.erp.identity.keycloak;

/** Já existe no Keycloak uma organização com o mesmo alias e outro tenant. Exige intervenção humana. */
public final class OrganizationConflictException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public OrganizationConflictException(String alias, String expectedTenant) {
        super("Organização '" + alias + "' existe no Keycloak com outro tenant_id (esperado " + expectedTenant + ")");
    }
}
