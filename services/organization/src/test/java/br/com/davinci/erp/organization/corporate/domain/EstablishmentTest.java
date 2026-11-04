package br.com.davinci.erp.organization.corporate.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import br.com.davinci.erp.organization.shared.domain.BusinessCode;
import br.com.davinci.erp.organization.shared.domain.ConflictException;
import br.com.davinci.erp.organization.shared.domain.CountryCode;
import br.com.davinci.erp.organization.shared.domain.EffectivePeriod;
import br.com.davinci.erp.organization.shared.domain.InvalidValueException;
import br.com.davinci.erp.organization.shared.domain.PostalAddress;
import br.com.davinci.erp.organization.shared.domain.TaxIdentifier;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class EstablishmentTest {

    private static final Instant NOW = Instant.parse("2026-11-03T12:00:00Z");
    private static final LocalDate DAY = LocalDate.of(2026, 2, 1);
    private static final PostalAddress JOINVILLE =
            new PostalAddress(new CountryCode("BR"), List.of("Rua das Flores, 10"), "Joinville", "SC", "89201-000");

    private static LegalEntity active() {
        return LegalEntityTest.planned().activate(LocalDate.of(2026, 1, 1), NOW);
    }

    private static Establishment open(LegalEntity entity) {
        return Establishment.open(
                UUID.randomUUID(),
                entity,
                BusinessCode.of("FAB-JOI"),
                new Establishment.Profile(
                        "Fábrica Joinville", EstablishmentType.PLANT, JOINVILLE, ZoneId.of("America/Sao_Paulo")),
                DAY,
                NOW);
    }

    @Test
    void abreSoComEntidadeAtivaENoPaisDela() {
        assertThat(open(active()).status()).isEqualTo(EstablishmentStatus.ACTIVE);
        assertThatThrownBy(() -> open(LegalEntityTest.planned()))
                .isInstanceOf(ConflictException.class)
                .extracting(e -> ((ConflictException) e).code())
                .isEqualTo("legal-entity-not-active");
        var abroad = new PostalAddress(new CountryCode("AR"), List.of("Av. Corrientes 1"), "Buenos Aires", null, null);
        assertThatThrownBy(() -> Establishment.open(
                        UUID.randomUUID(),
                        active(),
                        BusinessCode.of("X"),
                        new Establishment.Profile("X", EstablishmentType.BRANCH, abroad, ZoneId.of("UTC")),
                        DAY,
                        NOW))
                .isInstanceOf(InvalidValueException.class);
    }

    @Test
    void cicloDeVidaEReativacaoExigeEntidadeAtiva() {
        LegalEntity entity = active();
        Establishment suspended = open(entity).suspend(NOW);

        assertThat(suspended.suspend(NOW)).isSameAs(suspended);
        assertThatThrownBy(() -> suspended.reactivate(entity.suspend(NOW), NOW)).isInstanceOf(ConflictException.class);
        Establishment closed = suspended.reactivate(entity, NOW).close(DAY.plusDays(30), NOW);
        assertThat(closed.status()).isEqualTo(EstablishmentStatus.CLOSED);
        assertThatThrownBy(() -> closed.reactivate(entity, NOW)).isInstanceOf(InvalidTransitionException.class);
        assertThatThrownBy(() -> open(entity).close(DAY.minusDays(1), NOW)).isInstanceOf(InvalidValueException.class);
    }

    @Test
    void cnpjDoEstabelecimentoTemARaizDaEntidade() {
        var entityCnpj = new TaxIdentifierRecord(
                UUID.randomUUID(), TaxIdentifier.of("BR_CNPJ", "11222333000181"), true, EffectivePeriod.from(DAY));
        LegalEntity entity = active();

        TaxCoherence.requireCoherent(TaxIdentifier.of("BR_CNPJ", "11222333000262"), entity, List.of(entityCnpj), DAY);
        assertThatThrownBy(() -> TaxCoherence.requireCoherent(
                        TaxIdentifier.of("BR_CNPJ", "12ABC34501DE35"), entity, List.of(entityCnpj), DAY))
                .isInstanceOf(InvalidValueException.class)
                .extracting(e -> ((InvalidValueException) e).code())
                .isEqualTo("tax-identifier-mismatch");
        assertThatThrownBy(() -> TaxCoherence.requireCoherent(
                        TaxIdentifier.of("BR_CNPJ", "11222333000262"), entity, List.of(), DAY))
                .isInstanceOf(InvalidValueException.class);
        assertThatThrownBy(() -> TaxCoherence.requireCoherent(
                        TaxIdentifier.of("US_EIN", "123456789"), entity, List.of(entityCnpj), DAY))
                .isInstanceOf(InvalidValueException.class);
    }
}
