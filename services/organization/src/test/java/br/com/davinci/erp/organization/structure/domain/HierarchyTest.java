package br.com.davinci.erp.organization.structure.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import br.com.davinci.erp.organization.shared.domain.ConflictException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class HierarchyTest {

    private static final LocalDate JAN = LocalDate.of(2026, 1, 1);
    private static final LocalDate JUL = LocalDate.of(2026, 7, 1);
    private final UUID root = UUID.randomUUID();
    private final UUID a = UUID.randomUUID();
    private final UUID b = UUID.randomUUID();
    private final UUID c = UUID.randomUUID();

    /** root ── a ── b ── c, desde janeiro. */
    private List<StructureLink> chain() {
        List<StructureLink> links = Hierarchy.place(List.of(), root, null, JAN);
        links = Hierarchy.place(links, a, root, JAN);
        links = Hierarchy.place(links, b, a, JAN);
        return Hierarchy.place(links, c, b, JAN);
    }

    @Test
    void moverFechaOVinculoAtualEAbreOutroSemReescreverOPassado() {
        List<StructureLink> links = Hierarchy.place(chain(), c, a, JUL);

        assertThat(Hierarchy.parentsAt(links, JUL.minusDays(1))).containsEntry(c, b);
        assertThat(Hierarchy.parentsAt(links, JUL)).containsEntry(c, a);
        assertThat(Hierarchy.place(links, c, a, JUL)).isSameAs(links);
    }

    @Test
    void recusaCicloEmQualquerData() {
        assertThatThrownBy(() -> Hierarchy.place(chain(), a, c, JUL))
                .isInstanceOf(ConflictException.class)
                .extracting(e -> ((ConflictException) e).code())
                .isEqualTo("hierarchy-cycle");
        // b sai de baixo de a em julho; colocar a sob c a partir de março cria ciclo entre março e julho
        List<StructureLink> later = Hierarchy.place(chain(), b, root, JUL);
        assertThatThrownBy(() -> Hierarchy.place(later, a, c, LocalDate.of(2026, 3, 1)))
                .isInstanceOf(ConflictException.class);
    }

    @Test
    void naoReescreveMudancaProgramadaNemDeixaOrfao() {
        List<StructureLink> scheduled = Hierarchy.place(chain(), c, root, JUL);
        assertThatThrownBy(() -> Hierarchy.place(scheduled, c, a, LocalDate.of(2026, 3, 1)))
                .extracting(e -> ((ConflictException) e).code())
                .isEqualTo("overlapping-period");

        UUID d = UUID.randomUUID();
        List<StructureLink> withD = Hierarchy.place(List.of(), d, null, JAN);
        List<StructureLink> removedLater = Hierarchy.remove(withD, d, JUL);
        UUID e = UUID.randomUUID();
        assertThatThrownBy(() -> Hierarchy.place(removedLater, e, d, JAN))
                .extracting(x -> ((ConflictException) x).code())
                .isEqualTo("parent-not-in-structure");
    }

    @Test
    void naoRetiraUnidadeComFilhos() {
        assertThatThrownBy(() -> Hierarchy.remove(chain(), b, JUL))
                .extracting(e -> ((ConflictException) e).code())
                .isEqualTo("has-active-dependents");
        assertThat(Hierarchy.parentsAt(Hierarchy.remove(chain(), c, JUL), JUL)).doesNotContainKey(c);
    }

    /**
     * Teste de propriedade (critério de aceite da etapa 4): milhares de movimentos aleatórios, em datas aleatórias,
     * nunca produzem ciclo, órfão nem dois pais na mesma data.
     */
    @Test
    void movimentosAleatoriosNuncaQuebramAEstrutura() {
        Random random = new Random(20261103);
        List<UUID> units = new ArrayList<>();
        for (int i = 0; i < 40; i++) {
            units.add(UUID.randomUUID());
        }
        List<StructureLink> links = Hierarchy.place(List.of(), units.getFirst(), null, JAN);
        int accepted = 0;
        for (int i = 0; i < 4000; i++) {
            UUID unit = units.get(random.nextInt(units.size()));
            UUID parent = random.nextInt(8) == 0 ? null : units.get(random.nextInt(units.size()));
            // datas majoritariamente crescentes, com algumas para trás (que devem ser recusadas sem estragar nada)
            LocalDate on = JAN.plusDays(Math.max(0, i / 11 + random.nextInt(7) - 2));
            try {
                links = random.nextInt(10) == 0
                        ? Hierarchy.remove(links, unit, on)
                        : Hierarchy.place(links, unit, parent, on);
                accepted++;
            } catch (ConflictException | IllegalArgumentException expected) {
                // movimento inválido recusado: a estrutura continua como estava
            }
        }
        assertThat(accepted).as("movimentos aceitos").isGreaterThan(1500);
        for (int day = 0; day < 400; day++) {
            assertValid(links, JAN.plusDays(day));
        }
    }

    private static void assertValid(List<StructureLink> links, LocalDate date) {
        Set<UUID> seen = new HashSet<>();
        for (StructureLink l : links) {
            if (l.period().contains(date)) {
                assertThat(seen.add(l.unitId()))
                        .as("dois pais para a mesma unidade em %s", date)
                        .isTrue();
            }
        }
        Map<UUID, UUID> parents = Hierarchy.parentsAt(links, date);
        for (UUID start : parents.keySet()) {
            Set<UUID> path = new HashSet<>();
            UUID cursor = start;
            while (cursor != null) {
                assertThat(path.add(cursor)).as("ciclo em %s", date).isTrue();
                assertThat(parents).as("órfão em %s", date).containsKey(cursor);
                cursor = parents.get(cursor);
            }
        }
    }
}
