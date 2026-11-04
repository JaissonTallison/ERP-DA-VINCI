import 'server-only';
import { createHash, randomBytes } from 'node:crypto';
import { cookies } from 'next/headers';
import { cache } from 'react';
import * as client from 'openid-client';
import type { TenantClaim } from './claims';
import { realmRoles } from './claims';
import { serverEnv } from './env';
import { oidc } from './oidc';
import { redis } from './redis';

/**
 * Sessão do BFF: os tokens ficam só no Redis; o navegador recebe um identificador aleatório em cookie httpOnly.
 * A chave no Redis é o hash do identificador, então um vazamento do Redis não dá cookies válidos.
 */
export const SESSION_COOKIE = '__Host-davinci-session';
export const LOGIN_COOKIE = '__Host-davinci-login';

export type Session = {
  user: { sub: string; name: string; email: string };
  tenant: TenantClaim;
  roles: string[];
  idToken: string;
  accessToken: string;
  refreshToken: string;
  accessExpiresAt: number;
  createdAt: number;
};

const REFRESH_MARGIN_MS = 60_000;

export const cookieOptions = {
  httpOnly: true,
  secure: true,
  sameSite: 'lax',
  path: '/',
} as const;

function key(sid: string): string {
  return `web:session:${createHash('sha256').update(sid).digest('base64url')}`;
}

export async function createSession(data: Session): Promise<string> {
  const sid = randomBytes(32).toString('base64url');
  const store = await redis();
  await store.set(key(sid), JSON.stringify(data), { EX: serverEnv().SESSION_IDLE_SECONDS });
  return sid;
}

export async function destroySession(sid: string): Promise<Session | null> {
  const store = await redis();
  const raw = await store.getDel(key(sid));
  return raw ? (JSON.parse(raw) as Session) : null;
}

/**
 * Sessão do navegador atual, renovando o access token se estiver perto de expirar.
 * Memorizada por requisição: layout e página leem o Redis uma vez só.
 */
export const currentSession = cache(async (): Promise<Session | null> => {
  const sid = (await cookies()).get(SESSION_COOKIE)?.value;
  return sid ? loadSession(sid) : null;
});

async function loadSession(sid: string): Promise<Session | null> {
  const env = serverEnv();
  const store = await redis();
  const raw = await store.get(key(sid));
  if (!raw) {
    return null;
  }
  const session = JSON.parse(raw) as Session;
  if (Date.now() - session.createdAt > env.SESSION_MAX_SECONDS * 1000) {
    await store.del(key(sid));
    return null;
  }
  if (session.accessExpiresAt - Date.now() > REFRESH_MARGIN_MS) {
    await store.expire(key(sid), env.SESSION_IDLE_SECONDS);
    return session;
  }
  return refresh(sid, session);
}

/**
 * Renova os tokens. O Keycloak gira o refresh token a cada uso, então só uma requisição por vez pode renovar:
 * as demais esperam a trava e releem a sessão já renovada.
 */
async function refresh(sid: string, session: Session): Promise<Session | null> {
  const env = serverEnv();
  const store = await redis();
  const lock = `${key(sid)}:refresh`;
  if (!(await store.set(lock, '1', { NX: true, PX: 10_000 }))) {
    for (let attempt = 0; attempt < 20; attempt++) {
      await new Promise((resolve) => setTimeout(resolve, 250));
      const raw = await store.get(key(sid));
      if (!raw) {
        return null;
      }
      const updated = JSON.parse(raw) as Session;
      if (updated.accessExpiresAt - Date.now() > REFRESH_MARGIN_MS) {
        return updated;
      }
    }
    return null;
  }
  try {
    const tokens = await client.refreshTokenGrant(await oidc(), session.refreshToken);
    const renewed: Session = {
      ...session,
      accessToken: tokens.access_token,
      refreshToken: tokens.refresh_token ?? session.refreshToken,
      idToken: tokens.id_token ?? session.idToken,
      accessExpiresAt: Date.now() + (tokens.expiresIn() ?? 60) * 1000,
      roles: realmRoles(tokens.access_token),
    };
    await store.set(key(sid), JSON.stringify(renewed), { EX: env.SESSION_IDLE_SECONDS });
    return renewed;
  } catch {
    // Sessão encerrada no Keycloak (logout em outro lugar, usuário desativado, tenant suspenso): encerra aqui também.
    await store.del(key(sid));
    return null;
  } finally {
    await store.del(lock);
  }
}
