# Ambiente local

## Pré-requisitos

| Ferramenta       | Versão                                  | Observação                                                   |
| ---------------- | --------------------------------------- | ------------------------------------------------------------ |
| Node.js          | 24                                      | `nvm use` lê o `.nvmrc`                                      |
| pnpm             | 11.3                                    | `corepack enable` ou `npm i -g pnpm@11.3.0`                  |
| Java             | qualquer JRE ≥ 17 para iniciar o Gradle | O Gradle baixa sozinho o JDK Temurin 25 na primeira execução |
| Docker + Compose | Docker 25+, Compose v2+                 |                                                              |

Não é preciso instalar o Gradle: use `./gradlew`.

## Primeira execução

```bash
pnpm install          # dependências TypeScript
./gradlew build       # baixa Gradle 9.8.2 e JDK 25 na primeira vez; compila e testa o backend
docker compose up -d  # PostgreSQL, Redis e Kafka
pnpm dev              # frontend em http://localhost:3000
```

Para sobrescrever senhas do ambiente local: `cp .env.example .env`. Valores apenas para desenvolvimento.

## Serviços do compose

Todos os serviços escutam só em `127.0.0.1`.

| Perfil          | Serviço                                              | Porta                            | Acesso                                            |
| --------------- | ---------------------------------------------------- | -------------------------------- | ------------------------------------------------- |
| (padrão)        | PostgreSQL 18                                        | 5432                             | `davinci` / `davinci_dev`, banco `davinci`        |
| (padrão)        | Redis 8                                              | 6379                             |                                                   |
| (padrão)        | Kafka 4.2 (KRaft)                                    | 9092                             | auto-criação de tópicos desligada                 |
| `search`        | OpenSearch 3                                         | 9200                             | sem segurança (apenas local)                      |
| `analytics`     | ClickHouse                                           | 8123 (HTTP), 9000 (nativo)       | `davinci` / `davinci_dev`, banco `analytics`      |
| `storage`       | SeaweedFS (S3)                                       | 8333                             | access key `davinci`, secret `davinci_dev_secret` |
| `identity`      | Keycloak 26                                          | 8180                             | console `admin` / `davinci_dev`                   |
| `observability` | Grafana + Prometheus + Loki + Tempo + OTel Collector | 3001 (Grafana), 4317/4318 (OTLP) |                                                   |
| `full`          | todos acima                                          |                                  |                                                   |

```bash
docker compose --profile identity --profile observability up -d   # perfis específicos
docker compose --profile full up -d                                # tudo (~5 GB de RAM)
docker compose ps                                                  # estado e saúde
docker compose down                                                # para (mantém dados)
docker compose down -v                                             # para e APAGA os dados locais
```

## Verificação antes de abrir PR

Rode uma por vez (a máquina de desenvolvimento tem memória limitada):

```bash
pnpm format:check && pnpm lint && pnpm typecheck && pnpm test && pnpm build
./gradlew build
```

`./gradlew spotlessApply` corrige a formatação Java; `pnpm format` corrige a do restante.

## Problemas comuns

- **`Toolchain … does not provide JAVA_COMPILER`:** o JDK instalado é só runtime. O projeto já resolve sozinho via `gradle/gradle-daemon-jvm.properties`; confira se há acesso a `api.foojay.io`.
- **Download do Next.js expira no `pnpm install`:** o `pnpm-workspace.yaml` já define `fetchTimeout: 300000`. Não passe `--config.fetch-timeout` na linha de comando: no pnpm 11.3 o valor chega como texto e quebra os downloads.
- **OpenSearch não sobe (`vm.max_map_count`):** `sudo sysctl -w vm.max_map_count=262144`.
