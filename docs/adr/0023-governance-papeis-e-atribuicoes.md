# ADR-0023: Governance como dono de papéis, atribuições e segregação de funções

- Estado: Proposto
- Data: 2026-11-04
- Decisores: responsável pelo produto

## Contexto

O ADR-0021 define que o Governance será dono de papéis, atribuições, segregação de funções (SoD) e políticas, e que cada serviço decide localmente. O serviço ainda não existia, e isso bloqueava a autorização definitiva do Organization.

## Decisão

1. Serviço `governance` (Data Plane, porta 5633, banco próprio com RLS forçado), criado sobre o bounded context já aprovado no catálogo.
2. Papel = conjunto de permissões no formato `contexto:recurso:acao`, por tenant.
3. Atribuição = usuário (`sub` do token) + papel + escopo (`TENANT`, `ECONOMIC_GROUP`, `LEGAL_ENTITY`, `ESTABLISHMENT`, `UNIT`) + vigência `[início, fim)`.
4. SoD = par de permissões que a mesma pessoa não pode ter em períodos que se encontram. É conferida ao conceder, ao editar papel e ao criar regra, sob trava por tenant.
5. Ninguém concede acesso a si mesmo (regra no domínio e `CHECK` no banco).
6. O próprio Governance é administrado pelo papel `tenant-admin` do token, para não depender de si mesmo.
7. Eventos `governance.role.v1` e `governance.assignment.v1` são a fonte para os serviços decidirem localmente.

## Alternativas consideradas

- Papéis no Keycloak: não têm escopo por entidade nem vigência.
- Identificadores de escopo validados contra o Organization de forma síncrona: criaria dependência em tempo de escrita; a validação fica para a projeção por eventos.

## Consequências

- Os serviços ainda não consomem as atribuições: seguem na autorização provisória até a prova de conceito do Cedar (ADR-0021).
- O identificador de escopo não é conferido contra o Organization nesta entrega.
- Workflows genéricos de aprovação e políticas Cedar ficam para depois.
