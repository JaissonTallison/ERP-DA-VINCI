package br.com.davinci.erp.organization.costing.domain;

/** Bloqueado não recebe lançamentos novos, mas continua na hierarquia; encerrado é definitivo. */
public enum CostCenterStatus {
    ACTIVE,
    BLOCKED,
    CLOSED
}
