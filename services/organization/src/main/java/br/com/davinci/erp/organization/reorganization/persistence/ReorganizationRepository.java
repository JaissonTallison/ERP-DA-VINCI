package br.com.davinci.erp.organization.reorganization.persistence;

import br.com.davinci.erp.organization.reorganization.domain.Reorganization;
import br.com.davinci.erp.organization.reorganization.domain.ReorganizationStatus;
import br.com.davinci.erp.organization.reorganization.domain.Step;
import br.com.davinci.erp.platform.kernel.TenantId;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

@Repository
public class ReorganizationRepository {

    private static final String COLUMNS = """
            id, tenant_id, structure_id, description, effective_on, status, created_by, submitted_by, decided_by,
            decision_note, created_at, updated_at, version""";

    private final JdbcClient jdbc;
    private final JsonMapper json;

    public ReorganizationRepository(JdbcClient jdbc, JsonMapper json) {
        this.jdbc = jdbc;
        this.json = json;
    }

    public void insert(Reorganization r) {
        jdbc.sql("INSERT INTO reorganization (" + COLUMNS + ") VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)")
                .params(
                        r.id(),
                        r.tenantId().value(),
                        r.structureId(),
                        r.description(),
                        r.effectiveOn(),
                        r.status().name(),
                        r.createdBy(),
                        r.submittedBy(),
                        r.decidedBy(),
                        r.decisionNote(),
                        OffsetDateTime.ofInstant(r.createdAt(), ZoneOffset.UTC),
                        OffsetDateTime.ofInstant(r.updatedAt(), ZoneOffset.UTC),
                        r.version())
                .update();
    }

    public void update(Reorganization r) {
        jdbc.sql("""
                        UPDATE reorganization SET description = ?, effective_on = ?, status = ?, submitted_by = ?,
                               decided_by = ?, decision_note = ?, updated_at = ?, version = ?
                        WHERE id = ? AND version = ?
                        """)
                .params(
                        r.description(),
                        r.effectiveOn(),
                        r.status().name(),
                        r.submittedBy(),
                        r.decidedBy(),
                        r.decisionNote(),
                        OffsetDateTime.ofInstant(r.updatedAt(), ZoneOffset.UTC),
                        r.version(),
                        r.id(),
                        r.version() - 1)
                .update();
    }

    public Optional<Reorganization> find(UUID id) {
        return jdbc.sql("SELECT " + COLUMNS + " FROM reorganization WHERE id = ?")
                .param(id)
                .query(this::map)
                .optional();
    }

    public Optional<Reorganization> findForUpdate(UUID id) {
        return jdbc.sql("SELECT " + COLUMNS + " FROM reorganization WHERE id = ? FOR UPDATE")
                .param(id)
                .query(this::map)
                .optional();
    }

    public List<Reorganization> list(@Nullable UUID structureId, @Nullable ReorganizationStatus status) {
        return jdbc.sql("SELECT " + COLUMNS + " FROM reorganization"
                        + " WHERE (CAST(:structure AS uuid) IS NULL OR structure_id = :structure)"
                        + " AND (CAST(:status AS text) IS NULL OR status = :status)"
                        + " ORDER BY effective_on DESC, created_at DESC LIMIT 500")
                .param("structure", structureId)
                .param("status", status == null ? null : status.name())
                .query(this::map)
                .list();
    }

    public List<Step> steps(UUID id) {
        return jdbc.sql("SELECT operation, payload::text AS payload FROM reorganization_step"
                        + " WHERE reorganization_id = ? ORDER BY position")
                .param(id)
                .query((rs, row) -> step(rs.getString("operation"), json.readTree(rs.getString("payload"))))
                .list();
    }

    public void replaceSteps(TenantId tenant, UUID id, List<Step> steps) {
        jdbc.sql("DELETE FROM reorganization_step WHERE reorganization_id = ?")
                .param(id)
                .update();
        for (int i = 0; i < steps.size(); i++) {
            Step s = steps.get(i);
            ObjectNode payload = json.createObjectNode()
                    .put("unitId", text(s.unitId()))
                    .put("parentUnitId", text(s.parentUnitId()))
                    .put("name", s.name())
                    .put("costCenterId", text(s.costCenterId()))
                    .put("responsibleUnitId", text(s.responsibleUnitId()));
            jdbc.sql("INSERT INTO reorganization_step (tenant_id, reorganization_id, position, operation, payload)"
                            + " VALUES (?, ?, ?, ?, CAST(? AS jsonb))")
                    .params(tenant.value(), id, i, s.operation().name(), json.writeValueAsString(payload))
                    .update();
        }
    }

    private static Step step(String operation, JsonNode p) {
        return new Step(
                Step.Operation.valueOf(operation),
                uuid(p, "unitId"),
                uuid(p, "parentUnitId"),
                p.hasNonNull("name") ? p.get("name").asString() : null,
                uuid(p, "costCenterId"),
                uuid(p, "responsibleUnitId"));
    }

    private static @Nullable UUID uuid(JsonNode p, String field) {
        return p.hasNonNull(field) ? UUID.fromString(p.get(field).asString()) : null;
    }

    private static @Nullable String text(@Nullable UUID id) {
        return id == null ? null : id.toString();
    }

    private Reorganization map(ResultSet rs, int row) throws SQLException {
        return new Reorganization(
                rs.getObject("id", UUID.class),
                new TenantId(rs.getObject("tenant_id", UUID.class)),
                rs.getObject("structure_id", UUID.class),
                rs.getString("description"),
                rs.getObject("effective_on", LocalDate.class),
                ReorganizationStatus.valueOf(rs.getString("status")),
                rs.getString("created_by"),
                rs.getString("submitted_by"),
                rs.getString("decided_by"),
                rs.getString("decision_note"),
                rs.getObject("created_at", OffsetDateTime.class).toInstant(),
                rs.getObject("updated_at", OffsetDateTime.class).toInstant(),
                rs.getLong("version"));
    }
}
