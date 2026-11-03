package br.com.davinci.erp.tenantregistry.application;

import br.com.davinci.erp.platform.kernel.CellId;
import br.com.davinci.erp.platform.kernel.RegionCode;
import br.com.davinci.erp.tenantregistry.domain.Cell;
import br.com.davinci.erp.tenantregistry.persistence.CellRepository;
import java.time.Clock;
import java.time.temporal.ChronoUnit;
import java.util.List;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CellService {

    private final CellRepository cells;
    private final Clock clock;

    public CellService(CellRepository cells, Clock clock) {
        this.cells = cells;
        this.clock = clock;
    }

    @Transactional
    public Cell register(CellId id, int capacity) {
        if (cells.findById(id).isPresent()) {
            throw ConflictException.cellExists(id.toString());
        }
        Cell cell = Cell.open(id, capacity, clock.instant().truncatedTo(ChronoUnit.MICROS));
        cells.insert(cell);
        return cell;
    }

    @Transactional(readOnly = true)
    public List<Cell> list(@Nullable RegionCode region) {
        return cells.list(region);
    }
}
