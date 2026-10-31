export type BulkResource =
  | 'assets'
  | 'accounts'
  | 'real-estate'
  | 'liabilities'
  | 'transactions'
  | 'recurring-transactions'
  | 'payees'
  | 'categories'
  | 'transaction-rules'
  | 'budgets';

export interface BulkItem {
  id: number;
  label: string;
  transferId?: string | null;
  depth?: number;
  physical?: boolean;
}

export type MetadataPatch = Record<string, string | number | boolean | null>;

export interface BulkResult {
  succeeded: number[];
  failed: { item: BulkItem; message: string }[];
}

export interface BulkField {
  name: string;
  kind:
    | 'text'
    | 'textarea'
    | 'account'
    | 'institution'
    | 'category'
    | 'accountType'
    | 'paymentMethod'
    | 'boolean';
  maxLength?: number;
  clearable?: boolean;
  physicalOnly?: boolean;
}

export const BULK_FIELDS: Partial<Record<BulkResource, BulkField[]>> = {
  assets: [
    { name: 'notes', kind: 'textarea', maxLength: 1000, clearable: true },
    { name: 'accountId', kind: 'account', clearable: true },
    { name: 'brand', kind: 'text', maxLength: 255, clearable: true, physicalOnly: true },
    { name: 'model', kind: 'text', maxLength: 255, clearable: true, physicalOnly: true },
  ],
  accounts: [
    { name: 'institutionId', kind: 'institution', clearable: true },
    { name: 'type', kind: 'accountType' },
    { name: 'description', kind: 'textarea', maxLength: 500, clearable: true },
  ],
  liabilities: [
    { name: 'institutionId', kind: 'institution', clearable: true },
    { name: 'notes', kind: 'textarea', maxLength: 1000, clearable: true },
  ],
  transactions: [
    { name: 'categoryId', kind: 'category', clearable: true },
    { name: 'payee', kind: 'text', maxLength: 100, clearable: true },
    { name: 'description', kind: 'text', maxLength: 255, clearable: true },
    { name: 'notes', kind: 'textarea', maxLength: 1000, clearable: true },
    { name: 'tags', kind: 'text', maxLength: 500, clearable: true },
    { name: 'paymentMethod', kind: 'paymentMethod', clearable: true },
    { name: 'isReconciled', kind: 'boolean' },
  ],
  payees: [{ name: 'categoryId', kind: 'category', clearable: true }],
};
