package br.com.davinci.erp.tenantregistry.api;

import br.com.davinci.erp.tenantregistry.domain.Cell;
import java.time.Instant;
import java.util.List;

/** Representação v1 da célula. Contrato: components.schemas.Cell. */
public record CellResponse(
        String id, String region, String isolation, int capacity, int tenantCount, String status, Instant createdAt) {

    static CellResponse from(Cell cell) {
        return new CellResponse(
                cell.id().toString(),
                cell.id().region().value(),
                cell.id().isolation().code(),
                cell.capacity(),
                cell.tenantCount(),
                cell.status().name(),
                cell.createdAt());
    }

    public record Page(List<CellResponse> items) {}
}
