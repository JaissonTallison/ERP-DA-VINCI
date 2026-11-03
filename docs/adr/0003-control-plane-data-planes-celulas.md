# ADR-0003: Global Control Plane, Regional Data Planes e células

- Estado: Aceito
- Data: 2026-11-03

## Contexto

Clientes globais exigem residência de dados por jurisdição, isolamento de falhas e implantações dedicadas, sem multiplicar o custo operacional do SaaS compartilhado.

## Decisão

- **Global Control Plane** mínimo: Tenant Registry, catálogo de células, provisionamento, releases e políticas globais. Não armazena dados pessoais nem transacionais de tenants.
- **Regional Data Planes** independentes por jurisdição, com identidade, Kafka, chaves, backups e observabilidade próprios.
- **Células** como unidade de implantação e de raio de impacto dentro de cada região; capacidade máxima por célula definida e monitorada.
- O Control Plane **não é dependência síncrona** de transações: data planes mantêm cópia local (por eventos) do roteamento tenant → célula e das políticas.
- Identificadores `TenantId`, `RegionCode` e `CellId` padronizados em `libs/java/platform-kernel`.

## Alternativas consideradas

- **Cluster global único:** não atende residência de dados e tem raio de impacto total.
- **Uma instalação por cliente:** custo e esforço de operação inviáveis para médias empresas.

## Consequências

- Releases em ondas por célula (canário primeiro).
- Roteamento na borda regional é componente crítico e precisa de alta disponibilidade.
- Relatórios globais de um grupo multinacional dependem de troca de dados agregados entre regiões, com base legal.
