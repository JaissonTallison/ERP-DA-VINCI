package br.com.davinci.erp.organization.shared.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class TaxIdentifierTest {

    @Test
    void cnpjNumericoEhNormalizado() {
        TaxIdentifier cnpj = TaxIdentifier.of("BR_CNPJ", "11.222.333/0001-81");

        assertThat(cnpj.value()).isEqualTo("11222333000181");
        assertThat(cnpj.scheme().country()).isEqualTo("BR");
    }

    @Test
    void cnpjAlfanumericoDaReceitaEhAceitoEmQualquerCaixa() {
        assertThat(TaxIdentifier.of("BR_CNPJ", "12.abc.345/01de-35").value()).isEqualTo("12ABC34501DE35");
    }

    @ParameterizedTest
    @ValueSource(
            strings = {"11.222.333/0001-82", "12.ABC.345/01DE-36", "11111111111111", "1122233300018", "12ABC34501DEXX"})
    void cnpjInvalidoEhRecusado(String raw) {
        assertThatThrownBy(() -> TaxIdentifier.of("BR_CNPJ", raw))
                .isInstanceOf(InvalidValueException.class)
                .extracting(e -> ((InvalidValueException) e).code())
                .isEqualTo("invalid-tax-identifier");
    }

    @Test
    void esquemasSoDeFormato() {
        assertThat(TaxIdentifier.of("US_EIN", "12-3456789").value()).isEqualTo("123456789");
        assertThat(TaxIdentifier.of("EU_VAT", "pt 123456789").value()).isEqualTo("PT123456789");
        assertThatThrownBy(() -> TaxIdentifier.of("US_EIN", "1234")).isInstanceOf(InvalidValueException.class);
    }

    @Test
    void esquemaDesconhecido() {
        assertThatThrownBy(() -> TaxIdentifier.of("XX_ABC", "1")).isInstanceOf(InvalidValueException.class);
    }
}
