package br.com.davinci.erp.governance.shared.domain;

import java.util.UUID;

/** Recurso inexistente no tenant atual (inclui recurso de outro tenant: nunca se confirma a existência). */
public class NotFoundException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public NotFoundException(String resource, UUID id) {
        super(resource + " não encontrado: " + id);
    }
}
