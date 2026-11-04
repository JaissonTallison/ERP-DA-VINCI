# Confiabilidade

> Estado: **metas aprovadas como ponto de partida**; serão recalibradas com dados reais de produção.

## SLI, SLO e SLA

- **SLI** (o que medimos): disponibilidade = requisições bem-sucedidas / total; latência = p95/p99 por rota; frescor = atraso de consumo de eventos.
- **SLO** (meta interna): definido por serviço, em janela móvel de 28 dias, com _error budget_. Estourou o orçamento → congela releases de funcionalidade naquela célula.
- **SLA** (contrato): sempre mais frouxo que o SLO.

| Camada                                             | SLO de disponibilidade                 | Latência p95 (API) | SLA sugerido |
| -------------------------------------------------- | -------------------------------------- | ------------------ | ------------ |
| Crítica (Identity, Finance, Sales, Tax, Inventory) | 99,95%                                 | 300 ms             | 99,9%        |
| Padrão (demais domínios transacionais)             | 99,9%                                  | 500 ms             | 99,5%        |
| Analítica (Analytics, relatórios)                  | 99,5%; frescor ≤ 15 min                | 2 s                | 99%          |
| Control Plane                                      | 99,9% (não afeta operação dos tenants) | —                  | —            |

## RPO e RTO por domínio

| Domínio                                                | RPO      | RTO  | Motivo                                                  |
| ------------------------------------------------------ | -------- | ---- | ------------------------------------------------------- |
| Identity, Governance (auditoria)                       | ≤ 1 min  | 1 h  | Sem identidade nada funciona; auditoria tem valor legal |
| Accounting, Finance, Tax, Payroll                      | ≤ 1 min  | 1 h  | Valor legal e financeiro                                |
| Sales, Purchasing, Inventory, Manufacturing, Logistics | ≤ 5 min  | 2 h  | Operação diária                                         |
| CRM, Commercial, HR, Quality, Projects, Organization   | ≤ 15 min | 4 h  | Toleram atraso curto                                    |
| Integration                                            | ≤ 15 min | 4 h  | Reprocessável a partir da origem                        |
| Analytics                                              | 24 h     | 24 h | Reconstruível a partir de eventos e Iceberg             |
| Tenant Registry (CP)                                   | ≤ 5 min  | 4 h  | Data planes seguem operando com cache                   |

## Alta disponibilidade, backup e DR

- Cada célula distribuída em **3 zonas de disponibilidade** na região; nenhum componente com instância única.
- PostgreSQL: réplica síncrona na região + WAL contínuo para object storage (PITR); backups completos diários.
- Kafka: fator de replicação 3, `min.insync.replicas=2`, produtores com `acks=all` e idempotência.
- Backups cifrados, com cópia em região par da mesma jurisdição e retenção imutável.
- **Testes de recuperação:** restauração automática de backup em ambiente isolado semanal (verifica integridade e mede RTO); exercício de DR por região a cada trimestre; game days com injeção de falha.

## Padrões obrigatórios nos serviços

| Padrão                    | Regra                                                                                                                                                                     |
| ------------------------- | ------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| Idempotência              | Todo comando externo aceita `Idempotency-Key`; consumidores de eventos deduplicam por `event_id` (tabela de processados).                                                 |
| Transactional Outbox      | Evento é gravado na mesma transação do estado, em tabela de outbox, e publicado por relay. Proibido "salvar e publicar" em duas etapas (ADR-0008).                        |
| Saga                      | Processos entre serviços: coreografia para fluxos simples; orquestração explícita (estado persistido) para fluxos com compensação complexa (provisionamento, fechamento). |
| Circuit breaker e timeout | Toda chamada síncrona tem timeout, retry com backoff e jitter (só em operação idempotente) e circuit breaker (Resilience4j / Spring).                                     |
| Rate limiting             | Na borda por tenant e por cliente de API (proteção contra vizinho barulhento); quotas por plano.                                                                          |
| Bulkhead                  | Pools separados por dependência; filas por tenant em processamentos pesados.                                                                                              |
| Degradação                | Funcionalidades não essenciais desligam antes de afetar o núcleo transacional.                                                                                            |

## Observabilidade distribuída

- **OpenTelemetry** em todos os serviços (traces, métricas, logs), com `tenant_id`, `cell_id` e `correlation_id` como atributos.
- Backend: Prometheus (métricas), Loki (logs), Tempo (traces), Grafana (painéis e alertas). Localmente, `grafana/otel-lgtm` (perfil `observability`).
- Logs estruturados em JSON, **sem dados pessoais** em claro.
- Alertas por consumo de error budget (_burn rate_), não por limiar fixo de CPU.
