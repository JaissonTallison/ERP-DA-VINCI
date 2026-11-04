package br.com.davinci.erp.organization.structure.domain;

import br.com.davinci.erp.organization.shared.domain.BusinessCode;
import br.com.davinci.erp.organization.shared.domain.LocalizedText;
import br.com.davinci.erp.platform.kernel.TenantId;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/** Nível de unidade definido pelo cliente (diretoria, gerência, departamento, célula…). */
public record UnitType(
        UUID id,
        TenantId tenantId,
        BusinessCode code,
        LocalizedText name,
        Instant createdAt,
        Instant updatedAt,
        long version) {

    public UnitType {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(tenantId, "tenantId");
        Objects.requireNonNull(code, "code");
        Objects.requireNonNull(name, "name");
    }
}
