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

const RECOVERY_KEY = 'import_recovery';
interface ImportRecovery {
  userId: number;
  sessionId: number;
  selectedStep: ImportWizardStep;
}

/** Only identifiers and the wizard step survive reload; financial data stays on the server. */
export function readImportRecovery(userId: number): ImportRecovery | null {
  try {
    const value: unknown = JSON.parse(sessionStorage.getItem(RECOVERY_KEY) ?? 'null');
    if (
      value &&
      typeof value === 'object' &&
      'userId' in value &&
      value.userId === userId &&
      'sessionId' in value &&
      typeof value.sessionId === 'number' &&
      Number.isSafeInteger(value.sessionId) &&
      value.sessionId > 0 &&
      'selectedStep' in value &&
      ['account', 'review', 'confirm', 'progress'].includes(String(value.selectedStep))
    ) {
      return value as ImportRecovery;
    }
  } catch {
    // A blocked or corrupt storage entry must not prevent a new import.
  }
  return null;
}

function saveRecovery(userId: number | null, draft: ImportDraft | null): void {
  try {
    if (userId && draft?.sessionId) {
      sessionStorage.setItem(
        RECOVERY_KEY,
        JSON.stringify({
          userId,
          sessionId: draft.sessionId,
          selectedStep: draft.selectedStep,
        })
      );
    } else {
      sessionStorage.removeItem(RECOVERY_KEY);
    }
  } catch {
    // In-memory drafts still work when browser storage is unavailable.
  }
}

// Keep drafts across in-app navigation without writing financial data to browser storage.
export const useImportDraftStore = create<ImportDraftStore>(set => ({
  userId: null,
  draft: null,
  save: (userId, draft) => {
    saveRecovery(userId, draft);
    set({ userId, draft });
  },
  clear: () => {
    saveRecovery(null, null);
    set({ userId: null, draft: null });
  },
}));
