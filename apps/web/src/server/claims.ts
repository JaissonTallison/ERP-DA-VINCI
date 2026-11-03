/** Tenant do usuário, lido do claim `organization` emitido pelo Keycloak (realm davinci). */
export type TenantClaim = {
  alias: string;
  organizationId: string;
  tenantId: string;
  displayName: string;
};

export type TenantResult =
  | { ok: true; tenant: TenantClaim }
  | { ok: false; reason: 'sem-organizacao' | 'varias-organizacoes' };

type OrganizationEntry = { id?: unknown; tenant_id?: unknown; display_name?: unknown };

function first(value: unknown): string | undefined {
  const item = Array.isArray(value) ? value[0] : value;
  return typeof item === 'string' && item.length > 0 ? item : undefined;
}

/**
 * Exige exatamente uma organização com `tenant_id`. Organização desabilitada (tenant suspenso) não aparece no
 * token, então o usuário cai em `sem-organizacao` mesmo que o Keycloak tenha autenticado.
 */
export function tenantFrom(claims: Record<string, unknown>): TenantResult {
  const organization = claims['organization'];
  if (!organization || typeof organization !== 'object' || Array.isArray(organization)) {
    return { ok: false, reason: 'sem-organizacao' };
  }
  const entries = Object.entries(organization as Record<string, OrganizationEntry>).filter(
    ([, value]) => value && typeof value === 'object' && first(value.tenant_id),
  );
  if (entries.length === 0) {
    return { ok: false, reason: 'sem-organizacao' };
  }
  if (entries.length > 1) {
    return { ok: false, reason: 'varias-organizacoes' };
  }
  const [alias, value] = entries[0] as [string, OrganizationEntry];
  return {
    ok: true,
    tenant: {
      alias,
      organizationId: first(value.id) ?? '',
      tenantId: first(value.tenant_id) ?? '',
      displayName: first(value.display_name) ?? alias,
    },
  };
}

/** Papéis de realm do access token. Uso só para a interface: as APIs validam o token por conta própria. */
export function realmRoles(accessToken: string): string[] {
  const payload = accessToken.split('.')[1];
  if (!payload) {
    return [];
  }
  try {
    const decoded: unknown = JSON.parse(Buffer.from(payload, 'base64url').toString('utf8'));
    const roles = (decoded as { realm_access?: { roles?: unknown } }).realm_access?.roles;
    return Array.isArray(roles) ? roles.filter((r): r is string => typeof r === 'string') : [];
  } catch {
    return [];
  }
}
