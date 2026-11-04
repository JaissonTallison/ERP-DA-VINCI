'use client';

import { useQuery, useQueryClient } from '@tanstack/react-query';
import { useSearchParams } from 'next/navigation';
import { Suspense, useState } from 'react';
import { CreateForm } from '@/components/org/create-form';
import {
  Card,
  DataTable,
  Filters,
  InputField,
  Message,
  PageHeader,
  SelectField,
} from '@/components/org/ui';
import {
  api,
  today,
  type ExternalParty,
  type LegalEntity,
  type OwnershipChart,
  type Page,
} from '@/lib/organization';

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
  const client = useQueryClient();
  const parties = useQuery({
    queryKey: ['external-parties'],
    queryFn: () => api<{ items: ExternalParty[] }>('/v1/external-parties'),
  });
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
      <CreateForm
        title="Novo titular externo"
        fields={[
          {
            name: 'kind',
            label: 'Tipo',
            type: 'select',
            required: true,
            options: [
              { value: 'PERSON', label: 'Pessoa' },
              { value: 'COMPANY', label: 'Empresa' },
              { value: 'FUND', label: 'Fundo' },
              { value: 'OTHER', label: 'Outro' },
            ],
          },
          { name: 'name', label: 'Nome', required: true },
          { name: 'country', label: 'País (ISO)', initial: 'BR' },
          { name: 'taxIdentifier', label: 'Documento (guardado cifrado)' },
        ]}
        submit={(v) =>
          api('/v1/external-parties', {
            method: 'POST',
            body: JSON.stringify({
              kind: v.kind,
              name: v.name,
              country: v.country || null,
              taxIdentifier: v.taxIdentifier || null,
            }),
          })
        }
        onDone={() => client.invalidateQueries({ queryKey: ['external-parties'] })}
      />
      <CreateForm
        title="Nova participação na entidade selecionada"
        fields={[
          {
            name: 'owner',
            label: 'Titular',
            type: 'select',
            required: true,
            options: [
              ...(entities.data?.items ?? [])
                .filter((e) => e.id !== selected)
                .map((e) => ({ value: `entity:${e.id}`, label: e.legalName })),
              ...(parties.data?.items ?? []).map((p) => ({
                value: `party:${p.id}`,
                label: p.name,
              })),
            ],
          },
          { name: 'capitalPercentage', label: 'Capital (%)', type: 'number', required: true },
          { name: 'votingPercentage', label: 'Votos (%)', type: 'number', required: true },
          { name: 'validFrom', label: 'Início', type: 'date', required: true, initial: today() },
        ]}
        submit={(v) => {
          const [kind, ownerId] = (v.owner ?? '').split(':');
          return api(`/v1/legal-entities/${selected}/ownership`, {
            method: 'POST',
            body: JSON.stringify({
              [kind === 'entity' ? 'ownerLegalEntityId' : 'ownerExternalPartyId']: ownerId,
              capitalPercentage: Number(v.capitalPercentage),
              votingPercentage: Number(v.votingPercentage),
              validFrom: v.validFrom,
            }),
          });
        }}
        onDone={() => client.invalidateQueries({ queryKey: ['ownership-chart'] })}
      />
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
