package br.com.davinci.erp.organization.corporate.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import br.com.davinci.erp.organization.support.Cnpjs;
import br.com.davinci.erp.organization.support.IntegrationTest;
import br.com.davinci.erp.organization.support.Requests;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

/**
 * Critério de aceite da etapa 2: o tenant B não lê, não altera e não referencia dados do tenant A — pela API e direto
 * no banco com o papel da aplicação (RLS e chaves estrangeiras compostas).
 */
class TenantIsolationIT extends IntegrationTest {

    private UUID tenantA;
    private UUID tenantB;
    private UUID groupA;
    private UUID entityA;
    private UUID entityB;
    private String cnpjA;

    @BeforeEach
    void dados() throws Exception {
        tenantA = activeTenant();
        tenantB = activeTenant();
        groupA = create("/v1/economic-groups", tenantA, Requests.group(Requests.code()));
        cnpjA = Cnpjs.random();
        entityA = create(
                "/v1/legal-entities",
                tenantA,
                Requests.brazilianEntity(Requests.code(), cnpjA, groupA, "ACTIVE", "2026-01-01"));
        entityB = create(
                "/v1/legal-entities",
                tenantB,
                Requests.brazilianEntity(Requests.code(), Cnpjs.random(), null, "ACTIVE", "2026-01-01"));
    }

    @Test
    void pelaApiOutroTenantNaoExiste() throws Exception {
        mvc.perform(get("/v1/legal-entities/{id}", entityA).with(admin(tenantB)))
                .andExpect(status().isNotFound());
        mvc.perform(get("/v1/economic-groups/{id}", groupA).with(admin(tenantB)))
                .andExpect(status().isNotFound());
        mvc.perform(get("/v1/legal-entities").with(admin(tenantB)))
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.items[0].id").value(entityB.toString()));
        mvc.perform(patch("/v1/legal-entities/{id}", entityA)
                        .with(admin(tenantB))
                        .header("If-Match", "\"1\"")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"legalName\":\"Invasão\"}"))
                .andExpect(status().isNotFound());
        mvc.perform(post("/v1/legal-entities/{id}/close", entityA).with(admin(tenantB)))
                .andExpect(status().isNotFound());
        // referenciar o grupo de A a partir de B: o grupo "não existe" para B
        mvc.perform(put("/v1/legal-entities/{id}/economic-group", entityB)
                        .with(admin(tenantB))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"economicGroupId\":\"" + groupA + "\",\"effectiveOn\":\"2026-05-01\"}"))
                .andExpect(status().isNotFound());
        // o mesmo CNPJ pode existir em outro tenant (ex.: escritório contábil)
        create(
                "/v1/legal-entities",
                tenantB,
                Requests.brazilianEntity(Requests.code(), cnpjA, null, "ACTIVE", "2026-01-01"));
    }

    @Test
    void noBancoORlsEscondeOutroTenant() throws SQLException {
        try (Connection c = appRole()) {
            c.setAutoCommit(false);
            bind(c, tenantB);
            assertThat(count(c, "SELECT count(*) FROM legal_entity WHERE id = '" + entityA + "'"))
                    .isZero();
            assertThat(count(c, "SELECT count(*) FROM economic_group")).isZero();
            assertThat(count(c, "SELECT count(*) FROM legal_entity")).isEqualTo(1);
            c.rollback();
        }
    }

    @Test
    void noBancoSemTenantNadaFunciona() throws SQLException {
        try (Connection c = appRole();
                var st = c.createStatement()) {
            assertThatThrownBy(() -> st.executeQuery("SELECT count(*) FROM legal_entity"))
                    .isInstanceOf(SQLException.class)
                    .hasMessageContaining("app.tenant_id");
        }
    }

    @Test
    void noBancoNaoDaParaGravarParaOutroTenantNemReferenciarOutroTenant() throws SQLException {
        try (Connection c = appRole()) {
            c.setAutoCommit(false);
            bind(c, tenantB);
            // gravar linha do tenant A estando como B: barrado pela política (WITH CHECK)
            assertThatThrownBy(() -> exec(
                            c,
                            "INSERT INTO economic_group (id, tenant_id, code, name, reference_currency,"
                                    + " reference_timezone, status, created_at, updated_at, version) VALUES (gen_random_uuid(), '"
                                    + tenantA + "', 'X', 'X', 'BRL', 'UTC', 'ACTIVE', now(), now(), 0)"))
                    .isInstanceOf(SQLException.class)
                    .hasMessageContaining("row-level security");
            c.rollback();
            bind(c, tenantB);
            // vincular a entidade de B ao grupo de A: a chave estrangeira inclui o tenant e não encontra o grupo
            assertThatThrownBy(() -> exec(
                            c,
                            "INSERT INTO legal_entity_group_assignment (tenant_id, legal_entity_id,"
                                    + " economic_group_id, valid_from) VALUES ('" + tenantB + "', '" + entityB + "', '"
                                    + groupA
                                    + "', DATE '2026-05-01')"))
                    .isInstanceOf(SQLException.class)
                    .hasMessageContaining("foreign key");
            c.rollback();
        }
    }

    @Test
    void auditoriaEhSomenteInsercao() throws SQLException {
        try (Connection c = appRole()) {
            c.setAutoCommit(false);
            bind(c, tenantA);
            assertThat(count(c, "SELECT count(*) FROM audit_log WHERE aggregate_id = '" + entityA + "'"))
                    .isEqualTo(1);
            assertThatThrownBy(() -> exec(c, "DELETE FROM audit_log")).hasMessageContaining("permission denied");
            c.rollback();
        }
    }

    private UUID create(String path, UUID tenant, String body) throws Exception {
        String response = mvc.perform(post(path)
                        .with(admin(tenant))
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();
        return UUID.fromString(read(response).get("id").asString());
    }

    private static void bind(Connection c, UUID tenant) throws SQLException {
        try (var st = c.prepareStatement("SELECT set_config('app.tenant_id', ?, true)")) {
            st.setString(1, tenant.toString());
            st.execute();
        }
    }

    private static long count(Connection c, String sql) throws SQLException {
        try (var st = c.createStatement();
                var rs = st.executeQuery(sql)) {
            rs.next();
            return rs.getLong(1);
        }
    }

    private static void exec(Connection c, String sql) throws SQLException {
        try (var st = c.createStatement()) {
            st.execute(sql);
        }
    }
}
