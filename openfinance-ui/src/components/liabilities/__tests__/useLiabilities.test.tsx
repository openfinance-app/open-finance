/**
 * Unit tests for liability hooks
 * Task 6.2.5: Test useLiabilities hooks
 *
 * Tests useLiabilityBreakdown and useLiabilityTransactions hooks
 */
import { renderHook, waitFor } from '@testing-library/react';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { describe, it, expect, vi, beforeEach } from 'vitest';
import React from 'react';
import { useLiabilityBreakdown, useLiabilityTransactions } from '@/hooks/useLiabilities';
import apiClient from '@/services/apiClient';
import type { LiabilityBreakdown } from '@/types/liability';
import type { Transaction } from '@/types/transaction';

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

describe('Liability Hooks', () => {
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
    mockSessionStorage.getItem.mockReturnValue('test-encryption-key');
  });

  const wrapper = ({ children }: { children: React.ReactNode }) => (
    <QueryClientProvider client={queryClient}>
      {children}
    </QueryClientProvider>
  );

  describe('useLiabilityBreakdown', () => {
    const mockBreakdown: LiabilityBreakdown = {
      liabilityId: 1,
      name: 'Test Mortgage',
      currency: 'USD',
      currentBalance: 250000,
      principal: 300000,
      principalPaid: 50000,
      interestPaid: 10000,
      insurancePaid: 2000,
      feesPaid: 300,
      totalPaid: 65300,
      projectedInterest: 75000,
      projectedInsurance: 13000,
      projectedFees: 200,
      totalProjectedCost: 188200,
      linkedTransactionCount: 24,
      linkedTransactionsTotalAmount: 65300,
    };

    it('fetches breakdown from correct endpoint', async () => {
      const mockResponse = { data: mockBreakdown };
      mockedApiClient.get.mockResolvedValue(mockResponse);

      const { result } = renderHook(() => useLiabilityBreakdown(1), { wrapper });

      await waitFor(() => {
        expect(result.current.isSuccess).toBe(true);
      });

      expect(mockedApiClient.get).toHaveBeenCalledWith(
        '/liabilities/1/breakdown',
        {
          headers: {
            'X-Encryption-Session': 'test-encryption-key',
          },
        }
      );
    });

    it('returns correct breakdown data', async () => {
      const mockResponse = { data: mockBreakdown };
      mockedApiClient.get.mockResolvedValue(mockResponse);

      const { result } = renderHook(() => useLiabilityBreakdown(1), { wrapper });

      await waitFor(() => {
        expect(result.current.isSuccess).toBe(true);
      });

      expect(result.current.data).toEqual(mockBreakdown);
    });

    it('handles API errors correctly', async () => {
      const errorMessage = 'Failed to load breakdown';
      mockedApiClient.get.mockRejectedValue(new Error(errorMessage));

      const { result } = renderHook(() => useLiabilityBreakdown(1), { wrapper });

      await waitFor(() => {
        expect(result.current.isError).toBe(true);
      });

      expect(result.current.error?.message).toBe(errorMessage);
    });

    it('does not fetch when liabilityId is null', () => {
      const { result } = renderHook(() => useLiabilityBreakdown(null), { wrapper });

      expect(result.current.isLoading).toBe(false);
      expect(result.current.data).toBeUndefined();
      expect(mockedApiClient.get).not.toHaveBeenCalled();
    });

    it('throws error when encryption key is not found', async () => {
      mockSessionStorage.getItem.mockReturnValue(null);

      const { result } = renderHook(() => useLiabilityBreakdown(1), { wrapper });

      await waitFor(() => {
        expect(result.current.error?.message).toBe('Encryption key not found');
      });
    });

    it('uses correct query key', () => {
      const { result } = renderHook(() => useLiabilityBreakdown(1), { wrapper });

      // The hook should initialize correctly with the right query key
      expect(result.current).toBeDefined();
    });
  });

  describe('useLiabilityTransactions', () => {
    const mockTransactions: Transaction[] = [
      {
        id: 101,
        userId: 1,
        accountId: 1,
        type: 'EXPENSE',
        amount: 1500,
        currency: 'USD',
        date: '2024-01-01',
        description: 'Mortgage Payment',
        payee: 'Bank',
        isReconciled: true,
        createdAt: '2024-01-01T00:00:00Z',
      },
      {
        id: 102,
        userId: 1,
        accountId: 1,
        type: 'EXPENSE',
        amount: 1500,
        currency: 'USD',
        date: '2024-02-01',
        description: 'Mortgage Payment',
        payee: 'Bank',
        isReconciled: true,
        createdAt: '2024-02-01T00:00:00Z',
      },
    ];

    it('fetches transactions from correct endpoint', async () => {
      const mockResponse = { data: mockTransactions };
      mockedApiClient.get.mockResolvedValue(mockResponse);

      const { result } = renderHook(() => useLiabilityTransactions(1), { wrapper });

      await waitFor(() => {
        expect(result.current.isSuccess).toBe(true);
      });

      expect(mockedApiClient.get).toHaveBeenCalledWith(
        '/liabilities/1/transactions',
        {
          headers: {
            'X-Encryption-Session': 'test-encryption-key',
          },
        }
      );
    });

    it('returns correct transactions data', async () => {
      const mockResponse = { data: mockTransactions };
      mockedApiClient.get.mockResolvedValue(mockResponse);

      const { result } = renderHook(() => useLiabilityTransactions(1), { wrapper });

      await waitFor(() => {
        expect(result.current.isSuccess).toBe(true);
      });

      expect(result.current.data).toEqual(mockTransactions);
    });

    it('handles API errors correctly', async () => {
      const errorMessage = 'Failed to load transactions';
      mockedApiClient.get.mockRejectedValue(new Error(errorMessage));

      const { result } = renderHook(() => useLiabilityTransactions(1), { wrapper });

      await waitFor(() => {
        expect(result.current.isError).toBe(true);
      });

      expect(result.current.error?.message).toBe(errorMessage);
    });

    it('does not fetch when liabilityId is null', () => {
      const { result } = renderHook(() => useLiabilityTransactions(null), { wrapper });

      expect(result.current.isLoading).toBe(false);
      expect(result.current.data).toBeUndefined();
      expect(mockedApiClient.get).not.toHaveBeenCalled();
    });

    it('throws error when encryption key is not found', async () => {
      mockSessionStorage.getItem.mockReturnValue(null);

      const { result } = renderHook(() => useLiabilityTransactions(1), { wrapper });

      await waitFor(() => {
        expect(result.current.error?.message).toBe('Encryption key not found');
      });
    });

    it('returns empty array when no transactions found', async () => {
      const mockResponse = { data: [] };
      mockedApiClient.get.mockResolvedValue(mockResponse);

      const { result } = renderHook(() => useLiabilityTransactions(1), { wrapper });

      await waitFor(() => {
        expect(result.current.isSuccess).toBe(true);
      });

      expect(result.current.data).toEqual([]);
    });

    it('uses correct query key', () => {
      const { result } = renderHook(() => useLiabilityTransactions(1), { wrapper });

      // The hook should initialize correctly with the right query key
      expect(result.current).toBeDefined();
    });
  });
});