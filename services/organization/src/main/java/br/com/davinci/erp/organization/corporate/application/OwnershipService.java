package br.com.davinci.erp.organization.corporate.application;

import br.com.davinci.erp.organization.corporate.domain.ExternalParty;
import br.com.davinci.erp.organization.corporate.domain.LegalEntityStatus;
import br.com.davinci.erp.organization.corporate.domain.OwnershipGraph;
import br.com.davinci.erp.organization.corporate.domain.OwnershipInterest;
import br.com.davinci.erp.organization.corporate.domain.OwnershipInterest.Holder;
import br.com.davinci.erp.organization.corporate.domain.OwnershipInterest.HolderType;
import br.com.davinci.erp.organization.corporate.persistence.LegalEntityRepository;
import br.com.davinci.erp.organization.corporate.persistence.OwnershipRepository;
import br.com.davinci.erp.organization.shared.domain.ConflictException;
import br.com.davinci.erp.organization.shared.domain.CountryCode;
import br.com.davinci.erp.organization.shared.domain.EffectivePeriod;
import br.com.davinci.erp.organization.shared.domain.InvalidValueException;
import br.com.davinci.erp.organization.shared.domain.NotFoundException;
import br.com.davinci.erp.organization.shared.events.EventPublisher;
import br.com.davinci.erp.organization.shared.persistence.AuditLog;
import br.com.davinci.erp.organization.shared.security.Permission;
import br.com.davinci.erp.organization.shared.security.PiiCipher;
import br.com.davinci.erp.organization.shared.security.PolicyDecisionPoint;
import br.com.davinci.erp.organization.shared.tenancy.TenantScope;
import br.com.davinci.erp.platform.kernel.TenantId;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

/** Participações societárias (etapa 7): partes externas, participações com vigência e organograma societário. */
@Service
public class OwnershipService {

    static final String STREAM = "ownership";

    public record CreateParty(
            String kind,
            String name,
            @Nullable String country,
            @Nullable String taxIdentifier) {}

    public record CreateInterest(
            UUID ownedLegalEntityId,
            @Nullable UUID ownerLegalEntityId,
            @Nullable UUID ownerExternalPartyId,
            BigDecimal capitalPercentage,
            BigDecimal votingPercentage,
            @Nullable String shareClass,
            LocalDate validFrom) {}

    /** Participação com o nome do titular e do investido, para exibição. */
    public record NamedInterest(OwnershipInterest interest, String holderName, String ownedName) {}

    public record NamedChart(LocalDate asOf, OwnershipGraph.Chart chart, Map<UUID, String> names) {}

    private final OwnershipRepository ownership;
    private final LegalEntityRepository entities;
    private final PiiCipher cipher;
    private final TenantScope scope;
    private final PolicyDecisionPoint policy;
    private final EventPublisher events;
    private final AuditLog audit;
    private final JsonMapper json;
    private final Clock clock;

    OwnershipService(
            OwnershipRepository ownership,
            LegalEntityRepository entities,
            PiiCipher cipher,
            TenantScope scope,
            PolicyDecisionPoint policy,
            EventPublisher events,
            AuditLog audit,
            JsonMapper json,
            Clock clock) {
        this.ownership = ownership;
        this.entities = entities;
        this.cipher = cipher;
        this.scope = scope;
        this.policy = policy;
        this.events = events;
        this.audit = audit;
        this.json = json;
        this.clock = clock;
    }

