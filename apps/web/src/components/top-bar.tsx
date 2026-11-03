import Image from 'next/image';
import type { ReactNode } from 'react';

/** Barra superior: logo à esquerda e, opcionalmente, conteúdo à direita (usuário, ações). */
export function TopBar({ children }: { children?: ReactNode }) {
  return (
    <header className="bg-surface shadow-bar">
      <div className="flex h-18 items-center justify-between gap-4 px-4 sm:px-6">
        <Image
          src="/brand/logo.svg"
          alt="daVINCI ERP Enterprise"
          width={160}
          height={60}
          priority
          unoptimized
        />
        {children}
      </div>
    </header>
  );
}
