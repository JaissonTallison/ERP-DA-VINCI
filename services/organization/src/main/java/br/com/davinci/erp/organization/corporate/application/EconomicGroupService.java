package br.com.davinci.erp.organization.corporate.application;

import br.com.davinci.erp.organization.corporate.domain.EconomicGroup;
import br.com.davinci.erp.organization.corporate.domain.EconomicGroupStatus;
import br.com.davinci.erp.organization.corporate.persistence.EconomicGroupRepository;
import br.com.davinci.erp.organization.shared.domain.BusinessCode;
import br.com.davinci.erp.organization.shared.domain.ConflictException;
import br.com.davinci.erp.organization.shared.domain.NotFoundException;
import br.com.davinci.erp.organization.shared.domain.RegionalSettings;
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
import java.util.List;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.json.JsonMapper;

@Service
public class EconomicGroupService {

    static final String STREAM = "economic-group";
    private static final String CREATE = "create-economic-group";

    private final EconomicGroupRepository groups;
    private final IdempotencyRepository idempotency;
    private final TenantScope scope;
    private final PolicyDecisionPoint policy;
    private final EventPublisher events;
    private final AuditLog audit;
    private final CorporateSnapshots snapshots;
    private final JsonMapper json;
    private final Clock clock;

    EconomicGroupService(
            EconomicGroupRepository groups,
            IdempotencyRepository idempotency,
            TenantScope scope,
            PolicyDecisionPoint policy,
            EventPublisher events,
            AuditLog audit,
            CorporateSnapshots snapshots,
            JsonMapper json,
            Clock clock) {
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
    public EconomicGroup create(String idempotencyKey, Commands.CreateEconomicGroup command) {
        TenantId tenant = scope.write();
        policy.require(Permission.ECONOMIC_GROUP_MANAGE);
        String fingerprint = Fingerprint.of(json, command);
        var previous = idempotency.find(CREATE, idempotencyKey);
        if (previous.isPresent()) {
            if (!previous.get().requestHash().equals(fingerprint)) {
                throw new ConflictException("idempotency-key-reused", "Idempotency-Key já usada com outro conteúdo");
            }
            return find(previous.get().resourceId());
        }
        BusinessCode code = BusinessCode.of(command.code());
        if (groups.existsByCode(code)) {
            throw new ConflictException("duplicate-code", "Já existe um grupo econômico com o código " + code);
        }
        Instant now = now();
        EconomicGroup group = EconomicGroup.create(
                UUID.randomUUID(),
                tenant,
                code,
                command.name(),
                RegionalSettings.currency("referenceCurrency", command.referenceCurrency()),
                RegionalSettings.timezone("referenceTimezone", command.referenceTimezone()),
                now);
        groups.insert(group);
        idempotency.insert(tenant, CREATE, idempotencyKey, fingerprint, group.id(), now);
        record(tenant, null, group, "created");
        return group;
    }

    @Transactional(readOnly = true)
    public EconomicGroup get(UUID id) {
        scope.read();
        policy.require(Permission.ECONOMIC_GROUP_READ);
        return find(id);
    }

    @Transactional(readOnly = true)
    public List<EconomicGroup> page(@Nullable EconomicGroupStatus status, @Nullable String afterCode, int limit) {
        scope.read();
        policy.require(Permission.ECONOMIC_GROUP_READ);
        return groups.page(status, afterCode, limit);
    }

    @Transactional
    public EconomicGroup update(UUID id, long expectedVersion, Commands.UpdateEconomicGroup command) {
        TenantId tenant = scope.write();
        policy.require(Permission.ECONOMIC_GROUP_MANAGE);
        EconomicGroup current =
                groups.findForUpdate(id).orElseThrow(() -> new NotFoundException("Grupo econômico", id));
        if (current.version() != expectedVersion) {
            throw new VersionMismatchException(expectedVersion, current.version());
        }
        EconomicGroup changed = current.update(
                command.name(),
                command.referenceCurrency() == null
                        ? null
                        : RegionalSettings.currency("referenceCurrency", command.referenceCurrency()),
                command.referenceTimezone() == null
                        ? null
                        : RegionalSettings.timezone("referenceTimezone", command.referenceTimezone()),
                now());
        if (changed != current) {
            groups.update(changed);
            record(tenant, current, changed, "updated");
        }
        return changed;
    }

    @Transactional
    public EconomicGroup close(UUID id, @Nullable LocalDate effectiveOn) {
        TenantId tenant = scope.write();
        policy.require(Permission.ECONOMIC_GROUP_MANAGE);
        EconomicGroup current =
                groups.findForUpdate(id).orElseThrow(() -> new NotFoundException("Grupo econômico", id));
        LocalDate on = effectiveOn == null ? LocalDate.now(clock.withZone(current.referenceTimezone())) : effectiveOn;
        if (current.status() == EconomicGroupStatus.ACTIVE && groups.hasMembersFrom(id, on)) {
            throw new ConflictException(
                    "has-active-dependents", "O grupo ainda tem entidades jurídicas em " + on + " ou depois");
        }
        EconomicGroup closed = current.close(on, now());
        if (closed != current) {
            groups.update(closed);
            record(tenant, current, closed, "closed");
        }
        return closed;
    }

    EconomicGroup find(UUID id) {
        return groups.find(id).orElseThrow(() -> new NotFoundException("Grupo econômico", id));
    }

    private void record(TenantId tenant, @Nullable EconomicGroup before, EconomicGroup after, String fact) {
        var data = snapshots.economicGroup(after);
        audit.record(tenant, STREAM, after.id(), fact, before == null ? null : snapshots.economicGroup(before), data);
        events.publish(STREAM, fact, tenant, after.id(), data);
    }

    private Instant now() {
        return clock.instant().truncatedTo(ChronoUnit.MICROS);
    }
}
