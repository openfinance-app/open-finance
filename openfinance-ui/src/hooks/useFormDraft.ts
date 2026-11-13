import { createContext, useContext, useEffect, useId, useLayoutEffect } from 'react';
import { useTranslation } from 'react-i18next';

export type Draft = { dirty: boolean; busy: boolean };
type Guard = {
  drafts: Map<string, Draft>;
  dismiss: (action: () => void) => void;
};
export const DraftContext = createContext<Guard | null>(null);

/** Register both canonical form edits and invalid input drafts in the same dialog. */
export function useDialogDraft(dirty: boolean, busy = false): Guard | null {
  const guard = useContext(DraftContext);
  const id = useId();
  useLayoutEffect(() => {
    guard?.drafts.set(id, { dirty, busy });
    return () => {
      guard?.drafts.delete(id);
    };
  }, [guard, id, dirty, busy]);
  return guard;
}

export function useFormDraft(dirty: boolean, busy: boolean, onCancel: () => void): () => void {
  const guard = useDialogDraft(dirty, busy);
  const { t } = useTranslation('common');
  useEffect(() => {
    if (!dirty) return;
    const preventUnload = (event: BeforeUnloadEvent) => event.preventDefault();
    window.addEventListener('beforeunload', preventUnload);
    return () => window.removeEventListener('beforeunload', preventUnload);
  }, [dirty]);
  return () => {
    if (guard) guard.dismiss(onCancel);
    else if (!busy && (!dirty || window.confirm(t('unsavedForm')))) onCancel();
  };
}
