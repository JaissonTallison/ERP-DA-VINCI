# Contratos

Contratos versionados entre serviços (ADR-0008 e ADR-0014). Os testes de integração de cada serviço validam respostas e eventos contra estes arquivos.

| Contexto        | HTTP                                   | Eventos                                                           |
| --------------- | -------------------------------------- | ----------------------------------------------------------------- |
| Tenant Registry | `http/tenant-registry/v1/openapi.yaml` | `events/tenant-registry/v1/` (tópico `tenant-registry.tenant.v1`) |

```
contracts/
├── http/<contexto>/v<N>/openapi.yaml       # OpenAPI 3.1
└── events/<contexto>/v<N>/asyncapi.yaml    # AsyncAPI 3 + schemas
```

Quebra de compatibilidade = nova versão maior, com período de convivência.
