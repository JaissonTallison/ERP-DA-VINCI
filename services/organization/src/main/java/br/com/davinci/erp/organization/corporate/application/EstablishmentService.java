package br.com.davinci.erp.organization.corporate.application;

import br.com.davinci.erp.organization.corporate.domain.Establishment;
import br.com.davinci.erp.organization.corporate.domain.EstablishmentType;
import br.com.davinci.erp.organization.corporate.domain.LegalEntity;
import br.com.davinci.erp.organization.corporate.domain.TaxCoherence;
import br.com.davinci.erp.organization.corporate.domain.TaxIdentifierRecord;
import br.com.davinci.erp.organization.corporate.persistence.EstablishmentRepository;
import br.com.davinci.erp.organization.corporate.persistence.LegalEntityRepository;
import br.com.davinci.erp.organization.shared.domain.BusinessCode;
import br.com.davinci.erp.organization.shared.domain.ConflictException;
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
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.json.JsonMapper;

/** Casos de uso de estabelecimentos. A entidade jurídica é travada nas operações que dependem do status dela. */
@Service
public class EstablishmentService {

    static final String STREAM = "establishment";
    private static final String CREATE = "open-establishment";

    public enum Transition {
        SUSPEND,
        REACTIVATE,
        CLOSE
    }

    private final EstablishmentRepository establishments;
    private final LegalEntityRepository entities;
    private final LegalEntityService entityService;
    private final IdempotencyRepository idempotency;
    private final TenantScope scope;
    private final PolicyDecisionPoint policy;
    private final EventPublisher events;
    private final AuditLog audit;
    private final CorporateSnapshots snapshots;
    private final JsonMapper json;
    private final Clock clock;

