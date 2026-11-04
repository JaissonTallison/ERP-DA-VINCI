'use client';

import { useQuery, useQueryClient } from '@tanstack/react-query';
import { useRouter } from 'next/navigation';
import { useState } from 'react';
import { CreateForm } from '@/components/org/create-form';
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
  const client = useQueryClient();
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
      <CreateForm
        title="Novo grupo econômico"
        fields={[
          { name: 'code', label: 'Código', required: true },
          { name: 'name', label: 'Nome', required: true },
          { name: 'referenceCurrency', label: 'Moeda', required: true, initial: 'BRL' },
          {
            name: 'referenceTimezone',
            label: 'Fuso',
            required: true,
            initial: 'America/Sao_Paulo',
          },
        ]}
        submit={(v) => api('/v1/economic-groups', { method: 'POST', body: JSON.stringify(v) })}
        onDone={() => client.invalidateQueries({ queryKey: ['economic-groups'] })}
      />
      <CreateForm
        title="Nova entidade jurídica"
        fields={[
          { name: 'code', label: 'Código', required: true },
          { name: 'legalName', label: 'Razão social', required: true },
          { name: 'tradeName', label: 'Nome fantasia' },
          { name: 'cnpj', label: 'CNPJ', required: true },
          { name: 'street', label: 'Endereço', required: true },
          { name: 'city', label: 'Cidade', required: true },
          { name: 'state', label: 'UF', required: true },
          { name: 'postalCode', label: 'CEP', required: true },
          {
            name: 'economicGroupId',
            label: 'Grupo econômico',
            type: 'select',
            options: (groups.data?.items ?? []).map((g) => ({ value: g.id, label: g.name })),
          },
        ]}
        submit={(v) =>
          api('/v1/legal-entities', {
            method: 'POST',
            body: JSON.stringify({
              code: v.code,
              legalName: v.legalName,
              tradeName: v.tradeName || null,
              country: 'BR',
              subdivision: `BR-${v.state}`,
              functionalCurrency: 'BRL',
              timezone: 'America/Sao_Paulo',
              defaultLocale: 'pt-BR',
              registeredAddress: {
                country: 'BR',
                addressLines: [v.street],
                locality: v.city,
                administrativeArea: v.state,
                postalCode: v.postalCode,
              },
              taxIdentifiers: [{ scheme: 'BR_CNPJ', value: v.cnpj, primary: true }],
              economicGroupId: v.economicGroupId || null,
              status: 'ACTIVE',
            }),
          })
        }
        onDone={() => client.invalidateQueries({ queryKey: ['legal-entities'] })}
      />
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