    @Transactional
    public ExternalParty createParty(CreateParty command) {
        TenantId tenant = scope.write();
        policy.require(Permission.OWNERSHIP_MANAGE);
        ExternalParty.Kind kind;
        try {
            kind = ExternalParty.Kind.valueOf(command.kind().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new InvalidValueException("kind", "Tipo inválido (COMPANY, PERSON, FUND, OTHER): " + command.kind());
        }
        String taxId =
                command.taxIdentifier() == null || command.taxIdentifier().isBlank()
                        ? null
                        : command.taxIdentifier().strip();
        ExternalParty party = new ExternalParty(
                UUID.randomUUID(),
                tenant,
                kind,
                command.name(),
                command.country() == null ? null : new CountryCode(command.country()),
                taxId != null,
                now());
        ownership.insertParty(party, taxId == null ? null : cipher.encrypt(taxId));
        // auditoria sem o documento: dado pessoal não vai para logs nem eventos
        audit.record(
                tenant,
                "external-party",
                party.id(),
                "created",
                null,
                json.createObjectNode()
                        .put("kind", kind.name())
                        .put("name", party.name())
                        .put("hasTaxIdentifier", party.hasTaxIdentifier()));
        return party;
    }

    @Transactional(readOnly = true)
    public List<ExternalParty> parties() {
        scope.read();
        policy.require(Permission.OWNERSHIP_READ);
        return ownership.parties();
    }

    @Transactional
    public OwnershipInterest create(CreateInterest command) {
        TenantId tenant = scope.write();
        policy.require(Permission.OWNERSHIP_MANAGE);
        requireEntity(command.ownedLegalEntityId(), "ownedLegalEntityId");
        Holder holder;
        if ((command.ownerLegalEntityId() == null) == (command.ownerExternalPartyId() == null)) {
            throw new InvalidValueException(
                    "ownerLegalEntityId", "Informe exatamente um titular: entidade jurídica ou parte externa");
        }
        if (command.ownerLegalEntityId() != null) {
            requireEntity(command.ownerLegalEntityId(), "ownerLegalEntityId");
            holder = new Holder(HolderType.LEGAL_ENTITY, command.ownerLegalEntityId());
        } else {
            UUID partyId = command.ownerExternalPartyId();
            ownership.findParty(partyId).orElseThrow(() -> new NotFoundException("Parte externa", partyId));
            holder = new Holder(HolderType.EXTERNAL_PARTY, partyId);
        }
        OwnershipInterest interest = new OwnershipInterest(
                UUID.randomUUID(),
                tenant,
                command.ownedLegalEntityId(),
                holder,
                command.capitalPercentage(),
                command.votingPercentage(),
                command.shareClass(),
                EffectivePeriod.from(command.validFrom()),
                now());
        ownership.lock(tenant);
        OwnershipGraph.requireValid(ownership.activeFrom(command.validFrom()), interest);
        ownership.insert(interest);
        record(tenant, interest, "created", interest.period().validFrom());
        return interest;
    }

    @Transactional
    public OwnershipInterest end(UUID id, LocalDate on) {
        TenantId tenant = scope.write();
        policy.require(Permission.OWNERSHIP_MANAGE);
        ownership.lock(tenant);
        OwnershipInterest current = ownership.find(id).orElseThrow(() -> new NotFoundException("Participação", id));
        if (current.period().validTo() != null) {
            throw new ConflictException("invalid-transition", "A participação já está encerrada");
        }
        OwnershipInterest ended = current.endingOn(on);
        ownership.end(id, on);
        record(tenant, ended, "ended", on);
        return ended;
    }

    /** Participações diretas: {@code owners} (quem participa da entidade) ou {@code holdings} (onde ela participa). */
    @Transactional(readOnly = true)
    public List<NamedInterest> direct(UUID entityId, boolean owners, @Nullable LocalDate asOf) {
        scope.read();
        policy.require(Permission.OWNERSHIP_READ);
        List<OwnershipInterest> found = new ArrayList<>();
        for (OwnershipInterest i : ownership.activeOn(date(asOf))) {
            boolean matches = owners
                    ? i.ownedLegalEntityId().equals(entityId)
                    : i.holder().type() == HolderType.LEGAL_ENTITY
                            && i.holder().id().equals(entityId);
            if (matches) {
                found.add(i);
            }
        }
        Map<UUID, String> names = names(found);
        return found.stream()
                .map(i -> new NamedInterest(
                        i, names.getOrDefault(i.holder().id(), ""), names.getOrDefault(i.ownedLegalEntityId(), "")))
                .toList();
    }

    @Transactional(readOnly = true)
    public NamedChart chart(UUID entityId, @Nullable LocalDate asOf) {
        scope.read();
        policy.require(Permission.OWNERSHIP_READ);
        requireEntityExists(entityId);
        LocalDate on = date(asOf);
        OwnershipGraph.Chart chart = OwnershipGraph.chart(entityId, ownership.activeOn(on));
        List<OwnershipInterest> all = new ArrayList<>(chart.edges());
        Map<UUID, String> names = new HashMap<>(names(all));
        names.putAll(ownership.entityNames(List.of(entityId)));
        return new NamedChart(on, chart, names);
    }

    private Map<UUID, String> names(List<OwnershipInterest> interests) {
        List<UUID> entityIds = new ArrayList<>();
        List<UUID> partyIds = new ArrayList<>();
        for (OwnershipInterest i : interests) {
            entityIds.add(i.ownedLegalEntityId());
            (i.holder().type() == HolderType.LEGAL_ENTITY ? entityIds : partyIds)
                    .add(i.holder().id());
        }
        Map<UUID, String> names = new HashMap<>(ownership.entityNames(entityIds));
        names.putAll(ownership.partyNames(partyIds));
        return names;
    }

    private void requireEntity(UUID id, String field) {
        var entity = entities.find(id).orElseThrow(() -> new NotFoundException("Entidade jurídica", id));
        if (entity.status() == LegalEntityStatus.CLOSED) {
            throw new ConflictException(
                    "invalid-transition", "Entidade encerrada não recebe participação (" + field + ")");
        }
    }

    private void requireEntityExists(UUID id) {
        entities.find(id).orElseThrow(() -> new NotFoundException("Entidade jurídica", id));
    }

    private void record(TenantId tenant, OwnershipInterest i, String fact, LocalDate on) {
        ObjectNode data = json.createObjectNode()
                .put("ownershipInterestId", i.id().toString())
                .put("tenantId", tenant.toString())
                .put("ownedLegalEntityId", i.ownedLegalEntityId().toString())
                .put("holderType", i.holder().type().name())
                .put("holderId", i.holder().id().toString())
                .put("capitalPercentage", i.capitalPercentage())
                .put("votingPercentage", i.votingPercentage())
                .put("shareClass", i.shareClass())
                .put("validFrom", i.period().validFrom().toString())
                .put(
                        "validTo",
                        i.period().validTo() == null
                                ? null
                                : i.period().validTo().toString())
                .put("effectiveOn", on.toString());
        audit.record(tenant, STREAM, i.id(), fact, null, data);
        events.publish(STREAM, fact, tenant, i.id(), data);
    }

    private LocalDate date(@Nullable LocalDate asOf) {
        return asOf == null ? LocalDate.now(clock.withZone(ZoneOffset.UTC)) : asOf;
    }

    private Instant now() {
        return clock.instant().truncatedTo(ChronoUnit.MICROS);
    }
}
