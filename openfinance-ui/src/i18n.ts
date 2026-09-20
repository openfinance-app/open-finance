// filepath: openfinance-ui/src/i18n.ts
import i18n from 'i18next';
import { initReactI18next } from 'react-i18next';
import LanguageDetector from 'i18next-browser-languagedetector/cjs';
import Backend from 'i18next-http-backend';

// Guard against re-initialization (e.g. when the test environment pre-initializes
// the singleton via src/test/i18n-test.ts before this module is imported via apiClient).
export const i18nReady = i18n.isInitialized
  ? Promise.resolve()
  : i18n
      .use(Backend) // Load translations from /public/locales/
      .use(LanguageDetector) // Auto-detect from browser
      .use(initReactI18next) // Wire to React
      .init({
        fallbackLng: 'en',
        supportedLngs: ['en', 'fr'],
        defaultNS: 'common',
        ns: [
          'common',
          'auth',
          'onboarding',
          'history',
          'navigation',
          'dashboard',
          'accounts',
          'transactions',
          'assets',
          'liabilities',
          'budgets',
          'categories',
          'currencies',
          'import',
          'recurring',
          'realEstate',
          'rules',
          'backup',
          'settings',
          'payees',
          'institutions',
          'ai',
          'tools',
          'errors',
          'validation',
        ],
        // main.tsx waits for this initialization before rendering. All namespaces
        // load through the backend, which works in both Vite development and production.
        backend: {
          loadPath: '/locales/{{lng}}/{{ns}}.json',
          queryStringParams: { v: Date.now().toString() },
        },
        detection: {
          order: ['localStorage', 'navigator'],
          caches: ['localStorage'],
          lookupLocalStorage: 'openfinance_language',
        },
        interpolation: {
          escapeValue: false, // React already escapes
        },
        // Preload all namespaces for the current language to avoid delays
        preload: ['en', 'fr'],
        // Suppress the "Locize" support banner in test console output
        showSupportNotice: false,
        // Development: log missing keys (never during tests — too noisy)
        saveMissing: import.meta.env.DEV && import.meta.env.MODE !== 'test',
        missingKeyHandler: (lngs, ns, key) => {
          if (import.meta.env.MODE === 'test') return;
          console.warn(`[i18n] Missing translation: ${ns}:${key} for ${lngs}`);
        },
        // React integration options
        react: {
          useSuspense: false, // Disable Suspense to avoid initialization hangs on slow network
          bindI18n: 'languageChanged loaded',
          bindI18nStore: 'added',
        },
      });

export default i18n;
