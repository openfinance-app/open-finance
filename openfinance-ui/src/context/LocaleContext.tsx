/**
 * LocaleContext — Locale propagation for date-fns and HTML lang attribute
 *
 * Exposes:
 *  - `locale`        current i18next language code (e.g. 'en', 'fr')
 *  - `dateFnsLocale` matching date-fns Locale object for date formatting helpers
 *  - `setLocale`     change the active language, update <html lang>, and persist
 *                    the preference to the backend UserSettings API
 *  - `isChangingLocale` loading state during language switch
 *
 * Requirements: REQ-3.1.2, REQ-4.3
 */
import React, { createContext, useContext, useEffect, useState } from 'react';
import { useTranslation } from 'react-i18next';
import { enUS, fr } from 'date-fns/locale';
import type { Locale as DateFnsLocale } from 'date-fns';
import apiClient from '../services/apiClient';
import { STORAGE_KEYS } from '@/constants/storage';

// Map i18next language codes to date-fns locale objects
const DATE_FNS_LOCALES: Record<string, DateFnsLocale> = {
  en: enUS,
  fr: fr,
};

interface LocaleContextValue {
  /** Current i18next language code, e.g. 'en' or 'fr' */
  locale: string;
  /** Corresponding date-fns locale object for formatting helpers */
  dateFnsLocale: DateFnsLocale;
  /** Change the active locale, update <html lang>, and persist to backend */
  setLocale: (locale: string) => Promise<void>;
  /** Loading state during language switch */
  isChangingLocale: boolean;
}

const LocaleContext = createContext<LocaleContextValue>({
  locale: 'en',
  dateFnsLocale: enUS,
  setLocale: async () => { },
  isChangingLocale: false,
});

/**
 * Persist locale preference to backend.
 * Only calls the API if the user is authenticated (token exists).
 * Errors are swallowed — the locale change still applies locally.
 */
async function persistLocaleToBackend(locale: string): Promise<void> {
  const token = localStorage.getItem(STORAGE_KEYS.AUTH_TOKEN) || sessionStorage.getItem(STORAGE_KEYS.AUTH_TOKEN);
  if (!token) {
    // User is not logged in — store flag so AppLayout can sync it after login.
    sessionStorage.setItem(STORAGE_KEYS.PENDING_LANGUAGE_SYNC, locale);
    return;
  }

  try {
    await apiClient.put('/users/me/settings', { language: locale });
  } catch (error: any) {
    if (error?.response?.status === 401 || error?.response?.status === 403) {
      sessionStorage.setItem(STORAGE_KEYS.PENDING_LANGUAGE_SYNC, locale);
    } else if (import.meta.env.MODE !== 'test') {
      console.warn('[LocaleContext] Failed to persist locale preference to backend:', error);
    }
  }
}

// All namespaces defined in i18n.ts
const ALL_NAMESPACES = [
  'common', 'auth', 'navigation', 'dashboard', 'accounts', 'transactions',
  'assets', 'liabilities', 'budgets', 'categories', 'import', 'recurring',
  'realEstate', 'rules', 'backup', 'settings', 'ai', 'tools', 'errors', 'validation'
];

export const LocaleProvider: React.FC<{ children: React.ReactNode }> = ({ children }) => {
  const { i18n } = useTranslation();
  const [isChangingLocale, setIsChangingLocale] = useState(false);

  /**
   * Resolve the active language code.
   * i18n.resolvedLanguage is the language actually used after fallback/detection.
   */
  const currentLanguage = i18n.resolvedLanguage || i18n.language || 'en';
  const resolvedLocale = currentLanguage.split('-')[0];

  // Sync <html lang> whenever i18next language changes
  useEffect(() => {
    document.documentElement.lang = resolvedLocale;
  }, [resolvedLocale]);

  const setLocale = async (locale: string): Promise<void> => {
    // Don't change if already on this locale
    if (locale === resolvedLocale) return;

    setIsChangingLocale(true);
    try {
      if (import.meta.env.MODE !== 'test') {
        console.log(`[LocaleContext] Switching language to: ${locale}`);
      }

      // 1. Change the core language
      await i18n.changeLanguage(locale);

      // 2. Explicitly load all namespaces to ensure all components refresh immediately.
      // This fixes the "partial update" bug where sidebar or cards might stay in old language.
      await i18n.loadNamespaces(ALL_NAMESPACES);

      // Update HTML lang attribute
      document.documentElement.lang = locale;

      // 3. Persist to backend
      void persistLocaleToBackend(locale);

      if (import.meta.env.MODE !== 'test') {
        console.log(`[LocaleContext] Successfully switched to: ${locale}`);
      }
    } catch (error) {
      if (import.meta.env.MODE !== 'test') {
        console.error('[LocaleContext] Failed to change language:', error);
      }
    } finally {
      setIsChangingLocale(false);
    }
  };

  return (
    <LocaleContext.Provider
      value={{
        locale: resolvedLocale,
        dateFnsLocale: DATE_FNS_LOCALES[resolvedLocale] ?? enUS,
        setLocale,
        isChangingLocale,
      }}
    >
      {children}
    </LocaleContext.Provider>
  );
};

/** Hook to access locale context. Must be used within LocaleProvider. */
export const useLocale = (): LocaleContextValue => useContext(LocaleContext);
