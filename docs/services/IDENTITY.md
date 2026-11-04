# Identity

Responsável pela identidade dos usuários na região: o realm `davinci` do Keycloak (um por região), a organização de cada tenant e o login do frontend. Código em `services/identity`, `apps/web/src/server` e `infra/local/keycloak/davinci-realm.json`. Decisões: ADR-0009 e ADR-0016.

## Como as peças se encaixam

```
Tenant Registry ──(tenant-registry.tenant.v1)──▶ Identity ──(API admin, client_credentials)──▶ Keycloak
                                                                                                │ realm davinci
Navegador ──cookie httpOnly──▶ apps/web (BFF) ──OIDC Authorization Code + PKCE──────────────────┘
                                   │
                                   └── sessão e tokens no Redis
```

1. Um tenant registrado no Tenant Registry vira uma **organização** no Keycloak. Alias e nome = slug; descrição = nome de exibição; atributos `tenant_id`, `display_name`, `region`, `cell_id` e `isolation`.
2. A organização só fica **habilitada com o tenant `ACTIVE`**. Suspender o tenant desabilita a organização em segundos.
3. No login, o token traz o claim `organization` com o `tenant_id`. O BFF **exige exatamente uma organização** e recusa a sessão caso contrário.

## Serviço Identity (porta 5631)

- Consome `tenant-registry.tenant.v1` (grupo `identity`). Um grupo novo processa todo o histórico, então a projeção é reconstruível.
- **Idempotente por versão:** cada evento traz o estado completo do tenant; eventos com versão menor ou igual à aplicada são descartados. A tabela `tenant_organization` guarda a versão aplicada.
- **Falhas:**
  - Erros transitórios (Keycloak fora do ar) são repetidos 5 vezes, com 2 s de intervalo.
  - Mensagens fora do contrato, ou conflito de alias com organização de outro tenant, vão direto para a dead letter `tenant-registry.tenant.v1.identity-dlt`, sem travar a fila.
- **Menor privilégio:** a conta de serviço `identity-service` só tem `manage-organizations`, `view-organizations` e `query-organizations`. Não lê nem altera usuários.
- Sem API pública por enquanto; expõe só os probes de saúde.

## Realm `davinci`

| Item | Configuração |
| --- | --- |
| Organizations | Ativado; um tenant = uma organização |
| Login | Fluxo `davinci browser`: sessão → SSO da organização (identity-first) → usuário e senha → **TOTP obrigatório para `tenant-admin`** (configurado no primeiro login) |
| Senha | Mínimo 12 caracteres, diferente do usuário e do e-mail, sem repetir as 5 últimas; bloqueio progressivo após 5 erros |
| Tokens | Access token de 5 min; refresh token rotativo (uso único); sessão de 30 min de inatividade e 10 h no máximo |
| Escopos | Declarados um a um (`basic`, `profile`, `email`, `roles`, `acr`, `organization`): o token só tem os claims previstos |
| Papéis | `tenant-admin` e `tenant-user` (RBAC fino e ABAC ficam com o contexto Governance) |
| Idioma | pt-BR padrão; en e es disponíveis |

## Login no frontend (BFF)

| Proteção | Como |
| --- | --- |
| Tokens fora do navegador | Access, refresh e ID token ficam no Redis; o navegador só recebe `__Host-davinci-session` (aleatório, `HttpOnly`, `Secure`, `SameSite=Lax`) |
| Vazamento do Redis | A chave no Redis é o SHA-256 do identificador da sessão |
| Interceptação do código | Authorization Code + PKCE (S256), `state` e `nonce` guardados no servidor |
| Login CSRF | O `state` é amarrado ao navegador por cookie próprio, conferido no callback |
| Redirecionamento aberto | `returnTo` só aceita caminhos internos |
| Logout CSRF | Logout só por POST com `Origin` da própria aplicação |
| Logout completo | Encerra a sessão local e a do Keycloak (RP-Initiated Logout) |
| Renovação concorrente | O refresh token gira a cada uso; uma trava no Redis garante uma renovação por vez |
| Tenant suspenso | Organização desabilitada some do token: novo login cai em "Sua conta não está vinculada a uma empresa ativa"; a renovação de token também falha e encerra a sessão |

Rotas: `GET /auth/login?returnTo=`, `GET /auth/callback`, `POST /auth/logout`, `GET /auth/erro?motivo=`.

## Rodar localmente

```bash
docker compose --profile identity up -d
./gradlew :tenant-registry:bootRun        # 5630 (outro terminal)
./gradlew :identity:bootRun               # 5631 (outro terminal)
pnpm dev                                  # 5620

# registre e ative um tenant (ver docs/services/TENANT_REGISTRY.md) e crie usuários de teste:
scripts/dev/seed-identity.sh acme
```

Entre em http://localhost:5620 com `usuario@acme.dev` ou `admin@acme.dev` (senha `Davinci@dev2026`; o administrador configura o TOTP no primeiro acesso).

Ambiente criado antes desta entrega? O banco `identity` e o realm `davinci` só são criados na primeira subida dos volumes: recrie com `docker compose down -v`, ou crie o banco à mão (`CREATE ROLE identity LOGIN PASSWORD 'identity_dev'; CREATE DATABASE identity OWNER identity;`) e reinicie o Keycloak (`docker compose restart keycloak`).

Localmente, Keycloak e web rodam em `localhost` e cookies não separam por porta: o navegador envia os cookies do Keycloak também para a aplicação. Em produção ficam em domínios diferentes.

## Variáveis de ambiente

> Credenciais e endereços de infraestrutura **não têm valor padrão**: sem a variável, o serviço não sobe (falha rápida, em vez de usar senha de desenvolvimento). Os valores de desenvolvimento ficam no perfil `local` (`application-local.yaml`), ativado automaticamente pelo `bootRun` e pelos testes. Para rodar o jar localmente: `java -jar <serviço>.jar --spring.profiles.active=local`.

Identity, obrigatórias: `DATABASE_URL`, `DATABASE_USERNAME`, `DATABASE_PASSWORD`, `KAFKA_BOOTSTRAP_SERVERS`, `KEYCLOAK_URL`, `KEYCLOAK_CLIENT_SECRET`. Com padrão: `PORT` (5631), `KEYCLOAK_REALM` (davinci), `KEYCLOAK_CLIENT_ID` (identity-service), `KAFKA_TOPIC_REPLICAS` (1).

Web: ver `apps/web/.env.example`. Em produção todas são obrigatórias; em desenvolvimento (`pnpm dev`) há padrões apontando para o compose.
