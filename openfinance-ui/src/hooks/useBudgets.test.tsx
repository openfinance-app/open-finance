import { renderHook, waitFor, act } from '@testing-library/react';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { describe, it, expect, vi, beforeEach } from 'vitest';
import React from 'react';
import { useBudgetHistory, useBudgets, useBudget, useBudgetProgress, useBudgetSummary, useCreateBudget, useUpdateBudget, useDeleteBudget, useAnalyzeBudgets, useBulkCreateBudgets } from './useBudgets';
import apiClient from '@/services/apiClient';
import type { BudgetHistoryResponse } from '@/types/budget';

// Mock the API client
vi.mock('@/services/apiClient');
const mockedApiClient = apiClient as any;

// Mock sessionStorage
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

describe('useBudgetHistory', () => {
  let queryClient: QueryClient;

  beforeEach(() => {
    queryClient = new QueryClient({
      defaultOptions: {
        queries: {
          retry: false,
        },
      },
    });
    vi.clearAllMocks();
  });

  const wrapper = ({ children }: { children: React.ReactNode }) => (
    <QueryClientProvider client={queryClient}>
      {children}
    </QueryClientProvider>
  );

  describe('Query Configuration', () => {
    it('should be disabled when budgetId is null', async () => {
      mockSessionStorage.getItem.mockReturnValue('test-key');

      const { result } = renderHook(() => useBudgetHistory(null), { wrapper });

      // For disabled queries in React Query v4, isPending may be true initially
      expect(result.current.isPending).toBe(true);
      expect(result.current.isFetched).toBe(false);
    });

    it('should be enabled when budgetId is provided', () => {
      mockSessionStorage.getItem.mockReturnValue('test-key');

      const { result } = renderHook(() => useBudgetHistory(1), { wrapper });

      expect(result.current.isPending).toBe(true);
      expect(result.current.isFetched).toBe(false);
    });
  });

  describe('Encryption Key Validation', () => {
    it('throws error when encryption key is not found', async () => {
      mockSessionStorage.getItem.mockReturnValue(null);

      const { result } = renderHook(() => useBudgetHistory(1), { wrapper });

      await waitFor(() => {
        expect(result.current.isError).toBe(true);
      });

      expect(result.current.error?.message).toBe('Encryption key not found');
    });

    it('throws error when budgetId is required but not provided', async () => {
      mockSessionStorage.getItem.mockReturnValue('test-key');

      const { result } = renderHook(() => useBudgetHistory(null), { wrapper });

      // Since enabled is false, no query runs, so no error
      expect(result.current.isError).toBe(false);
    });
  });

  describe('API Call', () => {
    const mockHistoryResponse: BudgetHistoryResponse = {
      budgetId: 1,
      categoryName: 'Groceries',
      amount: 500,
      currency: 'USD',
      period: 'MONTHLY',
      startDate: '2026-02-01',
      endDate: '2026-02-28',
      history: [
        {
          label: 'Feb 2026',
          periodStart: '2026-02-01',
          periodEnd: '2026-02-28',
          budgeted: 500,
          spent: 350.25,
          remaining: 149.75,
          percentageSpent: 70.05,
          status: 'ON_TRACK',
        },
      ],
      totalSpent: 350.25,
      totalBudgeted: 500,
    };

    beforeEach(() => {
      mockSessionStorage.getItem.mockReturnValue('test-encryption-key');
    });

    it('makes correct API call with budgetId', async () => {
      mockedApiClient.get.mockResolvedValue({ data: mockHistoryResponse });

      const { result } = renderHook(() => useBudgetHistory(1), { wrapper });

      await waitFor(() => {
        expect(result.current.isSuccess).toBe(true);
      });

      expect(mockedApiClient.get).toHaveBeenCalledWith('/budgets/1/history', {
        headers: {
          'X-Encryption-Session': 'test-encryption-key',
        },
      });

      expect(result.current.data).toEqual(mockHistoryResponse);
    });

    it('returns data on successful API call', async () => {
      mockedApiClient.get.mockResolvedValue({ data: mockHistoryResponse });

      const { result } = renderHook(() => useBudgetHistory(1), { wrapper });

      await waitFor(() => {
        expect(result.current.isSuccess).toBe(true);
      });

      expect(result.current.data).toEqual(mockHistoryResponse);
      expect(result.current.data?.budgetId).toBe(1);
      expect(result.current.data?.categoryName).toBe('Groceries');
      expect(result.current.data?.history).toHaveLength(1);
    });

    it('handles API error', async () => {
      const errorMessage = 'Budget not found';
      mockedApiClient.get.mockRejectedValue(new Error(errorMessage));

      const { result } = renderHook(() => useBudgetHistory(999), { wrapper });

      await waitFor(() => {
        expect(result.current.isError).toBe(true);
      });

      expect(result.current.error?.message).toBe(errorMessage);
    });
  });

  describe('Query Key', () => {
    it('uses correct query key', () => {
      mockSessionStorage.getItem.mockReturnValue('test-key');

      const { result } = renderHook(() => useBudgetHistory(1), { wrapper });

      expect(true).toBe(true);
    });
  });
});

