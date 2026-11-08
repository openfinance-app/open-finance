import { useEffect } from 'react';

interface SelectionSnapshot {
  anchor: Node | null;
  anchorOffset: number;
  focus: Node | null;
  focusOffset: number;
}

function snapshot(selection: Selection | null): SelectionSnapshot | null {
  if (!selection || selection.isCollapsed) return null;
  return {
    anchor: selection.anchorNode,
    anchorOffset: selection.anchorOffset,
    focus: selection.focusNode,
    focusOffset: selection.focusOffset,
  };
}

/** A text-selection gesture must not activate a card, row, link or backdrop on release. */
export function SelectionClickGuard(): null {
  useEffect(() => {
    let selecting = false;
    let changed = false;
    let before: SelectionSnapshot | null = null;

    const reset = (): void => {
      selecting = false;
      changed = false;
      before = null;
    };
    const pointerDown = (event: PointerEvent): void => {
      reset();
      if (event.button !== 0 || event.isPrimary === false) return;
      selecting = true;
      before = snapshot(document.getSelection());
    };
    const selectionChanged = (): void => {
      if (selecting) changed = true;
    };
    const click = (event: MouseEvent): void => {
      const selection = document.getSelection();
      const after = snapshot(selection);
      const moved =
        after &&
        (!before ||
          after.anchor !== before.anchor ||
          after.anchorOffset !== before.anchorOffset ||
          after.focus !== before.focus ||
          after.focusOffset !== before.focusOffset);
      const suppress =
        selecting &&
        event.detail > 0 &&
        after &&
        (changed || moved) &&
        selection?.toString().length;
      reset();
      if (suppress) {
        event.preventDefault();
        event.stopPropagation();
      }
    };

    document.addEventListener('pointerdown', pointerDown, true);
    document.addEventListener('selectionchange', selectionChanged);
    document.addEventListener('click', click, true);
    document.addEventListener('pointercancel', reset, true);
    window.addEventListener('blur', reset);
    return () => {
      document.removeEventListener('pointerdown', pointerDown, true);
      document.removeEventListener('selectionchange', selectionChanged);
      document.removeEventListener('click', click, true);
      document.removeEventListener('pointercancel', reset, true);
      window.removeEventListener('blur', reset);
    };
  }, []);
  return null;
}
