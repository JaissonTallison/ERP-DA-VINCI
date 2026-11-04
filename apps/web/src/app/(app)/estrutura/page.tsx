'use client';

import { useQuery } from '@tanstack/react-query';
import { useState } from 'react';
import { UnitTree } from '@/components/org/unit-tree';
import { Card, Filters, InputField, Message, PageHeader, SelectField } from '@/components/org/ui';
import { api, today, type Page, type Structure } from '@/lib/organization';

/** Árvore organizacional por estrutura, em qualquer data. */
export default function EstruturaPage() {
  const structures = useQuery({
    queryKey: ['structures'],
    queryFn: () => api<Page<Structure>>('/v1/structures'),
  });
  const [selected, setSelected] = useState('');
  const [asOf, setAsOf] = useState(today());
  const structureId = selected || structures.data?.items[0]?.id || '';

  return (
    <>
      <PageHeader title="Estrutura organizacional" />
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