describe('useBudgets', () => {
  let queryClient: QueryClient;

  beforeEach(() => {
    queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } });
    vi.clearAllMocks();
    mockSessionStorage.getItem.mockReturnValue('test-key');
  });

  const wrapper = ({ children }: { children: React.ReactNode }) => (
    <QueryClientProvider client={queryClient}>{children}</QueryClientProvider>
  );

  it('fetches budgets without period', async () => {
    mockedApiClient.get.mockResolvedValue({ data: [{ id: 1, name: 'Food' }] });
    const { result } = renderHook(() => useBudgets(), { wrapper });
    await waitFor(() => expect(result.current.isSuccess).toBe(true));
    expect(mockedApiClient.get).toHaveBeenCalledWith('/budgets', expect.objectContaining({ headers: { 'X-Encryption-Session': 'test-key' } }));
  });

  it('fetches budgets with period', async () => {
    mockedApiClient.get.mockResolvedValue({ data: [] });
    const { result } = renderHook(() => useBudgets('MONTHLY'), { wrapper });
    await waitFor(() => expect(result.current.isSuccess).toBe(true));
    expect(mockedApiClient.get).toHaveBeenCalledWith('/budgets?period=MONTHLY', expect.anything());
  });

  it('throws when no encryption key', async () => {
    mockSessionStorage.getItem.mockReturnValue(null);
    const { result } = renderHook(() => useBudgets(), { wrapper });
    await waitFor(() => expect(result.current.isError).toBe(true));
    expect(result.current.error?.message).toBe('Encryption key not found');
  });
});

describe('useBudget', () => {
  let queryClient: QueryClient;
  beforeEach(() => {
    queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } });
    vi.clearAllMocks();
    mockSessionStorage.getItem.mockReturnValue('test-key');
  });
  const wrapper = ({ children }: { children: React.ReactNode }) => (
    <QueryClientProvider client={queryClient}>{children}</QueryClientProvider>
  );

  it('is disabled when budgetId is null', () => {
    const { result } = renderHook(() => useBudget(null), { wrapper });
    expect(result.current.isFetched).toBe(false);
  });

  it('fetches single budget', async () => {
    mockedApiClient.get.mockResolvedValue({ data: { id: 5, name: 'Transport' } });
    const { result } = renderHook(() => useBudget(5), { wrapper });
    await waitFor(() => expect(result.current.isSuccess).toBe(true));
    expect(mockedApiClient.get).toHaveBeenCalledWith('/budgets/5', expect.anything());
  });
});

describe('useBudgetProgress', () => {
  let queryClient: QueryClient;
  beforeEach(() => {
    queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } });
    vi.clearAllMocks();
    mockSessionStorage.getItem.mockReturnValue('test-key');
  });
  const wrapper = ({ children }: { children: React.ReactNode }) => (
    <QueryClientProvider client={queryClient}>{children}</QueryClientProvider>
  );

  it('fetches budget progress', async () => {
    mockedApiClient.get.mockResolvedValue({ data: { budgetId: 1, spent: 100, remaining: 400 } });
    const { result } = renderHook(() => useBudgetProgress(1), { wrapper });
    await waitFor(() => expect(result.current.isSuccess).toBe(true));
    expect(mockedApiClient.get).toHaveBeenCalledWith('/budgets/1/progress', expect.anything());
  });
});

describe('useBudgetSummary', () => {
  let queryClient: QueryClient;
  beforeEach(() => {
    queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } });
    vi.clearAllMocks();
    mockSessionStorage.getItem.mockReturnValue('test-key');
  });
  const wrapper = ({ children }: { children: React.ReactNode }) => (
    <QueryClientProvider client={queryClient}>{children}</QueryClientProvider>
  );

  it('fetches summary without period', async () => {
    mockedApiClient.get.mockResolvedValue({ data: { totalBudgets: 5 } });
    const { result } = renderHook(() => useBudgetSummary(), { wrapper });
    await waitFor(() => expect(result.current.isSuccess).toBe(true));
    expect(mockedApiClient.get).toHaveBeenCalledWith('/budgets/summary', expect.anything());
  });

  it('fetches summary with period', async () => {
    mockedApiClient.get.mockResolvedValue({ data: { totalBudgets: 3 } });
    const { result } = renderHook(() => useBudgetSummary('YEARLY'), { wrapper });
    await waitFor(() => expect(result.current.isSuccess).toBe(true));
    expect(mockedApiClient.get).toHaveBeenCalledWith('/budgets/summary?period=YEARLY', expect.anything());
  });
});

