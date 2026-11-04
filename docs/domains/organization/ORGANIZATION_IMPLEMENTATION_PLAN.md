# Organization Management — Plano de implementação

> Estado: **proposta para aprovação**. Nenhuma etapa de código começa sem autorização.

Cada etapa termina com: contratos (OpenAPI e JSON Schema) escritos antes do código; testes unitários e de integração com PostgreSQL e Kafka reais; **teste de isolamento entre tenants**; documentação atualizada; verificação completa (frontend, backend, varreduras); commit local. Nada é publicado sem autorização.

## Etapa 1 — Discovery e desenho (esta)

Documentos ORGANIZATION_* e ADRs 0017 a 0022. **Saída:** aprovação do desenho e dos pontos da seção "Aprovações necessárias".

## Etapa 2 — Esqueleto do serviço, tenant e entidades jurídicas

| Entrega                             | Detalhe                                                                                                                          |
| ----------------------------------- | -------------------------------------------------------------------------------------------------------------------------------- |
| Serviço `services/organization`     | Convenção `davinci.spring-boot-service`, porta 5632, perfil `local`, checagem de configuração obrigatória                        |
| Banco `organization` com RLS        | Papéis `organization_owner` e `organization_app`, `FORCE ROW LEVEL SECURITY`, `SET LOCAL app.tenant_id` por transação            |
| Projeção do tenant                  | Consumidor de `tenant-registry.tenant.v1` (padrão do Identity); escrita só com tenant `ACTIVE`                                   |
| Grupo econômico e entidade jurídica | CRUD, ciclo de vida, pertença ao grupo com vigência, identificadores fiscais (validador `BR_CNPJ` como primeiro esquema)         |
| Padrões                             | Outbox, idempotência, `If-Match`, Problem Details, auditoria append-only                                                         |
| Autorização provisória              | `PolicyDecisionPoint` com os papéis do token (`tenant-admin` escreve, `tenant-user` lê)                                          |
| Fundação                            | Audiência `organization` no realm; papéis de banco no `infra/local/postgres/init`; ArchUnit (teste de dependência entre módulos) |

**Aceite:**

- Teste prova que o tenant A não lê, não altera e não referencia dados do tenant B, pela API e direto no banco com o papel da aplicação.
- Eventos `legal-entity.*` validam contra o schema.

**Tamanho:** M.

## Etapa 3 — Estabelecimentos

Cadastro e ciclo de vida, matriz única, coerência do identificador fiscal com a entidade (regra plugável; CNPJ com a mesma raiz), eventos `establishment.*`.

**Aceite:**

- Não é possível ativar estabelecimento de entidade não ativa.
- Encerrar a entidade é bloqueado enquanto houver estabelecimento ativo.

**Tamanho:** S.

## Etapa 4 — Unidades, tipos e estruturas com vigência

Catálogo de tipos e aninhamento, unidades com nome multilíngue, estruturas, vínculos com vigência, movimento simples, consultas `roots`, `children`, `subtree` e `ancestors` com `asOf`.

**Aceite:**

- Teste de propriedade com milhares de movimentos aleatórios não gera ciclo nem sobreposição.
- Subárvore de 20 mil nós em < 200 ms (medido com Testcontainers).

**Tamanho:** L.

## Etapa 5 — Reorganizações

Rascunho, passos, validação (simulação), submissão, aprovação por outra pessoa, agendamento, aplicação atômica na data, prévia da árvore, eventos `reorganization.*` e `unit.moved`.

**Aceite:**

- Uma reorganização com 500 passos é aplicada inteira ou não é aplicada.
- O submissor não consegue aprovar.

**Tamanho:** M.

## Etapa 6 — Centros de custo

Cadastro por entidade, tipos `POSTABLE` e `GROUP`, hierarquia com vigência, bloqueio e encerramento, eventos `cost-center.*`; passo `REASSIGN_COST_CENTER` nas reorganizações.

**Tamanho:** M.

## Etapa 7 — Participações societárias

