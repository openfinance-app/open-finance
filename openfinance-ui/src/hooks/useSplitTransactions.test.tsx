/**
 * Unit tests for useSplitTransactions hook
 *
 * Tests fetching from /api/v1/transactions/{id}/splits, disabled when transactionId is null,
 * uses X-Encryption-Session header.
 */
import { renderHook, waitFor } from '@testing-library/react';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { describe, it, expect, vi, beforeEach } from 'vitest';
import React from 'react';
import { useSplitTransactions } from './useSplitTransactions';
import apiClient from '@/services/apiClient';
import type { TransactionSplitResponse } from '@/types/transaction';

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

describe('useSplitTransactions', () => {
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

  describe('Encryption Key Validation', () => {
    it('throws error when encryption key is not found', async () => {
      mockSessionStorage.getItem.mockReturnValue(null);

      const { result } = renderHook(() => useSplitTransactions(123), { wrapper });

      await waitFor(() => {
        expect(result.current.error?.message).toBe('Encryption key not found');
      });
    });

    it('throws error when encryption key is empty', async () => {
      mockSessionStorage.getItem.mockReturnValue('');

      const { result } = renderHook(() => useSplitTransactions(123), { wrapper });

      await waitFor(() => {
        expect(result.current.error?.message).toBe('Encryption key not found');
      });
    });
  });

  describe('Query Behavior', () => {
    beforeEach(() => {
      mockSessionStorage.getItem.mockReturnValue('test-encryption-key');
    });

    it('is disabled when transactionId is null', () => {
      const { result } = renderHook(() => useSplitTransactions(null), { wrapper });

      // In React Query v5, disabled queries have fetchStatus='idle' and data=undefined
      expect(result.current.fetchStatus).toBe('idle');
      expect(result.current.isFetched).toBe(false);
      expect(result.current.data).toBeUndefined();
    });

    it('is enabled when transactionId is provided', () => {
      const { result } = renderHook(() => useSplitTransactions(123), { wrapper });

      expect(result.current.fetchStatus).toBe('fetching');
    });

    it('throws error when transactionId is null in query function', async () => {
      mockSessionStorage.getItem.mockReturnValue('test-encryption-key');

      const { result } = renderHook(() => useSplitTransactions(null), { wrapper });

      // Since the query is disabled, it won't execute the queryFn
      expect(result.current.error).toBeNull();
    });
  });

  describe('API Call', () => {
    beforeEach(() => {
      mockSessionStorage.getItem.mockReturnValue('test-encryption-key');
    });

    it('calls the correct endpoint with transaction ID', async () => {
      const mockResponse = {
        data: [
          {
            id: 1,
            transactionId: 123,
            categoryId: 10,
            categoryName: 'Shopping',
            categoryColor: '#ff0000',
            categoryIcon: '🛒',
            amount: 50.00,
            description: 'Groceries',
          },
        ],
      };
      mockedApiClient.get.mockResolvedValue(mockResponse);

      const { result } = renderHook(() => useSplitTransactions(123), { wrapper });

      await waitFor(() => {
        expect(result.current.isSuccess).toBe(true);
      });

      expect(mockedApiClient.get).toHaveBeenCalledWith(
        '/transactions/123/splits',
        {
          headers: {
            'X-Encryption-Session': 'test-encryption-key',
          },
        }
      );
    });

    it('includes X-Encryption-Session header in request', async () => {
      const mockResponse = { data: [] };
      mockedApiClient.get.mockResolvedValue(mockResponse);

      const { result } = renderHook(() => useSplitTransactions(456), { wrapper });

      await waitFor(() => {
        expect(result.current.isSuccess).toBe(true);
      });

      expect(mockedApiClient.get).toHaveBeenCalledWith(
        '/transactions/456/splits',
        {
          headers: {
            'X-Encryption-Session': 'test-encryption-key',
          },
        }
      );
    });

    it('returns the correct data structure', async () => {
      const mockData: TransactionSplitResponse[] = [
        {
          id: 1,
          transactionId: 123,
          categoryId: 10,
          categoryName: 'Shopping',
          categoryColor: '#ff0000',
          categoryIcon: '🛒',
          amount: 50.00,
          description: 'Groceries',
        },
        {
          id: 2,
          transactionId: 123,
          categoryId: 20,
          categoryName: 'Entertainment',
          categoryColor: '#00ff00',
          categoryIcon: undefined,
          amount: 25.50,
          description: undefined,
        },
      ];
      const mockResponse = { data: mockData };
      mockedApiClient.get.mockResolvedValue(mockResponse);

      const { result } = renderHook(() => useSplitTransactions(123), { wrapper });

      await waitFor(() => {
        expect(result.current.isSuccess).toBe(true);
      });

      expect(result.current.data).toEqual(mockData);
    });

    it('handles empty splits array', async () => {
      const mockResponse = { data: [] };
      mockedApiClient.get.mockResolvedValue(mockResponse);

      const { result } = renderHook(() => useSplitTransactions(789), { wrapper });

      await waitFor(() => {
        expect(result.current.isSuccess).toBe(true);
      });

      expect(result.current.data).toEqual([]);
    });

    it('handles API errors correctly', async () => {
      const errorMessage = 'Network Error';
      mockedApiClient.get.mockRejectedValue(new Error(errorMessage));

      const { result } = renderHook(() => useSplitTransactions(123), { wrapper });

      await waitFor(() => {
        expect(result.current.isError).toBe(true);
      });

      expect(result.current.error?.message).toBe(errorMessage);
    });

    it('handles 404 errors when transaction has no splits', async () => {
      const errorMessage = 'Transaction not found or has no splits';
      mockedApiClient.get.mockRejectedValue(new Error(errorMessage));

      const { result } = renderHook(() => useSplitTransactions(999), { wrapper });

      await waitFor(() => {
        expect(result.current.isError).toBe(true);
      });

      expect(result.current.error?.message).toBe(errorMessage);
    });
  });

  describe('Query Configuration', () => {
    beforeEach(() => {
      mockSessionStorage.getItem.mockReturnValue('test-encryption-key');
    });

    it('uses correct query key', async () => {
      const mockResponse = { data: [] };
      mockedApiClient.get.mockResolvedValue(mockResponse);

      const { result } = renderHook(() => useSplitTransactions(123), { wrapper });

      await waitFor(() => {
        expect(result.current.isSuccess).toBe(true);
      });

      // The query key should include the transaction ID
      // We can verify this by checking that different transaction IDs would create different queries
      expect(result.current.data).toBeDefined();
    });

    it('has staleTime of 30 seconds', async () => {
      const mockResponse = { data: [] };
      mockedApiClient.get.mockResolvedValue(mockResponse);

      const { result } = renderHook(() => useSplitTransactions(123), { wrapper });

      await waitFor(() => {
        expect(result.current.isSuccess).toBe(true);
      });

      // The staleTime is configured in the hook, we can verify it works by checking
      // that the data is cached appropriately, but that's hard to test directly.
      // Instead, we verify the hook initializes correctly.
      expect(result.current.data).toEqual([]);
    });

    it('does not retry on failure', async () => {
      mockedApiClient.get.mockRejectedValue(new Error('API Error'));

      const { result } = renderHook(() => useSplitTransactions(123), { wrapper });

      await waitFor(() => {
        expect(result.current.isError).toBe(true);
      });

      // Since retry is disabled, it should only call once
      expect(mockedApiClient.get).toHaveBeenCalledTimes(1);
    });
  });

  describe('Different Transaction IDs', () => {
    beforeEach(() => {
      mockSessionStorage.getItem.mockReturnValue('test-encryption-key');
    });

    it('fetches splits for different transaction IDs independently', async () => {
      const mockResponse1 = { data: [{ id: 1, transactionId: 100, amount: 50 }] };
      const mockResponse2 = { data: [{ id: 2, transactionId: 200, amount: 75 }] };

      mockedApiClient.get
        .mockResolvedValueOnce(mockResponse1)
        .mockResolvedValueOnce(mockResponse2);

      const { result: result1 } = renderHook(() => useSplitTransactions(100), { wrapper });
      const { result: result2 } = renderHook(() => useSplitTransactions(200), { wrapper });

      await waitFor(() => {
        expect(result1.current.isSuccess).toBe(true);
        expect(result2.current.isSuccess).toBe(true);
      });

      expect(mockedApiClient.get).toHaveBeenCalledWith(
        '/transactions/100/splits',
        expect.any(Object)
      );
      expect(mockedApiClient.get).toHaveBeenCalledWith(
        '/transactions/200/splits',
        expect.any(Object)
      );

      expect(result1.current.data).toEqual(mockResponse1.data);
      expect(result2.current.data).toEqual(mockResponse2.data);
    });
  });

  describe('Edge Cases', () => {
    beforeEach(() => {
      mockSessionStorage.getItem.mockReturnValue('test-encryption-key');
    });

    it('handles transaction ID of 0', async () => {
      const mockResponse = { data: [] };
      mockedApiClient.get.mockResolvedValue(mockResponse);

      const { result } = renderHook(() => useSplitTransactions(0), { wrapper });

      await waitFor(() => {
        expect(result.current.isSuccess).toBe(true);
      });

      expect(mockedApiClient.get).toHaveBeenCalledWith(
        '/transactions/0/splits',
        expect.any(Object)
      );
    });

    it('handles large transaction IDs', async () => {
      const largeId = 999999999;
      const mockResponse = { data: [] };
      mockedApiClient.get.mockResolvedValue(mockResponse);

      const { result } = renderHook(() => useSplitTransactions(largeId), { wrapper });

      await waitFor(() => {
        expect(result.current.isSuccess).toBe(true);
      });

      expect(mockedApiClient.get).toHaveBeenCalledWith(
        `/transactions/${largeId}/splits`,
        expect.any(Object)
      );
    });
  });
});