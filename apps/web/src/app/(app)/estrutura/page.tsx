'use client';

import { useQuery, useQueryClient } from '@tanstack/react-query';
import { useState } from 'react';
import { CreateForm } from '@/components/org/create-form';
import { UnitTree } from '@/components/org/unit-tree';
import { Card, Filters, InputField, Message, PageHeader, SelectField } from '@/components/org/ui';
import {
  api,
  today,
  type Page,
  type Structure,
  type Unit,
  type UnitType,
} from '@/lib/organization';

const PURPOSES: Record<string, string> = {
  MANAGEMENT: 'Gerencial',
  FUNCTIONAL: 'Funcional',
  GEOGRAPHIC: 'Geográfica',
  CUSTOM: 'Personalizada',
};

/** Árvore organizacional por estrutura, em qualquer data. */
export default function EstruturaPage() {
  const structures = useQuery({
    queryKey: ['structures'],
    queryFn: () => api<Page<Structure>>('/v1/structures'),
  });
  const client = useQueryClient();
  const types = useQuery({
    queryKey: ['unit-types'],
    queryFn: () => api<Page<UnitType>>('/v1/unit-types'),
  });
  const units = useQuery({
    queryKey: ['units'],
    queryFn: () => api<Page<Unit>>('/v1/organizational-units?limit=200'),
  });
  const [selected, setSelected] = useState('');
  const [asOf, setAsOf] = useState(today());
  const structureId = selected || structures.data?.items[0]?.id || '';
  const unitOptions = (units.data?.items ?? []).map((u) => ({
    value: u.id,
    label: `${u.code} · ${u.name}`,
  }));

  return (
    <>
      <PageHeader title="Estrutura organizacional" />
      <CreateForm
        title="Nova estrutura"
        fields={[
          { name: 'code', label: 'Código', required: true },
          { name: 'name', label: 'Nome', required: true },
          {
            name: 'purpose',
            label: 'Finalidade',
            type: 'select',
            required: true,
            options: Object.entries(PURPOSES).map(([value, label]) => ({ value, label })),
          },
        ]}
        submit={(v) => api('/v1/structures', { method: 'POST', body: JSON.stringify(v) })}
        onDone={() => client.invalidateQueries({ queryKey: ['structures'] })}
      />
      <CreateForm
        title="Novo tipo de unidade"
        fields={[
          { name: 'code', label: 'Código', required: true },
          { name: 'name', label: 'Nome', required: true },
        ]}
        submit={(v) => api('/v1/unit-types', { method: 'POST', body: JSON.stringify(v) })}
        onDone={() => client.invalidateQueries({ queryKey: ['unit-types'] })}
      />
      <CreateForm
        title="Nova unidade"
        fields={[
          { name: 'code', label: 'Código', required: true },
          { name: 'name', label: 'Nome', required: true },
          {
            name: 'unitTypeId',
            label: 'Tipo',
            type: 'select',
            required: true,
            options: (types.data?.items ?? []).map((t) => ({ value: t.id, label: t.name })),
          },
          { name: 'validFrom', label: 'Início', type: 'date', required: true, initial: today() },
        ]}
        submit={(v) => api('/v1/organizational-units', { method: 'POST', body: JSON.stringify(v) })}
        onDone={() => client.invalidateQueries({ queryKey: ['units'] })}
      />
      <CreateForm
        title="Posicionar unidade na estrutura selecionada"
        fields={[
          {
            name: 'unitId',
            label: 'Unidade',
            type: 'select',
            required: true,
            options: unitOptions,
          },
          {
            name: 'parentUnitId',
            label: 'Pai (vazio = raiz)',
            type: 'select',
            options: unitOptions,
          },
          {
            name: 'effectiveOn',
            label: 'A partir de',
            type: 'date',
            required: true,
            initial: today(),
          },
        ]}
        submit={(v) =>
          api(`/v1/structures/${structureId}/moves`, {
            method: 'POST',
            body: JSON.stringify({ ...v, parentUnitId: v.parentUnitId || null }),
          })
        }
        onDone={async () => {
          await client.invalidateQueries({ queryKey: ['roots'] });
          await client.invalidateQueries({ queryKey: ['children'] });
        }}
      />
      <Filters>
        <SelectField
          label="Estrutura"
          value={structureId}
          onChange={(e) => setSelected(e.target.value)}
        >
          {structures.data?.items.map((s) => (
            <option key={s.id} value={s.id}>
              {s.name}
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
      <Message
        error={structures.error}
        empty={
          structures.data && structures.data.items.length === 0
            ? 'Nenhuma estrutura cadastrada.'
            : undefined
        }
      />
      {structureId && asOf ? (
        <Card>
          <UnitTree structureId={structureId} asOf={asOf} />
        </Card>
      ) : null}
    </>
  );
}
