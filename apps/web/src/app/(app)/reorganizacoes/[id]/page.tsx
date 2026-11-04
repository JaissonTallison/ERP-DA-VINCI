'use client';

import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { use, useState } from 'react';
import { useCanWrite } from '@/components/org/permissions';
import { FlatTree } from '@/components/org/unit-tree';
import { Button } from '@/components/ui/button';
import {
  Card,
  InputField,
  Message,
  PageHeader,
  SelectField,
  StatusText,
} from '@/components/org/ui';
import {
  api,
  type Page,
  type Reorganization,
  type Simulation,
  type Step,
  type Unit,
} from '@/lib/organization';

const OPERATIONS: Record<Step['operation'], string> = {
  MOVE_UNIT: 'Mover unidade',
  REMOVE_UNIT: 'Retirar da estrutura',
  RENAME_UNIT: 'Renomear unidade',
  CLOSE_UNIT: 'Encerrar unidade',
  REASSIGN_COST_CENTER: 'Trocar responsável de centro de custo',
};

/** Reorganização: passos, simulação com prévia da árvore, submissão e decisão. */
export default function ReorganizacaoPage({ params }: { params: Promise<{ id: string }> }) {
  const { id } = use(params);
  const canWrite = useCanWrite();
  const client = useQueryClient();
  const reorg = useQuery({
    queryKey: ['reorganization', id],
    queryFn: () => api<Reorganization>(`/v1/reorganizations/${id}`),
  });
  const steps = useQuery({
    queryKey: ['reorganization-steps', id],
    queryFn: () => api<{ steps: Step[] }>(`/v1/reorganizations/${id}/steps`),
  });
  const units = useQuery({
    queryKey: ['units'],
    queryFn: () => api<Page<Unit>>('/v1/organizational-units?limit=200'),
  });
  const [draft, setDraft] = useState<Step>({
    operation: 'MOVE_UNIT',
    unitId: '',
    parentUnitId: '',
  });
  const [simulation, setSimulation] = useState<Simulation | null>(null);
  const r = reorg.data;
  const unitLabel = new Map((units.data?.items ?? []).map((u) => [u.id, `${u.code} · ${u.name}`]));

  const refresh = async () => {
    setSimulation(null);
    await client.invalidateQueries({ queryKey: ['reorganization', id] });
    await client.invalidateQueries({ queryKey: ['reorganization-steps', id] });
    await client.invalidateQueries({ queryKey: ['reorganizations'] });
  };

  const saveSteps = useMutation({
    mutationFn: (next: Step[]) =>
      api<Reorganization>(`/v1/reorganizations/${id}/steps`, {
        method: 'PUT',
        ifMatch: r?.version,
        body: JSON.stringify({ steps: next }),
      }),
    onSuccess: refresh,
  });
  const action = useMutation({
    mutationFn: (name: 'submit' | 'approve' | 'reject' | 'cancel') =>
      api<Reorganization>(`/v1/reorganizations/${id}/${name}`, {
        method: 'POST',
        ifMatch: r?.version,
      }),
    onSuccess: refresh,
  });
  const simulate = useMutation({
    mutationFn: () => api<Simulation>(`/v1/reorganizations/${id}/simulation`, { method: 'POST' }),
    onSuccess: setSimulation,
  });

  const current = steps.data?.steps ?? [];
  const editable = canWrite && r?.status === 'DRAFT';

  function addStep() {
    const step: Step = {
      operation: draft.operation,
      unitId: draft.unitId || null,
      parentUnitId: draft.operation === 'MOVE_UNIT' ? draft.parentUnitId || null : null,
      name: draft.operation === 'RENAME_UNIT' ? (draft.name ?? null) : null,
    };
    saveSteps.mutate([...current, step]);
  }

  return (
    <>
      <PageHeader
        title={r?.description ?? 'Reorganização'}
        actions={
          r ? (
            <>
              {editable ? (
                <Button
                  variant="secondary"
                  onClick={() => simulate.mutate()}
                  disabled={simulate.isPending}
                >
                  Simular
                </Button>
              ) : null}
              {editable ? (
                <Button onClick={() => action.mutate('submit')} disabled={current.length === 0}>
                  Enviar para aprovação
                </Button>
              ) : null}
              {canWrite && r.status === 'PENDING_APPROVAL' ? (
                <>
                  <Button variant="secondary" onClick={() => action.mutate('reject')}>
                    Rejeitar
                  </Button>
                  <Button onClick={() => action.mutate('approve')}>Aprovar e aplicar</Button>
                </>
              ) : null}
              {editable || (canWrite && r.status === 'PENDING_APPROVAL') ? (
                <Button variant="secondary" onClick={() => action.mutate('cancel')}>
                  Cancelar
                </Button>
              ) : null}
            </>
          ) : null
        }
      />
      {r ? (
        <p className="mt-2 text-text-muted">
          Vigência {r.effectiveOn} · <StatusText status={r.status} />
          {r.submittedBy ? ` · enviada por ${r.submittedBy}` : ''}
          {r.decidedBy ? ` · decidida por ${r.decidedBy}` : ''}
        </p>
      ) : null}
      <Message error={reorg.error ?? action.error ?? saveSteps.error ?? simulate.error} />

      <Card title={`Passos (${current.length})`}>
        {current.length === 0 ? <p className="text-text-muted">Nenhum passo ainda.</p> : null}
        <ol className="flex list-decimal flex-col gap-2 pl-6">
          {current.map((s, index) => (
            <li key={`${index}-${s.operation}`} className="flex flex-wrap items-center gap-3">
              <span className="font-semibold">{OPERATIONS[s.operation]}</span>
              <span>{s.unitId ? (unitLabel.get(s.unitId) ?? s.unitId) : ''}</span>
              {s.operation === 'MOVE_UNIT' ? (
                <span className="text-text-muted">
                  para {s.parentUnitId ? (unitLabel.get(s.parentUnitId) ?? s.parentUnitId) : 'raiz'}
                </span>
              ) : null}
              {s.name ? <span className="text-text-muted">“{s.name}”</span> : null}
              {editable ? (
                <button
                  type="button"
                  className="text-sm text-danger hover:underline"
                  onClick={() => saveSteps.mutate(current.filter((_, i) => i !== index))}
                >
                  Remover
                </button>
              ) : null}
            </li>
          ))}
        </ol>
        {editable ? (
          <div className="mt-5 flex flex-wrap items-end gap-4 border-t border-border pt-5">
            <SelectField
              label="Operação"
              value={draft.operation}
              onChange={(e) =>
                setDraft({ ...draft, operation: e.target.value as Step['operation'] })
              }
            >
              {(['MOVE_UNIT', 'REMOVE_UNIT', 'RENAME_UNIT', 'CLOSE_UNIT'] as const).map((op) => (
                <option key={op} value={op}>
                  {OPERATIONS[op]}
                </option>
              ))}
            </SelectField>
            <SelectField
              label="Unidade"
              value={draft.unitId ?? ''}
              onChange={(e) => setDraft({ ...draft, unitId: e.target.value })}
            >
              <option value="">Selecione</option>
              {units.data?.items.map((u) => (
                <option key={u.id} value={u.id}>
                  {u.code} · {u.name}
                </option>
              ))}
            </SelectField>
            {draft.operation === 'MOVE_UNIT' ? (
              <SelectField
                label="Novo pai"
                value={draft.parentUnitId ?? ''}
                onChange={(e) => setDraft({ ...draft, parentUnitId: e.target.value })}
              >
                <option value="">Raiz da estrutura</option>
                {units.data?.items.map((u) => (
                  <option key={u.id} value={u.id}>
                    {u.code} · {u.name}
                  </option>
                ))}
              </SelectField>
            ) : null}
            {draft.operation === 'RENAME_UNIT' ? (
              <InputField
                label="Novo nome"
                maxLength={200}
                value={draft.name ?? ''}
                onChange={(e) => setDraft({ ...draft, name: e.target.value })}
              />
            ) : null}
            <Button
              variant="secondary"
              onClick={addStep}
              disabled={!draft.unitId || saveSteps.isPending}
            >
              Adicionar passo
            </Button>
          </div>
        ) : null}
      </Card>

      {simulation ? (
        <Card title="Simulação">
          {simulation.valid ? (
            <>
              <p className="mb-4 text-primary">
                Todos os passos podem ser aplicados. Árvore na data de vigência:
              </p>
              <FlatTree nodes={simulation.preview} />
            </>
          ) : (
            <p className="text-danger">
              O passo {(simulation.failedStep ?? 0) + 1} não pode ser aplicado: {simulation.message}
            </p>
          )}
        </Card>
      ) : null}
    </>
  );
}
