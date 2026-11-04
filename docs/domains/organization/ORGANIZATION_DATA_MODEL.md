# Organization Management — Modelo de dados

> Estado: **proposta para aprovação**. Nenhuma migração foi criada ou executada. Decisões: ADR-0019 (hierarquias) e ADR-0020 (vigência e histórico).

## 1. Princípios

1. **Banco `organization`, dono exclusivo:** nenhum outro serviço acessa. Outros contextos recebem eventos.
2. **Toda tabela de negócio tem `tenant_id`**, inclusive catálogos do tenant.
3. **Isolamento em duas camadas no banco:**
   - **Row-Level Security (RLS)** com `FORCE`: nenhuma consulta enxerga outro tenant, mesmo com bug na aplicação.
   - **Chaves estrangeiras compostas `(tenant_id, id)`:** é impossível gravar uma referência a um registro de outro tenant.
4. **Histórico por vigência** (`valid_from`/`valid_to`) nas relações que mudam no tempo, mais **trilha de auditoria append-only** para quem mudou o quê e quando.
5. **Sem exclusão física** de dado que já foi ativo. Encerramento por status e data; só rascunhos nunca referenciados podem ser apagados.

## 2. Hierarquias: estratégia escolhida

| Estratégia                              | Leitura de subárvore                                                 | Mover nó                                | Vigência no tempo                                | Complexidade             |
| --------------------------------------- | -------------------------------------------------------------------- | --------------------------------------- | ------------------------------------------------ | ------------------------ |
| **Lista de adjacência + CTE recursiva** | Boa: `WITH RECURSIVE` com índice; milissegundos para milhares de nós | **O(1)**: fecha um vínculo e abre outro | **Natural**: cada vínculo tem período            | Baixa                    |
| Closure table                           | Ótima: uma junção                                                    | O(tamanho da subárvore × profundidade)  | Difícil: o fechamento teria que existir por data | Alta                     |
| Caminho materializado (`ltree`)         | Ótima: prefixo indexado                                              | Reescreve o caminho de toda a subárvore | Difícil: um caminho por período                  | Média, mais uma extensão |
| Nested sets                             | Ótima                                                                | Renumera a árvore                       | Inviável                                         | Alta                     |

**Decisão: lista de adjacência com vínculos com vigência, lida por CTE recursiva** (ADR-0019).

- Mover é barato e não reescreve histórico.
- Uma consulta "como estava em D" é só um filtro de vigência dentro da recursão.
- Volume por estrutura: até ~20 mil unidades com profundidade típica ≤ 12. Com índice em `(tenant_id, structure_id, parent_unit_id)`, a CTE recursiva atende em milissegundos.
- **Rota de evolução, sem mudar a fonte da verdade:** se medições mostrarem lentidão em árvores grandes, cria-se uma tabela derivada `structure_closure_current`, só da data de hoje e reconstruída pelo próprio serviço a cada movimento. Não entra agora: seria otimização sem medição.

**Prevenção de ciclos:**

- Na aplicação, sob trava por estrutura, o movimento é recusado se o novo pai estiver na subárvore da unidade em qualquer data do período.
- No banco, `EXCLUDE` impede dois pais vigentes ao mesmo tempo (seção 4).
- Um teste de propriedade, com movimentos aleatórios, garante que nenhuma sequência válida produz ciclo.

## 3. Isolamento multi-tenant no banco

```sql
-- Papel de migração (dono) e papel da aplicação (sem bypass de RLS).
-- organization_owner: dono das tabelas, usado só pelo Flyway.
-- organization_app:   usado pelo serviço; sem BYPASSRLS, sem DDL, sem DELETE em tabelas de histórico.

ALTER TABLE legal_entity ENABLE ROW LEVEL SECURITY;
ALTER TABLE legal_entity FORCE ROW LEVEL SECURITY;
CREATE POLICY tenant_isolation ON legal_entity
    USING (tenant_id = current_setting('app.tenant_id')::uuid)
    WITH CHECK (tenant_id = current_setting('app.tenant_id')::uuid);
```

