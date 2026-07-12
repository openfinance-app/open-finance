import { useQuery } from '@tanstack/react-query';
import apiClient from '@/services/apiClient';
import type {
  IDashboardSummary,
  IAccountSummary,
  ICashFlow,
  ISpendingByCategory,
  INetWorthSummary,
  IAssetAllocation,
  IPortfolioPerformance,
  IBorrowingCapacity,
  INetWorthAllocation,
  IDailyCashFlow,
  ICashflowSankey,
  IEstimatedInterestSummary,
  IYearlyBalanceResponse,
} from '../types/dashboard';
import type { Transaction } from '../types/transaction';
import type { DateRange } from '../components/ui/PeriodSelector';
import { buildEncryptionHeaders } from '@/utils/encryption';

// ─── Helpers ──────────────────────────────────────────────────────────────────

const getEncryptionKey = (): string | null => sessionStorage.getItem('encryption_session');

/**
 * Build query params that support either a simple `period` (days) or an
 * explicit `startDate`/`endDate` date-range pair.
 * When a dateRange is supplied it takes precedence over the period number.
 */
function periodParams(period: number, dateRange?: DateRange): Record<string, string | number> {
  if (dateRange) {
    return { startDate: dateRange.from, endDate: dateRange.to };
  }
  return { period };
}

// ─── Fetchers ─────────────────────────────────────────────────────────────────

const fetchDashboardSummary = async (): Promise<IDashboardSummary> => {
  const response = await apiClient.get<IDashboardSummary>('/dashboard', {
    headers: buildEncryptionHeaders(),
  });
  return response.data;
};

const fetchAccountSummaries = async (): Promise<IAccountSummary[]> => {
  const response = await apiClient.get<IAccountSummary[]>('/dashboard/accounts', {
    headers: buildEncryptionHeaders(),
  });
  return response.data;
};

const fetchCashFlow = async (period: number = 30, dateRange?: DateRange): Promise<ICashFlow> => {
  const response = await apiClient.get<ICashFlow>('/dashboard/cashflow', {
    params: periodParams(period, dateRange),
  });
  return response.data;
};

const fetchSpendingByCategory = async (
  period: number = 30,
  dateRange?: DateRange
): Promise<ISpendingByCategory> => {
  const response = await apiClient.get<ISpendingByCategory>('/dashboard/spending', {
    params: periodParams(period, dateRange),
  });
  return response.data;
};

const fetchNetWorthHistory = async (
  period: number = 365,
  dateRange?: DateRange
): Promise<INetWorthSummary[]> => {
  const response = await apiClient.get<INetWorthSummary[]>('/dashboard/networth-history', {
    params: periodParams(period, dateRange),
    headers: buildEncryptionHeaders(),
  });
  return response.data;
};

const fetchAssetAllocation = async (): Promise<IAssetAllocation[]> => {
  const response = await apiClient.get<IAssetAllocation[]>('/dashboard/asset-allocation');
  return response.data;
};

const fetchPortfolioPerformance = async (
  period: number = 30,
  dateRange?: DateRange
): Promise<IPortfolioPerformance[]> => {
  const response = await apiClient.get<IPortfolioPerformance[]>(
    '/dashboard/portfolio-performance',
    {
      params: periodParams(period, dateRange),
    }
  );
  return response.data;
};

const fetchTransactionsByPeriod = async (
  period: number = 30,
  dateRange?: DateRange
): Promise<Transaction[]> => {
  const params: Record<string, string | number> = periodParams(period, dateRange);
  // The transactions search endpoint uses dateFrom/dateTo; map startDate/endDate accordingly
  const searchParams: Record<string, string | number> = {};
  if ('startDate' in params) {
    searchParams['dateFrom'] = params['startDate'];
    searchParams['endDate'] = params['endDate'];
  } else {
    const endDate = new Date().toISOString().split('T')[0];
    const startDate = new Date(Date.now() - period * 24 * 60 * 60 * 1000)
      .toISOString()
      .split('T')[0];
    searchParams['dateFrom'] = startDate;
    searchParams['endDate'] = endDate;
  }
  searchParams['sort'] = 'date,desc';
  searchParams['size'] = 50;
  const response = await apiClient.get<{ content: Transaction[] } | Transaction[]>(
    '/transactions/search',
    {
      params: searchParams,
      headers: buildEncryptionHeaders(),
    }
  );
  const data = response.data;
  return Array.isArray(data) ? data : data.content;
};

const fetchBorrowingCapacity = async (
  period: number = 90,
  dateRange?: DateRange
): Promise<IBorrowingCapacity> => {
  const response = await apiClient.get<IBorrowingCapacity>('/dashboard/borrowing-capacity', {
    params: periodParams(period, dateRange),
    headers: buildEncryptionHeaders(),
  });
  return response.data;
};

const fetchNetWorthAllocation = async (): Promise<INetWorthAllocation[]> => {
  const response = await apiClient.get<INetWorthAllocation[]>('/dashboard/networth-allocation', {
    headers: buildEncryptionHeaders(),
  });
  return response.data;
};

const fetchDailyCashFlow = async (year?: number, month?: number): Promise<IDailyCashFlow[]> => {
  const response = await apiClient.get<IDailyCashFlow[]>('/dashboard/daily-cashflow', {
    params: { year, month },
  });
  return response.data;
};

