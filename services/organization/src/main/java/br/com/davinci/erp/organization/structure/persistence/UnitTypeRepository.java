package br.com.davinci.erp.organization.structure.persistence;

import br.com.davinci.erp.organization.shared.domain.BusinessCode;
import br.com.davinci.erp.organization.structure.domain.UnitType;
import br.com.davinci.erp.platform.kernel.TenantId;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
public class UnitTypeRepository {

    private static final String COLUMNS = "id, tenant_id, code, name, created_at, updated_at, version";

    private final JdbcClient jdbc;
    private final LocalizedTextJson texts;

    public UnitTypeRepository(JdbcClient jdbc, LocalizedTextJson texts) {
        this.jdbc = jdbc;
        this.texts = texts;
    }

    public void insert(UnitType type) {
        jdbc.sql("INSERT INTO unit_type (" + COLUMNS + ") VALUES (?, ?, ?, CAST(? AS jsonb), ?, ?, ?)")
                .params(
                        type.id(),
                        type.tenantId().value(),
                        type.code().value(),
                        texts.write(type.name()),
                        Rows.timestamp(type.createdAt()),
                        Rows.timestamp(type.updatedAt()),
                        type.version())
                .update();
    }

    public Optional<UnitType> find(UUID id) {
        return jdbc.sql("SELECT " + COLUMNS + " FROM unit_type WHERE id = ?")
                .param(id)
                .query(this::map)
                .optional();
    }

    public boolean existsByCode(BusinessCode code) {
        return jdbc.sql("SELECT EXISTS (SELECT 1 FROM unit_type WHERE code = ?)")
                .param(code.value())
                .query(Boolean.class)
                .single();
    }

    public List<UnitType> list() {
        return jdbc.sql("SELECT " + COLUMNS + " FROM unit_type ORDER BY code")
                .query(this::map)
                .list();
    }

    /** Tipos que podem ser pais do tipo informado; vazio = qualquer pai. */
    public Set<UUID> allowedParents(UUID childType) {
        return new HashSet<>(jdbc.sql("SELECT parent_type_id FROM unit_type_nesting WHERE child_type_id = ?")
                .param(childType)
                .query(UUID.class)
                .list());
    }

    public void replaceNesting(TenantId tenant, UUID childType, Set<UUID> parentTypes) {
        jdbc.sql("DELETE FROM unit_type_nesting WHERE child_type_id = ?")
                .param(childType)
                .update();
        for (UUID parent : parentTypes) {
            jdbc.sql("INSERT INTO unit_type_nesting (tenant_id, child_type_id, parent_type_id) VALUES (?, ?, ?)")
                    .params(tenant.value(), childType, parent)
                    .update();
        }
    }

    private UnitType map(ResultSet rs, int row) throws SQLException {
        return new UnitType(
                rs.getObject("id", UUID.class),
                new TenantId(rs.getObject("tenant_id", UUID.class)),
                new BusinessCode(rs.getString("code")),
                texts.read(rs.getString("name")),
                Rows.instant(rs, "created_at"),
                Rows.instant(rs, "updated_at"),
                rs.getLong("version"));
    }
}
