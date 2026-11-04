# ERP-DA-VINCI

Plataforma ERP multissetorial, global e multi-tenant — SaaS compartilhado e implantação dedicada — para médias e grandes empresas, multinacionais e grupos econômicos.

> **Fase atual: 1 — Fundação.** Estrutura, padrões e arquitetura. Ainda não há módulos de negócio.

## Comece por aqui

| Preciso…                   | Leia                                                                                     |
| -------------------------- | ---------------------------------------------------------------------------------------- |
| Subir o ambiente local     | [docs/development/LOCAL_DEVELOPMENT.md](docs/development/LOCAL_DEVELOPMENT.md)           |
| Entender a arquitetura     | [docs/architecture/SYSTEM_ARCHITECTURE.md](docs/architecture/SYSTEM_ARCHITECTURE.md)     |
| Ver os domínios do ERP     | [docs/architecture/MICROSERVICES_CATALOG.md](docs/architecture/MICROSERVICES_CATALOG.md) |
| Saber por que algo é assim | [docs/adr/](docs/adr/README.md)                                                          |
| Contribuir com código      | [docs/development/ENGINEERING_STANDARDS.md](docs/development/ENGINEERING_STANDARDS.md)   |

Índice completo: [docs/README.md](docs/README.md).

## Comandos

```bash
# Frontend (TypeScript)
pnpm install
pnpm dev            # http://localhost:5620
pnpm lint && pnpm typecheck && pnpm test && pnpm build

# Backend (Java)
./gradlew build

# Dependências locais (PostgreSQL, Redis, Kafka)
docker compose up -d
```
