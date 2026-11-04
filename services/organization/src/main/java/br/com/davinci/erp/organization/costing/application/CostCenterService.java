package br.com.davinci.erp.organization.costing.application;

import br.com.davinci.erp.organization.corporate.domain.LegalEntityStatus;
import br.com.davinci.erp.organization.corporate.persistence.LegalEntityRepository;
import br.com.davinci.erp.organization.costing.domain.CostCenter;
import br.com.davinci.erp.organization.costing.domain.CostCenterKind;
import br.com.davinci.erp.organization.costing.domain.CostCenterStatus;
import br.com.davinci.erp.organization.costing.persistence.CostCenterRepository;
import br.com.davinci.erp.organization.shared.domain.BusinessCode;
import br.com.davinci.erp.organization.shared.domain.ConflictException;
import br.com.davinci.erp.organization.shared.domain.InvalidValueException;
import br.com.davinci.erp.organization.shared.domain.NotFoundException;
import br.com.davinci.erp.organization.shared.events.EventPublisher;
import br.com.davinci.erp.organization.shared.persistence.AuditLog;
import br.com.davinci.erp.organization.shared.security.Permission;
import br.com.davinci.erp.organization.shared.security.PolicyDecisionPoint;
import br.com.davinci.erp.organization.shared.tenancy.TenantScope;
import br.com.davinci.erp.organization.shared.web.VersionMismatchException;
import br.com.davinci.erp.organization.structure.domain.Hierarchy;
import br.com.davinci.erp.organization.structure.domain.StructureLink;
import br.com.davinci.erp.organization.structure.persistence.OrganizationalUnitRepository;
import br.com.davinci.erp.platform.kernel.TenantId;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

/** Centros de custo (etapa 6): cadastro por entidade, hierarquia com vigência, bloqueio e encerramento. */
@Service
public class CostCenterService {

    static final String STREAM = "cost-center";

    public enum Transition {
        BLOCK,
        UNBLOCK,
        CLOSE
    }

    public record Create(
            UUID legalEntityId,
            String code,
            String name,
            String kind,
            String category,
            @Nullable UUID responsibleUnitId,
            @Nullable UUID parentId,
            @Nullable LocalDate validFrom) {}

    public record Update(
            @Nullable String name,
            @Nullable String category,
            boolean responsiblePresent,
            @Nullable UUID responsibleUnitId) {}

    private final CostCenterRepository centers;
    private final LegalEntityRepository entities;
    private final OrganizationalUnitRepository units;
    private final TenantScope scope;
    private final PolicyDecisionPoint policy;
    private final EventPublisher events;
    private final AuditLog audit;
    private final JsonMapper json;
    private final Clock clock;

    CostCenterService(
            CostCenterRepository centers,
            LegalEntityRepository entities,
            OrganizationalUnitRepository units,
            TenantScope scope,
            PolicyDecisionPoint policy,
            EventPublisher events,
            AuditLog audit,
            JsonMapper json,
            Clock clock) {
        this.centers = centers;
        this.entities = entities;
        this.units = units;
        this.scope = scope;
        this.policy = policy;
        this.events = events;
        this.audit = audit;
        this.json = json;
        this.clock = clock;
    }

