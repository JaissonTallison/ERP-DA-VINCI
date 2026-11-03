# ADR-0016: Identidade com Keycloak Organizations e login BFF

- Estado: Aceito
- Data: 2026-11-03
- Complementa: ADR-0009 (decide o modelo de tenants no IdP, que estava em aberto). O motor de políticas de autorização continua em aberto, para o contexto Governance.

## Contexto

O ERP precisa de milhares de tenants por região, cada um com seu SSO corporativo, e de um login web seguro para dados financeiros.

## Decisão

1. **Um realm de usuários por região (`davinci`), com Keycloak Organizations.** Cada tenant é uma organização (alias = slug, atributo `tenant_id`). Realm por tenant foi descartado: o Keycloak degrada com centenas de realms e a operação se multiplica.
2. **A organização segue o tenant:** o serviço Identity consome os eventos do Tenant Registry e cria, habilita ou desabilita a organização, de forma idempotente por versão. Só tenant `ACTIVE` tem organização habilitada.
3. **O tenant do usuário vem do claim `organization`** (com id e atributos). O frontend exige exatamente uma organização. Usuário em várias organizações exigirá seleção explícita no login (fora desta entrega).
4. **Login web por BFF:** o servidor Next.js é cliente confidencial OIDC (Authorization Code + PKCE); tokens ficam no Redis e o navegador só recebe um cookie `HttpOnly` aleatório. Tokens no navegador foram descartados por exposição a XSS.
5. **MFA obrigatório para administradores** no próprio fluxo do Keycloak (condição por papel `tenant-admin` + TOTP).
6. **Realm como código:** `infra/local/keycloak/davinci-realm.json`, com escopos e claims declarados um a um. Em produção, o mesmo conteúdo será aplicado por ferramenta de configuração (definição junto com o ADR-0013).

## Consequências

- Suspender um tenant bloqueia novos logins em segundos e encerra as sessões ativas na próxima renovação de token (até 5 min).
- O Keycloak de cada região é dependência crítica do login (alta disponibilidade obrigatória), mas não das APIs, que validam tokens localmente.
- O Redis passa a guardar sessões: em produção precisa de persistência e alta disponibilidade; perder o Redis desloga os usuários, sem perda de dados.
