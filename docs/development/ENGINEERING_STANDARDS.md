# Padrões de engenharia

## Fluxo de trabalho

- `main` sempre implantável; trabalho em branches curtas (`feat/…`, `fix/…`, `docs/…`, `chore/…`).
- Pull request obrigatório, com CI verde e ao menos uma revisão (duas para `docs/adr`, `build-logic`, `.github`, `infra`).
- Commits no padrão **Conventional Commits**, em português: `feat(finance): registra baixa parcial de título`.
- PR pequeno e com propósito único. Mudança de arquitetura acompanha ADR no mesmo PR.

## Código

| Tema                 | Regra                                                                                                                                          |
| -------------------- | ---------------------------------------------------------------------------------------------------------------------------------------------- |
| Linguagem do domínio | Nomes de código em inglês; textos de interface, mensagens de erro ao usuário, documentação e commits em português do Brasil                    |
| TypeScript           | `strict` máximo; sem `any`, sem `@ts-ignore`; `import type` para tipos                                                                         |
| Java                 | Compilação com `-Xlint:all -Werror`; pacotes `@NullMarked`; `record` para valores; sem `null` em APIs públicas                                 |
| Formatação           | Prettier (TS) e Spotless/palantir-java-format (Java), verificados no CI                                                                        |
| Dependências         | Nova dependência exige justificativa no PR (problema, alternativas, licença, manutenção); a CI barra vulnerabilidades conhecidas (OSV-Scanner) |
| Segredos             | Nunca no repositório; `.env` é ignorado; CI roda gitleaks                                                                                      |
| Dados de tenant      | `tenant_id` em toda tabela e evento de negócio                                                                                                 |

## Testes

| Nível                | Ferramenta                                      | Exigência                                                          |
| -------------------- | ----------------------------------------------- | ------------------------------------------------------------------ |
| Unitário             | JUnit 6 + AssertJ / Vitest                      | Regras de domínio cobertas; sem mocks de tipos que você não possui |
| Integração           | Testcontainers (PostgreSQL, Kafka, Redis reais) | Todo repositório, migração e consumidor/produtor de evento         |
| Contrato             | Verificação de OpenAPI/AsyncAPI                 | Todo contrato publicado (a partir da Fase 2)                       |
| Isolamento de tenant | Teste automatizado por serviço                  | Prova que tenant A não lê nem escreve dados de B                   |
| Ponta a ponta        | Playwright                                      | Fluxos críticos de negócio                                         |

Cobertura é observada (JaCoCo/Vitest), não usada como meta isolada.

## APIs e eventos

- REST com OpenAPI 3.1, erros em RFC 9457 (Problem Details), paginação por cursor, `Idempotency-Key` em comandos.
- Versionamento na URL por versão maior (`/v1/…`); campos novos são opcionais.
- Eventos em CloudEvents, contratos AsyncAPI, ver ADR-0008.

## Definição de pronto

1. CI verde (lint, tipos, testes, build, formatação, segredos).
2. Documentação atualizada quando o comportamento muda.
3. Observabilidade: logs, métricas e traces com `tenant_id` e `correlation_id`.
4. Segurança: autorização verificada, entrada validada, sem dado pessoal em log.
