package br.com.davinci.erp.organization.corporate.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import br.com.davinci.erp.organization.support.Cnpjs;
import br.com.davinci.erp.organization.support.Contracts;
import br.com.davinci.erp.organization.support.IntegrationTest;
import br.com.davinci.erp.organization.support.Requests;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;
import tools.jackson.databind.JsonNode;

class CorporateApiIT extends IntegrationTest {

    private static final MediaType MERGE_PATCH = MediaType.parseMediaType("application/merge-patch+json");

    @Test
    void criaGrupoEEntidadeAtivaComCnpjEPublicaEventos() throws Exception {
        UUID tenant = activeTenant();
        String groupBody = createGroup(tenant, Requests.code())
                .andExpect(status().isCreated())
                .andExpect(header().string("ETag", "\"0\""))
                .andReturn()
                .getResponse()
                .getContentAsString();
        Contracts.assertHttp("EconomicGroup", groupBody);
        UUID group = UUID.fromString(read(groupBody).get("id").asString());

        String cnpj = Cnpjs.random();
        String body = createEntity(
                        tenant,
                        Requests.brazilianEntity(Requests.code(), formatted(cnpj), group, "ACTIVE", "2026-01-01"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("ACTIVE"))
                .andExpect(jsonPath("$.incorporatedOn").value("2026-01-01"))
                .andExpect(jsonPath("$.economicGroupId").value(group.toString()))
                .andExpect(jsonPath("$.taxIdentifiers[0].value").value(cnpj))
                .andExpect(jsonPath("$.registeredAddress.postalCode").value("01310-100"))
                .andReturn()
                .getResponse()
                .getContentAsString();
        Contracts.assertHttp("LegalEntity", body);
        UUID entity = UUID.fromString(read(body).get("id").asString());

        assertThat(events("economic-group", group, 1)).hasSize(1);
        List<JsonNode> entityEvents = events("legal-entity", entity, 1);
        assertThat(entityEvents.getFirst().get("type").asString()).endsWith("legal-entity.created.v1");
        assertThat(entityEvents.getFirst().get("data").get("economicGroupId").asString())
                .isEqualTo(group.toString());
    }

    @Test
    void entidadeBrasileiraSoFicaAtivaComCnpjPrincipal() throws Exception {
        UUID tenant = activeTenant();
        String withoutCnpj = """
                {"code":"%s","legalName":"Sem CNPJ Ltda","country":"BR","functionalCurrency":"BRL",
                 "timezone":"America/Sao_Paulo","defaultLocale":"pt-BR","status":"ACTIVE","effectiveOn":"2026-01-01",
                 "registeredAddress":{"country":"BR","addressLines":["Rua A, 1"],"locality":"Recife","postalCode":"50000-000"}}
                """.formatted(Requests.code());
        createEntity(tenant, withoutCnpj)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("missing-tax-identifier"));

        String planned = withoutCnpj.replace("\"ACTIVE\"", "\"PLANNED\"");
        UUID entity = id(createEntity(tenant, planned)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("PLANNED")));
        mvc.perform(post("/v1/legal-entities/{id}/tax-identifiers", entity)
                        .with(admin(tenant))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"scheme\":\"BR_CNPJ\",\"value\":\"" + Cnpjs.random()
                                + "\",\"primary\":true,\"validFrom\":\"2026-01-01\"}"))
                .andExpect(status().isOk());
        mvc.perform(post("/v1/legal-entities/{id}/activate", entity)
                        .with(admin(tenant))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"effectiveOn\":\"2026-02-01\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ACTIVE"))
                .andExpect(jsonPath("$.incorporatedOn").value("2026-02-01"));
    }

    @Test
    void validacoesDeEntrada() throws Exception {
        UUID tenant = activeTenant();
        createEntity(
                        tenant,
                        Requests.brazilianEntity(Requests.code(), "11.222.333/0001-82", null, "PLANNED", "2026-01-01"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("invalid-tax-identifier"));
        createEntity(
                        tenant,
                        Requests.brazilianEntity(Requests.code(), Cnpjs.random(), null, "PLANNED", "2026-01-01")
                                .replace(
                                        "\"country\":\"BR\",\"subdivision\"",
                                        "\"country\":\"BR\",\"tenantId\":\"x\",\"subdivision\""))
                .andExpect(status().isBadRequest());
        createEntity(
                        tenant,
                        Requests.brazilianEntity(Requests.code(), Cnpjs.random(), null, "PLANNED", "2026-01-01")
                                .replace("America/Sao_Paulo", "GMT-3"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.field").value("timezone"));
    }

    @Test
    void cnpjNaoSeRepeteNoMesmoTenant() throws Exception {
        UUID tenant = activeTenant();
        String cnpj = Cnpjs.random();
        createEntity(tenant, Requests.brazilianEntity(Requests.code(), cnpj, null, "ACTIVE", "2026-01-01"))
                .andExpect(status().isCreated());
        createEntity(tenant, Requests.brazilianEntity(Requests.code(), cnpj, null, "ACTIVE", "2026-01-01"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("duplicate-tax-identifier"));
    }

    @Test
    void alteracaoExigeVersaoAtual() throws Exception {
        UUID tenant = activeTenant();
        UUID entity = id(createEntity(
                tenant, Requests.brazilianEntity(Requests.code(), Cnpjs.random(), null, "ACTIVE", "2026-01-01")));

        mvc.perform(patch("/v1/legal-entities/{id}", entity)
                        .with(admin(tenant))
                        .header("If-Match", "\"7\"")
                        .contentType(MERGE_PATCH)
                        .content("{\"legalName\":\"Novo Nome Ltda\"}"))
                .andExpect(status().isPreconditionFailed())
                .andExpect(jsonPath("$.code").value("version-mismatch"));
        mvc.perform(patch("/v1/legal-entities/{id}", entity)
                        .with(admin(tenant))
                        .header("If-Match", "\"1\"")
                        .contentType(MERGE_PATCH)
                        .content("{\"legalName\":\"Novo Nome Ltda\",\"tradeName\":null}"))
                .andExpect(status().isOk())
                .andExpect(header().string("ETag", "\"2\""))
                .andExpect(jsonPath("$.legalName").value("Novo Nome Ltda"));
        mvc.perform(patch("/v1/legal-entities/{id}", entity)
                        .with(admin(tenant))
                        .header("If-Match", "\"2\"")
                        .contentType(MERGE_PATCH)
                        .content("{\"functionalCurrency\":\"USD\"}"))
                .andExpect(status().isBadRequest());
        mvc.perform(patch("/v1/legal-entities/{id}", entity)
                        .with(admin(tenant))
                        .header("If-Match", "\"2\"")
                        .contentType(MERGE_PATCH)
                        .content("{\"status\":\"CLOSED\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void mudancaDeGrupoComVigenciaEConsultaPorData() throws Exception {
        UUID tenant = activeTenant();
        UUID groupA = id(createGroup(tenant, Requests.code()));
        UUID groupB = id(createGroup(tenant, Requests.code()));
        UUID entity = id(createEntity(
                tenant, Requests.brazilianEntity(Requests.code(), Cnpjs.random(), groupA, "ACTIVE", "2026-01-01")));

        mvc.perform(put("/v1/legal-entities/{id}/economic-group", entity)
                        .with(admin(tenant))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"economicGroupId\":\"" + groupB + "\",\"effectiveOn\":\"2026-07-01\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.groupAssignments.length()").value(2));

        mvc.perform(get("/v1/legal-entities/{id}", entity)
                        .param("asOf", "2026-03-01")
                        .with(user(tenant)))
                .andExpect(jsonPath("$.economicGroupId").value(groupA.toString()));
        mvc.perform(get("/v1/legal-entities/{id}", entity)
                        .param("asOf", "2026-08-01")
                        .with(user(tenant)))
                .andExpect(jsonPath("$.economicGroupId").value(groupB.toString()));
        mvc.perform(get("/v1/economic-groups/{id}/legal-entities", groupA)
                        .param("asOf", "2026-03-01")
                        .with(user(tenant)))
                .andExpect(jsonPath("$.items.length()").value(1));
        mvc.perform(get("/v1/economic-groups/{id}/legal-entities", groupA)
                        .param("asOf", "2026-08-01")
                        .with(user(tenant)))
                .andExpect(jsonPath("$.items.length()").value(0));

        // A saiu do grupo A em julho: o grupo pode ser encerrado depois disso; o B ainda tem a entidade.
        mvc.perform(post("/v1/economic-groups/{id}/close", groupA)
                        .with(admin(tenant))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"effectiveOn\":\"2026-09-01\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CLOSED"));
        mvc.perform(post("/v1/economic-groups/{id}/close", groupB)
                        .with(admin(tenant))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"effectiveOn\":\"2026-09-01\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("has-active-dependents"));
    }

    @Test
    void cicloDeVidaPublicaEventosEmOrdemEEncerramentoTiraDoGrupo() throws Exception {
        UUID tenant = activeTenant();
        UUID group = id(createGroup(tenant, Requests.code()));
        UUID entity = id(createEntity(
                tenant, Requests.brazilianEntity(Requests.code(), Cnpjs.random(), group, "ACTIVE", "2026-01-01")));

        for (String transition : List.of("suspend", "suspend", "reactivate")) {
            mvc.perform(post("/v1/legal-entities/{id}/" + transition, entity).with(admin(tenant)))
                    .andExpect(status().isOk());
        }
        mvc.perform(post("/v1/legal-entities/{id}/close", entity)
                        .with(admin(tenant))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"effectiveOn\":\"2026-10-01\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CLOSED"))
                .andExpect(jsonPath("$.groupAssignments[0].validTo").value("2026-10-01"));
        mvc.perform(post("/v1/legal-entities/{id}/reactivate", entity).with(admin(tenant)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("invalid-transition"));

        List<String> facts = events("legal-entity", entity, 4).stream()
                .map(e -> e.get("type").asString().replace("br.com.davinci.organization.legal-entity.", ""))
                .toList();
        assertThat(facts).containsExactly("created.v1", "suspended.v1", "reactivated.v1", "closed.v1");
    }

    @Test
    void criacaoIdempotente() throws Exception {
        UUID tenant = activeTenant();
        String key = UUID.randomUUID().toString();
        String body = Requests.group(Requests.code());

        UUID first = id(mvc.perform(post("/v1/economic-groups")
                .with(admin(tenant))
                .header("Idempotency-Key", key)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body)));
        UUID second = id(mvc.perform(post("/v1/economic-groups")
                .with(admin(tenant))
                .header("Idempotency-Key", key)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body)));
        assertThat(second).isEqualTo(first);
        mvc.perform(post("/v1/economic-groups")
                        .with(admin(tenant))
                        .header("Idempotency-Key", key)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(Requests.group(Requests.code())))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("idempotency-key-reused"));
    }

    @Test
    void listaPaginadaEFiltrada() throws Exception {
        UUID tenant = activeTenant();
        for (int i = 0; i < 3; i++) {
            createEntity(
                            tenant,
                            Requests.brazilianEntity(Requests.code(), Cnpjs.random(), null, "PLANNED", "2026-01-01"))
                    .andExpect(status().isCreated());
        }
        String first = mvc.perform(get("/v1/legal-entities")
                        .param("country", "BR")
                        .param("limit", "2")
                        .with(user(tenant)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(2))
                .andReturn()
                .getResponse()
                .getContentAsString();
        Contracts.assertHttp("LegalEntityPage", first);
        mvc.perform(get("/v1/legal-entities")
                        .param("country", "BR")
                        .param("limit", "2")
                        .param("cursor", read(first).get("nextCursor").asString())
                        .with(user(tenant)))
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.nextCursor").isEmpty());
    }

    @Test
    void usuarioComumSoLeESemTokenNaoEntra() throws Exception {
        UUID tenant = activeTenant();
        mvc.perform(get("/v1/economic-groups").with(user(tenant))).andExpect(status().isOk());
        mvc.perform(post("/v1/economic-groups")
                        .with(user(tenant))
                        .header("Idempotency-Key", "k")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(Requests.group(Requests.code())))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("forbidden-scope"));
        mvc.perform(get("/v1/economic-groups")).andExpect(status().isUnauthorized());
        mvc.perform(get("/actuator/health")).andExpect(status().isOk());
    }

    @Test
    void tenantSuspensoFicaSomenteLeitura() throws Exception {
        UUID tenant = activeTenant();
        UUID group = id(createGroup(tenant, Requests.code()));
        suspendTenant(tenant);

        createGroup(tenant, Requests.code())
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("tenant-not-active"));
        mvc.perform(get("/v1/economic-groups/{id}", group).with(admin(tenant))).andExpect(status().isOk());
    }

    private ResultActions createGroup(UUID tenant, String code) throws Exception {
        return mvc.perform(post("/v1/economic-groups")
                .with(admin(tenant))
                .header("Idempotency-Key", UUID.randomUUID().toString())
                .contentType(MediaType.APPLICATION_JSON)
                .content(Requests.group(code)));
    }

    private ResultActions createEntity(UUID tenant, String body) throws Exception {
        return mvc.perform(post("/v1/legal-entities")
                .with(admin(tenant))
                .header("Idempotency-Key", UUID.randomUUID().toString())
                .contentType(MediaType.APPLICATION_JSON)
                .content(body));
    }

    private UUID id(ResultActions result) throws Exception {
        return UUID.fromString(read(result.andReturn().getResponse().getContentAsString())
                .get("id")
                .asString());
    }

    private static String formatted(String cnpj) {
        return cnpj.substring(0, 2) + "." + cnpj.substring(2, 5) + "." + cnpj.substring(5, 8) + "/"
                + cnpj.substring(8, 12) + "-" + cnpj.substring(12);
    }
}
