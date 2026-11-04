package br.com.davinci.erp.organization.reorganization.application;

import br.com.davinci.erp.organization.costing.application.CostCenterService;
import br.com.davinci.erp.organization.reorganization.domain.Reorganization;
import br.com.davinci.erp.organization.reorganization.domain.ReorganizationStatus;
import br.com.davinci.erp.organization.reorganization.domain.Step;
import br.com.davinci.erp.organization.reorganization.persistence.ReorganizationRepository;
import br.com.davinci.erp.organization.shared.domain.ConflictException;
import br.com.davinci.erp.organization.shared.domain.InvalidValueException;
import br.com.davinci.erp.organization.shared.domain.LocalizedText;
import br.com.davinci.erp.organization.shared.domain.NotFoundException;
import br.com.davinci.erp.organization.shared.events.EventPublisher;
import br.com.davinci.erp.organization.shared.persistence.AuditLog;
import br.com.davinci.erp.organization.shared.security.ForbiddenException;
import br.com.davinci.erp.organization.shared.security.Permission;
import br.com.davinci.erp.organization.shared.security.PolicyDecisionPoint;
import br.com.davinci.erp.organization.shared.tenancy.CurrentUser;
import br.com.davinci.erp.organization.shared.tenancy.TenantScope;
import br.com.davinci.erp.organization.shared.web.VersionMismatchException;
import br.com.davinci.erp.organization.structure.application.OrganizationalUnitService;
import br.com.davinci.erp.organization.structure.application.StructureCommands;
import br.com.davinci.erp.organization.structure.application.StructureService;
import br.com.davinci.erp.organization.structure.domain.OrganizationalUnit;
import br.com.davinci.erp.organization.structure.persistence.TreeNode;
import br.com.davinci.erp.platform.kernel.TenantId;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

/**
 * Reorganizações (etapa 5). A aprovação aplica todos os passos numa única transação: ou entram todos, ou nenhum. A
 * data de vigência vale para cada passo; datas futuras ficam registradas como vínculos que começam naquele dia, por
 * isso não há agendador.
 */
@Service
public class ReorganizationService {

    static final String STREAM = "reorganization";
    private static final int PREVIEW_LIMIT = 10_000;

    public record Simulation(
            boolean valid,
            @Nullable Integer failedStep,
            @Nullable String code,
            @Nullable String message,
            List<TreeNode> preview) {}

    private final ReorganizationRepository reorganizations;
    private final StructureService structures;
    private final OrganizationalUnitService units;
    private final CostCenterService costCenters;
    private final TenantScope scope;
    private final PolicyDecisionPoint policy;
    private final CurrentUser user;
    private final EventPublisher events;
    private final AuditLog audit;
    private final JsonMapper json;
    private final TransactionTemplate transactions;
    private final Clock clock;

    ReorganizationService(
            ReorganizationRepository reorganizations,
            StructureService structures,
            OrganizationalUnitService units,
            CostCenterService costCenters,
            TenantScope scope,
            PolicyDecisionPoint policy,
            CurrentUser user,
            EventPublisher events,
            AuditLog audit,
            JsonMapper json,
            PlatformTransactionManager transactionManager,
            Clock clock) {
        this.reorganizations = reorganizations;
        this.structures = structures;
        this.units = units;
        this.costCenters = costCenters;
        this.scope = scope;
        this.policy = policy;
        this.user = user;
        this.events = events;
        this.audit = audit;
        this.json = json;
        this.transactions = new TransactionTemplate(transactionManager);
        this.clock = clock;
    }

    @Transactional
    public Reorganization create(UUID structureId, String description, LocalDate effectiveOn) {
        TenantId tenant = scope.write();
        policy.require(Permission.REORGANIZATION_MANAGE);
        structures.get(structureId);
        Reorganization r = Reorganization.draft(
                UUID.randomUUID(), tenant, structureId, description, effectiveOn, user.actor(), now());
        reorganizations.insert(r);
        record(tenant, null, r, "created");
        return r;
    }

    @Transactional(readOnly = true)
    public Reorganization get(UUID id) {
        scope.read();
        policy.require(Permission.REORGANIZATION_READ);
        return find(id);
    }

