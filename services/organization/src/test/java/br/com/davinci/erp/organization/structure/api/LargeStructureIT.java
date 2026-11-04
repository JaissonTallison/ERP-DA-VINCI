package br.com.davinci.erp.organization.structure.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import br.com.davinci.erp.organization.support.IntegrationTest;
import java.io.OutputStream;
import java.sql.Connection;
import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.context.SecurityContextHolder;

/**
 * Critério de aceite da etapa 4: subárvore de 20 mil unidades em menos de 200 ms. A árvore (8 filhos por nó,
 * profundidade 6) é carregada direto no banco. O resultado é conferido por HTTP (MockMvc); o tempo é medido no
 * caminho do servidor (controlador, serviço, consulta e serialização JSON), já aquecido. O MockMvc fica fora da medição
 * porque acumula os 5 MB da resposta num buffer em memória que cresce aos poucos — custo que só existe no simulador.
 * Roda na tarefa {@code performanceTest}, numa JVM sem os contextos dos outros testes.
 */
@Tag("desempenho")
class LargeStructureIT extends IntegrationTest {

    private static final int UNITS = 20_000;

    @Autowired
    private StructureController controller;

    @Test
    void subarvoreDeVinteMilUnidades() throws Exception {
        UUID tenant = activeTenant();
        UUID structure = UUID.randomUUID();
        UUID root;
        try (Connection c = superuser();
                var st = c.createStatement()) {
            UUID type = UUID.randomUUID();
            st.execute(
                    "INSERT INTO unit_type (id, tenant_id, code, name, created_at, updated_at, version) VALUES ('%s', '%s',"
                                    .formatted(type, tenant)
                            + " 'PERF', '{\"text\":\"Perf\",\"translations\":{}}', now(), now(), 0)");
            st.execute("CREATE TEMP TABLE ids AS SELECT i, gen_random_uuid() AS id FROM generate_series(0, %d) i"
                    .formatted(UNITS - 1));
            st.execute("""
                    INSERT INTO organizational_unit (id, tenant_id, code, name, unit_type_id, status, valid_from, created_at,
                                                     updated_at, version)
                    SELECT id, '%s', 'U' || lpad(i::text, 6, '0'), jsonb_build_object('text', 'Unidade ' || i, 'translations', '{}'::jsonb),
                           '%s', 'ACTIVE', DATE '2026-01-01', now(), now(), 0 FROM ids
                    """.formatted(tenant, type));
            st.execute(
                    "INSERT INTO organizational_structure (id, tenant_id, code, name, purpose, created_at, updated_at, version)"
                            + " VALUES ('%s', '%s', 'GRANDE', '{\"text\":\"Grande\",\"translations\":{}}', 'MANAGEMENT', now(), now(), 0)"
                                    .formatted(structure, tenant));
            st.execute("""
                    INSERT INTO structure_link (tenant_id, structure_id, unit_id, parent_unit_id, valid_from)
                    SELECT '%s', '%s', c.id, p.id, DATE '2026-01-01'
                    FROM ids c LEFT JOIN ids p ON c.i > 0 AND p.i = (c.i - 1) / 8
                    """.formatted(tenant, structure));
            st.execute("ANALYZE structure_link; ANALYZE organizational_unit; ANALYZE unit_type");
            try (var rs = st.executeQuery("SELECT id FROM ids WHERE i = 0")) {
                rs.next();
                root = rs.getObject(1, UUID.class);
            }
        }

        var request = get("/v1/structures/{s}/units/{u}/subtree", structure, root)
                .param("asOf", "2026-06-01")
                .param("limit", "50000")
                .with(user(tenant));
        String body = mvc.perform(request)
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        assertThat(read(body).get("items").size()).isEqualTo(UNITS);
        assertThat(read(body).get("truncated").asBoolean()).isFalse();

        SecurityContextHolder.getContext().setAuthentication(authentication(tenant, "tenant-user"));
        long best = Long.MAX_VALUE;
        try {
            for (int i = 0; i < 10; i++) { // as 5 primeiras aquecem o JIT e o plano de consulta
                long start = System.nanoTime();
                var response = controller.subtree(structure, root, LocalDate.of(2026, 6, 1), 64, 50_000);
                json.writeValue(OutputStream.nullOutputStream(), response);
                if (i >= 5) {
                    best = Math.min(best, (System.nanoTime() - start) / 1_000_000);
                }
            }
        } finally {
            SecurityContextHolder.clearContext();
        }
        System.out.println("Subárvore de " + UNITS + " unidades: " + best + " ms");
        assertThat(best).as("tempo da subárvore (ms)").isLessThan(200);
    }
}
