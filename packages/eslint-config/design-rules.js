/**
 * Barra no lint padrões visuais proibidos pelo guia de design
 * (docs/engineering/design-system.md): degradê, cantos maiores que 8 px,
 * sombras pesadas e cor hexadecimal solta em classes utilitárias.
 */
const forbidden = [
  { pattern: String.raw`\bbg-(linear|radial|conic|gradient)-`, reason: 'degradê' },
  { pattern: String.raw`\brounded-(xl|2xl|3xl|full)\b`, reason: 'canto acima de 8 px' },
  { pattern: String.raw`\bshadow-(lg|xl|2xl)\b`, reason: 'sombra pesada' },
  { pattern: String.raw`-\[#[0-9a-fA-F]{3,8}\]`, reason: 'cor hexadecimal solta (use token)' },
];

const selectors = forbidden.flatMap(({ pattern, reason }) => [
  {
    selector: `Literal[value=/${pattern}/]`,
    message: `Padrão visual proibido: ${reason}.`,
  },
  {
    selector: `TemplateElement[value.raw=/${pattern}/]`,
    message: `Padrão visual proibido: ${reason}.`,
  },
]);

export const designRules = [
  {
    files: ['**/*.tsx'],
    rules: { 'no-restricted-syntax': ['error', ...selectors] },
  },
];

export default designRules;
