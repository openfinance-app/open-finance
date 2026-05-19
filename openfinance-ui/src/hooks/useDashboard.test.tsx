import { renderHook, waitFor } from '@testing-library/react';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { describe, it, expect, vi, beforeEach } from 'vitest';
import React from 'react';
import {
  useDashboardSummary,
  useAccountSummaries,
  useCashFlow,
  useSpendingByCategory,
  useNetWorthHistory,
  useAssetAllocation,
  usePortfolioPerformance,
  useBorrowingCapacity,
  useNetWorthAllocation,
  useDailyCashFlow,
  useCashflowSankey,
  useEstimatedInterest,
  useTransactionsByPeriod,
} from './useDashboard';
import apiClient from '@/services/apiClient';

vi.mock('@/services/apiClient');
const mockedApiClient = apiClient as any;

const mockSessionStorage = {
  getItem: vi.fn(),
  setItem: vi.fn(),
  removeItem: vi.fn(),
  clear: vi.fn(),
  key: vi.fn(),
  length: 0,
};
Object.defineProperty(window, 'sessionStorage', {
  value: mockSessionStorage,
});

describe('useDashboard hooks', () => {
  let queryClient: QueryClient;

  beforeEach(() => {
    queryClient = new QueryClient({
      defaultOptions: { queries: { retry: false } },
    });
    vi.clearAllMocks();
    mockSessionStorage.getItem.mockReturnValue('test-encryption-key');
  });

  const wrapper = ({ children }: { children: React.ReactNode }) => (
    <QueryClientProvider client={queryClient}>{children}</QueryClientProvider>
  );

  // ── useDashboardSummary ─────────────────────────────────────────────────
  describe('useDashboardSummary', () => {
    it('should fetch dashboard summary', async () => {
      const mockSummary = { totalAssets: 50000, totalLiabilities: 10000, netWorth: 40000 };
      mockedApiClient.get.mockResolvedValue({ data: mockSummary });

      const { result } = renderHook(() => useDashboardSummary(), { wrapper });

      await waitFor(() => expect(result.current.isSuccess).toBe(true));
      expect(result.current.data).toEqual(mockSummary);
    });

    it('should throw error when encryption key is missing', async () => {
      mockSessionStorage.getItem.mockReturnValue(null);

      const { result } = renderHook(() => useDashboardSummary(), { wrapper });

      // The hook has retry: 1, so we need extra time for the retry to complete
      await waitFor(() => expect(result.current.isError).toBe(true), { timeout: 5000 });
      expect(result.current.error?.message).toContain('Encryption key not found');
    });
  });

  // ── useAccountSummaries ─────────────────────────────────────────────────
  describe('useAccountSummaries', () => {
    it('should fetch account summaries', async () => {
      const mockAccounts = [{ id: 1, name: 'Checking', balance: 5000 }];
      mockedApiClient.get.mockResolvedValue({ data: mockAccounts });

      const { result } = renderHook(() => useAccountSummaries(), { wrapper });

      await waitFor(() => expect(result.current.isSuccess).toBe(true));
      expect(result.current.data).toEqual(mockAccounts);
    });
  });

  // ── useCashFlow ─────────────────────────────────────────────────────────
  describe('useCashFlow', () => {
    it('should fetch cash flow with default period', async () => {
      const mockCashFlow = { income: 5000, expenses: 3000, savings: 2000 };
      mockedApiClient.get.mockResolvedValue({ data: mockCashFlow });

      const { result } = renderHook(() => useCashFlow(), { wrapper });

      await waitFor(() => expect(result.current.isSuccess).toBe(true));
      expect(mockedApiClient.get).toHaveBeenCalledWith('/dashboard/cashflow', {
        params: { period: 30 },
      });
    });

    it('should fetch cash flow with custom period', async () => {
      const mockCashFlow = { income: 15000, expenses: 9000, savings: 6000 };
      mockedApiClient.get.mockResolvedValue({ data: mockCashFlow });

      const { result } = renderHook(() => useCashFlow(90), { wrapper });

      await waitFor(() => expect(result.current.isSuccess).toBe(true));
      expect(mockedApiClient.get).toHaveBeenCalledWith('/dashboard/cashflow', {
        params: { period: 90 },
      });
    });

    it('should fetch cash flow with date range', async () => {
      const mockCashFlow = { income: 5000, expenses: 3000 };
      mockedApiClient.get.mockResolvedValue({ data: mockCashFlow });
      const dateRange = { from: '2026-01-01', to: '2026-03-31' };

      const { result } = renderHook(() => useCashFlow(30, dateRange), { wrapper });

      await waitFor(() => expect(result.current.isSuccess).toBe(true));
      expect(mockedApiClient.get).toHaveBeenCalledWith('/dashboard/cashflow', {
        params: { startDate: '2026-01-01', endDate: '2026-03-31' },
      });
    });
  });

  // ── useSpendingByCategory ───────────────────────────────────────────────
  describe('useSpendingByCategory', () => {
    it('should fetch spending by category', async () => {
      const mockSpending = { categories: [{ name: 'Food', amount: 500 }] };
      mockedApiClient.get.mockResolvedValue({ data: mockSpending });

      const { result } = renderHook(() => useSpendingByCategory(), { wrapper });

      await waitFor(() => expect(result.current.isSuccess).toBe(true));
      expect(mockedApiClient.get).toHaveBeenCalledWith('/dashboard/spending', {
        params: { period: 30 },
      });
    });
  });

  // ── useNetWorthHistory ──────────────────────────────────────────────────
  describe('useNetWorthHistory', () => {
    it('should fetch net worth history', async () => {
      const mockHistory = [{ date: '2026-01-01', netWorth: 40000 }];
      mockedApiClient.get.mockResolvedValue({ data: mockHistory });

      const { result } = renderHook(() => useNetWorthHistory(), { wrapper });

      await waitFor(() => expect(result.current.isSuccess).toBe(true));
      expect(mockedApiClient.get).toHaveBeenCalledWith('/dashboard/networth-history', expect.objectContaining({
        params: { period: 365 },
      }));
    });
  });

  // ── useAssetAllocation ──────────────────────────────────────────────────
  describe('useAssetAllocation', () => {
    it('should fetch asset allocation', async () => {
      const mockAllocation = [{ type: 'STOCK', percentage: 60 }];
      mockedApiClient.get.mockResolvedValue({ data: mockAllocation });

      const { result } = renderHook(() => useAssetAllocation(), { wrapper });

      await waitFor(() => expect(result.current.isSuccess).toBe(true));
      expect(mockedApiClient.get).toHaveBeenCalledWith('/dashboard/asset-allocation');
    });
  });

  // ── usePortfolioPerformance ─────────────────────────────────────────────
  describe('usePortfolioPerformance', () => {
    it('should fetch portfolio performance', async () => {
      const mockPerformance = [{ date: '2026-01-01', value: 50000 }];
      mockedApiClient.get.mockResolvedValue({ data: mockPerformance });

      const { result } = renderHook(() => usePortfolioPerformance(), { wrapper });

      await waitFor(() => expect(result.current.isSuccess).toBe(true));
      expect(mockedApiClient.get).toHaveBeenCalledWith('/dashboard/portfolio-performance', {
        params: { period: 30 },
      });
    });

    it('should fetch portfolio performance with date range', async () => {
      const mockPerformance = [{ date: '2026-03-01', value: 52000 }];
      mockedApiClient.get.mockResolvedValue({ data: mockPerformance });
      const dateRange = { from: '2026-02-18', to: '2026-05-19' };

      const { result } = renderHook(() => usePortfolioPerformance(91, dateRange), { wrapper });

      await waitFor(() => expect(result.current.isSuccess).toBe(true));
      expect(mockedApiClient.get).toHaveBeenCalledWith('/dashboard/portfolio-performance', {
        params: { startDate: '2026-02-18', endDate: '2026-05-19' },
      });
    });
  });

  // ── useBorrowingCapacity ────────────────────────────────────────────────
  describe('useBorrowingCapacity', () => {
    it('should fetch borrowing capacity', async () => {
      const mockCapacity = { maxBorrowing: 200000, debtToIncome: 0.3 };
      mockedApiClient.get.mockResolvedValue({ data: mockCapacity });

      const { result } = renderHook(() => useBorrowingCapacity(), { wrapper });

      await waitFor(() => expect(result.current.isSuccess).toBe(true));
      expect(mockedApiClient.get).toHaveBeenCalledWith('/dashboard/borrowing-capacity', expect.objectContaining({
        params: { period: 90 },
      }));
    });
  });

  // ── useNetWorthAllocation ───────────────────────────────────────────────
  describe('useNetWorthAllocation', () => {
    it('should fetch net worth allocation', async () => {
      const mockAllocation = [{ category: 'Savings', value: 20000 }];
      mockedApiClient.get.mockResolvedValue({ data: mockAllocation });

      const { result } = renderHook(() => useNetWorthAllocation(), { wrapper });

      await waitFor(() => expect(result.current.isSuccess).toBe(true));
    });
  });

  // ── useDailyCashFlow ────────────────────────────────────────────────────
  describe('useDailyCashFlow', () => {
    it('should fetch daily cash flow with year and month', async () => {
      const mockDaily = [{ date: '2026-03-01', income: 100, expense: 50 }];
      mockedApiClient.get.mockResolvedValue({ data: mockDaily });

      const { result } = renderHook(() => useDailyCashFlow(2026, 3), { wrapper });

      await waitFor(() => expect(result.current.isSuccess).toBe(true));
      expect(mockedApiClient.get).toHaveBeenCalledWith('/dashboard/daily-cashflow', {
        params: { year: 2026, month: 3 },
      });
    });
  });

  // ── useCashflowSankey ───────────────────────────────────────────────────
  describe('useCashflowSankey', () => {
    it('should fetch cashflow sankey data', async () => {
      const mockSankey = { nodes: [], links: [] };
      mockedApiClient.get.mockResolvedValue({ data: mockSankey });

      const { result } = renderHook(() => useCashflowSankey(), { wrapper });

      await waitFor(() => expect(result.current.isSuccess).toBe(true));
    });
  });

  // ── useEstimatedInterest ────────────────────────────────────────────────
  describe('useEstimatedInterest', () => {
    it('should fetch estimated interest', async () => {
      const mockInterest = { total: 1200, accounts: [] };
      mockedApiClient.get.mockResolvedValue({ data: mockInterest });

      const { result } = renderHook(() => useEstimatedInterest(), { wrapper });

      await waitFor(() => expect(result.current.isSuccess).toBe(true));
      expect(mockedApiClient.get).toHaveBeenCalledWith('/dashboard/estimated-interest', expect.objectContaining({
        params: { period: '1Y' },
      }));
    });
  });

  // ── useTransactionsByPeriod ─────────────────────────────────────────────
  describe('useTransactionsByPeriod', () => {
    it('should fetch transactions by period', async () => {
      const mockTransactions = { content: [{ id: 1, description: 'Test', amount: -50 }] };
      mockedApiClient.get.mockResolvedValue({ data: mockTransactions });

      const { result } = renderHook(() => useTransactionsByPeriod(), { wrapper });

      await waitFor(() => expect(result.current.isSuccess).toBe(true));
    });

    it('should handle array response format', async () => {
      const mockTransactions = [{ id: 1, description: 'Test', amount: -50 }];
      mockedApiClient.get.mockResolvedValue({ data: mockTransactions });

      const { result } = renderHook(() => useTransactionsByPeriod(), { wrapper });

      await waitFor(() => expect(result.current.isSuccess).toBe(true));
      expect(result.current.data).toEqual(mockTransactions);
    });
  });
});
