/**
 * Accessibility Helpers for Real Estate Tools
 * 
 * ARIA labels, keyboard navigation, and screen reader announcements
 * Requirements: REQ-3.3.x, REQ-6.6
 */

import React, { useEffect, useCallback } from 'react';
import { announceToScreenReader } from '@/utils/accessibility';

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
  // Actions
  calculate: 'Calculer la simulation',
  reset: 'Réinitialiser tous les paramètres',
  save: 'Sauvegarder la simulation',
  load: 'Charger une simulation sauvegardée',
  delete: 'Supprimer la simulation',
  export: 'Exporter les résultats',
  navigateToRental: 'Passer au simulateur de location',
  navigateBack: 'Retourner au comparateur',
  
  // Input sections
  purchaseSection: 'Paramètres d\'achat',
  rentalSection: 'Paramètres de location',
  marketSection: 'Évolution du marché immobilier',
  resaleSection: 'Objectifs de revente',
  propertySection: 'Bien immobilier',
  revenueSection: 'Revenus locatifs',
  expensesSection: 'Charges propriétaire',
  
  // Results
  summaryCards: 'Résumé des scénarios',
  comparisonTable: 'Tableau comparatif détaillé',
  evolutionChart: 'Graphique d\'évolution du patrimoine',
  yearlyTable: 'Tableau année par année',
  
  // Navigation
  inputsTab: 'Onglet des paramètres de saisie',
  resultsTab: 'Onglet des résultats',
  
  // Regimes
  microFoncier: 'Régime Micro-Foncier',
  reelFoncier: 'Régime Réel Foncier',
  lmnpReel: 'Régime LMNP Réel',
  microBic: 'Régime Micro-BIC',
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
      Aller au contenu principal
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
