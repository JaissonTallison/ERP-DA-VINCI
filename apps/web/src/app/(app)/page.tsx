import { redirect } from 'next/navigation';
import { currentSession } from '@/server/session';

const ROLE_LABELS: Record<string, string> = {
  'tenant-admin': 'Administrador',
  'tenant-user': 'Usuário',
};

export default async function HomePage() {
  // Mesma leitura do layout: currentSession é memorizada por requisição.
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
    </>
  );
}
