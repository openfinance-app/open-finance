import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import type { ReactNode } from 'react';
import { screen } from '@testing-library/react';
import { mockAuthentication, renderWithProviders } from '@/test/test-utils';
import App from '@/App';
import i18n from '@/test/i18n-test';

// Keep the provider helper's router and exercise the application's real route table and guard.
vi.mock('react-router', async importOriginal => {
  const router = await importOriginal<typeof import('react-router')>();
  return {
    ...router,
    BrowserRouter: ({ children }: { children: ReactNode }) =>
      router.useInRouterContext() ? (
        <>{children}</>
      ) : (
        <router.BrowserRouter>{children}</router.BrowserRouter>
      ),
  };
});
vi.mock('@/components/layout/AppLayout', () => ({
  AppLayout: ({ children }: { children: ReactNode }) => <main>{children}</main>,
}));
vi.mock('@/pages/DashboardPage', () => ({ default: () => <h1>Dashboard screen</h1> }));
vi.mock('@/pages/AccountsPage', () => ({ default: () => <h1>Accounts screen</h1> }));
vi.mock('@/pages/TransactionsPage', () => ({ default: () => <h1>Transactions screen</h1> }));
vi.mock('@/pages/AssetsPage', () => ({ default: () => <h1>Assets screen</h1> }));
vi.mock('@/pages/LiabilitiesPage', () => ({ default: () => <h1>Liabilities screen</h1> }));
vi.mock('@/pages/BudgetsPage', () => ({ default: () => <h1>Budgets screen</h1> }));
vi.mock('@/pages/RealEstatePage', () => ({ default: () => <h1>RealEstate screen</h1> }));
vi.mock('@/pages/SettingsPage', () => ({ default: () => <h1>Settings screen</h1> }));
vi.mock('@/pages/HistoryPage', () => ({ default: () => <h1>History screen</h1> }));
vi.mock('@/pages/ImportPage', () => ({ default: () => <h1>Import screen</h1> }));
vi.mock('@/pages/RecurringTransactionsPage', () => ({
  default: () => <h1>RecurringTransactions screen</h1>,
}));
vi.mock('@/pages/SearchResultsPage', () => ({ default: () => <h1>SearchResults screen</h1> }));
vi.mock('@/pages/BackupPage', () => ({ default: () => <h1>Backup screen</h1> }));
vi.mock('@/pages/LoginPage', () => ({ default: () => <h1>Login screen</h1> }));
vi.mock('@/pages/RegisterPage', () => ({ default: () => <h1>Register screen</h1> }));
vi.mock('@/pages/OnboardingPage', () => ({ default: () => <h1>Onboarding screen</h1> }));
vi.mock('@/pages/ProfilePage', () => ({ default: () => <h1>Profile screen</h1> }));
vi.mock('@/pages/CommunityPage', () => ({ default: () => <h1>Community screen</h1> }));
vi.mock('@/pages/PremiumPage', () => ({ default: () => <h1>Premium screen</h1> }));
vi.mock('@/pages/FinancialFreedomPage', () => ({
  default: () => <h1>FinancialFreedom screen</h1>,
}));
vi.mock('@/pages/CompoundInterestPage', () => ({
  default: () => <h1>CompoundInterest screen</h1>,
}));
vi.mock('@/pages/LoanCalculatorPage', () => ({ default: () => <h1>LoanCalculator screen</h1> }));
vi.mock('@/pages/EarlyPayoffPage', () => ({ default: () => <h1>EarlyPayoff screen</h1> }));
vi.mock('@/pages/CategoriesPage', () => ({ default: () => <h1>Categories screen</h1> }));
vi.mock('@/pages/TransactionRulesPage', () => ({
  default: () => <h1>TransactionRules screen</h1>,
}));
vi.mock('@/components/real-estate-tools/RealEstateToolsHub', () => ({
  RealEstateToolsHub: () => <h1>RealEstateToolsHub screen</h1>,
}));
vi.mock('@/components/real-estate-tools/RealEstateToolsWrapper', () => ({
  RealEstateToolsWrapper: () => <h1>RealEstateToolsWrapper screen</h1>,
}));
vi.mock('@/components/settings/InstitutionManagementSettings', () => ({
  InstitutionManagementSettings: () => <h1>InstitutionManagementSettings screen</h1>,
}));
vi.mock('@/components/settings/PayeeManagementSettings', () => ({
  PayeeManagementSettings: () => <h1>PayeeManagementSettings screen</h1>,
}));

