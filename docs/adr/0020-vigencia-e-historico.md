# ADR-0020: Vigência por data e histórico organizacional

- Estado: **Proposto**
- Data: 2026-11-03

## Contexto

Estruturas e relacionamentos mudam com data marcada (reorganização a partir de 1º de janeiro, aquisição concluída em uma data, fechamento de filial). É preciso saber o que vale hoje, o que valia antes e o que valerá.

## Decisão

1. **Vigência em datas de calendário, intervalo semiaberto `[validFrom, validTo)`**; `validTo` vazio = sem fim. Aplica-se a pertença ao grupo, vínculos de estrutura, hierarquia de centros de custo, participações, identificadores fiscais e à existência de unidades e centros de custo.
2. **Datas são interpretadas no fuso da entidade jurídica**; visões de grupo usam o fuso de referência do grupo. Instantes técnicos (`created_at`, auditoria) ficam em UTC.
3. **Leituras aceitam `asOf`**; o padrão é "hoje" no fuso aplicável. Mudanças futuras ficam gravadas e passam a valer sozinhas.
4. **Histórico = vigência (tempo de negócio) + auditoria append-only (tempo de registro)**. Não é bitemporal completo: correções retroativas ficam visíveis na auditoria, não em uma segunda linha do tempo consultável.
5. **Atributos simples** (nome, endereço) na primeira entrega: alteração imediata, registrada na auditoria. Atributos com vigência própria (ex.: razão social a partir de uma data) entram quando houver demanda (tabela de histórico por atributo).

## Consequências

- Consumidores recebem eventos com `effectiveOn` e precisam respeitar a data.
- Consultas "como era em D" são exatas para relações; para atributos simples, dependem da auditoria até a etapa de histórico por atributo.
