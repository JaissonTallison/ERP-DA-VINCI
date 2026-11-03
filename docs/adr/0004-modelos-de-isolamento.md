# ADR-0004: Modelos de isolamento pooled, bridge e silo

- Estado: Aceito
- Data: 2026-11-03

## Contexto

O público vai de médias empresas (custo é decisivo) a multinacionais reguladas (isolamento é decisivo). Manter código diferente por modelo seria insustentável.

## Decisão

- **pooled:** célula e bancos compartilhados. Toda tabela de negócio tem `tenant_id NOT NULL`; PostgreSQL **Row-Level Security** habilitada com política por `current_setting('app.tenant_id')`; a camada de acesso define o tenant a cada transação e testes automatizados provam que não há vazamento entre tenants.
- **bridge:** serviços compartilhados; banco/cluster dedicado por tenant, selecionado pelo roteamento de _datasource_.
- **silo:** célula inteira dedicada (inclusive em conta de nuvem do cliente).
- Mesmo artefato nos três modelos; diferença apenas de configuração. O modelo é atributo do tenant no Tenant Registry e parte do `CellId`.
- Migração entre modelos é processo suportado (pooled → bridge → silo).

## Consequências

- `tenant_id` é obrigatório em todo schema desde a primeira tabela, inclusive em bridge/silo (permite migração e auditoria uniformes).
- Caches, tópicos e índices de busca carregam o tenant na chave/partição.
- Teste de isolamento entre tenants é critério de aceite de todo serviço.
