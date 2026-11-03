export type HealthStatus = {
  status: 'ok';
  service: 'web';
  version: string;
  checkedAt: string;
};

/** Estado de liveness do frontend. Não consulta dependências externas. */
export function getHealth(now: Date = new Date(), version = process.env.APP_VERSION): HealthStatus {
  return {
    status: 'ok',
    service: 'web',
    version: version && version.length > 0 ? version : '0.0.0-dev',
    checkedAt: now.toISOString(),
  };
}
