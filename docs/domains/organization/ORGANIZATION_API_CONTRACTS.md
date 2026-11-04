# Organization Management — Contratos de API

> Estado: **proposta para aprovação**. O OpenAPI 3.1 (`contracts/http/organization/v1/openapi.yaml`) será escrito antes do código na etapa 2 (contrato primeiro) e validado em teste, como no Tenant Registry.

## 1. Convenções (válidas para todos os endpoints)

| Tema                    | Regra                                                                                                                                 |
| ----------------------- | ------------------------------------------------------------------------------------------------------------------------------------- |
| Base                    | `/v1`, JSON, nomes em inglês `camelCase`                                                                                              |
| Tenant                  | **Sempre do token** (claim `organization` → `tenant_id`). Nunca no caminho, no corpo ou em parâmetro                                  |
| Recurso de outro tenant | **404**, nunca 403: não confirma a existência (proteção contra IDOR e enumeração)                                                     |
| Criação                 | `POST` com `Idempotency-Key` obrigatório; repetição devolve o mesmo resultado; mesma chave com outro corpo dá 409                     |
| Atualização             | `PATCH` (JSON Merge Patch) com `If-Match: "<version>"`; versão divergente dá 412                                                      |
| Transições de status    | `POST /{id}/activate`, `/suspend`, `/reactivate`, `/close` com corpo `{ "effectiveOn": "AAAA-MM-DD", "reason": "..." }`; idempotentes |
| Leitura temporal        | `asOf=AAAA-MM-DD`. Padrão: hoje no fuso da entidade (ou do grupo)                                                                     |
| Paginação               | Cursor opaco (`cursor`, `limit` ≤ 200), ordenação estável por `code`                                                                  |
| Erros                   | RFC 9457 com `code` estável (lista na seção 5)                                                                                        |
| Correlação              | `X-Correlation-Id` propagado para logs, auditoria e eventos                                                                           |
| Datas                   | Vigências em `date`; instantes em ISO 8601 UTC                                                                                        |
| Idioma                  | `Accept-Language` escolhe o texto de `LocalizedText`; a resposta inclui todas as traduções em `nameTranslations`                      |

## 2. Recursos

### Grupos econômicos

| Método e rota                                       | Uso                                      |
| --------------------------------------------------- | ---------------------------------------- |
| `POST /v1/economic-groups`                          | Criar                                    |
| `GET /v1/economic-groups`                           | Listar (`status`, `cursor`, `limit`)     |
| `GET /v1/economic-groups/{id}`                      | Detalhe                                  |
| `PATCH /v1/economic-groups/{id}`                    | Alterar nome, moeda e fuso de referência |
| `POST /v1/economic-groups/{id}/close`               | Encerrar                                 |
| `GET /v1/economic-groups/{id}/legal-entities?asOf=` | Entidades do grupo na data               |

### Entidades jurídicas

| Método e rota                                                                   | Uso                                                            |
| ------------------------------------------------------------------------------- | -------------------------------------------------------------- |
| `POST /v1/legal-entities`                                                       | Criar (status `PLANNED` ou `ACTIVE`)                           |
| `GET /v1/legal-entities`                                                        | Listar (`country`, `status`, `economicGroupId`, `asOf`, `q`)   |
| `GET /v1/legal-entities/{id}`                                                   | Detalhe, com identificadores fiscais e grupo vigentes          |
| `PATCH /v1/legal-entities/{id}`                                                 | Alterar atributos                                              |
| `POST /v1/legal-entities/{id}/activate` · `/suspend` · `/reactivate` · `/close` | Ciclo de vida                                                  |
| `PUT /v1/legal-entities/{id}/economic-group`                                    | Mudar de grupo a partir de `effectiveOn`                       |
| `POST /v1/legal-entities/{id}/tax-identifiers` · `POST …/{taxId}/end`           | Incluir e encerrar identificador fiscal                        |
| `GET /v1/legal-entities/{id}/ownership?direction=owners\|holdings&asOf=`        | Sócios diretos ou participações diretas                        |
| `GET /v1/legal-entities/{id}/ownership-chart?asOf=`                             | Cadeia societária com participação efetiva e controlador final |

