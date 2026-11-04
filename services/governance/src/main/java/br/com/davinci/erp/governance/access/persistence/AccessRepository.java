package br.com.davinci.erp.governance.access.persistence;

import br.com.davinci.erp.governance.access.domain.Assignment;
import br.com.davinci.erp.governance.access.domain.Role;
import br.com.davinci.erp.governance.access.domain.SodRule;
import br.com.davinci.erp.governance.shared.domain.BusinessCode;
import br.com.davinci.erp.governance.shared.domain.EffectivePeriod;
import br.com.davinci.erp.platform.kernel.TenantId;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/** Papéis, atribuições e regras de segregação (SQL explícito, RLS por tenant). */
@Repository
public class AccessRepository {

    private static final String ROLE = """
            id, tenant_id, code, name, permissions::text AS permissions, status, created_at, updated_at, version""";
    private static final String ASSIGNMENT = """
            id, tenant_id, subject, role_id, scope_type, scope_id, valid_from, valid_to, granted_by, created_at""";
    private static final String SOD = """
            id, tenant_id, code, description, permission_a, permission_b, active, created_at""";

    private final JdbcClient jdbc;
    private final JsonMapper json;

    public AccessRepository(JdbcClient jdbc, JsonMapper json) {
        this.jdbc = jdbc;
        this.json = json;
    }

    /** Trava as regras de acesso do tenant: conceder, editar papel e criar regra olham o conjunto inteiro. */
    public void lock(TenantId tenant) {
        jdbc.sql("SELECT pg_advisory_xact_lock(hashtextextended(?, 0))")
                .param("access:" + tenant)
                .query((rs, row) -> 1)
                .list();
    }

    public void insertRole(Role r) {
        jdbc.sql("INSERT INTO role (id, tenant_id, code, name, permissions, status, created_at, updated_at, version)"
                        + " VALUES (?, ?, ?, ?, CAST(? AS jsonb), ?, ?, ?, ?)")
                .params(
                        r.id(),
                        r.tenantId().value(),
                        r.code().value(),
                        r.name(),
                        json.writeValueAsString(r.permissions()),
                        r.active() ? "ACTIVE" : "INACTIVE",
                        timestamp(r.createdAt()),
                        timestamp(r.updatedAt()),
                        r.version())
                .update();
    }

    public void updateRole(Role r) {
        jdbc.sql("UPDATE role SET name = ?, permissions = CAST(? AS jsonb), status = ?, updated_at = ?, version = ?"
                        + " WHERE id = ? AND version = ?")
                .params(
                        r.name(),
                        json.writeValueAsString(r.permissions()),
                        r.active() ? "ACTIVE" : "INACTIVE",
                        timestamp(r.updatedAt()),
                        r.version(),
                        r.id(),
                        r.version() - 1)
                .update();
    }

    public Optional<Role> findRole(UUID id) {
        return jdbc.sql("SELECT " + ROLE + " FROM role WHERE id = ?")
                .param(id)
                .query(this::role)
                .optional();
    }

    public boolean roleCodeExists(BusinessCode code) {
        return jdbc.sql("SELECT EXISTS (SELECT 1 FROM role WHERE code = ?)")
                .param(code.value())
                .query(Boolean.class)
                .single();
    }

    public List<Role> roles() {
        return jdbc.sql("SELECT " + ROLE + " FROM role ORDER BY code LIMIT 1000")
                .query(this::role)
                .list();
    }

    public void insertAssignment(Assignment a) {
        jdbc.sql("INSERT INTO role_assignment (" + ASSIGNMENT + ") VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)")
                .params(
                        a.id(),
                        a.tenantId().value(),
                        a.subject(),
                        a.roleId(),
                        a.scopeType().name(),
                        a.scopeId(),
                        a.period().validFrom(),
                        a.period().validTo(),
                        a.grantedBy(),
                        timestamp(a.createdAt()))
                .update();
    }

    public void endAssignment(UUID id, LocalDate on) {
        jdbc.sql("UPDATE role_assignment SET valid_to = ? WHERE id = ?")
                .params(on, id)
                .update();
    }

    public Optional<Assignment> findAssignment(UUID id) {
        return jdbc.sql("SELECT " + ASSIGNMENT + " FROM role_assignment WHERE id = ?")
                .param(id)
                .query(this::assignment)
                .optional();
    }

