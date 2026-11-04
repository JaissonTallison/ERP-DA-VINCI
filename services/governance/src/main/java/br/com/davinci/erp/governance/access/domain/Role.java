package br.com.davinci.erp.governance.access.domain;

import br.com.davinci.erp.governance.shared.domain.BusinessCode;
import br.com.davinci.erp.governance.shared.domain.ConflictException;
import br.com.davinci.erp.governance.shared.domain.InvalidValueException;
import br.com.davinci.erp.platform.kernel.TenantId;
import java.time.Instant;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;
import java.util.regex.Pattern;

/** Conjunto nomeado de permissões do tenant, no formato {@code contexto:recurso:acao}. Imutável. */
public record Role(
        UUID id,
        TenantId tenantId,
        BusinessCode code,
        String name,
        Set<String> permissions,
        boolean active,
        Instant createdAt,
        Instant updatedAt,
        long version) {

    private static final Pattern PERMISSION = Pattern.compile("^[a-z][a-z-]*(:[a-z][a-z-]*){1,3}$");

    public Role {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(tenantId, "tenantId");
        Objects.requireNonNull(code, "code");
        if (name == null || name.isBlank() || name.length() > 200) {
            throw new InvalidValueException("name", "Nome obrigatório, até 200 caracteres");
        }
        if (permissions.isEmpty() || permissions.size() > 500) {
            throw new InvalidValueException("permissions", "Informe de 1 a 500 permissões");
        }
        for (String permission : permissions) {
            requirePermission("permissions", permission);
        }
        permissions = Set.copyOf(new TreeSet<>(permissions));
    }

    public static void requirePermission(String field, String permission) {
        if (!PERMISSION.matcher(permission).matches()) {
            throw new InvalidValueException(field, "Permissão inválida (contexto:recurso:acao): " + permission);
        }
    }

    public Role update(String newName, Set<String> newPermissions, Instant now) {
        requireActive();
        if (name.equals(newName) && permissions.equals(newPermissions)) {
            return this;
        }
        return new Role(id, tenantId, code, newName, newPermissions, true, createdAt, now, version + 1);
    }

    public Role deactivate(Instant now) {
        return active ? new Role(id, tenantId, code, name, permissions, false, createdAt, now, version + 1) : this;
    }

    public void requireActive() {
        if (!active) {
            throw new ConflictException("invalid-transition", "O papel " + code + " está inativo");
        }
    }
}
