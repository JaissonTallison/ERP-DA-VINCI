package br.com.davinci.erp.tenantregistry.domain;

import br.com.davinci.erp.platform.kernel.CellId;
import br.com.davinci.erp.platform.kernel.IsolationModel;
import br.com.davinci.erp.platform.kernel.RegionCode;
import br.com.davinci.erp.platform.kernel.TenantId;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import org.jspecify.annotations.Nullable;

/**
 * Agregado tenant. Imutável: cada transição devolve um novo estado e o evento correspondente.
 *
 * <p>Transições repetidas (ativar um tenant já ativo, suspender um já suspenso) são idempotentes: devolvem o
 * mesmo estado e nenhum evento.
 */
public record Tenant(
        TenantId id,
        TenantSlug slug,
        String displayName,
        RegionCode region,
        IsolationModel isolation,
        CellId cellId,
        TenantStatus status,
        @Nullable String statusReason,
        Instant createdAt,
        Instant updatedAt,
        long version) {

    public static final int DISPLAY_NAME_MAX = 120;
    public static final int REASON_MAX = 500;

    public Tenant {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(slug, "slug");
        Objects.requireNonNull(region, "region");
        Objects.requireNonNull(isolation, "isolation");
        Objects.requireNonNull(cellId, "cellId");
        Objects.requireNonNull(status, "status");
        Objects.requireNonNull(createdAt, "createdAt");
        Objects.requireNonNull(updatedAt, "updatedAt");
        displayName = requireText(displayName, "displayName", DISPLAY_NAME_MAX);
        if (!cellId.region().equals(region) || cellId.isolation() != isolation) {
            throw new IllegalArgumentException("Célula " + cellId + " incompatível com " + region + "/" + isolation);
        }
    }

    /** Novo tenant alocado em uma célula, aguardando o provisionamento nos data planes. */
    public static Transition register(TenantId id, TenantSlug slug, String displayName, CellId cell, Instant now) {
        Tenant tenant = new Tenant(
                id,
                slug,
                displayName,
                cell.region(),
                cell.isolation(),
                cell,
                TenantStatus.PROVISIONING,
                null,
                now,
                now,
                0);
        return new Transition(tenant, TenantEventType.REGISTERED);
    }

    public Transition activate(Instant now) {
        return switch (status) {
            case ACTIVE -> Transition.unchanged(this);
            case PROVISIONING -> new Transition(withStatus(TenantStatus.ACTIVE, null, now), TenantEventType.ACTIVATED);
            case SUSPENDED -> throw new InvalidTransitionException(status, "ativar");
        };
    }

    public Transition suspend(String reason, Instant now) {
        String validReason = requireText(reason, "reason", REASON_MAX);
        return switch (status) {
            case SUSPENDED -> Transition.unchanged(this);
            case ACTIVE ->
                new Transition(withStatus(TenantStatus.SUSPENDED, validReason, now), TenantEventType.SUSPENDED);
            case PROVISIONING -> throw new InvalidTransitionException(status, "suspender");
        };
    }

    public Transition reactivate(Instant now) {
        return switch (status) {
            case ACTIVE -> Transition.unchanged(this);
            case SUSPENDED -> new Transition(withStatus(TenantStatus.ACTIVE, null, now), TenantEventType.REACTIVATED);
            case PROVISIONING -> throw new InvalidTransitionException(status, "reativar");
        };
    }

    private Tenant withStatus(TenantStatus newStatus, @Nullable String reason, Instant now) {
        return new Tenant(
                id, slug, displayName, region, isolation, cellId, newStatus, reason, createdAt, now, version + 1);
    }

    private static String requireText(@Nullable String value, String field, int max) {
        String trimmed = value == null ? "" : value.strip();
        if (trimmed.isEmpty() || trimmed.length() > max) {
            throw new InvalidTenantDataException(field, field + " deve ter de 1 a " + max + " caracteres");
        }
        return trimmed;
    }

    /** Resultado de uma operação: novo estado e, se algo mudou, o evento a publicar. */
    public record Transition(Tenant tenant, @Nullable TenantEventType event) {

        static Transition unchanged(Tenant tenant) {
            return new Transition(tenant, null);
        }

        public Optional<TenantEventType> eventType() {
            return Optional.ofNullable(event);
        }
    }
}
