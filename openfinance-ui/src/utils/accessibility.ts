/**
 * Accessibility utilities for keyboard navigation, focus management, and screen readers.
 * Implements WCAG 2.1 Level AA compliance features.
 */
import { ARIA_ANNOUNCEMENT_CLEANUP_MS } from '@/constants/timing';

/**
 * Screen reader only text (visually hidden but accessible)
 * Usage: <span className={srOnly}>Hidden text</span>
 */
export const srOnly =
  'absolute w-px h-px p-0 -m-px overflow-hidden whitespace-nowrap border-0 [clip:rect(0,0,0,0)]';

/**
 * Focus visible styles - gold outline matching design system
 */
export const focusVisible =
  'focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-accent-gold focus-visible:ring-offset-2 focus-visible:ring-offset-background-primary';

/**
 * Keyboard navigation trap for modals/dialogs
 * Traps focus within a container element
 */
export class FocusTrap {
  private container: HTMLElement;
  private focusableElements: HTMLElement[] = [];
  private firstFocusable: HTMLElement | null = null;
  private lastFocusable: HTMLElement | null = null;
  private previouslyFocused: HTMLElement | null = null;

  constructor(container: HTMLElement) {
    this.container = container;
    this.updateFocusableElements();
  }

  /**
   * Update the list of focusable elements
   */
  private updateFocusableElements(): void {
    const focusableSelectors = [
      'a[href]',
      'button:not([disabled])',
      'input:not([disabled])',
      'select:not([disabled])',
      'textarea:not([disabled])',
      '[tabindex]:not([tabindex="-1"])',
      '[contenteditable="true"]',
    ].join(',');

    this.focusableElements = Array.from(
      this.container.querySelectorAll<HTMLElement>(focusableSelectors)
    ).filter((el) => {
      // Filter out hidden elements
      return el.offsetParent !== null && !el.hasAttribute('aria-hidden');
    });

    this.firstFocusable = this.focusableElements[0] || null;
    this.lastFocusable =
      this.focusableElements[this.focusableElements.length - 1] || null;
  }

  /**
   * Handle tab key press
   */
  private handleTab = (event: KeyboardEvent): void => {
    if (event.key !== 'Tab') return;

    this.updateFocusableElements();

    if (!this.firstFocusable || !this.lastFocusable) return;

    if (event.shiftKey) {
      // Shift + Tab
      if (document.activeElement === this.firstFocusable) {
        event.preventDefault();
        this.lastFocusable.focus();
      }
    } else {
      // Tab
      if (document.activeElement === this.lastFocusable) {
        event.preventDefault();
        this.firstFocusable.focus();
      }
    }
  };

  /**
   * Activate the focus trap
   */
  activate(): void {
    this.previouslyFocused = document.activeElement as HTMLElement;
    this.updateFocusableElements();

    if (this.firstFocusable) {
      this.firstFocusable.focus();
    }

    this.container.addEventListener('keydown', this.handleTab);
  }

  /**
   * Deactivate the focus trap and restore previous focus
   */
  deactivate(): void {
    this.container.removeEventListener('keydown', this.handleTab);

    if (this.previouslyFocused && this.previouslyFocused.focus) {
      this.previouslyFocused.focus();
    }
  }
}

/**
 * React hook for managing focus trap
 */
export function useFocusTrap(enabled: boolean): (node: HTMLElement | null) => void {
  let focusTrap: FocusTrap | null = null;

  return (node: HTMLElement | null) => {
    if (focusTrap) {
      focusTrap.deactivate();
      focusTrap = null;
    }

    if (node && enabled) {
      focusTrap = new FocusTrap(node);
      focusTrap.activate();
    }
  };
}

/**
 * Announce message to screen readers using ARIA live region
 */
export function announceToScreenReader(
  message: string,
  priority: 'polite' | 'assertive' = 'polite'
): void {
  const announcement = document.createElement('div');
  announcement.setAttribute('role', 'status');
  announcement.setAttribute('aria-live', priority);
  announcement.setAttribute('aria-atomic', 'true');
  announcement.className = srOnly;
  announcement.textContent = message;

  document.body.appendChild(announcement);

  // Remove after announcement
  setTimeout(() => {
    if (announcement.parentNode === document.body) {
      document.body.removeChild(announcement);
    }
  }, ARIA_ANNOUNCEMENT_CLEANUP_MS);
}

/**
 * Check if an element is currently visible and in viewport
 */
export function isElementVisible(element: HTMLElement): boolean {
  if (!element.offsetParent) return false;

  const rect = element.getBoundingClientRect();
  const viewHeight = Math.max(
    document.documentElement.clientHeight,
    window.innerHeight
  );

  return !(rect.bottom < 0 || rect.top - viewHeight >= 0);
}

/**
 * Get next focusable element in direction
 */
