package br.com.davinci.erp.tenantregistry.domain;

/** OPEN recebe novos tenants; CLOSED só mantém os atuais (drenagem, manutenção, capacidade revista). */
public enum CellStatus {
    OPEN,
    CLOSED
}
