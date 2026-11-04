package br.com.davinci.erp.governance.access.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import br.com.davinci.erp.governance.support.Contracts;
import br.com.davinci.erp.governance.support.IntegrationTest;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

/** Papéis, atribuições com vigência, segregação de funções, trilha e isolamento. */
class AccessApiIT extends IntegrationTest {

    private static final String PAYER = "finance:payment:approve";
    private static final String CREATOR = "finance:payment:create";

    private UUID tenant;
    private UUID approver;
    private UUID clerk;

    @BeforeEach
    void papeis() throws Exception {
        tenant = activeTenant();
        approver = id(role("APROVADOR", PAYER));
        clerk = id(role("LANCADOR", CREATOR));
        post("/v1/sod-rules", """
                {"code":"PAGAR","description":"Quem lança não aprova","permissionA":"%s","permissionB":"%s"}
                """.formatted(PAYER, CREATOR)).andExpect(status().isCreated());
    }

    @Test
    void atribuicaoComVigenciaEEscopo() throws Exception {
        UUID entity = UUID.randomUUID();
        String body = grant("ana", approver, "LEGAL_ENTITY", entity, "2026-01-01")
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();
        Contracts.assertHttp("Assignment", body);
        UUID assignment = UUID.fromString(read(body).get("id").asString());

        access("ana", "2025-12-31").andExpect(jsonPath("$.items.length()").value(0));
        access("ana", "2026-06-01")
                .andExpect(jsonPath("$.items[0].roleCode").value("APROVADOR"))
                .andExpect(jsonPath("$.items[0].scopeId").value(entity.toString()))
                .andExpect(jsonPath("$.items[0].permissions[0]").value(PAYER));

        grant("ana", approver, "LEGAL_ENTITY", entity, "2026-03-01")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("overlapping-period"));

        post("/v1/assignments/" + assignment + "/revoke", "{\"effectiveOn\":\"2026-07-01\"}")
                .andExpect(status().isOk());
        access("ana", "2026-06-30").andExpect(jsonPath("$.items.length()").value(1));
        access("ana", "2026-07-01").andExpect(jsonPath("$.items.length()").value(0));

        var events = events("assignment", assignment, 2);
        assertThat(events.getLast().get("type").asString()).endsWith("assignment.revoked.v1");
    }

    @Test
    void segregacaoDeFuncoes() throws Exception {
        grant("bia", approver, "TENANT", null, "2026-01-01").andExpect(status().isCreated());
        grant("bia", clerk, "TENANT", null, "2026-02-01")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("sod-violation"));
        role("TUDO", PAYER + "\",\"" + CREATOR)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("sod-violation"));

        // ampliar um papel não pode criar conflito para quem já o tem
        mvc.perform(MockMvcRequestBuilders.patch("/v1/roles/{id}", approver)
                        .with(admin(tenant))
                        .header("If-Match", "\"0\"")
                        .contentType("application/merge-patch+json")
                        .content("{\"permissions\":[\"%s\",\"%s\"]}".formatted(PAYER, CREATOR)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("sod-violation"));

        // períodos que não se encontram não ferem a regra
        String first = grant("caio", approver, "TENANT", null, "2026-01-01")
                .andReturn()
                .getResponse()
                .getContentAsString();
        post("/v1/assignments/" + read(first).get("id").asString() + "/revoke", "{\"effectiveOn\":\"2026-02-01\"}")
                .andExpect(status().isOk());
        grant("caio", clerk, "TENANT", null, "2026-02-01").andExpect(status().isCreated());
    }

    @Test
    void regraNovaNaoEntraComConflitoExistente() throws Exception {
        UUID reader = id(role("LEITOR", "finance:report:read"));
        grant("davi", reader, "TENANT", null, "2026-01-01").andExpect(status().isCreated());
        grant("davi", approver, "TENANT", null, "2026-01-01").andExpect(status().isCreated());
        post("/v1/sod-rules", """
                {"code":"LER","description":"Teste","permissionA":"finance:report:read","permissionB":"%s"}
                """.formatted(PAYER))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("sod-violation"));
    }

    @Test
    void ninguemConcedeASiMesmo() throws Exception {
        grant("user-tenant-admin", approver, "TENANT", null, "2026-01-01")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("self-grant"));
    }

    @Test
    void papelInativoNaoConcedeNemRecebe() throws Exception {
        grant("eva", approver, "TENANT", null, "2026-01-01").andExpect(status().isCreated());
        String role = post("/v1/roles/" + approver + "/deactivate", "")
                .andExpect(jsonPath("$.status").value("INACTIVE"))
                .andReturn()
                .getResponse()
                .getContentAsString();
        Contracts.assertHttp("Role", role);
        access("eva", "2026-06-01").andExpect(jsonPath("$.items.length()").value(0));
        grant("fabio", approver, "TENANT", null, "2026-01-01").andExpect(status().isConflict());
        assertThat(events("role", approver, 2).getLast().get("type").asString()).endsWith("role.deactivated.v1");
    }

    @Test
    void validacaoPermissaoIsolamentoEAuditoria() throws Exception {
        role("RUIM", "Sem Formato").andExpect(status().isBadRequest());
        grant("gil", approver, "LEGAL_ENTITY", null, "2026-01-01").andExpect(status().isBadRequest());

        mvc.perform(MockMvcRequestBuilders.post("/v1/roles/{id}/deactivate", approver)
                        .with(user(tenant)))
                .andExpect(status().isForbidden());
        mvc.perform(get("/v1/roles/{id}", approver).with(user(activeTenant()))).andExpect(status().isNotFound());
        mvc.perform(get("/v1/history/role/{id}", approver).with(admin(tenant)))
                .andExpect(jsonPath("$.items[0].action").value("created"));
        mvc.perform(get("/v1/history/role/{id}", approver).with(user(tenant))).andExpect(status().isForbidden());
    }

    private ResultActions access(String subject, String asOf) throws Exception {
        return mvc.perform(
                get("/v1/subjects/{s}/access", subject).param("asOf", asOf).with(user(tenant)));
    }

    private ResultActions role(String code, String permissions) throws Exception {
        return post(
                "/v1/roles",
                "{\"code\":\"%s\",\"name\":\"Papel %s\",\"permissions\":[\"%s\"]}".formatted(code, code, permissions));
    }

    private ResultActions grant(String subject, UUID role, String scopeType, UUID scopeId, String from)
            throws Exception {
        return post("/v1/assignments", """
                {"subject":"%s","roleId":"%s","scopeType":"%s","scopeId":%s,"validFrom":"%s"}
                """.formatted(
                        subject, role, scopeType, scopeId == null ? "null" : "\"" + scopeId + "\"", from));
    }

    private ResultActions post(String path, String body) throws Exception {
        var request = MockMvcRequestBuilders.post(path).with(admin(tenant));
        if (!body.isEmpty()) {
            request.contentType(MediaType.APPLICATION_JSON).content(body);
        }
        return mvc.perform(request);
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
