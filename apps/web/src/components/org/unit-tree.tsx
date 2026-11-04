'use client';

import { useQuery } from '@tanstack/react-query';
import { useState } from 'react';
import { api, type Tree, type TreeNode } from '@/lib/organization';
import { StatusText } from './ui';

/** Árvore com navegação por nível: cada nó carrega os filhos só quando é aberto. */
export function UnitTree({ structureId, asOf }: { structureId: string; asOf: string }) {
  const roots = useQuery({
    queryKey: ['roots', structureId, asOf],
    queryFn: () => api<Tree>(`/v1/structures/${structureId}/roots?asOf=${asOf}`),
  });
  if (roots.error) {
    return <p className="text-danger">{roots.error.message}</p>;
  }
  if (roots.data && roots.data.items.length === 0) {
    return <p className="text-text-muted">Nenhuma unidade posicionada nesta data.</p>;
  }
  return (
    <ul role="tree" className="flex flex-col">
      {roots.data?.items.map((node) => (
        <Branch key={node.unitId} node={node} structureId={structureId} asOf={asOf} />
      ))}
    </ul>
  );
}

function Branch({
  node,
  structureId,
  asOf,
}: {
  node: TreeNode;
  structureId: string;
  asOf: string;
}) {
  const [open, setOpen] = useState(false);
  const children = useQuery({
    queryKey: ['children', structureId, node.unitId, asOf],
    queryFn: () =>
      api<Tree>(`/v1/structures/${structureId}/units/${node.unitId}/children?asOf=${asOf}`),
    enabled: open,
  });
  return (
    <li role="treeitem" aria-expanded={node.hasChildren ? open : undefined} aria-selected={false}>
      <div className="flex items-center gap-2 rounded-md px-2 py-1.5 hover:bg-canvas">
        {node.hasChildren ? (
          <button
            type="button"
            onClick={() => setOpen(!open)}
            aria-label={open ? `Recolher ${node.name}` : `Expandir ${node.name}`}
            className="h-6 w-6 rounded-sm border border-border text-sm leading-none text-brand"
          >
            {open ? '−' : '+'}
          </button>
        ) : (
          <span className="w-6" />
        )}
        <span className="font-semibold">{node.code}</span>
        <span>{node.name}</span>
        <span className="text-sm text-text-muted">{node.unitTypeCode}</span>
        {node.status !== 'ACTIVE' ? (
          <span className="text-sm">
            <StatusText status={node.status} />
          </span>
        ) : null}
      </div>
      {open && children.data ? (
        <ul role="group" className="ml-6 border-l border-border pl-2">
          {children.data.items.map((child) => (
            <Branch key={child.unitId} node={child} structureId={structureId} asOf={asOf} />
          ))}
        </ul>
      ) : null}
      {open && children.error ? <p className="ml-8 text-danger">{children.error.message}</p> : null}
    </li>
  );
}

/** Lista plana (prévia de reorganização) com recuo pela profundidade. */
export function FlatTree({ nodes }: { nodes: TreeNode[] }) {
  return (
    <ul className="flex flex-col">
      {nodes.map((n) => (
        <li key={n.unitId} className="py-1" style={{ paddingLeft: `${n.depth * 24}px` }}>
          <span className="font-semibold">{n.code}</span> {n.name}{' '}
          <span className="text-sm text-text-muted">{n.unitTypeCode}</span>
        </li>
      ))}
    </ul>
  );
}
