# Governance

Serviço do contexto **Governance** (Data Plane, porta 5633): papéis, atribuições com escopo e vigência, segregação de funções e trilha de auditoria. Decisão no [ADR-0023](../adr/0023-governance-papeis-e-atribuicoes.md).

## O que já existe

| Recurso | Situação |
| --- | --- |
| Papéis | Criar, listar, alterar (`If-Match`), desativar. Permissões no formato `contexto:recurso:acao` |
| Atribuições | Conceder a um usuário num escopo, com início; revogar com data; consultar acessos vigentes em qualquer data |
| Segregação de funções | Regras por par de permissões, conferidas ao conceder, ao editar papel e ao criar regra |
| Eventos | `governance.role.v1`, `governance.assignment.v1` (outbox) |
| Histórico | `GET /v1/history/{tipo}/{id}` e `GET /v1/changes` (só administradores) |
| Isolamento | RLS forçado por tenant; projeção do tenant pelos eventos do Tenant Registry |

## O que ainda não existe

- Os outros serviços ainda não usam estas atribuições (dependem da prova de conceito do Cedar, ADR-0021).
- Workflows genéricos de aprovação e políticas Cedar.
- O identificador de escopo não é conferido contra o Organization.

## Rodar localmente

Em ambiente novo, o `compose` já cria o banco e os papéis. Em banco local já existente, crie `governance_owner`, `governance_app` e o banco `governance` como em `infra/local/postgres/init/01-databases.sql`. No Keycloak existente, inclua o escopo `governance-api` no cliente `davinci-web`, ou o token não terá a audiência `governance`.

```bash
./gradlew :governance:bootRun
```

## Variáveis de ambiente

Obrigatórias fora do perfil `local`: `DATABASE_URL`, `DATABASE_USERNAME`, `DATABASE_PASSWORD`, `DATABASE_MIGRATION_USERNAME`, `DATABASE_MIGRATION_PASSWORD`, `KAFKA_BOOTSTRAP_SERVERS`, `OIDC_ISSUER_URI`. No BFF, `GOVERNANCE_API_URL`.
