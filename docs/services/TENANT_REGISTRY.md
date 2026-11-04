# Tenant Registry

Serviço do **Global Control Plane**: fonte da verdade sobre tenants e células, com região de residência, célula, modelo de isolamento e ciclo de vida. Código em `services/tenant-registry`. Decisões: ADR-0003, ADR-0004, ADR-0014 e ADR-0015.

## Responsabilidades

- Catálogo de células por região, com capacidade máxima de tenants.
- Registro de tenants, com alocação automática na célula aberta menos ocupada da região e do isolamento pedidos.
- Ciclo de vida: `PROVISIONING → ACTIVE ⇄ SUSPENDED`.
- Publicação de eventos para os Regional Data Planes manterem o cache local de roteamento.

**Fora do escopo:** usuários (Identity), empresas e filiais do cliente (Organization) e dados pessoais ou transacionais. O encerramento de tenant (`DECOMMISSIONED`) entra com a saga de desprovisionamento.

## API v1

Contrato: [`contracts/http/tenant-registry/v1/openapi.yaml`](../../contracts/http/tenant-registry/v1/openapi.yaml).

| Método e rota | Escopo | Observação |
| --- | --- | --- |
| `POST /v1/cells` | write | Registra célula. Silo exige capacidade 1 |
| `GET /v1/cells?region=` | read | |
| `POST /v1/tenants` | write | Exige `Idempotency-Key` |
| `GET /v1/tenants?region=&status=&limit=&cursor=` | read | Ordenado por slug, cursor opaco |
| `GET /v1/tenants/{id}` | read | |
| `POST /v1/tenants/{id}/activate` | write | Idempotente |
| `POST /v1/tenants/{id}/suspend` | write | Corpo `{"reason": "..."}`; idempotente |
| `POST /v1/tenants/{id}/reactivate` | write | Idempotente |

Erros seguem a RFC 9457 (`application/problem+json`), com `code` estável: `tenant-not-found`, `slug-taken`, `no-cell-capacity`, `cell-exists`, `invalid-transition`, `idempotency-key-reused`, `concurrent-request`, `concurrent-update`, `invalid-field`, `invalid-argument`, `invalid-cursor`, `invalid-parameter`.

## Eventos

Contrato: [`contracts/events/tenant-registry/v1/`](../../contracts/events/tenant-registry/v1/asyncapi.yaml). Tópico `tenant-registry.tenant.v1`, com chave `tenantId`, no formato CloudEvents 1.0 estruturado (JSON).

| Tipo | Quando |
| --- | --- |
| `br.com.davinci.tenantregistry.tenant.registered.v1` | Tenant registrado e alocado |
| `…tenant.activated.v1` | Provisionamento concluído |
| `…tenant.suspended.v1` | Suspensão (com motivo) |
| `…tenant.reactivated.v1` | Reativação |

Cada evento traz o **estado completo** do tenant em `data`, mais `version`. Consumidores substituem a projeção e descartam versões já aplicadas.

## Garantias

| Garantia | Como |
| --- | --- |
| Evento nunca se perde nem é publicado sem o estado | Transactional Outbox: o evento é gravado na mesma transação; o relay publica a cada 500 ms com `acks=all` e produtor idempotente |
| Ordem por tenant | Relay publica em ordem de `sequence` e para no primeiro erro; chave Kafka = `tenantId` |
| Entrega pelo menos uma vez | Consumidor deduplica por `id` do evento e por `data.version` |
| Criação idempotente | `Idempotency-Key` + impressão digital SHA-256 do corpo; mesma chave com outro corpo → 409 |
| Capacidade de célula nunca excedida | Reserva atômica (`UPDATE … WHERE tenant_count < capacity`) + `CHECK` no banco |
| Atualização concorrente | Controle otimista por `version` → 409 `concurrent-update` |
| Várias instâncias do relay | `FOR UPDATE SKIP LOCKED` |

Eventos publicados ficam 7 dias no outbox, para auditoria e reprocessamento. A limpeza roda diariamente às 03:15.

## Segurança

- OAuth 2.0 resource server: valida emissor (`OIDC_ISSUER_URI`), assinatura (JWKS), validade e audiência `tenant-registry`.
- Escopos `tenant-registry.read` (consultas) e `tenant-registry.write` (tudo). Sem sessão e sem cookie.
- Público: só `/actuator/health/**` e `/actuator/info`. O SBOM (`/actuator/sbom/application`) exige escopo de leitura.
- `X-Correlation-Id` é propagado para logs, eventos e resposta.

**Isolamento entre tenants:** o Tenant Registry guarda dados de plataforma (o próprio cadastro de tenants), operados apenas pela equipe da plataforma. Por isso não usa Row-Level Security por tenant. O teste de isolamento do ADR-0004 vale para os serviços dos data planes.

## Observabilidade

- Traces e métricas via OpenTelemetry (OTLP em `localhost:5610` no ambiente local).
- Métrica `outbox.pending`: eventos aguardando publicação. Se crescer, o Kafka está indisponível ou lento.
- Health com probes de liveness e readiness: `/actuator/health/liveness` e `/actuator/health/readiness`.

## Rodar localmente

```bash
docker compose --profile identity up -d          # PostgreSQL, Redis, Kafka e Keycloak
./gradlew :tenant-registry:bootRun               # http://localhost:5630

TOKEN=$(curl -s -d grant_type=client_credentials \
  -d client_id=platform-operator-cli -d client_secret=platform_operator_dev_secret \
  http://localhost:5607/realms/platform/protocol/openid-connect/token | jq -r .access_token)

curl -s -X POST localhost:5630/v1/cells -H "Authorization: Bearer $TOKEN" -H 'Content-Type: application/json' \
  -d '{"region":"br-1","isolation":"pooled","name":"01","capacity":500}'

curl -s -X POST localhost:5630/v1/tenants -H "Authorization: Bearer $TOKEN" -H 'Content-Type: application/json' \
  -H "Idempotency-Key: $(uuidgen)" \
  -d '{"slug":"acme","displayName":"Acme S.A.","region":"br-1","isolation":"pooled"}'
```

O banco `tenant_registry` e o realm `platform` são criados na **primeira** subida dos volumes. Se o ambiente local já existia antes, recrie com `docker compose down -v` (apaga os dados locais).

## Variáveis de ambiente

> Credenciais e endereços de infraestrutura **não têm valor padrão**: sem a variável, o serviço não sobe (falha rápida, em vez de usar senha de desenvolvimento). Os valores de desenvolvimento ficam no perfil `local` (`application-local.yaml`), ativado automaticamente pelo `bootRun` e pelos testes. Para rodar o jar localmente: `java -jar <serviço>.jar --spring.profiles.active=local`.


| Variável | Valor no perfil `local` |
| --- | --- |
| `PORT` | `5630` |
| `DATABASE_URL` / `DATABASE_USERNAME` / `DATABASE_PASSWORD` | `jdbc:postgresql://localhost:5600/tenant_registry` / `tenant_registry` / `tenant_registry_dev` |
| `KAFKA_BOOTSTRAP_SERVERS` | `localhost:5602` |
| `KAFKA_TOPIC_REPLICAS` | `1` (produção: 3) |
| `OIDC_ISSUER_URI` | `http://localhost:5607/realms/platform` |
| `OTEL_EXPORTER_OTLP_TRACES_ENDPOINT` / `…_METRICS_ENDPOINT` | `http://localhost:5610/v1/traces` / `…/v1/metrics` |
| `TRACING_SAMPLING` | `1.0` |
