package br.com.davinci.erp.organization.costing.domain;

import br.com.davinci.erp.organization.shared.domain.BusinessCode;
import br.com.davinci.erp.organization.shared.domain.ConflictException;
import br.com.davinci.erp.organization.shared.domain.InvalidValueException;
import br.com.davinci.erp.platform.kernel.TenantId;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Objects;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/** Centro de custo de uma entidade jurídica. Imutável: cada mudança devolve nova versão; sem mudança, a mesma. */
public record CostCenter(
        UUID id,
        TenantId tenantId,
        UUID legalEntityId,
        BusinessCode code,
        String name,
        CostCenterKind kind,
        String category,
        @Nullable UUID responsibleUnitId,
        CostCenterStatus status,
        LocalDate validFrom,
        @Nullable LocalDate closedOn,
        Instant createdAt,
        Instant updatedAt,
        long version) {

    public CostCenter {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(tenantId, "tenantId");
        Objects.requireNonNull(legalEntityId, "legalEntityId");
        Objects.requireNonNull(code, "code");
        name = required("name", name, 200);
        Objects.requireNonNull(kind, "kind");
        category = required("category", category, 60);
        Objects.requireNonNull(status, "status");
        Objects.requireNonNull(validFrom, "validFrom");
    }

    public static CostCenter create(
            UUID id,
            TenantId tenant,
            UUID legalEntityId,
            BusinessCode code,
            String name,
            CostCenterKind kind,
            String category,
            @Nullable UUID responsibleUnitId,
            LocalDate validFrom,
            Instant now) {
        return new CostCenter(
                id,
                tenant,
                legalEntityId,
                code,
                name,
                kind,
                category,
                responsibleUnitId,
                CostCenterStatus.ACTIVE,
                validFrom,
                null,
                now,
                now,
                0);
    }

    public CostCenter update(String newName, String newCategory, @Nullable UUID responsible, Instant now) {
        requireOpen("alterar");
        if (name.equals(newName) && category.equals(newCategory) && Objects.equals(responsibleUnitId, responsible)) {
            return this;
        }
        return with(newName, newCategory, responsible, status, closedOn, now);
    }

    public CostCenter block(Instant now) {
        requireOpen("bloquear");
        return status == CostCenterStatus.BLOCKED
                ? this
                : with(name, category, responsibleUnitId, CostCenterStatus.BLOCKED, closedOn, now);
    }

    public CostCenter unblock(Instant now) {
        requireOpen("desbloquear");
        return status == CostCenterStatus.ACTIVE
                ? this
                : with(name, category, responsibleUnitId, CostCenterStatus.ACTIVE, closedOn, now);
    }

    /** O chamador garante que não há filhos a partir de {@code on}. */
    public CostCenter close(LocalDate on, Instant now) {
        if (status == CostCenterStatus.CLOSED) {
            return this;
        }
        if (on.isBefore(validFrom)) {
            throw new InvalidValueException("effectiveOn", "Encerramento antes do início de vigência");
        }
        return with(name, category, responsibleUnitId, CostCenterStatus.CLOSED, on, now);
    }

    public boolean usableOn(LocalDate date) {
        return !date.isBefore(validFrom) && (closedOn == null || date.isBefore(closedOn));
    }

    private void requireOpen(String action) {
        if (status == CostCenterStatus.CLOSED) {
            throw new ConflictException("invalid-transition", "Não é possível " + action + " um centro encerrado");
        }
    }

    private CostCenter with(
            String n,
            String c,
            @Nullable UUID responsible,
            CostCenterStatus s,
            @Nullable LocalDate closed,
            Instant now) {
        return new CostCenter(
                id,
                tenantId,
                legalEntityId,
                code,
                n,
                kind,
                c,
                responsible,
                s,
                validFrom,
                closed,
                createdAt,
                now,
                version + 1);
    }

    private static String required(String field, @Nullable String value, int max) {
        if (value == null || value.isBlank()) {
            throw new InvalidValueException(field, field + " é obrigatório");
        }
        String trimmed = value.strip();
        if (trimmed.length() > max) {
            throw new InvalidValueException(field, field + " deve ter no máximo " + max + " caracteres");
        }
        return trimmed;
    }
}
