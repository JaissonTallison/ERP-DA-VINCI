# Instruções para agentes e colaboradores

Vale para pessoas e agentes de IA. Em conflito, este arquivo vence preferências pessoais.

## Antes de mudar algo

- Leia o ADR relacionado em `docs/adr/`. Mudança de arquitetura exige ADR novo.
- Não crie serviço em `services/` sem bounded context aprovado (ADR-0005). Nada de serviço vazio ou fictício.
- Não crie infraestrutura de produção: ADR-0013 ainda está **Proposto**.

## Builds independentes

- TypeScript: `pnpm` + Turborepo, só em `apps/` e `packages/`.
- Java: `./gradlew`, só em `build-logic/`, `libs/java/` e `services/`.
- Nunca chame Gradle a partir do pnpm nem o contrário.

## Verificação (uma por vez — máquina de desenvolvimento com memória limitada)

```bash
pnpm format:check
pnpm lint
pnpm typecheck
pnpm test
pnpm build
./gradlew build
```

## Regras de código

- Sem `any`, sem `@ts-ignore`, sem dependência nova sem justificativa no PR.
- Java compila com `-Xlint:all -Werror`; formatação verificada pelo Spotless (`./gradlew spotlessApply` corrige).
- Todo dado de negócio carrega `tenant_id` (ADR-0004).
- Interface segue `docs/engineering/design-system.md`; o lint barra degradê, cantos > 8 px, sombras pesadas e cor solta.
- Textos, commits e documentação em português do Brasil.
