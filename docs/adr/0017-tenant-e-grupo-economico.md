# ADR-0017: Tenant contém grupos econômicos; não há agregado "Organization"

- Estado: **Proposto**
- Data: 2026-11-03

## Contexto

O Organization Management precisa decidir se um tenant representa um grupo econômico inteiro, uma organização específica ou outra coisa. "Organização" já significa a projeção do tenant no Keycloak (ADR-0016). O tenant também é a unidade de residência de dados (ADR-0003).

## Decisão

1. **Tenant = cliente contratante e fronteira de isolamento e residência.** Dentro dele, o Organization modela **um ou mais grupos econômicos**:
   - o caso comum é um grupo;
   - escritórios contábeis e de serviços compartilhados (BPO) que atendem empresas sem relação entre si têm vários grupos.
2. **Uma entidade jurídica pertence a no máximo um grupo por data**, com histórico (fusões e aquisições). Pode ficar sem grupo (empresa avulsa).
3. **Não existe agregado "Organization".** O papel de "empresa do cliente" é do grupo econômico; empresa única = grupo com uma entidade. O termo fica reservado ao nome do contexto e do serviço.
4. **Residência:** todas as entidades de um tenant têm os dados na região do tenant. Entidades de outros países podem ficar no mesmo tenant quando a transferência for permitida por contrato e lei. Quando a lei exigir residência local (ex.: UE), o grupo se divide em **um tenant por região**. A consolidação entre eles, por dados agregados e com base legal, fica para uma etapa futura ("federação de tenants" no Control Plane).

## Alternativas consideradas

- **Tenant = grupo econômico (1:1):** simples, mas não atende BPO nem reestruturações em que entidades mudam de grupo dentro do mesmo cliente.
- **Tenant = entidade jurídica:** isolamento excessivo; grupos virariam relações entre tenants desde o início.
- **Agregado "Organization" acima do grupo:** camada sem regra própria e com nome ambíguo.

## Consequências

- Todas as APIs e tabelas do Organization levam `tenant_id`; grupos e entidades são filhos do tenant.
- Grupos que exigem residência em várias regiões dependem da federação de tenants, que ainda não existe.
