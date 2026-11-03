import { NextResponse } from 'next/server';
import * as client from 'openid-client';
import { realmRoles, tenantFrom } from '@/server/claims';
import { serverEnv } from '@/server/env';
import { callbackUrl, oidc } from '@/server/oidc';
import { redis } from '@/server/redis';
import { LOGIN_COOKIE, SESSION_COOKIE, cookieOptions, createSession } from '@/server/session';

export const dynamic = 'force-dynamic';

type PendingLogin = { codeVerifier: string; nonce: string; returnTo: string };

function failure(reason: string): NextResponse {
  const response = NextResponse.redirect(
    new URL(`/auth/erro?motivo=${reason}`, serverEnv().APP_URL),
    303,
  );
  response.cookies.delete(LOGIN_COOKIE);
  return response;
}

/** Conclui o login: valida state, PKCE e ID token, exige um tenant ativo e cria a sessão no servidor. */
export async function GET(request: Request): Promise<Response> {
  const received = new URL(request.url);
  const state = received.searchParams.get('state');
  const bound = request.headers
    .get('cookie')
    ?.split(';')
    .map((part) => part.trim())
    .find((part) => part.startsWith(`${LOGIN_COOKIE}=`))
    ?.slice(LOGIN_COOKIE.length + 1);
  if (!state || state !== bound) {
    return failure('login');
  }

  const store = await redis();
  const raw = await store.getDel(`web:login:${state}`);
  if (!raw) {
    return failure('login');
  }
  const pending = JSON.parse(raw) as PendingLogin;

  // A URL validada precisa bater com o redirect_uri registrado, independentemente de proxies na frente.
  const current = new URL(callbackUrl());
  current.search = received.search;

  let tokens: Awaited<ReturnType<typeof client.authorizationCodeGrant>>;
  try {
    tokens = await client.authorizationCodeGrant(await oidc(), current, {
      pkceCodeVerifier: pending.codeVerifier,
      expectedState: state,
      expectedNonce: pending.nonce,
      idTokenExpected: true,
    });
  } catch (error) {
    console.error('Falha no retorno do login', error);
    return failure('login');
  }

  const claims = tokens.claims();
  if (!claims || !tokens.id_token || !tokens.refresh_token) {
    return failure('login');
  }
  const tenant = tenantFrom(claims);
  if (!tenant.ok) {
    return failure(tenant.reason);
  }

  const now = Date.now();
  const sid = await createSession({
    user: {
      sub: claims.sub,
      name: typeof claims['name'] === 'string' ? claims['name'] : '',
      email: typeof claims['email'] === 'string' ? claims['email'] : '',
    },
    tenant: tenant.tenant,
    roles: realmRoles(tokens.access_token),
    idToken: tokens.id_token,
    accessToken: tokens.access_token,
    refreshToken: tokens.refresh_token,
    accessExpiresAt: now + (tokens.expiresIn() ?? 60) * 1000,
    createdAt: now,
  });

  const response = NextResponse.redirect(new URL(pending.returnTo, serverEnv().APP_URL), 303);
  response.cookies.set(SESSION_COOKIE, sid, {
    ...cookieOptions,
    maxAge: serverEnv().SESSION_MAX_SECONDS,
  });
  response.cookies.delete(LOGIN_COOKIE);
  return response;
}
