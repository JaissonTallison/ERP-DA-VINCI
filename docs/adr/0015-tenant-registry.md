# ADR-0015: Desenho do Tenant Registry

- Estado: Aceito
- Data: 2026-11-03

## Contexto

O Tenant Registry é o primeiro serviço da plataforma e servirá de modelo para os demais. Precisa ser simples, auditável e correto sob concorrência.

## Decisão

1. **Acesso a dados com SQL explícito (`JdbcClient`)** e domínio em `record`s imutáveis, sem anotações de persistência. O ADR-0006 indicava Spring Data JDBC como padrão; para agregados pequenos, SQL explícito deixa o comportamento visível (reserva atômica de vaga, `SKIP LOCKED`, controle otimista) sem mapeamento implícito. Serviços com muitos agregados podem adotar Spring Data JDBC.
2. **Alocação de célula** na célula aberta menos ocupada da região e do isolamento pedidos, com reserva atômica no banco. Sem vaga, devolve 409 `no-cell-capacity`, sinal para a operação abrir nova célula. A criação automática de células fica para a saga de provisionamento.
3. **Silo = célula de capacidade 1**, garantido no domínio e por `CHECK` no banco.
4. **Transições idempotentes**: repetir uma transição já aplicada devolve o estado atual, sem evento e sem nova versão.
5. **Criação idempotente** por `Idempotency-Key`, com impressão digital do corpo, gravada na mesma transação.
6. **Sem Row-Level Security**: os dados são de plataforma, não de tenant (ver `docs/services/TENANT_REGISTRY.md`).
7. **Relay do outbox por polling** (500 ms) dentro do próprio serviço. CDC com Debezium fica para quando a latência ou o volume justificarem, pois é mais um componente para operar.

## Consequências

- O padrão "domínio imutável + JdbcClient + outbox + idempotência + contrato testado" vira a referência para os próximos serviços.
- O polling gera consultas constantes ao banco (barato com o índice parcial `published_at IS NULL`).
