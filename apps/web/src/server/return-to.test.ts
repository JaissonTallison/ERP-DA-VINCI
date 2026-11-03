import { describe, expect, it } from 'vitest';
import { safeReturnTo } from './return-to';

describe('safeReturnTo', () => {
  it('aceita caminhos internos', () => {
    expect(safeReturnTo('/financeiro/titulos?pagina=2')).toBe('/financeiro/titulos?pagina=2');
  });

  it.each([
    null,
    undefined,
    '',
    'https://evil.com',
    '//evil.com',
    '/\\evil.com',
    'evil',
    '/auth/logout',
  ])('troca %s pela página inicial', (value) => {
    expect(safeReturnTo(value)).toBe('/');
  });
});
