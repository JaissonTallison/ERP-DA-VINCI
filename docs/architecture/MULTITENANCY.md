# Multitenancy

> Estado: **aprovado**. Decisões: ADR-0003 (células), ADR-0004 (isolamento), ADR-0016 (identidade). Implementado: Tenant Registry e Identity.

## Conceitos

| Termo                                       | Significado                                                                                             | Onde vive                              |
| ------------------------------------------- | ------------------------------------------------------------------------------------------------------- | -------------------------------------- |
| **Tenant**                                  | Cliente contratante (empresa ou grupo econômico). Unidade de cobrança, isolamento e residência de dados | Tenant Registry (Control Plane)        |
| **Região** (`RegionCode`, ex. `br-1`)       | Jurisdição de residência dos dados do tenant. Definida no registro; muda só por migração formal         | Atributo do tenant                     |
| **Célula** (`CellId`, ex. `br-1-pooled-01`) | Implantação completa que atende um conjunto limitado de tenants                                         | Catálogo de células do Tenant Registry |
| **Modelo de isolamento**                    | `pooled`, `bridge` ou `silo`                                                                            | Atributo do tenant, parte do `CellId`  |
| **Organização**                             | Representação do tenant no IdP regional (Keycloak Organizations)                                        | Realm `davinci` da região              |
| **Entidade legal, filial, centro de custo** | Estrutura interna do cliente                                                                            | Contexto Organization (futuro)         |

Os identificadores técnicos (`TenantId`, `RegionCode`, `CellId`, `IsolationModel`) estão em `libs/java/platform-kernel` e são os mesmos em todos os serviços.

## Modelos de isolamento

| Modelo   | Compartilhado             | Isolamento dos dados                                                  | Indicado para                                     |
| -------- | ------------------------- | --------------------------------------------------------------------- | ------------------------------------------------- |
| `pooled` | Célula, serviços e bancos | `tenant_id` em toda tabela + Row-Level Security no PostgreSQL         | Médias empresas, SaaS padrão                      |
| `bridge` | Serviços da célula        | Banco (ou cluster) dedicado por tenant                                | Grandes empresas com exigência de dados separados |
| `silo`   | Nada                      | Célula inteira dedicada (capacidade 1), inclusive em conta do cliente | Multinacionais e setores regulados                |

O mesmo artefato roda nos três modelos: a diferença é só configuração de implantação.

## Ciclo de vida do tenant

```
registrar ──▶ PROVISIONING ──ativar──▶ ACTIVE ◀──reativar── SUSPENDED
                                          └──────suspender──────▶┘
```

1. **Registro** (Tenant Registry): aloca a célula aberta menos ocupada da região e do isolamento pedidos e publica `tenant.registered`.
2. **Provisionamento:** cada serviço prepara o tenant a partir do evento. Hoje o Identity cria a organização desabilitada; a saga completa vem depois.
3. **Ativação:** a organização é habilitada e os usuários passam a entrar.
4. **Suspensão:** a organização é desabilitada em segundos. Novos logins são recusados e as sessões ativas caem na próxima renovação de token (até 5 min).

## Como o tenant atravessa o sistema

| Camada                  | Como o tenant é identificado                                                                         | Implementado                          |
| ----------------------- | ---------------------------------------------------------------------------------------------------- | ------------------------------------- |
| Login                   | Claim `organization` do token, com `tenant_id` nos atributos; o BFF exige exatamente uma organização | Sim                                   |
| Borda / roteamento      | Tenant → célula pelo cache local do Tenant Registry (alimentado por eventos)                         | Planejado                             |
| APIs dos serviços       | `tenant_id` do token validado; nunca de parâmetro informado pelo cliente                             | A partir dos serviços de negócio      |
| Banco (`pooled`)        | `SET app.tenant_id` a cada transação + políticas RLS                                                 | A partir dos serviços de negócio      |
| Eventos                 | `tenantid` no envelope CloudEvents; chave da mensagem com o tenant                                   | Sim                                   |
| Cache (Redis)           | Chave sempre prefixada pelo tenant                                                                   | A partir do primeiro cache de negócio |
| Logs, métricas e traces | Atributo `tenant_id`                                                                                 | Parcial (correlação já propagada)     |

## Regras obrigatórias para serviços de negócio

1. Toda tabela de negócio tem `tenant_id uuid NOT NULL` e o tenant entra nos índices relevantes.
2. Em `pooled`, a tabela tem RLS habilitada com política por `current_setting('app.tenant_id')`; a aplicação define o tenant no início de cada transação.
3. O tenant vem **do token**, nunca do corpo da requisição.
4. **Teste de isolamento obrigatório:** o tenant A não lê nem altera dados do tenant B, por API e direto no banco.
5. Processamentos em lote rodam por tenant (filas e limites por tenant), para que um cliente grande não degrade os demais.

O Tenant Registry e o Identity não usam RLS: guardam dados de plataforma (o cadastro dos próprios tenants), operados apenas pela equipe da plataforma.
