import { useEffect, useRef } from 'react';
import { useNavigate } from 'react-router';
import { useSidebar } from '@/context/SidebarContext';

/**
 * Maps digit keys 1-9 to application routes.
 * Order mirrors the sidebar navigation sequence.
 */
const DIGIT_ROUTES: Record<string, string> = {
  '1': '/dashboard',
  '2': '/accounts',
  '3': '/transactions',
  '4': '/import',
  '5': '/budget',
  '6': '/assets',
  '7': '/tools/financial-freedom',
  '8': '/community',
  '9': '/premium',
};

interface UseKeyboardShortcutsOptions {
  /** Called when Ctrl/Cmd+K is pressed — should focus the search input */
  onFocusSearch?: () => void;
}

/**
 * Registers global keyboard shortcuts:
 *
 * - Ctrl/Cmd + K  : Focus the global search bar
 * - Ctrl/Cmd + B  : Toggle sidebar collapsed state
 * - 1–9           : Navigate to the corresponding section
 *                   (only when focus is NOT on an input/textarea/select/contenteditable)
 *
 * Shortcuts are automatically cleaned up on unmount.
 */
export function useKeyboardShortcuts({ onFocusSearch }: UseKeyboardShortcutsOptions = {}) {
  const navigate = useNavigate();
  const { toggleCollapsed } = useSidebar();

  // Keep stable refs so the effect closure never goes stale
  const onFocusSearchRef = useRef(onFocusSearch);
  const toggleCollapsedRef = useRef(toggleCollapsed);
  const navigateRef = useRef(navigate);

  useEffect(() => {
    onFocusSearchRef.current = onFocusSearch;
  });
  useEffect(() => {
    toggleCollapsedRef.current = toggleCollapsed;
  });
  useEffect(() => {
    navigateRef.current = navigate;
  });

  useEffect(() => {
    const isMac = navigator.platform.toUpperCase().includes('MAC');

    const isModifierHeld = (e: KeyboardEvent): boolean => (isMac ? e.metaKey : e.ctrlKey);

    const isInputFocused = (): boolean => {
      const el = document.activeElement;
      if (!el) return false;
      const tag = el.tagName.toLowerCase();
      if (tag === 'input' || tag === 'textarea' || tag === 'select') return true;
      if ((el as HTMLElement).isContentEditable) return true;
      return false;
    };

    const handleKeyDown = (e: KeyboardEvent) => {
      // ── Ctrl/Cmd + K — focus search ──────────────────────────────────────
      if (isModifierHeld(e) && e.key === 'k') {
        e.preventDefault();
        onFocusSearchRef.current?.();
        return;
      }

      // ── Ctrl/Cmd + B — toggle sidebar ────────────────────────────────────
      if (isModifierHeld(e) && e.key === 'b') {
        e.preventDefault();
        toggleCollapsedRef.current();
        return;
      }

      // ── Digit 1–9 — section navigation ───────────────────────────────────
      // Skip when the user is typing inside an input-like element
      if (!isModifierHeld(e) && !e.altKey && !isInputFocused()) {
        const route = DIGIT_ROUTES[e.key];
        if (route) {
          e.preventDefault();
          navigateRef.current(route);
        }
      }
    };

    window.addEventListener('keydown', handleKeyDown);
    return () => window.removeEventListener('keydown', handleKeyDown);
  }, []); // empty deps — refs keep values current
}
