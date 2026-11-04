# Bounded contexts

> Estado: **aprovado como mapa-alvo**. Serviços de plataforma implementados: Tenant Registry e Identity. Nenhum serviço de negócio ainda.

## Serviços implementados

| Serviço           | Plano         | Porta local | Situação                                                   | Documentação                                         |
| ----------------- | ------------- | ----------- | ---------------------------------------------------------- | ---------------------------------------------------- |
| `tenant-registry` | Control Plane | 5630        | Em uso                                                     | [TENANT_REGISTRY.md](../services/TENANT_REGISTRY.md) |
| `identity`        | Data Plane    | 5631        | Em uso (sincronização de organizações; API pública futura) | [IDENTITY.md](../services/IDENTITY.md)               |
| `apps/web` (BFF)  | Data Plane    | 5620        | Em uso (login e sessão)                                    | [IDENTITY.md](../services/IDENTITY.md)               |

Os demais contextos abaixo são o mapa-alvo: cada um vira serviço só quando entrar em desenvolvimento.

## Princípios

1. **Um bounded context = um serviço implantável**, dono exclusivo de seus dados, contratos, testes, migrações e pipeline (ADR-0005).
2. Um serviço **só é criado quando o contexto entra em desenvolvimento** — sem serviços vazios.
3. Nenhum serviço lê o banco de outro. Integração por **eventos** (padrão) ou **API síncrona** (consulta pontual, com timeout e circuit breaker).
4. Dependência síncrona só no sentido _upstream_ do mapa de contexto; nunca em ciclo.

## Catálogo

Legenda de plano: **CP** = Global Control Plane · **DP** = Regional Data Plane (célula).

### Plataforma

| Contexto            | Plano | Responsabilidade                                                                                             | Fora do escopo                                   |
| ------------------- | ----- | ------------------------------------------------------------------------------------------------------------ | ------------------------------------------------ |
| **Tenant Registry** | CP    | Tenants, região, célula, isolamento, ciclo de vida, plano contratado                                         | Usuários, empresas do cliente                    |
| **Identity**        | DP    | Usuários, autenticação federada (OIDC/SAML), MFA, sessões, credenciais de serviço                            | Permissões de negócio por documento (Governance) |
| **Organization**    | DP    | Estrutura do cliente: grupo econômico, entidades legais, filiais, centros de custo, unidades organizacionais | Cadastro de funcionários (HR)                    |
| **Governance**      | DP    | Papéis, políticas RBAC/ABAC, segregação de funções, trilha de auditoria, workflows de aprovação              | Autenticação (Identity)                          |
| **Integration**     | DP    | Conectores com sistemas externos (bancos, governos, marketplaces, EDI), webhooks, importação/exportação      | Regras de negócio dos domínios                   |
| **Analytics**       | DP    | Modelos analíticos em ClickHouse/Iceberg alimentados por eventos; relatórios e indicadores                   | Fonte da verdade transacional                    |

### Finanças e conformidade

| Contexto       | Responsabilidade                                                                                | Fora do escopo                     |
| -------------- | ----------------------------------------------------------------------------------------------- | ---------------------------------- |
| **Finance**    | Contas a pagar/receber, tesouraria, conciliação bancária, fluxo de caixa                        | Lançamentos contábeis (Accounting) |
| **Accounting** | Plano de contas, razão geral, lançamentos, fechamento, consolidação multi-entidade e multimoeda | Cálculo de tributos                |
| **Tax**        | Motor de tributos por jurisdição, documentos fiscais, obrigações acessórias                     | Pagamento (Finance)                |

### Cadeia comercial

| Contexto       | Responsabilidade                                                                      | Fora do escopo            |
| -------------- | ------------------------------------------------------------------------------------- | ------------------------- |
| **CRM**        | Contas, contatos, leads, oportunidades, atividades                                    | Pedido confirmado (Sales) |
| **Commercial** | Catálogo comercial, tabelas de preço, condições, descontos, contratos de fornecimento | Execução do pedido        |
| **Sales**      | Cotação, pedido de venda, faturamento (order-to-cash)                                 | Recebimento (Finance)     |
| **Purchasing** | Requisição, cotação, pedido de compra, recebimento contratual (procure-to-pay)        | Pagamento (Finance)       |

### Operações

| Contexto          | Responsabilidade                                                        | Fora do escopo            |
| ----------------- | ----------------------------------------------------------------------- | ------------------------- |
| **Inventory**     | Itens, saldos, movimentações, lotes, séries, custo de estoque           | Transporte (Logistics)    |
| **Logistics**     | Armazenagem (WMS), expedição, transporte, rastreamento                  | Saldo contábil de estoque |
| **Manufacturing** | Estruturas (BOM), roteiros, ordens de produção, apontamentos, MRP       | Inspeção (Quality)        |
| **Quality**       | Inspeções, não conformidades, ações corretivas, certificações           | Produção                  |
| **Projects**      | Projetos, WBS, orçamento, apontamento de horas, faturamento por projeto | Folha (Payroll)           |

### Pessoas

| Contexto    | Responsabilidade                                                   | Fora do escopo               |
| ----------- | ------------------------------------------------------------------ | ---------------------------- |
| **HR**      | Colaboradores, cargos, admissão, desligamento, férias, avaliação   | Cálculo de pagamento         |
| **Payroll** | Cálculo de folha por legislação, encargos, obrigações trabalhistas | Cadastro do colaborador (HR) |

## Justificativas de separação que costumam ser questionadas

- **Finance × Accounting:** ciclos e usuários diferentes (operação diária × fechamento); Accounting consome eventos de todos os domínios e precisa de modelo próprio, imutável.
- **Commercial × Sales × CRM:** CRM é relacionamento pré-venda; Commercial é política de preço (mudança rara, leitura intensa); Sales é transação. Volumes, SLOs e donos diferentes.
- **HR × Payroll:** Payroll é fortemente dependente de legislação por país e tem requisitos de privacidade e RPO mais rígidos. Isolar reduz o escopo regulado.
- **Inventory × Logistics:** saldo e custo (contábil) separados de execução física (WMS/TMS), que pode ser substituída por sistema terceiro via Integration.

## Mapa de contexto (dependências permitidas)

```
Tenant Registry (CP) ──eventos──▶ todos os data planes
Identity ◀── Governance ◀── todos os domínios (autorização)
Organization ──▶ todos os domínios (estrutura legal e centros de custo)

CRM ─▶ Sales ◀─ Commercial          Purchasing ─▶ Inventory ◀─ Sales
Sales ─▶ Tax     Purchasing ─▶ Tax    Inventory ◀─▶ Logistics (eventos)
Sales, Purchasing, Payroll, Projects ─▶ Finance ─▶ Accounting
Tax ─▶ Accounting                      Manufacturing ─▶ Inventory, Quality
HR ─▶ Payroll                          todos ─eventos─▶ Analytics
```

Setas = direção do fluxo de dados (upstream → downstream). Ciclos são proibidos; o lado downstream consome eventos e mantém projeções locais.

## Ordem sugerida de construção

1. **Fundação de plataforma:** Tenant Registry, Identity, Organization, Governance (sem eles nenhum domínio é seguro).
2. **Núcleo financeiro:** Accounting, Finance, Tax.
3. **Order-to-cash e procure-to-pay:** Commercial, Sales, Purchasing, Inventory.
4. Demais contextos conforme demanda comercial.
