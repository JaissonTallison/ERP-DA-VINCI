# Contratos

Contratos versionados entre serviços (ADR-0008). Vazio até o primeiro serviço (Fase 2).

```
contracts/
├── http/<contexto>/v<N>/openapi.yaml       # OpenAPI 3.1
└── events/<contexto>/v<N>/asyncapi.yaml    # AsyncAPI 3 + schemas
```

Quebra de compatibilidade = nova versão maior, com período de convivência.
