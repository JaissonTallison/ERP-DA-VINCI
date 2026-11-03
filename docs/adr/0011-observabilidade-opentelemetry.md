# ADR-0011: Observabilidade com OpenTelemetry e stack Grafana

- Estado: Aceito
- Data: 2026-11-03

## Decisão

- Instrumentação exclusivamente por OpenTelemetry (SDK/agent Java e Next.js), exportando OTLP para um Collector por célula.
- Backends: Prometheus (métricas), Loki (logs), Tempo (traces), Grafana (painéis e alertas).
- Atributos obrigatórios: `service.name`, `tenant_id`, `cell_id`, `region`, `correlation_id`.
- Logs em JSON, sem dados pessoais em claro.
- Localmente, a imagem `grafana/otel-lgtm` (perfil `observability` do compose) substitui a stack completa.

## Consequências

- Cardinalidade de `tenant_id` em métricas precisa de controle (usar em traces/logs livremente; em métricas, só nas agregadas por tenant definidas).
