package br.com.davinci.erp.organization.corporate.domain;

import br.com.davinci.erp.organization.shared.domain.CountryCode;
import br.com.davinci.erp.platform.kernel.TenantId;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/**
 * Titular de participação que não é entidade do tenant (pessoa, empresa de fora, fundo). O documento fiscal é dado
 * pessoal quando {@code PERSON}: só existe cifrado na persistência e não volta pela API.
 */
public record ExternalParty(
        UUID id,
        TenantId tenantId,
        Kind kind,
        String name,
        @Nullable CountryCode country,
        boolean hasTaxIdentifier,
        Instant createdAt) {

    public enum Kind {
        COMPANY,
        PERSON,
        FUND,
        OTHER
    }

    public ExternalParty {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(tenantId, "tenantId");
        Objects.requireNonNull(kind, "kind");
        name = Texts.required("name", name, 200);
        Objects.requireNonNull(createdAt, "createdAt");
    }
}
