package br.com.davinci.erp.organization.costing.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import br.com.davinci.erp.organization.support.Cnpjs;
import br.com.davinci.erp.organization.support.Contracts;
import br.com.davinci.erp.organization.support.IntegrationTest;
import br.com.davinci.erp.organization.support.Requests;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

/** Etapa 6: cadastro, hierarquia com vigência, bloqueio, encerramento e isolamento. */
class CostCenterApiIT extends IntegrationTest {

    private UUID tenant;
    private UUID entity;
    private UUID group;
    private UUID otherGroup;
    private UUID center;

    @BeforeEach
    void centros() throws Exception {
        tenant = activeTenant();
        entity = entity();
        group = id(create("ADM", "GROUP", null));
        otherGroup = id(create("COM", "GROUP", null));
        center = id(create("ADM-RH", "POSTABLE", group));
    }

    @Test
    void hierarquiaPorData() throws Exception {
        String body = post(
                        "/v1/cost-centers/" + center + "/moves",
                        "{\"parentId\":\"%s\",\"effectiveOn\":\"2026-07-01\"}".formatted(otherGroup))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.version").value(1))
                .andReturn()
                .getResponse()
                .getContentAsString();
        Contracts.assertHttp("CostCenter", body);

        tree("2026-03-01")
                .andExpect(jsonPath("$.items[?(@.code=='ADM-RH')].parentId").value(group.toString()));
        tree("2026-08-01")
                .andExpect(jsonPath("$.items[?(@.code=='ADM-RH')].parentId").value(otherGroup.toString()));

        var events = events("cost-center", center, 2);
        assertThat(events.getLast().get("type").asString()).endsWith("cost-center.moved.v1");
    }

    @Test
    void regrasDeHierarquia() throws Exception {
        create("ADM", "GROUP", null)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("duplicate-code"));
        create("X", "POSTABLE", center)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("nesting-not-allowed"));
        post(
                        "/v1/cost-centers/" + group + "/moves",
                        "{\"parentId\":\"%s\",\"effectiveOn\":\"2026-02-01\"}".formatted(otherGroup))
                .andExpect(status().isOk());
        post(
                        "/v1/cost-centers/" + otherGroup + "/moves",
                        "{\"parentId\":\"%s\",\"effectiveOn\":\"2026-03-01\"}".formatted(group))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("hierarchy-cycle"));

        UUID otherEntity = entity();
        post("/v1/cost-centers", body(otherEntity, "Y", "POSTABLE", group))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.field").value("parentId"));
    }

    @Test
    void bloqueioEEncerramento() throws Exception {
        post("/v1/cost-centers/" + center + "/block", "{}")
                .andExpect(jsonPath("$.status").value("BLOCKED"));
        post("/v1/cost-centers/" + center + "/unblock", "{}")
                .andExpect(jsonPath("$.status").value("ACTIVE"));
        post("/v1/cost-centers/" + group + "/close", "{\"effectiveOn\":\"2026-06-01\"}")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("has-active-dependents"));
        post("/v1/cost-centers/" + center + "/close", "{\"effectiveOn\":\"2026-06-01\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CLOSED"));
        tree("2026-05-31").andExpect(jsonPath("$.items.length()").value(3));
        tree("2026-06-01").andExpect(jsonPath("$.items.length()").value(2));
        post("/v1/cost-centers/" + group + "/close", "{\"effectiveOn\":\"2026-06-01\"}")
                .andExpect(status().isOk());
    }

    @Test
    void isolamentoEPermissao() throws Exception {
        mvc.perform(get("/v1/cost-centers/{id}", center).with(user(activeTenant())))
                .andExpect(status().isNotFound());
        mvc.perform(MockMvcRequestBuilders.post("/v1/cost-centers/{id}/block", center)
                        .with(user(tenant)))
                .andExpect(status().isForbidden());
    }

    private UUID entity() throws Exception {
        return id(post(
                "/v1/legal-entities",
                Requests.brazilianEntity(Requests.code(), Cnpjs.random(), null, "ACTIVE", "2026-01-01")));
    }

    private ResultActions tree(String asOf) throws Exception {
        return mvc.perform(get("/v1/legal-entities/{id}/cost-centers/tree", entity)
                .param("asOf", asOf)
                .with(user(tenant)));
    }

    private ResultActions create(String code, String kind, UUID parent) throws Exception {
        return post("/v1/cost-centers", body(entity, code, kind, parent));
    }

    private static String body(UUID entity, String code, String kind, UUID parent) {
        return """
                {"legalEntityId":"%s","code":"%s","name":"Centro %s","kind":"%s","category":"ADMINISTRATIVO",
                 "parentId":%s,"validFrom":"2026-01-01"}
                """.formatted(entity, code, code, kind, parent == null ? "null" : "\"" + parent + "\"");
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
