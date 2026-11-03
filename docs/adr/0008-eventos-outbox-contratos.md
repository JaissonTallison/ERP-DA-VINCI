# ADR-0008: Integração por eventos com Transactional Outbox e contratos versionados

- Estado: Aceito
- Data: 2026-11-03

## Decisão

- Integração entre serviços é **assíncrona por padrão**, via Apache Kafka; chamadas síncronas (HTTP) só para consulta pontual, com timeout, retry idempotente e circuit breaker.
- Publicação sempre por **Transactional Outbox**: o evento é gravado na mesma transação do estado e publicado por relay (polling ou CDC com Debezium — escolha no primeiro serviço).
- Consumidores **idempotentes** (deduplicação por `event_id`).
- Envelope de evento padrão (CloudEvents 1.0): `id`, `source`, `type`, `time`, `tenantid`, `cellid`, `correlationid`, `dataschema`.
- Tópicos: `<contexto>.<agregado>.<versão>` (ex.: `sales.order.v1`), particionados por `tenant_id` + chave do agregado; criação explícita (auto-criação desligada).
- Contratos em `contracts/` (AsyncAPI 3 / OpenAPI 3.1), versionados; quebra exige nova versão maior e convivência.
- Processos distribuídos por **Saga**: coreografia quando simples; orquestração com estado persistido quando houver compensação complexa.

## Consequências

- Consistência eventual entre serviços é a regra e deve ser tratada na experiência do usuário.
- Formato de serialização (JSON Schema ou Avro/Protobuf com registry) será decidido no primeiro contrato.
