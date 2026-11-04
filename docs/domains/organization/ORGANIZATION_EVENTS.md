# Organization Management — Eventos

> Estado: **proposta para aprovação**. Segue EVENT_DRIVEN_ARCHITECTURE.md, ADR-0008 e ADR-0014. Os JSON Schemas e o AsyncAPI (`contracts/events/organization/v1/`) serão escritos antes do código, na etapa de cada agregado.

## 1. Regras herdadas da fundação

| Regra                             | Aplicação aqui                                                                                                           |
| --------------------------------- | ------------------------------------------------------------------------------------------------------------------------ |
| CloudEvents 1.0 estruturado, JSON | Igual ao Tenant Registry                                                                                                 |
| `type`                            | `br.com.davinci.organization.<agregado>.<fato>.v1`                                                                       |
| `data`                            | **Estado completo** do agregado após o fato + `version` + vigência                                                       |
| Publicação                        | Transactional Outbox, na mesma transação da mudança; relay com `acks=all` e produtor idempotente                         |
| Chave da mensagem                 | Id do agregado (ordem por agregado)                                                                                      |
| Consumidor                        | Deduplica por `id` e descarta `data.version` ≤ aplicada                                                                  |
| Correlação                        | `correlationid` do `X-Correlation-Id` original; reorganização usa o id dela como correlação de todos os eventos que gera |
| Tenant                            | `tenantid` no envelope e em `data`                                                                                       |
| Dead letter                       | `<tópico>-dlt` por consumidor                                                                                            |

## 2. Por que eventos de integração por agregado (e não um por entidade da lista)

Os exemplos do roteiro (`organization.created`, `organization.updated`, `legal_entity.created`…) foram avaliados assim:

- **"organization.\*" não é usado:** não há agregado "Organization" (ver glossário). Para "a estrutura do cliente mudou", o consumidor assina os tópicos dos agregados que lhe interessam.
- **"created" e "updated" separados para tudo geram ruído:** os consumidores querem o estado atual. Os fatos de negócio relevantes (ativado, suspenso, encerrado, movido, mudou de grupo) viram tipos próprios; mudanças de atributo viram `updated`.
- **Um tópico por agregado:** cada consumidor assina só o que usa (Finance: entidades e centros de custo; HR: unidades e estabelecimentos).

## 3. Tópicos e tipos

| Tópico                           | Tipos (`…organization.<agregado>.<fato>.v1`)                                                                                           | Chave                                                                        |
| -------------------------------- | -------------------------------------------------------------------------------------------------------------------------------------- | ---------------------------------------------------------------------------- |
| `organization.economic-group.v1` | `economic-group.created`, `.updated`, `.closed`                                                                                        | id do grupo                                                                  |
| `organization.legal-entity.v1`   | `legal-entity.created`, `.updated`, `.activated`, `.suspended`, `.reactivated`, `.closed`, `.group-changed`, `.tax-identifier-changed` | id da entidade                                                               |
| `organization.establishment.v1`  | `establishment.created`, `.updated`, `.suspended`, `.reactivated`, `.closed`                                                           | id do estabelecimento                                                        |
| `organization.unit.v1`           | `unit.created`, `.updated`, `.deactivated`, `.closed`, `.moved`                                                                        | id da unidade                                                                |
| `organization.reorganization.v1` | `reorganization.scheduled`, `.applied`, `.cancelled`                                                                                   | id da reorganização                                                          |
| `organization.cost-center.v1`    | `cost-center.created`, `.updated`, `.blocked`, `.unblocked`, `.closed`, `.parent-changed`                                              | id do centro de custo                                                        |
| `organization.ownership.v1`      | `ownership.recorded`, `.changed`, `.ended`                                                                                             | id da entidade **possuída** (ordem por entidade, para recalcular o controle) |

Catálogos por tenant (tipos de unidade, regras de aninhamento) e titulares externos **não** geram eventos de integração nesta fase: nenhum consumidor precisa deles. A participação societária leva o nome e o país do titular externo, sem identificador pessoal.

## 4. Exemplos de `data`

`legal-entity.activated`:

```json
{
  "legalEntityId": "…",
  "tenantId": "…",
  "code": "ACME-IND",
  "legalName": "Acme Indústria Ltda",
  "country": "BR",
  "subdivision": "BR-SP",
  "functionalCurrency": "BRL",
  "timezone": "America/Sao_Paulo",
  "defaultLocale": "pt-BR",
  "fiscalYearStartMonth": 1,
  "status": "ACTIVE",
  "effectiveOn": "2026-11-01",
  "economicGroupId": "…",
  "taxIdentifiers": [
    {
      "scheme": "BR_CNPJ",
      "value": "12345678000195",
      "primary": true,
      "validFrom": "2026-11-01",
      "validTo": null
    }
  ],
  "version": 3
}
```

`unit.moved` (cada unidade movida numa reorganização gera o seu):

```json
{
  "unitId": "…",
  "tenantId": "…",
  "structureId": "…",
  "structureCode": "GERENCIAL",
  "previousParentUnitId": "…",
  "parentUnitId": "…",
  "effectiveOn": "2027-01-01",
  "reorganizationId": "…",
  "version": 7
}
```

## 5. Mudanças com data futura

Eventos são publicados **quando a mudança é gravada**, com a data de vigência em `effectiveOn`/`validFrom`. Consumidores que precisam do estado "de hoje" aplicam por data. Para quem prefere não lidar com vigência, a reorganização agendada publica `reorganization.applied` quando a data chega (agendador do próprio serviço, idempotente).

## 6. Consumo pelo Organization

| Tópico                      | Uso                                                                                            |
| --------------------------- | ---------------------------------------------------------------------------------------------- |
| `tenant-registry.tenant.v1` | Projeção do tenant: só `ACTIVE` aceita escrita. Mesmo padrão do Identity (versão, dead letter) |

## 7. Consumidores esperados

| Contexto                     | Tópicos                                              | Para quê                                                                                                 |
| ---------------------------- | ---------------------------------------------------- | -------------------------------------------------------------------------------------------------------- |
| Accounting, Finance          | legal-entity, cost-center, economic-group, ownership | Plano de entidades, moeda funcional, calendário fiscal, centros de lançamento, perímetro de consolidação |
| Tax                          | legal-entity, establishment                          | Registros fiscais e endereços por jurisdição                                                             |
| HR, Payroll                  | unit, establishment, legal-entity                    | Lotação, local de trabalho, empregador                                                                   |
| Purchasing, Sales, Inventory | legal-entity, establishment                          | Quem compra, vende e armazena                                                                            |
| Governance                   | todos                                                | Escopos de permissão (entidade, filial, subárvore) e auditoria                                           |
| Analytics                    | todos                                                | Dimensões organizacionais com vigência                                                                   |
