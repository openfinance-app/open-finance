/**
 * Recurring Transaction Types
 * Task 12.2.11: Create useRecurringTransactions hook (types)
 * 
 * TypeScript interfaces and types for recurring transaction management
 */

import type { TransactionType } from './transaction';

/**
 * Frequency options for recurring transactions
 */
export const RECURRING_FREQUENCIES = {
  DAILY: 'DAILY',
  WEEKLY: 'WEEKLY',
  BIWEEKLY: 'BIWEEKLY',
  MONTHLY: 'MONTHLY',
  QUARTERLY: 'QUARTERLY',
  YEARLY: 'YEARLY',
} as const;

export type RecurringFrequency = typeof RECURRING_FREQUENCIES[keyof typeof RECURRING_FREQUENCIES];

/**
 * Recurring transaction entity from backend
 */
export interface RecurringTransaction {
  id: number;
  accountId: number;
  accountName: string;
  toAccountId: number | null;
  toAccountName: string | null;
  type: TransactionType;
  amount: number;
  currency: string;
  categoryId: number | null;
  categoryName: string | null;
  categoryIcon: string | null;
  categoryColor: string | null;
  payee: string | null;
  description: string;
  notes: string | null;
  frequency: RecurringFrequency;
  frequencyDisplayName: string;
  nextOccurrence: string; // ISO 8601 date (YYYY-MM-DD)
  endDate: string | null; // ISO 8601 date (YYYY-MM-DD) or null for indefinite
  isActive: boolean;
  createdAt: string; // ISO 8601 datetime
  updatedAt: string; // ISO 8601 datetime
  // Computed fields
  isDue: boolean;
  daysUntilNext: number;
  isEnded: boolean;
}

/**
 * Request DTO for creating/updating recurring transactions
 */
export interface RecurringTransactionRequest {
  accountId: number;
  toAccountId?: number | null;
  type: TransactionType;
  /** Exact decimal string as entered by the user — never a JS number. */
  amount: string;
  currency: string;
  categoryId?: number | null;
  payee?: string | null;
  description: string;
  notes?: string | null;
  frequency: RecurringFrequency;
  nextOccurrence: string; // ISO 8601 date (YYYY-MM-DD)
  endDate?: string | null; // ISO 8601 date (YYYY-MM-DD) or null for indefinite
}

/**
 * Filters for recurring transactions
 */
export interface RecurringTransactionFilters {
  accountId?: number;
  type?: TransactionType;
  frequency?: RecurringFrequency;
  isActive?: boolean;
  search?: string;
  page?: number;
  size?: number;
  sort?: string;
}

/**
 * Result of manual processing trigger
 */
export interface ProcessingResult {
  processedCount: number;
  failedCount: number;
  errors: string[];
}

/**
 * Helper function to get human-readable frequency name
 */
export function getFrequencyDisplayName(frequency: RecurringFrequency): string {
  const names: Record<RecurringFrequency, string> = {
    DAILY: 'Daily',
    WEEKLY: 'Weekly',
    BIWEEKLY: 'Every 2 Weeks',
    MONTHLY: 'Monthly',
    QUARTERLY: 'Quarterly',
    YEARLY: 'Yearly',
  };
  return names[frequency];
}

/**
 * Helper function to get frequency badge color
 */
export function getFrequencyBadgeVariant(frequency: RecurringFrequency): 'default' | 'secondary' | 'outline' {
  switch (frequency) {
    case 'DAILY':
      return 'default';
    case 'WEEKLY':
    case 'BIWEEKLY':
      return 'secondary';
    case 'MONTHLY':
    case 'QUARTERLY':
    case 'YEARLY':
      return 'outline';
    default:
      return 'default';
  }
}

/**
 * Helper function to check if recurring transaction is due soon (within 7 days)
 */
export function isDueSoon(recurringTransaction: RecurringTransaction): boolean {
  return recurringTransaction.daysUntilNext <= 7 && recurringTransaction.daysUntilNext >= 0;
}

/**
 * Helper function to check if recurring transaction is overdue
 */
export function isOverdue(recurringTransaction: RecurringTransaction): boolean {
  return recurringTransaction.isDue;
}

/**
 * Helper function to get status badge text
 */
export function getStatusText(recurringTransaction: RecurringTransaction): string {
  if (recurringTransaction.isEnded) return 'Ended';
  if (!recurringTransaction.isActive) return 'Paused';
  if (recurringTransaction.isDue) return 'Due Now';
  if (isDueSoon(recurringTransaction)) return 'Due Soon';
  return 'Active';
}

/**
 * Helper function to get status badge variant
 */
export function getStatusBadgeVariant(
  recurringTransaction: RecurringTransaction
): 'default' | 'secondary' | 'destructive' | 'outline' | 'success' {
  if (recurringTransaction.isEnded) return 'outline';
  if (!recurringTransaction.isActive) return 'secondary';
  if (recurringTransaction.isDue) return 'destructive';
  if (isDueSoon(recurringTransaction)) return 'default';
  return 'success';
}
