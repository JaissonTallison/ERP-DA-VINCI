package br.com.davinci.erp.tenantregistry.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import br.com.davinci.erp.tenantregistry.support.Contracts;
import br.com.davinci.erp.tenantregistry.support.IntegrationTest;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;
import tools.jackson.databind.JsonNode;

class TenantApiIT extends IntegrationTest {

    @Test
    void registraTenantNaCelulaDaRegiaoEPublicaEvento() throws Exception {
        String region = uniqueRegion();
        registerCell(region, "pooled", "01", 10);
        String slug = uniqueSlug();

        String body = registerTenant(UUID.randomUUID().toString(), slug, region, "pooled", "corr-registro-1")
                .andExpect(status().isCreated())
                .andExpect(header().string("X-Correlation-Id", "corr-registro-1"))
                .andExpect(jsonPath("$.status").value("PROVISIONING"))
                .andExpect(jsonPath("$.cellId").value(region + "-pooled-01"))
                .andReturn()
                .getResponse()
                .getContentAsString();
        Contracts.assertMatchesHttpSchema("Tenant", body);
        String id = read(body).get("id").asString();

        List<JsonNode> events = eventsFor(id, 1);
        assertThat(events).hasSize(1);
        JsonNode event = events.getFirst();
        assertThat(event.get("type").asString()).isEqualTo("br.com.davinci.tenantregistry.tenant.registered.v1");
        assertThat(event.get("correlationid").asString()).isEqualTo("corr-registro-1");
        assertThat(event.get("data").get("slug").asString()).isEqualTo(slug);
    }

    @Test
    void repetirComMesmaChaveDevolveOMesmoTenantSemNovoEvento() throws Exception {
        String region = uniqueRegion();
        registerCell(region, "pooled", "01", 10);
        String key = UUID.randomUUID().toString();
        String slug = uniqueSlug();

        String first = registerTenant(key, slug, region, "pooled", null)
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();
        String second = registerTenant(key, slug, region, "pooled", null)
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();

        String id = read(first).get("id").asString();
        assertThat(read(second).get("id").asString()).isEqualTo(id);
        assertThat(outboxCount(id)).isEqualTo(1);
    }