    EstablishmentService(
            EstablishmentRepository establishments,
            LegalEntityRepository entities,
            LegalEntityService entityService,
            IdempotencyRepository idempotency,
            TenantScope scope,
            PolicyDecisionPoint policy,
            EventPublisher events,
            AuditLog audit,
            CorporateSnapshots snapshots,
            JsonMapper json,
            Clock clock) {
        this.establishments = establishments;
        this.entities = entities;
        this.entityService = entityService;
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
    public EstablishmentView open(String idempotencyKey, Commands.OpenEstablishment command) {
        TenantId tenant = scope.write();
        policy.require(Permission.ESTABLISHMENT_MANAGE);
        String fingerprint = Fingerprint.of(json, command);
        var previous = idempotency.find(CREATE, idempotencyKey);
        if (previous.isPresent()) {
            if (!previous.get().requestHash().equals(fingerprint)) {
                throw new ConflictException("idempotency-key-reused", "Idempotency-Key já usada com outro conteúdo");
            }
            return view(find(previous.get().resourceId()));
        }
        // Trava a entidade: o status dela não muda enquanto o estabelecimento é aberto.
        LegalEntityView entity = entityService.lockedView(command.legalEntityId());
        BusinessCode code = BusinessCode.of(command.code());
        if (establishments.existsByCode(command.legalEntityId(), code)) {
            throw new ConflictException(
                    "duplicate-code", "Já existe um estabelecimento com o código " + code + " nesta entidade");
        }
        LegalEntity le = entity.entity();
        LocalDate today = LocalDate.now(clock.withZone(le.timezone()));
        LocalDate openedOn = command.openedOn() == null ? today : command.openedOn();
        if (openedOn.isAfter(today)) {
            throw new InvalidValueException(
                    "openedOn", "Abertura com data futura ainda não é suportada (máximo: " + today + ")");
        }
        if (le.incorporatedOn() != null && openedOn.isBefore(le.incorporatedOn())) {
            throw new InvalidValueException(
                    "openedOn", "O estabelecimento não pode abrir antes da constituição da entidade");
        }
        Establishment.Profile profile = new Establishment.Profile(
                command.name(),
                type(command.type()),
                command.address(),
                command.timezone() == null ? le.timezone() : RegionalSettings.timezone("timezone", command.timezone()));
        Instant now = now();
        Establishment establishment = Establishment.open(UUID.randomUUID(), le, code, profile, openedOn, now);

        List<TaxIdentifierRecord> taxes = new ArrayList<>();
        for (Commands.NewTaxIdentifier t : command.taxIdentifiers()) {
            LocalDate from = t.validFrom() == null ? openedOn : t.validFrom();
            TaxIdentifier identifier = TaxIdentifier.of(t.scheme(), t.value());
            TaxCoherence.requireCoherent(identifier, le, entity.taxIdentifiers(), from);
            taxes.add(new TaxIdentifierRecord(UUID.randomUUID(), identifier, t.primary(), EffectivePeriod.from(from)));
        }
        establishments.insert(establishment);
        for (TaxIdentifierRecord t : taxes) {
            establishments.insertTaxIdentifier(tenant, establishment.id(), t);
        }
        idempotency.insert(tenant, CREATE, idempotencyKey, fingerprint, establishment.id(), now);
        EstablishmentView view = new EstablishmentView(establishment, List.copyOf(taxes));
        record(tenant, null, view, "created", openedOn);
        return view;
    }

    @Transactional(readOnly = true)
    public EstablishmentView get(UUID id) {
        scope.read();
        policy.require(Permission.ESTABLISHMENT_READ);
        return view(find(id));
    }

    @Transactional(readOnly = true)
    public List<EstablishmentView> page(
            EstablishmentRepository.Filter filter, @Nullable String afterCode, @Nullable UUID afterId, int limit) {
        scope.read();
        policy.require(Permission.ESTABLISHMENT_READ);
        List<Establishment> found = establishments.page(filter, afterCode, afterId, limit);
        var taxes = establishments.taxIdentifiers(
                found.stream().map(Establishment::id).toList());
        return found.stream()
                .map(e -> new EstablishmentView(e, List.copyOf(taxes.get(e.id()))))
                .toList();
    }

    @Transactional
    public EstablishmentView update(UUID id, long expectedVersion, Commands.UpdateEstablishment command) {
        TenantId tenant = scope.write();
        policy.require(Permission.ESTABLISHMENT_MANAGE);
        EstablishmentView before = view(lock(id));
        Establishment current = before.establishment();
        if (current.version() != expectedVersion) {
            throw new VersionMismatchException(expectedVersion, current.version());
        }
        Establishment.Profile p = current.profile();
        Establishment.Profile changes = new Establishment.Profile(
                command.name() == null ? p.name() : command.name(),
                command.type() == null ? p.type() : type(command.type()),
                command.address() == null ? p.address() : command.address(),
                command.timezone() == null ? p.timezone() : RegionalSettings.timezone("timezone", command.timezone()));
        Establishment changed = current.update(changes, entity(current.legalEntityId()), now());
        if (changed == current) {
            return before;
        }
        establishments.update(changed);
        EstablishmentView after = new EstablishmentView(changed, before.taxIdentifiers());
        record(tenant, before, after, "updated", today(changed));
        return after;
    }

    @Transactional
    public EstablishmentView transition(UUID id, Transition transition, @Nullable LocalDate effectiveOn) {
        TenantId tenant = scope.write();
        policy.require(Permission.ESTABLISHMENT_MANAGE);
        EstablishmentView before = view(lock(id));
        Establishment current = before.establishment();
        LocalDate today = today(current);
        LocalDate on = effectiveOn == null ? today : effectiveOn;
        if (on.isAfter(today)) {
            throw new InvalidValueException(
                    "effectiveOn", "Mudança de status com data futura ainda não é suportada (máximo: " + today + ")");
        }
        Instant now = now();
        Establishment changed = switch (transition) {
            case SUSPEND -> current.suspend(now);
            case REACTIVATE ->
                current.reactivate(
                        entityService.lockedView(current.legalEntityId()).entity(), now);
            case CLOSE -> current.close(on, now);
        };
        if (changed == current) {
            return before;
        }
        establishments.update(changed);
        EstablishmentView after = new EstablishmentView(changed, before.taxIdentifiers());
        String fact = switch (transition) {
            case SUSPEND -> "suspended";
            case REACTIVATE -> "reactivated";
            case CLOSE -> "closed";
        };
        record(tenant, before, after, fact, on);
        return after;
    }

    @Transactional
    public EstablishmentView addTaxIdentifier(UUID id, Commands.NewTaxIdentifier command) {
        TenantId tenant = scope.write();
        policy.require(Permission.ESTABLISHMENT_MANAGE);
        EstablishmentView before = view(lock(id));
        Establishment current = before.establishment();
        LocalDate from = command.validFrom() == null ? today(current) : command.validFrom();
        TaxIdentifier identifier = TaxIdentifier.of(command.scheme(), command.value());
        LegalEntityView entity = entityService.lockedView(current.legalEntityId());
        TaxCoherence.requireCoherent(identifier, entity.entity(), entity.taxIdentifiers(), from);
        TaxIdentifierRecord record =
                new TaxIdentifierRecord(UUID.randomUUID(), identifier, command.primary(), EffectivePeriod.from(from));
        establishments.insertTaxIdentifier(tenant, id, record);
        List<TaxIdentifierRecord> taxes = new ArrayList<>(before.taxIdentifiers());
        taxes.add(record);
        return touched(tenant, before, List.copyOf(taxes), from);
    }

    @Transactional
    public EstablishmentView endTaxIdentifier(UUID id, UUID taxIdentifierId, @Nullable LocalDate effectiveOn) {
        TenantId tenant = scope.write();
        policy.require(Permission.ESTABLISHMENT_MANAGE);
        EstablishmentView before = view(lock(id));
        LocalDate on = effectiveOn == null ? today(before.establishment()) : effectiveOn;
        TaxIdentifierRecord target = before.taxIdentifiers().stream()
                .filter(t -> t.id().equals(taxIdentifierId))
                .findFirst()
                .orElseThrow(() -> new NotFoundException("Identificador fiscal", taxIdentifierId));
        if (!target.period().isOpen() && !on.isBefore(target.period().validTo())) {
            return before;
        }
        EffectivePeriod ended = target.period().endingOn(on);
        establishments.endTaxIdentifier(taxIdentifierId, on);
        List<TaxIdentifierRecord> taxes = before.taxIdentifiers().stream()
                .map(t -> t.id().equals(taxIdentifierId)
                        ? new TaxIdentifierRecord(t.id(), t.identifier(), t.primary(), ended)
                        : t)
                .toList();
        return touched(tenant, before, taxes, on);
    }

    private EstablishmentView touched(
            TenantId tenant, EstablishmentView before, List<TaxIdentifierRecord> taxes, LocalDate on) {
        Establishment e = before.establishment();
        Instant now = now();
        Establishment bumped = new Establishment(
                e.id(),
                e.tenantId(),
                e.legalEntityId(),
                e.code(),
                e.name(),
                e.type(),
                e.address(),
                e.timezone(),
                e.status(),
                e.openedOn(),
                e.closedOn(),
                e.createdAt(),
                now,
                e.version() + 1);
        establishments.touch(e.id(), bumped.version(), now);
        EstablishmentView after = new EstablishmentView(bumped, taxes);
        record(tenant, before, after, "tax-identifier-changed", on);
        return after;
    }

    private LegalEntity entity(UUID id) {
        return entities.find(id).orElseThrow(() -> new NotFoundException("Entidade jurídica", id));
    }

    private Establishment find(UUID id) {
        return establishments.find(id).orElseThrow(() -> new NotFoundException("Estabelecimento", id));
    }

    private Establishment lock(UUID id) {
        return establishments.findForUpdate(id).orElseThrow(() -> new NotFoundException("Estabelecimento", id));
    }

    private EstablishmentView view(Establishment e) {
        return new EstablishmentView(
                e, List.copyOf(establishments.taxIdentifiers(List.of(e.id())).get(e.id())));
    }

    private void record(
            TenantId tenant, @Nullable EstablishmentView before, EstablishmentView after, String fact, LocalDate on) {
        var data = snapshots.establishment(after, on);
        audit.record(
                tenant,
                STREAM,
                after.establishment().id(),
                fact,
                before == null ? null : snapshots.establishment(before, on),
                data);
        events.publish(STREAM, fact, tenant, after.establishment().id(), data);
    }

    private static EstablishmentType type(String raw) {
        try {
            return EstablishmentType.valueOf(raw.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new InvalidValueException("type", "Tipo de estabelecimento inválido: " + raw);
        }
    }

    private LocalDate today(Establishment e) {
        return LocalDate.now(clock.withZone(e.timezone()));
    }

    private Instant now() {
        return clock.instant().truncatedTo(ChronoUnit.MICROS);
    }
}
