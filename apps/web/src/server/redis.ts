import 'server-only';
import { createClient } from '@redis/client';
import { serverEnv } from './env';

function connect() {
  return createClient({ url: serverEnv().REDIS_URL })
    .on('error', (error: unknown) => console.error('Redis indisponível', error))
    .connect();
}

// Uma conexão por processo; em desenvolvimento sobrevive ao recarregamento de módulos.
const holder = globalThis as typeof globalThis & {
  davinciRedis?: ReturnType<typeof connect> | undefined;
};

export function redis(): ReturnType<typeof connect> {
  return (holder.davinciRedis ??= connect());
}
