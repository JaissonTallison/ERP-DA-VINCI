# Stack definitiva

> Versões validadas em 2026-11-03. Atualizações passam pelo Dependabot e pelo CI.

## Backend

| Item                                  | Versão           | Observação                                                                                        |
| ------------------------------------- | ---------------- | ------------------------------------------------------------------------------------------------- |
| Java                                  | 25 (LTS)         | Toolchain fixo no Gradle                                                                          |
| Spring Boot                           | 4.1.2            | Suporta Java 17–26 e Gradle 8.14+/9.x (validado na documentação oficial). Spring Framework 7.0.10 |
| Gradle                                | 9.8.2            | Wrapper com checksum SHA-256                                                                      |
| JUnit                                 | 6.0.3            | Gerenciado pelo BOM do Spring Boot                                                                |
| Testcontainers                        | 2.0.5            | Gerenciado pelo BOM; entra com o primeiro serviço                                                 |
| Flyway                                | 12.4             | Gerenciado pelo BOM; migrações por serviço                                                        |
| JSpecify                              | 1.0.0            | Anotações de nulidade (padrão do Spring 7)                                                        |
| Spotless + palantir-java-format       | 8.11.0 / 2.103.0 | Formatação verificada no build                                                                    |
| CycloneDX Gradle plugin               | 3.5.1            | SBOM de cada serviço                                                                              |
| networknt json-schema-validator       | 3.0.9            | Somente testes: valida respostas e eventos contra `contracts/` (usa Jackson 3)                    |
| Spring Data JDBC/JPA, Spring Security | via BOM          | Escolha por serviço (JDBC por padrão; JPA quando o modelo justificar)                             |

## Frontend

| Item                                                 | Versão     | Observação                                                                           |
| ---------------------------------------------------- | ---------- | ------------------------------------------------------------------------------------ |
| Node.js                                              | 24 (LTS)   | `.nvmrc`                                                                             |
| pnpm                                                 | 11.3.0     | `packageManager` no `package.json`                                                   |
| Turborepo                                            | 2.11.10    |                                                                                      |
| Next.js                                              | 16.4.5     | App Router, saída `standalone`                                                       |
| React                                                | 19.3.1     |                                                                                      |
| TypeScript                                           | **6.0.3**  | TypeScript 7 bloqueado: `typescript-eslint` exige `<6.1` (ADR-0007)                  |
| ESLint                                               | **9.39.5** | ESLint 10 bloqueado: plugins do `eslint-config-next` ainda não o suportam (ADR-0007) |
| Tailwind CSS                                         | 4.3.4      | Tokens via `@theme`                                                                  |
| Vitest                                               | 5.1.0      | Testes unitários                                                                     |
| TanStack Query, TanStack Table, React Hook Form, Zod | —          | Adotados; instalados quando a primeira tela os usar (sem dependência ociosa)         |

## Dados (ambiente local)

| Item              | Imagem                                 | Observação                                                                      |
| ----------------- | -------------------------------------- | ------------------------------------------------------------------------------- |
| PostgreSQL        | `postgres:18.6`                        | Banco transacional; um por serviço em produção                                  |
| Redis             | `redis:8.8.4`                          | Cache e rate limiting; nunca fonte da verdade                                   |
| Apache Kafka      | `apache/kafka:4.2.2`                   | KRaft, mesma linha do cliente do Spring Boot 4.1                                |
| OpenSearch        | `opensearchproject/opensearch:3.9.0`   | Busca (projeção derivada)                                                       |
| ClickHouse        | `clickhouse/clickhouse-server:26.8.36` | Analytics                                                                       |
| Object storage S3 | `chrislusf/seaweedfs:4.52`             | Substitui MinIO no ambiente local (ADR-0010)                                    |
| Apache Iceberg    | —                                      | Lakehouse sobre object storage; catálogo definido na fase do contexto Analytics |
| Keycloak          | `quay.io/keycloak/keycloak:26.8.1`     | IdP broker                                                                      |
| Observabilidade   | `grafana/otel-lgtm:0.39.0`             | Grafana, Prometheus, Loki, Tempo e Collector OTel (somente local)               |

## Infraestrutura e entrega

Docker, Kubernetes, Terraform, Helm, Argo CD e GitHub Actions. Na Fase 1 apenas Docker (ambiente local) e GitHub Actions (CI) estão configurados; a estratégia de produção está no ADR-0013 (proposto).
