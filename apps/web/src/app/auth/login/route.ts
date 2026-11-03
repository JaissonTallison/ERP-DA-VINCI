import { NextResponse } from 'next/server';
import * as client from 'openid-client';
import { redis } from '@/server/redis';
import { callbackUrl, oidc } from '@/server/oidc';
import { safeReturnTo } from '@/server/return-to';
import { LOGIN_COOKIE, cookieOptions } from '@/server/session';

export const dynamic = 'force-dynamic';

const LOGIN_TTL_SECONDS = 600;

/** Inicia o login: Authorization Code + PKCE, com state e nonce guardados no servidor. */
export async function GET(request: Request): Promise<Response> {
  const params = new URL(request.url).searchParams;
  const config = await oidc();
  const codeVerifier = client.randomPKCECodeVerifier();
  const state = client.randomState();
  const nonce = client.randomNonce();

  const store = await redis();
  await store.set(
    `web:login:${state}`,
    JSON.stringify({ codeVerifier, nonce, returnTo: safeReturnTo(params.get('returnTo')) }),
    { EX: LOGIN_TTL_SECONDS },
  );

  const authorization = client.buildAuthorizationUrl(config, {
    redirect_uri: callbackUrl(),
    scope: 'openid organization',
    code_challenge: await client.calculatePKCECodeChallenge(codeVerifier),
    code_challenge_method: 'S256',
    state,
    nonce,
    ...(params.get('prompt') === 'login' ? { prompt: 'login' } : {}),
  });

  const response = NextResponse.redirect(authorization);
  // Liga o login a este navegador: o callback só aceita o state que veio daqui (contra login CSRF).
  response.cookies.set(LOGIN_COOKIE, state, { ...cookieOptions, maxAge: LOGIN_TTL_SECONDS });
  return response;
}
