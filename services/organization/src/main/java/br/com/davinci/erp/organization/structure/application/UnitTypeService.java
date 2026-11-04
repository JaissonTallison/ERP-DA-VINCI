package br.com.davinci.erp.organization.structure.application;

import br.com.davinci.erp.organization.shared.domain.BusinessCode;
import br.com.davinci.erp.organization.shared.domain.ConflictException;
import br.com.davinci.erp.organization.shared.domain.InvalidValueException;
import br.com.davinci.erp.organization.shared.domain.NotFoundException;
import br.com.davinci.erp.organization.shared.persistence.AuditLog;
import br.com.davinci.erp.organization.shared.security.Permission;
import br.com.davinci.erp.organization.shared.security.PolicyDecisionPoint;
import br.com.davinci.erp.organization.shared.tenancy.TenantScope;
import br.com.davinci.erp.organization.structure.domain.UnitType;
import br.com.davinci.erp.organization.structure.persistence.UnitTypeRepository;
import br.com.davinci.erp.platform.kernel.TenantId;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.json.JsonMapper;

/** Catálogo de tipos de unidade do tenant. Sem eventos de integração (nenhum consumidor precisa). */
@Service
public class UnitTypeService {

    private final UnitTypeRepository types;
    private final TenantScope scope;
    private final PolicyDecisionPoint policy;
    private final AuditLog audit;
    private final StructureSnapshots snapshots;
    private final JsonMapper json;
    private final Clock clock;

    UnitTypeService(
            UnitTypeRepository types,
            TenantScope scope,
            PolicyDecisionPoint policy,
            AuditLog audit,
            StructureSnapshots snapshots,
            JsonMapper json,
            Clock clock) {
        this.types = types;
        this.scope = scope;
        this.policy = policy;
        this.audit = audit;
        this.snapshots = snapshots;
        this.json = json;
        this.clock = clock;
    }

    @Transactional
    public UnitType create(StructureCommands.CreateUnitType command) {
        TenantId tenant = scope.write();
        policy.require(Permission.UNIT_MANAGE);
        BusinessCode code = BusinessCode.of(command.code());
        if (types.existsByCode(code)) {
            throw new ConflictException("duplicate-code", "Já existe um tipo de unidade com o código " + code);
        }
        Instant now = clock.instant().truncatedTo(ChronoUnit.MICROS);
        UnitType type = new UnitType(UUID.randomUUID(), tenant, code, command.name(), now, now, 0);
        types.insert(type);
        audit.record(
                tenant,
                "unit-type",
                type.id(),
                "created",
                null,
                json.createObjectNode().put("code", code.value()).set("name", snapshots.name(type.name())));
        return type;
    }

    @Transactional(readOnly = true)
    public List<UnitType> list() {
        scope.read();
        policy.require(Permission.UNIT_READ);
        return types.list();
    }

    @Transactional(readOnly = true)
    public Set<UUID> allowedParents(UUID typeId) {
        scope.read();
        policy.require(Permission.UNIT_READ);
        find(typeId);
        return types.allowedParents(typeId);
    }

    @Transactional
    public Set<UUID> setNesting(UUID typeId, StructureCommands.SetNesting command) {
        TenantId tenant = scope.write();
        policy.require(Permission.UNIT_MANAGE);
        find(typeId);
        for (UUID parent : command.allowedParentTypeIds()) {
            if (types.find(parent).isEmpty()) {
                throw new InvalidValueException("allowedParentTypeIds", "Tipo de unidade inexistente: " + parent);
            }
        }
        Set<UUID> before = types.allowedParents(typeId);
        types.replaceNesting(tenant, typeId, command.allowedParentTypeIds());
        var after = json.createObjectNode();
        command.allowedParentTypeIds()
                .forEach(id -> after.withArray("allowedParentTypeIds").add(id.toString()));
        var previous = json.createObjectNode();
        before.forEach(id -> previous.withArray("allowedParentTypeIds").add(id.toString()));
        audit.record(tenant, "unit-type", typeId, "nesting-changed", previous, after);
        return Set.copyOf(command.allowedParentTypeIds());
    }

    UnitType find(UUID id) {
        return types.find(id).orElseThrow(() -> new NotFoundException("Tipo de unidade", id));
    }
}
