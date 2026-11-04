package br.com.davinci.erp.organization.structure.domain;

import br.com.davinci.erp.organization.shared.domain.ConflictException;
import br.com.davinci.erp.organization.shared.domain.EffectivePeriod;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.TreeSet;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/**
 * Regras de uma estrutura com vigência (ADR-0019): um pai por unidade em cada data, sem ciclos em nenhuma data, sem
 * reescrever mudanças já programadas. Opera sobre os vínculos que valem a partir da data da mudança.
 */
public final class Hierarchy {

    public static final int MAX_DEPTH = 64;

    private Hierarchy() {}

    public static Optional<StructureLink> linkAt(List<StructureLink> links, UUID unit, LocalDate date) {
        return links.stream()
                .filter(l -> l.unitId().equals(unit) && l.period().contains(date))
                .findFirst();
    }

    /**
     * Novo conjunto de vínculos com {@code unit} sob {@code parent} (ou raiz, se nulo) a partir de {@code on}. Devolve
     * a mesma lista se a posição não mudar.
     */
    public static List<StructureLink> place(List<StructureLink> links, UUID unit, @Nullable UUID parent, LocalDate on) {
        requireNoLaterChange(links, unit, on);
        Optional<StructureLink> current = linkAt(links, unit, on);
        if (current.isPresent() && Objects.equals(current.get().parentUnitId(), parent)) {
            return links;
        }
        if (parent != null && linkAt(links, parent, on).isEmpty()) {
            throw new ConflictException("parent-not-in-structure", "O pai não está posicionado na estrutura em " + on);
        }
        List<StructureLink> result = withoutCurrent(links, unit, on);
        result.add(new StructureLink(unit, parent, EffectivePeriod.from(on)));
        requireAcyclic(result, unit, on);
        result.sort(ORDER);
        return List.copyOf(result);
    }

    /** Tira a unidade da estrutura a partir de {@code on}; exige que ela não tenha filhos dali em diante. */
    public static List<StructureLink> remove(List<StructureLink> links, UUID unit, LocalDate on) {
        requireNoLaterChange(links, unit, on);
        if (linkAt(links, unit, on).isEmpty()) {
            return links;
        }
        boolean hasChildren = links.stream()
                .anyMatch(l -> unit.equals(l.parentUnitId())
                        && (l.period().isOpen() || l.period().validTo().isAfter(on)));
        if (hasChildren) {
            throw new ConflictException(
                    "has-active-dependents", "Mova ou retire os filhos da unidade antes de retirá-la");
        }
        List<StructureLink> result = withoutCurrent(links, unit, on);
        result.sort(ORDER);
        return List.copyOf(result);
    }

    /** Pai de cada unidade numa data. */
    public static Map<UUID, @Nullable UUID> parentsAt(List<StructureLink> links, LocalDate date) {
        Map<UUID, @Nullable UUID> parents = new HashMap<>();
        for (StructureLink l : links) {
            if (l.period().contains(date)) {
                parents.put(l.unitId(), l.parentUnitId());
            }
        }
        return parents;
    }

    private static final Comparator<StructureLink> ORDER = Comparator.comparing((StructureLink l) -> l.unitId())
            .thenComparing(l -> l.period().validFrom());

    private static void requireNoLaterChange(List<StructureLink> links, UUID unit, LocalDate on) {
        for (StructureLink l : links) {
            if (l.unitId().equals(unit) && l.period().validFrom().isAfter(on)) {
                throw new ConflictException(
                        "overlapping-period",
                        "Já há mudança programada para esta unidade em "
                                + l.period().validFrom() + "; altere a partir dessa data");
            }
        }
    }

    private static List<StructureLink> withoutCurrent(List<StructureLink> links, UUID unit, LocalDate on) {
        List<StructureLink> result = new ArrayList<>();
        for (StructureLink l : links) {
            if (!l.unitId().equals(unit) || !l.period().contains(on)) {
                result.add(l);
            } else if (l.period().validFrom().isBefore(on)) {
                result.add(new StructureLink(
                        l.unitId(), l.parentUnitId(), l.period().endingOn(on)));
            }
            // vínculo que começa exatamente em `on` é substituído
        }
        return result;
    }

    /**
     * Em cada data em que a estrutura muda a partir de {@code on}, sobe da unidade até a raiz: não pode passar por ela
     * mesma (ciclo) nem por um ancestral sem posição naquela data (filho órfão de um pai com retirada programada).
     */
    private static void requireAcyclic(List<StructureLink> links, UUID unit, LocalDate on) {
        TreeSet<LocalDate> dates = new TreeSet<>();
        dates.add(on);
        for (StructureLink l : links) {
            if (l.period().validFrom().isAfter(on)) {
                dates.add(l.period().validFrom());
            }
            if (l.period().validTo() != null && l.period().validTo().isAfter(on)) {
                dates.add(l.period().validTo());
            }
        }
        for (LocalDate date : dates) {
            Map<UUID, @Nullable UUID> parents = parentsAt(links, date);
            UUID cursor = unit;
            for (int depth = 0; ; depth++) {
                if (!parents.containsKey(cursor)) {
                    throw new ConflictException(
                            "parent-not-in-structure", "Um ancestral da unidade sai da estrutura em " + date);
                }
                UUID parent = parents.get(cursor);
                if (parent == null) {
                    break;
                }
                if (parent.equals(unit)) {
                    throw new ConflictException(
                            "hierarchy-cycle", "O movimento criaria um ciclo na estrutura em " + date);
                }
                if (depth >= MAX_DEPTH) {
                    throw new ConflictException(
                            "hierarchy-too-deep", "A estrutura passaria de " + MAX_DEPTH + " níveis");
                }
                cursor = parent;
            }
        }
    }
}
