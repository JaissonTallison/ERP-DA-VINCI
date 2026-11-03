package br.com.davinci.erp.platform.kernel;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class TenantIdTest {

    @Test
    void parseEToStringSaoSimetricos() {
        String raw = "0b6f1c1e-6a0e-4d7c-9a51-3a7f4f1b2c3d";
        assertThat(TenantId.parse(raw).toString()).isEqualTo(raw);
    }

    @Test
    void rejeitaValorMalformado() {
        assertThatThrownBy(() -> TenantId.parse("acme"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("acme");
    }
}
