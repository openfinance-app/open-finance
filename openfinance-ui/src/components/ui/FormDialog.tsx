import { useCallback, useMemo, useState, type ComponentProps } from 'react';
import { DraftContext, type Draft } from '@/hooks/useFormDraft';
import { useTranslation } from 'react-i18next';
import { Dialog } from '@/components/ui/Dialog';

/** User dismissal is guarded; a successful save can still close the controlled dialog directly. */
export function FormDialog({ onOpenChange, ...props }: ComponentProps<typeof Dialog>) {
  const { t } = useTranslation('common');
  const [drafts] = useState(() => new Map<string, Draft>());
  const dismiss = useCallback(
    (action: () => void) => {
      const states = [...drafts.values()];
      if (states.some(state => state.busy)) return;
      if (states.some(state => state.dirty) && !window.confirm(t('unsavedForm'))) return;
      action();
    },
    [drafts, t]
  );
  const guard = useMemo(() => ({ drafts, dismiss }), [drafts, dismiss]);
  return (
    <DraftContext.Provider value={guard}>
      <Dialog
        {...props}
        onOpenChange={open => {
          if (open) onOpenChange?.(true);
          else dismiss(() => onOpenChange?.(false));
        }}
      />
    </DraftContext.Provider>
  );
}
