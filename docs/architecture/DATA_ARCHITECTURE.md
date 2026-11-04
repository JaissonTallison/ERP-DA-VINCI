# Arquitetura de dados

> Estado: **aprovado**. Decisões: ADR-0010 (persistência poliglota), ADR-0004 (isolamento), ADR-0008 e ADR-0014 (eventos).

## Princípios

1. **Banco por serviço.** Nenhum serviço lê ou escreve no banco de outro. Dados de outro contexto chegam por evento (projeção local) ou por API.
2. **PostgreSQL é a fonte da verdade transacional.** As demais tecnologias guardam cópias derivadas e reconstruíveis.
3. **Esquema versionado no próprio serviço** (Flyway, `src/main/resources/db/migration`). Migração nunca é editada depois de aplicada; correção é uma nova migração.
4. **Dados ficam na região de residência do tenant** (ver SYSTEM_ARCHITECTURE.md).

## Tecnologias e papéis

| Tecnologia                      | Papel                                                                        | Situação                             |
| ------------------------------- | ---------------------------------------------------------------------------- | ------------------------------------ |
| PostgreSQL 18                   | Fonte da verdade, um banco por serviço                                       | Em uso (Tenant Registry, Identity)   |
| Redis 8                         | Sessões do BFF, cache, rate limiting e travas curtas; nunca fonte da verdade | Em uso (sessões do web)              |
| Kafka                           | Eventos de domínio e integração                                              | Em uso (`tenant-registry.tenant.v1`) |
| OpenSearch                      | Busca textual (projeção)                                                     | Planejado                            |
| ClickHouse                      | Analytics operacional (projeção)                                             | Planejado                            |
| Apache Iceberg + object storage | Histórico analítico e lakehouse                                              | Planejado                            |
| Object storage S3               | Anexos, documentos, backups, auditoria imutável                              | Planejado (SeaweedFS no local)       |

## Convenções de modelagem

| Tema            | Regra                                                                                                       |
| --------------- | ----------------------------------------------------------------------------------------------------------- |
| Identificadores | `uuid` gerado pela aplicação; nunca sequência exposta em API                                                |
| Datas e horas   | `timestamptz`, sempre gravado em UTC; precisão de microssegundos (a aplicação trunca para o mesmo valor)    |
| Dinheiro        | `numeric(19,4)` com o código da moeda (ISO 4217) ao lado; em Java, `BigDecimal`. Nunca ponto flutuante      |
| Tenant          | `tenant_id uuid NOT NULL` em toda tabela de negócio (ver MULTITENANCY.md)                                   |
| Concorrência    | Coluna `version` com controle otimista nas atualizações                                                     |
| Exclusão        | Registros com valor legal (financeiro, fiscal, trabalhista) não são apagados: são cancelados ou estornados  |
| Nomes           | `snake_case`, tabelas no singular (`tenant`, `outbox_event`), restrições nomeadas (`<tabela>_<coluna>_key`) |
| Acesso          | SQL explícito com `JdbcClient` (ADR-0015) ou Spring Data JDBC; sem ORM com carregamento implícito           |

## Tabelas técnicas padrão

| Tabela                 | Para que serve                                                         | Exemplo                          |
| ---------------------- | ---------------------------------------------------------------------- | -------------------------------- |
| `outbox_event`         | Eventos gravados na mesma transação do estado (Transactional Outbox)   | Tenant Registry                  |
| `idempotency_record`   | Repetição segura de comandos com `Idempotency-Key`                     | Tenant Registry                  |
| Projeção com `version` | Estado de outro contexto recebido por evento; descarta versões antigas | `tenant_organization` (Identity) |

## Bancos do ambiente local

| Banco             | Dono                                                                 | Criado por                                   |
| ----------------- | -------------------------------------------------------------------- | -------------------------------------------- |
| `davinci`         | Uso geral do desenvolvimento                                         | `compose.yaml`                               |
| `keycloak`        | Keycloak                                                             | `infra/local/postgres/init/01-databases.sql` |
| `tenant_registry` | Tenant Registry                                                      | idem                                         |
| `identity`        | Identity                                                             | idem                                         |
| `organization`    | Organization (papéis `organization_owner` e `organization_app`; RLS) | idem                                         |

O script de inicialização só roda quando o volume é criado. Em ambiente já existente, crie o banco à mão ou recrie o volume (`docker compose down -v`, que apaga os dados locais).

## Retenção, backup e recuperação

- RPO e RTO por domínio: ver RELIABILITY.md.
- Eventos publicados ficam 7 dias no outbox (reprocessamento e auditoria). No Kafka, a retenção é definida por tópico.
- Dados pessoais seguem a política de privacidade (ver ../security/SECURITY_ARCHITECTURE.md): minimização, cifragem de campos sensíveis e anonimização ao fim do prazo legal.
