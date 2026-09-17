/**
 * NumberFormatContext — global number/currency format preference
 *
 * Supports three formats:
 *   - '1,234.56'  — comma thousands separator, dot decimal  (US/UK style)
 *   - '1.234,56'  — dot thousands separator, comma decimal  (European style)
 *   - '1 234,56'  — space thousands separator, comma decimal (French/Swiss style)
 *
 * Persistence strategy (mirrors ThemeContext):
 *   - Reads initial value from the backend UserSettings API on mount.
 *   - Falls back to localStorage (`open_finance_number_format`) when offline.
 *   - Persists changes to the backend via PUT /users/me/settings and keeps
 *     localStorage in sync as a fast-read cache across page refreshes.
 *   - Reverts to the previously persisted value on API failure.
 *
 * i18n locale default (Task 1.5.3):
 *   - When the user has no explicit `numberFormat` saved in UserSettings, the
 *     default format tracks the active locale:
 *       'fr' → '1 234,56'  (French/Swiss style)
 *       'en' → '1,234.56'  (US/UK style)
 *   - If the user has ever explicitly saved a format override, it is preserved
 *     and will NOT be changed by locale switching.
 *
 * Usage:
 *   Wrap the application with <NumberFormatProvider> (inside QueryClientProvider
 *   and AuthProvider so the hook can call the API).
 *
 *   const { numberFormat, setNumberFormat } = useNumberFormat();
 */
import { createContext, useContext, useEffect, useState, useCallback, useMemo } from 'react';
import type { ReactNode } from 'react';
import { useTranslation } from 'react-i18next';
import { useUserSettings, useUpdateUserSettings } from '@/hooks/useUserSettings';

/** The three supported number format strings. */
export type NumberFormat = '1,234.56' | '1.234,56' | '1 234,56';

const LS_KEY = 'open_finance_number_format';
const DEFAULT_FORMAT: NumberFormat = '1,234.56';

/** Map from i18next language codes to locale-appropriate default number formats. */
const LOCALE_DEFAULT_FORMAT: Record<string, NumberFormat> = {
  fr: '1 234,56',
  en: '1,234.56',
};

interface NumberFormatContextType {
  /** Currently active number format */
  numberFormat: NumberFormat;
  /** Whether the backend settings are still loading */
  isLoading: boolean;
  /**
   * Change the number format preference.
   * Persists to the backend and keeps localStorage in sync.
   * Reverts on API failure.
   */
  setNumberFormat: (format: NumberFormat) => void;
}

const NumberFormatContext = createContext<NumberFormatContextType | undefined>(undefined);

/** Guard: returns value only if it is one of the three recognised formats. */
function isValidFormat(value: string | null | undefined): value is NumberFormat {
  return value === '1,234.56' || value === '1.234,56' || value === '1 234,56';
}

/** Read the cached preference from localStorage (fast synchronous init). */
function readStoredFormat(): NumberFormat {
  try {
    const stored = localStorage.getItem(LS_KEY);
    if (isValidFormat(stored)) return stored;
  } catch {
    // localStorage may be unavailable (SSR, private mode, etc.)
  }
  return DEFAULT_FORMAT;
}

interface NumberFormatProviderProps {
  children: ReactNode;
}

export function NumberFormatProvider({ children }: NumberFormatProviderProps) {
  const { data: settings, isLoading } = useUserSettings();
  const updateSettings = useUpdateUserSettings();
  const { i18n } = useTranslation();

  // Initialise from localStorage so the UI is correct before the API responds.
  const [numberFormat, setNumberFormatState] = useState<NumberFormat>(readStoredFormat);

  // Once backend settings arrive, sync local state and refresh localStorage.
  useEffect(() => {
    if (settings?.numberFormat && isValidFormat(settings.numberFormat)) {
      setNumberFormatState(settings.numberFormat);
      try {
        localStorage.setItem(LS_KEY, settings.numberFormat);
      } catch {
        /* ignore */
      }
    }
  }, [settings]);

  /**
   * When the locale changes and the user has NOT saved an explicit number
   * format override, apply the locale-appropriate default format.
   */
  useEffect(() => {
    if (!isLoading && !settings?.numberFormat) {
      const lang = i18n.language?.split('-')[0] ?? 'en';
      const localeDefault = LOCALE_DEFAULT_FORMAT[lang] ?? DEFAULT_FORMAT;
      setNumberFormatState(localeDefault);
      try {
        localStorage.setItem(LS_KEY, localeDefault);
      } catch {
        /* ignore */
      }
    }
  }, [i18n.language, isLoading, settings?.numberFormat]);

  const setNumberFormat = useCallback(
    (newFormat: NumberFormat) => {
      // Optimistic update — instant UI feedback.
      setNumberFormatState(newFormat);
      try {
        localStorage.setItem(LS_KEY, newFormat);
      } catch {
        /* ignore */
      }

      updateSettings.mutate(
        { numberFormat: newFormat },
        {
          onError: () => {
            // Revert to the previously persisted format on failure.
            const previous: NumberFormat = isValidFormat(settings?.numberFormat)
              ? settings!.numberFormat
              : DEFAULT_FORMAT;
            setNumberFormatState(previous);
            try {
              localStorage.setItem(LS_KEY, previous);
            } catch {
              /* ignore */
            }
          },
        }
      );
    },
    [settings, updateSettings]
  );

  const value = useMemo<NumberFormatContextType>(
    () => ({ numberFormat, isLoading, setNumberFormat }),
    [numberFormat, isLoading, setNumberFormat]
  );

  return <NumberFormatContext.Provider value={value}>{children}</NumberFormatContext.Provider>;
}

/**
 * Hook to access the number format context.
 * Must be used inside <NumberFormatProvider>.
 */
export function useNumberFormat(): NumberFormatContextType {
  const context = useContext(NumberFormatContext);
  if (context === undefined) {
    throw new Error('useNumberFormat must be used within a NumberFormatProvider');
  }
  return context;
}
