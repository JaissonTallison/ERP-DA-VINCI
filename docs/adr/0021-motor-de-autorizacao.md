# ADR-0021: Motor de autorização — Cedar embutido, condicionado a prova de conceito

- Estado: Aceito, condicionado à prova de conceito (decide o item "motor de políticas" em aberto no ADR-0009)
- Data: 2026-11-03

## Contexto

O Organization introduz escopos de permissão hierárquicos (tenant → grupo → entidade → estabelecimento; subárvore de estrutura) que valerão para todos os domínios. Opções avaliadas: Cedar, OPA/Rego, OpenFGA e implementação própria. A comparação completa está em `docs/domains/organization/ORGANIZATION_SECURITY.md`.

## Decisão (proposta)

1. **Cedar como motor-alvo**, embutido em cada serviço (biblioteca `cedar-java`):
   - RBAC + ABAC com hierarquia de entidades nativa;
   - decisão no processo, em microssegundos;
   - nenhum serviço extra com estado por célula;
   - políticas tipadas e analisáveis.
2. **Prova de conceito obrigatória antes de aceitar:**
   - binding nativo em Linux x86_64 e ARM64;
   - p99 < 1 ms a 10 mil decisões/s;
   - pré-filtro de listagens por escopo;
   - registro da decisão na auditoria.

   Se falhar, a alternativa é OPA com WASM embutido.

3. **Até lá:** interface `PolicyDecisionPoint` em cada serviço, com implementação provisória pelos papéis do token (`tenant-admin` escreve, `tenant-user` lê).
4. **Responsabilidades:**
   - o Governance será dono de papéis, atribuições, SoD e políticas;
   - os serviços decidem localmente;
   - o Organization fornece a hierarquia (ancestrais na data).

## Alternativas consideradas

- **OPA:** processo extra por instância; Rego difícil de revisar.
- **OpenFGA:** exige replicar toda a estrutura organizacional num servidor com estado por célula; reavaliar para compartilhamento por documento.
- **Própria:** só como implementação provisória.

## Consequências

- A troca do provisório pelo Cedar não altera os casos de uso, só a implementação da interface.
- Biblioteca nativa na imagem dos serviços (a prova de conceito verifica).
