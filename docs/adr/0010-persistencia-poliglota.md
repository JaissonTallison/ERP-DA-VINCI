# ADR-0010: Persistência poliglota e banco por serviço

- Estado: Aceito
- Data: 2026-11-03

## Decisão

| Tecnologia        | Papel                                           | Regra                                                                    |
| ----------------- | ----------------------------------------------- | ------------------------------------------------------------------------ |
| PostgreSQL        | Fonte da verdade transacional                   | Um banco por serviço; migrações Flyway do próprio serviço                |
| Redis             | Cache, rate limiting, locks curtos              | Nunca fonte da verdade; chaves prefixadas pelo tenant                    |
| Kafka             | Eventos de domínio e integração                 | Ver ADR-0008                                                             |
| OpenSearch        | Busca textual                                   | Projeção reconstruível a partir de eventos                               |
| ClickHouse        | Analytics operacional                           | Alimentado por eventos; sem escrita de negócio                           |
| Apache Iceberg    | Lakehouse histórico e auditoria analítica       | Tabelas sobre object storage; catálogo definido com o contexto Analytics |
| Object storage S3 | Documentos, anexos, backups, auditoria imutável | Buckets por região; Object Lock para auditoria                           |

**Ambiente local:** o MinIO deixou de publicar imagens Docker da edição comunitária, então o S3 local usa **SeaweedFS** (Apache 2.0). O código usa apenas a API S3, sem acoplamento ao fornecedor.

## Consequências

- Nenhum serviço consulta o banco de outro; relatórios cruzados vêm de Analytics.
- Toda projeção derivada precisa de rotina de reconstrução documentada.
