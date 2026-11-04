package br.com.davinci.erp.organization.structure.persistence;

import br.com.davinci.erp.organization.shared.domain.BusinessCode;
import br.com.davinci.erp.organization.structure.domain.OrganizationalUnit;
import br.com.davinci.erp.organization.structure.domain.UnitStatus;
import br.com.davinci.erp.platform.kernel.TenantId;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
public class OrganizationalUnitRepository {

    private static final String COLUMNS =
            "id, tenant_id, code, name, unit_type_id, legal_entity_id, establishment_id, status, valid_from, closed_on,"
                    + " created_at, updated_at, version";

    private final JdbcClient jdbc;
    private final LocalizedTextJson texts;

    public OrganizationalUnitRepository(JdbcClient jdbc, LocalizedTextJson texts) {
        this.jdbc = jdbc;
        this.texts = texts;
    }

    public void insert(OrganizationalUnit u) {
        jdbc.sql("INSERT INTO organizational_unit (" + COLUMNS
                        + ") VALUES (?, ?, ?, CAST(? AS jsonb), ?, ?, ?, ?, ?, ?, ?, ?, ?)")
                .params(
                        u.id(),
                        u.tenantId().value(),
                        u.code().value(),
                        texts.write(u.name()),
                        u.unitTypeId(),
                        u.legalEntityId(),
                        u.establishmentId(),
                        u.status().name(),
                        u.validFrom(),
                        u.closedOn(),
                        Rows.timestamp(u.createdAt()),
                        Rows.timestamp(u.updatedAt()),
                        u.version())
                .update();
    }

    public void update(OrganizationalUnit u) {
        jdbc.sql("""
                        UPDATE organizational_unit SET name = CAST(? AS jsonb), unit_type_id = ?, legal_entity_id = ?,
                               establishment_id = ?, status = ?, closed_on = ?, updated_at = ?, version = ?
                        WHERE id = ?
                        """)
                .params(
                        texts.write(u.name()),
                        u.unitTypeId(),
                        u.legalEntityId(),
                        u.establishmentId(),
                        u.status().name(),
                        u.closedOn(),
                        Rows.timestamp(u.updatedAt()),
                        u.version(),
                        u.id())
                .update();
    }

    public Optional<OrganizationalUnit> find(UUID id) {
        return jdbc.sql("SELECT " + COLUMNS + " FROM organizational_unit WHERE id = ?")
                .param(id)
                .query(this::map)
                .optional();
    }

    public Optional<OrganizationalUnit> findForUpdate(UUID id) {
        return jdbc.sql("SELECT " + COLUMNS + " FROM organizational_unit WHERE id = ? FOR UPDATE")
                .param(id)
                .query(this::map)
                .optional();
    }

    public boolean existsByCode(BusinessCode code) {
        return jdbc.sql("SELECT EXISTS (SELECT 1 FROM organizational_unit WHERE code = ?)")
                .param(code.value())
                .query(Boolean.class)
                .single();
    }

    public record Filter(
            @Nullable UUID unitTypeId,
            @Nullable UUID legalEntityId,
            @Nullable UnitStatus status,
            @Nullable String text) {}

    public List<OrganizationalUnit> page(Filter filter, @Nullable String afterCode, int limit) {
        return jdbc.sql("""
                        SELECT %s FROM organizational_unit
                        WHERE (CAST(:type AS uuid) IS NULL OR unit_type_id = :type)
                          AND (CAST(:entity AS uuid) IS NULL OR legal_entity_id = :entity)
                          AND (CAST(:status AS text) IS NULL OR status = :status)
                          AND (CAST(:text AS text) IS NULL OR code ILIKE :text OR name ->> 'text' ILIKE :text)
                          AND (CAST(:after AS text) IS NULL OR code > :after)
                        ORDER BY code LIMIT :limit
                        """.formatted(COLUMNS))
                .param("type", filter.unitTypeId())
                .param("entity", filter.legalEntityId())
                .param(
                        "status",
                        filter.status() == null ? null : filter.status().name())
                .param(
                        "text",
                        filter.text() == null ? null : "%" + filter.text().replace("%", "\\%") + "%")
                .param("after", afterCode)
                .param("limit", limit)
                .query(this::map)
                .list();
    }

    /** Filhos em qualquer estrutura em {@code on} ou depois. */
    public boolean hasChildrenFrom(UUID unitId, LocalDate on) {
        return jdbc.sql(
                        "SELECT EXISTS (SELECT 1 FROM structure_link WHERE parent_unit_id = ? AND (valid_to IS NULL OR valid_to > ?))")
                .params(unitId, on)
                .query(Boolean.class)
                .single();
    }

    /** Tira a unidade de todas as estruturas a partir de {@code on} (encerramento). */
    public void endPlacements(UUID unitId, LocalDate on) {
        jdbc.sql("DELETE FROM structure_link WHERE unit_id = ? AND valid_from >= ?")
                .params(unitId, on)
                .update();
        jdbc.sql(
                        "UPDATE structure_link SET valid_to = ? WHERE unit_id = ? AND valid_from < ? AND (valid_to IS NULL OR valid_to > ?)")
                .params(on, unitId, on, on)
                .update();
    }

    private OrganizationalUnit map(ResultSet rs, int row) throws SQLException {
        return new OrganizationalUnit(
                rs.getObject("id", UUID.class),
                new TenantId(rs.getObject("tenant_id", UUID.class)),
                new BusinessCode(rs.getString("code")),
                texts.read(rs.getString("name")),
                rs.getObject("unit_type_id", UUID.class),
                Rows.uuid(rs, "legal_entity_id"),
                Rows.uuid(rs, "establishment_id"),
                UnitStatus.valueOf(rs.getString("status")),
                rs.getObject("valid_from", LocalDate.class),
                Rows.date(rs, "closed_on"),
                Rows.instant(rs, "created_at"),
                Rows.instant(rs, "updated_at"),
                rs.getLong("version"));
    }
}
