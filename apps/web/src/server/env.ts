import 'server-only';
import { z } from 'zod';

/**
 * Configuração do servidor, validada na primeira leitura (não no build).
 * Em desenvolvimento há padrões apontando para o compose.yaml; em produção tudo é obrigatório.
 */
const dev = process.env.NODE_ENV === 'development';

const schema = z.object({
  APP_URL: dev ? z.url().default('http://localhost:5620') : z.url(),
  OIDC_ISSUER: dev ? z.url().default('http://localhost:5607/realms/davinci') : z.url(),
  OIDC_CLIENT_ID: z.string().min(1).default('davinci-web'),
  OIDC_CLIENT_SECRET: dev ? z.string().min(1).default('davinci_web_dev_secret') : z.string().min(1),
  REDIS_URL: dev ? z.url().default('redis://localhost:5601') : z.url(),
  /** API do serviço Organization; o navegador nunca fala com ela direto, só por /api/organization. */
  ORGANIZATION_API_URL: dev ? z.url().default('http://localhost:5632') : z.url(),
  /** Inatividade máxima da sessão, igual à do Keycloak (ssoSessionIdleTimeout). */
  SESSION_IDLE_SECONDS: z.coerce.number().int().positive().default(1800),
  /** Duração máxima da sessão, igual à do Keycloak (ssoSessionMaxLifespan). */
  SESSION_MAX_SECONDS: z.coerce.number().int().positive().default(36000),
});

export type ServerEnv = z.infer<typeof schema>;

let cached: ServerEnv | undefined;

export function serverEnv(): ServerEnv {
  cached ??= schema.parse(process.env);
  return cached;
}
