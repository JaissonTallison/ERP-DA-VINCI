# ADR-0014: Eventos em JSON, validados por JSON Schema

- Estado: Aceito
- Data: 2026-11-03
- Complementa: ADR-0008 (decide o item "formato de serialização", que estava em aberto)

## Contexto

O ADR-0008 deixou em aberto o formato dos eventos: JSON Schema, Avro ou Protobuf com Schema Registry. O primeiro serviço com eventos (Tenant Registry) exige a decisão.

## Decisão

- Eventos em **JSON**, no formato **CloudEvents 1.0 estruturado** (`application/cloudevents+json`): envelope e dados na mesma mensagem.
- Cada evento tem **JSON Schema 2020-12** versionado em `contracts/events/<contexto>/v<N>/`, referenciado no campo `dataschema` (URN estável) e descrito em AsyncAPI 3.
- O **produtor** valida, nos testes de integração, cada evento publicado contra o schema do repositório. Se o código divergir do contrato, o CI quebra.
- `data` carrega o estado completo do agregado mais `version`, para que consumidores substituam a projeção sem consultar a API (_event-carried state transfer_).
- Mudança compatível (campo opcional novo) mantém a versão. Mudança incompatível cria `v<N+1>` (schema, tópico e tipo novos), com publicação nas duas versões durante a convivência.
- Sem Schema Registry por enquanto.

## Alternativas consideradas

- **Avro ou Protobuf + Schema Registry:** mensagens menores e compatibilidade verificada no broker, mas exige mais um componente crítico por região e geração de código. Reavaliar se o volume de algum tópico tornar o tamanho da mensagem relevante.

## Consequências

- Eventos legíveis em qualquer ferramenta e consumíveis pelo frontend e por integrações sem biblioteca específica.
- A compatibilidade depende da disciplina de revisão em `contracts/` e dos testes de contrato, não de um registry.
