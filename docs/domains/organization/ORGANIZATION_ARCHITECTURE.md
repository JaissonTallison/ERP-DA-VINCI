# Organization Management — Arquitetura

> Estado: **proposta para aprovação**. Decisões: ADR-0017 (tenant × grupo econômico), ADR-0018 (um serviço modular).

## 1. Diagnóstico da fundação existente

| Item da fundação             | Situação                                                                                                      | Impacto no Organization                                                                                                         |
| ---------------------------- | ------------------------------------------------------------------------------------------------------------- | ------------------------------------------------------------------------------------------------------------------------------- |
| Tenant Registry              | Implementado; publica `tenant-registry.tenant.v1` com estado completo                                         | Organization consome para projeção local do tenant (ativo, suspenso)                                                            |
| Identity + login BFF         | Implementado; token traz `tenant_id` no claim `organization`                                                  | Fonte do tenant nas APIs. Falta: audiência `organization` no token e repasse do token pelo BFF                                  |
| Padrões de serviço           | `JdbcClient`, outbox, idempotência, Problem Details, contrato testado, perfil `local`, falha sem configuração | Reutilizados sem mudança                                                                                                        |
| Multitenancy (`pooled`)      | Diretriz de RLS documentada, **nunca implementada**                                                           | Organization será o primeiro serviço com RLS: precisa de papel de banco sem privilégio de dono (ver ORGANIZATION_DATA_MODEL.md) |
| Autorização fina (RBAC/ABAC) | Só papéis `tenant-admin`/`tenant-user` no token; motor em aberto (ADR-0009)                                   | Organization precisa de escopos por entidade e unidade: decisão no ADR-0021                                                     |
| API Gateway                  | **Não existe**. Hoje: navegador → BFF (Next.js) → serviços                                                    | Organization é chamado pelo BFF; borda e rate limiting entram com a infraestrutura de produção (ADR-0013)                       |
| Contratos                    | OpenAPI e AsyncAPI/JSON Schema em `contracts/`, validados em teste                                            | Mesmo padrão                                                                                                                    |
| Auditoria imutável           | Diretriz documentada, não implementada                                                                        | Organization implementa trilha própria append-only até existir a de Governance                                                  |
| Moeda, fuso, idioma          | Convenções em DATA_ARCHITECTURE (dinheiro em `numeric`, `timestamptz` UTC)                                    | Organization é a fonte de moeda funcional, fuso e calendário fiscal por entidade                                                |

Conclusão: a fundação cobre infraestrutura, identidade e padrões. As lacunas que o Organization expõe são **RLS** (primeiro uso), **autorização por escopo** e **repasse de token pelo BFF**, todas tratadas no plano de implementação.

## 2. Limites do contexto

```
                         ┌───────────────────────────┐
  Tenant Registry ─────▶ │  ORGANIZATION MANAGEMENT  │ ─────▶ Finance, Accounting, Tax,
  (tenant ativo/suspenso)│                           │        HR, Payroll, Purchasing,
                         │  grupos · entidades       │        Sales, Analytics, Governance
  Identity ────────────▶ │  estabelecimentos         │        (eventos: estrutura legal,
  (token: tenant, sub)   │  unidades · estruturas    │         unidades, centros de custo)
                         │  centros de custo         │
  Governance (futuro) ◀─▶│  participações            │
  (permissões, SoD)      └───────────────────────────┘
```

| Pertence ao Organization                                                                                             | Não pertence (dono)                                                            |
| -------------------------------------------------------------------------------------------------------------------- | ------------------------------------------------------------------------------ |
| Grupos, entidades jurídicas, estabelecimentos, unidades, estruturas, reorganizações, centros de custo, participações | Tenant, região, célula, isolamento, plano, licenciamento (**Tenant Registry**) |
| Moeda funcional, fuso, calendário fiscal e idioma padrão de cada entidade                                            | Usuários, autenticação, MFA (**Identity**)                                     |
| Regras estruturais (sem ciclo, soma ≤ 100%)                                                                          | Papéis, políticas, segregação de funções (**Governance**)                      |
| Identificadores fiscais e endereço legal                                                                             | Cálculo de tributos e obrigações (**Tax**)                                     |
| Cadastro do centro de custo                                                                                          | Lançamentos, rateios, orçamento (**Accounting/Finance**)                       |
| Unidade organizacional                                                                                               | Colaborador, cargo, lotação (**HR**): HR referencia a unidade pelo id          |

### Relação com o Tenant Registry (sem dependência excessiva)

