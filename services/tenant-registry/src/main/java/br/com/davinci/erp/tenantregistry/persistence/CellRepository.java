package br.com.davinci.erp.tenantregistry.persistence;

import br.com.davinci.erp.platform.kernel.CellId;
import br.com.davinci.erp.platform.kernel.IsolationModel;
import br.com.davinci.erp.platform.kernel.RegionCode;
import br.com.davinci.erp.tenantregistry.domain.Cell;
import br.com.davinci.erp.tenantregistry.domain.CellStatus;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import org.jspecify.annotations.Nullable;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
public class CellRepository {

    private static final String COLUMNS = "id, capacity, tenant_count, status, created_at";

    private final JdbcClient jdbc;

    public CellRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    public void insert(Cell cell) {
        jdbc.sql("INSERT INTO cell (id, region, isolation, capacity, tenant_count, status, created_at)"
                        + " VALUES (?, ?, ?, ?, ?, ?, ?)")
                .params(
                        cell.id().toString(),
                        cell.id().region().value(),
                        cell.id().isolation().code(),
                        cell.capacity(),
                        cell.tenantCount(),
                        cell.status().name(),
                        TenantRepository.timestamp(cell.createdAt()))
                .update();
    }

    public Optional<Cell> findById(CellId id) {
        return jdbc.sql("SELECT " + COLUMNS + " FROM cell WHERE id = ?")
                .param(id.toString())
                .query(CellRepository::map)
                .optional();
    }

    public List<Cell> list(@Nullable RegionCode region) {
        return jdbc.sql("SELECT " + COLUMNS + " FROM cell"
                        + " WHERE (CAST(:region AS text) IS NULL OR region = :region) ORDER BY id")
                .param("region", region == null ? null : region.value())
                .query(CellRepository::map)
                .list();
    }

    /** Células abertas com vaga, da menos ocupada para a mais ocupada. */
    public List<CellId> allocationCandidates(RegionCode region, IsolationModel isolation) {
        return jdbc.sql("""
                        SELECT id FROM cell
                        WHERE region = ? AND isolation = ? AND status = 'OPEN' AND tenant_count < capacity
                        ORDER BY tenant_count, id
                        """)
                .params(region.value(), isolation.code())
                .query((rs, row) -> CellId.parse(rs.getString("id")))
                .list();
    }

    /**
     * Ocupa uma vaga da célula de forma atômica. Devolve {@code false} se a célula lotou ou fechou entre a consulta
     * e a reserva (outra transação concorrente).
     */
    public boolean reserveSlot(CellId id) {
        return jdbc.sql("UPDATE cell SET tenant_count = tenant_count + 1"
                                + " WHERE id = ? AND status = 'OPEN' AND tenant_count < capacity")
                        .param(id.toString())
                        .update()
                == 1;
    }

    private static Cell map(ResultSet rs, int row) throws SQLException {
        return new Cell(
                CellId.parse(rs.getString("id")),
                rs.getInt("capacity"),
                rs.getInt("tenant_count"),
                CellStatus.valueOf(rs.getString("status")),
                rs.getObject("created_at", OffsetDateTime.class).toInstant());
    }
}
