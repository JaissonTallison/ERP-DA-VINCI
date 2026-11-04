package br.com.davinci.erp.organization.corporate.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import br.com.davinci.erp.organization.shared.domain.ConflictException;
import br.com.davinci.erp.organization.shared.domain.EffectivePeriod;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class GroupAssignmentsTest {

    private static final UUID A = UUID.randomUUID();
    private static final UUID B = UUID.randomUUID();
    private static final LocalDate JAN = LocalDate.of(2026, 1, 1);
    private static final LocalDate JUL = LocalDate.of(2026, 7, 1);

    @Test
    void mudarDeGrupoEncerraOVinculoAtualEAbreOutro() {
        List<GroupAssignment> history =
                GroupAssignments.change(List.of(new GroupAssignment(A, EffectivePeriod.from(JAN))), B, JUL);

        assertThat(history)
                .containsExactly(
                        new GroupAssignment(A, new EffectivePeriod(JAN, JUL)),
                        new GroupAssignment(B, EffectivePeriod.from(JUL)));
        assertThat(GroupAssignments.groupAt(history, JUL.minusDays(1))).contains(A);
        assertThat(GroupAssignments.groupAt(history, JUL)).contains(B);
    }

    @Test
    void sairDoGrupoEMesmoGrupo() {
        List<GroupAssignment> current = List.of(new GroupAssignment(A, EffectivePeriod.from(JAN)));

        assertThat(GroupAssignments.change(current, null, JUL))
                .containsExactly(new GroupAssignment(A, new EffectivePeriod(JAN, JUL)));
        assertThat(GroupAssignments.change(current, A, JUL)).isSameAs(current);
        assertThat(GroupAssignments.change(List.of(), null, JUL)).isEmpty();
    }

    @Test
    void mudancaNoMesmoDiaDoInicioSubstituiOVinculo() {
        assertThat(GroupAssignments.change(List.of(new GroupAssignment(A, EffectivePeriod.from(JAN))), B, JAN))
                .containsExactly(new GroupAssignment(B, EffectivePeriod.from(JAN)));
    }

    @Test
    void naoReescreveMudancaFutura() {
        assertThatThrownBy(() ->
                        GroupAssignments.change(List.of(new GroupAssignment(A, EffectivePeriod.from(JUL))), B, JAN))
                .isInstanceOf(ConflictException.class);
    }
}
