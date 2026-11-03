package br.com.davinci.erp.identity.tenant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import br.com.davinci.erp.identity.support.TenantEvents;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

class TenantEventParserTest {

    private final TenantEventParser parser =
            new TenantEventParser(JsonMapper.builder().build());

    @Test
    void leEventoDoContrato() {
        UUID tenant = UUID.randomUUID();

        TenantEvent event = parser.parse(TenantEvents.event(tenant, "acme", "activated", "ACTIVE", 1))
                .orElseThrow();

        assertThat(event.type()).isEqualTo("activated");
        assertThat(event.tenantId().value()).isEqualTo(tenant);
        assertThat(event.slug()).isEqualTo("acme");
        assertThat(event.status()).isEqualTo(TenantEvent.TenantStatus.ACTIVE);
        assertThat(event.version()).isEqualTo(1);
        assertThat(event.cellId()).hasToString("br-1-pooled-01");
    }

    @Test
    void ignoraTiposDeOutrasFamilias() {
        assertThat(parser.parse("{\"type\":\"br.com.davinci.outro.evento.v1\"}"))
                .isEmpty();
    }

    @Test
    void rejeitaMensagemForaDoContrato() {
        assertThatThrownBy(() -> parser.parse("não é json")).isInstanceOf(InvalidTenantEventException.class);
        assertThatThrownBy(() -> parser.parse("{\"type\":\"br.com.davinci.tenantregistry.tenant.activated.v1\"}"))
                .isInstanceOf(InvalidTenantEventException.class);
    }
}
