/**
 * Centralized application route paths.
 *
 * Single source of truth for both the `<Route path>` definitions in `App.tsx` and every imperative
 * navigation (`navigate(...)`, `<Navigate to>`, `<Link to>`). Using these constants eliminates
 * typos like navigating to `/budgets` (plural) when the registered route is `/budget`.
 */
export const ROUTES = {
  HOME: '/',
  LOGIN: '/login',
  REGISTER: '/register',
  ONBOARDING: '/onboarding',
  DASHBOARD: '/dashboard',
  ACCOUNTS: '/accounts',
  TRANSACTIONS: '/transactions',
  RECURRING_TRANSACTIONS: '/recurring-transactions',
  SEARCH: '/search',
  IMPORT: '/import',
  ASSETS: '/assets',
  PORTFOLIO: '/portfolio',
  LIABILITIES: '/liabilities',
  INSTITUTIONS: '/institutions',
  PAYEES: '/payees',
  CATEGORIES: '/categories',
  TRANSACTION_RULES: '/transaction-rules',
  BUDGET: '/budget',
  HISTORY: '/history',
  REAL_ESTATE: '/real-estate',
  REAL_ESTATE_TOOLS: '/real-estate/tools',
  REAL_ESTATE_TOOLS_RENTAL: '/real-estate/tools/rental',
  REAL_ESTATE_TOOLS_BUY_RENT: '/real-estate/tools/buy-rent',
  AI_ASSISTANT: '/ai-assistant',
  FINANCIAL_FREEDOM: '/tools/financial-freedom',
  COMPOUND_INTEREST: '/tools/compound-interest',
  LOAN_CALCULATOR: '/tools/loan-calculator',
  EARLY_PAYOFF: '/tools/early-payoff',
  COMMUNITY: '/community',
  PREMIUM: '/premium',
  PROFILE: '/profile',
  SETTINGS: '/settings',
  BACKUP: '/backup',
} as const;

/**
 * Parameterized / nested route patterns (used only in `<Route path>` definitions, not for
 * navigation — build concrete paths with the helpers below).
 */
export const ROUTE_PATTERNS = {
  ACCOUNT_DETAIL: '/accounts/:id',
  BUDGET_DETAIL: '/budget/:id',
  REAL_ESTATE_TOOLS_WILDCARD: '/real-estate/tools/*',
  /** Catch-all fallback. */
  NOT_FOUND: '*',
} as const;

export type Route = (typeof ROUTES)[keyof typeof ROUTES];
