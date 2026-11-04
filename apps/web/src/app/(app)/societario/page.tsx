'use client';

import { useQuery } from '@tanstack/react-query';
import { useSearchParams } from 'next/navigation';
import { Suspense, useState } from 'react';
import {
  Card,
  DataTable,
  Filters,
  InputField,
  Message,
  PageHeader,
  SelectField,
} from '@/components/org/ui';
import { api, today, type LegalEntity, type OwnershipChart, type Page } from '@/lib/organization';

const PERCENT = new Intl.NumberFormat('pt-BR', { maximumFractionDigits: 4 });

/** Organograma societário: titulares diretos e indiretos, participação efetiva e controlador final. */
export default function SocietarioPage() {
  return (
    <Suspense>
      <Ownership />
    </Suspense>
  );
}

function Ownership() {
  const initial = useSearchParams().get('entidade') ?? '';
  const [entityId, setEntityId] = useState(initial);
  const [asOf, setAsOf] = useState(today());
  const entities = useQuery({
    queryKey: ['legal-entities', ''],
    queryFn: () => api<Page<LegalEntity>>('/v1/legal-entities?limit=200'),
  });
  const selected = entityId || entities.data?.items[0]?.id || '';
  const chart = useQuery({
    queryKey: ['ownership-chart', selected, asOf],
    queryFn: () =>
      api<OwnershipChart>(`/v1/legal-entities/${selected}/ownership-chart?asOf=${asOf}`),
    enabled: Boolean(selected && asOf),
  });
  const c = chart.data;

  return (
    <>
      <PageHeader title="Organograma societário" />
      <Filters>
        <SelectField
          label="Entidade jurídica"
          value={selected}
          onChange={(e) => setEntityId(e.target.value)}
        >
          {entities.data?.items.map((e) => (
            <option key={e.id} value={e.id}>
              {e.legalName}
            </option>
          ))}
        </SelectField>
        <InputField
          label="Data"
          type="date"
          value={asOf}
          onChange={(e) => setAsOf(e.target.value)}
        />
      </Filters>
      <Message error={chart.error ?? entities.error} />
      {c ? (
        <>
          <Card title="Controle">
            {c.ultimateController ? (
              <p>
                Controlador final:{' '}
                <span className="font-semibold">{c.ultimateController.name}</span>
                <span className="text-text-muted">
                  {' '}
                  · cadeia: {c.controlChain.map((h) => h.name).join(' → ')}
                </span>
              </p>
            ) : (
              <p className="text-text-muted">Nenhum titular com mais de 50% dos votos.</p>
            )}
          </Card>
          <Card title="Participação efetiva">
            <DataTable
              rows={[...c.holders].sort((a, b) => b.effectiveCapital - a.effectiveCapital)}
              rowKey={(h) => h.holderId}
              columns={[
                { header: 'Titular', cell: (h) => h.name },
                {
                  header: 'Tipo',
                  cell: (h) => (h.holderType === 'LEGAL_ENTITY' ? 'Entidade do grupo' : 'Terceiro'),
                },
                { header: 'Nível', cell: (h) => h.depth, align: 'right' },
                {
                  header: 'Efetiva (%)',
                  cell: (h) => PERCENT.format(h.effectiveCapital),
                  align: 'right',
                },
              ]}
            />
            <Message
              empty={
                c.holders.length === 0 ? 'Nenhuma participação registrada nesta data.' : undefined
              }
            />
          </Card>
          <Card title="Participações diretas na cadeia">
            <DataTable
              rows={c.edges}
              rowKey={(e) => e.id}
              columns={[
                { header: 'Titular', cell: (e) => e.holderName },
                { header: 'Participa de', cell: (e) => e.ownedName },
                {
                  header: 'Capital (%)',
                  cell: (e) => PERCENT.format(e.capitalPercentage),
                  align: 'right',
                },
                {
                  header: 'Votos (%)',
                  cell: (e) => PERCENT.format(e.votingPercentage),
                  align: 'right',
                },
              ]}
            />
          </Card>
        </>
      ) : null}
    </>
  );
}
