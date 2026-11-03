package br.com.davinci.erp.platform.kernel;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class RegionCodeTest {

    @ParameterizedTest
    @ValueSource(strings = {"br-1", "eu-1", "us-12"})
    void aceitaFormatoValido(String raw) {
        assertThat(new RegionCode(raw).value()).isEqualTo(raw);
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "BR-1", "br1", "br-0", "bra-1", "br-123"})
    void rejeitaFormatoInvalido(String raw) {
        assertThatThrownBy(() -> new RegionCode(raw)).isInstanceOf(IllegalArgumentException.class);
    }
}
