package br.com.davinci.erp.organization.reorganization.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import br.com.davinci.erp.organization.support.Contracts;
import br.com.davinci.erp.organization.support.IntegrationTest;
import java.sql.Connection;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.JwtRequestPostProcessor;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import tools.jackson.databind.JsonNode;

/** Critérios de aceite da etapa 5: aplicação tudo ou nada, simulação sem efeito e segregação de funções. */
class ReorganizationApiIT extends IntegrationTest {

    private UUID tenant;
    private UUID type;
    private UUID structure;
    private UUID root;
    private UUID left;
    private UUID right;
    private UUID leaf;

    @BeforeEach
    void estrutura() throws Exception {
        tenant = activeTenant();
        type = id(post("/v1/unit-types", "{\"code\":\"AREA\",\"name\":\"Área\"}"));
        structure = id(post("/v1/structures", "{\"code\":\"G\",\"name\":\"Gerencial\",\"purpose\":\"MANAGEMENT\"}"));
        root = unit("ROOT");
        left = unit("LEFT");
        right = unit("RIGHT");
        leaf = unit("LEAF");
        place(root, null);
        place(left, root);
        place(right, root);
        place(leaf, left);
    }

    @Test
    void cicloCompletoAteAplicar() throws Exception {
        UUID id = draft("2026-07-01");
        steps(id, 0, moveStep(leaf, right) + "," + renameStep(leaf, "Folha"));

        String simulation = action(id, "simulation", null, admin(tenant))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.valid").value(true))
                .andReturn()
                .getResponse()
                .getContentAsString();
        assertThat(parentIn(read(simulation).get("preview"), "LEAF")).isEqualTo(right.toString());
        parentOf(leaf, "2026-08-01").andExpect(jsonPath("$.items[0].code").value("LEAF"));
        assertThat(unitName(leaf)).isEqualTo("LEAF");

        action(id, "submit", 1L, admin(tenant)).andExpect(status().isOk());
        String applied = action(id, "approve", 2L, secondAdmin(tenant))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("APPLIED"))
                .andExpect(jsonPath("$.decidedBy").value("outro-admin"))
                .andReturn()
                .getResponse()
                .getContentAsString();
        Contracts.assertHttp("Reorganization", applied);

        children(left, "2026-06-30").andExpect(jsonPath("$.items[0].code").value("LEAF"));
        children(left, "2026-07-01").andExpect(jsonPath("$.items.length()").value(0));
        children(right, "2026-07-01").andExpect(jsonPath("$.items[0].name").value("Folha"));

