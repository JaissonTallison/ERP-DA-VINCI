# ADR-0002: Monorepo poliglota — pnpm/Turborepo + Gradle

- Estado: Aceito
- Data: 2026-11-03

## Contexto

Frontend em TypeScript e serviços em Java precisam evoluir juntos (contratos, documentação, padrões), mas com builds, caches e implantações independentes.

## Decisão

- Um único repositório com dois sistemas de build **independentes**: pnpm 11 + Turborepo para `apps/` e `packages/`; Gradle 9 (wrapper) para `build-logic/`, `libs/java/` e `services/`.
- Nenhuma ponte entre eles: o pnpm não executa Gradle e vice-versa.
- Convenções de build Java em plugins de convenção (`build-logic/`, _included build_); versões em catálogo único (`gradle/libs.versions.toml`).
- Pipelines separados por filtro de caminho.
- Projetos Gradle incluídos explicitamente em `settings.gradle.kts` (sem descoberta automática), para que cada módulo novo passe por revisão.

## Alternativas consideradas

- **Repositórios separados por serviço:** quebra a visão de contratos e padrões; caro para começar.
- **Nx/Bazel orquestrando tudo:** poderoso, mas aumenta muito a complexidade operacional e a curva de aprendizado; reavaliar se o número de serviços passar de ~40.
- **Maven:** sem _included builds_ e plugins de convenção tão simples quanto os do Gradle.

## Consequências

- Mudança que atravessa front e back pode ser um único PR.
- Crescimento exige disciplina de CODEOWNERS e filtros de caminho no CI.
- Ciclos de dependência são barrados pelas próprias ferramentas (pnpm/Turbo e Gradle).
