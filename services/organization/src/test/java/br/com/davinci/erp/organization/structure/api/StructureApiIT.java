package br.com.davinci.erp.organization.structure.api;

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
import tools.jackson.databind.JsonNode;

/** Critérios de aceite da etapa 4: árvore com vigência, regras de hierarquia e isolamento. */
class StructureApiIT extends IntegrationTest {

    private UUID tenant;
    private UUID board;
    private UUID department;
    private UUID structure;
    private UUID presidency;
    private UUID industrial;
    private UUID commercial;
    private UUID sales;

    /** Presidência ── Diretoria Industrial ── Vendas; Presidência ── Diretoria Comercial (desde janeiro). */
    @BeforeEach
    void estrutura() throws Exception {
        tenant = activeTenant();
        board = id(post(
                "/v1/unit-types",
                "{\"code\":\"DIRETORIA\",\"name\":\"Diretoria\",\"nameTranslations\":{\"en\":\"Board\"}}"));
        department = id(post("/v1/unit-types", "{\"code\":\"DEPARTAMENTO\",\"name\":\"Departamento\"}"));
        put("/v1/unit-types/" + department + "/nesting", "{\"allowedParentTypeIds\":[\"" + board + "\"]}")
                .andExpect(status().isOk());

        structure = id(post(
                "/v1/structures",
                "{\"code\":\"GERENCIAL\",\"name\":\"Estrutura gerencial\",\"purpose\":\"MANAGEMENT\"}"));
        presidency = unit("PRES", "Presidência", board);
        industrial = unit("DIR-IND", "Diretoria Industrial", board);
        commercial = unit("DIR-COM", "Diretoria Comercial", board);
        sales = unit("VENDAS", "Vendas", department);
        move(presidency, null, "2026-01-01").andExpect(status().isOk());
        move(industrial, presidency, "2026-01-01").andExpect(status().isOk());
        move(commercial, presidency, "2026-01-01").andExpect(status().isOk());
        move(sales, industrial, "2026-01-01").andExpect(status().isOk());
    }

    @Test
    void reorganizacaoComVigenciaEConsultaPorData() throws Exception {
        move(sales, commercial, "2026-07-01")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.version").value(2));

        children(industrial, "2026-03-01")
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.items[0].code").value("VENDAS"));
        children(industrial, "2026-08-01")
                .andExpect(jsonPath("$.items.length()").value(0));
        children(commercial, "2026-08-01").andExpect(jsonPath("$.items[0].code").value("VENDAS"));

        String tree = mvc.perform(get("/v1/structures/{s}/units/{u}/subtree", structure, presidency)
                        .param("asOf", "2026-08-01")
                        .with(user(tenant)))
                .andExpect(jsonPath("$.items.length()").value(4))
                .andExpect(jsonPath("$.items[0].code").value("PRES"))
                .andExpect(jsonPath("$.items[0].hasChildren").value(true))
                .andExpect(jsonPath("$.truncated").value(false))
                .andReturn()
                .getResponse()
                .getContentAsString();
        Contracts.assertHttp("Tree", tree);
        assertThat(read(tree).get("items").findValuesAsString("code"))
                .containsExactly("PRES", "DIR-COM", "DIR-IND", "VENDAS");
        mvc.perform(get("/v1/structures/{s}/units/{u}/subtree", structure, presidency)
                        .param("asOf", "2026-08-01")
                        .param("maxDepth", "1")
                        .with(user(tenant)))
                .andExpect(jsonPath("$.items.length()").value(3))
                .andExpect(jsonPath("$.items[0].hasChildren").value(true))
                .andExpect(jsonPath("$.items[1].code").value("DIR-COM"))
                .andExpect(jsonPath("$.items[1].hasChildren").value(true)) // no limite de profundidade
                .andExpect(jsonPath("$.items[2].code").value("DIR-IND"))
                .andExpect(jsonPath("$.items[2].hasChildren").value(false));

        mvc.perform(get("/v1/structures/{s}/units/{u}/ancestors", structure, sales)
                        .param("asOf", "2026-03-01")
                        .with(user(tenant)))
                .andExpect(jsonPath("$.items.length()").value(3))
                .andExpect(jsonPath("$.items[0].code").value("VENDAS"))
                .andExpect(jsonPath("$.items[1].code").value("DIR-IND"))
                .andExpect(jsonPath("$.items[2].code").value("PRES"));
        mvc.perform(get("/v1/structures/{s}/roots", structure)
                        .param("asOf", "2026-03-01")
                        .with(user(tenant)))
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.items[0].code").value("PRES"));

        List<JsonNode> events = events("unit", sales, 3);
        JsonNode lastMove = events.getLast().get("data");
        assertThat(events.getLast().get("type").asString()).endsWith("unit.moved.v1");
        assertThat(lastMove.get("placement").get("previousParentUnitId").asString())
                .isEqualTo(industrial.toString());
        assertThat(lastMove.get("placement").get("parentUnitId").asString()).isEqualTo(commercial.toString());
        assertThat(lastMove.get("effectiveOn").asString()).isEqualTo("2026-07-01");
    }

