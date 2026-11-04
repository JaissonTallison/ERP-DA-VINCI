# Design System

Fonte dos tokens: `packages/design-tokens/src/tokens.css` (Tailwind v4 `@theme`). Mudar ali muda o sistema inteiro.

## Marca

Logos, símbolo e ícones por plataforma ficam em [`brand/`](../../brand/README.md) e são gerados por script. No app web, use os arquivos sincronizados em `apps/web/public/brand/`. No cabeçalho, a logo horizontal tem 160 px de largura (o mínimo do guia da marca), numa barra de 72 px.

## Princípios

Visual de produto corporativo consolidado: sóbrio, denso na medida certa, sem enfeite. Quem usa o ERP oito horas por dia precisa de clareza, não de decoração.

## Tokens (2026-11-03)

| Token        | Valor                                      | Uso                                              |
| ------------ | ------------------------------------------ | ------------------------------------------------ |
| `brand`      | `#102A43`                                  | Marca, menu, títulos                             |
| `primary`    | `#0F766E`                                  | Botão principal, estado positivo                 |
| `marker`     | `#F59E0B`                                  | Apenas marcador visual (nunca texto)             |
| `attention`  | `#B45309`                                  | Texto de atenção                                 |
| `danger`     | `#B91C1C`                                  | Texto de erro/vencido                            |
| `canvas`     | `#F4F7FA`                                  | Fundo da aplicação e dos campos                  |
| `surface`    | `#FFFFFF`                                  | Cards, barra superior                            |
| `text-muted` | `#334155`                                  | Texto secundário                                 |
| Cantos       | 8 px (`rounded-md`); 2 px só em checkbox   |                                                  |
| Fonte        | IBM Plex Sans                              | Escala 12,5 · 14 · **15** · 17 · 19 · 23 · 28 px |
| Campos       | 36 px de altura (44 px em telas de acesso) |                                                  |

## Proibido (barrado no lint quando possível)

- Degradê; cantos acima de 8 px; sombras pesadas; cor hexadecimal solta em classe — **lint**.
- Ícone em círculo colorido, emoji, ponto colorido antes de status, barrinha de progresso em lista, etiqueta colorida para todo status, faixa de números gigantes em tela de trabalho, "Bem-vindo de volta!", texto de exemplo — **revisão de PR**.

## Padrões de componente

- Status é texto colorido, sem caixa nem ponto.
- Tabelas zebradas (`brand-zebra`), cabeçalho 14 px, viram cartões no celular.
- Cabeçalho de página: título e ações; abaixo, linha em vermelho só quando algo pede ação.
- Responsivo sem rolagem lateral em 390, 820, 1366 e 2560 px.

## Histórico

- 2026-11-03 — Tokens iniciais e regras de lint criados na Fase 1.
- 2026-11-03 — Marca daVINCI ERP Enterprise organizada em `brand/` e aplicada ao cabeçalho, favicon, manifesto PWA e Open Graph.
