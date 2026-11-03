package br.com.davinci.erp.tenantregistry.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import br.com.davinci.erp.platform.kernel.CellId;
import br.com.davinci.erp.platform.kernel.TenantId;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class TenantTest {

    private static final Instant T0 = Instant.parse("2026-11-03T12:00:00Z");
    private static final Instant T1 = T0.plusSeconds(60);
    private static final CellId CELL = CellId.parse("br-1-pooled-01");

    private static Tenant provisioning() {
        return Tenant.register(new TenantId(UUID.randomUUID()), new TenantSlug("acme"), " Acme S.A. ", CELL, T0)
                .tenant();
    }

    @Test
    void registroNasceEmProvisionamentoNaRegiaoEIsolamentoDaCelula() {
        Tenant.Transition registered =
                Tenant.register(new TenantId(UUID.randomUUID()), new TenantSlug("acme"), "Acme", CELL, T0);

        assertThat(registered.eventType()).contains(TenantEventType.REGISTERED);
        assertThat(registered.tenant().status()).isEqualTo(TenantStatus.PROVISIONING);
        assertThat(registered.tenant().region()).isEqualTo(CELL.region());
        assertThat(registered.tenant().isolation()).isEqualTo(CELL.isolation());
        assertThat(registered.tenant().version()).isZero();
    }

    @Test
    void normalizaEspacosDoNome() {
        assertThat(provisioning().displayName()).isEqualTo("Acme S.A.");
    }

    @Test
    void cicloCompletoIncrementaVersaoEGeraEventos() {
        Tenant.Transition activated = provisioning().activate(T1);
        Tenant.Transition suspended = activated.tenant().suspend("inadimplência", T1);
        Tenant.Transition reactivated = suspended.tenant().reactivate(T1);

        assertThat(activated.eventType()).contains(TenantEventType.ACTIVATED);
        assertThat(suspended.eventType()).contains(TenantEventType.SUSPENDED);
        assertThat(suspended.tenant().statusReason()).isEqualTo("inadimplência");
        assertThat(reactivated.eventType()).contains(TenantEventType.REACTIVATED);
        assertThat(reactivated.tenant().statusReason()).isNull();
        assertThat(reactivated.tenant().version()).isEqualTo(3);
        assertThat(reactivated.tenant().updatedAt()).isEqualTo(T1);
    }

    @Test
    void repetirTransicaoNaoGeraEventoNemNovaVersao() {
        Tenant active = provisioning().activate(T1).tenant();

        Tenant.Transition again = active.activate(T1.plusSeconds(1));

        assertThat(again.eventType()).isEmpty();
        assertThat(again.tenant()).isSameAs(active);
    }

    @Test
    void rejeitaTransicoesForaDeOrdem() {
        Tenant tenant = provisioning();

        assertThatThrownBy(() -> tenant.suspend("motivo", T1)).isInstanceOf(InvalidTransitionException.class);
        assertThatThrownBy(() -> tenant.reactivate(T1)).isInstanceOf(InvalidTransitionException.class);
    }

    @Test
    void suspensaoExigeMotivo() {
        Tenant active = provisioning().activate(T1).tenant();

        assertThatThrownBy(() -> active.suspend("  ", T1))
                .isInstanceOf(InvalidTenantDataException.class)
                .extracting(e -> ((InvalidTenantDataException) e).field())
                .isEqualTo("reason");
    }
}
