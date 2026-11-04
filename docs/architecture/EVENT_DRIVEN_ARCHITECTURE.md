# Arquitetura orientada a eventos

> Estado: **aprovado e em uso**. Decisões: ADR-0008 (eventos, outbox, sagas) e ADR-0014 (JSON + JSON Schema).

## Quando usar evento e quando usar API

| Situação                                                    | Mecanismo                                                                    |
| ----------------------------------------------------------- | ---------------------------------------------------------------------------- |
| Outro contexto precisa reagir a um fato ("tenant suspenso") | **Evento** (padrão)                                                          |
| Outro contexto precisa manter uma cópia local de dados      | **Evento** com estado completo (_event-carried state transfer_)              |
| Consulta pontual que precisa do dado mais recente           | API síncrona, com timeout, retry idempotente e circuit breaker               |
| Processo de vários passos com compensação                   | Saga: coreografada se simples, orquestrada com estado persistido se complexa |

Nunca há chamada síncrona em ciclo entre serviços.

## Envelope: CloudEvents 1.0 estruturado

Envelope e dados na mesma mensagem JSON (`application/cloudevents+json`):

| Campo                | Regra                                                    |
| -------------------- | -------------------------------------------------------- |
| `id`                 | UUID único do evento; base da deduplicação               |
| `source`             | Serviço de origem (ex. `/control-plane/tenant-registry`) |
| `type`               | `br.com.davinci.<contexto>.<agregado>.<fato>.v<N>`       |
| `time`               | Momento do fato, UTC                                     |
| `subject`            | Id do agregado                                           |
| `tenantid`, `cellid` | Tenant e célula afetados                                 |
| `correlationid`      | Propagado do `X-Correlation-Id` da requisição de origem  |
| `dataschema`         | URN do JSON Schema em `contracts/`                       |
| `data`               | Estado completo do agregado, com `version`               |

## Tópicos

| Regra                | Detalhe                                                                                                                                                                           |
| -------------------- | --------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| Nome                 | `<contexto>.<agregado>.v<N>` (ex. `tenant-registry.tenant.v1`)                                                                                                                    |
| Dono                 | O serviço produtor declara o tópico; a auto-criação está desligada no broker                                                                                                      |
| Chave                | Id do agregado: garante ordem por agregado (e por tenant)                                                                                                                         |
| Partições e réplicas | 6 partições; réplicas 1 no local e 3 em produção (`min.insync.replicas=2`)                                                                                                        |
| Dead letter          | `<tópico>.<grupo-consumidor>-dlt` (ex.: `tenant-registry.tenant.v1.identity-dlt`), com as mesmas partições do tópico de origem: falhas de consumidores diferentes não se misturam |

### Catálogo atual

| Tópico                                       | Produtor              | Consumidores                                      | Contrato                               |
| -------------------------------------------- | --------------------- | ------------------------------------------------- | -------------------------------------- |
| `tenant-registry.tenant.v1`                  | Tenant Registry       | Identity, Organization                            | `contracts/events/tenant-registry/v1/` |
| `tenant-registry.tenant.v1.identity-dlt`     | Identity (falhas)     | Operação                                          | Mensagem original, sem transformação   |
| `tenant-registry.tenant.v1.organization-dlt` | Organization (falhas) | Operação                                          | Mensagem original, sem transformação   |
| `organization.economic-group.v1`             | Organization          | Futuros (Accounting, Finance, Analytics)          | `contracts/events/organization/v1/`    |
| `organization.legal-entity.v1`               | Organization          | Futuros (Accounting, Finance, Tax, HR, Analytics) | `contracts/events/organization/v1/`    |
| `organization.establishment.v1`              | Organization          | Futuros (Tax, HR, Inventory, Logistics)           | `contracts/events/organization/v1/`    |
| `organization.unit.v1`                       | Organization          | Futuros (HR, Workflow, Analytics)                 | `contracts/events/organization/v1/`    |

## Garantias do produtor: Transactional Outbox

1. O serviço grava o estado e o evento (tabela `outbox_event`) **na mesma transação**.
2. O relay lê os pendentes em ordem (`FOR UPDATE SKIP LOCKED`), publica com `acks=all` e produtor idempotente, e marca como publicado.
3. Se cair entre publicar e marcar, o evento é reenviado: a entrega é **pelo menos uma vez**.

Proibido: gravar no banco e publicar no Kafka em duas etapas separadas.

## Garantias do consumidor

| Regra             | Como (exemplo: Identity)                                                                  |
| ----------------- | ----------------------------------------------------------------------------------------- |
| Idempotência      | Descarta eventos com `data.version` menor ou igual à já aplicada (projeção com `version`) |
| Ordem             | Uma partição por agregado; processamento sequencial por partição                          |
| Falha transitória | Retentativas com intervalo (5 × 2 s) antes de desistir                                    |
| Mensagem inválida | Vai direto para a dead letter, sem travar a partição                                      |
| Reconstrução      | Um grupo de consumidores novo relê o histórico (`auto-offset-reset: earliest`)            |

## Evolução de contratos

- Campo novo opcional: compatível, mesma versão.
- Remoção, renomeação ou mudança de tipo: nova versão maior (`v2`), com novo tópico e novo `type`, publicada em paralelo durante a transição.
- O produtor valida nos testes cada evento publicado contra o JSON Schema de `contracts/`; consumidores validam suas fixtures contra o mesmo schema.

## Observabilidade

- `correlationid` liga a requisição HTTP de origem a todos os eventos e logs derivados.
- Métrica `outbox.pending` no produtor (eventos aguardando publicação).
- Tópico de dead letter não vazio é sinal de alerta para a operação.

## Ferramentas no ambiente local

Dentro do contêiner, use o listener interno `kafka:19092`:

```bash
docker exec erp-da-vinci-kafka-1 /opt/kafka/bin/kafka-console-consumer.sh \
  --bootstrap-server kafka:19092 --topic tenant-registry.tenant.v1 --from-beginning
```
