# ADR-0019: Hierarquias por lista de adjacência com vigência

- Estado: **Proposto**
- Data: 2026-11-03

## Contexto

Estruturas organizacionais e hierarquias de centros de custo precisam de consulta por subárvore, mudanças frequentes (reorganizações), histórico ("como era em D") e milhares de nós por tenant, sem ciclos. Participações societárias formam um grafo com percentuais, não uma árvore.

## Decisão

1. **Relações distintas**, cada uma com suas regras: pertença ao grupo, participação societária (grafo acíclico com percentuais), estrutura organizacional (árvore por estrutura) e hierarquia de centros de custo (árvore por entidade). Nenhuma hierarquia genérica única.
2. **Árvores: lista de adjacência em que cada vínculo tem vigência** (`parent`, `valid_from`, `valid_to`), lida por **CTE recursiva** filtrada por data.
3. **Sem ciclos e sem dois pais ao mesmo tempo:**
   - checagem de ciclo na aplicação, sob trava por estrutura (`pg_advisory_xact_lock`);
   - `EXCLUDE USING gist` contra vigências sobrepostas (extensão oficial `btree_gist`);
   - teste de propriedade com movimentos aleatórios.
4. **Otimização só com medição:** se necessário, tabela de fechamento derivada apenas para a data atual, reconstruída pelo serviço. A fonte da verdade continua sendo a adjacência.

## Alternativas consideradas

- **Closure table:** leitura ótima, mas mover custa proporcional à subárvore, e a vigência exigiria um fechamento por data.
- **`ltree` / caminho materializado:** leitura ótima, mas mover reescreve a subárvore, há um caminho por período e entra mais uma extensão.
- **Nested sets:** inviável com mudanças frequentes e vigência.

## Consequências

- Leituras de subárvore custam uma CTE recursiva (milissegundos no volume esperado; meta medida na etapa 4).
- A API navega por nível para árvores grandes.
