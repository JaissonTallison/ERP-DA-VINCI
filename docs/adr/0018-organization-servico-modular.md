# ADR-0018: Organization Management como um único serviço modular

- Estado: **Proposto**
- Data: 2026-11-03

## Contexto

O contexto tem dez conceitos (grupo, entidade, estabelecimento, unidades, estruturas, centros de custo, participações…) com invariantes que cruzam conceitos: encerrar uma entidade depende de estabelecimentos, unidades e centros de custo; uma reorganização mexe em unidades, vínculos e centros de custo de uma vez.

## Decisão

- **Um serviço `organization`** (Data Plane, porta local 5632, banco `organization`), com módulos internos `corporate`, `structure`, `costing` e `shared`, e direção de dependência verificada por teste (ArchUnit).
- Invariantes entre conceitos são garantidos em **transação local**, não por sagas.
- Outros contextos consomem **eventos** e mantêm projeções locais; não chamam o serviço a cada transação.

## Alternativas consideradas

- **Um serviço por agregado:** sagas para invariantes simples, mais operação, sem ganho de escala (volume moderado, leitura dominante).
- **Dois serviços (estrutura legal × gerencial):** possível no futuro; hoje centros de custo e unidades cruzam as duas metades.

## Consequências

- Reavaliar a divisão se equipes, cadência de mudança ou carga divergirem entre os módulos. Os limites dos módulos já preparam essa separação.
