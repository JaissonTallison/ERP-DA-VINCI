package br.com.davinci.erp.tenantregistry.domain;

import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;

/** Identificador legível e imutável do tenant, usado em subdomínios. De 3 a 40 caracteres. */
public record TenantSlug(String value) {

    private static final Pattern FORMAT = Pattern.compile("[a-z][a-z0-9-]{1,38}[a-z0-9]");

    /** Nomes que colidem com subdomínios e rotas da própria plataforma. */
    private static final Set<String> RESERVED =
            Set.of("admin", "api", "app", "auth", "console", "control-plane", "login", "status", "support", "www");

    public TenantSlug {
        Objects.requireNonNull(value, "value");
        if (!FORMAT.matcher(value).matches() || value.contains("--")) {
            throw new InvalidTenantDataException("slug", "Slug inválido: " + value);
        }
        if (RESERVED.contains(value)) {
            throw new InvalidTenantDataException("slug", "Slug reservado pela plataforma: " + value);
        }
    }

    @Override
    public String toString() {
        return value;
    }
}
