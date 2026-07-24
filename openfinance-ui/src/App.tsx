import { lazy, Suspense } from 'react';
import { BrowserRouter as Router, Routes, Route, Navigate } from 'react-router';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
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
                          <Route path="/login" element={<LoginPage />} />
                          <Route path="/register" element={<RegisterPage />} />
                          <Route path="/onboarding" element={<OnboardingPage />} />

                          {/* Protected routes - require authentication */}
                          <Route
                            path="/dashboard"
                            element={
                              <ProtectedRoute>
                                <DashboardPage />
                              </ProtectedRoute>
                            }
                          />
                          <Route
                            path="/accounts"
                            element={
                              <ProtectedRoute>
                                <AccountsPage />
                              </ProtectedRoute>
                            }
                          />
                          <Route
                            path="/accounts/:id"
                            element={<Navigate to="/accounts" replace />}
                          />
                          <Route
                            path="/transactions"
                            element={
                              <ProtectedRoute>
                                <TransactionsPage />
                              </ProtectedRoute>
                            }
                          />
                          <Route
                            path="/recurring-transactions"
                            element={
                              <ProtectedRoute>
                                <RecurringTransactionsPage />
                              </ProtectedRoute>
                            }
                          />
                          <Route
                            path="/search"
                            element={
                              <ProtectedRoute>
                                <SearchResultsPage />
                              </ProtectedRoute>
                            }
                          />
                          <Route
                            path="/import"
                            element={
                              <ProtectedRoute>
                                <ImportPage />
                              </ProtectedRoute>
                            }
                          />
                          <Route
                            path="/assets"
                            element={
                              <ProtectedRoute>
                                <AssetsPage />
                              </ProtectedRoute>
                            }
                          />
                          <Route path="/portfolio" element={<Navigate to="/assets" replace />} />
                          <Route
                            path="/liabilities"
                            element={
                              <ProtectedRoute>
                                <LiabilitiesPage />
                              </ProtectedRoute>
                            }
                          />
                          <Route
                            path="/institutions"
                            element={
                              <ProtectedRoute>
                                <InstitutionManagementSettings />
                              </ProtectedRoute>
                            }
                          />
                          <Route
                            path="/payees"
                            element={
                              <ProtectedRoute>
                                <PayeeManagementSettings />
                              </ProtectedRoute>
                            }
                          />
                          <Route
                            path="/categories"
                            element={
                              <ProtectedRoute>
                                <CategoriesPage />
                              </ProtectedRoute>
                            }
                          />
                          <Route
                            path="/transaction-rules"
                            element={
                              <ProtectedRoute>
                                <TransactionRulesPage />
                              </ProtectedRoute>
                            }
                          />
                          <Route
                            path="/budget"
                            element={
                              <ProtectedRoute>
                                <BudgetsPage />
                              </ProtectedRoute>
                            }
                          />
                          <Route path="/budget/:id" element={<Navigate to="/budget" replace />} />
                          <Route
                            path="/history"
                            element={
                              <ProtectedRoute>
                                <HistoryPage />
                              </ProtectedRoute>
                            }
                          />
                          <Route
                            path="/real-estate"
                            element={
                              <ProtectedRoute>
                                <RealEstatePage />
                              </ProtectedRoute>
                            }
                          />
                          <Route
                            path="/real-estate/tools"
                            element={
                              <ProtectedRoute>
                                <RealEstateToolsHub />
                              </ProtectedRoute>
                            }
                          />
                          <Route
                            path="/real-estate/tools/*"
                            element={
                              <ProtectedRoute>
                                <RealEstateToolsWrapper />
                              </ProtectedRoute>
                            }
                          />

                          {/* AI Assistant is now a floating widget — redirect old URL for backward compatibility */}
                          <Route
                            path="/ai-assistant"
                            element={<Navigate to="/dashboard" replace />}
                          />
                          {/* /tools view removed - keep submenu routes only */}
                          <Route
                            path="/tools/financial-freedom"
                            element={
                              <ProtectedRoute>
                                <FinancialFreedomPage />
                              </ProtectedRoute>
                            }
                          />
                          <Route
                            path="/tools/compound-interest"
                            element={
                              <ProtectedRoute>
                                <CompoundInterestPage />
                              </ProtectedRoute>
                            }
                          />
                          <Route
                            path="/tools/loan-calculator"
                            element={
                              <ProtectedRoute>
                                <LoanCalculatorPage />
                              </ProtectedRoute>
                            }
                          />
                          <Route
                            path="/tools/early-payoff"
                            element={
                              <ProtectedRoute>
                                <EarlyPayoffPage />
                              </ProtectedRoute>
                            }
                          />
                          <Route
                            path="/community"
                            element={
                              <ProtectedRoute>
                                <CommunityPage />
                              </ProtectedRoute>
                            }
                          />
                          <Route
                            path="/premium"
                            element={
                              <ProtectedRoute>
                                <PremiumPage />
                              </ProtectedRoute>
                            }
                          />
                          <Route
                            path="/profile"
                            element={
                              <ProtectedRoute>
                                <ProfilePage />
                              </ProtectedRoute>
                            }
                          />
                          <Route
                            path="/settings"
                            element={
                              <ProtectedRoute>
                                <SettingsPage />
                              </ProtectedRoute>
                            }
                          />
                          <Route
                            path="/backup"
                            element={
                              <ProtectedRoute>
                                <BackupPage />
                              </ProtectedRoute>
                            }
                          />

                          {/* Default redirect - go to dashboard (protected) */}
                          <Route path="/" element={<Navigate to="/dashboard" replace />} />

                          {/* 404 fallback */}
                          <Route path="*" element={<Navigate to="/dashboard" replace />} />
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
