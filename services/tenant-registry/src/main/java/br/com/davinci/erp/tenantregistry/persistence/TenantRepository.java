package br.com.davinci.erp.tenantregistry.persistence;

import br.com.davinci.erp.platform.kernel.CellId;
import br.com.davinci.erp.platform.kernel.IsolationModel;
import br.com.davinci.erp.platform.kernel.RegionCode;
import br.com.davinci.erp.platform.kernel.TenantId;
import br.com.davinci.erp.tenantregistry.domain.Tenant;
import br.com.davinci.erp.tenantregistry.domain.TenantSlug;
import br.com.davinci.erp.tenantregistry.domain.TenantStatus;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import org.jspecify.annotations.Nullable;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
public class TenantRepository {

    private static final String COLUMNS =
            "id, slug, display_name, region, isolation, cell_id, status, status_reason, created_at, updated_at, version";

    private final JdbcClient jdbc;

    public TenantRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    public void insert(Tenant tenant) {
        jdbc.sql("INSERT INTO tenant (" + COLUMNS + ") VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)")
                .params(
                        tenant.id().value(),
                        tenant.slug().value(),
                        tenant.displayName(),
                        tenant.region().value(),
                        tenant.isolation().code(),
                        tenant.cellId().toString(),
                        tenant.status().name(),
                        tenant.statusReason(),
                        timestamp(tenant.createdAt()),
                        timestamp(tenant.updatedAt()),
                        tenant.version())
                .update();
    }

    /** Atualiza com controle otimista: falha se outra transação alterou o tenant desde a leitura. */
    public void update(Tenant tenant) {
        int rows = jdbc.sql("""
                        UPDATE tenant SET status = ?, status_reason = ?, updated_at = ?, version = ?
                        WHERE id = ? AND version = ?
                        """)
                .params(
                        tenant.status().name(),
                        tenant.statusReason(),
                        timestamp(tenant.updatedAt()),
                        tenant.version(),
                        tenant.id().value(),
                        tenant.version() - 1)
                .update();
        if (rows != 1) {
            throw new ConcurrentUpdateException(tenant.id());
        }
    }

    public Optional<Tenant> findById(TenantId id) {
        return jdbc.sql("SELECT " + COLUMNS + " FROM tenant WHERE id = ?")
                .param(id.value())
                .query(TenantRepository::map)
                .optional();
    }

    public boolean existsBySlug(TenantSlug slug) {
        return jdbc.sql("SELECT EXISTS (SELECT 1 FROM tenant WHERE slug = ?)")
                .param(slug.value())
                .query(Boolean.class)
                .single();
    }

    /** Página ordenada por slug, a partir do slug seguinte a {@code afterSlug}. */
    public List<Tenant> page(
            @Nullable RegionCode region, @Nullable TenantStatus status, @Nullable String afterSlug, int limit) {
        return jdbc.sql("""
                        SELECT %s FROM tenant
                        WHERE (CAST(:region AS text) IS NULL OR region = :region)
                          AND (CAST(:status AS text) IS NULL OR status = :status)
                          AND (CAST(:after AS text) IS NULL OR slug > :after)
                        ORDER BY slug
                        LIMIT :limit
                        """.formatted(COLUMNS))
                .param("region", region == null ? null : region.value())
                .param("status", status == null ? null : status.name())
                .param("after", afterSlug)
                .param("limit", limit)
                .query(TenantRepository::map)
                .list();
    }

    private static Tenant map(ResultSet rs, int row) throws SQLException {
        return new Tenant(
                new TenantId(rs.getObject("id", java.util.UUID.class)),
                new TenantSlug(rs.getString("slug")),
                rs.getString("display_name"),
                new RegionCode(rs.getString("region")),
                IsolationModel.fromCode(rs.getString("isolation")),
                CellId.parse(rs.getString("cell_id")),
                TenantStatus.valueOf(rs.getString("status")),
                rs.getString("status_reason"),
                rs.getObject("created_at", OffsetDateTime.class).toInstant(),
                rs.getObject("updated_at", OffsetDateTime.class).toInstant(),
                rs.getLong("version"));
    }

    static OffsetDateTime timestamp(Instant instant) {
        return OffsetDateTime.ofInstant(instant, java.time.ZoneOffset.UTC);
    }
}
