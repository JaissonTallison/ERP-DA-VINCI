'use client';

import type { ReactNode, SelectHTMLAttributes, InputHTMLAttributes } from 'react';
import { STATUS_LABELS } from '@/lib/organization';

/** Peças comuns das telas do Organization, seguindo docs/development/DESIGN_SYSTEM.md. */

export function PageHeader({ title, actions }: { title: string; actions?: ReactNode }) {
  return (
    <div className="flex flex-wrap items-center justify-between gap-3">
      <h1 className="text-3xl font-bold text-brand">{title}</h1>
      {actions ? <div className="flex flex-wrap gap-2">{actions}</div> : null}
    </div>
  );
}

export function Card({ title, children }: { title?: string; children: ReactNode }) {
  return (
    <section className="mt-6 rounded-md border border-border bg-surface p-5 shadow-card">
      {title ? <h2 className="mb-4 text-xl font-bold text-brand">{title}</h2> : null}
      {children}
    </section>
  );
}

export function Filters({ children }: { children: ReactNode }) {
  return <div className="mt-6 flex flex-wrap items-end gap-4">{children}</div>;
}

const FIELD =
  'h-field w-full rounded-md border border-border bg-canvas px-3 text-base focus:bg-surface focus:outline-2 focus:outline-primary';

export function SelectField({
  label,
  children,
  ...props
}: SelectHTMLAttributes<HTMLSelectElement> & { label: string }) {
  return (
    <label className="flex min-w-56 flex-col gap-1 text-sm text-text-muted">
      {label}
      <select className={FIELD} {...props}>
        {children}
      </select>
    </label>
  );
}

export function InputField({
  label,
  ...props
}: InputHTMLAttributes<HTMLInputElement> & { label: string }) {
  return (
    <label className="flex min-w-44 flex-col gap-1 text-sm text-text-muted">
      {label}
      <input className={FIELD} {...props} />
    </label>
  );
}

const STATUS_COLOR: Record<string, string> = {
  ACTIVE: 'text-primary',
  APPLIED: 'text-primary',
  PENDING_APPROVAL: 'text-attention',
  SUSPENDED: 'text-attention',
  BLOCKED: 'text-attention',
  INACTIVE: 'text-attention',
  REJECTED: 'text-danger',
};

/** Status é texto colorido, sem caixa nem ponto. */
export function StatusText({ status }: { status: string }) {
  return (
    <span className={STATUS_COLOR[status] ?? 'text-text-muted'}>
      {STATUS_LABELS[status] ?? status}
    </span>
  );
}

export function Message({ error, empty }: { error?: unknown; empty?: string | undefined }) {
  if (error) {
    return (
      <p className="mt-4 text-danger">
        {error instanceof Error ? error.message : 'Não foi possível carregar.'}
      </p>
    );
  }
  return empty ? <p className="mt-4 text-text-muted">{empty}</p> : null;
}

export type Column<T> = { header: string; cell: (row: T) => ReactNode; align?: 'right' };

/** Tabela zebrada; no celular, cada linha vira um cartão. */
export function DataTable<T>({
  rows,
  columns,
  rowKey,
  onRowClick,
}: {
  rows: T[];
  columns: Column<T>[];
  rowKey: (row: T) => string;
  onRowClick?: (row: T) => void;
}) {
  return (
    <>
      <table className="hidden w-full text-left md:table">
        <thead>
          <tr className="border-b border-border text-sm text-text-muted">
            {columns.map((c) => (
              <th
                key={c.header}
                className={`px-3 py-2 font-semibold ${c.align === 'right' ? 'text-right' : ''}`}
              >
                {c.header}
              </th>
            ))}
          </tr>
        </thead>
        <tbody>
          {rows.map((row) => (
            <tr
              key={rowKey(row)}
              onClick={onRowClick ? () => onRowClick(row) : undefined}
              className={`border-b border-border even:bg-brand-zebra hover:bg-canvas ${onRowClick ? 'cursor-pointer' : ''}`}
            >
              {columns.map((c) => (
                <td
                  key={c.header}
                  className={`px-3 py-2 ${c.align === 'right' ? 'text-right tabular-nums' : ''}`}
                >
                  {c.cell(row)}
                </td>
              ))}
            </tr>
          ))}
        </tbody>
      </table>
      <ul className="flex flex-col gap-3 md:hidden">
        {rows.map((row) => (
          <li
            key={rowKey(row)}
            onClick={onRowClick ? () => onRowClick(row) : undefined}
            className="rounded-md border border-border p-3"
          >
            <dl className="grid grid-cols-[120px_1fr] gap-x-3 gap-y-1">
              {columns.map((c) => (
                <div key={c.header} className="contents">
                  <dt className="text-sm text-text-muted">{c.header}</dt>
                  <dd className="break-words">{c.cell(row)}</dd>
                </div>
              ))}
            </dl>
          </li>
        ))}
      </ul>
    </>
  );
}
