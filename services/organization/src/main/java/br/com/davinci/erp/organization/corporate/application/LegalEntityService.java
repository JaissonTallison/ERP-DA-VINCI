package br.com.davinci.erp.organization.corporate.application;

import br.com.davinci.erp.organization.corporate.domain.EconomicGroup;
import br.com.davinci.erp.organization.corporate.domain.EconomicGroupStatus;
import br.com.davinci.erp.organization.corporate.domain.GroupAssignment;
import br.com.davinci.erp.organization.corporate.domain.GroupAssignments;
import br.com.davinci.erp.organization.corporate.domain.LegalEntity;
import br.com.davinci.erp.organization.corporate.domain.LegalEntityStatus;
import br.com.davinci.erp.organization.corporate.domain.TaxIdentifierRecord;
import br.com.davinci.erp.organization.corporate.domain.TaxRequirements;
import br.com.davinci.erp.organization.corporate.persistence.EconomicGroupRepository;
import br.com.davinci.erp.organization.corporate.persistence.LegalEntityRepository;
import br.com.davinci.erp.organization.shared.domain.BusinessCode;
import br.com.davinci.erp.organization.shared.domain.ConflictException;
import br.com.davinci.erp.organization.shared.domain.CountryCode;
import br.com.davinci.erp.organization.shared.domain.EffectivePeriod;
import br.com.davinci.erp.organization.shared.domain.InvalidValueException;
import br.com.davinci.erp.organization.shared.domain.NotFoundException;
import br.com.davinci.erp.organization.shared.domain.RegionalSettings;
import br.com.davinci.erp.organization.shared.domain.TaxIdentifier;
import br.com.davinci.erp.organization.shared.events.EventPublisher;
import br.com.davinci.erp.organization.shared.persistence.AuditLog;
import br.com.davinci.erp.organization.shared.persistence.IdempotencyRepository;
import br.com.davinci.erp.organization.shared.security.Permission;
import br.com.davinci.erp.organization.shared.security.PolicyDecisionPoint;
import br.com.davinci.erp.organization.shared.tenancy.TenantScope;
import br.com.davinci.erp.organization.shared.web.VersionMismatchException;
import br.com.davinci.erp.platform.kernel.TenantId;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Function;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.json.JsonMapper;

/** Casos de uso de entidades jurídicas. Datas sem informação valem "hoje" no fuso da entidade (ADR-0020). */
@Service
public class LegalEntityService {

    static final String STREAM = "legal-entity";
    private static final String CREATE = "create-legal-entity";

    public enum Transition {
        ACTIVATE,
        SUSPEND,
        REACTIVATE,
        CLOSE
    }

    private final LegalEntityRepository entities;
    private final EconomicGroupRepository groups;
    private final IdempotencyRepository idempotency;
    private final TenantScope scope;
    private final PolicyDecisionPoint policy;
    private final EventPublisher events;
    private final AuditLog audit;
    private final CorporateSnapshots snapshots;
    private final JsonMapper json;
    private final Clock clock;

    LegalEntityService(
            LegalEntityRepository entities,
            EconomicGroupRepository groups,
            IdempotencyRepository idempotency,
            TenantScope scope,
            PolicyDecisionPoint policy,
            EventPublisher events,
            AuditLog audit,
            CorporateSnapshots snapshots,
            JsonMapper json,
            Clock clock) {
        this.entities = entities;
        this.groups = groups;
        this.idempotency = idempotency;
        this.scope = scope;
        this.policy = policy;
        this.events = events;
        this.audit = audit;
        this.snapshots = snapshots;
        this.json = json;
        this.clock = clock;
    }

