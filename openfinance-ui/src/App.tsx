import { lazy, Suspense } from 'react';
import { BrowserRouter as Router, Routes, Route, Navigate } from 'react-router';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { ROUTES, ROUTE_PATTERNS } from '@/constants/routes';
import { AuthProvider } from './context/AuthContext';
import { VisibilityProvider } from './context/VisibilityContext';
import { CurrencyDisplayProvider } from './context/CurrencyDisplayContext';
import { ThemeProvider } from './context/ThemeContext';
import { NumberFormatProvider } from './context/NumberFormatContext';
import { DecimalPlacesProvider } from './context/DecimalPlacesContext';
import { LocaleProvider } from './context/LocaleContext';
import { ProtectedRoute } from './components/ProtectedRoute';
import './index.css';

// ---------------------------------------------------------------------------
// Lazy-loaded page components — each becomes its own JS chunk at build time
// ---------------------------------------------------------------------------
const DashboardPage = lazy(() => import('./pages/DashboardPage'));
const AccountsPage = lazy(() => import('./pages/AccountsPage'));
const TransactionsPage = lazy(() => import('./pages/TransactionsPage'));
const AssetsPage = lazy(() => import('./pages/AssetsPage'));
const LiabilitiesPage = lazy(() => import('./pages/LiabilitiesPage'));
const BudgetsPage = lazy(() => import('./pages/BudgetsPage'));
const RealEstatePage = lazy(() => import('./pages/RealEstatePage'));
const SettingsPage = lazy(() => import('./pages/SettingsPage'));
const HistoryPage = lazy(() => import('./pages/HistoryPage'));
const ImportPage = lazy(() => import('./pages/ImportPage'));
const RecurringTransactionsPage = lazy(() => import('./pages/RecurringTransactionsPage'));
const SearchResultsPage = lazy(() => import('./pages/SearchResultsPage'));
const BackupPage = lazy(() => import('./pages/BackupPage'));
const LoginPage = lazy(() => import('./pages/LoginPage'));
const RegisterPage = lazy(() => import('./pages/RegisterPage'));
const OnboardingPage = lazy(() => import('./pages/OnboardingPage'));
const ProfilePage = lazy(() => import('./pages/ProfilePage'));
const CommunityPage = lazy(() => import('./pages/CommunityPage'));
const PremiumPage = lazy(() => import('./pages/PremiumPage'));
const FinancialFreedomPage = lazy(() => import('./pages/FinancialFreedomPage'));
const CompoundInterestPage = lazy(() => import('./pages/CompoundInterestPage'));
const LoanCalculatorPage = lazy(() => import('./pages/LoanCalculatorPage'));
const EarlyPayoffPage = lazy(() => import('./pages/EarlyPayoffPage'));
const CategoriesPage = lazy(() => import('./pages/CategoriesPage'));
const TransactionRulesPage = lazy(() => import('./pages/TransactionRulesPage'));

// Lazy-loaded non-page components used directly in routes
const RealEstateToolsHub = lazy(() =>
  import('./components/real-estate-tools/RealEstateToolsHub').then(m => ({
    default: m.RealEstateToolsHub,
  }))
);
const RealEstateToolsWrapper = lazy(() =>
  import('./components/real-estate-tools/RealEstateToolsWrapper').then(m => ({
    default: m.RealEstateToolsWrapper,
  }))
);
const InstitutionManagementSettings = lazy(() =>
  import('./components/settings/InstitutionManagementSettings').then(m => ({
    default: m.InstitutionManagementSettings,
  }))
);
const PayeeManagementSettings = lazy(() =>
  import('./components/settings/PayeeManagementSettings').then(m => ({
    default: m.PayeeManagementSettings,
  }))
);

// ---------------------------------------------------------------------------
// Loading fallback shown while a lazy chunk is being fetched
// ---------------------------------------------------------------------------
const PageLoadingFallback = () => (
  <div className="min-h-screen bg-gray-900 flex items-center justify-center">
    <div className="flex flex-col items-center gap-4">
      <div className="w-12 h-12 border-4 border-yellow-500 border-t-transparent rounded-full animate-spin" />
      <p className="text-gray-400 text-sm">Loading...</p>
    </div>
  </div>
);

