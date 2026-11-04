import { serverEnv } from '@/server/env';
import { forward } from '@/server/service-proxy';

export const dynamic = 'force-dynamic';

type Context = { params: Promise<{ path: string[] }> };

async function handle(request: Request, { params }: Context) {
  return forward(request, (await params).path, serverEnv().GOVERNANCE_API_URL);
}

export { handle as GET, handle as POST, handle as PUT, handle as PATCH };