    @Transactional
    public LegalEntityView create(String idempotencyKey, Commands.CreateLegalEntity command) {
        TenantId tenant = scope.write();
        policy.require(Permission.LEGAL_ENTITY_MANAGE);
        String fingerprint = Fingerprint.of(json, command);
        var previous = idempotency.find(CREATE, idempotencyKey);
        if (previous.isPresent()) {
            if (!previous.get().requestHash().equals(fingerprint)) {
                throw new ConflictException("idempotency-key-reused", "Idempotency-Key já usada com outro conteúdo");
            }
            UUID id = previous.get().resourceId();
            return view(entities.find(id).orElseThrow(() -> new NotFoundException("Entidade jurídica", id)), null);
        }
        BusinessCode code = BusinessCode.of(command.code());
        if (entities.existsByCode(code)) {
            throw new ConflictException("duplicate-code", "Já existe uma entidade jurídica com o código " + code);
        }
        CountryCode country = new CountryCode(command.country());
        LegalEntity.Profile profile = new LegalEntity.Profile(
                command.legalName(),
                command.tradeName(),
                command.legalForm(),
                RegionalSettings.currency("functionalCurrency", command.functionalCurrency()),
                RegionalSettings.timezone("timezone", command.timezone()),
                RegionalSettings.locale("defaultLocale", command.defaultLocale()),
                command.fiscalYearStartMonth(),
                command.registeredAddress());
        Instant now = now();
        LegalEntity entity =
                LegalEntity.plan(UUID.randomUUID(), tenant, code, country, command.subdivision(), profile, now);
        LocalDate today = today(entity);
        LocalDate effectiveOn = command.effectiveOn() == null ? today : command.effectiveOn();

        List<TaxIdentifierRecord> taxes = new ArrayList<>();
        for (Commands.NewTaxIdentifier t : command.taxIdentifiers()) {
            taxes.add(new TaxIdentifierRecord(
                    UUID.randomUUID(),
                    TaxIdentifier.of(t.scheme(), t.value()),
                    t.primary(),
                    EffectivePeriod.from(t.validFrom() == null ? effectiveOn : t.validFrom())));
        }
        if (command.active()) {
            requireNotFuture(effectiveOn, today);
            TaxRequirements.requireForActivation(country, taxes, effectiveOn);
            entity = entity.activate(effectiveOn, now);
        }
        List<GroupAssignment> assignments = command.economicGroupId() == null
                ? List.of()
                : List.of(new GroupAssignment(
                        activeGroup(command.economicGroupId()).id(), EffectivePeriod.from(effectiveOn)));

        entities.insert(entity);
        for (TaxIdentifierRecord t : taxes) {
            entities.insertTaxIdentifier(tenant, entity.id(), t);
        }
        entities.saveAssignments(tenant, entity.id(), List.of(), assignments);
        idempotency.insert(tenant, CREATE, idempotencyKey, fingerprint, entity.id(), now);
        LegalEntityView view = new LegalEntityView(entity, assignments, List.copyOf(taxes), effectiveOn);
        record(tenant, null, view, "created", effectiveOn);
        return view;
    }

    @Transactional(readOnly = true)
    public LegalEntityView get(UUID id, @Nullable LocalDate asOf) {
        scope.read();
        policy.require(Permission.LEGAL_ENTITY_READ);
        return view(find(id), asOf);
    }

    @Transactional(readOnly = true)
    public List<LegalEntityView> page(LegalEntityRepository.Filter filter, @Nullable String afterCode, int limit) {
        scope.read();
        policy.require(Permission.LEGAL_ENTITY_READ);
        return views(entities.page(filter, afterCode, limit), filter.asOf());
    }

    /** Entidades do grupo na data (padrão: hoje no fuso de referência do grupo). */
    @Transactional(readOnly = true)
    public List<LegalEntityView> ofGroup(UUID groupId, @Nullable LocalDate asOf, int limit) {
        scope.read();
        policy.require(Permission.LEGAL_ENTITY_READ);
        EconomicGroup group = groups.find(groupId).orElseThrow(() -> new NotFoundException("Grupo econômico", groupId));
        LocalDate date = asOf == null ? LocalDate.now(clock.withZone(group.referenceTimezone())) : asOf;
        return views(
                entities.page(new LegalEntityRepository.Filter(null, null, groupId, date, null), null, limit), date);
    }

    /** Hoje em UTC, para listagens sem grupo (cada entidade tem seu fuso). */
    public LocalDate todayUtc() {
        return LocalDate.now(clock.withZone(ZoneOffset.UTC));
    }

    @Transactional
    public LegalEntityView update(UUID id, long expectedVersion, Commands.UpdateLegalEntity command) {
        TenantId tenant = scope.write();
        policy.require(Permission.LEGAL_ENTITY_MANAGE);
        LegalEntityView before = view(lock(id), null);
        LegalEntity current = before.entity();
        if (current.version() != expectedVersion) {
            throw new VersionMismatchException(expectedVersion, current.version());
        }
        LegalEntity.Profile p = current.profile();
        LegalEntity.Profile changes = new LegalEntity.Profile(
                command.legalName() == null ? p.legalName() : command.legalName(),
                command.tradeNamePresent() ? command.tradeName() : p.tradeName(),
                command.legalFormPresent() ? command.legalForm() : p.legalForm(),
                command.functionalCurrency() == null
                        ? p.functionalCurrency()
                        : RegionalSettings.currency("functionalCurrency", command.functionalCurrency()),
                command.timezone() == null ? p.timezone() : RegionalSettings.timezone("timezone", command.timezone()),
                command.defaultLocale() == null
                        ? p.defaultLocale()
                        : RegionalSettings.locale("defaultLocale", command.defaultLocale()),
                command.fiscalYearStartMonth() == null ? p.fiscalYearStartMonth() : command.fiscalYearStartMonth(),
                command.registeredAddress() == null ? p.registeredAddress() : command.registeredAddress());
        LegalEntity changed = current.update(changes, now());
        if (changed == current) {
            return before;
        }
        entities.update(changed);
        LegalEntityView after =
                new LegalEntityView(changed, before.groupAssignments(), before.taxIdentifiers(), before.asOf());
        record(tenant, before, after, "updated", today(changed));
        return after;
    }