    @Transactional
    public CostCenter create(Create command) {
        TenantId tenant = scope.write();
        policy.require(Permission.COST_CENTER_MANAGE);
        var entity = entities.find(command.legalEntityId())
                .orElseThrow(() -> new NotFoundException("Entidade jurídica", command.legalEntityId()));
        if (entity.status() == LegalEntityStatus.CLOSED) {
            throw new ConflictException(
                    "invalid-transition", "Entidade jurídica encerrada não recebe centros de custo");
        }
        BusinessCode code = BusinessCode.of(command.code());
        if (centers.existsByCode(entity.id(), code)) {
            throw new ConflictException("duplicate-code", "Já existe um centro de custo com o código " + code);
        }
        CostCenterKind kind;
        try {
            kind = CostCenterKind.valueOf(command.kind().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new InvalidValueException("kind", "Tipo inválido (POSTABLE ou GROUP): " + command.kind());
        }
        requireUnit(command.responsibleUnitId());
        LocalDate from = command.validFrom() == null ? today() : command.validFrom();
        CostCenter center = CostCenter.create(
                UUID.randomUUID(),
                tenant,
                entity.id(),
                code,
                command.name(),
                kind,
                command.category(),
                command.responsibleUnitId(),
                from,
                now());
        centers.insert(center);
        place(tenant, center, command.parentId(), from);
        record(tenant, null, center, "created", from, command.parentId());
        return center;
    }

    @Transactional(readOnly = true)
    public CostCenter get(UUID id) {
        scope.read();
        policy.require(Permission.COST_CENTER_READ);
        return find(id);
    }

    @Transactional(readOnly = true)
    public List<CostCenter> list(UUID legalEntityId, @Nullable CostCenterStatus status) {
        scope.read();
        policy.require(Permission.COST_CENTER_READ);
        return centers.list(legalEntityId, status);
    }

    @Transactional(readOnly = true)
    public List<CostCenterRepository.Node> tree(UUID legalEntityId, @Nullable LocalDate asOf) {
        scope.read();
        policy.require(Permission.COST_CENTER_READ);
        return centers.tree(legalEntityId, asOf == null ? today() : asOf);
    }

    @Transactional
    public CostCenter update(UUID id, long expectedVersion, Update command) {
        TenantId tenant = scope.write();
        policy.require(Permission.COST_CENTER_MANAGE);
        CostCenter current = lock(id);
        if (current.version() != expectedVersion) {
            throw new VersionMismatchException(expectedVersion, current.version());
        }
        return applyUpdate(tenant, current, command);
    }

    /** Troca a unidade responsável; usado pelo passo {@code REASSIGN_COST_CENTER} das reorganizações. */
    @Transactional
    public CostCenter reassign(UUID id, @Nullable UUID responsibleUnitId) {
        TenantId tenant = scope.write();
        policy.require(Permission.COST_CENTER_MANAGE);
        return applyUpdate(tenant, lock(id), new Update(null, null, true, responsibleUnitId));
    }

    @Transactional
    public CostCenter move(UUID id, @Nullable UUID parentId, @Nullable LocalDate effectiveOn) {
        TenantId tenant = scope.write();
        policy.require(Permission.COST_CENTER_MANAGE);
        CostCenter center = lock(id);
        LocalDate on = effectiveOn == null ? today() : effectiveOn;
        if (!center.usableOn(on)) {
            throw new ConflictException("invalid-transition", "Centro de custo fora de vigência em " + on);
        }
        if (place(tenant, center, parentId, on)) {
            CostCenter changed = center.update(center.name(), center.category(), center.responsibleUnitId(), now());
            // a posição faz parte do estado publicado: nova versão mesmo sem mudar atributos
            CostCenter bumped = changed == center
                    ? new CostCenter(
                            center.id(),
                            center.tenantId(),
                            center.legalEntityId(),
                            center.code(),
                            center.name(),
                            center.kind(),
                            center.category(),
                            center.responsibleUnitId(),
                            center.status(),
                            center.validFrom(),
                            center.closedOn(),
                            center.createdAt(),
                            now(),
                            center.version() + 1)
                    : changed;
            centers.update(bumped);
            record(tenant, center, bumped, "moved", on, parentId);
            return bumped;
        }
        return center;
    }

    @Transactional
    public CostCenter transition(UUID id, Transition transition, @Nullable LocalDate effectiveOn) {
        TenantId tenant = scope.write();
        policy.require(Permission.COST_CENTER_MANAGE);
        CostCenter current = lock(id);
        LocalDate on = effectiveOn == null ? today() : effectiveOn;
        CostCenter changed = switch (transition) {
            case BLOCK -> current.block(now());
            case UNBLOCK -> current.unblock(now());
            case CLOSE -> {
                if (centers.hasChildrenFrom(id, on)) {
                    throw new ConflictException(
                            "has-active-dependents", "O centro de custo tem filhos em " + on + " ou depois");
                }
                yield current.close(on, now());
            }
        };
        if (changed == current) {
            return current;
        }
        centers.update(changed);
        if (transition == Transition.CLOSE) {
            centers.lockHierarchy(current.legalEntityId());
            List<StructureLink> before = centers.linksFrom(current.legalEntityId(), on);
            List<StructureLink> after = Hierarchy.remove(before, id, on);
            centers.saveLinks(tenant, current.legalEntityId(), before, after);
        }
        String fact = switch (transition) {
            case BLOCK -> "blocked";
            case UNBLOCK -> "unblocked";
            case CLOSE -> "closed";
        };
        record(tenant, current, changed, fact, on, null);
        return changed;
    }

    private CostCenter applyUpdate(TenantId tenant, CostCenter current, Update command) {
        UUID responsible = command.responsiblePresent() ? command.responsibleUnitId() : current.responsibleUnitId();
        if (command.responsiblePresent()) {
            requireUnit(responsible);
        }
        CostCenter changed = current.update(
                command.name() == null ? current.name() : command.name(),
                command.category() == null ? current.category() : command.category(),
                responsible,
                now());
        if (changed != current) {
            centers.update(changed);
            record(tenant, current, changed, "updated", today(), null);
        }
        return changed;
    }

    /** Posiciona o centro na hierarquia; o pai é do tipo GROUP, da mesma entidade e vigente. */
    private boolean place(TenantId tenant, CostCenter center, @Nullable UUID parentId, LocalDate on) {
        if (parentId != null) {
            CostCenter parent = find(parentId);
            if (!parent.legalEntityId().equals(center.legalEntityId())) {
                throw new InvalidValueException("parentId", "O pai pertence a outra entidade jurídica");
            }
            if (parent.kind() != CostCenterKind.GROUP) {
                throw new ConflictException("nesting-not-allowed", "Só centros do tipo GROUP podem ter filhos");
            }
            if (!parent.usableOn(on)) {
                throw new ConflictException("invalid-transition", "O pai está fora de vigência em " + on);
            }
        }
        centers.lockHierarchy(center.legalEntityId());
        List<StructureLink> before = centers.linksFrom(center.legalEntityId(), on);
        List<StructureLink> after = Hierarchy.place(before, center.id(), parentId, on);
        if (after == before) {
            return false;
        }
        centers.saveLinks(tenant, center.legalEntityId(), before, after);
        return true;
    }

    private void requireUnit(@Nullable UUID unitId) {
        if (unitId != null && units.find(unitId).isEmpty()) {
            throw new NotFoundException("Unidade organizacional", unitId);
        }
    }

    private void record(
            TenantId tenant,
            @Nullable CostCenter before,
            CostCenter after,
            String fact,
            LocalDate on,
            @Nullable UUID parentId) {
        ObjectNode data = snapshot(after, on).put("parentId", parentId == null ? null : parentId.toString());
        audit.record(tenant, STREAM, after.id(), fact, before == null ? null : snapshot(before, on), data);
        events.publish(STREAM, fact, tenant, after.id(), data);
    }

    private ObjectNode snapshot(CostCenter c, LocalDate on) {
        return json.createObjectNode()
                .put("costCenterId", c.id().toString())
                .put("tenantId", c.tenantId().toString())
                .put("legalEntityId", c.legalEntityId().toString())
                .put("code", c.code().value())
                .put("name", c.name())
                .put("kind", c.kind().name())
                .put("category", c.category())
                .put(
                        "responsibleUnitId",
                        c.responsibleUnitId() == null
                                ? null
                                : c.responsibleUnitId().toString())
                .put("status", c.status().name())
                .put("validFrom", c.validFrom().toString())
                .put("closedOn", c.closedOn() == null ? null : c.closedOn().toString())
                .put("effectiveOn", on.toString())
                .put("version", c.version());
    }

    private CostCenter find(UUID id) {
        return centers.find(id).orElseThrow(() -> new NotFoundException("Centro de custo", id));
    }

    private CostCenter lock(UUID id) {
        return centers.findForUpdate(id).orElseThrow(() -> new NotFoundException("Centro de custo", id));
    }

    private LocalDate today() {
        return LocalDate.now(clock.withZone(ZoneOffset.UTC));
    }

    private Instant now() {
        return clock.instant().truncatedTo(ChronoUnit.MICROS);
    }
}
