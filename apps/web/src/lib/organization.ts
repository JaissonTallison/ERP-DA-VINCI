/** Cliente do Organization pelo BFF (/api/organization). Tipos seguem contracts/http/organization/v1/openapi.yaml. */

export class ApiError extends Error {
  constructor(
    readonly status: number,
    readonly code: string | undefined,
    message: string,
  ) {
    super(message);
  }
}

type Problem = { title?: string; detail?: string; code?: string };

export async function api<T>(
  path: string,
  init?: RequestInit & { ifMatch?: number | undefined },
): Promise<T> {
  const headers = new Headers(init?.headers);
  if (init?.body) {
    headers.set('content-type', 'application/json');
  }
  if (init?.ifMatch !== undefined) {
    headers.set('if-match', `"${init.ifMatch}"`);
  }
  if (init?.method === 'POST') {
    headers.set('idempotency-key', crypto.randomUUID());
  }
  const response = await fetch(`/api/organization${path}`, { ...init, headers });
  if (!response.ok) {
    const problem = (await response.json().catch(() => ({}))) as Problem;
    throw new ApiError(
      response.status,
      problem.code,
      problem.detail ?? problem.title ?? `Erro ${response.status}`,
    );
  }
  return (await response.json()) as T;
}

export function query(params: Record<string, string | undefined>): string {
  const search = new URLSearchParams();
  for (const [key, value] of Object.entries(params)) {
    if (value) {
      search.set(key, value);
    }
  }
  const text = search.toString();
  return text ? `?${text}` : '';
}

export type Page<T> = { items: T[]; nextCursor?: string | null };

export type EconomicGroup = {
  id: string;
  code: string;
  name: string;
  referenceCurrency: string;
  status: string;
};

export type LegalEntity = {
  id: string;
  code: string;
  legalName: string;
  tradeName: string | null;
  country: string;
  functionalCurrency: string;
  status: string;
  economicGroupId: string | null;
  taxIdentifiers: { scheme: string; value: string }[];
};

export type Establishment = {
  id: string;
  code: string;
  name: string;
  type: string;
  status: string;
  openedOn: string;
  address: { locality: string; administrativeArea: string | null };
};

export type UnitType = { id: string; code: string; name: string };

export type ExternalParty = { id: string; kind: string; name: string };

export type Structure = { id: string; code: string; name: string; purpose: string };

export type TreeNode = {
  unitId: string;
  code: string;
  name: string;
  unitTypeCode: string;
  status: string;
  parentUnitId: string | null;
  depth: number;
  hasChildren: boolean;
};

export type Tree = { asOf: string; items: TreeNode[]; truncated: boolean };

export type Unit = { id: string; code: string; name: string; status: string };

export type Reorganization = {
  id: string;
  structureId: string;
  description: string;
  effectiveOn: string;
  status: string;
  createdBy: string;
  submittedBy: string | null;
  decidedBy: string | null;
  decisionNote: string | null;
  version: number;
};

export type Step = {
  operation: 'MOVE_UNIT' | 'REMOVE_UNIT' | 'RENAME_UNIT' | 'CLOSE_UNIT' | 'REASSIGN_COST_CENTER';
  unitId?: string | null;
  parentUnitId?: string | null;
  name?: string | null;
  costCenterId?: string | null;
  responsibleUnitId?: string | null;
};

export type Simulation = {
  valid: boolean;
  failedStep: number | null;
  code: string | null;
  message: string | null;
  preview: TreeNode[];
};

export type CostCenterNode = {
  id: string;
  code: string;
  name: string;
  kind: 'POSTABLE' | 'GROUP';
  status: string;
  parentId: string | null;
};

export type OwnershipChart = {
  asOf: string;
  legalEntityId: string;
  name: string;
  holders: {
    holderType: string;
    holderId: string;
    name: string;
    depth: number;
    effectiveCapital: number;
  }[];
  edges: {
    id: string;
    ownedLegalEntityId: string;
    ownedName: string;
    holderType: string;
    holderId: string;
    holderName: string;
    capitalPercentage: number;
    votingPercentage: number;
  }[];
  controlChain: { holderType: string; holderId: string; name: string }[];
  ultimateController: { holderType: string; holderId: string; name: string } | null;
};

export const STATUS_LABELS: Record<string, string> = {
  ACTIVE: 'Ativo',
  PLANNED: 'Planejada',
  SUSPENDED: 'Suspenso',
  INACTIVE: 'Inativa',
  BLOCKED: 'Bloqueado',
  CLOSED: 'Encerrado',
  DRAFT: 'Rascunho',
  PENDING_APPROVAL: 'Aguardando aprovação',
  APPLIED: 'Aplicada',
  REJECTED: 'Rejeitada',
  CANCELLED: 'Cancelada',
};

export function today(): string {
  return new Date().toISOString().slice(0, 10);
}
