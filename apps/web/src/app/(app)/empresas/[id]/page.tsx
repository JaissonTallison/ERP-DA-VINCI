'use client';

import { useQuery, useQueryClient } from '@tanstack/react-query';
import Link from 'next/link';
import { use } from 'react';
import { CreateForm } from '@/components/org/create-form';
import { Card, DataTable, Message, PageHeader, StatusText } from '@/components/org/ui';
import { api, today, type Establishment, type LegalEntity, type Page } from '@/lib/organization';
import { buttonClasses } from '@/components/ui/button';

const ESTABLISHMENT_TYPES: Record<string, string> = {
  HEADQUARTERS: 'Matriz',
  BRANCH: 'Filial',
  PLANT: 'Fábrica',
  STORE: 'Loja',
  WAREHOUSE: 'Depósito',
  OFFICE: 'Escritório',
  OTHER: 'Outro',
};

/** Entidade jurídica: dados principais e estabelecimentos. */
export default function EntidadePage({ params }: { params: Promise<{ id: string }> }) {
  const { id } = use(params);
  const client = useQueryClient();
  const entity = useQuery({
    queryKey: ['legal-entity', id],
    queryFn: () => api<LegalEntity>(`/v1/legal-entities/${id}`),
  });
  const establishments = useQuery({
    queryKey: ['establishments', id],
    queryFn: () => api<Page<Establishment>>(`/v1/establishments?legalEntityId=${id}&limit=200`),
  });
  const e = entity.data;

  return (
    <>
      <PageHeader
        title={e?.legalName ?? 'Entidade jurídica'}
        actions={
          <>
            <Link className={buttonClasses('secondary')} href={`/centros-de-custo?entidade=${id}`}>
              Centros de custo
            </Link>
            <Link className={buttonClasses('secondary')} href={`/societario?entidade=${id}`}>
              Organograma societário
            </Link>
          </>
        }
      />
      <Message error={entity.error} />
      {e ? (
        <Card title="Dados">
          <dl className="grid gap-x-6 gap-y-3 sm:grid-cols-[200px_1fr]">
            {(
              [
                ['Código', e.code],
                ['Nome fantasia', e.tradeName ?? '—'],
                ['País', e.country],
                ['Moeda funcional', e.functionalCurrency],
                [
                  'Documentos',
                  e.taxIdentifiers.map((t) => `${t.scheme} ${t.value}`).join(', ') || '—',
                ],
              ] as const
            ).map(([label, value]) => (
              <div key={label} className="sm:contents">
                <dt className="text-sm text-text-muted">{label}</dt>
                <dd className="break-all">{value}</dd>
              </div>
            ))}
            <div className="sm:contents">
              <dt className="text-sm text-text-muted">Situação</dt>
              <dd>
                <StatusText status={e.status} />
              </dd>
            </div>
          </dl>
        </Card>
      ) : null}
      <CreateForm
        title="Novo estabelecimento"
        fields={[
          { name: 'code', label: 'Código', required: true },
          { name: 'name', label: 'Nome', required: true },
          {
            name: 'type',
            label: 'Tipo',
            type: 'select',
            required: true,
            options: Object.entries(ESTABLISHMENT_TYPES).map(([value, label]) => ({
              value,
              label,
            })),
          },
          { name: 'cnpj', label: 'CNPJ', required: true },
          { name: 'street', label: 'Endereço', required: true },
          { name: 'city', label: 'Cidade', required: true },
          { name: 'state', label: 'UF', required: true },
          { name: 'postalCode', label: 'CEP', required: true },
          { name: 'openedOn', label: 'Abertura', type: 'date', required: true, initial: today() },
        ]}
        submit={(v) =>
          api('/v1/establishments', {
            method: 'POST',
            body: JSON.stringify({
              legalEntityId: id,
              code: v.code,
              name: v.name,
              type: v.type,
              address: {
                country: 'BR',
                addressLines: [v.street],
                locality: v.city,
                administrativeArea: v.state,
                postalCode: v.postalCode,
              },
              taxIdentifiers: [{ scheme: 'BR_CNPJ', value: v.cnpj, primary: true }],
              openedOn: v.openedOn,
            }),
          })
        }
        onDone={() => client.invalidateQueries({ queryKey: ['establishments', id] })}
      />
      <Card title="Estabelecimentos">
        <DataTable
          rows={establishments.data?.items ?? []}
          rowKey={(s) => s.id}
          columns={[
            { header: 'Código', cell: (s) => s.code },
            { header: 'Nome', cell: (s) => s.name },
            { header: 'Tipo', cell: (s) => ESTABLISHMENT_TYPES[s.type] ?? s.type },
            { header: 'Cidade', cell: (s) => s.address.locality },
            { header: 'Aberto em', cell: (s) => s.openedOn },
            { header: 'Situação', cell: (s) => <StatusText status={s.status} /> },
          ]}
        />
        <Message
          error={establishments.error}
          empty={
            establishments.data && establishments.data.items.length === 0
              ? 'Nenhum estabelecimento.'
              : undefined
          }
        />
      </Card>
    </>
  );
}