    @Test
    void regrasDaHierarquia() throws Exception {
        move(presidency, sales, "2026-05-01")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("hierarchy-cycle"));
        UUID orders = unit("PEDIDOS", "Pedidos", department);
        move(orders, sales, "2026-05-01")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("nesting-not-allowed"));
        UUID loose = unit("AVULSA", "Avulsa", board);
        move(orders, loose, "2026-05-01")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("parent-not-in-structure"));
        move(sales, commercial, "2027-01-01").andExpect(status().isOk());
        move(sales, presidency, "2026-12-01")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("overlapping-period"));
    }

    @Test
    void encerramentosRespeitamOsFilhos() throws Exception {
        action("/v1/organizational-units/{id}/close", industrial, "2026-09-01")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("has-active-dependents"));
        mvc.perform(MockMvcRequestBuilders.post("/v1/structures/{s}/removals", structure)
                        .with(admin(tenant))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"unitId\":\"" + industrial + "\",\"effectiveOn\":\"2026-09-01\"}"))
                .andExpect(status().isConflict());

        action("/v1/organizational-units/{id}/close", sales, "2026-09-01")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CLOSED"));
        children(industrial, "2026-08-31")
                .andExpect(jsonPath("$.items.length()").value(1));
        children(industrial, "2026-09-01")
                .andExpect(jsonPath("$.items.length()").value(0));
        action("/v1/organizational-units/{id}/close", industrial, "2026-09-01").andExpect(status().isOk());
    }

    @Test
    void unidadeLigadaAEntidadeEEstabelecimento() throws Exception {
        String cnpj = Cnpjs.random();
        UUID entity = id(post(
                "/v1/legal-entities", Requests.brazilianEntity(Requests.code(), cnpj, null, "ACTIVE", "2026-01-01")));
        UUID other = id(post(
                "/v1/legal-entities",
                Requests.brazilianEntity(Requests.code(), Cnpjs.random(), null, "ACTIVE", "2026-01-01")));
        UUID plant =
                id(post("/v1/establishments", Requests.establishment(entity, "FAB", "PLANT", Cnpjs.branchOf(cnpj, 2))));

        post("/v1/organizational-units", unitBody("FAB-OPS", "Operação da fábrica", board, other, plant))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.field").value("establishmentId"));
        String body = post("/v1/organizational-units", unitBody("FAB-OPS", "Operação da fábrica", board, entity, plant))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();
        Contracts.assertHttp("Unit", body);

        action("/v1/establishments/{id}/close", plant, null)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("has-active-dependents"));
        mvc.perform(MockMvcRequestBuilders.post("/v1/legal-entities/{id}/close", entity)
                        .with(admin(tenant)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("has-active-dependents"));
    }

    @Test
    void nomeMultilingueEValidacao() throws Exception {
        mvc.perform(get("/v1/unit-types").with(user(tenant)))
                .andExpect(jsonPath("$.items[?(@.code=='DIRETORIA')].nameTranslations.en")
                        .value("Board"));
        post("/v1/unit-types", "{\"code\":\"X\",\"name\":\"X\",\"nameTranslations\":{\"pt_BR!\":\"x\"}}")
                .andExpect(status().isBadRequest());
    }

    @Test
    void outroTenantNaoVeNemMexeNaEstrutura() throws Exception {
        UUID other = activeTenant();
        mvc.perform(get("/v1/structures/{s}/roots", structure).with(admin(other)))
                .andExpect(status().isNotFound());
        mvc.perform(MockMvcRequestBuilders.post("/v1/structures/{s}/moves", structure)
                        .with(admin(other))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"unitId\":\"" + sales + "\",\"parentUnitId\":null,\"effectiveOn\":\"2026-05-01\"}"))
                .andExpect(status().isNotFound());
        mvc.perform(get("/v1/organizational-units/{id}", sales).with(admin(other)))
                .andExpect(status().isNotFound());
        mvc.perform(get("/v1/unit-types").with(admin(other)))
                .andExpect(jsonPath("$.items.length()").value(0));
    }

    private UUID unit(String code, String name, UUID type) throws Exception {
        return id(post("/v1/organizational-units", unitBody(code, name, type, null, null)));
    }

    private static String unitBody(String code, String name, UUID type, UUID entity, UUID establishment) {
        return """
                {"code":"%s","name":"%s","unitTypeId":"%s",%s%s"validFrom":"2026-01-01"}
                """.formatted(
                        code,
                        name,
                        type,
                        entity == null ? "" : "\"legalEntityId\":\"" + entity + "\",",
                        establishment == null ? "" : "\"establishmentId\":\"" + establishment + "\",");
    }

    private ResultActions move(UUID unit, UUID parent, String on) throws Exception {
        return mvc.perform(MockMvcRequestBuilders.post("/v1/structures/{s}/moves", structure)
                .with(admin(tenant))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"unitId\":\"%s\",\"parentUnitId\":%s,\"effectiveOn\":\"%s\"}"
                        .formatted(unit, parent == null ? "null" : "\"" + parent + "\"", on)));
    }

    private ResultActions children(UUID parent, String asOf) throws Exception {
        return mvc.perform(get("/v1/structures/{s}/units/{u}/children", structure, parent)
                        .param("asOf", asOf)
                        .with(user(tenant)))
                .andExpect(status().isOk());
    }

    private ResultActions action(String path, UUID id, String effectiveOn) throws Exception {
        var request = MockMvcRequestBuilders.post(path, id).with(admin(tenant));
        if (effectiveOn != null) {
            request.contentType(MediaType.APPLICATION_JSON).content("{\"effectiveOn\":\"" + effectiveOn + "\"}");
        }
        return mvc.perform(request);
    }

    private ResultActions post(String path, String body) throws Exception {
        return mvc.perform(MockMvcRequestBuilders.post(path)
                .with(admin(tenant))
                .header("Idempotency-Key", UUID.randomUUID().toString())
                .contentType(MediaType.APPLICATION_JSON)
                .content(body));
    }

    private ResultActions put(String path, String body) throws Exception {
        return mvc.perform(MockMvcRequestBuilders.put(path)
                .with(admin(tenant))
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
