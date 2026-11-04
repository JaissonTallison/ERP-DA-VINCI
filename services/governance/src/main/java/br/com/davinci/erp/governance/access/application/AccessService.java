package br.com.davinci.erp.governance.access.application;

import br.com.davinci.erp.governance.access.domain.Assignment;
import br.com.davinci.erp.governance.access.domain.Role;
import br.com.davinci.erp.governance.access.domain.SodRule;
import br.com.davinci.erp.governance.access.persistence.AccessRepository;
import br.com.davinci.erp.governance.shared.domain.BusinessCode;
import br.com.davinci.erp.governance.shared.domain.ConflictException;
import br.com.davinci.erp.governance.shared.domain.EffectivePeriod;
import br.com.davinci.erp.governance.shared.domain.NotFoundException;
import br.com.davinci.erp.governance.shared.events.EventPublisher;
import br.com.davinci.erp.governance.shared.persistence.AuditLog;
import br.com.davinci.erp.governance.shared.security.Permission;
import br.com.davinci.erp.governance.shared.security.PolicyDecisionPoint;
import br.com.davinci.erp.governance.shared.tenancy.CurrentUser;
import br.com.davinci.erp.governance.shared.tenancy.TenantScope;
import br.com.davinci.erp.governance.shared.web.VersionMismatchException;
import br.com.davinci.erp.platform.kernel.TenantId;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

/** Papéis, atribuições e segregação de funções. Toda mudança passa pela trava de acesso do tenant. */
@Service
public class AccessService {

    public record Grant(
            String subject,
            UUID roleId,
            Assignment.ScopeType scopeType,
            @Nullable UUID scopeId,
            @Nullable LocalDate validFrom) {}

    /** Atribuição vigente com o papel, como consumidores e telas precisam ler. */
    public record EffectiveGrant(Assignment assignment, Role role) {}

    private final AccessRepository access;
    private final TenantScope scope;
    private final PolicyDecisionPoint policy;
    private final CurrentUser user;
    private final EventPublisher events;
    private final AuditLog audit;
    private final JsonMapper json;
    private final Clock clock;

    AccessService(
            AccessRepository access,
            TenantScope scope,
            PolicyDecisionPoint policy,
            CurrentUser user,
            EventPublisher events,
            AuditLog audit,
            JsonMapper json,
            Clock clock) {
        this.access = access;
        this.scope = scope;
        this.policy = policy;
        this.user = user;
        this.events = events;
        this.audit = audit;
        this.json = json;
        this.clock = clock;
    }

    @Transactional
    public Role createRole(String code, String name, Set<String> permissions) {
        TenantId tenant = manage();
        BusinessCode businessCode = BusinessCode.of(code);
        if (access.roleCodeExists(businessCode)) {
            throw new ConflictException("duplicate-code", "Já existe um papel com o código " + businessCode);
        }
        Instant now = now();
        Role role = new Role(UUID.randomUUID(), tenant, businessCode, name, permissions, true, now, now, 0);
        SodRule.require(access.sodRules(), role.permissions());
        access.insertRole(role);
        recordRole(tenant, null, role, "created");
        return role;
    }

    @Transactional(readOnly = true)
    public List<Role> roles() {
        read();
        return access.roles();
    }

    @Transactional(readOnly = true)
    public Role role(UUID id) {
        read();
        return findRole(id);
    }

    /** Mudança de permissões vale para quem já tem o papel: confere a segregação de cada um. */
    @Transactional
    public Role updateRole(UUID id, long expectedVersion, @Nullable String name, @Nullable Set<String> permissions) {
        TenantId tenant = manage();
        Role current = findRole(id);
        if (current.version() != expectedVersion) {
            throw new VersionMismatchException(expectedVersion, current.version());
        }
        Role changed = current.update(
                name == null ? current.name() : name, permissions == null ? current.permissions() : permissions, now());
        if (changed == current) {
            return current;
        }
        Map<UUID, Role> roles = rolesById();
        roles.put(changed.id(), changed);
        requireNoViolation(access.assignmentsFrom(today()), roles, access.sodRules());
        access.updateRole(changed);
        recordRole(tenant, current, changed, "updated");
        return changed;
    }

