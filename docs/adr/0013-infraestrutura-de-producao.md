# ADR-0013: Estratégia de infraestrutura de produção

- Estado: Aceito (2026-11-04)
- Data: 2026-11-03

## Proposta

- **Provisionamento:** Terraform, com módulos por camada (conta/rede regional → cluster Kubernetes → dados gerenciados → célula). Estado remoto por região, com bloqueio e cifrado.
- **Empacotamento:** um chart Helm por serviço + chart "célula" que compõe todos os serviços com valores por modelo de isolamento.
- **Entrega:** GitOps com Argo CD por região (ApplicationSets geram uma aplicação por célula); ondas de release canário → pooled → bridge → silo.
- **Kubernetes:** cluster por região e ambiente; células como namespaces (pooled/bridge) ou clusters dedicados (silo).
- **Dados gerenciados** preferidos a operar bancos em Kubernetes (PostgreSQL e Kafka gerenciados), salvo em silo on-premises.

## Decisões necessárias do negócio antes de aprovar

1. Provedor(es) de nuvem e regiões iniciais.
2. Se implantações silo em infraestrutura do cliente fazem parte do produto na primeira versão.
3. Orçamento de infraestrutura por célula pooled.

## Consequências

- Até a aprovação, `infra/` contém apenas o ambiente local.