beforeEach(async () => {
  mockAuthentication();
  await i18n.changeLanguage('en');
});
afterEach(() => {
  window.history.replaceState({}, '', '/');
});

const protectedRoutes = [
  ['/dashboard', 'Dashboard'],
  ['/accounts', 'Accounts'],
  ['/transactions', 'Transactions'],
  ['/assets', 'Assets'],
  ['/liabilities', 'Liabilities'],
  ['/budget', 'Budgets'],
  ['/real-estate', 'RealEstate'],
  ['/settings', 'Settings'],
  ['/history', 'History'],
  ['/import', 'Import'],
  ['/recurring-transactions', 'RecurringTransactions'],
  ['/search', 'SearchResults'],
  ['/backup', 'Backup'],
  ['/profile', 'Profile'],
  ['/community', 'Community'],
  ['/premium', 'Premium'],
  ['/tools/financial-freedom', 'FinancialFreedom'],
  ['/tools/compound-interest', 'CompoundInterest'],
  ['/tools/loan-calculator', 'LoanCalculator'],
  ['/tools/early-payoff', 'EarlyPayoff'],
  ['/categories', 'Categories'],
  ['/transaction-rules', 'TransactionRules'],
  ['/real-estate/tools', 'RealEstateToolsHub'],
  ['/real-estate/tools/buy-rent', 'RealEstateToolsWrapper'],
  ['/institutions', 'InstitutionManagementSettings'],
  ['/payees', 'PayeeManagementSettings'],
] as const;

describe('application route wiring', () => {
  it.each(protectedRoutes)(
    'opens the correct authenticated screen at %s',
    async (path, screenName) => {
      window.history.replaceState({}, '', path);
      renderWithProviders(<App />);
      expect(
        await screen.findByRole('heading', { name: `${screenName} screen` }, { timeout: 5000 })
      ).toBeInTheDocument();
      expect(window.location.pathname).toBe(path);
    }
  );

  it.each([
    ['/login', 'Login'],
    ['/register', 'Register'],
    ['/onboarding', 'Onboarding'],
  ])('keeps %s public', async (path, screenName) => {
    localStorage.clear();
    sessionStorage.clear();
    window.history.replaceState({}, '', path);
    renderWithProviders(<App />);
    expect(
      await screen.findByRole('heading', { name: `${screenName} screen` }, { timeout: 5000 })
    ).toBeInTheDocument();
  });

  it.each(['/accounts', '/import', '/settings', '/real-estate/tools/rental'])(
    'redirects anonymous requests for %s before rendering private content',
    async path => {
      localStorage.clear();
      sessionStorage.clear();
      window.history.replaceState({}, '', path);
      renderWithProviders(<App />);
      expect(
        await screen.findByRole('heading', { name: 'Login screen' }, { timeout: 5000 })
      ).toBeInTheDocument();
      expect(screen.queryByRole('main')).not.toBeInTheDocument();
      expect(window.location.pathname).toBe('/login');
    }
  );

  it.each([
    ['/portfolio', 'Assets', '/assets'],
    ['/accounts/9', 'Accounts', '/accounts'],
    ['/', 'Dashboard', '/dashboard'],
    ['/does-not-exist', 'Dashboard', '/dashboard'],
  ])('resolves redirect %s to its supported route', async (path, screenName, destination) => {
    window.history.replaceState({}, '', path);
    renderWithProviders(<App />);
    expect(
      await screen.findByRole('heading', { name: `${screenName} screen` }, { timeout: 5000 })
    ).toBeInTheDocument();
    expect(window.location.pathname).toBe(destination);
  });
});
