# Organization Management — Segurança e autorização

> Estado: **proposta para aprovação**. Decisão do motor de políticas: ADR-0021 (proposto). Base: SECURITY_ARCHITECTURE.md, ADR-0009, ADR-0016.

## 1. Ameaças e controles

| Ameaça                                                                                 | Controle                                                                                                                                    |
| -------------------------------------------------------------------------------------- | ------------------------------------------------------------------------------------------------------------------------------------------- |
| Ler ou alterar dados de outro tenant (IDOR)                                            | Tenant **só do token**; RLS com `FORCE` no banco; chaves estrangeiras compostas `(tenant_id, id)`; recurso de outro tenant responde **404** |
| Referência cruzada entre tenants (ex.: filial apontando para entidade de outro tenant) | Impossível no banco: toda FK inclui `tenant_id`                                                                                             |
| Usuário do tenant acessando entidade ou filial fora do seu escopo                      | Autorização por escopo (seção 2); fora do escopo de leitura responde 404; com leitura mas sem escrita responde 403 `forbidden-scope`        |
| _Mass assignment_                                                                      | Contrato com `additionalProperties: false`; `id`, `tenantId`, `status` e `version` nunca vêm do cliente                                     |
| Alteração concorrente silenciosa                                                       | `If-Match` com versão (412 em conflito)                                                                                                     |
| Repetição de comando                                                                   | `Idempotency-Key`                                                                                                                           |
| Mudança estrutural indevida (reorganização, encerramento de entidade)                  | Segregação de funções (quem prepara ≠ quem aprova) e reautenticação com MFA (_step-up_, claim `acr`) para operações críticas                |
| Adulteração do histórico                                                               | `audit_log` append-only (o papel da aplicação não tem `UPDATE`/`DELETE`); cópia para armazenamento imutável quando Governance existir       |
| Enumeração de códigos e identificadores fiscais                                        | Listagens filtradas pelo escopo; limite de taxa no BFF/borda; buscas exigem permissão de leitura                                            |
| Vazamento de dado pessoal                                                              | Titular externo pessoa física: identificador fiscal cifrado na aplicação; eventos não levam documento pessoal; logs sem dado pessoal        |

## 2. Modelo de autorização (RBAC + ABAC com escopo)

**Permissão = ação sobre um tipo de recurso**, concedida a um papel; **atribuição = (usuário, papel, escopo)**.

### Permissões

| Recurso                 | Permissões                                                                                         |
| ----------------------- | -------------------------------------------------------------------------------------------------- |
| Grupo econômico         | `economic-group.read`, `economic-group.manage`                                                     |
| Entidade jurídica       | `legal-entity.read`, `legal-entity.manage`, `legal-entity.lifecycle` (ativar, suspender, encerrar) |
| Estabelecimento         | `establishment.read`, `establishment.manage`                                                       |
| Unidade e estrutura     | `unit.read`, `unit.manage`, `structure.move`                                                       |
| Reorganização           | `reorganization.prepare`, `reorganization.approve`                                                 |
| Centro de custo         | `cost-center.read`, `cost-center.manage`                                                           |
| Participação societária | `ownership.read`, `ownership.manage`                                                               |
| Histórico               | `history.read`                                                                                     |

### Escopos e herança

```
tenant
└── grupo econômico
    └── entidade jurídica
        ├── estabelecimento
        └── centro de custo
estrutura × unidade ──▶ subárvore da unidade naquela estrutura
```

- O escopo concedido vale para o nó e tudo abaixo dele, **na data da operação**: após uma reorganização, o escopo por subárvore acompanha a nova posição.
- Unidade vinculada a uma entidade jurídica também herda o escopo da entidade (atributo `legalEntityId`).
- **Listagens** são filtradas no SQL pelo conjunto de entidades e subárvores permitidas (não filtrar depois de paginar).

### Segregação de funções

| Conflito                                      | Regra                                                                   |
| --------------------------------------------- | ----------------------------------------------------------------------- |
| Preparar × aprovar reorganização              | Pessoas diferentes (garantido no domínio e no banco)                    |
| Registrar participação × aprovar consolidação | Fica para o Governance/Accounting (registro apenas auditado nesta fase) |
| Administrar permissões × operar estrutura     | Responsabilidade do Governance                                          |

### Até o Governance existir

| Papel do token (realm `davinci`) | Pode                                                                      |
| -------------------------------- | ------------------------------------------------------------------------- |
| `tenant-admin`                   | Tudo no próprio tenant (escopo = tenant), com MFA já obrigatório no login |
| `tenant-user`                    | Somente leitura (`*.read`) no próprio tenant                              |

A aplicação usa uma interface de decisão (`PolicyDecisionPoint`) desde o início. Os papéis do token são a primeira implementação; trocar pelo motor escolhido não muda os casos de uso.

## 3. Motor de políticas: comparação técnica

Critérios do ERP: decisões em **< 1 ms** por requisição, herança hierárquica (entidade → filial, subárvore), filtro de listagens, políticas auditáveis e analisáveis, operação simples por região e célula, sem dado saindo da região.

