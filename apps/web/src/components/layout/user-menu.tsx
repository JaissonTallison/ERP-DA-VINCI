import { Button } from '@/components/ui/button';

/** Usuário logado e saída. O nome some no celular para a barra caber em 390 px. */
export function UserMenu({ name, tenant }: { name: string; tenant: string }) {
  return (
    <div className="flex items-center gap-4">
      <div className="hidden text-right sm:block">
        <p className="text-sm font-semibold text-brand">{name}</p>
        <p className="text-xs text-text-muted">{tenant}</p>
      </div>
      <form action="/auth/logout" method="post">
        <Button type="submit" variant="secondary">
          Sair
        </Button>
      </form>
    </div>
  );
}
