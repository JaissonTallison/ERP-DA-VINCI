package br.com.davinci.erp.governance.access.domain;

import br.com.davinci.erp.governance.shared.domain.ConflictException;
import br.com.davinci.erp.governance.shared.domain.EffectivePeriod;
import br.com.davinci.erp.governance.shared.domain.InvalidValueException;
import br.com.davinci.erp.platform.kernel.TenantId;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Objects;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/** Papel atribuído a um usuário ({@code sub} do token) num escopo, durante um período. */
public record Assignment(
        UUID id,
        TenantId tenantId,
        String subject,
        UUID roleId,
        ScopeType scopeType,
        @Nullable UUID scopeId,
        EffectivePeriod period,
        String grantedBy,
        Instant createdAt) {

    public enum ScopeType {
        TENANT,
        ECONOMIC_GROUP,
        LEGAL_ENTITY,
        ESTABLISHMENT,
        UNIT
    }

    public Assignment {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(tenantId, "tenantId");
        if (subject == null || subject.isBlank() || subject.length() > 200) {
            throw new InvalidValueException("subject", "Usuário obrigatório, até 200 caracteres");
        }
        Objects.requireNonNull(roleId, "roleId");
        Objects.requireNonNull(scopeType, "scopeType");
        if ((scopeType == ScopeType.TENANT) != (scopeId == null)) {
            throw new InvalidValueException("scopeId", "Escopo TENANT não tem identificador; os demais exigem");
        }
        Objects.requireNonNull(period, "period");
        if (subject.equals(grantedBy)) {
            throw new ConflictException("self-grant", "Ninguém concede acesso a si mesmo");
        }
    }

    public boolean overlaps(EffectivePeriod other) {
        boolean startsBeforeOtherEnds =
                other.validTo() == null || period.validFrom().isBefore(other.validTo());
        boolean endsAfterOtherStarts =
                period.validTo() == null || other.validFrom().isBefore(period.validTo());
        return startsBeforeOtherEnds && endsAfterOtherStarts;
    }

    public boolean sameGrant(Assignment other) {
        return roleId.equals(other.roleId) && scopeType == other.scopeType && Objects.equals(scopeId, other.scopeId);
    }

    public Assignment endingOn(LocalDate on) {
        if (period.validTo() != null) {
            throw new ConflictException("invalid-transition", "A atribuição já está encerrada");
        }
        if (!on.isAfter(period.validFrom())) {
            throw new InvalidValueException("effectiveOn", "O fim deve ser depois do início da atribuição");
        }
        return new Assignment(
                id, tenantId, subject, roleId, scopeType, scopeId, period.endingOn(on), grantedBy, createdAt);
    }
}
