package br.com.davinci.erp.organization.shared.tenancy;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class TenantClaimsTest {

    private static final String TENANT = "0b6f1c1e-6a0e-4d7c-9a51-3a7f4f1b2c3d";

    @Test
    void leOTenantDaUnicaOrganizacao() {
        assertThat(TenantClaims.tenantOf(Map.of("acme", Map.of("id", "x", "tenant_id", List.of(TENANT)))))
                .hasValueSatisfying(t -> assertThat(t.toString()).isEqualTo(TENANT));
    }

    @Test
    void recusaAusenteVariasOuMalformado() {
        assertThat(TenantClaims.tenantOf(null)).isEmpty();
        assertThat(TenantClaims.tenantOf(List.of("acme"))).isEmpty();
        assertThat(TenantClaims.tenantOf(Map.of("acme", Map.of("id", "x")))).isEmpty();
        assertThat(TenantClaims.tenantOf(Map.of("acme", Map.of("tenant_id", List.of("não-é-uuid")))))
                .isEmpty();
        assertThat(TenantClaims.tenantOf(Map.of(
                        "a", Map.of("tenant_id", List.of(TENANT)),
                        "b", Map.of("tenant_id", List.of("1b6f1c1e-6a0e-4d7c-9a51-3a7f4f1b2c3d")))))
                .isEmpty();
    }
}