    @Transactional
    public LegalEntityView transition(UUID id, Transition transition, @Nullable LocalDate effectiveOn) {
        TenantId tenant = scope.write();
        policy.require(Permission.LEGAL_ENTITY_LIFECYCLE);
        LegalEntityView before = view(lock(id), null);
        LegalEntity current = before.entity();
        LocalDate today = today(current);
        LocalDate on = effectiveOn == null ? today : effectiveOn;
        // Mudanças de status com data futura dependem do agendador (etapa de reorganizações).
        requireNotFuture(on, today);
        Instant now = now();
        LegalEntity changed = switch (transition) {
            case ACTIVATE -> {
                TaxRequirements.requireForActivation(current.country(), before.taxIdentifiers(), on);
                yield current.activate(on, now);
            }
            case SUSPEND -> current.suspend(now);
            case REACTIVATE -> current.reactivate(now);
            case CLOSE -> {
                if (entities.hasOpenDependents(id)) {
                    throw new ConflictException(
                            "has-active-dependents",
                            "Encerre os estabelecimentos e as unidades organizacionais da entidade antes de encerrá-la");
                }
                yield current.close(on, now);
            }
        };
        if (changed == current) {
            return before;
        }
        entities.update(changed);
        List<GroupAssignment> assignments = before.groupAssignments();
        if (transition == Transition.CLOSE) {
            // Entidade encerrada sai do grupo na data do encerramento.
            assignments = GroupAssignments.change(assignments, null, on);
            entities.saveAssignments(tenant, id, before.groupAssignments(), assignments);
        }
        LegalEntityView after = new LegalEntityView(changed, assignments, before.taxIdentifiers(), on);
        String fact = switch (transition) {
            case ACTIVATE -> "activated";
            case SUSPEND -> "suspended";
            case REACTIVATE -> "reactivated";
            case CLOSE -> "closed";
        };
        record(tenant, before, after, fact, on);
        return after;
    }

    @Transactional
    public LegalEntityView assignGroup(UUID id, @Nullable UUID groupId, LocalDate effectiveOn) {
        TenantId tenant = scope.write();
        policy.require(Permission.LEGAL_ENTITY_MANAGE);
        LegalEntityView before = view(lock(id), effectiveOn);
        if (before.entity().status() == LegalEntityStatus.CLOSED) {
            throw new ConflictException("invalid-transition", "Entidade encerrada não muda de grupo");
        }
        if (groupId != null) {
            activeGroup(groupId);
        }
        List<GroupAssignment> assignments = GroupAssignments.change(before.groupAssignments(), groupId, effectiveOn);
        if (assignments == before.groupAssignments()) {
            return before;
        }
        entities.saveAssignments(tenant, id, before.groupAssignments(), assignments);
        return touched(tenant, before, assignments, before.taxIdentifiers(), "group-changed", effectiveOn);
    }

    @Transactional
    public LegalEntityView addTaxIdentifier(UUID id, Commands.NewTaxIdentifier command) {
        TenantId tenant = scope.write();
        policy.require(Permission.LEGAL_ENTITY_MANAGE);
        LegalEntityView before = view(lock(id), null);
        LocalDate from = command.validFrom() == null ? today(before.entity()) : command.validFrom();
        TaxIdentifierRecord record = new TaxIdentifierRecord(
                UUID.randomUUID(),
                TaxIdentifier.of(command.scheme(), command.value()),
                command.primary(),
                EffectivePeriod.from(from));
        String expected = record.identifier().scheme().country();
        if (expected != null && !expected.equals(before.entity().country().value())) {
            throw new InvalidValueException(
                    "scheme",
                    "invalid-tax-identifier",
                    record.identifier().scheme() + " não se aplica a entidade de "
                            + before.entity().country());
        }
        entities.insertTaxIdentifier(tenant, id, record);
        List<TaxIdentifierRecord> taxes = new ArrayList<>(before.taxIdentifiers());
        taxes.add(record);
        return touched(tenant, before, before.groupAssignments(), List.copyOf(taxes), "tax-identifier-changed", from);
    }

