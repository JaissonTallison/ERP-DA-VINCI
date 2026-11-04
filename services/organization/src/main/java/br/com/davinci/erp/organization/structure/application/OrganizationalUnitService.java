package br.com.davinci.erp.organization.structure.application;

import br.com.davinci.erp.organization.corporate.domain.Establishment;
import br.com.davinci.erp.organization.corporate.domain.EstablishmentStatus;
import br.com.davinci.erp.organization.corporate.domain.LegalEntity;
import br.com.davinci.erp.organization.corporate.domain.LegalEntityStatus;
import br.com.davinci.erp.organization.corporate.persistence.EstablishmentRepository;
import br.com.davinci.erp.organization.corporate.persistence.LegalEntityRepository;
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
import br.com.davinci.erp.organization.structure.domain.OrganizationalUnit;
import br.com.davinci.erp.organization.structure.persistence.OrganizationalUnitRepository;
import br.com.davinci.erp.platform.kernel.TenantId;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Casos de uso de unidades. Datas sem informação valem "hoje" em UTC (a unidade pode cruzar entidades e fusos). */
@Service
public class OrganizationalUnitService {

    static final String STREAM = "unit";

    public enum Transition {
        DEACTIVATE,
        REACTIVATE,
        CLOSE
    }

    private final OrganizationalUnitRepository units;
    private final UnitTypeService types;
    private final LegalEntityRepository entities;
    private final EstablishmentRepository establishments;
    private final TenantScope scope;
    private final PolicyDecisionPoint policy;
    private final EventPublisher events;
    private final AuditLog audit;
    private final StructureSnapshots snapshots;
    private final Clock clock;

    OrganizationalUnitService(
            OrganizationalUnitRepository units,
            UnitTypeService types,
            LegalEntityRepository entities,
            EstablishmentRepository establishments,
            TenantScope scope,
            PolicyDecisionPoint policy,
            EventPublisher events,
            AuditLog audit,
            StructureSnapshots snapshots,
            Clock clock) {
        this.units = units;
        this.types = types;
        this.entities = entities;
        this.establishments = establishments;
        this.scope = scope;
        this.policy = policy;
        this.events = events;
        this.audit = audit;
        this.snapshots = snapshots;
        this.clock = clock;
    }

    @Transactional
    public OrganizationalUnit create(StructureCommands.CreateUnit command) {
        TenantId tenant = scope.write();
        policy.require(Permission.UNIT_MANAGE);
        BusinessCode code = BusinessCode.of(command.code());
        if (units.existsByCode(code)) {
            throw new ConflictException("duplicate-code", "Já existe uma unidade com o código " + code);
        }
        types.find(command.unitTypeId());
        requireLinks(command.legalEntityId(), command.establishmentId());
        Instant now = now();
        OrganizationalUnit unit = OrganizationalUnit.create(
                UUID.randomUUID(),
                tenant,
                code,
                new OrganizationalUnit.Profile(
                        command.name(), command.unitTypeId(), command.legalEntityId(), command.establishmentId()),
                command.validFrom() == null ? today() : command.validFrom(),
                now);
        units.insert(unit);
        record(tenant, null, unit, "created", unit.validFrom());
        return unit;
    }

    @Transactional(readOnly = true)
    public OrganizationalUnit get(UUID id) {
        scope.read();
        policy.require(Permission.UNIT_READ);
        return find(id);
    }

    @Transactional(readOnly = true)
    public List<OrganizationalUnit> page(
            OrganizationalUnitRepository.Filter filter, @Nullable String afterCode, int limit) {
        scope.read();
        policy.require(Permission.UNIT_READ);
        return units.page(filter, afterCode, limit);
    }

