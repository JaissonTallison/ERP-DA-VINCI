import { redirect } from 'next/navigation';
import { TopBar } from '@/components/top-bar';
import { currentSession } from '@/server/session';

export const dynamic = 'force-dynamic';

const ROLE_LABELS: Record<string, string> = {
  'tenant-admin': 'Administrador',
  'tenant-user': 'Usuário',
};

export default async function HomePage() {
  const session = await currentSession();
  if (!session) {
    redirect('/auth/login');
  }

  const roles = session.roles.flatMap((role) => ROLE_LABELS[role] ?? []);
  const rows: [string, string][] = [
    ['Usuário', session.user.name || '—'],
    ['E-mail', session.user.email || '—'],
    ['Empresa', session.tenant.displayName],
    ['Identificador do tenant', session.tenant.tenantId],
    ['Papéis', roles.length > 0 ? roles.join(', ') : 'Nenhum papel atribuído'],
  ];

  return (
    <>
      <TopBar>
        <div className="flex items-center gap-4">
          <div className="hidden text-right sm:block">
            <p className="text-sm font-semibold text-brand">{session.user.name}</p>
            <p className="text-xs text-text-muted">{session.tenant.displayName}</p>
          </div>
          <form action="/auth/logout" method="post">
            <button
              type="submit"
              className="h-field rounded-md border border-border bg-surface px-4 text-sm font-semibold text-brand hover:bg-canvas"
            >
              Sair
            </button>
          </form>
        </div>
      </TopBar>
      <main className="px-4 py-6 sm:px-6">
        <h1 className="text-3xl font-bold text-brand">Início</h1>
        <section className="mt-6 rounded-md border border-border bg-surface p-5 shadow-card">
          <h2 className="text-xl font-bold">Sua sessão</h2>
          <dl className="mt-4 grid gap-x-6 gap-y-4 sm:grid-cols-[220px_1fr] sm:gap-y-3">
            {rows.map(([label, value]) => (
              <div key={label} className="sm:contents">
                <dt className="text-sm text-text-muted">{label}</dt>
                <dd className="break-all">{value}</dd>
              </div>
            ))}
          </dl>
        </section>
        <p className="mt-6 text-sm text-text-muted">
          Nenhum módulo de negócio está disponível nesta fase.
        </p>
      </main>
    </>
  );
}
