# Organization

Serviço do contexto **Organization Management** (Data Plane): estrutura corporativa do cliente. Desenho completo em [docs/domains/organization](../domains/organization/ORGANIZATION_DOMAIN_MODEL.md); decisões nos ADRs 0017 a 0022.

## O que já existe (etapas 2 e 3)

| Recurso | Situação |
| --- | --- |
| Grupos econômicos | Criar, listar, consultar, alterar (`If-Match`), encerrar (só sem entidades a partir da data) |
| Entidades jurídicas | Criar (`PLANNED` ou `ACTIVE`), listar com filtros e `asOf`, consultar, alterar, ativar, suspender, reativar, encerrar |
| Estabelecimentos | Matriz, filial, fábrica, loja, depósito, escritório: abrir (entidade ativa, mesmo país), listar por entidade, alterar, suspender, reativar (entidade ativa), encerrar. Uma matriz não encerrada por entidade; no Brasil, CNPJ com a raiz da entidade (a matriz usa o próprio CNPJ da entidade). Entidade só encerra sem estabelecimento aberto |
| Pertença ao grupo | Com vigência; consulta "em qual grupo estava em D" |
| Identificadores fiscais | `BR_CNPJ` com dígitos verificadores (inclusive alfanumérico); `US_EIN` e `EU_VAT` só formato. Entidade brasileira ativa exige CNPJ principal vigente |
| Projeção do tenant | Eventos do Tenant Registry; tenant não ativo deixa a estrutura somente leitura |
| Eventos | `organization.economic-group.v1`, `organization.legal-entity.v1`, `organization.establishment.v1` (outbox) |
| Isolamento | RLS forçado + chaves estrangeiras compostas por tenant, provados em teste pela API e direto no banco |
| Auditoria | `audit_log` append-only (ator, correlação, antes e depois) |
| Autorização | Provisória: `tenant-admin` escreve, `tenant-user` lê (ADR-0021) |

Próximas etapas: unidades e estruturas, reorganizações, centros de custo e participações ([plano](../domains/organization/ORGANIZATION_IMPLEMENTATION_PLAN.md)).

## API e eventos

- HTTP: [`contracts/http/organization/v1/openapi.yaml`](../../contracts/http/organization/v1/openapi.yaml). Convenções em [ORGANIZATION_API_CONTRACTS.md](../domains/organization/ORGANIZATION_API_CONTRACTS.md).
- Eventos: [`contracts/events/organization/v1/`](../../contracts/events/organization/v1/asyncapi.yaml).
- O tenant vem sempre do token do realm `davinci` (claim `organization`, audiência `organization`). Recurso de outro tenant responde 404.
- Datas sem informação valem "hoje" no fuso da entidade. Mudança de status com data futura ainda não é aceita (depende do agendador da etapa de reorganizações).

## Banco e papéis

| Papel | Uso |
| --- | --- |
| `organization_owner` | Dono das tabelas; só o Flyway usa |
| `organization_app` | A aplicação: sem privilégio de dono, sujeita ao RLS; sem `DELETE` no histórico (exceto correção de grupo no mesmo dia) e só `INSERT`/`SELECT` na auditoria |

Toda transação começa com `set_config('app.tenant_id', <tenant>, true)`. Sem isso, qualquer consulta falha, inclusive para o dono (`FORCE ROW LEVEL SECURITY`).

## Rodar localmente

```bash
docker compose --profile identity up -d
./gradlew :organization:bootRun            # http://localhost:5632 (perfil local)
```

Ambiente criado antes desta entrega? Crie o banco e os papéis (o script de inicialização só roda em volume novo):

```sql
CREATE ROLE organization_owner LOGIN PASSWORD 'organization_owner_dev';
CREATE ROLE organization_app LOGIN PASSWORD 'organization_app_dev';
CREATE DATABASE organization OWNER organization_owner;
```

E, no Keycloak local já existente, inclua o escopo `organization-api` (audiência) no cliente `davinci-web` pelo console, ou recrie o realm (`docker compose down -v` apaga os dados locais).

## Variáveis de ambiente

Obrigatórias (sem valor padrão fora do perfil `local`): `DATABASE_URL`, `DATABASE_USERNAME`, `DATABASE_PASSWORD`, `DATABASE_MIGRATION_USERNAME`, `DATABASE_MIGRATION_PASSWORD`, `KAFKA_BOOTSTRAP_SERVERS`, `OIDC_ISSUER_URI`. Com padrão: `PORT` (5632), `KAFKA_TOPIC_REPLICAS` (1), `TRACING_SAMPLING`, endpoints OTLP.
