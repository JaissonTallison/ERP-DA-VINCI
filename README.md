# ERP-DA-VINCI

Plataforma ERP multissetorial, global e multi-tenant — SaaS compartilhado e implantação dedicada — para médias e grandes empresas, multinacionais e grupos econômicos.

> **Fase atual: 1 — Fundação.** Estrutura, padrões e arquitetura. Ainda não há módulos de negócio.

## Comece por aqui

| Preciso…                   | Leia                                                                                 |
| -------------------------- | ------------------------------------------------------------------------------------ |
| Subir o ambiente local     | [docs/development/setup.md](docs/development/setup.md)                               |
| Entender a arquitetura     | [docs/architecture/global-architecture.md](docs/architecture/global-architecture.md) |
| Ver os domínios do ERP     | [docs/architecture/bounded-contexts.md](docs/architecture/bounded-contexts.md)       |
| Saber por que algo é assim | [docs/adr/](docs/adr/README.md)                                                      |
| Contribuir com código      | [docs/engineering/standards.md](docs/engineering/standards.md)                       |

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