const fetchCashflowSankey = async (
  period: number = 30,
  dateRange?: DateRange
): Promise<ICashflowSankey> => {
  const encryptionKey = getEncryptionKey();
  const headers: Record<string, string> = {};
  if (encryptionKey) headers['X-Encryption-Session'] = encryptionKey;
  const response = await apiClient.get<ICashflowSankey>('/dashboard/cashflow-sankey', {
    params: periodParams(period, dateRange),
    headers,
  });
  return response.data;
};

const fetchEstimatedInterest = async (
  period: string = '1Y'
): Promise<IEstimatedInterestSummary> => {
  const response = await apiClient.get<IEstimatedInterestSummary>('/dashboard/estimated-interest', {
    params: { period },
    headers: buildEncryptionHeaders(),
  });
  return response.data;
};

// ─── Query key helpers ────────────────────────────────────────────────────────

/** Stable query-key fragment for period / date-range combos */
function periodKey(period: number, dateRange?: DateRange) {
  return dateRange ? `${dateRange.from}__${dateRange.to}` : period;
}

// ─── Hooks ────────────────────────────────────────────────────────────────────

export const useDashboardSummary = () =>
  useQuery({
    queryKey: ['dashboard', 'summary'],
    queryFn: fetchDashboardSummary,
    staleTime: 5 * 60 * 1000,
    retry: 1,
  });

export const useAccountSummaries = () =>
  useQuery({
    queryKey: ['dashboard', 'accounts'],
    queryFn: fetchAccountSummaries,
    staleTime: 5 * 60 * 1000,
    retry: 1,
  });

export const useCashFlow = (period: number = 30, dateRange?: DateRange) =>
  useQuery({
    queryKey: ['dashboard', 'cashflow', periodKey(period, dateRange)],
    queryFn: () => fetchCashFlow(period, dateRange),
    staleTime: 5 * 60 * 1000,
    retry: 1,
  });

export const useSpendingByCategory = (period: number = 30, dateRange?: DateRange) =>
  useQuery({
    queryKey: ['dashboard', 'spending', periodKey(period, dateRange)],
    queryFn: () => fetchSpendingByCategory(period, dateRange),
    staleTime: 5 * 60 * 1000,
    retry: 1,
  });

export const useNetWorthHistory = (period: number = 365, dateRange?: DateRange) =>
  useQuery({
    queryKey: ['dashboard', 'networth-history', periodKey(period, dateRange)],
    queryFn: () => fetchNetWorthHistory(period, dateRange),
    staleTime: 5 * 60 * 1000,
    retry: 1,
  });

export const useAssetAllocation = () =>
  useQuery({
    queryKey: ['dashboard', 'asset-allocation'],
    queryFn: fetchAssetAllocation,
    staleTime: 5 * 60 * 1000,
    retry: 1,
  });

export const usePortfolioPerformance = (period: number = 30, dateRange?: DateRange) =>
  useQuery({
    queryKey: ['dashboard', 'portfolio-performance', periodKey(period, dateRange)],
    queryFn: () => fetchPortfolioPerformance(period, dateRange),
    staleTime: 5 * 60 * 1000,
    retry: 1,
  });

export const useBorrowingCapacity = (period: number = 90, dateRange?: DateRange) =>
  useQuery({
    queryKey: ['dashboard', 'borrowing-capacity', periodKey(period, dateRange)],
    queryFn: () => fetchBorrowingCapacity(period, dateRange),
    staleTime: 5 * 60 * 1000,
    retry: 1,
  });

export const useNetWorthAllocation = () =>
  useQuery({
    queryKey: ['dashboard', 'networth-allocation'],
    queryFn: fetchNetWorthAllocation,
    staleTime: 5 * 60 * 1000,
    retry: 1,
  });

export const useDailyCashFlow = (year?: number, month?: number) =>
  useQuery({
    queryKey: ['dashboard', 'daily-cashflow', year, month],
    queryFn: () => fetchDailyCashFlow(year, month),
    staleTime: 5 * 60 * 1000,
    retry: 1,
  });

export const useCashflowSankey = (period: number = 30, dateRange?: DateRange) =>
  useQuery({
    queryKey: ['dashboard', 'cashflow-sankey', periodKey(period, dateRange)],
    queryFn: () => fetchCashflowSankey(period, dateRange),
    staleTime: 5 * 60 * 1000,
    retry: 1,
  });

export const useEstimatedInterest = (period: string = '1Y') =>
  useQuery({
    queryKey: ['dashboard', 'estimated-interest', period],
    queryFn: () => fetchEstimatedInterest(period),
    staleTime: 5 * 60 * 1000,
    retry: 1,
  });

export const useTransactionsByPeriod = (period: number = 30, dateRange?: DateRange) =>
  useQuery({
    queryKey: ['dashboard', 'transactions-period', periodKey(period, dateRange)],
    queryFn: () => fetchTransactionsByPeriod(period, dateRange),
    staleTime: 5 * 60 * 1000,
    retry: 1,
  });

// ─── Yearly Balance Variation ─────────────────────────────────────────────────

const fetchYearlyBalance = async (): Promise<IYearlyBalanceResponse> => {
  const response = await apiClient.get<IYearlyBalanceResponse>('/dashboard/yearly-balance', {
    headers: buildEncryptionHeaders(),
  });
  return response.data;
};

export const useYearlyBalance = () =>
  useQuery({
    queryKey: ['dashboard', 'yearly-balance'],
    queryFn: fetchYearlyBalance,
    staleTime: 5 * 60 * 1000,
    retry: 1,
  });
