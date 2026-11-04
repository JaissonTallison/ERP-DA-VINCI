'use client';

import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { CreateForm } from '@/components/org/create-form';
import { useCanWrite } from '@/components/org/permissions';
import { Card, DataTable, Message, PageHeader, StatusText } from '@/components/org/ui';
import { api, today } from '@/lib/organization';

type Role = { id: string; code: string; name: string; permissions: string[]; status: string };
type Assignment = {
  id: string;
  subject: string;
  roleCode: string;
  scopeType: string;
  scopeId: string | null;
  validFrom: string;
  validTo: string | null;
};
type SodRule = {
  id: string;
  code: string;
  description: string;
  permissionA: string;
  permissionB: string;
  active: boolean;
};

const SCOPES: Record<string, string> = {
  TENANT: 'Toda a empresa',
  ECONOMIC_GROUP: 'Grupo econômico',
  LEGAL_ENTITY: 'Entidade jurídica',
  ESTABLISHMENT: 'Estabelecimento',
  UNIT: 'Unidade',
};

const governance = <T,>(path: string, init?: RequestInit) =>
  api<T>(path, { ...init, service: 'governance' });

/** Papéis, atribuições com escopo e vigência e regras de segregação de funções. */
export default function AcessosPage() {
  const client = useQueryClient();
  const canWrite = useCanWrite();
  const roles = useQuery({
    queryKey: ['roles'],
    queryFn: () => governance<{ items: Role[] }>('/v1/roles'),
  });
  const assignments = useQuery({
    queryKey: ['assignments'],
    queryFn: () => governance<{ items: Assignment[] }>(`/v1/assignments?activeOn=${today()}`),
  });
  const rules = useQuery({
    queryKey: ['sod-rules'],
    queryFn: () => governance<{ items: SodRule[] }>('/v1/sod-rules'),
  });
  const revoke = useMutation({
    mutationFn: (id: string) =>
      governance(`/v1/assignments/${id}/revoke`, { method: 'POST', body: '{}' }),
    onSuccess: () => client.invalidateQueries({ queryKey: ['assignments'] }),
  });

  return (
    <>
      <PageHeader title="Acessos" />
      <CreateForm
        title="Novo papel"
        fields={[
          { name: 'code', label: 'Código', required: true },
          { name: 'name', label: 'Nome', required: true },
          { name: 'permissions', label: 'Permissões (separadas por vírgula)', required: true },
        ]}
        submit={(v) =>
          governance('/v1/roles', {
            method: 'POST',
            body: JSON.stringify({
              code: v.code,
              name: v.name,
              permissions: (v.permissions ?? '')
                .split(',')
                .map((p) => p.trim())
                .filter(Boolean),
            }),
          })
        }
        onDone={() => client.invalidateQueries({ queryKey: ['roles'] })}
      />
      <CreateForm
        title="Atribuir papel"
        fields={[
          { name: 'subject', label: 'Usuário (identificador)', required: true },
          {
            name: 'roleId',
            label: 'Papel',
            type: 'select',
            required: true,
            options: (roles.data?.items ?? [])
              .filter((r) => r.status === 'ACTIVE')
              .map((r) => ({ value: r.id, label: r.name })),
          },
          {
            name: 'scopeType',
            label: 'Escopo',
            type: 'select',
            required: true,
            initial: 'TENANT',
            options: Object.entries(SCOPES).map(([value, label]) => ({ value, label })),
          },
          { name: 'scopeId', label: 'Identificador do escopo' },
          { name: 'validFrom', label: 'Início', type: 'date', required: true, initial: today() },
        ]}
        submit={(v) =>
          governance('/v1/assignments', {
            method: 'POST',
            body: JSON.stringify({ ...v, scopeId: v.scopeId || null }),
          })
        }
        onDone={() => client.invalidateQueries({ queryKey: ['assignments'] })}
      />
      <CreateForm
        title="Nova regra de segregação"
        fields={[
          { name: 'code', label: 'Código', required: true },
          { name: 'description', label: 'Descrição', required: true },
          { name: 'permissionA', label: 'Permissão A', required: true },
          { name: 'permissionB', label: 'Permissão B', required: true },
        ]}
        submit={(v) => governance('/v1/sod-rules', { method: 'POST', body: JSON.stringify(v) })}
        onDone={() => client.invalidateQueries({ queryKey: ['sod-rules'] })}
      />
      <Card title="Papéis">
        <DataTable
          rows={roles.data?.items ?? []}
          rowKey={(r) => r.id}
          columns={[
            { header: 'Código', cell: (r) => r.code },
            { header: 'Nome', cell: (r) => r.name },
            { header: 'Permissões', cell: (r) => r.permissions.join(', ') },
            { header: 'Situação', cell: (r) => <StatusText status={r.status} /> },
          ]}
        />
        <Message
          error={roles.error}
          empty={roles.data?.items.length === 0 ? 'Nenhum papel cadastrado.' : undefined}
        />
      </Card>
      <Card title="Atribuições vigentes hoje">
        <DataTable
          rows={assignments.data?.items ?? []}
          rowKey={(a) => a.id}
          columns={[
            { header: 'Usuário', cell: (a) => a.subject },
            { header: 'Papel', cell: (a) => a.roleCode },
            { header: 'Escopo', cell: (a) => SCOPES[a.scopeType] ?? a.scopeType },
            { header: 'Início', cell: (a) => a.validFrom },
            {
              header: 'Ação',
              cell: (a) =>
                canWrite ? (
                  <button
                    type="button"
                    className="text-sm text-danger hover:underline"
                    onClick={() => revoke.mutate(a.id)}
                  >
                    Revogar
                  </button>
                ) : (
                  '—'
                ),
            },
          ]}
        />
        <Message
          error={assignments.error ?? revoke.error}
          empty={assignments.data?.items.length === 0 ? 'Nenhuma atribuição vigente.' : undefined}
        />
      </Card>
      <Card title="Segregação de funções">
        <DataTable
          rows={rules.data?.items ?? []}
          rowKey={(r) => r.id}
          columns={[
            { header: 'Código', cell: (r) => r.code },
            { header: 'Descrição', cell: (r) => r.description },
            { header: 'Permissões', cell: (r) => `${r.permissionA} × ${r.permissionB}` },
            { header: 'Situação', cell: (r) => (r.active ? 'Ativa' : 'Inativa') },
          ]}
        />
        <Message
          error={rules.error}
          empty={rules.data?.items.length === 0 ? 'Nenhuma regra cadastrada.' : undefined}
        />
      </Card>
    </>
  );
}