    @Transactional
    public Role deactivateRole(UUID id) {
        TenantId tenant = manage();
        Role current = findRole(id);
        Role changed = current.deactivate(now());
        if (changed != current) {
            access.updateRole(changed);
            recordRole(tenant, current, changed, "deactivated");
        }
        return changed;
    }

    @Transactional
    public Assignment grant(Grant command) {
        TenantId tenant = manage();
        Role role = findRole(command.roleId());
        role.requireActive();
        LocalDate from = command.validFrom() == null ? today() : command.validFrom();
        Assignment assignment = new Assignment(
                UUID.randomUUID(),
                tenant,
                command.subject(),
                role.id(),
                command.scopeType(),
                command.scopeId(),
                EffectivePeriod.from(from),
                user.actor(),
                now());
        List<Assignment> held = access.assignmentsOf(command.subject());
        Map<UUID, Role> roles = rolesById();
        Set<String> permissions = new HashSet<>(role.permissions());
        for (Assignment other : held) {
            if (other.overlaps(assignment.period())) {
                if (other.sameGrant(assignment)) {
                    throw new ConflictException("overlapping-period", "O usuário já tem esse papel nesse escopo");
                }
                Role otherRole = roles.get(other.roleId());
                if (otherRole != null && otherRole.active()) {
                    permissions.addAll(otherRole.permissions());
                }
            }
        }
        SodRule.require(access.sodRules(), permissions);
        access.insertAssignment(assignment);
        recordAssignment(tenant, assignment, role, "granted", from);
        return assignment;
    }

    @Transactional
    public Assignment revoke(UUID id, @Nullable LocalDate effectiveOn) {
        TenantId tenant = manage();
        Assignment current = access.findAssignment(id).orElseThrow(() -> new NotFoundException("Atribuição", id));
        LocalDate on = effectiveOn == null ? today() : effectiveOn;
        Assignment ended = current.endingOn(on);
        access.endAssignment(id, on);
        recordAssignment(tenant, ended, findRole(ended.roleId()), "revoked", on);
        return ended;
    }

    @Transactional(readOnly = true)
    public List<EffectiveGrant> assignments(@Nullable UUID roleId, @Nullable LocalDate activeOn) {
        read();
        return withRoles(access.assignments(roleId, activeOn));
    }

    /** Acessos vigentes de um usuário na data. Papel inativo não concede nada. */
    @Transactional(readOnly = true)
    public List<EffectiveGrant> accessOf(String subject, @Nullable LocalDate asOf) {
        read();
        LocalDate on = asOf == null ? today() : asOf;
        return withRoles(access.assignmentsOf(subject)).stream()
                .filter(g -> g.assignment().period().contains(on) && g.role().active())
                .toList();
    }

    /** Regra nova só entra se ninguém estiver ferindo-a; os conflitos precisam ser resolvidos antes. */
    @Transactional
    public SodRule createSod(String code, String description, String permissionA, String permissionB) {
        TenantId tenant = manage();
        BusinessCode businessCode = BusinessCode.of(code);
        if (access.sodCodeExists(businessCode)) {
            throw new ConflictException("duplicate-code", "Já existe uma regra com o código " + businessCode);
        }
        SodRule rule = new SodRule(
                UUID.randomUUID(), tenant, businessCode, description, permissionA, permissionB, true, now());
        requireNoViolation(access.assignmentsFrom(today()), rolesById(), List.of(rule));
        access.insertSod(rule);
        audit.record(tenant, "sod-rule", rule.id(), "created", null, sodSnapshot(rule));
        return rule;
    }

    @Transactional(readOnly = true)
    public List<SodRule> sodRules() {
        read();
        return access.sodRules();
    }

    @Transactional
    public void deactivateSod(UUID id) {
        TenantId tenant = manage();
        SodRule rule = access.findSod(id).orElseThrow(() -> new NotFoundException("Regra de segregação", id));
        if (rule.active()) {
            access.setSodActive(id, false);
            audit.record(tenant, "sod-rule", id, "deactivated", sodSnapshot(rule), json.createObjectNode());
        }
    }

