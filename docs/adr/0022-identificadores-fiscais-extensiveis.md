# ADR-0022: Identificadores fiscais por esquema, com validadores plugáveis

- Estado: Aceito
- Data: 2026-11-03

## Contexto

Entidades e estabelecimentos têm identificadores fiscais diferentes por país (CNPJ e IE no Brasil, EIN nos EUA, NIF em Portugal, VAT na UE, RUT no Chile…), com formatos e dígitos verificadores próprios. Não é viável implementar todos agora, nem misturar regra fiscal no modelo.

## Decisão

1. `TaxIdentifier(scheme, value)`: `scheme` de um catálogo versionado (`BR_CNPJ`, `BR_CPF`, `BR_IE_<UF>`, `US_EIN`, `PT_NIF`, `EU_VAT`…), valor guardado **normalizado**.
2. **Validador por esquema** (interface `TaxIdentifierValidator`), registrado por esquema. Esquema sem validador aceita só a validação de formato básica do catálogo.
3. **Regras de coerência entre identificadores** (ex.: estabelecimento com a mesma raiz de CNPJ da entidade) também plugáveis por esquema.
4. **Primeira entrega:** `BR_CNPJ` com validação completa (incluindo o CNPJ alfanumérico previsto pela Receita Federal). Outros países entram conforme a demanda comercial, cada um com testes de referência.
5. Unicidade: o mesmo identificador vigente não se repete **dentro do tenant**; pode existir em tenants diferentes.
6. Regras tributárias (inscrições, regimes, obrigações) pertencem ao contexto **Tax**, não ao Organization.

## Consequências

- Um país novo = um validador + entradas no catálogo, sem mudar o modelo.
- O catálogo de esquemas vira contrato compartilhado com Tax e com o frontend (máscaras e mensagens).
