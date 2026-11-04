package br.com.davinci.erp.organization.corporate.domain;

import br.com.davinci.erp.organization.shared.domain.BusinessCode;
import br.com.davinci.erp.platform.kernel.TenantId;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Currency;
import java.util.Objects;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/**
 * Grupo econômico (ADR-0017): entidades jurídicas sob controle comum ou geridas em conjunto. Imutável: cada mudança
 * devolve uma nova versão; sem mudança, devolve a mesma instância.
 */
public record EconomicGroup(
        UUID id,
        TenantId tenantId,
        BusinessCode code,
        String name,
        Currency referenceCurrency,
        ZoneId referenceTimezone,
        EconomicGroupStatus status,
        @Nullable LocalDate closedOn,
        Instant createdAt,
        Instant updatedAt,
        long version) {

    public EconomicGroup {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(tenantId, "tenantId");
        Objects.requireNonNull(code, "code");
        name = Texts.required("name", name, 200);
        Objects.requireNonNull(referenceCurrency, "referenceCurrency");
        Objects.requireNonNull(referenceTimezone, "referenceTimezone");
        Objects.requireNonNull(status, "status");
    }

    public static EconomicGroup create(
            UUID id, TenantId tenant, BusinessCode code, String name, Currency currency, ZoneId timezone, Instant now) {
        return new EconomicGroup(
                id, tenant, code, name, currency, timezone, EconomicGroupStatus.ACTIVE, null, now, now, 0);
    }

    public EconomicGroup update(
            @Nullable String newName, @Nullable Currency currency, @Nullable ZoneId timezone, Instant now) {
        if (status == EconomicGroupStatus.CLOSED) {
            throw new InvalidTransitionException("alterar um grupo", status);
        }
        EconomicGroup changed = new EconomicGroup(
                id,
                tenantId,
                code,
                newName == null ? name : newName,
                currency == null ? referenceCurrency : currency,
                timezone == null ? referenceTimezone : timezone,
                status,
                closedOn,
                createdAt,
                now,
                version + 1);
        return changed.sameContentAs(this) ? this : changed;
    }

    /** Encerramento; o chamador garante que nenhuma entidade continua no grupo a partir de {@code on}. */
    public EconomicGroup close(LocalDate on, Instant now) {
        if (status == EconomicGroupStatus.CLOSED) {
            return this;
        }
        return new EconomicGroup(
                id,
                tenantId,
                code,
                name,
                referenceCurrency,
                referenceTimezone,
                EconomicGroupStatus.CLOSED,
                on,
                createdAt,
                now,
                version + 1);
    }

    private boolean sameContentAs(EconomicGroup other) {
        return name.equals(other.name)
                && referenceCurrency.equals(other.referenceCurrency)
                && referenceTimezone.equals(other.referenceTimezone);
    }
}
