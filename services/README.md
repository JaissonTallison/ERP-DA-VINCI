# Serviços

Um diretório por bounded context, criado **apenas** quando o contexto entra em desenvolvimento (ADR-0005). Catálogo e ordem de construção: `docs/architecture/MICROSERVICES_CATALOG.md`.

Cada serviço aplica `id("davinci.spring-boot-service")` e é incluído explicitamente em `settings.gradle.kts`.

| Serviço | Plano | Porta local | Documentação |
| --- | --- | --- | --- |
| `tenant-registry` | Control Plane | 5630 | [docs/services/TENANT_REGISTRY.md](../docs/services/TENANT_REGISTRY.md) |
| `identity` | Data Plane | 5631 | [docs/services/IDENTITY.md](../docs/services/IDENTITY.md) |
