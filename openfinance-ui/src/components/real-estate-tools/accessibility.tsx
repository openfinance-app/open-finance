/**
 * Accessibility Helpers for Real Estate Tools
 * 
 * ARIA labels, keyboard navigation, and screen reader announcements
 * Requirements: REQ-3.3.x, REQ-6.6
 */

import React, { useEffect, useCallback } from 'react';
import { announceToScreenReader } from '@/utils/accessibility';
import i18n from '@/i18n';

/**
 * Hook to announce messages to screen readers.
 *
 * Delegates to the shared {@link announceToScreenReader} (previously this hook duplicated the
 * same ARIA-live-region DOM logic independently).
 */
export function useAnnouncer() {
  const announce = useCallback((message: string, priority: 'polite' | 'assertive' = 'polite') => {
    announceToScreenReader(message, priority);
  }, []);

  return { announce };
}

/**
 * Hook for keyboard shortcuts
 */
export function useKeyboardShortcuts(shortcuts: {
  [key: string]: () => void;
}) {
  useEffect(() => {
    const handleKeyDown = (event: KeyboardEvent) => {
      // Don't trigger shortcuts when typing in input fields
      if (event.target instanceof HTMLInputElement ||
          event.target instanceof HTMLTextAreaElement ||
          event.target instanceof HTMLSelectElement) {
        return;
      }

      const key = event.key.toLowerCase();
      const ctrl = event.ctrlKey || event.metaKey;
      const shift = event.shiftKey;
      const alt = event.altKey;

      // Build shortcut key string
      let shortcutKey = '';
      if (ctrl) shortcutKey += 'ctrl+';
      if (shift) shortcutKey += 'shift+';
      if (alt) shortcutKey += 'alt+';
      shortcutKey += key;

      if (shortcuts[shortcutKey]) {
        event.preventDefault();
        shortcuts[shortcutKey]();
      }
    };

    window.addEventListener('keydown', handleKeyDown);
    return () => window.removeEventListener('keydown', handleKeyDown);
  }, [shortcuts]);
}

/**
 * ARIA labels for common actions
 */
export const ariaLabels = {
  calculate: i18n.t('realEstate.accessibility.calculate'),
  reset: i18n.t('realEstate.accessibility.reset'),
  save: i18n.t('realEstate.accessibility.save'),
  load: i18n.t('realEstate.accessibility.load'),
  delete: i18n.t('realEstate.accessibility.delete'),
  export: i18n.t('realEstate.accessibility.export'),
  navigateToRental: i18n.t('realEstate.accessibility.navigateToRental'),
  navigateBack: i18n.t('realEstate.accessibility.navigateBack'),
  purchaseSection: i18n.t('realEstate.accessibility.purchaseSection'),
  rentalSection: i18n.t('realEstate.accessibility.rentalSection'),
  marketSection: i18n.t('realEstate.accessibility.marketSection'),
  resaleSection: i18n.t('realEstate.accessibility.resaleSection'),
  propertySection: i18n.t('realEstate.accessibility.propertySection'),
  revenueSection: i18n.t('realEstate.accessibility.revenueSection'),
  expensesSection: i18n.t('realEstate.accessibility.expensesSection'),
  summaryCards: i18n.t('realEstate.accessibility.summaryCards'),
  comparisonTable: i18n.t('realEstate.accessibility.comparisonTable'),
  evolutionChart: i18n.t('realEstate.accessibility.evolutionChart'),
  yearlyTable: i18n.t('realEstate.accessibility.yearlyTable'),
  inputsTab: i18n.t('realEstate.accessibility.inputsTab'),
  resultsTab: i18n.t('realEstate.accessibility.resultsTab'),
  microFoncier: i18n.t('realEstate.accessibility.microFoncier'),
  reelFoncier: i18n.t('realEstate.accessibility.reelFoncier'),
  lmnpReel: i18n.t('realEstate.accessibility.lmnpReel'),
  microBic: i18n.t('realEstate.accessibility.microBic'),
};

/**
 * Get ARIA label for a regime
 */
export function getRegimeAriaLabel(regime: string): string {
  const labels: Record<string, string> = {
    micro_foncier: ariaLabels.microFoncier,
    reel_foncier: ariaLabels.reelFoncier,
    lmnp_reel: ariaLabels.lmnpReel,
    micro_bic: ariaLabels.microBic,
  };
  return labels[regime] || regime;
}

/**
 * Skip to content link component
 */
export const SkipToContent: React.FC<{ contentId: string }> = ({ contentId }) => {
  return (
    <a
      href={`#${contentId}`}
      className="sr-only focus:not-sr-only focus:absolute focus:top-4 focus:left-4 
                 bg-primary text-primary-foreground px-4 py-2 rounded z-50"
    >
      {i18n.t('realEstate.accessibility.skipToContent')}
    </a>
  );
};

/**
 * Live region for dynamic updates
 */
export const LiveRegion: React.FC<{
  id: string;
  children: React.ReactNode;
  priority?: 'polite' | 'assertive';
}> = ({ id, children, priority = 'polite' }) => {
  return (
    <div
      id={id}
      role="status"
      aria-live={priority}
      aria-atomic="true"
      className="sr-only"
    >
      {children}
    </div>
  );
};

/**
 * Visually hidden text for screen readers
 */
export const VisuallyHidden: React.FC<{ children: React.ReactNode }> = ({ children }) => {
  return <span className="sr-only">{children}</span>;
};

/**
 * Focus trap for modals
 */
export function useFocusTrap(isActive: boolean, containerRef: React.RefObject<HTMLElement>) {
  useEffect(() => {
    if (!isActive || !containerRef.current) return;

    const container = containerRef.current;
    const focusableElements = container.querySelectorAll<HTMLElement>(
      'button, [href], input, select, textarea, [tabindex]:not([tabindex="-1"])'
    );
    
    const firstElement = focusableElements[0];
    const lastElement = focusableElements[focusableElements.length - 1];

    const handleTabKey = (e: KeyboardEvent) => {
      if (e.key !== 'Tab') return;

      if (e.shiftKey) {
        if (document.activeElement === firstElement) {
          e.preventDefault();
          lastElement?.focus();
        }
      } else {
        if (document.activeElement === lastElement) {
          e.preventDefault();
          firstElement?.focus();
        }
      }
    };

    container.addEventListener('keydown', handleTabKey);
    firstElement?.focus();

    return () => container.removeEventListener('keydown', handleTabKey);
  }, [isActive, containerRef]);
}

export default {
  useAnnouncer,
  useKeyboardShortcuts,
  ariaLabels,
  getRegimeAriaLabel,
  SkipToContent,
  LiveRegion,
  VisuallyHidden,
  useFocusTrap,
};
