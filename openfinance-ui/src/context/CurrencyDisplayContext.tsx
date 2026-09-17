/**
 * CurrencyDisplayContext - Manages the user's preferred amount display mode
 * and optional secondary currency for tooltip comparison.
 *
 * Reference: REQ-11.1, REQ-11.2, REQ-11.3, REQ-6.1, REQ-6.2, REQ-6.3, REQ-6.4
 *
 * Supports three display modes:
 *   - 'base'   : Show amount in user's base currency (tooltip: native + secondary)
 *   - 'native' : Show native currency amount (tooltip: base + secondary)
 *   - 'both'   : Show both currencies inline (tooltip: secondary only)
 *
 * The display mode is persisted in localStorage under `open_finance_amount_display_mode`.
 * The secondary currency is persisted under `open_finance_secondary_currency`.
 * Both default to `null`/`'base'` when absent.
 */
import { createContext, useContext, useState, useEffect, useCallback, useMemo } from 'react';
import type { ReactNode } from 'react';

/** The three possible amount display modes. */
export type AmountDisplayMode = 'base' | 'native' | 'both';

const DISPLAY_MODE_KEY = 'open_finance_amount_display_mode';
const SECONDARY_CURRENCY_KEY = 'open_finance_secondary_currency';
const DEFAULT_MODE: AmountDisplayMode = 'base';

interface CurrencyDisplayContextType {
  /** Currently active display mode */
  displayMode: AmountDisplayMode;
  /** Update the display mode and persist to localStorage */
  setDisplayMode: (mode: AmountDisplayMode) => void;
  /**
   * ISO 4217 secondary currency code used for tooltip comparison.
   * Null means no secondary currency is configured.
   * Requirement REQ-6.1, REQ-6.2
   */
  secondaryCurrency: string | null;
  /**
   * Set (or clear) the secondary currency and persist to localStorage.
   * Pass null to remove the secondary currency preference.
   * Requirement REQ-6.3, REQ-6.4
   */
  setSecondaryCurrency: (code: string | null) => void;
}

const CurrencyDisplayContext = createContext<CurrencyDisplayContextType | undefined>(undefined);

interface CurrencyDisplayProviderProps {
  children: ReactNode;
}

/**
 * Reads the stored display mode from localStorage, falling back to DEFAULT_MODE
 * if the value is absent or invalid.
 */
function readStoredMode(): AmountDisplayMode {
  try {
    const stored = localStorage.getItem(DISPLAY_MODE_KEY);
    if (stored === 'base' || stored === 'native' || stored === 'both') {
      return stored;
    }
  } catch {
    // localStorage may be unavailable (SSR or private mode)
  }
  return DEFAULT_MODE;
}

/**
 * Reads the stored secondary currency from localStorage.
 * Returns null if absent or localStorage is unavailable.
 *
 * Requirement REQ-6.1: initialise from localStorage on mount
 */
function readStoredSecondaryCurrency(): string | null {
  try {
    return localStorage.getItem(SECONDARY_CURRENCY_KEY) ?? null;
  } catch {
    return null;
  }
}

/**
 * CurrencyDisplayProvider
 *
 * Wrap the application (or a sub-tree) with this provider to make the
 * currency display preferences available throughout the component tree.
 */
export function CurrencyDisplayProvider({ children }: CurrencyDisplayProviderProps) {
  const [displayMode, setDisplayModeState] = useState<AmountDisplayMode>(readStoredMode);
  const [secondaryCurrency, setSecondaryCurrencyState] = useState<string | null>(
    readStoredSecondaryCurrency
  );

  /** Persist the display mode preference whenever it changes. */
  useEffect(() => {
    try {
      localStorage.setItem(DISPLAY_MODE_KEY, displayMode);
    } catch (error) {
      console.error('Failed to persist currency display mode:', error);
    }
  }, [displayMode]);

  /**
   * Persist the secondary currency whenever it changes.
   * Removes the key when null to keep localStorage clean.
   * Requirement REQ-6.3, REQ-6.4
   */
  useEffect(() => {
    try {
      if (secondaryCurrency) {
        localStorage.setItem(SECONDARY_CURRENCY_KEY, secondaryCurrency);
      } else {
        localStorage.removeItem(SECONDARY_CURRENCY_KEY);
      }
    } catch (error) {
      console.error('Failed to persist secondary currency:', error);
    }
  }, [secondaryCurrency]);

  /** Update display mode state. */
  const setDisplayMode = useCallback((mode: AmountDisplayMode) => {
    setDisplayModeState(mode);
  }, []);

  /**
   * Update secondary currency state.
   * Accepts an ISO 4217 code string or null to clear the preference.
   * Requirement REQ-6.3
   */
  const setSecondaryCurrency = useCallback((code: string | null) => {
    setSecondaryCurrencyState(code && code.trim() ? code.trim() : null);
  }, []);

  const value: CurrencyDisplayContextType = useMemo(
    () => ({ displayMode, setDisplayMode, secondaryCurrency, setSecondaryCurrency }),
    [displayMode, setDisplayMode, secondaryCurrency, setSecondaryCurrency]
  );

  return (
    <CurrencyDisplayContext.Provider value={value}>{children}</CurrencyDisplayContext.Provider>
  );
}

/**
 * Hook to access the currency display context.
 *
 * Must be used inside a {@link CurrencyDisplayProvider}.
 *
 * @throws {Error} When called outside a CurrencyDisplayProvider
 */
export function useCurrencyDisplay(): CurrencyDisplayContextType {
  const context = useContext(CurrencyDisplayContext);
  if (context === undefined) {
    throw new Error('useCurrencyDisplay must be used within a CurrencyDisplayProvider');
  }
  return context;
}