1. **Só eventos, nunca chamada síncrona.** O Organization consome `tenant-registry.tenant.v1` e mantém uma projeção mínima: `tenant_id`, status, versão. É o mesmo padrão do Identity.
2. **Tenant suspenso = somente leitura.** Escritas retornam 409 `tenant-not-active`. Leituras continuam para o próprio tenant (o login já está bloqueado pelo Identity; isso protege integrações de serviço).
3. **O Tenant Registry não conhece grupos nem entidades**, e o Organization não aloca células nem muda isolamento.
4. **Registro de tenant não cria estrutura automaticamente.** A estrutura é cadastrada no onboarding pelo cliente ou pela implantação: criá-la vazia geraria dados fictícios.

## 3. Proposta de serviço (ADR-0018)

**Um único microsserviço (`organization`), modular por dentro.**

| Alternativa                        | Avaliação                                                                                                                                                                                                                                                                            |
| ---------------------------------- | ------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------ |
| **Um serviço modular** (escolhida) | Reorganizações mexem em unidades, vínculos e centros de custo **na mesma transação**; encerrar uma entidade exige checar estabelecimentos, unidades e centros de custo **de forma consistente**. Mesmo dono, mesma cadência de mudança, volume moderado (milhares de nós por tenant) |
| Um serviço por agregado            | Transformaria invariantes locais em sagas distribuídas (encerramento, reorganização) sem ganho de escala: o volume é pequeno e a leitura domina                                                                                                                                      |
| Dois serviços (legal × gerencial)  | Separação plausível no futuro, mas centros de custo e unidades cruzam as duas metades. Reavaliar se equipes ou cadência divergirem                                                                                                                                                   |

Módulos internos, com dependência só no sentido indicado, verificada por teste de arquitetura (ArchUnit) a partir da etapa 2:

```
corporate/   grupo econômico, entidade jurídica, estabelecimento, participação, titular externo
structure/   unidade, tipo de unidade, estrutura, vínculos, reorganização      ──▶ corporate
costing/     centro de custo e sua hierarquia                                  ──▶ corporate, structure
shared/      objetos de valor (vigência, endereço, identificador fiscal…), projeção do tenant, outbox, auditoria
```

| Item               | Valor                                                   |
| ------------------ | ------------------------------------------------------- |
| Plano              | Data Plane (uma instância por célula)                   |
| Porta local        | 5632 (faixa 5630–5699, verificada livre)                |
| Banco              | `organization` (PostgreSQL), dono exclusivo             |
| Tópicos produzidos | `organization.*.v1` (ver ORGANIZATION_EVENTS.md)        |
| Tópico consumido   | `tenant-registry.tenant.v1`                             |
| Chamado por        | BFF (usuário) e, no futuro, serviços (token de serviço) |

## 4. Consistência

| Operação                             | Fronteira transacional                                                                                       |
| ------------------------------------ | ------------------------------------------------------------------------------------------------------------ |
| Criar ou alterar um agregado         | Uma transação: agregado + outbox + auditoria                                                                 |
| Mover unidade, aplicar reorganização | Uma transação com trava por estrutura (`pg_advisory_xact_lock`), para a checagem de ciclo não sofrer corrida |
| Registrar participação               | Uma transação com trava por tenant no grafo societário                                                       |
| Encerrar entidade jurídica           | Uma transação: checa dependências na data e muda o status                                                    |
| Propagar a outros contextos          | Assíncrono, por eventos (outbox), entrega pelo menos uma vez                                                 |

## 5. Leituras temporais

Toda leitura aceita `asOf` (data). Sem `asOf`, vale a data de hoje no fuso da entidade (ou do grupo, para visões de grupo). Mudanças futuras ficam gravadas com `validFrom` futuro e passam a valer sozinhas na data, sem job de aplicação. A exceção é a reorganização agendada, cujo status muda para `APPLIED` por um agendador (os vínculos já estão gravados com a vigência).

## 6. Escala esperada

| Dimensão                 | Ordem de grandeza por tenant                                                                                     |
| ------------------------ | ---------------------------------------------------------------------------------------------------------------- |
| Entidades jurídicas      | até ~2.000 (grupos com muitas SPEs)                                                                              |
| Estabelecimentos         | até ~50.000 (redes varejistas)                                                                                   |
| Unidades organizacionais | até ~20.000                                                                                                      |
| Centros de custo         | até ~50.000                                                                                                      |
| Leituras × escritas      | leitura domina (cada lançamento de outros contextos valida entidade e centro de custo, via projeção local deles) |

Outros contextos **não** chamam o Organization a cada transação: mantêm projeções locais a partir dos eventos, como o Identity faz com o tenant.
