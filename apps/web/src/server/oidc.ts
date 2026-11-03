import 'server-only';
import * as client from 'openid-client';
import { serverEnv } from './env';

const holder = globalThis as typeof globalThis & {
  davinciOidc?: Promise<client.Configuration> | undefined;
};

/** Configuração OIDC descoberta no Keycloak (cliente confidencial `davinci-web`). */
export function oidc(): Promise<client.Configuration> {
  const env = serverEnv();
  const issuer = new URL(env.OIDC_ISSUER);
  return (holder.davinciOidc ??= client
    .discovery(
      issuer,
      env.OIDC_CLIENT_ID,
      env.OIDC_CLIENT_SECRET,
      undefined,
      // HTTP só é aceito para o Keycloak local; em qualquer outro endereço exige-se HTTPS.
      issuer.protocol === 'http:' && issuer.hostname === 'localhost'
        ? { execute: [client.allowInsecureRequests] }
        : undefined,
    )
    .catch((error: unknown) => {
      holder.davinciOidc = undefined;
      throw error;
    }));
}

export function callbackUrl(): string {
  return new URL('/auth/callback', serverEnv().APP_URL).href;
}