    @Test
    void mesmaChaveComOutroConteudoEhRecusada() throws Exception {
        String region = uniqueRegion();
        registerCell(region, "pooled", "01", 10);
        String key = UUID.randomUUID().toString();

        registerTenant(key, uniqueSlug(), region, "pooled", null).andExpect(status().isCreated());
        registerTenant(key, uniqueSlug(), region, "pooled", null)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("idempotency-key-reused"));
    }

    @Test
    void slugDuplicadoEhRecusado() throws Exception {
        String region = uniqueRegion();
        registerCell(region, "pooled", "01", 10);
        String slug = uniqueSlug();

        registerTenant(UUID.randomUUID().toString(), slug, region, "pooled", null)
                .andExpect(status().isCreated());
        String problem = registerTenant(UUID.randomUUID().toString(), slug, region, "pooled", null)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("slug-taken"))
                .andReturn()
                .getResponse()
                .getContentAsString();
        Contracts.assertMatchesHttpSchema("Problem", problem);
    }

    @Test
    void distribuiEntreCelulasERespeitaCapacidade() throws Exception {
        String region = uniqueRegion();
        registerCell(region, "bridge", "01", 1);
        registerCell(region, "bridge", "02", 1);

        String a = cellOf(registerTenant(UUID.randomUUID().toString(), uniqueSlug(), region, "bridge", null));
        String b = cellOf(registerTenant(UUID.randomUUID().toString(), uniqueSlug(), region, "bridge", null));

        assertThat(List.of(a, b)).containsExactlyInAnyOrder(region + "-bridge-01", region + "-bridge-02");
        registerTenant(UUID.randomUUID().toString(), uniqueSlug(), region, "bridge", null)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("no-cell-capacity"));
    }

    @Test
    void cicloDeVidaCompletoPublicaEventosEmOrdem() throws Exception {
        String region = uniqueRegion();
        registerCell(region, "pooled", "01", 10);
        String id = read(registerTenant(UUID.randomUUID().toString(), uniqueSlug(), region, "pooled", null)
                        .andReturn()
                        .getResponse()
                        .getContentAsString())
                .get("id")
                .asString();

        mvc.perform(post("/v1/tenants/{id}/suspend", id)
                        .with(writer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"teste\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("invalid-transition"));

        mvc.perform(post("/v1/tenants/{id}/activate", id).with(writer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ACTIVE"))
                .andExpect(jsonPath("$.version").value(1));
        mvc.perform(post("/v1/tenants/{id}/activate", id).with(writer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.version").value(1));
        mvc.perform(post("/v1/tenants/{id}/suspend", id)
                        .with(writer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"\"}"))
                .andExpect(status().isBadRequest());
        mvc.perform(post("/v1/tenants/{id}/suspend", id)
                        .with(writer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"Inadimplência\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SUSPENDED"))
                .andExpect(jsonPath("$.statusReason").value("Inadimplência"));
        mvc.perform(post("/v1/tenants/{id}/reactivate", id).with(writer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ACTIVE"))
                .andExpect(jsonPath("$.version").value(3));

        List<String> types = eventsFor(id, 4).stream()
                .map(e -> e.get("type").asString().replace("br.com.davinci.tenantregistry.tenant.", ""))
                .toList();
        assertThat(types).containsExactly("registered.v1", "activated.v1", "suspended.v1", "reactivated.v1");
    }

    @Test
    void listaPaginadaPorCursor() throws Exception {
        String region = uniqueRegion();
        registerCell(region, "pooled", "01", 10);
        for (int i = 0; i < 3; i++) {
            registerTenant(UUID.randomUUID().toString(), uniqueSlug(), region, "pooled", null)
                    .andExpect(status().isCreated());
        }

        String first = mvc.perform(get("/v1/tenants")
                        .param("region", region)
                        .param("limit", "2")
                        .with(reader()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(2))
                .andReturn()
                .getResponse()
                .getContentAsString();
        Contracts.assertMatchesHttpSchema("TenantPage", first);
        String cursor = read(first).get("nextCursor").asString();

        mvc.perform(get("/v1/tenants")
                        .param("region", region)
                        .param("limit", "2")
                        .param("cursor", cursor)
                        .with(reader()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.nextCursor").doesNotExist());
    }

    @Test
    void tenantInexistenteDevolve404() throws Exception {
        mvc.perform(get("/v1/tenants/{id}", UUID.randomUUID()).with(reader()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("tenant-not-found"));
    }

    @Test
    void celulaSiloComCapacidadeMaiorQueUmEhRecusada() throws Exception {
        mvc.perform(post("/v1/cells")
                        .with(writer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(cellJson(uniqueRegion(), "silo", "acme", 2)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("invalid-field"));
    }

    private void registerCell(String region, String isolation, String name, int capacity) throws Exception {
        String body = mvc.perform(post("/v1/cells")
                        .with(writer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(cellJson(region, isolation, name, capacity)))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();
        Contracts.assertMatchesHttpSchema("Cell", body);
    }

    private ResultActions registerTenant(String key, String slug, String region, String isolation, String correlationId)
            throws Exception {
        var request = post("/v1/tenants")
                .with(writer())
                .header("Idempotency-Key", key)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"slug":"%s","displayName":"Empresa %s","region":"%s","isolation":"%s"}
                        """.formatted(slug, slug, region, isolation));
        if (correlationId != null) {
            request.header("X-Correlation-Id", correlationId);
        }
        return mvc.perform(request);
    }

    private String cellOf(ResultActions result) throws Exception {
        return read(result.andExpect(status().isCreated())
                        .andReturn()
                        .getResponse()
                        .getContentAsString())
                .get("cellId")
                .asString();
    }

    private static String cellJson(String region, String isolation, String name, int capacity) {
        return """
                {"region":"%s","isolation":"%s","name":"%s","capacity":%d}
                """.formatted(region, isolation, name, capacity);
    }
}
