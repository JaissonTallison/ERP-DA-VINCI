# Contratos

Contratos versionados entre serviços (ADR-0008 e ADR-0014). Os testes de integração de cada serviço validam respostas e eventos contra estes arquivos.

| Contexto        | HTTP                                   | Eventos                                                                                                                                                                                                                                             |
| --------------- | -------------------------------------- | --------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| Tenant Registry | `http/tenant-registry/v1/openapi.yaml` | `events/tenant-registry/v1/` (tópico `tenant-registry.tenant.v1`)                                                                                                                                                                                   |
| Organization    | `http/organization/v1/openapi.yaml`    | `events/organization/v1/` (`organization.economic-group.v1`, `organization.legal-entity.v1`, `organization.establishment.v1`, `organization.unit.v1`, `organization.reorganization.v1`, `organization.cost-center.v1`, `organization.ownership.v1`) |
| Governance      | `http/governance/v1/openapi.yaml`      | `events/governance/v1/` (`governance.role.v1`, `governance.assignment.v1`)                                                                                                                                                                          |

```
contracts/
├── http/<contexto>/v<N>/openapi.yaml       # OpenAPI 3.1
└── events/<contexto>/v<N>/asyncapi.yaml    # AsyncAPI 3 + schemas
```

Quebra de compatibilidade = nova versão maior, com período de convivência.