Titulares externos (dado pessoal cifrado), participações com vigência, soma ≤ 100%, ausência de ciclo, organograma societário com participação efetiva e controlador final, eventos `ownership.*`.

**Aceite:**

- Cálculo de participação indireta conferido com casos de referência (cadeias e caminhos múltiplos).

**Tamanho:** M.

## Etapa 8 — Histórico e telas

API de histórico e de mudanças por período. Telas no BFF:

- grupo e entidades;
- estabelecimentos;
- árvore organizacional com navegação por nível e seletor de data;
- reorganização com prévia;
- centros de custo;
- organograma societário.

Tudo seguindo o Design System. Componentes de dados usam o TanStack Query.

**Tamanho:** L.

## Etapa 9 — Autorização definitiva

Prova de conceito do Cedar (critérios em ORGANIZATION_SECURITY.md). Se aprovada: implementação de `PolicyDecisionPoint` com Cedar, escopos por grupo, entidade, filial e subárvore, pré-filtro de listagens, _step-up_ de MFA para operações críticas.

**Depende de:** Governance (atribuições e políticas). Até lá, permanece a autorização provisória.

**Tamanho:** M.

## Ordem e dependências

```
2 ─▶ 3 ─▶ 4 ─▶ 5
│         └──▶ 6 (usa unidades)
└──▶ 7 (usa entidades)
2..7 ─▶ 8
PoC Cedar (paralela) ─▶ 9
```

## Aprovações necessárias antes da etapa 2

1. **ADR-0017:** um tenant contém um ou mais grupos econômicos; um grupo com entidades em jurisdições de residência diferentes exige um tenant por região (federação de tenants fica para depois).
2. **ADR-0018:** um único serviço `organization`, modular.
3. **ADR-0019:** hierarquias por lista de adjacência com vigência e CTE recursiva; extensão `btree_gist`.
4. **ADR-0020:** vigência em datas (`[from, to)`), interpretadas no fuso da entidade; histórico de atributos simples só por auditoria na primeira entrega.
5. **ADR-0021:** Cedar como motor-alvo, condicionado à prova de conceito; autorização provisória por papéis até o Governance.
6. **ADR-0022:** identificadores fiscais por esquema com validadores plugáveis; só `BR_CNPJ` com validação completa na etapa 2.
7. Ausência de agregado "Organization" e uso de **Establishment** para filiais (glossário do modelo de domínio).
8. Primeira adoção de RLS e papéis de banco separados (muda o ambiente local).
9. **Dependência nova:** ArchUnit (somente em testes), para garantir a direção das dependências entre módulos.

## Riscos técnicos

| Risco                                                             | Mitigação                                                                                                              |
| ----------------------------------------------------------------- | ---------------------------------------------------------------------------------------------------------------------- |
| RLS mal configurado (papel dono ignora a política)                | `FORCE ROW LEVEL SECURITY`, papel da aplicação sem privilégio de dono, teste de isolamento direto no banco             |
| Vigência e fuso confundem usuários e integrações                  | `asOf` explícito na API, datas interpretadas no fuso da entidade, eventos com `effectiveOn`                            |
| Árvores muito grandes ou profundas                                | Navegação por nível, limite de profundidade, medição na etapa 4 e caminho de otimização definido (fechamento derivado) |
| Corrida na checagem de ciclo                                      | Trava por estrutura (`pg_advisory_xact_lock`) e `EXCLUDE` no banco                                                     |
| Regras fiscais por país crescem sem controle                      | Validadores por esquema isolados (ADR-0022); regras tributárias ficam no contexto Tax                                  |
| Participações cruzadas reais (fora do suporte inicial)            | Rejeitadas com erro explícito; suporte junto com a consolidação                                                        |
| Binding nativo do Cedar                                           | Prova de conceito antes de adotar; OPA com WASM como alternativa                                                       |
| Grupo multinacional com exigência de residência em várias regiões | Um tenant por região, com consolidação por dados agregados (desenho detalhado em etapa futura)                         |
