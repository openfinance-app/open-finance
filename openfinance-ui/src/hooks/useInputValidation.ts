import { useDialogDraft } from '@/hooks/useFormDraft';
import { useEffect, useLayoutEffect, useRef, useState } from 'react';

/** Keep invalid localized drafts out of submissions, including forms using noValidate. */
export function useInputValidation(message?: string, draftChanged = false) {
  useDialogDraft(!!message && draftChanged);
  const ref = useRef<HTMLInputElement>(null);
  const [revealed, setRevealed] = useState(false);
  const reveal = () => setRevealed(true);

  useLayoutEffect(() => {
    ref.current?.setCustomValidity(message ?? '');
  }, [message]);

  useEffect(() => {
    const input = ref.current;
    const form = input?.form;
    const rejectInvalidDraft = (event: Event) => {
      if (!input || input.disabled || input.readOnly || input.validity.valid) return;
      event.preventDefault();
      event.stopImmediatePropagation();
      let parent = input.parentElement;
      while (parent) {
        if (parent instanceof HTMLDetailsElement) parent.open = true;
        parent = parent.parentElement;
      }
      setRevealed(true);
      input.focus();
    };
    form?.addEventListener('submit', rejectInvalidDraft, true);
    input?.addEventListener('invalid', rejectInvalidDraft);
    return () => {
      form?.removeEventListener('submit', rejectInvalidDraft, true);
      input?.removeEventListener('invalid', rejectInvalidDraft);
    };
  }, []);

  return {
    ref,
    reveal,
    validationError: revealed ? message : undefined,
  };
}
