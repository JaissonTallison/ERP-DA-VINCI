package br.com.davinci.erp.organization.corporate.domain;

import br.com.davinci.erp.organization.corporate.domain.OwnershipInterest.Holder;
import br.com.davinci.erp.organization.corporate.domain.OwnershipInterest.HolderType;
import br.com.davinci.erp.organization.shared.domain.ConflictException;
import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Deque;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/**
 * Regras e cálculos da cadeia societária numa data: soma ≤ 100%, ausência de ciclo, participação efetiva (soma, por
 * caminho, do produto das participações) e controlador final (maioria dos votos, subindo enquanto o controlador for
 * uma entidade do tenant). Participações cruzadas são rejeitadas (ver riscos no plano).
 */
public final class OwnershipGraph {

    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);
    private static final BigDecimal HALF = BigDecimal.valueOf(50);
    private static final MathContext MC = MathContext.DECIMAL64;
    private static final int MAX_DEPTH = 64;

    private OwnershipGraph() {}

    /** Valida a nova participação contra as existentes em cada data a partir do início dela. */
    public static void requireValid(Collection<OwnershipInterest> existing, OwnershipInterest added) {
        Set<LocalDate> dates = new TreeSet<>();
        dates.add(added.period().validFrom());
        for (OwnershipInterest i : existing) {
            addIfInside(dates, i.period().validFrom(), added);
            if (i.period().validTo() != null) {
                addIfInside(dates, i.period().validTo(), added);
            }
        }
        for (LocalDate date : dates) {
            List<OwnershipInterest> active = new ArrayList<>();
            for (OwnershipInterest i : existing) {
                if (i.activeOn(date)) {
                    active.add(i);
                }
            }
            BigDecimal total = added.capitalPercentage();
            for (OwnershipInterest i : active) {
                if (i.ownedLegalEntityId().equals(added.ownedLegalEntityId())) {
                    total = total.add(i.capitalPercentage());
                }
            }
            if (total.compareTo(HUNDRED) > 0) {
                throw new ConflictException(
                        "ownership-over-100", "A soma das participações passaria de 100% em " + date);
            }
            if (added.holder().type() == HolderType.LEGAL_ENTITY
                    && reachesUpward(active, added.holder().id(), added.ownedLegalEntityId())) {
                throw new ConflictException("ownership-cycle", "A participação criaria um ciclo societário em " + date);
            }
        }
    }

    private static void addIfInside(Set<LocalDate> dates, LocalDate date, OwnershipInterest added) {
        if (added.period().contains(date)) {
            dates.add(date);
        }
    }

    /** {@code true} se, subindo pelos titulares de {@code from}, chega-se a {@code target}. */
    private static boolean reachesUpward(List<OwnershipInterest> active, UUID from, UUID target) {
        Deque<UUID> pending = new ArrayDeque<>(List.of(from));
        Set<UUID> seen = new HashSet<>();
        while (!pending.isEmpty()) {
            UUID current = pending.pop();
            if (current.equals(target)) {
                return true;
            }
            if (!seen.add(current)) {
                continue;
            }
            for (OwnershipInterest i : active) {
                if (i.ownedLegalEntityId().equals(current) && i.holder().type() == HolderType.LEGAL_ENTITY) {
                    pending.push(i.holder().id());
                }
            }
        }
        return false;
    }

    /** Titular alcançado subindo a partir da entidade, com a participação efetiva dele nela. */
    public record HolderShare(Holder holder, int depth, BigDecimal effectiveCapital) {}

    public record Chart(
            UUID legalEntityId,
            List<HolderShare> holders,
            List<OwnershipInterest> edges,
            List<Holder> controlChain,
            @Nullable Holder ultimateController) {}

    /** Organograma societário de {@code entity} com as participações ativas na data. */
    public static Chart chart(UUID entity, Collection<OwnershipInterest> activeOnDate) {
        Map<UUID, List<OwnershipInterest>> ownersOf = new LinkedHashMap<>();
        for (OwnershipInterest i : activeOnDate) {
            ownersOf.computeIfAbsent(i.ownedLegalEntityId(), k -> new ArrayList<>())
                    .add(i);
        }
        Map<Holder, BigDecimal> effective = new LinkedHashMap<>();
        Map<Holder, Integer> depth = new LinkedHashMap<>();
        List<OwnershipInterest> edges = new ArrayList<>();
        Set<OwnershipInterest> seenEdges = new HashSet<>();
        accumulate(entity, BigDecimal.ONE, 1, ownersOf, effective, depth, edges, seenEdges);

        List<HolderShare> holders = new ArrayList<>();
        effective.forEach((h, share) -> holders.add(
                new HolderShare(h, depth.get(h), share.multiply(HUNDRED, MC).setScale(6, RoundingMode.HALF_EVEN))));

        List<Holder> chain = new ArrayList<>();
        UUID current = entity;
        Set<UUID> visited = new HashSet<>();
        while (visited.add(current)) {
            OwnershipInterest majority = null;
            for (OwnershipInterest i : ownersOf.getOrDefault(current, List.of())) {
                if (i.votingPercentage().compareTo(HALF) > 0) {
                    majority = i;
                }
            }
            if (majority == null) {
                break;
            }
            chain.add(majority.holder());
            if (majority.holder().type() != HolderType.LEGAL_ENTITY) {
                break;
            }
            current = majority.holder().id();
        }
        return new Chart(entity, holders, edges, chain, chain.isEmpty() ? null : chain.getLast());
    }

    private static void accumulate(
            UUID entity,
            BigDecimal factor,
            int level,
            Map<UUID, List<OwnershipInterest>> ownersOf,
            Map<Holder, BigDecimal> effective,
            Map<Holder, Integer> depth,
            List<OwnershipInterest> edges,
            Set<OwnershipInterest> seenEdges) {
        if (level > MAX_DEPTH) {
            throw new ConflictException("hierarchy-too-deep", "Cadeia societária com mais de " + MAX_DEPTH + " níveis");
        }
        for (OwnershipInterest i : ownersOf.getOrDefault(entity, List.of())) {
            if (seenEdges.add(i)) {
                edges.add(i);
            }
            BigDecimal share = factor.multiply(i.capitalPercentage().divide(HUNDRED, MC), MC);
            effective.merge(i.holder(), share, BigDecimal::add);
            depth.merge(i.holder(), level, Math::min);
            if (i.holder().type() == HolderType.LEGAL_ENTITY) {
                accumulate(i.holder().id(), share, level + 1, ownersOf, effective, depth, edges, seenEdges);
            }
        }
    }
}