### Estabelecimentos

| Método e rota                                                     | Uso                                                   |
| ----------------------------------------------------------------- | ----------------------------------------------------- |
| `POST /v1/establishments`                                         | Criar (com `legalEntityId`)                           |
| `GET /v1/establishments`                                          | Listar (`legalEntityId`, `type`, `country`, `status`) |
| `GET /v1/establishments/{id}` · `PATCH`                           | Detalhe e alteração                                   |
| `POST /v1/establishments/{id}/suspend` · `/reactivate` · `/close` | Ciclo de vida                                         |

### Unidades, tipos e estruturas

| Método e rota                                                     | Uso                                                           |
| ----------------------------------------------------------------- | ------------------------------------------------------------- |
| `POST /v1/unit-types` · `GET` · `PUT /v1/unit-types/{id}/nesting` | Catálogo de tipos e regras de aninhamento                     |
| `POST /v1/organizational-units` · `GET` · `GET /{id}` · `PATCH`   | Unidades                                                      |
| `POST /v1/organizational-units/{id}/deactivate` · `/close`        | Ciclo de vida                                                 |
| `POST /v1/structures` · `GET` · `GET /{id}`                       | Estruturas (gerencial, funcional…)                            |
| `GET /v1/structures/{id}/roots?asOf=`                             | Raízes                                                        |
| `GET /v1/structures/{id}/units/{unitId}/children?asOf=`           | Filhos diretos (navegação por nível)                          |
| `GET /v1/structures/{id}/units/{unitId}/subtree?asOf=&maxDepth=`  | Subárvore (lista plana com `parentId` e `depth`)              |
| `GET /v1/structures/{id}/units/{unitId}/ancestors?asOf=`          | Caminho até a raiz                                            |
| `POST /v1/structures/{id}/moves`                                  | Movimento simples e imediato (uma unidade, com `effectiveOn`) |

### Reorganizações

| Método e rota                                                               | Uso                                         |
| --------------------------------------------------------------------------- | ------------------------------------------- |
| `POST /v1/reorganizations`                                                  | Criar rascunho (estrutura, data, descrição) |
| `PUT /v1/reorganizations/{id}/steps`                                        | Definir os passos                           |
| `POST /v1/reorganizations/{id}/validate`                                    | Simular: devolve conflitos sem gravar       |
| `POST /v1/reorganizations/{id}/submit` · `/approve` · `/reject` · `/cancel` | Fluxo; aprovador ≠ submissor                |
| `GET /v1/reorganizations/{id}/preview`                                      | Árvore resultante na data efetiva           |

### Centros de custo

| Método e rota                                              | Uso                                            |
| ---------------------------------------------------------- | ---------------------------------------------- |
| `POST /v1/cost-centers` · `GET` · `GET /{id}` · `PATCH`    | Cadastro (`legalEntityId`, `kind`, `category`) |
| `POST /v1/cost-centers/{id}/block` · `/unblock` · `/close` | Ciclo de vida                                  |
| `PUT /v1/cost-centers/{id}/parent`                         | Mudar o pai a partir de `effectiveOn`          |
| `GET /v1/legal-entities/{id}/cost-center-tree?asOf=`       | Hierarquia de totalização                      |

### Participações societárias e titulares externos

| Método e rota                                                               | Uso                                                       |
| --------------------------------------------------------------------------- | --------------------------------------------------------- |
| `POST /v1/external-parties` · `GET` · `GET /{id}`                           | Titulares de fora do tenant                               |
| `POST /v1/ownership-interests`                                              | Registrar participação (vigência a partir de `validFrom`) |
| `POST /v1/ownership-interests/{id}/end`                                     | Encerrar em `effectiveOn`                                 |
| `GET /v1/ownership-interests?ownedLegalEntityId=&ownerLegalEntityId=&asOf=` | Consultar                                                 |

