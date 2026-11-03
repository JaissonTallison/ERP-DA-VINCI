package br.com.davinci.erp.platform.kernel;

import java.util.Arrays;

/** Modelo de isolamento de um tenant. Ver ADR-0004. */
public enum IsolationModel {
    /** Infraestrutura e banco compartilhados; isolamento por {@code tenant_id} e Row-Level Security. */
    POOLED("pooled"),
    /** Serviços compartilhados na célula, banco ou schema dedicado ao tenant. */
    BRIDGE("bridge"),
    /** Célula inteira dedicada ao tenant (implantação dedicada). */
    SILO("silo");

    private final String code;

    IsolationModel(String code) {
        this.code = code;
    }

    public String code() {
        return code;
    }

    public static IsolationModel fromCode(String code) {
        return Arrays.stream(values())
                .filter(m -> m.code.equals(code))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Modelo de isolamento inválido: " + code));
    }
}