- O serviço executa `SET LOCAL app.tenant_id = '<tenant do token>'` no início de **toda** transação. Sem essa configuração, `current_setting` falha e a consulta não retorna nada (falha fechada).
- O consumidor de eventos define o tenant a partir do evento.
- Isso exige dois papéis de banco. É uma mudança em relação ao Tenant Registry, que usa um papel só, sem problema por guardar dado de plataforma. Para o ambiente local: `infra/local/postgres/init` (etapa 2 do plano).

## 4. Tabelas (esboço)

Colunas comuns omitidas: `created_at timestamptz`, `updated_at timestamptz`, `version bigint` (controle otimista) nos agregados.

```sql
CREATE EXTENSION IF NOT EXISTS btree_gist;  -- EXCLUDE com igualdade + intervalo (contrib oficial do PostgreSQL)

-- Projeção do tenant (eventos do Tenant Registry)
CREATE TABLE tenant (
    tenant_id uuid PRIMARY KEY,
    status    text   NOT NULL CHECK (status IN ('PROVISIONING','ACTIVE','SUSPENDED')),
    version   bigint NOT NULL
);

CREATE TABLE economic_group (
    id                 uuid NOT NULL,
    tenant_id          uuid NOT NULL REFERENCES tenant,
    code               text NOT NULL,
    name               text NOT NULL,
    reference_currency char(3) NOT NULL,
    reference_timezone text NOT NULL,
    status             text NOT NULL CHECK (status IN ('ACTIVE','CLOSED')),
    PRIMARY KEY (tenant_id, id),
    UNIQUE (tenant_id, code)
);

CREATE TABLE legal_entity (
    id                      uuid NOT NULL,
    tenant_id               uuid NOT NULL REFERENCES tenant,
    code                    text NOT NULL,
    legal_name              text NOT NULL,
    trade_name              text,
    country                 char(2) NOT NULL,
    subdivision             text,
    legal_form              text,
    functional_currency     char(3) NOT NULL,
    timezone                text NOT NULL,
    default_locale          text NOT NULL,
    fiscal_year_start_month smallint NOT NULL CHECK (fiscal_year_start_month BETWEEN 1 AND 12),
    registered_address      jsonb NOT NULL,     -- PostalAddress validado pela aplicação (campos variam por país)
    incorporated_on         date,
    closed_on               date,
    status                  text NOT NULL CHECK (status IN ('PLANNED','ACTIVE','SUSPENDED','CLOSED')),
    PRIMARY KEY (tenant_id, id),
    UNIQUE (tenant_id, code)
);

CREATE TABLE legal_entity_group_assignment (
    tenant_id         uuid NOT NULL,
    legal_entity_id   uuid NOT NULL,
    economic_group_id uuid NOT NULL,
    valid_from        date NOT NULL,
    valid_to          date,
    CHECK (valid_to IS NULL OR valid_to > valid_from),
    FOREIGN KEY (tenant_id, legal_entity_id)   REFERENCES legal_entity (tenant_id, id),
    FOREIGN KEY (tenant_id, economic_group_id) REFERENCES economic_group (tenant_id, id),
    -- no máximo um grupo por entidade em cada data
    EXCLUDE USING gist (tenant_id WITH =, legal_entity_id WITH =,
                        daterange(valid_from, valid_to) WITH &&)
);

CREATE TABLE tax_identifier (                 -- de entidade jurídica OU de estabelecimento
    id               uuid PRIMARY KEY,
    tenant_id        uuid NOT NULL,
    legal_entity_id  uuid,
    establishment_id uuid,
    scheme           text NOT NULL,          -- BR_CNPJ, US_EIN, EU_VAT…
    value            text NOT NULL,          -- normalizado
    is_primary       boolean NOT NULL,
    valid_from       date NOT NULL,
    valid_to         date,
    CHECK (num_nonnulls(legal_entity_id, establishment_id) = 1),
    FOREIGN KEY (tenant_id, legal_entity_id)  REFERENCES legal_entity (tenant_id, id),
    FOREIGN KEY (tenant_id, establishment_id) REFERENCES establishment (tenant_id, id),
    -- o mesmo identificador não pode estar vigente duas vezes no tenant
    EXCLUDE USING gist (tenant_id WITH =, scheme WITH =, value WITH =,
                        daterange(valid_from, valid_to) WITH &&)
);

CREATE TABLE establishment (
    id              uuid NOT NULL,
    tenant_id       uuid NOT NULL,
    legal_entity_id uuid NOT NULL,
    code            text NOT NULL,
    name            text NOT NULL,
    type            text NOT NULL,
    address         jsonb NOT NULL,
    timezone        text NOT NULL,
    status          text NOT NULL CHECK (status IN ('ACTIVE','SUSPENDED','CLOSED')),
    opened_on       date NOT NULL,
    closed_on       date,
    PRIMARY KEY (tenant_id, id),
    UNIQUE (tenant_id, legal_entity_id, code),
    FOREIGN KEY (tenant_id, legal_entity_id) REFERENCES legal_entity (tenant_id, id)
);
-- uma matriz ativa por entidade
CREATE UNIQUE INDEX establishment_one_headquarters
    ON establishment (tenant_id, legal_entity_id) WHERE type = 'HEADQUARTERS' AND status <> 'CLOSED';

CREATE TABLE unit_type (
    id        uuid NOT NULL,
    tenant_id uuid NOT NULL,
    code      text NOT NULL,
    name      jsonb NOT NULL,                  -- LocalizedText
    PRIMARY KEY (tenant_id, id),
    UNIQUE (tenant_id, code)
);
CREATE TABLE unit_type_nesting (               -- quais tipos podem ser pais de quais (vazio = livre)
    tenant_id      uuid NOT NULL,
    parent_type_id uuid NOT NULL,
    child_type_id  uuid NOT NULL,
    PRIMARY KEY (tenant_id, parent_type_id, child_type_id)
);

CREATE TABLE organizational_unit (
    id               uuid NOT NULL,
    tenant_id        uuid NOT NULL,
    code             text NOT NULL,
    name             jsonb NOT NULL,           -- LocalizedText
    unit_type_id     uuid NOT NULL,
    legal_entity_id  uuid,
    establishment_id uuid,
    status           text NOT NULL CHECK (status IN ('ACTIVE','INACTIVE','CLOSED')),
    valid_from       date NOT NULL,
    valid_to         date,
    PRIMARY KEY (tenant_id, id),
    UNIQUE (tenant_id, code),
    FOREIGN KEY (tenant_id, unit_type_id)     REFERENCES unit_type (tenant_id, id),
    FOREIGN KEY (tenant_id, legal_entity_id)  REFERENCES legal_entity (tenant_id, id),
    FOREIGN KEY (tenant_id, establishment_id) REFERENCES establishment (tenant_id, id)
);

CREATE TABLE organizational_structure (
    id        uuid NOT NULL,
    tenant_id uuid NOT NULL,
    code      text NOT NULL,
    name      jsonb NOT NULL,
    purpose   text NOT NULL CHECK (purpose IN ('MANAGEMENT','FUNCTIONAL','GEOGRAPHIC','CUSTOM')),
    PRIMARY KEY (tenant_id, id),
    UNIQUE (tenant_id, code)
);

CREATE TABLE structure_link (                  -- posição da unidade na estrutura, com vigência
    tenant_id      uuid NOT NULL,
    structure_id   uuid NOT NULL,
    unit_id        uuid NOT NULL,
    parent_unit_id uuid,                       -- NULL = raiz
    valid_from     date NOT NULL,
    valid_to       date,
    reorganization_id uuid,                    -- de onde veio a mudança (opcional)
    CHECK (valid_to IS NULL OR valid_to > valid_from),
    CHECK (parent_unit_id IS DISTINCT FROM unit_id),
    FOREIGN KEY (tenant_id, structure_id)   REFERENCES organizational_structure (tenant_id, id),
    FOREIGN KEY (tenant_id, unit_id)        REFERENCES organizational_unit (tenant_id, id),
    FOREIGN KEY (tenant_id, parent_unit_id) REFERENCES organizational_unit (tenant_id, id),
    EXCLUDE USING gist (tenant_id WITH =, structure_id WITH =, unit_id WITH =,
                        daterange(valid_from, valid_to) WITH &&)
);
CREATE INDEX structure_link_children ON structure_link (tenant_id, structure_id, parent_unit_id, valid_from);

CREATE TABLE reorganization (
    id             uuid NOT NULL,
    tenant_id      uuid NOT NULL,
    structure_id   uuid NOT NULL,
    status         text NOT NULL CHECK (status IN ('DRAFT','PENDING_APPROVAL','SCHEDULED','APPLIED','CANCELLED')),
    effective_on   date NOT NULL,
    description    text NOT NULL,
    submitted_by   text, approved_by text,     -- sub do usuário; aprovador ≠ submissor
    CHECK (approved_by IS NULL OR approved_by <> submitted_by),
    PRIMARY KEY (tenant_id, id),
    FOREIGN KEY (tenant_id, structure_id) REFERENCES organizational_structure (tenant_id, id)
);
CREATE TABLE reorganization_step (
    tenant_id         uuid NOT NULL,
    reorganization_id uuid NOT NULL,
    position          int  NOT NULL,
    operation         text NOT NULL CHECK (operation IN ('CREATE_UNIT','MOVE_UNIT','RENAME_UNIT','CLOSE_UNIT','REASSIGN_COST_CENTER')),
    payload           jsonb NOT NULL,
    PRIMARY KEY (tenant_id, reorganization_id, position),
    FOREIGN KEY (tenant_id, reorganization_id) REFERENCES reorganization (tenant_id, id)
);

CREATE TABLE cost_center (
    id                  uuid NOT NULL,
    tenant_id           uuid NOT NULL,
    legal_entity_id     uuid NOT NULL,
    code                text NOT NULL,
    name                jsonb NOT NULL,
    kind                text NOT NULL CHECK (kind IN ('POSTABLE','GROUP')),
    category            text NOT NULL,
    responsible_unit_id uuid,
    status              text NOT NULL CHECK (status IN ('ACTIVE','BLOCKED','CLOSED')),
    valid_from          date NOT NULL,
    valid_to            date,
    PRIMARY KEY (tenant_id, id),
    UNIQUE (tenant_id, legal_entity_id, code),
    FOREIGN KEY (tenant_id, legal_entity_id)     REFERENCES legal_entity (tenant_id, id),
    FOREIGN KEY (tenant_id, responsible_unit_id) REFERENCES organizational_unit (tenant_id, id)
);
CREATE TABLE cost_center_link (                -- hierarquia de totalização, com vigência
    tenant_id      uuid NOT NULL,
    cost_center_id uuid NOT NULL,
    parent_id      uuid NOT NULL,
    valid_from     date NOT NULL,
    valid_to       date,
    CHECK (parent_id <> cost_center_id),
    FOREIGN KEY (tenant_id, cost_center_id) REFERENCES cost_center (tenant_id, id),
    FOREIGN KEY (tenant_id, parent_id)      REFERENCES cost_center (tenant_id, id),
    EXCLUDE USING gist (tenant_id WITH =, cost_center_id WITH =, daterange(valid_from, valid_to) WITH &&)
);

CREATE TABLE external_party (
    id        uuid NOT NULL,
    tenant_id uuid NOT NULL,
    kind      text NOT NULL CHECK (kind IN ('COMPANY','PERSON','FUND','OTHER')),
    name      text NOT NULL,
    country   char(2),
    tax_identifier_encrypted bytea,            -- cifrado na aplicação quando PERSON (dado pessoal)
    PRIMARY KEY (tenant_id, id)
);

CREATE TABLE ownership_interest (
    id                     uuid NOT NULL,
    tenant_id              uuid NOT NULL,
    owned_legal_entity_id  uuid NOT NULL,
    owner_legal_entity_id  uuid,
    owner_external_party_id uuid,
    capital_percentage     numeric(7,4) NOT NULL CHECK (capital_percentage > 0 AND capital_percentage <= 100),
    voting_percentage      numeric(7,4) NOT NULL CHECK (voting_percentage >= 0 AND voting_percentage <= 100),
    share_class            text,
    valid_from             date NOT NULL,
    valid_to               date,
    PRIMARY KEY (tenant_id, id),
    CHECK (num_nonnulls(owner_legal_entity_id, owner_external_party_id) = 1),
    CHECK (owner_legal_entity_id IS DISTINCT FROM owned_legal_entity_id),
    FOREIGN KEY (tenant_id, owned_legal_entity_id)   REFERENCES legal_entity (tenant_id, id),
    FOREIGN KEY (tenant_id, owner_legal_entity_id)   REFERENCES legal_entity (tenant_id, id),
    FOREIGN KEY (tenant_id, owner_external_party_id) REFERENCES external_party (tenant_id, id)
);
-- soma ≤ 100% e ausência de ciclo: OwnershipPolicy, sob trava por tenant (não expressável em CHECK)

-- Padrões já usados nos outros serviços
CREATE TABLE outbox_event (...);               -- igual ao Tenant Registry
CREATE TABLE idempotency_record (...);         -- idem, com tenant_id na chave

CREATE TABLE audit_log (                       -- append-only: organization_app só tem INSERT e SELECT
    id             bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    tenant_id      uuid NOT NULL,
    aggregate_type text NOT NULL,
    aggregate_id   uuid NOT NULL,
    action         text NOT NULL,
    actor          text NOT NULL,              -- sub do token (usuário ou serviço)
    correlation_id text NOT NULL,
    occurred_at    timestamptz NOT NULL,
    before         jsonb,
    after          jsonb
);
CREATE INDEX audit_log_aggregate ON audit_log (tenant_id, aggregate_type, aggregate_id, occurred_at);
```

