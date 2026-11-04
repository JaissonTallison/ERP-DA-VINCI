'use client';

import { createContext, useContext, type ReactNode } from 'react';

const CanWrite = createContext(false);

/** Só esconde o que daria 403: quem decide de verdade é o serviço Organization. */
export function PermissionsProvider({
  canWrite,
  children,
}: {
  canWrite: boolean;
  children: ReactNode;
}) {
  return <CanWrite.Provider value={canWrite}>{children}</CanWrite.Provider>;
}

export function useCanWrite(): boolean {
  return useContext(CanWrite);
}