    @Transactional(readOnly = true)
    public List<Reorganization> list(@Nullable UUID structureId, @Nullable ReorganizationStatus status) {
        scope.read();
        policy.require(Permission.REORGANIZATION_READ);
        return reorganizations.list(structureId, status);
    }

    @Transactional(readOnly = true)
    public List<Step> steps(UUID id) {
        scope.read();
        policy.require(Permission.REORGANIZATION_READ);
        find(id);
        return reorganizations.steps(id);
    }

    @Transactional
    public Reorganization edit(UUID id, long expectedVersion, @Nullable String description, @Nullable LocalDate on) {
        TenantId tenant = scope.write();
        policy.require(Permission.REORGANIZATION_MANAGE);
        Reorganization current = lock(id, expectedVersion);
        Reorganization changed = current.edit(
                description == null ? current.description() : description,
                on == null ? current.effectiveOn() : on,
                now());
        reorganizations.update(changed);
        record(tenant, current, changed, "updated");
        return changed;
    }

    /** Substitui todos os passos do rascunho (a ordem da lista é a ordem de aplicação). */
    @Transactional
    public Reorganization replaceSteps(UUID id, long expectedVersion, List<Step> steps) {
        TenantId tenant = scope.write();
        policy.require(Permission.REORGANIZATION_MANAGE);
        if (steps.size() > Reorganization.MAX_STEPS) {
            throw new InvalidValueException("steps", "No máximo " + Reorganization.MAX_STEPS + " passos");
        }
        Reorganization current = lock(id, expectedVersion);
        Reorganization changed = current.touch(now());
        reorganizations.replaceSteps(tenant, id, steps);
        reorganizations.update(changed);
        return changed;
    }

    /** Aplica os passos numa transação que sempre volta atrás e devolve o resultado e a árvore resultante. */
    public Simulation simulate(UUID id) {
        Simulation result = transactions.execute(status -> {
            scope.write();
            policy.require(Permission.REORGANIZATION_MANAGE);
            Reorganization r = find(id);
            Simulation outcome;
            try {
                applySteps(r, reorganizations.steps(id));
                outcome = new Simulation(true, null, null, null, forest(r));
            } catch (StepFailure failure) {
                outcome = new Simulation(false, failure.position, failure.code, failure.getMessage(), List.of());
            }
            status.setRollbackOnly();
            return outcome;
        });
        if (result == null) {
            throw new IllegalStateException("Simulação sem resultado");
        }
        return result;
    }

    @Transactional
    public Reorganization submit(UUID id, long expectedVersion) {
        TenantId tenant = scope.write();
        policy.require(Permission.REORGANIZATION_MANAGE);
        Reorganization current = lock(id, expectedVersion);
        Reorganization changed =
                current.submit(user.actor(), reorganizations.steps(id).size(), now());
        reorganizations.update(changed);
        record(tenant, current, changed, "submitted");
        return changed;
    }

    /** Aprova e aplica tudo de uma vez; qualquer passo inválido desfaz a aprovação inteira. */
    @Transactional
    public Reorganization approve(UUID id, long expectedVersion, @Nullable String note) {
        TenantId tenant = scope.write();
        policy.require(Permission.REORGANIZATION_APPROVE);
        Reorganization current = lock(id, expectedVersion);
        Reorganization changed;
        try {
            changed = current.approve(user.actor(), note, now());
        } catch (ConflictException e) {
            if ("segregation-of-duties".equals(e.code())) {
                throw new ForbiddenException(Permission.REORGANIZATION_APPROVE);
            }
            throw e;
        }
        try {
            applySteps(current, reorganizations.steps(id));
        } catch (StepFailure failure) {
            throw new ConflictException(
                    "reorganization-step-failed", "Passo " + failure.position + ": " + failure.getMessage());
        }
        reorganizations.update(changed);
        record(tenant, current, changed, "applied");
        return changed;
    }

    @Transactional
    public Reorganization reject(UUID id, long expectedVersion, @Nullable String note) {
        TenantId tenant = scope.write();
        policy.require(Permission.REORGANIZATION_APPROVE);
        Reorganization current = lock(id, expectedVersion);
        Reorganization changed = current.reject(user.actor(), note, now());
        reorganizations.update(changed);
        record(tenant, current, changed, "rejected");
        return changed;
    }

