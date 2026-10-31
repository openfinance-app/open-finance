import { createContext } from 'react';
import type { BulkItem, BulkResult } from '@/types/bulk';

export interface BulkSelectionState {
  items: BulkItem[];
  selected: Set<number>;
  busy: boolean;
  locked: boolean;
  progress: number;
  result: BulkResult | null;
  canEdit: boolean;
  toggle: (id: number) => void;
  toggleAll: () => void;
  clear: () => void;
  edit: () => void;
  remove: () => void;
}

export const BulkContext = createContext<BulkSelectionState | null>(null);
