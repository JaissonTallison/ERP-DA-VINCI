package br.com.davinci.erp.organization.structure.application;

import br.com.davinci.erp.organization.shared.domain.BusinessCode;
import br.com.davinci.erp.organization.shared.domain.ConflictException;
import br.com.davinci.erp.organization.shared.domain.InvalidValueException;
import br.com.davinci.erp.organization.shared.domain.NotFoundException;
import br.com.davinci.erp.organization.shared.persistence.AuditLog;
import br.com.davinci.erp.organization.shared.security.Permission;
import br.com.davinci.erp.organization.shared.security.PolicyDecisionPoint;
import br.com.davinci.erp.organization.shared.tenancy.TenantScope;
import br.com.davinci.erp.organization.structure.domain.Hierarchy;
import br.com.davinci.erp.organization.structure.domain.OrganizationalStructure;
import br.com.davinci.erp.organization.structure.domain.OrganizationalUnit;
import br.com.davinci.erp.organization.structure.domain.StructureLink;
import br.com.davinci.erp.organization.structure.domain.StructurePurpose;
import br.com.davinci.erp.organization.structure.persistence.OrganizationalUnitRepository;
import br.com.davinci.erp.organization.structure.persistence.StructureRepository;
import br.com.davinci.erp.organization.structure.persistence.TreeNode;
import br.com.davinci.erp.organization.structure.persistence.UnitTypeRepository;
import br.com.davinci.erp.platform.kernel.TenantId;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Estruturas e posições das unidades, com vigência (ADR-0019, ADR-0020). Leituras por data ({@code asOf}). */
@Service
public class StructureService {

    private final StructureRepository structures;
    private final UnitTypeRepository types;
    private final OrganizationalUnitService units;
    private final OrganizationalUnitRepository unitRepository;
    private final TenantScope scope;
    private final PolicyDecisionPoint policy;
    private final AuditLog audit;
    private final StructureSnapshots snapshots;
    private final Clock clock;

    StructureService(
            StructureRepository structures,
            UnitTypeRepository types,
            OrganizationalUnitService units,
            OrganizationalUnitRepository unitRepository,
            TenantScope scope,
            PolicyDecisionPoint policy,
            AuditLog audit,
            StructureSnapshots snapshots,
            Clock clock) {
        this.structures = structures;
        this.types = types;
        this.units = units;
        this.unitRepository = unitRepository;
        this.scope = scope;
        this.policy = policy;
        this.audit = audit;
        this.snapshots = snapshots;
        this.clock = clock;
    }