// ---------------------------------------------------------------------------
// React Query client — configured outside the component to avoid re-creation
// ---------------------------------------------------------------------------
const queryClient = new QueryClient({
  defaultOptions: {
    queries: {
      refetchOnWindowFocus: false,
      retry: 1,
      staleTime: 5 * 60 * 1000, // 5 minutes stale time
      gcTime: 10 * 60 * 1000, // 10 minutes garbage collection
      refetchOnReconnect: 'always', // Refetch on network reconnect
    },
    mutations: {
      retry: 0, // Don't retry mutations
    },
  },
});

function App() {
  return (
    <QueryClientProvider client={queryClient}>
      <AuthProvider>
        <VisibilityProvider>
          <CurrencyDisplayProvider>
            <ThemeProvider>
              <NumberFormatProvider>
                <DecimalPlacesProvider>
                  <LocaleProvider>
                    <Router>
                      <Suspense fallback={<PageLoadingFallback />}>
                        <Routes>
                          {/* Public routes */}
                          <Route path={ROUTES.LOGIN} element={<LoginPage />} />
                          <Route path={ROUTES.REGISTER} element={<RegisterPage />} />
                          <Route path={ROUTES.ONBOARDING} element={<OnboardingPage />} />

                          {/* Protected routes - require authentication */}
                          <Route
                            path={ROUTES.DASHBOARD}
                            element={
                              <ProtectedRoute>
                                <DashboardPage />
                              </ProtectedRoute>
                            }
                          />
                          <Route
                            path={ROUTES.ACCOUNTS}
                            element={
                              <ProtectedRoute>
                                <AccountsPage />
                              </ProtectedRoute>
                            }
                          />
                          <Route
                            path={ROUTE_PATTERNS.ACCOUNT_DETAIL}
                            element={<Navigate to={ROUTES.ACCOUNTS} replace />}
                          />
                          <Route
                            path={ROUTES.TRANSACTIONS}
                            element={
                              <ProtectedRoute>
                                <TransactionsPage />
                              </ProtectedRoute>
                            }
                          />
                          <Route
                            path={ROUTES.RECURRING_TRANSACTIONS}
                            element={
                              <ProtectedRoute>
                                <RecurringTransactionsPage />
                              </ProtectedRoute>
                            }
                          />
                          <Route
                            path={ROUTES.SEARCH}
                            element={
                              <ProtectedRoute>
                                <SearchResultsPage />
                              </ProtectedRoute>
                            }
                          />
                          <Route
                            path={ROUTES.IMPORT}
                            element={
                              <ProtectedRoute>
                                <ImportPage />
                              </ProtectedRoute>
                            }
                          />
                          <Route
                            path={ROUTES.ASSETS}
                            element={
                              <ProtectedRoute>
                                <AssetsPage />
                              </ProtectedRoute>
                            }
                          />
                          <Route path={ROUTES.PORTFOLIO} element={<Navigate to={ROUTES.ASSETS} replace />} />
                          <Route
                            path={ROUTES.LIABILITIES}
                            element={
                              <ProtectedRoute>
                                <LiabilitiesPage />
                              </ProtectedRoute>
                            }
                          />
                          <Route
                            path={ROUTES.INSTITUTIONS}
                            element={
                              <ProtectedRoute>
                                <InstitutionManagementSettings />
                              </ProtectedRoute>
                            }
                          />
                          <Route
                            path={ROUTES.PAYEES}
                            element={
                              <ProtectedRoute>
                                <PayeeManagementSettings />
                              </ProtectedRoute>
                            }
                          />
                          <Route
                            path={ROUTES.CATEGORIES}
                            element={
                              <ProtectedRoute>
                                <CategoriesPage />
                              </ProtectedRoute>
                            }
                          />
                          <Route
                            path={ROUTES.TRANSACTION_RULES}
                            element={
                              <ProtectedRoute>
                                <TransactionRulesPage />
                              </ProtectedRoute>
                            }
                          />
                          <Route
                            path={ROUTES.BUDGET}
                            element={
                              <ProtectedRoute>
                                <BudgetsPage />
                              </ProtectedRoute>
                            }
                          />
                          <Route path={ROUTE_PATTERNS.BUDGET_DETAIL} element={<Navigate to={ROUTES.BUDGET} replace />} />
                          <Route
                            path={ROUTES.HISTORY}
                            element={
                              <ProtectedRoute>
                                <HistoryPage />
                              </ProtectedRoute>
                            }
                          />
                          <Route
                            path={ROUTES.REAL_ESTATE}
                            element={
                              <ProtectedRoute>
                                <RealEstatePage />
                              </ProtectedRoute>
                            }
                          />
                          <Route
                            path={ROUTES.REAL_ESTATE_TOOLS}
                            element={
                              <ProtectedRoute>
                                <RealEstateToolsHub />
                              </ProtectedRoute>
                            }
                          />
                          <Route
                            path={ROUTE_PATTERNS.REAL_ESTATE_TOOLS_WILDCARD}
                            element={
                              <ProtectedRoute>
                                <RealEstateToolsWrapper />
                              </ProtectedRoute>
                            }
                          />

                          {/* AI Assistant is now a floating widget — redirect old URL for backward compatibility */}
                          <Route
                            path={ROUTES.AI_ASSISTANT}
                            element={<Navigate to={ROUTES.DASHBOARD} replace />}
                          />
                          {/* /tools view removed - keep submenu routes only */}
                          <Route
                            path={ROUTES.FINANCIAL_FREEDOM}
                            element={
                              <ProtectedRoute>
                                <FinancialFreedomPage />
                              </ProtectedRoute>
                            }
                          />
                          <Route
                            path={ROUTES.COMPOUND_INTEREST}
                            element={
                              <ProtectedRoute>
                                <CompoundInterestPage />
                              </ProtectedRoute>
                            }
                          />
                          <Route
                            path={ROUTES.LOAN_CALCULATOR}
                            element={
                              <ProtectedRoute>
                                <LoanCalculatorPage />
                              </ProtectedRoute>
                            }
                          />
                          <Route
                            path={ROUTES.EARLY_PAYOFF}
                            element={
                              <ProtectedRoute>
                                <EarlyPayoffPage />
                              </ProtectedRoute>
                            }
                          />
                          <Route
                            path={ROUTES.COMMUNITY}
                            element={
                              <ProtectedRoute>
                                <CommunityPage />
                              </ProtectedRoute>
                            }
                          />
                          <Route
                            path={ROUTES.PREMIUM}
                            element={
                              <ProtectedRoute>
                                <PremiumPage />
                              </ProtectedRoute>
                            }
                          />
                          <Route
                            path={ROUTES.PROFILE}
                            element={
                              <ProtectedRoute>
                                <ProfilePage />
                              </ProtectedRoute>
                            }
                          />
                          <Route
                            path={ROUTES.SETTINGS}
                            element={
                              <ProtectedRoute>
                                <SettingsPage />
                              </ProtectedRoute>
                            }
                          />
                          <Route
                            path={ROUTES.BACKUP}
                            element={
                              <ProtectedRoute>
                                <BackupPage />
                              </ProtectedRoute>
                            }
                          />

                          {/* Default redirect - go to dashboard (protected) */}
                          <Route path={ROUTES.HOME} element={<Navigate to={ROUTES.DASHBOARD} replace />} />

                          {/* 404 fallback */}
                          <Route path={ROUTE_PATTERNS.NOT_FOUND} element={<Navigate to={ROUTES.DASHBOARD} replace />} />
                        </Routes>
                      </Suspense>
                    </Router>
                  </LocaleProvider>
                </DecimalPlacesProvider>
              </NumberFormatProvider>
            </ThemeProvider>
          </CurrencyDisplayProvider>
        </VisibilityProvider>
      </AuthProvider>
    </QueryClientProvider>
  );
}

export default App;