    @Transactional
    public Reorganization cancel(UUID id, long expectedVersion) {
        TenantId tenant = scope.write();
        policy.require(Permission.REORGANIZATION_MANAGE);
        Reorganization current = lock(id, expectedVersion);
        Reorganization changed = current.cancel(user.actor(), now());
        reorganizations.update(changed);
        record(tenant, current, changed, "cancelled");
        return changed;
    }

    private void applySteps(Reorganization r, List<Step> steps) {
        LocalDate on = r.effectiveOn();
        for (int i = 0; i < steps.size(); i++) {
            Step s = steps.get(i);
            try {
                switch (s.operation()) {
                    case MOVE_UNIT ->
                        structures.move(
                                r.structureId(), new StructureCommands.Move(id(s.unitId()), s.parentUnitId(), on));
                    case REMOVE_UNIT -> structures.remove(r.structureId(), id(s.unitId()), on);
                    case CLOSE_UNIT -> units.transition(id(s.unitId()), OrganizationalUnitService.Transition.CLOSE, on);
                    case RENAME_UNIT -> {
                        OrganizationalUnit unit = units.get(id(s.unitId()));
                        LocalizedText name = new LocalizedText(
                                s.name() == null ? unit.name().text() : s.name(),
                                unit.name().translations());
                        units.update(
                                unit.id(),
                                unit.version(),
                                new StructureCommands.UpdateUnit(name, null, false, null, false, null));
                    }
                    case REASSIGN_COST_CENTER -> costCenters.reassign(id(s.costCenterId()), s.responsibleUnitId());
                }
            } catch (ConflictException e) {
                throw new StepFailure(i, e.code(), e.getMessage());
            } catch (InvalidValueException e) {
                throw new StepFailure(i, e.code(), e.getMessage());
            } catch (NotFoundException e) {
                throw new StepFailure(i, "not-found", e.getMessage());
            }
        }
    }

    private List<TreeNode> forest(Reorganization r) {
        List<TreeNode> nodes = new ArrayList<>();
        for (TreeNode root : structures.roots(r.structureId(), r.effectiveOn())) {
            int room = PREVIEW_LIMIT - nodes.size();
            if (room <= 0) {
                break;
            }
            nodes.addAll(structures.subtree(r.structureId(), root.unitId(), r.effectiveOn(), 64, room));
        }
        return nodes;
    }

    private static UUID id(@Nullable UUID value) {
        if (value == null) {
            throw new IllegalStateException("Passo sem identificador");
        }
        return value;
    }

    private void record(TenantId tenant, @Nullable Reorganization before, Reorganization after, String fact) {
        ObjectNode data = snapshot(after);
        audit.record(tenant, STREAM, after.id(), fact, before == null ? null : snapshot(before), data);
        if (!"created".equals(fact) && !"updated".equals(fact)) {
            events.publish(STREAM, fact, tenant, after.id(), data);
        }
    }

    private ObjectNode snapshot(Reorganization r) {
        return json.createObjectNode()
                .put("reorganizationId", r.id().toString())
                .put("tenantId", r.tenantId().toString())
                .put("structureId", r.structureId().toString())
                .put("description", r.description())
                .put("effectiveOn", r.effectiveOn().toString())
                .put("status", r.status().name())
                .put("submittedBy", r.submittedBy())
                .put("decidedBy", r.decidedBy())
                .put("version", r.version());
    }

    private Reorganization find(UUID id) {
        return reorganizations.find(id).orElseThrow(() -> new NotFoundException("Reorganização", id));
    }

    private Reorganization lock(UUID id, long expectedVersion) {
        Reorganization current =
                reorganizations.findForUpdate(id).orElseThrow(() -> new NotFoundException("Reorganização", id));
        if (current.version() != expectedVersion) {
            throw new VersionMismatchException(expectedVersion, current.version());
        }
        return current;
    }

    private Instant now() {
        return clock.instant().truncatedTo(ChronoUnit.MICROS);
    }

    /** Falha de um passo, com a posição para o usuário corrigir o rascunho. */
    private static final class StepFailure extends RuntimeException {

        private static final long serialVersionUID = 1L;

        private final int position;
        private final String code;

        StepFailure(int position, String code, @Nullable String message) {
            super(message);
            this.position = position;
            this.code = code;
        }
    }
}
