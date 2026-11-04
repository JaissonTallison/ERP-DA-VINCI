package br.com.davinci.erp.organization.corporate.domain;

/** {@code PLANNED} (em constituição) → {@code ACTIVE} ⇄ {@code SUSPENDED} → {@code CLOSED} (terminal). */
public enum LegalEntityStatus {
    PLANNED,
    ACTIVE,
    SUSPENDED,
    CLOSED
}
