# Serviços

Um diretório por bounded context, criado **apenas** quando o contexto entra em desenvolvimento (ADR-0005). Catálogo e ordem de construção: `docs/architecture/bounded-contexts.md`.

Cada serviço aplica `id("davinci.spring-boot-service")` e é incluído explicitamente em `settings.gradle.kts`.

| Serviço | Plano | Porta local | Documentação |
| --- | --- | --- | --- |
| `tenant-registry` | Control Plane | 5630 | [docs/services/tenant-registry.md](../docs/services/tenant-registry.md) |
