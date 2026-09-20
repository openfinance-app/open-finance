export type EntityType =
  | 'ACCOUNT'
  | 'TRANSACTION'
  | 'ASSET'
  | 'LIABILITY'
  | 'REAL_ESTATE'
  | 'BUDGET'
  | 'CATEGORY'
  | 'PAYEE'
  | 'TRANSACTION_RULE'
  | 'RECURRING_TRANSACTION'
  | 'IMPORT';

export type OperationType = 'CREATE' | 'UPDATE' | 'DELETE';

export interface OperationHistoryResponse {
  canUndo: boolean;
  canRedo: boolean;
  id: number;
  entityType: EntityType;
  entityId: number;
  entityLabel?: string;
  operationType: OperationType;
  createdAt: string; // ISO string Date representation
  undoneAt?: string;
  redoneAt?: string;
}

export interface PageableResponse<T> {
  content: T[];
  pageable: {
    sort: {
      empty: boolean;
      sorted: boolean;
      unsorted: boolean;
    };
    offset: number;
    pageNumber: number;
    pageSize: number;
    unpaged: boolean;
    paged: boolean;
  };
  last: boolean;
  totalPages: number;
  totalElements: number;
  size: number;
  number: number;
  sort: {
    empty: boolean;
    sorted: boolean;
    unsorted: boolean;
  };
  first: boolean;
  numberOfElements: number;
  empty: boolean;
}
