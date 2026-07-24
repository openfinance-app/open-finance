/**
 * DecimalPlacesContext — global "preferred decimal places" display preference.
 *
 * When enabled, the user's chosen number of decimal places (1-8) overrides the default
 * per-currency decimal count for EVERY currency in the UI. Display-only: it never changes
 * stored amounts or calculations.
 *
 * Persistence strategy (mirrors NumberFormatContext):
 *   - Reads initial value from the backend UserSettings API on mount.
 *   - Falls back to localStorage (`open_finance_decimal_places`) for first paint.
 *   - Persists changes via PUT /users/me/settings and keeps localStorage in sync.
 *   - Reverts to the previously persisted value on API failure.
 *
 * It also pushes the *effective* override (enabled ? places : null) into the module-level
 * override in `utils/currency.ts` via `setDecimalPlacesOverride`, so every formatter that
 * funnels through `getCurrencyDecimals` honors the preference. The effective value is also
 * exposed via `useDecimalPlaces()` so the primary formatting path (`useFormatCurrency`)
 * re-renders reactively when the preference changes.
 */
import {
  createContext,
  useContext,
  useEffect,
  useLayoutEffect,
  useMemo,
  useState,
  useCallback,
} from 'react';
import type { ReactNode } from 'react';
import { useUserSettings, useUpdateUserSettings } from '@/hooks/useUserSettings';
import { setDecimalPlacesOverride } from '@/utils/currency';

const LS_KEY = 'open_finance_decimal_places';
const MIN_PLACES = 1;
const MAX_PLACES = 8;
const DEFAULT_PLACES = 2;

interface StoredPref {
  enabled: boolean;
  places: number;
}

interface DecimalPlacesContextType {
  /** Whether the override is currently active. */
  overrideEnabled: boolean;
  /** The chosen number of decimal places (1-8), used when overrideEnabled is true. */
  decimalPlaces: number;
  /** enabled ? places : null — what formatters should apply. */
  effectiveDecimals: number | null;
  /** Whether backend settings are still loading. */
  isLoading: boolean;
  /** Enable/disable the override (persisted, optimistic, reverts on error). */
  setOverrideEnabled: (enabled: boolean) => void;
  /** Change the number of decimal places (clamped 1-8; persisted, optimistic). */
  setDecimalPlaces: (places: number) => void;
}

const DecimalPlacesContext = createContext<DecimalPlacesContextType | undefined>(undefined);

function clampPlaces(value: number): number {
  if (!Number.isFinite(value)) return DEFAULT_PLACES;
  return Math.min(MAX_PLACES, Math.max(MIN_PLACES, Math.floor(value)));
}

function readStored(): StoredPref {
  try {
    const raw = localStorage.getItem(LS_KEY);
    if (raw) {
      const parsed = JSON.parse(raw) as Partial<StoredPref>;
      return {
        enabled: parsed.enabled === true,
        places: clampPlaces(typeof parsed.places === 'number' ? parsed.places : DEFAULT_PLACES),
      };
    }
  } catch {
    // localStorage unavailable or malformed — fall through to defaults.
  }
  return { enabled: false, places: DEFAULT_PLACES };
}

function writeStored(pref: StoredPref): void {
  try {
    localStorage.setItem(LS_KEY, JSON.stringify(pref));
  } catch {
    /* ignore */
  }
}

interface DecimalPlacesProviderProps {
  children: ReactNode;
}

export function DecimalPlacesProvider({ children }: DecimalPlacesProviderProps) {
  const { data: settings, isLoading } = useUserSettings();
  const updateSettings = useUpdateUserSettings();

  const [pref, setPref] = useState<StoredPref>(readStored);

  const effectiveDecimals = pref.enabled ? pref.places : null;

  // Push the effective override into the currency module BEFORE paint to avoid a flash.
  useLayoutEffect(() => {
    setDecimalPlacesOverride(effectiveDecimals);
  }, [effectiveDecimals]);

  // Once backend settings arrive, sync local state + localStorage.
  useEffect(() => {
    if (settings && typeof settings.preferredDecimalPlaces === 'number') {
      const next: StoredPref = {
        enabled: settings.decimalPlacesOverrideEnabled === true,
        places: clampPlaces(settings.preferredDecimalPlaces),
      };
      setPref(next);
      writeStored(next);
    }
  }, [settings]);

  const persist = useCallback(
    (next: StoredPref) => {
      setPref(next);
      writeStored(next);
      updateSettings.mutate(
        {
          decimalPlacesOverrideEnabled: next.enabled,
          preferredDecimalPlaces: next.places,
        },
        {
          onError: () => {
            const reverted: StoredPref = {
              enabled: settings?.decimalPlacesOverrideEnabled === true,
              places: clampPlaces(
                typeof settings?.preferredDecimalPlaces === 'number'
                  ? settings.preferredDecimalPlaces
                  : DEFAULT_PLACES
              ),
            };
            setPref(reverted);
            writeStored(reverted);
          },
        }
      );
    },
    [settings, updateSettings]
  );

  const setOverrideEnabled = useCallback(
    (enabled: boolean) => persist({ enabled, places: pref.places }),
    [persist, pref.places]
  );

  const setDecimalPlaces = useCallback(
    (places: number) => persist({ enabled: pref.enabled, places: clampPlaces(places) }),
    [persist, pref.enabled]
  );

  const value = useMemo<DecimalPlacesContextType>(
    () => ({
      overrideEnabled: pref.enabled,
      decimalPlaces: pref.places,
      effectiveDecimals,
      isLoading,
      setOverrideEnabled,
      setDecimalPlaces,
    }),
    [pref, effectiveDecimals, isLoading, setOverrideEnabled, setDecimalPlaces]
  );

  return <DecimalPlacesContext.Provider value={value}>{children}</DecimalPlacesContext.Provider>;
}

/** Access the decimal-places preference. Must be used inside <DecimalPlacesProvider>. */
export function useDecimalPlaces(): DecimalPlacesContextType {
  const context = useContext(DecimalPlacesContext);
  if (context === undefined) {
    throw new Error('useDecimalPlaces must be used within a DecimalPlacesProvider');
  }
  return context;
}
