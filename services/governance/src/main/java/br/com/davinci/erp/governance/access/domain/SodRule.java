package br.com.davinci.erp.governance.access.domain;

import br.com.davinci.erp.governance.shared.domain.BusinessCode;
import br.com.davinci.erp.governance.shared.domain.ConflictException;
import br.com.davinci.erp.governance.shared.domain.InvalidValueException;
import br.com.davinci.erp.platform.kernel.TenantId;
import java.time.Instant;
import java.util.Collection;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/** Segregação de funções: a mesma pessoa não pode ter as duas permissões no mesmo período. */
public record SodRule(
        UUID id,
        TenantId tenantId,
        BusinessCode code,
        String description,
        String permissionA,
        String permissionB,
        boolean active,
        Instant createdAt) {

    public SodRule {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(tenantId, "tenantId");
        Objects.requireNonNull(code, "code");
        if (description == null || description.isBlank() || description.length() > 500) {
            throw new InvalidValueException("description", "Descrição obrigatória, até 500 caracteres");
        }
        Role.requirePermission("permissionA", permissionA);
        Role.requirePermission("permissionB", permissionB);
        if (permissionA.equals(permissionB)) {
            throw new InvalidValueException("permissionB", "As duas permissões devem ser diferentes");
        }
    }

    public boolean violatedBy(Set<String> permissions) {
        return active && permissions.contains(permissionA) && permissions.contains(permissionB);
    }

    /** Lança conflito se o conjunto de permissões ferir alguma regra. */
    public static void require(Collection<SodRule> rules, Set<String> permissions) {
        for (SodRule rule : rules) {
            if (rule.violatedBy(permissions)) {
                throw new ConflictException(
                        "sod-violation",
                        "Segregação de funções (" + rule.code() + "): " + rule.permissionA() + " e "
                                + rule.permissionB() + " não podem ficar com a mesma pessoa");
            }
        }
    }
}
