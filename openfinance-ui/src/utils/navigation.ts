/**
 * URL builders for cross-view deep links (dashboard cards → filtered views).
 * Target pages consume these params on mount and strip them (see pages).
 */
import type { DateRange } from '@/components/ui/PeriodSelector';

export type TransactionTypeFilter = 'INCOME' | 'EXPENSE' | 'TRANSFER';

export interface TransactionsLinkParams {
  accountId?: number;
  categoryId?: number;
  noCategory?: boolean;
  type?: TransactionTypeFilter;
  dateRange?: DateRange;
}

export function buildTransactionsLink(params: TransactionsLinkParams): string {
  const sp = new URLSearchParams();
  if (params.accountId != null) sp.set('accountId', String(params.accountId));
  if (params.categoryId != null) sp.set('categoryId', String(params.categoryId));
  if (params.noCategory) sp.set('noCategory', '1');
  if (params.type) sp.set('type', params.type);
  if (params.dateRange?.from) sp.set('dateFrom', params.dateRange.from);
  if (params.dateRange?.to) sp.set('dateTo', params.dateRange.to);
  const qs = sp.toString();
  return qs ? `/transactions?${qs}` : '/transactions';
}

/** `{from: today − days, to: today}` as ISO yyyy-MM-dd — matches the dashboard period semantics. */
export function periodToDateRange(days: number): DateRange {
  const toIso = (d: Date) => d.toISOString().slice(0, 10);
  const today = new Date();
  const start = new Date(today);
  start.setDate(today.getDate() - days);
  return { from: toIso(start), to: toIso(today) };
}
