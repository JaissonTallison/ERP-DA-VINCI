package br.com.davinci.erp.tenantregistry.application;

import br.com.davinci.erp.platform.kernel.CellId;
import br.com.davinci.erp.platform.kernel.RegionCode;
import br.com.davinci.erp.platform.kernel.TenantId;
import br.com.davinci.erp.tenantregistry.domain.Tenant;
import br.com.davinci.erp.tenantregistry.domain.TenantStatus;
import br.com.davinci.erp.tenantregistry.events.TenantEventPublisher;
import br.com.davinci.erp.tenantregistry.persistence.CellRepository;
import br.com.davinci.erp.tenantregistry.persistence.IdempotencyRepository;
import br.com.davinci.erp.tenantregistry.persistence.TenantRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;
import java.util.function.BiFunction;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TenantService {

    static final String REGISTER_OPERATION = "register-tenant";

    private final TenantRepository tenants;
    private final CellRepository cells;
    private final IdempotencyRepository idempotency;
    private final TenantEventPublisher events;
    private final Clock clock;

    public TenantService(
            TenantRepository tenants,
            CellRepository cells,
            IdempotencyRepository idempotency,
            TenantEventPublisher events,
            Clock clock) {
        this.tenants = tenants;
        this.cells = cells;
        this.idempotency = idempotency;
        this.events = events;
        this.clock = clock;
    }

    /**
     * Registra o tenant na célula menos ocupada da região e do modelo de isolamento pedidos. Repetir a mesma
     * requisição com a mesma {@code Idempotency-Key} devolve o tenant já criado, sem novo evento.
     */
    @Transactional
    public Tenant register(RegisterTenantCommand command) {
        String fingerprint = command.fingerprint();
        var previous = idempotency.find(REGISTER_OPERATION, command.idempotencyKey());
        if (previous.isPresent()) {
            if (!previous.get().requestHash().equals(fingerprint)) {
                throw ConflictException.idempotencyKeyReused();
            }
            return get(new TenantId(previous.get().resourceId()));
        }
        if (tenants.existsBySlug(command.slug())) {
            throw ConflictException.slugTaken(command.slug().value());
        }

        Instant now = now();
        CellId cell = allocateCell(command);
        Tenant.Transition registered =
                Tenant.register(new TenantId(UUID.randomUUID()), command.slug(), command.displayName(), cell, now);
        Tenant tenant = registered.tenant();

        tenants.insert(tenant);
        idempotency.insert(
                REGISTER_OPERATION,
                command.idempotencyKey(),
                fingerprint,
                tenant.id().value(),
                now);
        registered.eventType().ifPresent(type -> events.publish(tenant, type, now));
        return tenant;
    }

    @Transactional(readOnly = true)
    public Tenant get(TenantId id) {
        return tenants.findById(id).orElseThrow(() -> new TenantNotFoundException(id));
    }

    @Transactional(readOnly = true)
    public List<Tenant> page(
            @Nullable RegionCode region, @Nullable TenantStatus status, @Nullable String afterSlug, int limit) {
        return tenants.page(region, status, afterSlug, limit);
    }

    @Transactional
    public Tenant activate(TenantId id) {
        return transition(id, Tenant::activate);
    }

    @Transactional
    public Tenant suspend(TenantId id, String reason) {
        return transition(id, (tenant, now) -> tenant.suspend(reason, now));
    }

    @Transactional
    public Tenant reactivate(TenantId id) {
        return transition(id, Tenant::reactivate);
    }

    private Tenant transition(TenantId id, BiFunction<Tenant, Instant, Tenant.Transition> action) {
        Instant now = now();
        Tenant.Transition transition = action.apply(get(id), now);
        Tenant tenant = transition.tenant();
        transition.eventType().ifPresent(type -> {
            tenants.update(tenant);
            events.publish(tenant, type, now);
        });
        return tenant;
    }

    private CellId allocateCell(RegisterTenantCommand command) {
        for (CellId candidate : cells.allocationCandidates(command.region(), command.isolation())) {
            if (cells.reserveSlot(candidate)) {
                return candidate;
            }
        }
        throw ConflictException.noCellCapacity(
                command.region().value(), command.isolation().code());
    }

    /** Precisão de microssegundos, a mesma do PostgreSQL: o que a API devolve é o que fica gravado. */
    private Instant now() {
        return clock.instant().truncatedTo(ChronoUnit.MICROS);
    }
}
