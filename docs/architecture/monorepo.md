# Organização do monorepo

> Decisão: ADR-0002 (monorepo poliglota).

## Estrutura

```
.
├── apps/                     # Aplicações TypeScript implantáveis (pnpm + Turborepo)
│   └── web/                  # Frontend Next.js
├── packages/                 # Pacotes TypeScript compartilhados
│   ├── design-tokens/        # Tokens do Design System (cores, tipografia, cantos, sombras)
│   ├── eslint-config/        # Regras de lint (base + regras visuais)
│   └── typescript-config/    # tsconfig base, Next.js e biblioteca
├── services/                 # Serviços Java por bounded context (Gradle) — a partir da Fase 2
├── libs/java/                # Bibliotecas Java técnicas compartilhadas (sem regra de negócio)
│   └── platform-kernel/      # TenantId, RegionCode, CellId, IsolationModel
├── build-logic/              # Plugins de convenção do Gradle (build compartilhado do backend)
├── contracts/                # Contratos versionados (OpenAPI, AsyncAPI) — a partir da Fase 2
├── infra/                    # Infraestrutura; hoje apenas o ambiente local
│   └── local/                # Arquivos montados pelo compose.yaml
├── brand/                    # Identidade visual: SVGs oficiais, PNGs e ícones por plataforma
├── scripts/brand/            # Geradores da marca (pnpm brand:svg / brand:export)
├── docs/                     # Arquitetura, ADRs, padrões e guias
├── compose.yaml              # Ambiente local de desenvolvimento
├── settings.gradle.kts       # Raiz do build Java
└── package.json              # Raiz do workspace TypeScript
```

## Dois sistemas de build, nenhuma ponte

|            | TypeScript                                   | Java                                       |
| ---------- | -------------------------------------------- | ------------------------------------------ |
| Ferramenta | pnpm 11 + Turborepo 2                        | Gradle 9 (wrapper versionado)              |
| Escopo     | `apps/*`, `packages/*`                       | `build-logic`, `libs/java/*`, `services/*` |
| Comando    | `pnpm build` / `lint` / `typecheck` / `test` | `./gradlew build`                          |
| Pipeline   | `.github/workflows/frontend.yml`             | `.github/workflows/backend.yml`            |

O pnpm **não** executa o Gradle, e o Gradle não conhece o `package.json`. Cada lado tem build, cache e pipeline independentes, disparados por filtro de caminho.

## Regras de dependência

1. `apps/*` podem depender de `packages/*`. `packages/*` nunca dependem de `apps/*`.
2. Um serviço em `services/*` **não** depende de outro serviço. Integração só por contrato (`contracts/`).
3. `libs/java/*` são técnicas e pequenas; não podem conter regra de domínio nem depender de `services/*`.
4. Toda lib nova precisa de justificativa: dois ou mais consumidores reais.
5. **Ciclos são proibidos** e barrados por ferramenta: pnpm e Turborepo falham com ciclo no grafo de pacotes; Gradle falha com ciclo entre projetos. Ciclos entre pacotes Java dentro de um serviço serão verificados com ArchUnit quando o primeiro serviço existir.

## Contratos

- `contracts/http/<contexto>/` — OpenAPI 3.1; `contracts/events/<contexto>/` — AsyncAPI 3 + schemas.
- Versionamento semântico por contrato; quebra de compatibilidade exige nova versão maior e período de convivência.
- Verificação de compatibilidade no CI (a ser adicionada junto com o primeiro contrato).

## Como adicionar um serviço (Fase 2+)

1. ADR ou registro no catálogo justificando o contexto.
2. `services/<contexto>/` com `build.gradle.kts` aplicando `id("davinci.spring-boot-service")`.
3. `include` explícito em `settings.gradle.kts`.
4. Banco próprio, migrações próprias (Flyway), contratos em `contracts/`.
