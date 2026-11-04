import { redirect } from 'next/navigation';
import type { ReactNode } from 'react';
import { PermissionsProvider } from '@/components/org/permissions';
import { MainNav } from '@/components/layout/main-nav';
import { TopBar } from '@/components/layout/top-bar';
import { UserMenu } from '@/components/layout/user-menu';
import { currentSession } from '@/server/session';

export const dynamic = 'force-dynamic';

/** Área autenticada do ERP: toda rota dentro de (app) exige sessão com um tenant ativo. */
export default async function AppLayout({ children }: { children: ReactNode }) {
  const session = await currentSession();
  if (!session) {
    redirect('/auth/login');
  }

  return (
    <>
      <TopBar>
        <UserMenu name={session.user.name} tenant={session.tenant.displayName} />
      </TopBar>
      <MainNav />
      <main className="px-4 py-6 sm:px-6">
        <PermissionsProvider canWrite={session.roles.includes('tenant-admin')}>
          {children}
        </PermissionsProvider>
      </main>
    </>
  );
}
