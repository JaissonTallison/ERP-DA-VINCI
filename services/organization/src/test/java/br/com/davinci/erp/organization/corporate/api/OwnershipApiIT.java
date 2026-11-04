package br.com.davinci.erp.organization.corporate.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import br.com.davinci.erp.organization.support.Cnpjs;
import br.com.davinci.erp.organization.support.Contracts;
import br.com.davinci.erp.organization.support.IntegrationTest;
import br.com.davinci.erp.organization.support.Requests;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

/** Etapas 7 e 8: participações pela API, dado pessoal cifrado, organograma, histórico e isolamento. */
class OwnershipApiIT extends IntegrationTest {

    private static final String DOCUMENT = "12345678909";

    private UUID tenant;
    private UUID holding;
    private UUID subsidiary;
    private UUID person;

    @BeforeEach
    void participantes() throws Exception {
        tenant = activeTenant();
        holding = entity();
        subsidiary = entity();
        person = id(post(
                "/v1/external-parties",
                "{\"kind\":\"PERSON\",\"name\":\"Titular Externo\",\"country\":\"BR\",\"taxIdentifier\":\"%s\""
                                .formatted(DOCUMENT)
                        + "}"));
    }

    @Test
    void organogramaComParticipacaoEfetivaEControlador() throws Exception {
        UUID direct = id(interest(subsidiary, "ownerLegalEntityId", holding, "80", "80"));
        id(interest(holding, "ownerExternalPartyId", person, "60", "60"));

        String chart = mvc.perform(get("/v1/legal-entities/{id}/ownership-chart", subsidiary)
                        .param("asOf", "2026-06-01")
                        .with(user(tenant)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ultimateController.name").value("Titular Externo"))
                .andExpect(jsonPath("$.holders[?(@.name=='Titular Externo')].effectiveCapital")
                        .value(48.0))
                .andReturn()
                .getResponse()
                .getContentAsString();
        Contracts.assertHttp("OwnershipChart", chart);

        mvc.perform(get("/v1/legal-entities/{id}/ownership", holding)
                        .param("direction", "holdings")
                        .param("asOf", "2026-06-01")
                        .with(user(tenant)))
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.items[0].ownedLegalEntityId").value(subsidiary.toString()));

        post("/v1/ownership-interests/" + direct + "/end", "{\"effectiveOn\":\"2026-07-01\"}")
                .andExpect(status().isOk());
        mvc.perform(get("/v1/legal-entities/{id}/ownership", subsidiary)
                        .param("asOf", "2026-07-01")
                        .with(user(tenant)))
                .andExpect(jsonPath("$.items.length()").value(0));

        var events = events("ownership", direct, 2);
        assertThat(events.getLast().get("type").asString()).endsWith("ownership.ended.v1");
    }

    @Test
    void somaECiclo() throws Exception {
        id(interest(subsidiary, "ownerLegalEntityId", holding, "70", "70"));
        interest(subsidiary, "ownerExternalPartyId", person, "40", "30")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ownership-over-100"));
        interest(holding, "ownerLegalEntityId", subsidiary, "10", "10")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ownership-cycle"));
    }

    @Test
    void documentoCifradoEForaDaApiEDaAuditoria() throws Exception {
        String list = mvc.perform(get("/v1/external-parties").with(user(tenant)))
                .andExpect(jsonPath("$.items[0].hasTaxIdentifier").value(true))
                .andReturn()
                .getResponse()
                .getContentAsString();
        assertThat(list).doesNotContain(DOCUMENT);

        try (Connection c = superuser();
                var st = c.prepareStatement("SELECT tax_identifier_encrypted FROM external_party WHERE id = ?")) {
            st.setObject(1, person);
            try (var rs = st.executeQuery()) {
                rs.next();
                assertThat(new String(rs.getBytes(1), StandardCharsets.ISO_8859_1))
                        .doesNotContain(DOCUMENT);
            }
        }
        String history = mvc.perform(
                        get("/v1/history/external-party/{id}", person).with(admin(tenant)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(1))
                .andReturn()
                .getResponse()
                .getContentAsString();
        Contracts.assertHttp("HistoryPage", history);
        assertThat(history).doesNotContain(DOCUMENT);
    }

    @Test
    void historicoPorPeriodoSoParaAdministradorEDoProprioTenant() throws Exception {
        mvc.perform(get("/v1/history/external-party/{id}", person).with(user(tenant)))
                .andExpect(status().isForbidden());
        mvc.perform(get("/v1/history/external-party/{id}", person).with(admin(activeTenant())))
                .andExpect(jsonPath("$.items.length()").value(0));
        mvc.perform(get("/v1/changes")
                        .param("from", "2020-01-01T00:00:00Z")
                        .param("to", "2100-01-01T00:00:00Z")
                        .param("aggregateType", "external-party")
                        .with(admin(tenant)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(1));
        mvc.perform(get("/v1/changes")
                        .param("from", "2100-01-01T00:00:00Z")
                        .param("to", "2020-01-01T00:00:00Z")
                        .with(admin(tenant)))
                .andExpect(status().isBadRequest());
        mvc.perform(get("/v1/legal-entities/{id}/ownership-chart", subsidiary).with(user(activeTenant())))
                .andExpect(status().isNotFound());
    }

    private UUID entity() throws Exception {
        return id(post(
                "/v1/legal-entities",
                Requests.brazilianEntity(Requests.code(), Cnpjs.random(), null, "ACTIVE", "2026-01-01")));
    }

    private ResultActions interest(UUID owned, String ownerField, UUID owner, String capital, String voting)
            throws Exception {
        return post(
                "/v1/legal-entities/" + owned + "/ownership",
                "{\"%s\":\"%s\",\"capitalPercentage\":%s,\"votingPercentage\":%s,\"validFrom\":\"2026-01-01\"}"
                        .formatted(ownerField, owner, capital, voting));
    }

    private ResultActions post(String path, String body) throws Exception {
        return mvc.perform(MockMvcRequestBuilders.post(path)
                .with(admin(tenant))
                .header("Idempotency-Key", UUID.randomUUID().toString())
                .contentType(MediaType.APPLICATION_JSON)
                .content(body));
    }

    private UUID id(ResultActions result) throws Exception {
        return UUID.fromString(read(result.andExpect(status().isCreated())
                        .andReturn()
                        .getResponse()
                        .getContentAsString())
                .get("id")
                .asString());
    }
}
