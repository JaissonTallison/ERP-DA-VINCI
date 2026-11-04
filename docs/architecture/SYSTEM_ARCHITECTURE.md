# Arquitetura global

> Estado: **aprovada para a Fase 1** (fundação). Decisões detalhadas nos ADRs 0003, 0004 e 0010.

## Visão em uma frase

Um **Global Control Plane** pequeno governa tenants, células e implantações; **Regional Data Planes** independentes executam o ERP em **células** isoladas, cada uma capaz de operar sozinha mesmo com o Control Plane fora do ar.

```
                         ┌───────────────────────────────────────────┐
                         │             GLOBAL CONTROL PLANE           │
                         │  Tenant Registry · Provisionamento · Billing│
                         │  Catálogo de células · Releases · Políticas │
                         └───────────────┬───────────────────────────┘
                     assíncrono (eventos,│ GitOps, cache de roteamento)
             ┌──────────────────────────┼──────────────────────────┐
             ▼                          ▼                          ▼
   ┌──────────────────┐      ┌──────────────────┐      ┌──────────────────┐
   │ DATA PLANE br-1  │      │ DATA PLANE eu-1  │      │ DATA PLANE us-1  │
   │  Edge/Roteador   │      │  Edge/Roteador   │      │  Edge/Roteador   │
   │ ┌──────┐┌──────┐ │      │ ┌──────┐┌──────┐ │      │ ┌──────┐         │
   │ │pooled││silo  │ │      │ │pooled││bridge│ │      │ │pooled│   ...   │
   │ │ -01  ││ acme │ │      │ │ -01  ││ -01  │ │      │ │ -01  │         │
   │ └──────┘└──────┘ │      │ └──────┘└──────┘ │      │ └──────┘         │
   │ Identidade, Kafka, observabilidade e dados regionais                   │
   └──────────────────┘      └──────────────────┘      └──────────────────┘
```

## Componentes

### Global Control Plane

Responsável por **o que existe e onde**, nunca por executar transações de negócio.

| Capacidade          | Descrição                                                                                                                    |
| ------------------- | ---------------------------------------------------------------------------------------------------------------------------- |
| Tenant Registry     | Fonte da verdade de tenants: `TenantId`, região de residência, célula, modelo de isolamento, plano, estado do ciclo de vida. |
| Catálogo de células | Inventário de células por região, capacidade e versão implantada.                                                            |
| Provisionamento     | Orquestra criação, migração e encerramento de tenants e células (Saga orquestrada, idempotente).                             |
| Gestão de releases  | Define ondas de implantação por célula (canário → pooled → silo).                                                            |
| Políticas globais   | Catálogos de permissões, residência de dados permitida, parâmetros de conformidade.                                          |

**Regra inegociável:** o Control Plane **não é dependência síncrona** de transações operacionais. Cada data plane mantém uma **cópia local** (replicada por eventos) do mapa tenant → célula e das políticas. Se o Control Plane cair, nenhum tenant existente para; só ficam indisponíveis operações de ciclo de vida (criar tenant, mover célula).

Os metadados do Control Plane contêm apenas dados de configuração e identificação mínima de organizações — **nenhum dado pessoal ou transacional de tenant** sai da região de residência.

### Regional Data Planes

Um data plane por jurisdição de residência (`br-1`, `eu-1`, `us-1`…). Cada um é **independente**: conta de nuvem, rede, chaves de criptografia (KMS), Kafka, identidade, observabilidade e backups próprios. Falha ou incidente de segurança em uma região não se propaga.

### Células (cell-based architecture)

Célula = unidade de implantação completa (todos os serviços + bancos + broker) que atende um conjunto limitado de tenants.

- **Limite do raio de impacto:** um defeito, sobrecarga ou release ruim afeta só os tenants da célula.
- **Escala horizontal por células**, não por crescimento infinito de um cluster. Ao atingir o limite de capacidade definido, nova célula.
- **Identificador:** `CellId` = `<região>-<isolamento>-<nome>`, ex.: `br-1-pooled-01`, `eu-1-silo-acme` (implementado em `libs/java/platform-kernel`).
- **Roteamento:** a borda regional resolve o tenant (subdomínio, claim do token ou cabeçalho assinado) para a célula, consultando o cache local do Tenant Registry.

### Modelos de isolamento (ADR-0004)

| Modelo     | O que é compartilhado     | Para quem                                               | Isolamento de dados                                                                                 |
| ---------- | ------------------------- | ------------------------------------------------------- | --------------------------------------------------------------------------------------------------- |
| **pooled** | Célula, serviços e bancos | Médias empresas, SaaS padrão                            | `tenant_id` em toda tabela + PostgreSQL Row-Level Security + filtro obrigatório na camada de acesso |
| **bridge** | Serviços da célula        | Grandes empresas com exigência de dados separados       | Banco (ou cluster) dedicado por tenant, serviços compartilhados                                     |
| **silo**   | Nada                      | Multinacionais, setores regulados, implantação dedicada | Célula inteira dedicada; pode rodar em conta/nuvem do cliente                                       |

O **mesmo artefato** de software roda nos três modelos. A diferença é configuração de implantação, nunca ramificação de código.

## Residência de dados

1. Todo tenant tem uma **região de residência** registrada no nascimento (`RegionCode`). Mudança exige processo formal de migração.
2. Dados pessoais e transacionais **nunca** saem da região. Replicação de DR acontece para uma região par **na mesma jurisdição** (ou em jurisdição aprovada contratualmente).
3. Dados agregados para analytics global só saem da região após anonimização, conforme política de privacidade da jurisdição.
4. Grupos econômicos com empresas em várias jurisdições: cada entidade legal pode residir em uma região diferente; a consolidação acontece por troca de dados contábeis já agregados, com base legal registrada.

## Provisionamento automatizado

Fluxo previsto (implementação na Fase 2+):

1. Pedido de tenant chega ao Control Plane (portal comercial ou API).
2. Saga de provisionamento: reserva `TenantId` → escolhe célula conforme região, isolamento e capacidade → cria recursos (schema/banco, chaves, realm de identidade) → publica `TenantProvisioned`.
3. Cada data plane consome o evento, atualiza seu cache de roteamento e executa migrações de schema por serviço.
4. Toda etapa é idempotente e compensável.

## Ambientes

| Ambiente      | Finalidade               | Estrutura                                       |
| ------------- | ------------------------ | ----------------------------------------------- |
| local         | Desenvolvimento          | `compose.yaml` (uma "célula" simplificada)      |
| dev / staging | Integração e homologação | Uma região, uma célula pooled; dados sintéticos |
| produção      | Clientes                 | Uma região por jurisdição, N células            |

Infraestrutura de produção (Terraform, Helm, Argo CD) **não foi criada**: a estratégia está proposta no ADR-0013 e depende de aprovação.
