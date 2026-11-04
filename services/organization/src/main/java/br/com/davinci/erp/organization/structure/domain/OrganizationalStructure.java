package br.com.davinci.erp.organization.structure.domain;

import br.com.davinci.erp.organization.shared.domain.BusinessCode;
import br.com.davinci.erp.organization.shared.domain.LocalizedText;
import br.com.davinci.erp.platform.kernel.TenantId;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/** Uma visão hierárquica das unidades (gerencial, funcional, geográfica…), com vínculos com vigência. */
public record OrganizationalStructure(
        UUID id,
        TenantId tenantId,
        BusinessCode code,
        LocalizedText name,
        StructurePurpose purpose,
        Instant createdAt,
        Instant updatedAt,
        long version) {

    public OrganizationalStructure {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(tenantId, "tenantId");
        Objects.requireNonNull(code, "code");
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(purpose, "purpose");
    }
}
