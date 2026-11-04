'use client';

import Link from 'next/link';
import { usePathname } from 'next/navigation';

const LINKS = [
  { href: '/', label: 'Início' },
  { href: '/empresas', label: 'Empresas' },
  { href: '/estrutura', label: 'Estrutura' },
  { href: '/reorganizacoes', label: 'Reorganizações' },
  { href: '/centros-de-custo', label: 'Centros de custo' },
  { href: '/societario', label: 'Societário' },
];

/** Navegação principal: trilho cinza-gelo com a opção ativa em branco. */
export function MainNav() {
  const pathname = usePathname();
  return (
    <nav
      aria-label="Principal"
      className="overflow-x-auto border-b border-border bg-canvas px-4 sm:px-6"
    >
      <ul className="flex gap-1 py-2">
        {LINKS.map((link) => {
          const active = link.href === '/' ? pathname === '/' : pathname.startsWith(link.href);
          return (
            <li key={link.href}>
              <Link
                href={link.href}
                aria-current={active ? 'page' : undefined}
                className={`block whitespace-nowrap rounded-md px-3 py-1.5 text-sm font-semibold ${
                  active ? 'bg-surface text-brand shadow-card' : 'text-text-muted hover:text-brand'
                }`}
              >
                {link.label}
              </Link>
            </li>
          );
        })}
      </ul>
    </nav>
  );
}
