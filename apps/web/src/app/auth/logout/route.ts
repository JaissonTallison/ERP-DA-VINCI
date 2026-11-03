import { NextResponse } from 'next/server';
import * as client from 'openid-client';
import { cookies } from 'next/headers';
import { serverEnv } from '@/server/env';
import { oidc } from '@/server/oidc';
import { SESSION_COOKIE, destroySession } from '@/server/session';

export const dynamic = 'force-dynamic';

/** Encerra a sessão aqui e no Keycloak (RP-Initiated Logout). Só aceita POST da própria aplicação. */
export async function POST(request: Request): Promise<Response> {
  const env = serverEnv();
  if (request.headers.get('origin') !== new URL(env.APP_URL).origin) {
    return new Response('Origem não permitida', { status: 403 });
  }

  const sid = (await cookies()).get(SESSION_COOKIE)?.value;
  const session = sid ? await destroySession(sid) : null;

  const destination = session
    ? client.buildEndSessionUrl(await oidc(), {
        id_token_hint: session.idToken,
        post_logout_redirect_uri: new URL('/', env.APP_URL).href,
      })
    : new URL('/', env.APP_URL);

  const response = NextResponse.redirect(destination, 303);
  response.cookies.delete(SESSION_COOKIE);
  return response;
}
