'use client';

import { useQuery } from '@tanstack/react-query';
import { useRouter } from 'next/navigation';
import { useState } from 'react';
import {
  Card,
  DataTable,
  Filters,
  Message,
  PageHeader,
  SelectField,
  StatusText,
} from '@/components/org/ui';
import { api, query, type EconomicGroup, type LegalEntity, type Page } from '@/lib/organization';

/** Grupos econômicos e entidades jurídicas, com filtro por grupo. */
export default function EmpresasPage() {
  const router = useRouter();
  const [groupId, setGroupId] = useState('');
  const groups = useQuery({
    queryKey: ['economic-groups'],
    queryFn: () => api<Page<EconomicGroup>>('/v1/economic-groups?limit=200'),
  });
  const entities = useQuery({
    queryKey: ['legal-entities', groupId],
    queryFn: () =>
      api<Page<LegalEntity>>(
        `/v1/legal-entities${query({ economicGroupId: groupId, limit: '200' })}`,
      ),
  });
  const groupName = new Map((groups.data?.items ?? []).map((g) => [g.id, g.name]));

  return (
    <>
      <PageHeader title="Empresas" />
      <Filters>
        <SelectField
          label="Grupo econômico"
          value={groupId}
          onChange={(e) => setGroupId(e.target.value)}
        >
          <option value="">Todos</option>
          {groups.data?.items.map((g) => (
            <option key={g.id} value={g.id}>
              {g.name}
            </option>
          ))}
        </SelectField>
      </Filters>
      <Card title="Entidades jurídicas">
        <DataTable
          rows={entities.data?.items ?? []}
          rowKey={(e) => e.id}
          onRowClick={(e) => router.push(`/empresas/${e.id}`)}
          columns={[
            { header: 'Código', cell: (e) => e.code },
            { header: 'Razão social', cell: (e) => e.legalName },
            { header: 'País', cell: (e) => e.country },
            { header: 'Documento', cell: (e) => e.taxIdentifiers[0]?.value ?? '—' },
            {
              header: 'Grupo',
              cell: (e) => (e.economicGroupId ? (groupName.get(e.economicGroupId) ?? '—') : '—'),
            },
            { header: 'Situação', cell: (e) => <StatusText status={e.status} /> },
          ]}
        />
        <Message
          error={entities.error ?? groups.error}
          empty={
            entities.data && entities.data.items.length === 0
              ? 'Nenhuma entidade cadastrada.'
              : undefined
          }
        />
      </Card>
    </>
  );
}
