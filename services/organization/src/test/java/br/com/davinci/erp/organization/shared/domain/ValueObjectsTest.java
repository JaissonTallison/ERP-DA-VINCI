package br.com.davinci.erp.organization.shared.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class ValueObjectsTest {

    @Test
    void codigoDeNegocioViraMaiusculo() {
        assertThat(BusinessCode.of(" acme-ind ").value()).isEqualTo("ACME-IND");
        assertThatThrownBy(() -> BusinessCode.of("com espaço")).isInstanceOf(InvalidValueException.class);
    }

    @Test
    void paisPrecisaSerIso() {
        assertThat(new CountryCode("BR").value()).isEqualTo("BR");
        assertThatThrownBy(() -> new CountryCode("XX")).isInstanceOf(InvalidValueException.class);
        assertThatThrownBy(() -> new CountryCode("br")).isInstanceOf(InvalidValueException.class);
    }

    @Test
    void vigenciaSemiaberta() {
        var period = new EffectivePeriod(LocalDate.of(2026, 1, 1), LocalDate.of(2026, 7, 1));

        assertThat(period.contains(LocalDate.of(2026, 1, 1))).isTrue();
        assertThat(period.contains(LocalDate.of(2026, 6, 30))).isTrue();
        assertThat(period.contains(LocalDate.of(2026, 7, 1))).isFalse();
        assertThatThrownBy(() -> new EffectivePeriod(LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 1)))
                .isInstanceOf(InvalidValueException.class);
    }

    @Test
    void enderecoBrasileiroNormalizaCep() {
        var address =
                new PostalAddress(new CountryCode("BR"), List.of("Av. Paulista, 1000"), "São Paulo", "SP", "01310100");

        assertThat(address.postalCode()).isEqualTo("01310-100");
        assertThatThrownBy(() -> new PostalAddress(new CountryCode("BR"), List.of("Rua X"), "São Paulo", "SP", "123"))
                .isInstanceOf(InvalidValueException.class);
        assertThatThrownBy(() -> new PostalAddress(new CountryCode("BR"), List.of(), "São Paulo", "SP", "01310-100"))
                .isInstanceOf(InvalidValueException.class);
    }

    @Test
    void enderecoDePaisSemRegraAceitaCodigoLivreOuAusente() {
        assertThat(new PostalAddress(new CountryCode("AR"), List.of("Av. Corrientes 1"), "Buenos Aires", null, null)
                        .postalCode())
                .isNull();
    }

    @ParameterizedTest
    @ValueSource(strings = {"America/Sao_Paulo", "Europe/Lisbon", "UTC"})
    void fusoComNomeDeRegiao(String zone) {
        assertThat(RegionalSettings.timezone("timezone", zone).getId()).isEqualTo(zone);
    }

    @ParameterizedTest
    @ValueSource(strings = {"GMT-3", "Etc/GMT+3", "UTC-03:00", "-03:00", "America/Nowhere"})
    void fusoComDeslocamentoFixoOuInexistenteEhRecusado(String zone) {
        assertThatThrownBy(() -> RegionalSettings.timezone("timezone", zone)).isInstanceOf(InvalidValueException.class);
    }

    @Test
    void moedaEIdioma() {
        assertThat(RegionalSettings.currency("c", "BRL").getCurrencyCode()).isEqualTo("BRL");
        assertThatThrownBy(() -> RegionalSettings.currency("c", "brl")).isInstanceOf(InvalidValueException.class);
        assertThatThrownBy(() -> RegionalSettings.currency("c", "XYZ")).isInstanceOf(InvalidValueException.class);
        assertThat(RegionalSettings.locale("l", "pt-BR").toLanguageTag()).isEqualTo("pt-BR");
        assertThatThrownBy(() -> RegionalSettings.locale("l", "pt_BR!")).isInstanceOf(InvalidValueException.class);
    }
}
