// filepath: openfinance-ui/src/i18n.ts
import i18n from 'i18next';
import { initReactI18next } from 'react-i18next';
import LanguageDetector from 'i18next-browser-languagedetector/cjs';
import Backend from 'i18next-http-backend';

// Bundle frequently-used namespaces inline to eliminate the HTTP round-trip
// that causes missing-translation warnings on first render.
import navEn from '../public/locales/en/navigation.json';
import navFr from '../public/locales/fr/navigation.json';
import commonEn from '../public/locales/en/common.json';
import commonFr from '../public/locales/fr/common.json';

// Guard against re-initialization (e.g. when the test environment pre-initializes
// the singleton via src/test/i18n-test.ts before this module is imported via apiClient).
if (!i18n.isInitialized) {
  i18n
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
      // Inline bundles for navigation and common — available instantly, no HTTP delay
      resources: {
        en: { navigation: navEn, common: commonEn },
        fr: { navigation: navFr, common: commonFr },
      },
      partialBundledLanguages: true, // Allow mixing bundled resources with HTTP backend
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
      },
    });
} // end if (!i18n.isInitialized)

export default i18n;