    public List<Assignment> assignmentsOf(String subject) {
        return jdbc.sql("SELECT " + ASSIGNMENT + " FROM role_assignment WHERE subject = ? ORDER BY valid_from")
                .param(subject)
                .query(this::assignment)
                .list();
    }

    public List<Assignment> assignments(@Nullable UUID roleId, @Nullable LocalDate activeOn) {
        return jdbc.sql("SELECT " + ASSIGNMENT + " FROM role_assignment"
                        + " WHERE (CAST(:role AS uuid) IS NULL OR role_id = :role)"
                        + " AND (CAST(:on AS date) IS NULL OR (valid_from <= :on AND (valid_to IS NULL OR valid_to > :on)))"
                        + " ORDER BY subject, valid_from LIMIT 1000")
                .param("role", roleId)
                .param("on", activeOn)
                .query(this::assignment)
                .list();
    }

    /** Todas as atribuições não encerradas antes de {@code from}, para conferir regras contra quem já tem acesso. */
    public List<Assignment> assignmentsFrom(LocalDate from) {
        return jdbc.sql("SELECT " + ASSIGNMENT + " FROM role_assignment WHERE valid_to IS NULL OR valid_to > ?")
                .param(from)
                .query(this::assignment)
                .list();
    }

    public void insertSod(SodRule s) {
        jdbc.sql("INSERT INTO sod_rule (" + SOD + ") VALUES (?, ?, ?, ?, ?, ?, ?, ?)")
                .params(
                        s.id(),
                        s.tenantId().value(),
                        s.code().value(),
                        s.description(),
                        s.permissionA(),
                        s.permissionB(),
                        s.active(),
                        timestamp(s.createdAt()))
                .update();
    }

    public void setSodActive(UUID id, boolean active) {
        jdbc.sql("UPDATE sod_rule SET active = ? WHERE id = ?")
                .params(active, id)
                .update();
    }

    public Optional<SodRule> findSod(UUID id) {
        return jdbc.sql("SELECT " + SOD + " FROM sod_rule WHERE id = ?")
                .param(id)
                .query(this::sod)
                .optional();
    }

    public boolean sodCodeExists(BusinessCode code) {
        return jdbc.sql("SELECT EXISTS (SELECT 1 FROM sod_rule WHERE code = ?)")
                .param(code.value())
                .query(Boolean.class)
                .single();
    }

    public List<SodRule> sodRules() {
        return jdbc.sql("SELECT " + SOD + " FROM sod_rule ORDER BY code LIMIT 1000")
                .query(this::sod)
                .list();
    }

    private static OffsetDateTime timestamp(Instant instant) {
        return OffsetDateTime.ofInstant(instant, ZoneOffset.UTC);
    }

    private Role role(ResultSet rs, int row) throws SQLException {
        Set<String> permissions = new HashSet<>();
        for (JsonNode permission : json.readTree(rs.getString("permissions"))) {
            permissions.add(permission.asString());
        }
        return new Role(
                rs.getObject("id", UUID.class),
                new TenantId(rs.getObject("tenant_id", UUID.class)),
                new BusinessCode(rs.getString("code")),
                rs.getString("name"),
                permissions,
                "ACTIVE".equals(rs.getString("status")),
                rs.getObject("created_at", OffsetDateTime.class).toInstant(),
                rs.getObject("updated_at", OffsetDateTime.class).toInstant(),
                rs.getLong("version"));
    }

    private Assignment assignment(ResultSet rs, int row) throws SQLException {
        return new Assignment(
                rs.getObject("id", UUID.class),
                new TenantId(rs.getObject("tenant_id", UUID.class)),
                rs.getString("subject"),
                rs.getObject("role_id", UUID.class),
                Assignment.ScopeType.valueOf(rs.getString("scope_type")),
                rs.getObject("scope_id", UUID.class),
                new EffectivePeriod(
                        rs.getObject("valid_from", LocalDate.class), rs.getObject("valid_to", LocalDate.class)),
                rs.getString("granted_by"),
                rs.getObject("created_at", OffsetDateTime.class).toInstant());
    }

    private SodRule sod(ResultSet rs, int row) throws SQLException {
        return new SodRule(
                rs.getObject("id", UUID.class),
                new TenantId(rs.getObject("tenant_id", UUID.class)),
                new BusinessCode(rs.getString("code")),
                rs.getString("description"),
                rs.getString("permission_a"),
                rs.getString("permission_b"),
                rs.getBoolean("active"),
                rs.getObject("created_at", OffsetDateTime.class).toInstant());
    }
}
