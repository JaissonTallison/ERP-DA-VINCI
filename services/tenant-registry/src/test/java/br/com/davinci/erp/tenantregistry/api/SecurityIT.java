package br.com.davinci.erp.tenantregistry.api;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import br.com.davinci.erp.tenantregistry.support.IntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

class SecurityIT extends IntegrationTest {

    @Test
    void semTokenDevolve401() throws Exception {
        mvc.perform(get("/v1/tenants")).andExpect(status().isUnauthorized());
    }

    @Test
    void escopoDeLeituraNaoPodeEscrever() throws Exception {
        mvc.perform(get("/v1/cells").with(reader())).andExpect(status().isOk());
        mvc.perform(post("/v1/cells")
                        .with(reader())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"region\":\"br-1\",\"isolation\":\"pooled\",\"name\":\"01\",\"capacity\":1}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void healthEhPublicoEDemaisEndpointsDeGestaoNao() throws Exception {
        mvc.perform(get("/actuator/health")).andExpect(status().isOk());
        mvc.perform(get("/actuator/sbom")).andExpect(status().isUnauthorized());
    }
}
