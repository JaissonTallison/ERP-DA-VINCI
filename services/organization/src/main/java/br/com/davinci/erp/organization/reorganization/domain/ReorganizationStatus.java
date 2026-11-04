package br.com.davinci.erp.organization.reorganization.domain;

/** Ciclo: rascunho → aguardando aprovação → aplicada | rejeitada; cancelável antes da decisão. */
public enum ReorganizationStatus {
    DRAFT,
    PENDING_APPROVAL,
    APPLIED,
    REJECTED,
    CANCELLED
}