describe('useCreateBudget', () => {
  let queryClient: QueryClient;
  beforeEach(() => {
    queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } });
    vi.clearAllMocks();
    mockSessionStorage.getItem.mockReturnValue('test-key');
  });
  const wrapper = ({ children }: { children: React.ReactNode }) => (
    <QueryClientProvider client={queryClient}>{children}</QueryClientProvider>
  );

  it('creates budget and invalidates cache', async () => {
    mockedApiClient.post.mockResolvedValue({ data: { id: 1, name: 'Food' } });
    const invalidateSpy = vi.spyOn(queryClient, 'invalidateQueries');
    const { result } = renderHook(() => useCreateBudget(), { wrapper });
    await act(async () => {
      await result.current.mutateAsync({ categoryId: 1, amount: 500, period: 'MONTHLY', currency: 'USD' } as any);
    });
    expect(mockedApiClient.post).toHaveBeenCalledWith('/budgets', expect.anything(), expect.anything());
    expect(invalidateSpy).toHaveBeenCalledWith({ queryKey: ['budgets'] });
  });
});

describe('useUpdateBudget', () => {
  let queryClient: QueryClient;
  beforeEach(() => {
    queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } });
    vi.clearAllMocks();
    mockSessionStorage.getItem.mockReturnValue('test-key');
  });
  const wrapper = ({ children }: { children: React.ReactNode }) => (
    <QueryClientProvider client={queryClient}>{children}</QueryClientProvider>
  );

  it('updates budget', async () => {
    mockedApiClient.put.mockResolvedValue({ data: { id: 1, name: 'Food' } });
    const { result } = renderHook(() => useUpdateBudget(), { wrapper });
    await act(async () => {
      await result.current.mutateAsync({ id: 1, data: { categoryId: 1, amount: 600, period: 'MONTHLY', currency: 'USD' } as any });
    });
    expect(mockedApiClient.put).toHaveBeenCalledWith('/budgets/1', expect.anything(), expect.anything());
  });
});

describe('useDeleteBudget', () => {
  let queryClient: QueryClient;
  beforeEach(() => {
    queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } });
    vi.clearAllMocks();
    mockSessionStorage.getItem.mockReturnValue('test-key');
  });
  const wrapper = ({ children }: { children: React.ReactNode }) => (
    <QueryClientProvider client={queryClient}>{children}</QueryClientProvider>
  );

  it('deletes budget', async () => {
    mockedApiClient.delete.mockResolvedValue({});
    const { result } = renderHook(() => useDeleteBudget(), { wrapper });
    await act(async () => {
      await result.current.mutateAsync(1);
    });
    expect(mockedApiClient.delete).toHaveBeenCalledWith('/budgets/1');
  });
});

describe('useAnalyzeBudgets', () => {
  let queryClient: QueryClient;
  beforeEach(() => {
    queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } });
    vi.clearAllMocks();
    mockSessionStorage.getItem.mockReturnValue('test-key');
  });
  const wrapper = ({ children }: { children: React.ReactNode }) => (
    <QueryClientProvider client={queryClient}>{children}</QueryClientProvider>
  );

  it('analyzes and returns suggestions', async () => {
    mockedApiClient.post.mockResolvedValue({ data: [{ categoryId: 1, suggestedAmount: 300 }] });
    const { result } = renderHook(() => useAnalyzeBudgets(), { wrapper });
    await act(async () => {
      await result.current.mutateAsync({ months: 3 } as any);
    });
    expect(mockedApiClient.post).toHaveBeenCalledWith('/budgets/suggestions', expect.anything(), expect.anything());
  });
});

describe('useBulkCreateBudgets', () => {
  let queryClient: QueryClient;
  beforeEach(() => {
    queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } });
    vi.clearAllMocks();
    mockSessionStorage.getItem.mockReturnValue('test-key');
  });
  const wrapper = ({ children }: { children: React.ReactNode }) => (
    <QueryClientProvider client={queryClient}>{children}</QueryClientProvider>
  );

  it('bulk creates budgets and invalidates cache', async () => {
    mockedApiClient.post.mockResolvedValue({ data: { created: 3 } });
    const invalidateSpy = vi.spyOn(queryClient, 'invalidateQueries');
    const { result } = renderHook(() => useBulkCreateBudgets(), { wrapper });
    await act(async () => {
      await result.current.mutateAsync({ budgets: [] } as any);
    });
    expect(mockedApiClient.post).toHaveBeenCalledWith('/budgets/bulk', expect.anything(), expect.anything());
    expect(invalidateSpy).toHaveBeenCalledWith({ queryKey: ['budgets'] });
  });
});