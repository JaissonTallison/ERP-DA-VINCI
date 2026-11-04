package br.com.davinci.erp.organization.corporate.persistence;

import br.com.davinci.erp.organization.corporate.domain.EconomicGroup;
import br.com.davinci.erp.organization.corporate.domain.EconomicGroupStatus;
import br.com.davinci.erp.organization.shared.domain.BusinessCode;
import br.com.davinci.erp.platform.kernel.TenantId;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Currency;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/** Grupos econômicos do tenant da transação (o RLS filtra o tenant; não há consulta sem ele). */
@Repository
public class EconomicGroupRepository {

    private static final String COLUMNS =
            "id, tenant_id, code, name, reference_currency, reference_timezone, status, closed_on, created_at, updated_at, version";

    private final JdbcClient jdbc;

    public EconomicGroupRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    public void insert(EconomicGroup group) {
        jdbc.sql("INSERT INTO economic_group (" + COLUMNS + ") VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)")
                .params(
                        group.id(),
                        group.tenantId().value(),
                        group.code().value(),
                        group.name(),
                        group.referenceCurrency().getCurrencyCode(),
                        group.referenceTimezone().getId(),
                        group.status().name(),
                        group.closedOn(),
                        Rows.timestamp(group.createdAt()),
                        Rows.timestamp(group.updatedAt()),
                        group.version())
                .update();
    }

    public void update(EconomicGroup group) {
        jdbc.sql("""
                        UPDATE economic_group SET name = ?, reference_currency = ?, reference_timezone = ?, status = ?,
                               closed_on = ?, updated_at = ?, version = ?
                        WHERE id = ?
                        """)
                .params(
                        group.name(),
                        group.referenceCurrency().getCurrencyCode(),
                        group.referenceTimezone().getId(),
                        group.status().name(),
                        group.closedOn(),
                        Rows.timestamp(group.updatedAt()),
                        group.version(),
                        group.id())
                .update();
    }

    public Optional<EconomicGroup> find(UUID id) {
        return jdbc.sql("SELECT " + COLUMNS + " FROM economic_group WHERE id = ?")
                .param(id)
                .query(EconomicGroupRepository::map)
                .optional();
    }

    /** Lê travando a linha até o fim da transação (escritas no mesmo grupo ficam em fila). */
    public Optional<EconomicGroup> findForUpdate(UUID id) {
        return jdbc.sql("SELECT " + COLUMNS + " FROM economic_group WHERE id = ? FOR UPDATE")
                .param(id)
                .query(EconomicGroupRepository::map)
                .optional();
    }

    public boolean existsByCode(BusinessCode code) {
        return jdbc.sql("SELECT EXISTS (SELECT 1 FROM economic_group WHERE code = ?)")
                .param(code.value())
                .query(Boolean.class)
                .single();
    }

    public List<EconomicGroup> page(@Nullable EconomicGroupStatus status, @Nullable String afterCode, int limit) {
        return jdbc.sql("""
                        SELECT %s FROM economic_group
                        WHERE (CAST(:status AS text) IS NULL OR status = :status)
                          AND (CAST(:after AS text) IS NULL OR code > :after)
                        ORDER BY code LIMIT :limit
                        """.formatted(COLUMNS))
                .param("status", status == null ? null : status.name())
                .param("after", afterCode)
                .param("limit", limit)
                .query(EconomicGroupRepository::map)
                .list();
    }

    /** Alguma entidade continua no grupo em {@code on} ou depois? */
    public boolean hasMembersFrom(UUID groupId, LocalDate on) {
        return jdbc.sql("""
                        SELECT EXISTS (SELECT 1 FROM legal_entity_group_assignment
                                        WHERE economic_group_id = ? AND (valid_to IS NULL OR valid_to > ?))
                        """).params(groupId, on).query(Boolean.class).single();
    }

    private static EconomicGroup map(ResultSet rs, int row) throws SQLException {
        return new EconomicGroup(
                rs.getObject("id", UUID.class),
                new TenantId(rs.getObject("tenant_id", UUID.class)),
                new BusinessCode(rs.getString("code")),
                rs.getString("name"),
                Currency.getInstance(rs.getString("reference_currency")),
                ZoneId.of(rs.getString("reference_timezone")),
                EconomicGroupStatus.valueOf(rs.getString("status")),
                Rows.date(rs, "closed_on"),
                Rows.instant(rs, "created_at"),
                Rows.instant(rs, "updated_at"),
                rs.getLong("version"));
    }
}
