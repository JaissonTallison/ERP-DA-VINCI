package br.com.davinci.erp.organization.shared.security;

/**
 * Ponto de decisão de autorização (ADR-0021). Os casos de uso só dependem desta interface: a implementação provisória
 * usa os papéis do token; a definitiva (Cedar, após a prova de conceito) acrescenta escopos por entidade e unidade.
 */
public interface PolicyDecisionPoint {

    /** Lança {@link ForbiddenException} se o usuário atual não tiver a permissão no tenant. */
    void require(Permission permission);
}
