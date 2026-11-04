'use client';

import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { useRouter } from 'next/navigation';
import { useState, type FormEvent } from 'react';
import { useCanWrite } from '@/components/org/permissions';
import { Button } from '@/components/ui/button';
import {
  Card,
  DataTable,
  InputField,
  Message,
  PageHeader,
  SelectField,
  StatusText,
} from '@/components/org/ui';
import { api, today, type Page, type Reorganization, type Structure } from '@/lib/organization';

/** Reorganizações: lista e criação de rascunho. */
export default function ReorganizacoesPage() {
  const router = useRouter();
  const canWrite = useCanWrite();
  const client = useQueryClient();
  const structures = useQuery({
    queryKey: ['structures'],
    queryFn: () => api<Page<Structure>>('/v1/structures'),
  });
  const list = useQuery({
    queryKey: ['reorganizations'],
    queryFn: () => api<{ items: Reorganization[] }>('/v1/reorganizations'),
  });
  const [form, setForm] = useState({ structureId: '', description: '', effectiveOn: today() });
  const create = useMutation({
    mutationFn: () =>
      api<Reorganization>('/v1/reorganizations', {
        method: 'POST',
        body: JSON.stringify({
          ...form,
          structureId: form.structureId || structures.data?.items[0]?.id,
        }),
      }),
    onSuccess: async (r) => {
      await client.invalidateQueries({ queryKey: ['reorganizations'] });
      router.push(`/reorganizacoes/${r.id}`);
    },
  });
  const structureName = new Map((structures.data?.items ?? []).map((s) => [s.id, s.name]));

  function submit(event: FormEvent) {
    event.preventDefault();
    create.mutate();
  }

  return (
    <>
      <PageHeader title="Reorganizações" />
      {canWrite ? (
        <Card title="Nova reorganização">
          <form onSubmit={submit} className="flex flex-wrap items-end gap-4">
            <SelectField
              label="Estrutura"
              value={form.structureId}
              onChange={(e) => setForm({ ...form, structureId: e.target.value })}
            >
              {structures.data?.items.map((s) => (
                <option key={s.id} value={s.id}>
                  {s.name}
                </option>
              ))}
            </SelectField>
            <InputField
              label="Descrição"
              required
              maxLength={500}
              value={form.description}
              onChange={(e) => setForm({ ...form, description: e.target.value })}
            />
            <InputField
              label="Vigência"
              type="date"
              required
              value={form.effectiveOn}
              onChange={(e) => setForm({ ...form, effectiveOn: e.target.value })}
            />
            <Button type="submit" disabled={create.isPending}>
              Criar rascunho
            </Button>
          </form>
          <Message error={create.error} />
        </Card>
      ) : null}
      <Card title="Todas">
        <DataTable
          rows={list.data?.items ?? []}
          rowKey={(r) => r.id}
          onRowClick={(r) => router.push(`/reorganizacoes/${r.id}`)}
          columns={[
            { header: 'Descrição', cell: (r) => r.description },
            { header: 'Estrutura', cell: (r) => structureName.get(r.structureId) ?? '—' },
            { header: 'Vigência', cell: (r) => r.effectiveOn },
            { header: 'Situação', cell: (r) => <StatusText status={r.status} /> },
          ]}
        />
        <Message
          error={list.error}
          empty={list.data && list.data.items.length === 0 ? 'Nenhuma reorganização.' : undefined}
        />
      </Card>
    </>
  );
}
