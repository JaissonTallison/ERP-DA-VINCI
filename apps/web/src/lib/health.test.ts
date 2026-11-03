import { describe, expect, it } from 'vitest';
import { getHealth } from './health';

describe('getHealth', () => {
  const now = new Date('2026-11-03T12:00:00.000Z');

  it('informa a versão recebida', () => {
    expect(getHealth(now, '1.2.3')).toEqual({
      status: 'ok',
      service: 'web',
      version: '1.2.3',
      checkedAt: '2026-11-03T12:00:00.000Z',
    });
  });

  it('usa versão de desenvolvimento quando ausente ou vazia', () => {
    expect(getHealth(now, undefined).version).toBe('0.0.0-dev');
    expect(getHealth(now, '').version).toBe('0.0.0-dev');
  });
});
