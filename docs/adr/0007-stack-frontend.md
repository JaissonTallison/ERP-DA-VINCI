# ADR-0007: Frontend — Next.js 16, TypeScript 6.0, ESLint 9

- Estado: Aceito
- Data: 2026-11-03

## Contexto

Na data da fundação, as últimas versões publicadas eram TypeScript 7.0 e ESLint 10, mas o ecossistema ainda não as suporta:

- `typescript-eslint` 8.72 declara `typescript >=4.8.4 <6.1.0`.
- `eslint-config-next` 16.4 depende de `eslint-plugin-react`, `eslint-plugin-import` e `eslint-plugin-jsx-a11y`, que declaram suporte até ESLint 9.

## Decisão

- Next.js 16.4 (App Router, `output: 'standalone'`), React 19.3, Tailwind CSS 4.3 com tokens em `@davinci/design-tokens`.
- **TypeScript 6.0.3** e **ESLint 9.39.5**, fixados. Dependabot ignora TypeScript ≥ 7 e ESLint ≥ 10 até o ecossistema acompanhar.
- TypeScript em modo estrito máximo (`strict`, `noUncheckedIndexedAccess`, `exactOptionalPropertyTypes`); `any` e `@ts-ignore` barrados no lint.
- TanStack Query, TanStack Table, React Hook Form e Zod são a stack adotada para telas; instalados quando a primeira tela os usar.
- Vitest para testes unitários; Playwright para ponta a ponta quando houver fluxos.
- Regras visuais do Design System barradas no lint (`@davinci/eslint-config/design-rules`).

## Consequências

- Revisar a cada trimestre a liberação de TypeScript 7 (compilador nativo, ganho grande de velocidade) e ESLint 10.
