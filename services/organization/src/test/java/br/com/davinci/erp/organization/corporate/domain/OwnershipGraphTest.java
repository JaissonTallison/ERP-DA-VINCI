package br.com.davinci.erp.organization.corporate.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import br.com.davinci.erp.organization.corporate.domain.OwnershipInterest.Holder;
import br.com.davinci.erp.organization.corporate.domain.OwnershipInterest.HolderType;
import br.com.davinci.erp.organization.shared.domain.ConflictException;
import br.com.davinci.erp.organization.shared.domain.EffectivePeriod;
import br.com.davinci.erp.platform.kernel.TenantId;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** Casos de referência da participação indireta, controle, soma e ciclo (aceite da etapa 7). */
class OwnershipGraphTest {

    private static final LocalDate JAN = LocalDate.of(2026, 1, 1);
    private static final TenantId TENANT = new TenantId(UUID.randomUUID());

    private final UUID a = UUID.randomUUID();
    private final UUID b = UUID.randomUUID();
    private final UUID c = UUID.randomUUID();
    private final UUID d = UUID.randomUUID();
    private final UUID person = UUID.randomUUID();

    @Test
    void cadeiaSimplesMultiplicaAsParticipacoes() {
        // pessoa 80% de A; A 50% de B; B 50% de C → pessoa tem 20% de C
        var chart = OwnershipGraph.chart(
                c, List.of(external(person, a, "80"), interest(a, b, "50", "50"), interest(b, c, "50", "50")));

        assertThat(share(chart, external(person))).isEqualByComparingTo("20");
        assertThat(share(chart, entity(a))).isEqualByComparingTo("25");
        assertThat(share(chart, entity(b))).isEqualByComparingTo("50");
    }

    @Test
    void caminhosMultiplosSomam() {
        // A 60% de B; A 30% de C; B 40% de D; C 50% de D → A tem 0,6×0,4 + 0,3×0,5 = 39% de D
        var chart = OwnershipGraph.chart(
                d,
                List.of(
                        interest(a, b, "60", "60"),
                        interest(a, c, "30", "30"),
                        interest(b, d, "40", "40"),
                        interest(c, d, "50", "50")));

        assertThat(share(chart, entity(a))).isEqualByComparingTo("39");
        assertThat(chart.edges()).hasSize(4);
    }

    @Test
    void controladorFinalSegueAMaioriaDeVotos() {
        var chart = OwnershipGraph.chart(
                c, List.of(external(person, a, "51"), interest(a, b, "10", "60"), interest(b, c, "30", "70")));

        assertThat(chart.controlChain()).containsExactly(entity(b), entity(a), external(person));
        assertThat(chart.ultimateController()).isEqualTo(external(person));
    }

    @Test
    void semMaioriaNaoHaControlador() {
        var chart = OwnershipGraph.chart(c, List.of(interest(a, c, "50", "50"), interest(b, c, "50", "50")));

        assertThat(chart.ultimateController()).isNull();
    }

    @Test
    void somaAcimaDeCemEmQualquerDataFutura() {
        OwnershipInterest later = interest(a, c, "60", "60", JAN.plusMonths(6));

        assertThatThrownBy(() -> OwnershipGraph.requireValid(List.of(later), interest(b, c, "50", "50")))
                .isInstanceOfSatisfying(
                        ConflictException.class, e -> assertThat(e.code()).isEqualTo("ownership-over-100"));
        OwnershipGraph.requireValid(List.of(later), interest(b, c, "40", "40"));
    }

    @Test
    void cicloDiretoEIndiretoSaoRejeitados() {
        List<OwnershipInterest> existing = List.of(interest(a, b, "50", "50"), interest(b, c, "50", "50"));

        assertThatThrownBy(() -> OwnershipGraph.requireValid(existing, interest(c, a, "10", "10")))
                .isInstanceOfSatisfying(
                        ConflictException.class, e -> assertThat(e.code()).isEqualTo("ownership-cycle"));
        assertThatThrownBy(() -> OwnershipGraph.requireValid(existing, interest(b, a, "10", "10")))
                .isInstanceOf(ConflictException.class);
        OwnershipGraph.requireValid(existing, interest(a, c, "10", "10"));
    }

    private static BigDecimal share(OwnershipGraph.Chart chart, Holder holder) {
        return chart.holders().stream()
                .filter(h -> h.holder().equals(holder))
                .findFirst()
                .orElseThrow()
                .effectiveCapital();
    }

    private static Holder entity(UUID id) {
        return new Holder(HolderType.LEGAL_ENTITY, id);
    }

    private static Holder external(UUID id) {
        return new Holder(HolderType.EXTERNAL_PARTY, id);
    }

    private static OwnershipInterest external(UUID party, UUID owned, String percentage) {
        return build(external(party), owned, percentage, percentage, JAN);
    }

    private static OwnershipInterest interest(UUID owner, UUID owned, String capital, String voting) {
        return interest(owner, owned, capital, voting, JAN);
    }

    private static OwnershipInterest interest(UUID owner, UUID owned, String capital, String voting, LocalDate from) {
        return build(entity(owner), owned, capital, voting, from);
    }

    private static OwnershipInterest build(Holder holder, UUID owned, String capital, String voting, LocalDate from) {
        return new OwnershipInterest(
                UUID.randomUUID(),
                TENANT,
                owned,
                holder,
                new BigDecimal(capital),
                new BigDecimal(voting),
                null,
                EffectivePeriod.from(from),
                Instant.EPOCH);
    }
}
