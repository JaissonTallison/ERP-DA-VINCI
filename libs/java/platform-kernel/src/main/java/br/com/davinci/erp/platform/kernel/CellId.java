package br.com.davinci.erp.platform.kernel;

import java.util.Objects;
import java.util.regex.Pattern;

/**
 * Célula de implantação dentro de uma região, por exemplo {@code br-1-pooled-01} ou {@code eu-1-silo-acme}.
 *
 * <p>O prefixo é sempre a região da célula; o tipo indica o modelo de isolamento.
 */
public record CellId(RegionCode region, IsolationModel isolation, String name) {

    private static final Pattern NAME = Pattern.compile("[a-z0-9]([a-z0-9-]{0,30}[a-z0-9])?");

    public CellId {
        Objects.requireNonNull(region, "region");
        Objects.requireNonNull(isolation, "isolation");
        Objects.requireNonNull(name, "name");
        if (!NAME.matcher(name).matches()) {
            throw new IllegalArgumentException("Nome de célula inválido: " + name);
        }
    }

    public static CellId parse(String raw) {
        Objects.requireNonNull(raw, "raw");
        String[] parts = raw.split("-", 4);
        if (parts.length != 4) {
            throw new IllegalArgumentException("CellId inválido: " + raw);
        }
        return new CellId(new RegionCode(parts[0] + "-" + parts[1]), IsolationModel.fromCode(parts[2]), parts[3]);
    }

    @Override
    public String toString() {
        return region + "-" + isolation.code() + "-" + name;
    }
}
