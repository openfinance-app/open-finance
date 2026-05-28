/**
 * Test i18n Configuration
 * 
 * This file configures i18next for testing by preloading all translation resources
 * instead of loading them from the file system, which may not be available in tests.
 */

import i18n from 'i18next';
import { initReactI18next } from 'react-i18next';

// Import all translation resources
import commonEn from '../../public/locales/en/common.json';
import authEn from '../../public/locales/en/auth.json';
import navigationEn from '../../public/locales/en/navigation.json';
import dashboardEn from '../../public/locales/en/dashboard.json';
import accountsEn from '../../public/locales/en/accounts.json';
import transactionsEn from '../../public/locales/en/transactions.json';
import assetsEn from '../../public/locales/en/assets.json';
import liabilitiesEn from '../../public/locales/en/liabilities.json';
import budgetsEn from '../../public/locales/en/budgets.json';
import categoriesEn from '../../public/locales/en/categories.json';
import currenciesEn from '../../public/locales/en/currencies.json';
import importEn from '../../public/locales/en/import.json';
import recurringEn from '../../public/locales/en/recurring.json';
import realEstateEn from '../../public/locales/en/realEstate.json';
import rulesEn from '../../public/locales/en/rules.json';
import backupEn from '../../public/locales/en/backup.json';
import settingsEn from '../../public/locales/en/settings.json';
import payeesEn from '../../public/locales/en/payees.json';
import institutionsEn from '../../public/locales/en/institutions.json';
import aiEn from '../../public/locales/en/ai.json';
import toolsEn from '../../public/locales/en/tools.json';
import errorsEn from '../../public/locales/en/errors.json';
import validationEn from '../../public/locales/en/validation.json';
import onboardingEn from '../../public/locales/en/onboarding.json';

import commonFr from '../../public/locales/fr/common.json';
import authFr from '../../public/locales/fr/auth.json';
import navigationFr from '../../public/locales/fr/navigation.json';
import dashboardFr from '../../public/locales/fr/dashboard.json';
import accountsFr from '../../public/locales/fr/accounts.json';
import transactionsFr from '../../public/locales/fr/transactions.json';
import assetsFr from '../../public/locales/fr/assets.json';
import liabilitiesFr from '../../public/locales/fr/liabilities.json';
import budgetsFr from '../../public/locales/fr/budgets.json';
import categoriesFr from '../../public/locales/fr/categories.json';
import currenciesFr from '../../public/locales/fr/currencies.json';
import importFr from '../../public/locales/fr/import.json';
import recurringFr from '../../public/locales/fr/recurring.json';
import realEstateFr from '../../public/locales/fr/realEstate.json';
import rulesFr from '../../public/locales/fr/rules.json';
import backupFr from '../../public/locales/fr/backup.json';
import settingsFr from '../../public/locales/fr/settings.json';
import payeesFr from '../../public/locales/fr/payees.json';
import institutionsFr from '../../public/locales/fr/institutions.json';
import aiFr from '../../public/locales/fr/ai.json';
import toolsFr from '../../public/locales/fr/tools.json';
import errorsFr from '../../public/locales/fr/errors.json';
import validationFr from '../../public/locales/fr/validation.json';
import onboardingFr from '../../public/locales/fr/onboarding.json';

// Initialize i18n with preloaded resources
i18n
  .use(initReactI18next)
  .init({
    lng: 'en',
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
      'onboarding',
    ],
    resources: {
      en: {
        common: commonEn,
        auth: authEn,
        navigation: navigationEn,
        dashboard: dashboardEn,
        accounts: accountsEn,
        transactions: transactionsEn,
        assets: assetsEn,
        liabilities: liabilitiesEn,
        budgets: budgetsEn,
        categories: categoriesEn,
        currencies: currenciesEn,
        import: importEn,
        recurring: recurringEn,
        realEstate: realEstateEn,
        rules: rulesEn,
        backup: backupEn,
        settings: settingsEn,
        payees: payeesEn,
        institutions: institutionsEn,
        ai: aiEn,
        tools: toolsEn,
        errors: errorsEn,
        validation: validationEn,
        onboarding: onboardingEn,
      },
      fr: {
        common: commonFr,
        auth: authFr,
        navigation: navigationFr,
        dashboard: dashboardFr,
        accounts: accountsFr,
        transactions: transactionsFr,
        assets: assetsFr,
        liabilities: liabilitiesFr,
        budgets: budgetsFr,
        categories: categoriesFr,
        currencies: currenciesFr,
        import: importFr,
        recurring: recurringFr,
        realEstate: realEstateFr,
        rules: rulesFr,
        backup: backupFr,
        settings: settingsFr,
        payees: payeesFr,
        institutions: institutionsFr,
        ai: aiFr,
        tools: toolsFr,
        errors: errorsFr,
        validation: validationFr,
        onboarding: onboardingFr,
      },
    },
    interpolation: {
      escapeValue: false,
    },
    react: {
      useSuspense: false,
    },
    // Ensure synchronous initialization so that isInitialized=true before any
    // other module (e.g. src/i18n.ts via apiClient) can call init() again on
    // the same singleton and overwrite our inline test resources with the HTTP
    // backend (which has no server in the test environment).
    initImmediate: false,
  });

export default i18n;
