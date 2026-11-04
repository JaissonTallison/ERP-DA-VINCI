'use client';

import { useMutation } from '@tanstack/react-query';
import { useState, type FormEvent } from 'react';
import { Button } from '@/components/ui/button';
import { useCanWrite } from './permissions';
import { InputField, Message, SelectField } from './ui';

export type FieldSpec = {
  name: string;
  label: string;
  type?: 'text' | 'date' | 'number' | 'select';
  required?: boolean;
  initial?: string;
  options?: { value: string; label: string }[];
};

/** Formulário de cadastro recolhível: um único padrão para todas as telas do Organization. */
export function CreateForm({
  title,
  fields,
  submit,
  onDone,
}: {
  title: string;
  fields: FieldSpec[];
  submit: (values: Record<string, string>) => Promise<unknown>;
  onDone: () => Promise<unknown>;
}) {
  const canWrite = useCanWrite();
  const blank = () => Object.fromEntries(fields.map((f) => [f.name, f.initial ?? '']));
  const [values, setValues] = useState<Record<string, string>>(blank);
  const mutation = useMutation({
    mutationFn: () => submit(values),
    onSuccess: async () => {
      setValues(blank());
      await onDone();
    },
  });

  if (!canWrite) {
    return null;
  }

  function send(event: FormEvent) {
    event.preventDefault();
    mutation.mutate();
  }

  return (
    <details className="mt-6 rounded-md border border-border bg-surface p-4 shadow-card">
      <summary className="cursor-pointer font-semibold text-brand">{title}</summary>
      <form onSubmit={send} className="mt-4 flex flex-wrap items-end gap-4">
        {fields.map((f) => {
          const common = {
            label: f.label,
            required: f.required ?? false,
            value: values[f.name] ?? '',
          };
          const change = (value: string) => setValues({ ...values, [f.name]: value });
          return f.type === 'select' ? (
            <SelectField key={f.name} {...common} onChange={(e) => change(e.target.value)}>
              {f.required ? null : <option value="">Nenhum</option>}
              {f.required && !values[f.name] ? <option value="">Selecione</option> : null}
              {f.options?.map((o) => (
                <option key={o.value} value={o.value}>
                  {o.label}
                </option>
              ))}
            </SelectField>
          ) : (
            <InputField
              key={f.name}
              {...common}
              type={f.type ?? 'text'}
              step={f.type === 'number' ? 'any' : undefined}
              onChange={(e) => change(e.target.value)}
            />
          );
        })}
        <Button type="submit" disabled={mutation.isPending}>
          Salvar
        </Button>
      </form>
      <Message error={mutation.error} />
    </details>
  );
}