    @Transactional
    public OrganizationalStructure create(StructureCommands.CreateStructure command) {
        TenantId tenant = scope.write();
        policy.require(Permission.UNIT_MANAGE);
        BusinessCode code = BusinessCode.of(command.code());
        if (structures.existsByCode(code)) {
            throw new ConflictException("duplicate-code", "Já existe uma estrutura com o código " + code);
        }
        StructurePurpose purpose;
        try {
            purpose = StructurePurpose.valueOf(command.purpose().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new InvalidValueException("purpose", "Finalidade inválida: " + command.purpose());
        }
        Instant now = clock.instant().truncatedTo(ChronoUnit.MICROS);
        OrganizationalStructure structure =
                new OrganizationalStructure(UUID.randomUUID(), tenant, code, command.name(), purpose, now, now, 0);
        structures.insert(structure);
        audit.record(
                tenant,
                "structure",
                structure.id(),
                "created",
                null,
                snapshots.name(structure.name()).put("code", code.value()));
        return structure;
    }

    @Transactional(readOnly = true)
    public List<OrganizationalStructure> list() {
        scope.read();
        policy.require(Permission.UNIT_READ);
        return structures.list();
    }

    @Transactional(readOnly = true)
    public OrganizationalStructure get(UUID id) {
        scope.read();
        policy.require(Permission.UNIT_READ);
        return find(id);
    }

    /** Posiciona ou move a unidade a partir de {@code effectiveOn} (pai nulo = raiz). Datas futuras são aceitas. */
    @Transactional
    public OrganizationalUnit move(UUID structureId, StructureCommands.Move command) {
        TenantId tenant = scope.write();
        policy.require(Permission.STRUCTURE_MOVE);
        OrganizationalStructure structure = find(structureId);
        LocalDate on = command.effectiveOn();
        OrganizationalUnit unit = units.lock(command.unitId());
        requireUsable(unit, on, "unitId");
        if (command.parentUnitId() != null) {
            OrganizationalUnit parent = units.find(command.parentUnitId());
            requireUsable(parent, on, "parentUnitId");
            Set<UUID> allowed = types.allowedParents(unit.unitTypeId());
            if (!allowed.isEmpty() && !allowed.contains(parent.unitTypeId())) {
                throw new ConflictException(
                        "nesting-not-allowed", "O tipo do pai não é permitido para o tipo desta unidade");
            }
        }
        structures.lock(structureId);
        List<StructureLink> before = structures.linksFrom(structureId, on);
        UUID previousParent = Hierarchy.linkAt(before, unit.id(), on)
                .map(StructureLink::parentUnitId)
                .orElse(null);
        List<StructureLink> after = Hierarchy.place(before, unit.id(), command.parentUnitId(), on);
        if (after == before) {
            return unit;
        }
        structures.saveLinks(tenant, structureId, before, after);
        // a posição faz parte do estado publicado: nova versão para os consumidores não descartarem o evento
        OrganizationalUnit changed = unit.touch(clock.instant().truncatedTo(ChronoUnit.MICROS));
        unitRepository.update(changed);
        units.record(
                tenant,
                unit,
                changed,
                "moved",
                on,
                new StructureSnapshots.Placement(structure, previousParent, command.parentUnitId(), true));
        return changed;
    }

    /** Retira a unidade da estrutura a partir de {@code effectiveOn}; exige que ela não tenha filhos dali em diante. */
    @Transactional
    public OrganizationalUnit remove(UUID structureId, UUID unitId, LocalDate on) {
        TenantId tenant = scope.write();
        policy.require(Permission.STRUCTURE_MOVE);
        OrganizationalStructure structure = find(structureId);
        OrganizationalUnit unit = units.lock(unitId);
        structures.lock(structureId);
        List<StructureLink> before = structures.linksFrom(structureId, on);
        UUID previousParent = Hierarchy.linkAt(before, unitId, on)
                .map(StructureLink::parentUnitId)
                .orElse(null);
        List<StructureLink> after = Hierarchy.remove(before, unitId, on);
        if (after == before) {
            return unit;
        }
        structures.saveLinks(tenant, structureId, before, after);
        // a posição faz parte do estado publicado: nova versão para os consumidores não descartarem o evento
        OrganizationalUnit changed = unit.touch(clock.instant().truncatedTo(ChronoUnit.MICROS));
        unitRepository.update(changed);
        units.record(
                tenant,
                unit,
                changed,
                "removed",
                on,
                new StructureSnapshots.Placement(structure, previousParent, null, false));
        return changed;
    }

    @Transactional(readOnly = true)
    public List<TreeNode> roots(UUID structureId, @Nullable LocalDate asOf) {
        read(structureId);
        return structures.roots(structureId, date(asOf));
    }

    @Transactional(readOnly = true)
    public List<TreeNode> children(UUID structureId, UUID unitId, @Nullable LocalDate asOf) {
        read(structureId);
        return structures.children(structureId, unitId, date(asOf));
    }

    @Transactional(readOnly = true)
    public List<TreeNode> subtree(UUID structureId, UUID unitId, @Nullable LocalDate asOf, int maxDepth, int limit) {
        read(structureId);
        return structures.subtree(structureId, unitId, date(asOf), maxDepth, limit);
    }

    @Transactional(readOnly = true)
    public List<TreeNode> ancestors(UUID structureId, UUID unitId, @Nullable LocalDate asOf) {
        read(structureId);
        return structures.ancestors(structureId, unitId, date(asOf));
    }

    public LocalDate date(@Nullable LocalDate asOf) {
        return asOf == null ? units.today() : asOf;
    }

    private void read(UUID structureId) {
        scope.read();
        policy.require(Permission.UNIT_READ);
        find(structureId);
    }

    private static void requireUsable(OrganizationalUnit unit, LocalDate on, String field) {
        if (!unit.usableOn(on)) {
            throw new ConflictException(
                    "invalid-transition",
                    "A unidade " + unit.code() + " (" + field + ") está encerrada ou ainda não existe em " + on);
        }
    }

    private OrganizationalStructure find(UUID id) {
        return structures.find(id).orElseThrow(() -> new NotFoundException("Estrutura", id));
    }
}
