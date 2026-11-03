import { describe, expect, it } from 'vitest';
import { realmRoles, tenantFrom } from './claims';

const TENANT = '0b6f1c1e-6a0e-4d7c-9a51-3a7f4f1b2c3d';

describe('tenantFrom', () => {
  it('lê o tenant da organização do usuário', () => {
    const result = tenantFrom({
      organization: {
        acme: { id: 'org-1', tenant_id: [TENANT], display_name: ['Acme S.A.'] },
      },
    });

    expect(result).toEqual({
      ok: true,
      tenant: {
        alias: 'acme',
        organizationId: 'org-1',
        tenantId: TENANT,
        displayName: 'Acme S.A.',
      },
    });
  });

  it('recusa usuário sem organização (inclui tenant suspenso, que some do token)', () => {
    expect(tenantFrom({})).toEqual({ ok: false, reason: 'sem-organizacao' });
    expect(tenantFrom({ organization: ['acme'] })).toEqual({
      ok: false,
      reason: 'sem-organizacao',
    });
    expect(tenantFrom({ organization: { acme: { id: 'org-1' } } })).toEqual({
      ok: false,
      reason: 'sem-organizacao',
    });
  });

  it('recusa usuário em mais de uma organização', () => {
    expect(
      tenantFrom({
        organization: { a: { tenant_id: [TENANT] }, b: { tenant_id: [TENANT] } },
      }),
    ).toEqual({ ok: false, reason: 'varias-organizacoes' });
  });
});

describe('realmRoles', () => {
  const token = (payload: object) =>
    `h.${Buffer.from(JSON.stringify(payload)).toString('base64url')}.s`;

  it('extrai os papéis de realm do access token', () => {
    expect(
      realmRoles(token({ realm_access: { roles: ['tenant-admin', 'offline_access'] } })),
    ).toEqual(['tenant-admin', 'offline_access']);
  });

  it('devolve lista vazia para token sem papéis ou malformado', () => {
    expect(realmRoles(token({}))).toEqual([]);
    expect(realmRoles('não-é-jwt')).toEqual([]);
  });
});
