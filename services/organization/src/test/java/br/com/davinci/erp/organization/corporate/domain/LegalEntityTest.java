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
import br.com.davinci.erp.platform.kernel.TenantId;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Currency;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class LegalEntityTest {

    private static final Instant NOW = Instant.parse("2026-11-03T12:00:00Z");
    private static final LocalDate DAY = LocalDate.of(2026, 11, 1);
    private static final CountryCode BR = new CountryCode("BR");

    static LegalEntity planned() {
        var profile = new LegalEntity.Profile(
                "Acme Indústria Ltda",
                null,
                "LTDA",
                Currency.getInstance("BRL"),
                ZoneId.of("America/Sao_Paulo"),
                Locale.forLanguageTag("pt-BR"),
                1,
                new PostalAddress(BR, List.of("Av. Paulista, 1000"), "São Paulo", "SP", "01310-100"));
        return LegalEntity.plan(
                UUID.randomUUID(), new TenantId(UUID.randomUUID()), BusinessCode.of("ACME"), BR, "BR-SP", profile, NOW);
    }

    @Test
    void cicloDeVidaCompleto() {
        LegalEntity active = planned().activate(DAY, NOW);
        LegalEntity suspended = active.suspend(NOW);
        LegalEntity reactivated = suspended.reactivate(NOW);
        LegalEntity closed = reactivated.close(DAY.plusDays(10), NOW);

        assertThat(active.status()).isEqualTo(LegalEntityStatus.ACTIVE);
        assertThat(active.incorporatedOn()).isEqualTo(DAY);
        assertThat(suspended.status()).isEqualTo(LegalEntityStatus.SUSPENDED);
        assertThat(reactivated.status()).isEqualTo(LegalEntityStatus.ACTIVE);
        assertThat(closed.status()).isEqualTo(LegalEntityStatus.CLOSED);
        assertThat(closed.closedOn()).isEqualTo(DAY.plusDays(10));
        assertThat(closed.version()).isEqualTo(4);
    }

    @Test
    void transicoesRepetidasNaoMudamNada() {
        LegalEntity active = planned().activate(DAY, NOW);
        assertThat(active.activate(DAY, NOW)).isSameAs(active);
        assertThat(active.reactivate(NOW)).isSameAs(active);
    }

    @Test
    void transicoesForaDeOrdem() {
        assertThatThrownBy(() -> planned().suspend(NOW)).isInstanceOf(InvalidTransitionException.class);
        assertThatThrownBy(() -> planned().close(DAY, NOW)).isInstanceOf(InvalidTransitionException.class);
        LegalEntity closed = planned().activate(DAY, NOW).close(DAY, NOW);
        assertThatThrownBy(() -> closed.reactivate(NOW)).isInstanceOf(InvalidTransitionException.class);
        assertThatThrownBy(() -> planned().activate(DAY, NOW).close(DAY.minusDays(1), NOW))
                .isInstanceOf(InvalidValueException.class);
    }

    @Test
    void moedaFuncionalSoMudaAntesDeAtivar() {
        LegalEntity planned = planned();
        var inUsd = new LegalEntity.Profile(
                planned.legalName(),
                null,
                "LTDA",
                Currency.getInstance("USD"),
                planned.timezone(),
                planned.defaultLocale(),
                1,
                planned.registeredAddress());

        assertThat(planned.update(inUsd, NOW).functionalCurrency().getCurrencyCode())
                .isEqualTo("USD");
        assertThatThrownBy(() -> planned.activate(DAY, NOW).update(inUsd, NOW))
                .isInstanceOf(InvalidValueException.class);
    }

    @Test
    void sedeLegalNoPaisDaEntidadeESubdivisaoDoPais() {
        LegalEntity e = planned();
        var abroad = new LegalEntity.Profile(
                e.legalName(),
                null,
                null,
                e.functionalCurrency(),
                e.timezone(),
                e.defaultLocale(),
                1,
                new PostalAddress(new CountryCode("AR"), List.of("Av. Corrientes 1"), "Buenos Aires", null, null));
        assertThatThrownBy(() -> e.update(abroad, NOW)).isInstanceOf(InvalidValueException.class);
        assertThatThrownBy(
                        () -> LegalEntity.plan(UUID.randomUUID(), e.tenantId(), e.code(), BR, "AR-B", e.profile(), NOW))
                .isInstanceOf(InvalidValueException.class);
    }

    @Test
    void entidadeBrasileiraAtivaPrecisaDeCnpjPrincipalVigente() {
        var cnpj = new TaxIdentifierRecord(
                UUID.randomUUID(), TaxIdentifier.of("BR_CNPJ", "11222333000181"), true, EffectivePeriod.from(DAY));

        TaxRequirements.requireForActivation(BR, List.of(cnpj), DAY);
        assertThatThrownBy(() -> TaxRequirements.requireForActivation(BR, List.of(), DAY))
                .isInstanceOf(ConflictException.class);
        assertThatThrownBy(() -> TaxRequirements.requireForActivation(BR, List.of(cnpj), DAY.minusDays(1)))
                .isInstanceOf(ConflictException.class);
        TaxRequirements.requireForActivation(new CountryCode("AR"), List.of(), DAY);
    }
}
