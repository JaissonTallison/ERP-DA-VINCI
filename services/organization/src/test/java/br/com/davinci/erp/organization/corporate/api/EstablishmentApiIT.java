package br.com.davinci.erp.organization.corporate.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import br.com.davinci.erp.organization.support.Cnpjs;
import br.com.davinci.erp.organization.support.Contracts;
import br.com.davinci.erp.organization.support.IntegrationTest;
import br.com.davinci.erp.organization.support.Requests;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

/** Critérios de aceite da etapa 3 (docs/domains/organization/ORGANIZATION_IMPLEMENTATION_PLAN.md). */
class EstablishmentApiIT extends IntegrationTest {

    private UUID tenant;
    private UUID entity;
    private String cnpj;

    @BeforeEach
    void entidadeAtiva() throws Exception {
        tenant = activeTenant();
        cnpj = Cnpjs.random();
        entity = id(post(
                "/v1/legal-entities", Requests.brazilianEntity(Requests.code(), cnpj, null, "ACTIVE", "2026-01-01")));
    }

    @Test
    void abreMatrizComOCnpjDaEntidadeEFilialComAMesmaRaiz() throws Exception {
        String body = post("/v1/establishments", Requests.establishment(entity, "MATRIZ", "HEADQUARTERS", cnpj))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("ACTIVE"))
                .andExpect(jsonPath("$.timezone").value("America/Sao_Paulo"))
                .andReturn()
                .getResponse()
                .getContentAsString();
        Contracts.assertHttp("Establishment", body);
        UUID headquarters = UUID.fromString(read(body).get("id").asString());

        post("/v1/establishments", Requests.establishment(entity, "FILIAL-02", "BRANCH", Cnpjs.branchOf(cnpj, 2)))
                .andExpect(status().isCreated());

        assertThat(events("establishment", headquarters, 1)
                        .getFirst()
                        .get("type")
                        .asString())
                .endsWith("establishment.created.v1");
    }

    @Test
    void cnpjDeOutraRaizEEnderecoForaDoPaisSaoRecusados() throws Exception {
        post("/v1/establishments", Requests.establishment(entity, "F2", "BRANCH", Cnpjs.random()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("tax-identifier-mismatch"));
        post(
                        "/v1/establishments",
                        Requests.establishment(entity, "F3", "BRANCH", Cnpjs.branchOf(cnpj, 3))
                                .replace("\"country\":\"BR\"", "\"country\":\"AR\"")
                                .replace("\"postalCode\":\"89201-000\"", "\"postalCode\":null"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.field").value("address.country"));
    }

    @Test
    void umaMatrizNaoEncerradaPorEntidade() throws Exception {
        UUID first = id(post("/v1/establishments", Requests.establishment(entity, "MATRIZ", "HEADQUARTERS", cnpj)));
        post("/v1/establishments", Requests.establishment(entity, "MATRIZ-2", "HEADQUARTERS", Cnpjs.branchOf(cnpj, 9)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("duplicate-headquarters"));

        mvc.perform(MockMvcRequestBuilders.post("/v1/establishments/{id}/close", first)
                        .with(admin(tenant)))
                .andExpect(status().isOk());
        post("/v1/establishments", Requests.establishment(entity, "MATRIZ-2", "HEADQUARTERS", Cnpjs.branchOf(cnpj, 9)))
                .andExpect(status().isCreated());
    }

    @Test
    void naoAbreNemReativaComEntidadeInativa() throws Exception {
        UUID branch =
                id(post("/v1/establishments", Requests.establishment(entity, "F2", "BRANCH", Cnpjs.branchOf(cnpj, 2))));
        action("/v1/establishments/{id}/suspend", branch).andExpect(status().isOk());
        action("/v1/legal-entities/{id}/suspend", entity).andExpect(status().isOk());

        action("/v1/establishments/{id}/reactivate", branch)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("legal-entity-not-active"));
        post("/v1/establishments", Requests.establishment(entity, "F3", "BRANCH", Cnpjs.branchOf(cnpj, 3)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("legal-entity-not-active"));
    }

    @Test
    void entidadeSoEncerraSemEstabelecimentoAberto() throws Exception {
        UUID branch =
                id(post("/v1/establishments", Requests.establishment(entity, "F2", "BRANCH", Cnpjs.branchOf(cnpj, 2))));

        action("/v1/legal-entities/{id}/close", entity)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("has-active-dependents"));
        action("/v1/establishments/{id}/close", branch)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CLOSED"));
        action("/v1/legal-entities/{id}/close", entity)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CLOSED"));

        List<String> facts = events("establishment", branch, 2).stream()
                .map(e -> e.get("type").asString().replace("br.com.davinci.organization.establishment.", ""))
                .toList();
        assertThat(facts).containsExactly("created.v1", "closed.v1");
    }

    @Test
    void listaPorEntidadeComCursor() throws Exception {
        for (int i = 2; i <= 4; i++) {
            post("/v1/establishments", Requests.establishment(entity, "F" + i, "STORE", Cnpjs.branchOf(cnpj, i)))
                    .andExpect(status().isCreated());
        }
        String first = mvc.perform(get("/v1/establishments")
                        .param("legalEntityId", entity.toString())
                        .param("limit", "2")
                        .with(user(tenant)))
                .andExpect(jsonPath("$.items.length()").value(2))
                .andReturn()
                .getResponse()
                .getContentAsString();
        Contracts.assertHttp("EstablishmentPage", first);
        mvc.perform(get("/v1/establishments")
                        .param("legalEntityId", entity.toString())
                        .param("limit", "2")
                        .param("cursor", read(first).get("nextCursor").asString())
                        .with(user(tenant)))
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.items[0].code").value("F4"));
    }

    @Test
    void outroTenantNaoVeNemUsaAEntidade() throws Exception {
        UUID branch =
                id(post("/v1/establishments", Requests.establishment(entity, "F2", "BRANCH", Cnpjs.branchOf(cnpj, 2))));
        UUID other = activeTenant();

        mvc.perform(get("/v1/establishments/{id}", branch).with(admin(other))).andExpect(status().isNotFound());
        mvc.perform(get("/v1/establishments").with(admin(other)))
                .andExpect(jsonPath("$.items.length()").value(0));
        mvc.perform(MockMvcRequestBuilders.post("/v1/establishments")
                        .with(admin(other))
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(Requests.establishment(entity, "INVASAO", "BRANCH", Cnpjs.branchOf(cnpj, 5))))
                .andExpect(status().isNotFound());
        mvc.perform(get("/v1/establishments").with(user(tenant)))
                .andExpect(jsonPath("$.items.length()").value(1));
    }

    private ResultActions post(String path, String body) throws Exception {
        return mvc.perform(MockMvcRequestBuilders.post(path)
                .with(admin(tenant))
                .header("Idempotency-Key", UUID.randomUUID().toString())
                .contentType(MediaType.APPLICATION_JSON)
                .content(body));
    }

    private ResultActions action(String path, UUID id) throws Exception {
        return mvc.perform(MockMvcRequestBuilders.post(path, id).with(admin(tenant)));
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
