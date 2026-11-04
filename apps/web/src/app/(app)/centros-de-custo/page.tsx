'use client';

import { useQuery, useQueryClient } from '@tanstack/react-query';
import { useSearchParams } from 'next/navigation';
import { Suspense, useState, type ReactNode } from 'react';
import { CreateForm } from '@/components/org/create-form';
import {
  Card,
  Filters,
  InputField,
  Message,
  PageHeader,
  SelectField,
  StatusText,
} from '@/components/org/ui';
import { api, today, type CostCenterNode, type LegalEntity, type Page } from '@/lib/organization';

/** Centros de custo de uma entidade, em árvore de totalização, em qualquer data. */
export default function CentrosDeCustoPage() {
  return (
    <Suspense>
      <CostCenters />
    </Suspense>
  );
}

function CostCenters() {
  const client = useQueryClient();
  const initial = useSearchParams().get('entidade') ?? '';
  const [entityId, setEntityId] = useState(initial);
  const [asOf, setAsOf] = useState(today());
  const entities = useQuery({
    queryKey: ['legal-entities', ''],
    queryFn: () => api<Page<LegalEntity>>('/v1/legal-entities?limit=200'),
  });
  const selected = entityId || entities.data?.items[0]?.id || '';
  const tree = useQuery({
    queryKey: ['cost-center-tree', selected, asOf],
    queryFn: () =>
      api<{ items: CostCenterNode[] }>(
        `/v1/legal-entities/${selected}/cost-centers/tree?asOf=${asOf}`,
      ),
    enabled: Boolean(selected && asOf),
  });
  const nodes = tree.data?.items ?? [];
  const children = new Map<string | null, CostCenterNode[]>();
  for (const n of nodes) {
    const key = n.parentId && nodes.some((p) => p.id === n.parentId) ? n.parentId : null;
    children.set(key, [...(children.get(key) ?? []), n]);
  }

  function render(parent: string | null, depth: number): ReactNode[] {
    return (children.get(parent) ?? []).flatMap((n) => [
      <li
        key={n.id}
        className="flex flex-wrap gap-3 py-1"
        style={{ paddingLeft: `${depth * 24}px` }}
      >
        <span className="font-semibold">{n.code}</span>
        <span>{n.name}</span>
        <span className="text-sm text-text-muted">
          {n.kind === 'GROUP' ? 'Totalizador' : 'Lançável'}
        </span>
        {n.status !== 'ACTIVE' ? <StatusText status={n.status} /> : null}
      </li>,
      ...render(n.id, depth + 1),
    ]);
  }

  return (
    <>
      <PageHeader title="Centros de custo" />
      <CreateForm
        title="Novo centro de custo na entidade selecionada"
        fields={[
          { name: 'code', label: 'Código', required: true },
          { name: 'name', label: 'Nome', required: true },
          {
            name: 'kind',
            label: 'Tipo',
            type: 'select',
            required: true,
            options: [
              { value: 'POSTABLE', label: 'Lançável' },
              { value: 'GROUP', label: 'Totalizador' },
            ],
          },
          { name: 'category', label: 'Categoria', required: true },
          {
            name: 'parentId',
            label: 'Pai',
            type: 'select',
            options: nodes
              .filter((n) => n.kind === 'GROUP')
              .map((n) => ({ value: n.id, label: `${n.code} · ${n.name}` })),
          },
          { name: 'validFrom', label: 'Início', type: 'date', required: true, initial: today() },
        ]}
        submit={(v) =>
          api('/v1/cost-centers', {
            method: 'POST',
            body: JSON.stringify({ ...v, legalEntityId: selected, parentId: v.parentId || null }),
          })
        }
        onDone={() => client.invalidateQueries({ queryKey: ['cost-center-tree'] })}
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
      <Card>
        <ul>{render(null, 0)}</ul>
        <Message
          error={tree.error ?? entities.error}
          empty={
            tree.data && nodes.length === 0
              ? 'Nenhum centro de custo vigente nesta data.'
              : undefined
          }
        />
      </Card>
    </>
  );
}
