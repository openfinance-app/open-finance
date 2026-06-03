import { renderHook, waitFor } from '@testing-library/react';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { describe, it, expect, vi, beforeEach } from 'vitest';
import React from 'react';
import {
  useAccounts,
  useAccountsSearch,
  useAccount,
  useAccountBalanceHistory,
  useCreateAccount,
  useUpdateAccount,
  useDeleteAccount,
  useCloseAccount,
  useReopenAccount,
  usePermanentDeleteAccount,
  useInterestRateVariations,
  useCreateVariation,
  useDeleteVariation,
  useInterestEstimate,
} from './useAccounts';
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

describe('useAccounts hooks', () => {
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

  const mockAccount = {
    id: 1,
    name: 'Checking Account',
    type: 'CHECKING',
    currency: 'USD',
    balance: 1500.0,
    isActive: true,
  };

  // ── useAccounts ──────────────────────────────────────────────────────────
  describe('useAccounts', () => {
    it('should fetch accounts with default active filter', async () => {
      mockedApiClient.get.mockResolvedValue({ data: [mockAccount] });

      const { result } = renderHook(() => useAccounts(), { wrapper });

      await waitFor(() => expect(result.current.isSuccess).toBe(true));

      expect(mockedApiClient.get).toHaveBeenCalledWith('/accounts', {
        params: { filter: 'active' },
        headers: { 'X-Encryption-Session': 'test-encryption-key' },
      });
      expect(result.current.data).toEqual([mockAccount]);
    });

    it('should fetch accounts with custom filter', async () => {
      mockedApiClient.get.mockResolvedValue({ data: [mockAccount] });

      const { result } = renderHook(() => useAccounts('all'), { wrapper });

      await waitFor(() => expect(result.current.isSuccess).toBe(true));

      expect(mockedApiClient.get).toHaveBeenCalledWith('/accounts', {
        params: { filter: 'all' },
        headers: { 'X-Encryption-Session': 'test-encryption-key' },
      });
    });

    it('should throw error when encryption key is missing', async () => {
      mockSessionStorage.getItem.mockReturnValue(null);

      const { result } = renderHook(() => useAccounts(), { wrapper });

      await waitFor(() => expect(result.current.isError).toBe(true));
      expect(result.current.error?.message).toBe('Encryption key not found');
    });
  });

  // ── useAccountsSearch ─────────────────────────────────────────────────────
  describe('useAccountsSearch', () => {
    const mockPaginatedResponse = {
      content: [mockAccount],
      totalElements: 1,
      totalPages: 1,
      number: 0,
      size: 20,
    };

    it('should fetch accounts with search filters', async () => {
      mockedApiClient.get.mockResolvedValue({ data: mockPaginatedResponse });

      const filters = { keyword: 'check', type: 'CHECKING', page: 0, size: 20 };
      const { result } = renderHook(() => useAccountsSearch(filters), { wrapper });

      await waitFor(() => expect(result.current.isSuccess).toBe(true));
      expect(result.current.data).toEqual(mockPaginatedResponse);
    });

    it('should include default sort when none specified', async () => {
      mockedApiClient.get.mockResolvedValue({ data: mockPaginatedResponse });

      const { result } = renderHook(() => useAccountsSearch({}), { wrapper });

      await waitFor(() => expect(result.current.isSuccess).toBe(true));

      const calledUrl = mockedApiClient.get.mock.calls[0][0] as string;
      expect(calledUrl).toContain('sort=name%2Casc');
    });
  });

  // ── useAccount ────────────────────────────────────────────────────────────
  describe('useAccount', () => {
    it('should fetch a single account by ID', async () => {
      mockedApiClient.get.mockResolvedValue({ data: mockAccount });

      const { result } = renderHook(() => useAccount(1), { wrapper });

      await waitFor(() => expect(result.current.isSuccess).toBe(true));

      expect(mockedApiClient.get).toHaveBeenCalledWith('/accounts/1', {
        headers: { 'X-Encryption-Session': 'test-encryption-key' },
      });
      expect(result.current.data).toEqual(mockAccount);
    });

    it('should be disabled when accountId is null', () => {
      const { result } = renderHook(() => useAccount(null), { wrapper });
      expect(result.current.isFetched).toBe(false);
    });
  });

  // ── useAccountBalanceHistory ──────────────────────────────────────────────
  describe('useAccountBalanceHistory', () => {
    const mockHistory = [
      { date: '2026-01-01', balance: 1000 },
      { date: '2026-02-01', balance: 1500 },
    ];

    it('should fetch balance history with default period', async () => {
      mockedApiClient.get.mockResolvedValue({ data: mockHistory });

      const { result } = renderHook(() => useAccountBalanceHistory(1), { wrapper });

      await waitFor(() => expect(result.current.isSuccess).toBe(true));

      expect(mockedApiClient.get).toHaveBeenCalledWith('/accounts/1/balance-history', {
        params: { period: '3M' },
        headers: { 'X-Encryption-Session': 'test-encryption-key' },
      });
    });

    it('should be disabled when accountId is null', () => {
      const { result } = renderHook(() => useAccountBalanceHistory(null), { wrapper });
      expect(result.current.isFetched).toBe(false);
    });
  });

  // ── useCreateAccount ──────────────────────────────────────────────────────
  describe('useCreateAccount', () => {
    it('should create an account and invalidate queries', async () => {
      mockedApiClient.post.mockResolvedValue({ data: mockAccount });
      const invalidateSpy = vi.spyOn(queryClient, 'invalidateQueries');

      const { result } = renderHook(() => useCreateAccount(), { wrapper });

      result.current.mutate({
        name: 'Checking Account',
        type: 'CHECKING',
        currency: 'USD',
        balance: 1500,
      } as any);

      await waitFor(() => expect(result.current.isSuccess).toBe(true));

      expect(mockedApiClient.post).toHaveBeenCalledWith(
        '/accounts',
        expect.any(Object),
        { headers: { 'X-Encryption-Session': 'test-encryption-key' } }
      );
      expect(invalidateSpy).toHaveBeenCalledWith({ queryKey: ['accounts'] });
      expect(invalidateSpy).toHaveBeenCalledWith({ queryKey: ['dashboard'] });
    });
  });

  // ── useUpdateAccount ──────────────────────────────────────────────────────
  describe('useUpdateAccount', () => {
    it('should update an account and invalidate queries', async () => {
      const updatedAccount = { ...mockAccount, name: 'Updated Account' };
      mockedApiClient.put.mockResolvedValue({ data: updatedAccount });
      const invalidateSpy = vi.spyOn(queryClient, 'invalidateQueries');

      const { result } = renderHook(() => useUpdateAccount(), { wrapper });

      result.current.mutate({ id: 1, data: { name: 'Updated Account' } as any });

      await waitFor(() => expect(result.current.isSuccess).toBe(true));

      expect(mockedApiClient.put).toHaveBeenCalledWith(
        '/accounts/1',
        expect.any(Object),
        { headers: { 'X-Encryption-Session': 'test-encryption-key' } }
      );
      expect(invalidateSpy).toHaveBeenCalledWith({ queryKey: ['accounts', 1] });
    });
  });

  // ── useDeleteAccount ──────────────────────────────────────────────────────
  describe('useDeleteAccount', () => {
    it('should delete an account and invalidate queries', async () => {
      mockedApiClient.delete.mockResolvedValue({});
      const invalidateSpy = vi.spyOn(queryClient, 'invalidateQueries');

      const { result } = renderHook(() => useDeleteAccount(), { wrapper });

      result.current.mutate(1);

      await waitFor(() => expect(result.current.isSuccess).toBe(true));

      expect(mockedApiClient.delete).toHaveBeenCalledWith('/accounts/1', {
        headers: { 'X-Encryption-Session': 'test-encryption-key' },
      });
      expect(invalidateSpy).toHaveBeenCalledWith({ queryKey: ['accounts'] });
    });
  });

  // ── useCloseAccount ───────────────────────────────────────────────────────
  describe('useCloseAccount', () => {
    it('should close an account', async () => {
      mockedApiClient.post.mockResolvedValue({});
      const invalidateSpy = vi.spyOn(queryClient, 'invalidateQueries');

      const { result } = renderHook(() => useCloseAccount(), { wrapper });

      result.current.mutate(1);

      await waitFor(() => expect(result.current.isSuccess).toBe(true));

      expect(mockedApiClient.post).toHaveBeenCalledWith('/accounts/1/close');
      expect(invalidateSpy).toHaveBeenCalledWith({ queryKey: ['accounts'] });
    });
  });

  // ── useReopenAccount ──────────────────────────────────────────────────────
  describe('useReopenAccount', () => {
    it('should reopen a closed account', async () => {
      mockedApiClient.post.mockResolvedValue({});

      const { result } = renderHook(() => useReopenAccount(), { wrapper });

      result.current.mutate(1);

      await waitFor(() => expect(result.current.isSuccess).toBe(true));

      expect(mockedApiClient.post).toHaveBeenCalledWith('/accounts/1/reopen');
    });
  });

  // ── usePermanentDeleteAccount ─────────────────────────────────────────────
  describe('usePermanentDeleteAccount', () => {
    it('should permanently delete an account', async () => {
      mockedApiClient.delete.mockResolvedValue({});

      const { result } = renderHook(() => usePermanentDeleteAccount(), { wrapper });

      result.current.mutate(1);

      await waitFor(() => expect(result.current.isSuccess).toBe(true));

      expect(mockedApiClient.delete).toHaveBeenCalledWith('/accounts/1/permanent', {
        headers: { 'X-Encryption-Session': 'test-encryption-key' },
      });
    });

    it('should throw error when encryption key is missing', async () => {
      mockSessionStorage.getItem.mockReturnValue(null);

      const { result } = renderHook(() => usePermanentDeleteAccount(), { wrapper });

      result.current.mutate(1);

      await waitFor(() => expect(result.current.isError).toBe(true));
      expect(result.current.error?.message).toBe('Encryption key not found');
    });
  });

  // ── useInterestRateVariations ─────────────────────────────────────────────
  describe('useInterestRateVariations', () => {
    const mockVariations = [
      { id: 1, accountId: 1, effectiveDate: '2026-01-01', rate: 3.5 },
    ];

    it('should fetch interest rate variations', async () => {
      mockedApiClient.get.mockResolvedValue({ data: mockVariations });

      const { result } = renderHook(() => useInterestRateVariations(1), { wrapper });

      await waitFor(() => expect(result.current.isSuccess).toBe(true));

      expect(mockedApiClient.get).toHaveBeenCalledWith('/accounts/1/interest-variations', {
        headers: { 'X-Encryption-Session': 'test-encryption-key' },
      });
    });

    it('should be disabled when accountId is null', () => {
      const { result } = renderHook(() => useInterestRateVariations(null), { wrapper });
      expect(result.current.isFetched).toBe(false);
    });
  });

  // ── useCreateVariation ────────────────────────────────────────────────────
  describe('useCreateVariation', () => {
    it('should create an interest rate variation', async () => {
      const mockVariation = { id: 1, accountId: 1, effectiveDate: '2026-01-01', rate: 3.5 };
      mockedApiClient.post.mockResolvedValue({ data: mockVariation });
      const invalidateSpy = vi.spyOn(queryClient, 'invalidateQueries');

      const { result } = renderHook(() => useCreateVariation(), { wrapper });

      result.current.mutate({
        accountId: 1,
        data: { effectiveDate: '2026-01-01', rate: 3.5 } as any,
      });

      await waitFor(() => expect(result.current.isSuccess).toBe(true));

      expect(invalidateSpy).toHaveBeenCalledWith({ queryKey: ['accounts', 1, 'interest-variations'] });
      expect(invalidateSpy).toHaveBeenCalledWith({ queryKey: ['accounts', 1, 'interest-estimate'] });
    });
  });

  // ── useDeleteVariation ────────────────────────────────────────────────────
  describe('useDeleteVariation', () => {
    it('should delete an interest rate variation', async () => {
      mockedApiClient.delete.mockResolvedValue({});
      const invalidateSpy = vi.spyOn(queryClient, 'invalidateQueries');

      const { result } = renderHook(() => useDeleteVariation(), { wrapper });

      result.current.mutate({ accountId: 1, variationId: 5 });

      await waitFor(() => expect(result.current.isSuccess).toBe(true));

      expect(mockedApiClient.delete).toHaveBeenCalledWith('/accounts/1/interest-variations/5', {
        headers: { 'X-Encryption-Session': 'test-encryption-key' },
      });
      expect(invalidateSpy).toHaveBeenCalledWith({ queryKey: ['accounts', 1, 'interest-variations'] });
    });
  });

  // ── useInterestEstimate ───────────────────────────────────────────────────
  describe('useInterestEstimate', () => {
    it('should fetch interest estimate with default period', async () => {
      const mockEstimate = { estimate: 52.5, historicalAccumulated: 120.0 };
      mockedApiClient.get.mockResolvedValue({ data: mockEstimate });

      const { result } = renderHook(() => useInterestEstimate(1), { wrapper });

      await waitFor(() => expect(result.current.isSuccess).toBe(true));

      expect(mockedApiClient.get).toHaveBeenCalledWith('/accounts/1/interest-estimate', {
        params: { period: '1Y' },
        headers: { 'X-Encryption-Session': 'test-encryption-key' },
      });
      expect(result.current.data).toEqual(mockEstimate);
    });

    it('should be disabled when accountId is null', () => {
      const { result } = renderHook(() => useInterestEstimate(null), { wrapper });
      expect(result.current.isFetched).toBe(false);
    });
  });
});
