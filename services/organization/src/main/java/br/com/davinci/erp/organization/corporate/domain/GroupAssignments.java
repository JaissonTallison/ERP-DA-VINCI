package br.com.davinci.erp.organization.corporate.domain;

import br.com.davinci.erp.organization.shared.domain.ConflictException;
import br.com.davinci.erp.organization.shared.domain.EffectivePeriod;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/**
 * Histórico de grupos de uma entidade (no máximo um grupo por data). Mudar de grupo em D encerra o vínculo vigente
 * em D e abre outro; o passado nunca é reescrito.
 */
public final class GroupAssignments {

    private GroupAssignments() {}

    public static Optional<UUID> groupAt(List<GroupAssignment> assignments, LocalDate date) {
        return assignments.stream()
                .filter(a -> a.period().contains(date))
                .map(GroupAssignment::economicGroupId)
                .findFirst();
    }

    /**
     * Novo histórico com a entidade no grupo {@code group} (ou sem grupo, se nulo) a partir de {@code on}. Devolve a
     * mesma lista se nada mudar.
     */
    public static List<GroupAssignment> change(List<GroupAssignment> current, @Nullable UUID group, LocalDate on) {
        for (GroupAssignment assignment : current) {
            if (assignment.period().validFrom().isAfter(on)) {
                throw new ConflictException(
                        "overlapping-period",
                        "Já há mudança de grupo a partir de "
                                + assignment.period().validFrom() + "; altere a partir dessa data");
            }
        }
        Optional<UUID> currentGroup = groupAt(current, on);
        if (currentGroup.isEmpty() && group == null) {
            return current;
        }
        if (currentGroup.isPresent() && currentGroup.get().equals(group)) {
            return current;
        }
        List<GroupAssignment> result = new ArrayList<>();
        for (GroupAssignment assignment : current) {
            if (!assignment.period().contains(on)) {
                result.add(assignment);
            } else if (assignment.period().validFrom().isBefore(on)) {
                result.add(new GroupAssignment(
                        assignment.economicGroupId(), assignment.period().endingOn(on)));
            }
            // vínculo que começa exatamente em `on` é substituído
        }
        if (group != null) {
            result.add(new GroupAssignment(group, EffectivePeriod.from(on)));
        }
        result.sort(Comparator.comparing(a -> a.period().validFrom()));
        return List.copyOf(result);
    }
}