| Critério               | **Cedar** (AWS, Apache 2.0)                                                         | **OPA / Rego** (CNCF graduado)                                | **OpenFGA** (Zanzibar, CNCF)                                                               | **Implementação própria**                                                        |
| ---------------------- | ----------------------------------------------------------------------------------- | ------------------------------------------------------------- | ------------------------------------------------------------------------------------------ | -------------------------------------------------------------------------------- |
| Modelo                 | RBAC + ABAC, com hierarquia de entidades nativa (`resource in LegalEntity::"x"`)    | Genérico (qualquer política sobre JSON)                       | ReBAC: relações entre objetos e herança por relação                                        | O que for codificado                                                             |
| Linguagem              | Tipada, com esquema e validação estática; análise formal de políticas               | Rego: poderosa, curva de aprendizado alta, sem tipos          | DSL de modelo de autorização simples                                                       | Java                                                                             |
| Execução               | **Embutido no processo** (biblioteca Rust com binding Java via JNI)                 | Processo à parte (sidecar/daemon, chamada HTTP local) ou WASM | **Servidor à parte com banco próprio**                                                     | No processo                                                                      |
| Latência               | Microssegundos                                                                      | ~1 ms (rede local)                                            | ms (rede + banco)                                                                          | Microssegundos                                                                   |
| Dados para decidir     | Enviados na requisição (entidades e ancestrais)                                     | Enviados na requisição ou sincronizados em _bundles_          | **Relações replicadas no servidor** (precisa sincronizar cada mudança de estrutura)        | Na própria base                                                                  |
| Filtro de listagens    | Avaliação parcial ainda experimental: na prática, pré-filtro por escopos no SQL     | Avaliação parcial (complexa)                                  | `ListObjects` nativo                                                                       | SQL                                                                              |
| Auditoria das decisões | A aplicação registra a decisão e a política aplicada                                | _Decision logs_ nativos                                       | Logs do servidor                                                                           | Manual                                                                           |
| Operação por célula    | Nenhuma peça extra                                                                  | Sidecar por instância e distribuição de políticas             | Mais um serviço com estado, alta disponibilidade e backup **por célula**                   | Nenhuma                                                                          |
| Riscos                 | Binding JNI: biblioteca nativa na imagem (glibc/arquitetura); ecossistema mais novo | Rego difícil de revisar; mais uma peça em execução            | Consistência entre a estrutura no Organization e as relações no OpenFGA; custo operacional | Reinventar a lógica, políticas espalhadas no código, difícil de auditar e provar |

### Recomendação

**Cedar como motor-alvo, embutido nos serviços, condicionado a uma prova de conceito** (ADR-0021, proposto):

1. Encaixa no modelo: RBAC + ABAC com hierarquia de entidades, exatamente o par escopo-herança daqui.
2. Roda no processo, sem rede e sem mais um serviço com estado por célula, o que preserva a independência regional.
3. Políticas tipadas e analisáveis atendem auditoria e clientes regulados ("prove que ninguém de fora da filial X altera centros de custo dela").
4. O Organization fornece os ancestrais (entidade, grupo, subárvore) como hierarquia de entidades do Cedar; o Governance passa a ser dono das políticas e das atribuições.

**Por que não os outros, agora:**

- **OPA:** custo de mais um processo por instância e Rego difícil de revisar, sem ganho para este modelo.
- **OpenFGA:** excelente para compartilhamento por relação, mas exigiria replicar toda a estrutura organizacional num servidor à parte, por célula. Reavaliar se surgir compartilhamento entre usuários no nível de documento.
- **Implementação própria:** fica só como implementação provisória da interface (seção 2), não como destino.

**Prova de conceito (antes de aprovar o ADR-0021):**

- `cedar-java` na imagem do serviço (Linux x86_64 e ARM64).
- 10 mil decisões por segundo com latência p99 < 1 ms.
- Políticas para os escopos acima.
- Pré-filtro de listagem por escopo.
- Registro da decisão na auditoria.

Se a prova falhar, a alternativa é o OPA com WASM embutido.

## 4. Integração com Governance, Risk & Compliance (futuro)

| Peça                                                  | Dono                                                                 |
| ----------------------------------------------------- | -------------------------------------------------------------------- |
| Catálogo de permissões do Organization (tabela acima) | Organization (publicado como contrato)                               |
| Papéis, atribuições com escopo, matriz de SoD         | Governance                                                           |
| Políticas Cedar                                       | Governance (versionadas e revisadas)                                 |
| Ancestrais e escopos estruturais na data              | Organization (API `ancestors` e eventos)                             |
| Decisão                                               | Cada serviço, localmente, com políticas distribuídas pelo Governance |

## 5. Auditoria

- Toda escrita grava em `audit_log`: ator (`sub`), tenant, agregado, ação, antes/depois, correlação e data.
- Leitura de histórico exige `history.read`.
- Exportação para o armazenamento imutável da plataforma quando o Governance existir (ADR-0012 / SECURITY_ARCHITECTURE).

## 6. Pré-requisitos na fundação

Situação em 2026-11-03: itens 1 e 3 **feitos** na etapa 2; item 2 fica para a etapa 8 (telas).

1. **Audiência do token:** incluir `organization` como audiência do token do `davinci-web` (escopo de cliente no realm), validada pelo serviço.
2. **Repasse do token pelo BFF:** o BFF chama o Organization com o access token da sessão, renovado no servidor. O token nunca vai ao navegador.
3. **Papéis de banco separados** (dono × aplicação) para o RLS ter efeito.
