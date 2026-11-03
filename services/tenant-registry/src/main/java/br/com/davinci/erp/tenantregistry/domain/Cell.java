package br.com.davinci.erp.tenantregistry.domain;

import br.com.davinci.erp.platform.kernel.CellId;
import br.com.davinci.erp.platform.kernel.IsolationModel;
import java.time.Instant;
import java.util.Objects;

/** Célula do catálogo, com capacidade máxima de tenants (ADR-0003). */
public record Cell(CellId id, int capacity, int tenantCount, CellStatus status, Instant createdAt) {

    public static final int MAX_CAPACITY = 100_000;

    public Cell {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(status, "status");
        Objects.requireNonNull(createdAt, "createdAt");
        if (capacity < 1 || capacity > MAX_CAPACITY) {
            throw new InvalidTenantDataException("capacity", "Capacidade deve estar entre 1 e " + MAX_CAPACITY);
        }
        if (id.isolation() == IsolationModel.SILO && capacity != 1) {
            throw new InvalidTenantDataException("capacity", "Célula silo é dedicada: capacidade deve ser 1");
        }
        if (tenantCount < 0 || tenantCount > capacity) {
            throw new IllegalArgumentException("tenantCount fora da capacidade: " + tenantCount);
        }
    }

    public static Cell open(CellId id, int capacity, Instant now) {
        return new Cell(id, capacity, 0, CellStatus.OPEN, now);
    }
}