    @Transactional
    public LegalEntityView endTaxIdentifier(UUID id, UUID taxIdentifierId, @Nullable LocalDate effectiveOn) {
        TenantId tenant = scope.write();
        policy.require(Permission.LEGAL_ENTITY_MANAGE);
        LegalEntityView before = view(lock(id), null);
        LocalDate on = effectiveOn == null ? today(before.entity()) : effectiveOn;
        TaxIdentifierRecord target = before.taxIdentifiers().stream()
                .filter(t -> t.id().equals(taxIdentifierId))
                .findFirst()
                .orElseThrow(() -> new NotFoundException("Identificador fiscal", taxIdentifierId));
        if (!target.period().isOpen() && !on.isBefore(target.period().validTo())) {
            return before;
        }
        EffectivePeriod ended = target.period().endingOn(on);
        entities.endTaxIdentifier(taxIdentifierId, on);
        List<TaxIdentifierRecord> taxes = before.taxIdentifiers().stream()
                .map(t -> t.id().equals(taxIdentifierId)
                        ? new TaxIdentifierRecord(t.id(), t.identifier(), t.primary(), ended)
                        : t)
                .toList();
        return touched(tenant, before, before.groupAssignments(), taxes, "tax-identifier-changed", on);
    }

    private LegalEntityView touched(
            TenantId tenant,
            LegalEntityView before,
            List<GroupAssignment> assignments,
            List<TaxIdentifierRecord> taxes,
            String fact,
            LocalDate on) {
        LegalEntity e = before.entity();
        Instant now = now();
        LegalEntity bumped = new LegalEntity(
                e.id(),
                e.tenantId(),
                e.code(),
                e.legalName(),
                e.tradeName(),
                e.country(),
                e.subdivision(),
                e.legalForm(),
                e.functionalCurrency(),
                e.timezone(),
                e.defaultLocale(),
                e.fiscalYearStartMonth(),
                e.registeredAddress(),
                e.incorporatedOn(),
                e.closedOn(),
                e.status(),
                e.createdAt(),
                now,
                e.version() + 1);
        entities.touch(e.id(), bumped.version(), now);
        LegalEntityView after = new LegalEntityView(bumped, assignments, taxes, before.asOf());
        record(tenant, before, after, fact, on);
        return after;
    }

    private EconomicGroup activeGroup(UUID groupId) {
        EconomicGroup group = groups.find(groupId).orElseThrow(() -> new NotFoundException("Grupo econômico", groupId));
        if (group.status() != EconomicGroupStatus.ACTIVE) {
            throw new ConflictException("invalid-transition", "Grupo econômico encerrado não recebe entidades");
        }
        return group;
    }

    /** Entidade com identificadores fiscais, travada para a transação (usada ao abrir estabelecimentos). */
    LegalEntityView lockedView(UUID id) {
        return view(lock(id), null);
    }

    private LegalEntity find(UUID id) {
        return entities.find(id).orElseThrow(() -> new NotFoundException("Entidade jurídica", id));
    }

    private LegalEntity lock(UUID id) {
        return entities.findForUpdate(id).orElseThrow(() -> new NotFoundException("Entidade jurídica", id));
    }

    private LegalEntityView view(LegalEntity entity, @Nullable LocalDate asOf) {
        return views(List.of(entity), asOf).getFirst();
    }

    private List<LegalEntityView> views(List<LegalEntity> list, @Nullable LocalDate asOf) {
        List<UUID> ids = list.stream().map(LegalEntity::id).toList();
        var assignments = entities.assignments(ids);
        var taxes = entities.taxIdentifiers(ids);
        Function<LegalEntity, LocalDate> date = e -> asOf == null ? today(e) : asOf;
        return list.stream()
                .map(e -> new LegalEntityView(
                        e, List.copyOf(assignments.get(e.id())), List.copyOf(taxes.get(e.id())), date.apply(e)))
                .toList();
    }

    private void record(
            TenantId tenant, @Nullable LegalEntityView before, LegalEntityView after, String fact, LocalDate on) {
        var data = snapshots.legalEntity(after, on);
        audit.record(
                tenant,
                STREAM,
                after.entity().id(),
                fact,
                before == null ? null : snapshots.legalEntity(before, on),
                data);
        events.publish(STREAM, fact, tenant, after.entity().id(), data);
    }

    private static void requireNotFuture(LocalDate on, LocalDate today) {
        if (on.isAfter(today)) {
            throw new InvalidValueException(
                    "effectiveOn",
                    "Mudança de status com data futura ainda não é suportada (data máxima: " + today + ")");
        }
    }

    private LocalDate today(LegalEntity entity) {
        return LocalDate.now(clock.withZone(entity.timezone()));
    }

    private Instant now() {
        return clock.instant().truncatedTo(ChronoUnit.MICROS);
    }
}