### Histórico

| Método e rota                                           | Uso                                                |
| ------------------------------------------------------- | -------------------------------------------------- |
| `GET /v1/history?aggregateType=&aggregateId=&from=&to=` | Trilha de auditoria (quem, quando, antes e depois) |
| `GET /v1/structures/{id}/changes?from=&to=`             | Movimentos de uma estrutura num período            |

## 3. Exemplos

Criar entidade jurídica:

```http
POST /v1/legal-entities
Idempotency-Key: 6f1c…
X-Correlation-Id: onboarding-acme-01

{
  "code": "ACME-IND",
  "legalName": "Acme Indústria Ltda",
  "tradeName": "Acme",
  "country": "BR",
  "subdivision": "BR-SP",
  "legalForm": "LTDA",
  "functionalCurrency": "BRL",
  "timezone": "America/Sao_Paulo",
  "defaultLocale": "pt-BR",
  "fiscalYearStartMonth": 1,
  "registeredAddress": {
    "country": "BR", "addressLines": ["Av. Paulista, 1000", "Conj. 101"],
    "locality": "São Paulo", "administrativeArea": "SP", "postalCode": "01310-100"
  },
  "taxIdentifiers": [{ "scheme": "BR_CNPJ", "value": "12.345.678/0001-95", "primary": true }],
  "economicGroupId": "0b6f…",
  "status": "ACTIVE",
  "effectiveOn": "2026-11-01"
}
```

Resposta `201` com `Location`, `ETag: "0"` e a representação completa: identificador fiscal normalizado (`12345678000195`), `economicGroup` vigente e `version`.

Subárvore:

```http
GET /v1/structures/{id}/units/{unitId}/subtree?asOf=2026-11-01&maxDepth=2
```

```json
{
  "asOf": "2026-11-01",
  "items": [
    {
      "id": "…",
      "code": "PRES",
      "name": "Presidência",
      "unitType": "DIRETORIA",
      "parentId": null,
      "depth": 0
    },
    {
      "id": "…",
      "code": "DIR-IND",
      "name": "Diretoria Industrial",
      "unitType": "DIRETORIA",
      "parentId": "…",
      "depth": 1
    }
  ],
  "truncated": true
}
```

## 4. Validação de entrada

- Corpos com `additionalProperties: false` no contrato: campos desconhecidos dão 400 (contra _mass assignment_).
- `id`, `tenantId`, `status`, `version` e datas de auditoria **nunca** são aceitos no corpo de criação ou alteração.
- Identificador fiscal validado pelo esquema (ex.: dígitos do CNPJ); esquema desconhecido dá 400.
- Códigos ISO (país, moeda, fuso, idioma) validados contra listas oficiais.

## 5. Códigos de erro (campo `code`)

`not-found`, `tenant-not-active`, `invalid-field`, `invalid-tax-identifier`, `duplicate-code`, `duplicate-tax-identifier`, `version-mismatch`, `idempotency-key-reused`, `invalid-transition`, `has-active-dependents`, `hierarchy-cycle`, `overlapping-period`, `nesting-not-allowed`, `ownership-exceeds-100`, `ownership-cycle`, `same-approver`, `forbidden-scope`.

## 6. Consumidores previstos

| Consumidor                                      | Como usa                                                                     |
| ----------------------------------------------- | ---------------------------------------------------------------------------- |
| BFF (telas de cadastro)                         | API, com o token do usuário                                                  |
| Finance, Accounting, Tax, HR, Purchasing, Sales | **Eventos** (projeção local). API só para consulta eventual ou reconciliação |
| Governance                                      | Eventos (escopos) e API de ancestrais para resolver herança de permissões    |
| Analytics                                       | Eventos                                                                      |
