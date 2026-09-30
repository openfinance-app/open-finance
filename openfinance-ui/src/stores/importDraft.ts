import { create } from 'zustand';
import type { ImportTransactionDTO, ImportWizardStep } from '@/types/import';

export interface ImportDraft {
  selectedStep: ImportWizardStep;
  uploadId: string | null;
  fileName: string;
  accountOverride: number | null | undefined;
  sessionId: number | null;
  categoryMappings: Record<string, number>;
  newCategoryNames: string[];
  skipDuplicates: boolean;
  editedTransactions: ImportTransactionDTO[] | null;
}

interface ImportDraftStore {
  userId: number | null;
  draft: ImportDraft | null;
  save: (userId: number, draft: ImportDraft) => void;
  clear: () => void;
}

// Keep drafts across in-app navigation without writing financial data to browser storage.
export const useImportDraftStore = create<ImportDraftStore>(set => ({
  userId: null,
  draft: null,
  save: (userId, draft) => set({ userId, draft }),
  clear: () => set({ userId: null, draft: null }),
}));
