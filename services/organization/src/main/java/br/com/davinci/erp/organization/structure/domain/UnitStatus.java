package br.com.davinci.erp.organization.structure.domain;

/** {@code ACTIVE} ⇄ {@code INACTIVE} (fora de uso, mantida na árvore) → {@code CLOSED} (terminal). */
public enum UnitStatus {
    ACTIVE,
    INACTIVE,
    CLOSED
}
