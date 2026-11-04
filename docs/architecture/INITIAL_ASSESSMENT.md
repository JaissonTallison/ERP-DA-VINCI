# Diagnóstico inicial (Fase 1)

Data: 2026-11-03.

## Estado encontrado

- Repositório Git inicializado, branch `main`, **sem nenhum commit e sem arquivos**. Não havia código, configuração ou documentação a preservar; nada foi apagado.

## Ambiente de desenvolvimento verificado

| Ferramenta       | Encontrado                                | Observação                                                                                                            |
| ---------------- | ----------------------------------------- | --------------------------------------------------------------------------------------------------------------------- |
| Java             | OpenJDK 25.0.4 **headless** (sem `javac`) | Resolvido com provisionamento automático do JDK Temurin 25 pelo Gradle (foojay)                                       |
| Gradle           | não instalado                             | Wrapper 9.8.2 versionado no repositório                                                                               |
| Node.js          | 24.15                                     | Compatível (Next.js exige ≥ 20.9)                                                                                     |
| pnpm             | 11.3                                      | Fixado em `packageManager`; `fetchTimeout` no `pnpm-workspace.yaml` (o parâmetro de linha de comando tem bug no 11.3) |
| Docker / Compose | 29.9 / 5.6                                | Ok                                                                                                                    |
| Memória          | 15 GiB                                    | Limites de heap conservadores no Gradle, Kafka e OpenSearch                                                           |

## Validação da stack proposta

| Item proposto                                                                    | Resultado                                                                     |
| -------------------------------------------------------------------------------- | ----------------------------------------------------------------------------- |
| Java 25 LTS                                                                      | ✔ Adotado                                                                     |
| Spring Boot 4.1.x "após validação"                                               | ✔ 4.1.2 suporta Java 17–26 e Gradle 9 (documentação oficial); build validado  |
| Next.js, React, Tailwind                                                         | ✔ Next 16.4.5, React 19.3.1, Tailwind 4.3.4                                   |
| TypeScript (última)                                                              | ✘ 7.0 incompatível com `typescript-eslint` → **6.0.3** (ADR-0007)             |
| ESLint (última)                                                                  | ✘ 10 incompatível com plugins do `eslint-config-next` → **9.39.5** (ADR-0007) |
| Object storage S3 local (MinIO, usual)                                           | ✘ MinIO não publica mais imagens → **SeaweedFS** (ADR-0010)                   |
| Kafka                                                                            | ✔ 4.2.2 (mesma linha do cliente gerenciado pelo Spring Boot 4.1)              |
| Demais itens (PostgreSQL, Redis, OpenSearch, ClickHouse, Keycloak, OTel/Grafana) | ✔ Versões atuais fixadas no `compose.yaml`                                    |

## Escopo deliberadamente fora da Fase 1

- Serviços de negócio (proibido nesta fase).
- Infraestrutura de produção (ADR-0013 proposto, aguardando decisões de negócio).
- SBOM e assinatura de imagens (não há artefato implantável ainda).
