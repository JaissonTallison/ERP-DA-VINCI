package br.com.davinci.erp.platform.kernel;

import java.util.Objects;
import java.util.UUID;

/**
 * Identificador global e imutável de um tenant, emitido pelo Tenant Registry.
 *
 * <p>Nunca é reaproveitado, nem quando o tenant é migrado entre células ou regiões.
 */
public record TenantId(UUID value) {

    public TenantId {
        Objects.requireNonNull(value, "value");
    }

    public static TenantId parse(String raw) {
        Objects.requireNonNull(raw, "raw");
        try {
            return new TenantId(UUID.fromString(raw));
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("TenantId inválido: " + raw, e);
        }
    }

    @Override
    public String toString() {
        return value.toString();
    }
}