    @Transactional
    public OrganizationalUnit update(UUID id, long expectedVersion, StructureCommands.UpdateUnit command) {
        TenantId tenant = scope.write();
        policy.require(Permission.UNIT_MANAGE);
        OrganizationalUnit current = lock(id);
        if (current.version() != expectedVersion) {
            throw new VersionMismatchException(expectedVersion, current.version());
        }
        OrganizationalUnit.Profile p = current.profile();
        UUID entity = command.legalEntityPresent() ? command.legalEntityId() : p.legalEntityId();
        UUID establishment = command.establishmentPresent() ? command.establishmentId() : p.establishmentId();
        if (command.unitTypeId() != null) {
            types.find(command.unitTypeId());
        }
        requireLinks(entity, establishment);
        OrganizationalUnit changed = current.update(
                new OrganizationalUnit.Profile(
                        command.name() == null ? p.name() : command.name(),
                        command.unitTypeId() == null ? p.unitTypeId() : command.unitTypeId(),
                        entity,
                        establishment),
                now());
        if (changed != current) {
            units.update(changed);
            record(tenant, current, changed, "updated", today());
        }
        return changed;
    }

    @Transactional
    public OrganizationalUnit transition(UUID id, Transition transition, @Nullable LocalDate effectiveOn) {
        TenantId tenant = scope.write();
        policy.require(Permission.UNIT_MANAGE);
        OrganizationalUnit current = lock(id);
        LocalDate on = effectiveOn == null ? today() : effectiveOn;
        Instant now = now();
        OrganizationalUnit changed = switch (transition) {
            case DEACTIVATE -> current.deactivate(now);
            case REACTIVATE -> current.reactivate(now);
            case CLOSE -> {
                if (units.hasChildrenFrom(id, on)) {
                    throw new ConflictException(
                            "has-active-dependents",
                            "A unidade tem filhos em alguma estrutura em " + on + " ou depois");
                }
                yield current.close(on, now);
            }
        };
        if (changed == current) {
            return current;
        }
        units.update(changed);
        if (transition == Transition.CLOSE) {
            units.endPlacements(id, on);
        }
        String fact = switch (transition) {
            case DEACTIVATE -> "deactivated";
            case REACTIVATE -> "reactivated";
            case CLOSE -> "closed";
        };
        record(tenant, current, changed, fact, on);
        return changed;
    }

    /** Entidade e estabelecimento existentes, não encerrados e coerentes entre si. */
    private void requireLinks(@Nullable UUID entityId, @Nullable UUID establishmentId) {
        if (establishmentId != null && entityId == null) {
            throw new InvalidValueException("legalEntityId", "Informe a entidade jurídica do estabelecimento");
        }
        if (entityId != null) {
            LegalEntity entity =
                    entities.find(entityId).orElseThrow(() -> new NotFoundException("Entidade jurídica", entityId));
            if (entity.status() == LegalEntityStatus.CLOSED) {
                throw new ConflictException("invalid-transition", "Entidade jurídica encerrada não recebe unidades");
            }
        }
        if (establishmentId != null) {
            Establishment establishment = establishments
                    .find(establishmentId)
                    .orElseThrow(() -> new NotFoundException("Estabelecimento", establishmentId));
            if (!establishment.legalEntityId().equals(entityId)) {
                throw new InvalidValueException(
                        "establishmentId", "O estabelecimento não pertence à entidade jurídica informada");
            }
            if (establishment.status() == EstablishmentStatus.CLOSED) {
                throw new ConflictException("invalid-transition", "Estabelecimento encerrado não recebe unidades");
            }
        }
    }

    OrganizationalUnit find(UUID id) {
        return units.find(id).orElseThrow(() -> new NotFoundException("Unidade organizacional", id));
    }

    OrganizationalUnit lock(UUID id) {
        return units.findForUpdate(id).orElseThrow(() -> new NotFoundException("Unidade organizacional", id));
    }

    void record(
            TenantId tenant, @Nullable OrganizationalUnit before, OrganizationalUnit after, String fact, LocalDate on) {
        record(tenant, before, after, fact, on, null);
    }

    void record(
            TenantId tenant,
            @Nullable OrganizationalUnit before,
            OrganizationalUnit after,
            String fact,
            LocalDate on,
            StructureSnapshots.@Nullable Placement placement) {
        var data = snapshots.unit(after, on, placement);
        audit.record(tenant, STREAM, after.id(), fact, before == null ? null : snapshots.unit(before, on, null), data);
        events.publish(STREAM, fact, tenant, after.id(), data);
    }

    LocalDate today() {
        return LocalDate.now(clock.withZone(ZoneOffset.UTC));
    }

    private Instant now() {
        return clock.instant().truncatedTo(ChronoUnit.MICROS);
    }
}
