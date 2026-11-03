package br.com.davinci.erp.tenantregistry.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class TenantSlugTest {

    @ParameterizedTest
    @ValueSource(strings = {"acme", "grupo-acme-br", "a1b", "abcdefghijabcdefghijabcdefghijabcdefghij"})
    void aceitaSlugValido(String raw) {
        assertThat(new TenantSlug(raw).value()).isEqualTo(raw);
    }

    @ParameterizedTest
    @ValueSource(
            strings = {"ab", "Acme", "1acme", "acme-", "ac--me", "acme_br", "abcdefghijabcdefghijabcdefghijabcdefghijx"
            })
    void rejeitaFormatoInvalido(String raw) {
        assertThatThrownBy(() -> new TenantSlug(raw)).isInstanceOf(InvalidTenantDataException.class);
    }

    @ParameterizedTest
    @ValueSource(strings = {"admin", "api", "www"})
    void rejeitaSlugReservado(String raw) {
        assertThatThrownBy(() -> new TenantSlug(raw))
                .isInstanceOf(InvalidTenantDataException.class)
                .hasMessageContaining("reservado");
    }
}
