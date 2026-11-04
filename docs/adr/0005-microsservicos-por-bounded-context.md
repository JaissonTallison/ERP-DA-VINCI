# ADR-0005: Microsserviços por bounded context, criados sob demanda

- Estado: Aceito
- Data: 2026-11-03

## Contexto

Foram definidos 20 bounded contexts (ver `docs/architecture/MICROSERVICES_CATALOG.md`). Criar todos os serviços de uma vez geraria código vazio, contratos especulativos e custo operacional sem valor.

## Decisão

- Cada bounded context se torna **um serviço** com dados, contratos, testes, migrações e pipeline próprios.
- O serviço é criado **somente quando o contexto entra em desenvolvimento**, com ADR ou registro de escopo.
- Dentro do serviço, arquitetura em camadas por porta/adaptador (domínio sem dependência de framework).
- Proibido: banco compartilhado entre serviços, biblioteca com regra de negócio compartilhada, chamada síncrona em ciclo.

## Consequências

- `services/` começa vazio na Fase 1, de propósito.
- Separar um contexto em dois serviços (ou unir) exige novo ADR com justificativa de escala, equipe ou conformidade.
