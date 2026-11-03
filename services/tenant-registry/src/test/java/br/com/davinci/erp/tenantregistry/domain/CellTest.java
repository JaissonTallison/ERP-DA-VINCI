package br.com.davinci.erp.tenantregistry.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import br.com.davinci.erp.platform.kernel.CellId;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class CellTest {

    private static final Instant NOW = Instant.parse("2026-11-03T12:00:00Z");

    @Test
    void celulaNovaAbreVazia() {
        Cell cell = Cell.open(CellId.parse("br-1-pooled-01"), 500, NOW);

        assertThat(cell.status()).isEqualTo(CellStatus.OPEN);
        assertThat(cell.tenantCount()).isZero();
    }

    @Test
    void celulaSiloAtendeUmUnicoTenant() {
        assertThat(Cell.open(CellId.parse("eu-1-silo-acme"), 1, NOW).capacity()).isEqualTo(1);
        assertThatThrownBy(() -> Cell.open(CellId.parse("eu-1-silo-acme"), 2, NOW))
                .isInstanceOf(InvalidTenantDataException.class);
    }

    @Test
    void rejeitaCapacidadeForaDosLimites() {
        assertThatThrownBy(() -> Cell.open(CellId.parse("br-1-pooled-01"), 0, NOW))
                .isInstanceOf(InvalidTenantDataException.class);
        assertThatThrownBy(() -> Cell.open(CellId.parse("br-1-pooled-01"), Cell.MAX_CAPACITY + 1, NOW))
                .isInstanceOf(InvalidTenantDataException.class);
    }
}
