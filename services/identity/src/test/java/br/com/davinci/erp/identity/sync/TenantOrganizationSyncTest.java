package br.com.davinci.erp.identity.sync;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.davinci.erp.identity.keycloak.OrganizationSpec;
import br.com.davinci.erp.identity.tenant.TenantEvent;
import br.com.davinci.erp.platform.kernel.CellId;
import br.com.davinci.erp.platform.kernel.TenantId;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class TenantOrganizationSyncTest {

    private static TenantEvent event(TenantEvent.TenantStatus status) {
        return new TenantEvent(
                UUID.randomUUID(),
                "x",
                new TenantId(UUID.fromString("0b6f1c1e-6a0e-4d7c-9a51-3a7f4f1b2c3d")),
                "acme",
                "Acme S.A.",
                "br-1",
                CellId.parse("br-1-pooled-01"),
                "pooled",
                status,
                1);
    }

    @Test
    void organizacaoUsaOSlugComoAliasENomeEOTenantNosAtributos() {
        OrganizationSpec spec = TenantOrganizationSync.desiredOrganization(event(TenantEvent.TenantStatus.ACTIVE));

        assertThat(spec.alias()).isEqualTo("acme");
        assertThat(spec.name()).isEqualTo("acme");
        assertThat(spec.description()).isEqualTo("Acme S.A.");
        assertThat(spec.tenantId()).isEqualTo("0b6f1c1e-6a0e-4d7c-9a51-3a7f4f1b2c3d");
        assertThat(spec.attributes()).containsEntry("cell_id", List.of("br-1-pooled-01"));
    }

    @Test
    void soTenantAtivoTemOrganizacaoHabilitada() {
        assertThat(TenantOrganizationSync.desiredOrganization(event(TenantEvent.TenantStatus.ACTIVE))
                        .enabled())
                .isTrue();
        assertThat(TenantOrganizationSync.desiredOrganization(event(TenantEvent.TenantStatus.PROVISIONING))
                        .enabled())
                .isFalse();
        assertThat(TenantOrganizationSync.desiredOrganization(event(TenantEvent.TenantStatus.SUSPENDED))
                        .enabled())
                .isFalse();
    }
}
