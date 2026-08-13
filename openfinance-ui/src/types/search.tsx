/**
 * Search-related types for global search functionality
 */

import type { TransactionType } from './transaction';

/**
 * Search result types matching backend SearchResultType enum
 */
export type SearchResultType =
  | 'TRANSACTION'
  | 'ACCOUNT'
  | 'ASSET'
  | 'REAL_ESTATE'
  | 'LIABILITY'
  | 'BUDGET'
  | 'CATEGORY'
  | 'RECURRING_TRANSACTION';

/**
 * Unified search result from backend
 */
export interface SearchResult {
  resultType: SearchResultType;
  id: number;
  title: string;
  subtitle?: string;
  amount?: number;
  currency?: string;
  date?: string;
  icon?: string;
  color?: string;
  tags?: string[];
  rank?: number;
  snippet?: string;
  createdAt: string;
  updatedAt?: string;
}

/**
 * Global search response with grouped results
 */
export interface GlobalSearchResponse {
  query: string;
  totalResults: number;
  resultsByType: Record<string, SearchResult[]>;
  countsPerType: Record<string, number>;
  executionTimeMs: number;
  hasMore: boolean;
  limit: number;
}

/**
 * Advanced search request with filters
 */
export interface AdvancedSearchRequest {
  query: string;
  /** When true, `query` is treated as a regular expression instead of a plain substring. */
  regex?: boolean;
  entityTypes?: SearchResultType[];
  accountIds?: number[];
  categoryIds?: number[];
  minAmount?: number;
  maxAmount?: number;
  dateFrom?: string;
  dateTo?: string;
  tags?: string[];
  isReconciled?: boolean;
  transactionType?: TransactionType;
  limit?: number;
}

/**
 * Search suggestion for autocomplete
 */
export interface SearchSuggestion {
  type: 'recent' | 'suggestion' | 'result';
  text: string;
  icon?: string;
  resultType?: SearchResultType;
  resultId?: number;
}

/**
 * Saved search for quick access
 */
export interface SavedSearch {
  id: string;
  name: string;
  filters: AdvancedSearchRequest;
  createdAt: string;
  lastUsed?: string;
}

/**
 * Helper function to get display name for result type
 */
export const getResultTypeDisplayName = (type: SearchResultType, t?: (key: string) => string): string => {
  if (t) {
    return t(`search.types.${type}`) || t('search.types.DEFAULT');
  }
  switch (type) {
    case 'TRANSACTION':
      return 'Transactions';
    case 'ACCOUNT':
      return 'Accounts';
    case 'ASSET':
      return 'Assets';
    case 'REAL_ESTATE':
      return 'Real Estate';
    case 'LIABILITY':
      return 'Liabilities';
    case 'BUDGET':
      return 'Budgets';
    case 'CATEGORY':
      return 'Categories';
    case 'RECURRING_TRANSACTION':
      return 'Recurring';
    default:
      return 'Results';
  }
};

/**
 * Helper function to get icon for result type
 */
export const getResultTypeIcon = (type: SearchResultType): string => {
  switch (type) {
    case 'TRANSACTION':
      return 'Receipt';
    case 'ACCOUNT':
      return 'Wallet';
    case 'ASSET':
      return 'TrendingUp';
    case 'REAL_ESTATE':
      return 'Home';
    case 'LIABILITY':
      return 'CreditCard';
    case 'BUDGET':
      return 'PieChart';
    case 'CATEGORY':
      return 'Tag';
    case 'RECURRING_TRANSACTION':
      return 'RefreshCw';
    default:
      return 'Search';
  }
};

/**
 * Helper function to get route for result type
 * Routes to detail pages when available, otherwise to list pages with query parameters
 */
export const getResultRoute = (result: SearchResult): string => {
  switch (result.resultType) {
    case 'TRANSACTION':
      // Navigate to transactions page with highlight parameter
      return `/transactions?highlight=${result.id}`;
    case 'ACCOUNT':
      // Navigate to accounts list with highlight parameter instead of detail page
      return `/accounts?highlight=${result.id}`;
    case 'ASSET':
      // Navigate to assets page with highlight parameter
      return `/assets?highlight=${result.id}`;
    case 'REAL_ESTATE':
      // Navigate to real estate page with highlight parameter
      return `/real-estate?highlight=${result.id}`;
    case 'LIABILITY':
      // Navigate to liabilities page with highlight parameter
      return `/liabilities?highlight=${result.id}`;
    case 'BUDGET':
      // Navigate to budget detail page
      return `/budget/${result.id}`;
    case 'CATEGORY':
      // Navigate to transactions page with category filter
      return `/transactions?category=${result.id}`;
    case 'RECURRING_TRANSACTION':
      // Navigate to recurring transactions page with highlight parameter
      return `/recurring-transactions?highlight=${result.id}`;
    default:
      return '/';
  }
};

/**
 * Helper function to highlight matching keywords in text
 */
export const highlightMatch = (text: string, query: string): React.ReactNode => {
  if (!query || !text) return text;

  const parts = text.split(new RegExp(`(${query})`, 'gi'));
  return parts.map((part, index) =>
    part.toLowerCase() === query.toLowerCase() ? (
      <mark key={index} className="bg-yellow-300/50 font-semibold">
        {part}
      </mark>
    ) : (
      part
    )
  );
};
