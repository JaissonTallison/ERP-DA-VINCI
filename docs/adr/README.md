# Registros de decisão arquitetural (ADR)

Cada decisão estrutural vira um ADR curto, numerado e imutável. Para mudar uma decisão, crie um novo ADR que _substitui_ o anterior e atualize o estado do antigo.

Estados: **Proposto** (aguarda aprovação) · **Aceito** · **Substituído por ADR-XXXX** · **Descartado**.

| ADR                                                | Título                                                                  | Estado                                |
| -------------------------------------------------- | ----------------------------------------------------------------------- | ------------------------------------- |
| [0001](0001-registrar-decisoes-em-adr.md)          | Registrar decisões em ADR                                               | Aceito                                |
| [0002](0002-monorepo-poliglota.md)                 | Monorepo poliglota: pnpm/Turborepo + Gradle                             | Aceito                                |
| [0003](0003-control-plane-data-planes-celulas.md)  | Global Control Plane, Regional Data Planes e células                    | Aceito                                |
| [0004](0004-modelos-de-isolamento.md)              | Modelos de isolamento pooled, bridge e silo                             | Aceito                                |
| [0005](0005-microsservicos-por-bounded-context.md) | Microsserviços por bounded context, criados sob demanda                 | Aceito                                |
| [0006](0006-stack-backend.md)                      | Backend: Java 25, Spring Boot 4.1, Gradle 9 com convenções              | Aceito                                |
| [0007](0007-stack-frontend.md)                     | Frontend: Next.js 16, TypeScript 6.0, ESLint 9                          | Aceito                                |
| [0008](0008-eventos-outbox-contratos.md)           | Integração por eventos com Transactional Outbox e contratos versionados | Aceito                                |
| [0009](0009-identidade-e-autorizacao.md)           | Identidade federada via Keycloak e autorização RBAC + ABAC              | Aceito (motor de políticas em aberto) |
| [0010](0010-persistencia-poliglota.md)             | Persistência poliglota e banco por serviço                              | Aceito                                |
| [0011](0011-observabilidade-opentelemetry.md)      | Observabilidade com OpenTelemetry e stack Grafana                       | Aceito                                |
| [0012](0012-cadeia-de-suprimentos-de-software.md)  | Segurança da cadeia de suprimentos de software                          | Aceito                                |
| [0013](0013-infraestrutura-de-producao.md)         | Estratégia de infraestrutura de produção                                | **Proposto**                          |

Modelo: [template.md](template.md).