    /** Agrupa por usuário e confere cada par de atribuições que coexistem em algum momento. */
    private static void requireNoViolation(List<Assignment> assignments, Map<UUID, Role> roles, List<SodRule> rules) {
        Map<String, List<Assignment>> bySubject = new HashMap<>();
        for (Assignment a : assignments) {
            bySubject.computeIfAbsent(a.subject(), k -> new ArrayList<>()).add(a);
        }
        for (List<Assignment> held : bySubject.values()) {
            for (Assignment a : held) {
                Set<String> permissions = new HashSet<>();
                for (Assignment b : held) {
                    Role role = roles.get(b.roleId());
                    if (role != null && role.active() && b.overlaps(a.period())) {
                        permissions.addAll(role.permissions());
                    }
                }
                SodRule.require(rules, permissions);
            }
        }
    }

    private List<EffectiveGrant> withRoles(List<Assignment> assignments) {
        Map<UUID, Role> roles = rolesById();
        return assignments.stream()
                .map(a -> new EffectiveGrant(a, roles.get(a.roleId())))
                .toList();
    }

    private Map<UUID, Role> rolesById() {
        Map<UUID, Role> roles = new HashMap<>();
        for (Role role : access.roles()) {
            roles.put(role.id(), role);
        }
        return roles;
    }

    private Role findRole(UUID id) {
        return access.findRole(id).orElseThrow(() -> new NotFoundException("Papel", id));
    }

    private TenantId manage() {
        TenantId tenant = scope.write();
        policy.require(Permission.ACCESS_MANAGE);
        access.lock(tenant);
        return tenant;
    }

    private void read() {
        scope.read();
        policy.require(Permission.ACCESS_READ);
    }

    private void recordRole(TenantId tenant, @Nullable Role before, Role after, String fact) {
        ObjectNode data = roleSnapshot(after);
        audit.record(tenant, "role", after.id(), fact, before == null ? null : roleSnapshot(before), data);
        events.publish("role", fact, tenant, after.id(), data);
    }

    private ObjectNode roleSnapshot(Role r) {
        ObjectNode node = json.createObjectNode()
                .put("roleId", r.id().toString())
                .put("tenantId", r.tenantId().toString())
                .put("code", r.code().value())
                .put("name", r.name())
                .put("status", r.active() ? "ACTIVE" : "INACTIVE")
                .put("version", r.version());
        ArrayNode permissions = node.putArray("permissions");
        new TreeSet<>(r.permissions()).forEach(permissions::add);
        return node;
    }

    private void recordAssignment(TenantId tenant, Assignment a, Role role, String fact, LocalDate on) {
        ObjectNode data = json.createObjectNode()
                .put("assignmentId", a.id().toString())
                .put("tenantId", tenant.toString())
                .put("subject", a.subject())
                .put("roleId", a.roleId().toString())
                .put("roleCode", role.code().value())
                .put("scopeType", a.scopeType().name())
                .put("scopeId", a.scopeId() == null ? null : a.scopeId().toString())
                .put("validFrom", a.period().validFrom().toString())
                .put(
                        "validTo",
                        a.period().validTo() == null
                                ? null
                                : a.period().validTo().toString())
                .put("grantedBy", a.grantedBy())
                .put("effectiveOn", on.toString());
        audit.record(tenant, "assignment", a.id(), fact, null, data);
        events.publish("assignment", fact, tenant, a.id(), data);
    }

    private ObjectNode sodSnapshot(SodRule s) {
        return json.createObjectNode()
                .put("code", s.code().value())
                .put("permissionA", s.permissionA())
                .put("permissionB", s.permissionB());
    }

    private LocalDate today() {
        return LocalDate.now(clock.withZone(ZoneOffset.UTC));
    }

    private Instant now() {
        return clock.instant().truncatedTo(ChronoUnit.MICROS);
    }
}
