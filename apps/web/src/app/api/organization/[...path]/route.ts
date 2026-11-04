import { serverEnv } from '@/server/env';
import { currentSession } from '@/server/session';

export const dynamic = 'force-dynamic';

/** Só caminhos da API v1, com segmentos simples: impede que o proxy seja usado para outro destino. */
const SEGMENT = /^[A-Za-z0-9-]+$/;
const FORWARDED_HEADERS = ['content-type', 'if-match', 'idempotency-key', 'accept'];

/**
 * Proxy do BFF para o Organization: injeta o access token da sessão (que nunca vai ao navegador) e repassa a
 * resposta como veio, incluindo ETag e Problem Details.
 */
async function forward(request: Request, path: string[]): Promise<Response> {
  if (path[0] !== 'v1' || !path.every((segment) => SEGMENT.test(segment))) {
    return Response.json({ title: 'Caminho inválido' }, { status: 400 });
  }
  const env = serverEnv();
  if (request.method !== 'GET' && request.headers.get('origin') !== new URL(env.APP_URL).origin) {
    return Response.json({ title: 'Origem não permitida' }, { status: 403 });
  }
  const session = await currentSession();
  if (!session) {
    return Response.json({ title: 'Sessão expirada' }, { status: 401 });
  }
  const url = new URL(`/${path.join('/')}`, env.ORGANIZATION_API_URL);
  url.search = new URL(request.url).search;
  const headers = new Headers({ authorization: `Bearer ${session.accessToken}` });
  for (const name of FORWARDED_HEADERS) {
    const value = request.headers.get(name);
    if (value) {
      headers.set(name, value);
    }
  }
  const upstream = await fetch(url, {
    method: request.method,
    headers,
    body: request.method === 'GET' ? null : await request.text(),
    cache: 'no-store',
  });
  const responseHeaders = new Headers({ 'cache-control': 'no-store' });
  for (const name of ['content-type', 'etag', 'location']) {
    const value = upstream.headers.get(name);
    if (value) {
      responseHeaders.set(name, value);
    }
  }
  return new Response(upstream.body, { status: upstream.status, headers: responseHeaders });
}

type Context = { params: Promise<{ path: string[] }> };

export async function GET(request: Request, { params }: Context) {
  return forward(request, (await params).path);
}
export async function POST(request: Request, { params }: Context) {
  return forward(request, (await params).path);
}
export async function PUT(request: Request, { params }: Context) {
  return forward(request, (await params).path);
}
export async function PATCH(request: Request, { params }: Context) {
  return forward(request, (await params).path);
}
