import { renderHook, waitFor } from '@testing-library/react';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { describe, it, expect, vi, beforeEach } from 'vitest';
import React from 'react';
import { useTransactions, useCreateTransaction, useCreateTransfer, useUpdateTransaction, useUpdateTransfer, useDeleteTransaction, useCategories, useCreateCategory, useCategoryTree, useDeleteCategory, useUpdateCategory } from './useTransactions';
import apiClient from '@/services/apiClient';

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

describe('useTransactions', () => {
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

      const { result } = renderHook(() => useTransactions(), { wrapper });

      await waitFor(() => {
        expect(result.current.error?.message).toBe('Encryption key not found');
      });
    });

    it('throws error when encryption key is empty', async () => {
      mockSessionStorage.getItem.mockReturnValue('');

      const { result } = renderHook(() => useTransactions(), { wrapper });

      await waitFor(() => {
        expect(result.current.error?.message).toBe('Encryption key not found');
      });
    });
  });

  describe('API Call', () => {
    beforeEach(() => {
      mockSessionStorage.getItem.mockReturnValue('test-encryption-key');
    });

    it('calls the correct endpoint without filters', async () => {
      const mockResponse = {
        data: {
          content: [],
          totalElements: 0,
          totalPages: 0,
          number: 0,
          size: 20,
        },
      };
      mockedApiClient.get.mockResolvedValue(mockResponse);

      const { result } = renderHook(() => useTransactions(), { wrapper });

      await waitFor(() => {
        expect(result.current.isSuccess).toBe(true);
      });

      expect(mockedApiClient.get).toHaveBeenCalledWith(
        '/transactions/search?sort=date%2Cdesc',
        {
          headers: {
            'X-Encryption-Session': 'test-encryption-key',
          },
        }
      );
    });

    it('builds correct query parameters with filters', async () => {
      const mockResponse = {
        data: {
          content: [],
          totalElements: 0,
          totalPages: 0,
          number: 0,
          size: 20,
        },
      };
      mockedApiClient.get.mockResolvedValue(mockResponse);

      const filters = {
        accountId: 1,
        type: 'EXPENSE' as const,
        categoryId: 2,
        dateFrom: '2026-01-01',
        dateTo: '2026-01-31',
        keyword: 'grocery',
        page: 1,
        size: 10,
        sort: 'amount,asc',
      };

      const { result } = renderHook(() => useTransactions(filters), { wrapper });

      await waitFor(() => {
        expect(result.current.isSuccess).toBe(true);
      });

      const expectedUrl =
        '/transactions/search?' +
        'accountId=1&' +
        'type=EXPENSE&' +
        'categoryId=2&' +
        'dateFrom=2026-01-01&' +
        'dateTo=2026-01-31&' +
        'keyword=grocery&' +
        'page=1&' +
        'size=10&' +
        'sort=amount%2Casc';

      expect(mockedApiClient.get).toHaveBeenCalledWith(
        expectedUrl,
        {
          headers: {
            'X-Encryption-Session': 'test-encryption-key',
          },
        }
      );
    });

    it('uses default sort when no sort is provided', async () => {
      const mockResponse = {
        data: {
          content: [],
          totalElements: 0,
          totalPages: 0,
          number: 0,
          size: 20,
        },
      };
      mockedApiClient.get.mockResolvedValue(mockResponse);

      const filters = {
        keyword: 'test',
      };

      const { result } = renderHook(() => useTransactions(filters), { wrapper });

      await waitFor(() => {
        expect(result.current.isSuccess).toBe(true);
      });

      expect(mockedApiClient.get).toHaveBeenCalledWith(
        '/transactions/search?keyword=test&sort=date%2Cdesc',
        {
          headers: {
            'X-Encryption-Session': 'test-encryption-key',
          },
        }
      );
    });

    it('handles partial filters correctly', async () => {
      const mockResponse = {
        data: {
          content: [],
          totalElements: 0,
          totalPages: 0,
          number: 0,
          size: 20,
        },
      };
      mockedApiClient.get.mockResolvedValue(mockResponse);

      const filters = {
        accountId: 1,
        page: 0,
      };

      const { result } = renderHook(() => useTransactions(filters), { wrapper });

      await waitFor(() => {
        expect(result.current.isSuccess).toBe(true);
      });

      expect(mockedApiClient.get).toHaveBeenCalledWith(
        '/transactions/search?accountId=1&page=0&sort=date%2Cdesc',
        {
          headers: {
            'X-Encryption-Session': 'test-encryption-key',
          },
        }
      );
    });

    it('returns the correct data structure', async () => {
      const mockData = {
        content: [
          { id: 1, description: 'Test transaction', amount: 100 },
        ],
        totalElements: 1,
        totalPages: 1,
        number: 0,
        size: 20,
      };
      const mockResponse = { data: mockData };
      mockedApiClient.get.mockResolvedValue(mockResponse);

      const { result } = renderHook(() => useTransactions(), { wrapper });

      await waitFor(() => {
        expect(result.current.isSuccess).toBe(true);
      });

      expect(result.current.data).toEqual(mockData);
    });

    it('handles API errors correctly', async () => {
      const errorMessage = 'API Error';
      mockedApiClient.get.mockRejectedValue(new Error(errorMessage));

      const { result } = renderHook(() => useTransactions(), { wrapper });

      await waitFor(() => {
        expect(result.current.isError).toBe(true);
      });

      expect(result.current.error?.message).toBe(errorMessage);
    });
  });

  describe('Query Key', () => {
    // Note: React Query hooks don't expose queryKey in their return value.
    // These tests verify the hooks can be called with different parameters,
    // ensuring the query system works correctly.
    it('works without filters', () => {
      const { result } = renderHook(() => useTransactions(), { wrapper });
      // Just verify the hook initializes correctly
      expect(result.current).toBeDefined();
    });

    it('works with filters', () => {
      const filters = { keyword: 'test' };
      const { result } = renderHook(() => useTransactions(filters), { wrapper });
      // Just verify the hook initializes correctly with filters
      expect(result.current).toBeDefined();
    });
  });

  describe('useCreateTransaction', () => {
    it('calls POST /transactions on mutate', async () => {
      mockSessionStorage.getItem.mockReturnValue('test-key');
      mockedApiClient.post.mockResolvedValue({ data: { id: 1, description: 'New' } });

      const { result } = renderHook(() => useCreateTransaction(), { wrapper });
      await result.current.mutateAsync({ type: 'EXPENSE', accountId: 1, amount: 50, currency: 'USD', date: '2026-01-01', description: 'Test' });

      expect(mockedApiClient.post).toHaveBeenCalledWith('/transactions', expect.any(Object), expect.objectContaining({ headers: { 'X-Encryption-Session': 'test-key' } }));
    });

    it('throws when encryption key missing', async () => {
      mockSessionStorage.getItem.mockReturnValue(null);
      const { result } = renderHook(() => useCreateTransaction(), { wrapper });
      await expect(result.current.mutateAsync({ type: 'EXPENSE', accountId: 1, amount: 50, currency: 'USD', date: '2026-01-01', description: 'Test' })).rejects.toThrow('Encryption key not found');
    });
  });

  describe('useCreateTransfer', () => {
    it('calls POST /transactions/transfer', async () => {
      mockSessionStorage.getItem.mockReturnValue('test-key');
      mockedApiClient.post.mockResolvedValue({ data: { id: 2 } });

      const { result } = renderHook(() => useCreateTransfer(), { wrapper });
      await result.current.mutateAsync({ type: 'TRANSFER', accountId: 1, toAccountId: 2, amount: 100, currency: 'USD', date: '2026-01-01', description: 'Transfer' } as any);

      expect(mockedApiClient.post).toHaveBeenCalledWith('/transactions/transfer', expect.any(Object), expect.any(Object));
    });
  });

  describe('useUpdateTransaction', () => {
    it('calls PUT /transactions/:id', async () => {
      mockSessionStorage.getItem.mockReturnValue('test-key');
      mockedApiClient.put.mockResolvedValue({ data: { id: 1 } });

      const { result } = renderHook(() => useUpdateTransaction(), { wrapper });
      await result.current.mutateAsync({ id: 1, data: { type: 'EXPENSE', accountId: 1, amount: 75, currency: 'USD', date: '2026-01-01', description: 'Updated' } });

      expect(mockedApiClient.put).toHaveBeenCalledWith('/transactions/1', expect.any(Object), expect.any(Object));
    });

    it('throws when encryption key missing', async () => {
      mockSessionStorage.getItem.mockReturnValue(null);
      const { result } = renderHook(() => useUpdateTransaction(), { wrapper });
      await expect(result.current.mutateAsync({ id: 1, data: { type: 'EXPENSE', accountId: 1, amount: 75, currency: 'USD', date: '2026-01-01', description: 'x' } })).rejects.toThrow('Encryption key not found');
    });
  });

  describe('useUpdateTransfer', () => {
    it('calls PUT /transactions/transfer/:transferId', async () => {
      mockSessionStorage.getItem.mockReturnValue('test-key');
      mockedApiClient.put.mockResolvedValue({ data: { id: 1 } });

      const { result } = renderHook(() => useUpdateTransfer(), { wrapper });
      await result.current.mutateAsync({ transferId: 99, data: { fromAccountId: 1, toAccountId: 2, amount: 200, currency: 'USD', date: '2026-01-01' } } as any);

      expect(mockedApiClient.put).toHaveBeenCalledWith('/transactions/transfers/99', expect.any(Object), expect.any(Object));
    });
  });

  describe('useDeleteTransaction', () => {
    it('calls DELETE /transactions/:id', async () => {
      mockSessionStorage.getItem.mockReturnValue('test-key');
      mockedApiClient.delete.mockResolvedValue({});

      const { result } = renderHook(() => useDeleteTransaction(), { wrapper });
      await result.current.mutateAsync(42);

      expect(mockedApiClient.delete).toHaveBeenCalledWith('/transactions/42', expect.any(Object));
    });

    it('throws when encryption key missing', async () => {
      mockSessionStorage.getItem.mockReturnValue(null);
      const { result } = renderHook(() => useDeleteTransaction(), { wrapper });
      await expect(result.current.mutateAsync(42)).rejects.toThrow('Encryption key not found');
    });
  });

  describe('useCategories', () => {
    it('fetches categories', async () => {
      mockSessionStorage.getItem.mockReturnValue('test-key');
      mockedApiClient.get.mockResolvedValue({ data: [{ id: 1, name: 'Food' }] });

      const { result } = renderHook(() => useCategories(), { wrapper });
      await waitFor(() => expect(result.current.data).toBeDefined());
      expect(mockedApiClient.get).toHaveBeenCalledWith('/categories', expect.any(Object));
    });

    it('fetches categories by type', async () => {
      mockSessionStorage.getItem.mockReturnValue('test-key');
      mockedApiClient.get.mockResolvedValue({ data: [{ id: 1, name: 'Salary' }] });

      const { result } = renderHook(() => useCategories('INCOME'), { wrapper });
      await waitFor(() => expect(result.current.data).toBeDefined());
      expect(mockedApiClient.get).toHaveBeenCalledWith('/categories?type=INCOME', expect.any(Object));
    });
  });

  describe('useCreateCategory', () => {
    it('calls POST /categories', async () => {
      mockSessionStorage.getItem.mockReturnValue('test-key');
      mockedApiClient.post.mockResolvedValue({ data: { id: 3, name: 'New Cat' } });

      const { result } = renderHook(() => useCreateCategory(), { wrapper });
      await result.current.mutateAsync({ name: 'New Cat', type: 'EXPENSE' } as any);

      expect(mockedApiClient.post).toHaveBeenCalledWith('/categories', expect.any(Object), expect.any(Object));
    });
  });

  describe('useCategoryTree', () => {
    it('fetches category tree', async () => {
      mockSessionStorage.getItem.mockReturnValue('test-key');
      mockedApiClient.get.mockResolvedValue({ data: [{ id: 1, name: 'Food', children: [] }] });

      const { result } = renderHook(() => useCategoryTree(), { wrapper });
      await waitFor(() => expect(result.current.data).toBeDefined());
      expect(mockedApiClient.get).toHaveBeenCalledWith('/categories/tree', expect.any(Object));
    });
  });

  describe('useDeleteCategory', () => {
    it('calls DELETE /categories/:id', async () => {
      mockSessionStorage.getItem.mockReturnValue('test-key');
      mockedApiClient.delete.mockResolvedValue({});

      const { result } = renderHook(() => useDeleteCategory(), { wrapper });
      await result.current.mutateAsync(5);

      expect(mockedApiClient.delete).toHaveBeenCalledWith('/categories/5', expect.any(Object));
    });
  });

  describe('useUpdateCategory', () => {
    it('calls PUT /categories/:id', async () => {
      mockSessionStorage.getItem.mockReturnValue('test-key');
      mockedApiClient.put.mockResolvedValue({ data: { id: 5, name: 'Updated' } });

      const { result } = renderHook(() => useUpdateCategory(), { wrapper });
      await result.current.mutateAsync({ id: 5, data: { name: 'Updated', type: 'EXPENSE' } } as any);

      expect(mockedApiClient.put).toHaveBeenCalledWith('/categories/5', expect.any(Object), expect.any(Object));
    });
  });
});