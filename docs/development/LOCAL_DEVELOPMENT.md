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
pnpm dev              # frontend em http://localhost:5620
```

Para sobrescrever senhas do ambiente local: `cp .env.example .env`. Valores apenas para desenvolvimento.

## Mapa de portas

A máquina de desenvolvimento roda vários projetos. O ERP usa **somente a faixa 5600–5699**, sempre em `127.0.0.1`:

| Faixa     | Uso                                                         |
| --------- | ----------------------------------------------------------- |
| 5600–5619 | Infraestrutura do `compose.yaml`                            |
| 5620–5629 | Aplicações TypeScript                                       |
| 5630–5699 | Serviços Java (um por bounded context, em ordem de criação) |

Antes de reservar uma porta nova, confira se está livre: `ss -ltn "( sport = :5631 )"`.

| Perfil          | Serviço                                              | Porta                                      | Acesso                                                                               |
| --------------- | ---------------------------------------------------- | ------------------------------------------ | ------------------------------------------------------------------------------------ |
| (padrão)        | PostgreSQL 18                                        | 5600                                       | `davinci` / `davinci_dev`, banco `davinci`                                           |
| (padrão)        | Redis 8                                              | 5601                                       |                                                                                      |
| (padrão)        | Kafka 4.2 (KRaft)                                    | 5602                                       | auto-criação de tópicos desligada                                                    |
| `search`        | OpenSearch 3                                         | 5603                                       | sem segurança (apenas local)                                                         |
| `analytics`     | ClickHouse                                           | 5604 (HTTP), 5605 (nativo)                 | `davinci` / `davinci_dev`, banco `analytics`                                         |
| `storage`       | SeaweedFS (S3)                                       | 5606                                       | access key `davinci`, secret `davinci_dev_secret`                                    |
| `identity`      | Keycloak 26                                          | 5607                                       | console `admin` / `davinci_dev`; realms `platform` (operação) e `davinci` (usuários) |
| `observability` | Grafana + Prometheus + Loki + Tempo + OTel Collector | 5608 (Grafana), 5609/5610 (OTLP gRPC/HTTP) |                                                                                      |
| `full`          | todos acima                                          |                                            |                                                                                      |
| —               | Frontend (`pnpm dev`)                                | 5620                                       |                                                                                      |
| —               | Tenant Registry                                      | 5630                                       |                                                                                      |
| —               | Identity                                             | 5631                                       |                                                                                      |

```bash
docker compose --profile identity --profile observability up -d   # perfis específicos
docker compose --profile full up -d                                # tudo (~5 GB de RAM)
docker compose ps                                                  # estado e saúde
docker compose down                                                # para (mantém dados)
docker compose down -v                                             # para e APAGA os dados locais
```

## Serviços Java no ambiente local

`./gradlew :<serviço>:bootRun` sobe o serviço com o perfil `local`, que aponta para o compose. Fora desse perfil, credenciais e endereços vêm só de variáveis de ambiente (não há senha padrão).

## Usuários de teste

Depois de registrar e ativar um tenant, `scripts/dev/seed-identity.sh <slug>` cria `admin@<slug>.dev` (tenant-admin, com MFA) e `usuario@<slug>.dev` (senha `Davinci@dev2026`). Detalhes em [docs/services/IDENTITY.md](../services/IDENTITY.md).

## Verificação antes de abrir PR

Rode uma por vez (a máquina de desenvolvimento tem memória limitada):

```bash
pnpm format:check && pnpm lint && pnpm typecheck && pnpm test && pnpm build
./gradlew build
```

`./gradlew spotlessApply` corrige a formatação Java; `pnpm format` corrige a do restante.

## Problemas comuns

- **Ferramentas do Kafka dentro do contêiner não conectam (`localhost:5602 could not be established`):** dentro da rede do compose, use o listener interno `kafka:19092`. O `localhost:9092` anuncia o endereço do host (`localhost:5602`), que só existe fora do contêiner. Exemplo: `docker exec erp-da-vinci-kafka-1 /opt/kafka/bin/kafka-console-consumer.sh --bootstrap-server kafka:19092 --topic tenant-registry.tenant.v1 --from-beginning`.

- **`Toolchain … does not provide JAVA_COMPILER`:** o JDK instalado é só runtime. O projeto já resolve sozinho via `gradle/gradle-daemon-jvm.properties`; confira se há acesso a `api.foojay.io`.
- **Download do Next.js expira no `pnpm install`:** o `pnpm-workspace.yaml` já define `fetchTimeout: 300000`. Não passe `--config.fetch-timeout` na linha de comando: no pnpm 11.3 o valor chega como texto e quebra os downloads.
- **OpenSearch não sobe (`vm.max_map_count`):** `sudo sysctl -w vm.max_map_count=262144`.
