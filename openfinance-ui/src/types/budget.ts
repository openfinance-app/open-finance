/**
 * Budget-related types
 * TASK-8.2: Budget Tracking feature type definitions
 * TASK-8.6: Auto Budget Creation types (REQ-2.9.1.5)
 */

export type BudgetPeriod = 'WEEKLY' | 'MONTHLY' | 'QUARTERLY' | 'YEARLY';

export type BudgetStatus = 'ON_TRACK' | 'WARNING' | 'EXCEEDED';

export interface BudgetRequest {
  categoryId: number;
  /** Exact decimal string as entered by the user — never a JS number. */
  amount: string;
  currency: string;
  period: BudgetPeriod;
  startDate: string; // ISO date format "YYYY-MM-DD"
  endDate: string;
  rollover: boolean;
  notes?: string;
}

export interface BudgetResponse {
  id: number;
  categoryId: number;
  categoryName: string;
  categoryType: 'INCOME' | 'EXPENSE';
  amount: number;
  currency: string;
  period: BudgetPeriod;
  startDate: string;
  endDate: string;
  rollover: boolean;
  notes?: string;
  createdAt: string;
  updatedAt: string;
}

export interface BudgetProgressResponse {
  budgetId: number;
  categoryName: string;
  budgeted: number;
  spent: number;
  remaining: number; // Can be negative if over budget
  percentageSpent: number;
  currency: string;
  period: BudgetPeriod;
  startDate: string;
  endDate: string;
  daysRemaining: number;
  status: BudgetStatus;
}

export interface BudgetSummaryResponse {
  period: BudgetPeriod;
  totalBudgets: number;
  activeBudgets: number;
  totalBudgeted: number;
  totalSpent: number;
  totalRemaining: number;
  averageSpentPercentage: number;
  currency: string;
  budgets: BudgetProgressResponse[];
}

/**
 * A single sub-period entry in a budget's history.
 * REQ-2.9.1.4: Budget history per sub-period breakdown
 */
export interface BudgetHistoryEntry {
  label: string;
  periodStart: string; // ISO date "YYYY-MM-DD"
  periodEnd: string;
  budgeted: number;
  spent: number;
  remaining: number;
  percentageSpent: number;
  status: BudgetStatus;
}

/**
 * Full history response for a single budget: metadata + per-sub-period entries.
 * REQ-2.9.1.4: Budget history and per-period breakdown
 */
export interface BudgetHistoryResponse {
  budgetId: number;
  categoryName: string;
  amount: number;
  currency: string;
  period: BudgetPeriod;
  startDate: string;
  endDate: string;
  history: BudgetHistoryEntry[];
  totalSpent: number;
  totalBudgeted: number;
}

// ========== TASK-8.6: Auto Budget Creation (REQ-2.9.1.5) ==========

/**
 * Request body for POST /api/v1/budgets/suggestions.
 * Triggers spending analysis over the given lookback window.
 */
export interface BudgetSuggestionRequest {
  /** Target budget period for per-period averages. */
  period: BudgetPeriod;
  /**
   * Number of months to scan (1–24). Defaults to 6 on the backend when omitted,
   * but the UI always sends an explicit value.
   */
  lookbackMonths: number;
  /** Optional ISO 4217 currency code; backend defaults to "EUR" when absent. */
  currency?: string;
  /** Restrict analysis to specific category IDs; null / omitted = all EXPENSE categories. */
  categoryIds?: number[];
}

/**
 * A single automatic budget suggestion derived from transaction history.
 * Returned by POST /api/v1/budgets/suggestions.
 * REQ-2.9.1.5
 */
export interface BudgetSuggestion {
  categoryId: number;
  categoryName: string;
  /** Ceiling-rounded average spend per target period — recommended budget amount. */
  suggestedAmount: number;
  /** Exact arithmetic average (before ceiling rounding). */
  averageSpent: number;
  /** Total EXPENSE transactions found in the lookback window for this category. */
  transactionCount: number;
  period: BudgetPeriod;
  currency: string;
  /** Start of the analysed lookback window. */
  startDate: string; // ISO "YYYY-MM-DD"
  /** End of the analysed lookback window. */
  endDate: string;
  /**
   * true when a budget already exists for this category+period combination.
   * The frontend renders a warning badge; the bulk-create endpoint will skip it.
   */
  hasExistingBudget: boolean;
}

/**
 * Request body for POST /api/v1/budgets/bulk.
 * Contains the user-confirmed BudgetRequest items derived from selected suggestions.
 * REQ-2.9.1.5
 */
export interface BudgetBulkCreateRequest {
  budgets: BudgetRequest[];
}

/**
 * Response from POST /api/v1/budgets/bulk.
 * Summarises how many budgets were created, skipped (duplicate), and any errors.
 * REQ-2.9.1.5
 */
export interface BudgetBulkCreateResponse {
  created: BudgetResponse[];
  successCount: number;
  /** Duplicate category+period combinations silently skipped. */
  skippedCount: number;
  /** Human-readable error messages for unexpected failures. */
  errors: string[];
}
