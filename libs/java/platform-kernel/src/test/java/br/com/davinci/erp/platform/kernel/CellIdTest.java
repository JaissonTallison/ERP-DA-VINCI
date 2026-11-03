package br.com.davinci.erp.platform.kernel;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class CellIdTest {

    @Test
    void decompoeRegiaoIsolamentoENome() {
        CellId cell = CellId.parse("eu-1-silo-acme-group");

        assertThat(cell.region()).isEqualTo(new RegionCode("eu-1"));
        assertThat(cell.isolation()).isEqualTo(IsolationModel.SILO);
        assertThat(cell.name()).isEqualTo("acme-group");
        assertThat(cell).hasToString("eu-1-silo-acme-group");
    }

    @ParameterizedTest
    @ValueSource(strings = {"br-1-pooled", "br-1-shared-01", "br-1-pooled-Cell", "br-1-pooled--01"})
    void rejeitaFormatoInvalido(String raw) {
        assertThatThrownBy(() -> CellId.parse(raw)).isInstanceOf(IllegalArgumentException.class);
    }
}