export function getNextFocusableElement(
  current: HTMLElement,
  direction: 'next' | 'previous'
): HTMLElement | null {
  const focusableSelectors = [
    'a[href]',
    'button:not([disabled])',
    'input:not([disabled])',
    'select:not([disabled])',
    'textarea:not([disabled])',
    '[tabindex]:not([tabindex="-1"])',
  ].join(',');

  const focusableElements = Array.from(
    document.querySelectorAll<HTMLElement>(focusableSelectors)
  ).filter((el) => el.offsetParent !== null);

  const currentIndex = focusableElements.indexOf(current);
  if (currentIndex === -1) return null;

  const nextIndex =
    direction === 'next' ? currentIndex + 1 : currentIndex - 1;

  if (nextIndex < 0 || nextIndex >= focusableElements.length) return null;

  return focusableElements[nextIndex];
}

/**
 * Generate unique ID for ARIA labelledby/describedby
 */
let idCounter = 0;
export function generateA11yId(prefix = 'a11y'): string {
  idCounter++;
  return `${prefix}-${idCounter}-${Date.now()}`;
}

/**
 * Keyboard event utilities
 */
export const Keys = {
  ENTER: 'Enter',
  SPACE: ' ',
  ESCAPE: 'Escape',
  ARROW_UP: 'ArrowUp',
  ARROW_DOWN: 'ArrowDown',
  ARROW_LEFT: 'ArrowLeft',
  ARROW_RIGHT: 'ArrowRight',
  TAB: 'Tab',
  HOME: 'Home',
  END: 'End',
  PAGE_UP: 'PageUp',
  PAGE_DOWN: 'PageDown',
} as const;

/**
 * Check if key matches any of the provided keys
 */
export function isKey(event: KeyboardEvent, ...keys: string[]): boolean {
  return keys.includes(event.key);
}

/**
 * Handle keyboard activation (Enter or Space)
 */
export function handleActivation(
  event: KeyboardEvent,
  callback: () => void
): void {
  if (isKey(event, Keys.ENTER, Keys.SPACE)) {
    event.preventDefault();
    callback();
  }
}

/**
 * Skip link for keyboard navigation
 * Returns props to apply to skip link element
 */
export function getSkipLinkProps(targetId: string) {
  return {
    href: `#${targetId}`,
    className: `${srOnly} focus:not-sr-only focus:absolute focus:top-4 focus:left-4 focus:z-50 focus:px-4 focus:py-2 focus:bg-surface-raised focus:text-text-primary focus:rounded ${focusVisible}`,
    onClick: (e: React.MouseEvent<HTMLAnchorElement>) => {
      e.preventDefault();
      const target = document.getElementById(targetId);
      if (target) {
        target.focus();
        target.scrollIntoView({ behavior: 'smooth', block: 'start' });
      }
    },
  };
}

/**
 * ARIA label utilities
 */
export const ariaLabel = {
  /**
   * Create accessible label for money amount
   */
  money: (amount: number, currency: string): string => {
    const formatter = new Intl.NumberFormat('en-US', {
      style: 'currency',
      currency: currency,
    });
    return formatter.format(amount);
  },

  /**
   * Create accessible label for percentage
   */
  percentage: (value: number, decimals = 2): string => {
    return `${value.toFixed(decimals)} percent`;
  },

  /**
   * Create accessible label for date
   */
  date: (date: Date): string => {
    return date.toLocaleDateString('en-US', {
      year: 'numeric',
      month: 'long',
      day: 'numeric',
    });
  },

  /**
   * Create accessible label for gain/loss
   */
  gainLoss: (amount: number, currency: string): string => {
    const isGain = amount >= 0;
    const label = isGain ? 'gain' : 'loss';
    const absAmount = Math.abs(amount);
    return `${label} of ${ariaLabel.money(absAmount, currency)}`;
  },
};

/**
 * ARIA props for common patterns
 */
export const ariaProps = {
  /**
   * Loading state
   */
  loading: (label = 'Loading'): Record<string, string | boolean> => ({
    'aria-busy': 'true',
    'aria-label': label,
    role: 'status',
  }),

  /**
   * Alert/notification
   */
  alert: (
    type: 'info' | 'success' | 'warning' | 'error' = 'info'
  ): Record<string, string> => ({
    role: 'alert',
    'aria-live': type === 'error' ? 'assertive' : 'polite',
  }),

  /**
   * Dialog/modal
   */
  dialog: (
    labelId: string,
    descriptionId?: string
  ): Record<string, string | boolean> => ({
    role: 'dialog',
    'aria-modal': 'true',
    'aria-labelledby': labelId,
    ...(descriptionId && { 'aria-describedby': descriptionId }),
  }),

  /**
   * Menu/dropdown
   */
  menu: (expanded: boolean): Record<string, string | boolean> => ({
    role: 'menu',
    'aria-expanded': String(expanded),
  }),

  /**
   * Button with expanded state (e.g., accordion, dropdown)
   */
  expandButton: (
    expanded: boolean,
    controlsId?: string
  ): Record<string, string> => ({
    'aria-expanded': String(expanded),
    ...(controlsId && { 'aria-controls': controlsId }),
  }),
};

/**
 * Check if user prefers reduced motion
 */
export function prefersReducedMotion(): boolean {
  return window.matchMedia('(prefers-reduced-motion: reduce)').matches;
}

/**
 * Apply animation only if user doesn't prefer reduced motion
 */
export function getAnimationClass(animationClass: string): string {
  return prefersReducedMotion() ? '' : animationClass;
}
