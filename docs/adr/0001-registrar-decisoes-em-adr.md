# ADR-0001: Registrar decisões em ADR

- Estado: Aceito
- Data: 2026-11-03

## Contexto

A plataforma terá vida longa, muitas equipes e auditorias de clientes regulados. Decisões estruturais precisam ser rastreáveis.

## Decisão

Toda decisão que afete mais de um serviço, a segurança, os dados ou a operação é registrada em `docs/adr/` no formato do `template.md`, revisada em PR e nunca editada depois de aceita (apenas substituída).

## Consequências

- PR que muda arquitetura sem ADR é recusado na revisão.
- O histórico explica o "porquê" para quem chegar depois.