        List<JsonNode> events = events("reorganization", id, 2);
        assertThat(events).hasSize(2);
        assertThat(events.getLast().get("type").asString()).endsWith("reorganization.applied.v1");
        assertThat(events("unit", leaf, 3)).hasSizeGreaterThanOrEqualTo(3);
    }

    @Test
    void quemSubmeteNaoAprova() throws Exception {
        UUID id = draft("2026-07-01");
        steps(id, 0, moveStep(leaf, right));
        action(id, "submit", 1L, admin(tenant)).andExpect(status().isOk());

        action(id, "approve", 2L, admin(tenant)).andExpect(status().isForbidden());
        action(id, "reject", 2L, secondAdmin(tenant))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("REJECTED"));
        children(left, "2026-08-01").andExpect(jsonPath("$.items.length()").value(1));
    }

    @Test
    void quinhentosPassosEntramInteirosOuNaoEntram() throws Exception {
        List<UUID> many = bulkUnits(500);
        StringBuilder moves = new StringBuilder();
        for (UUID unit : many) {
            moves.append(moveStep(unit, right)).append(',');
        }

        // o último passo cria um ciclo: nada pode ficar aplicado
        UUID broken = draft("2026-07-01");
        steps(broken, 0, moves + moveStep(root, leaf));
        action(broken, "simulation", null, admin(tenant))
                .andExpect(jsonPath("$.valid").value(false))
                .andExpect(jsonPath("$.failedStep").value(500));
        action(broken, "submit", 1L, admin(tenant)).andExpect(status().isOk());
        action(broken, "approve", 2L, secondAdmin(tenant))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("reorganization-step-failed"));
        children(right, "2026-08-01").andExpect(jsonPath("$.items.length()").value(0));
        mvc.perform(get("/v1/reorganizations/{id}", broken).with(user(tenant)))
                .andExpect(jsonPath("$.status").value("PENDING_APPROVAL"));

        UUID valid = draft("2026-07-01");
        steps(valid, 0, moves.substring(0, moves.length() - 1));
        action(valid, "submit", 1L, admin(tenant)).andExpect(status().isOk());
        action(valid, "approve", 2L, secondAdmin(tenant)).andExpect(status().isOk());
        children(right, "2026-08-01").andExpect(jsonPath("$.items.length()").value(500));
    }

    @Test
    void transicoesEVersao() throws Exception {
        UUID id = draft("2026-07-01");
        action(id, "submit", 0L, admin(tenant))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("empty-reorganization"));
        steps(id, 0, moveStep(leaf, right));
        action(id, "submit", 0L, admin(tenant)).andExpect(status().isPreconditionFailed());
        action(id, "cancel", 1L, user(tenant)).andExpect(status().isForbidden());
        action(id, "cancel", 1L, admin(tenant)).andExpect(jsonPath("$.status").value("CANCELLED"));
        action(id, "submit", 2L, admin(tenant)).andExpect(status().isConflict());
    }

    @Test
    void outroTenantNaoEnxerga() throws Exception {
        UUID id = draft("2026-07-01");
        mvc.perform(get("/v1/reorganizations/{id}", id).with(user(activeTenant())))
                .andExpect(status().isNotFound());
    }

    private List<UUID> bulkUnits(int count) throws Exception {
        List<UUID> ids = new ArrayList<>();
        try (Connection c = superuser();
                var insertUnit = c.prepareStatement("""
                        INSERT INTO organizational_unit (id, tenant_id, code, name, unit_type_id, status, valid_from,
                                                         created_at, updated_at, version)
                        VALUES (?, ?, ?, '{"text":"Lote","translations":{}}', ?, 'ACTIVE', DATE '2026-01-01', now(), now(), 0)
                        """);
                var insertLink = c.prepareStatement("""
                        INSERT INTO structure_link (tenant_id, structure_id, unit_id, parent_unit_id, valid_from)
                        VALUES (?, ?, ?, ?, DATE '2026-01-01')
                        """)) {
            for (int i = 0; i < count; i++) {
                UUID id = UUID.randomUUID();
                ids.add(id);
                insertUnit.setObject(1, id);
                insertUnit.setObject(2, tenant);
                insertUnit.setString(3, "LOTE-" + i);
                insertUnit.setObject(4, type);
                insertUnit.executeUpdate();
                insertLink.setObject(1, tenant);
                insertLink.setObject(2, structure);
                insertLink.setObject(3, id);
                insertLink.setObject(4, left);
                insertLink.executeUpdate();
            }
        }
        return ids;
    }

    private static String moveStep(UUID unit, UUID parent) {
        return "{\"operation\":\"MOVE_UNIT\",\"unitId\":\"%s\",\"parentUnitId\":\"%s\"}".formatted(unit, parent);
    }

    private static String renameStep(UUID unit, String name) {
        return "{\"operation\":\"RENAME_UNIT\",\"unitId\":\"%s\",\"name\":\"%s\"}".formatted(unit, name);
    }

    private static String parentIn(JsonNode nodes, String code) {
        for (JsonNode node : nodes) {
            if (code.equals(node.get("code").asString())) {
                return node.get("parentUnitId").asString();
            }
        }
        throw new AssertionError("Unidade fora da prévia: " + code);
    }

    private String unitName(UUID unit) throws Exception {
        return read(mvc.perform(get("/v1/organizational-units/{id}", unit).with(user(tenant)))
                        .andReturn()
                        .getResponse()
                        .getContentAsString())
                .get("name")
                .asString();
    }

    private ResultActions parentOf(UUID unit, String asOf) throws Exception {
        return children(left, asOf);
    }

    private UUID draft(String on) throws Exception {
        return id(post(
                "/v1/reorganizations",
                "{\"structureId\":\"%s\",\"description\":\"Teste\",\"effectiveOn\":\"%s\"}".formatted(structure, on)));
    }

    private void steps(UUID id, long version, String steps) throws Exception {
        mvc.perform(MockMvcRequestBuilders.put("/v1/reorganizations/{id}/steps", id)
                        .with(admin(tenant))
                        .header("If-Match", "\"" + version + "\"")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"steps\":[" + steps + "]}"))
                .andExpect(status().isOk());
    }

    private ResultActions action(UUID id, String name, Long version, JwtRequestPostProcessor who) throws Exception {
        var request = MockMvcRequestBuilders.post("/v1/reorganizations/{id}/{name}", id, name)
                .with(who);
        if (version != null) {
            request.header("If-Match", "\"" + version + "\"");
        }
        return mvc.perform(request);
    }

    private ResultActions children(UUID parent, String asOf) throws Exception {
        return mvc.perform(get("/v1/structures/{s}/units/{u}/children", structure, parent)
                .param("asOf", asOf)
                .with(user(tenant)));
    }

    private UUID unit(String code) throws Exception {
        return id(post(
                "/v1/organizational-units",
                "{\"code\":\"%s\",\"name\":\"%s\",\"unitTypeId\":\"%s\",\"validFrom\":\"2026-01-01\"}"
                        .formatted(code, code, type)));
    }

    private void place(UUID unit, UUID parent) throws Exception {
        String parentJson = parent == null ? "null" : "\"" + parent + "\"";
        post(
                        "/v1/structures/" + structure + "/moves",
                        "{\"unitId\":\"%s\",\"parentUnitId\":%s,\"effectiveOn\":\"2026-01-01\"}"
                                .formatted(unit, parentJson))
                .andExpect(status().isOk());
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