As referências de `tax_identifier` a `establishment` exigem a ordem certa de criação na migração; o esboço não representa a ordem final.

## 5. Consultas hierárquicas

Subárvore de uma unidade numa data:

```sql
WITH RECURSIVE subtree AS (
    SELECT unit_id, parent_unit_id, 0 AS depth
      FROM structure_link
     WHERE tenant_id = :tenant AND structure_id = :structure AND unit_id = :root
       AND valid_from <= :as_of AND (valid_to IS NULL OR valid_to > :as_of)
    UNION ALL
    SELECT l.unit_id, l.parent_unit_id, s.depth + 1
      FROM structure_link l
      JOIN subtree s ON l.parent_unit_id = s.unit_id
     WHERE l.tenant_id = :tenant AND l.structure_id = :structure
       AND l.valid_from <= :as_of AND (l.valid_to IS NULL OR l.valid_to > :as_of)
       AND s.depth < 64                       -- limite de segurança
)
SELECT * FROM subtree;
```

Para telas com milhares de nós, a API entrega **por nível** (`children` de um nó) ou subárvore com `maxDepth`. A árvore inteira só vai para exportação.

## 6. Histórico, auditoria e exclusão

| Necessidade                                        | Mecanismo                                                                                                                                                      |
| -------------------------------------------------- | -------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| "Como era a estrutura em D?"                       | Vigência (`valid_from`/`valid_to`) nas relações e nos atributos que mudam no tempo                                                                             |
| "Quem mudou, quando, de onde?"                     | `audit_log` com antes/depois, ator, correlação; append-only por permissão de banco                                                                             |
| "O que mudou entre D1 e D2?"                       | Comparação de duas leituras `asOf` (API de histórico)                                                                                                          |
| Atributo que muda com vigência (ex.: razão social) | Etapa posterior: tabela `legal_entity_name_history` com vigência. Na primeira entrega, mudanças simples de atributo são registradas só na auditoria (ADR-0020) |
| Exclusão                                           | Física só para `PLANNED`/rascunho nunca referenciado; demais por encerramento                                                                                  |

## 7. Migrações e operação

- Flyway no próprio serviço, executado com o papel `organization_owner`. A aplicação usa `organization_app`.
- `btree_gist` é extensão oficial (contrib) do PostgreSQL, presente na imagem `postgres:18.6`; criada na primeira migração.
- Índices de vigência: `(tenant_id, <chave>, valid_from)` e índices parciais `WHERE valid_to IS NULL` para "vigente agora", caso as medições indiquem.
- Retenção: dados organizacionais acompanham o tenant enquanto houver contrato; auditoria retida pelo prazo legal mais longo aplicável (≥ 5 anos no Brasil para registros com efeito fiscal).
