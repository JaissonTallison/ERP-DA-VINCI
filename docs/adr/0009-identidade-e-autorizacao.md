# ADR-0009: Identidade federada via Keycloak e autorização RBAC + ABAC

- Estado: Aceito (motor de políticas em aberto)
- Data: 2026-11-03

## Decisão

- OAuth 2.0 + OpenID Connect para usuários e serviços; SAML 2.0 para federação quando o IdP do cliente exigir.
- **Keycloak** por região como IdP da plataforma e _broker_ para Entra ID, Okta e demais IdPs de clientes. Serviços confiam apenas no emissor regional.
- Tokens de curta duração com `tenant_id` e `cell_id`; validação local via JWKS.
- MFA obrigatório para administradores e acessos privilegiados.
- Autorização RBAC + ABAC, políticas no contexto Governance e avaliação local nos serviços (sem chamada síncrona por requisição).

## Em aberto

- Motor de políticas: OPA (Rego), Cedar ou implementação própria. Decidir no início do contexto Governance, com prova de conceito medindo latência e auditabilidade.
- Uso de _Organizations_ do Keycloak × realm por tenant (impacta escala para milhares de tenants por região).

## Consequências

- Keycloak passa a ser componente crítico por região (alta disponibilidade obrigatória).
