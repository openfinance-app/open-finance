import { renderHook, waitFor } from '@testing-library/react';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { describe, it, expect, vi, beforeEach } from 'vitest';
import React from 'react';
import {
  useLiabilities,
  useLiabilitiesPaged,
  useCreateLiability,
  useUpdateLiability,
  useDeleteLiability,
  useLiabilityTotals,
  useLiabilityTransactions,
} from './useLiabilities';
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
Object.defineProperty(window, 'sessionStorage', { value: mockSessionStorage });

describe('useLiabilities hooks', () => {
  let queryClient: QueryClient;

  beforeEach(() => {
    queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } });
    vi.clearAllMocks();
  });

  const wrapper = ({ children }: { children: React.ReactNode }) => (
    <QueryClientProvider client={queryClient}>{children}</QueryClientProvider>
  );

  describe('useLiabilities', () => {
    it('fetches liabilities list', async () => {
      mockSessionStorage.getItem.mockReturnValue('test-key');
      mockedApiClient.get.mockResolvedValue({ data: [{ id: 1, name: 'Mortgage' }] });

      const { result } = renderHook(() => useLiabilities(), { wrapper });
      await waitFor(() => expect(result.current.data).toBeDefined());
      expect(mockedApiClient.get).toHaveBeenCalledWith('/liabilities', expect.any(Object));
    });

    it('errors when encryption key missing', async () => {
      mockSessionStorage.getItem.mockReturnValue(null);
      const { result } = renderHook(() => useLiabilities(), { wrapper });
      await waitFor(() => expect(result.current.error).toBeTruthy());
    });

    it('fetches with type filter', async () => {
      mockSessionStorage.getItem.mockReturnValue('test-key');
      mockedApiClient.get.mockResolvedValue({ data: [] });

      const { result } = renderHook(() => useLiabilities({ type: 'MORTGAGE' }), { wrapper });
      await waitFor(() => !result.current.isLoading);
      expect(mockedApiClient.get).toHaveBeenCalledWith('/liabilities?type=MORTGAGE', expect.any(Object));
    });
  });

  describe('useLiabilitiesPaged', () => {
    it('fetches paged liabilities', async () => {
      mockSessionStorage.getItem.mockReturnValue('test-key');
      mockedApiClient.get.mockResolvedValue({ data: { content: [], totalElements: 0, totalPages: 0, number: 0, size: 20 } });

      const { result } = renderHook(() => useLiabilitiesPaged(), { wrapper });
      await waitFor(() => expect(result.current.data).toBeDefined());
      expect(mockedApiClient.get).toHaveBeenCalledWith(expect.stringContaining('/liabilities/paged'), expect.any(Object));
    });

    it('includes search and type params', async () => {
      mockSessionStorage.getItem.mockReturnValue('test-key');
      mockedApiClient.get.mockResolvedValue({ data: { content: [], totalElements: 0, totalPages: 0, number: 0, size: 20 } });

      const { result } = renderHook(() => useLiabilitiesPaged({ type: 'LOAN', search: 'car' }), { wrapper });
      await waitFor(() => expect(result.current.data).toBeDefined());
      const url = mockedApiClient.get.mock.calls[0][0];
      expect(url).toContain('type=LOAN');
      expect(url).toContain('search=car');
    });
  });

  describe('useCreateLiability', () => {
    it('calls POST /liabilities', async () => {
      mockSessionStorage.getItem.mockReturnValue('test-key');
      mockedApiClient.post.mockResolvedValue({ data: { id: 1, name: 'New Loan' } });

      const { result } = renderHook(() => useCreateLiability(), { wrapper });
      await result.current.mutateAsync({ name: 'New Loan', type: 'LOAN', currentBalance: 10000, currency: 'USD' } as any);

      expect(mockedApiClient.post).toHaveBeenCalledWith('/liabilities', expect.any(Object), expect.objectContaining({ headers: { 'X-Encryption-Key': 'test-key' } }));
    });

    it('throws when encryption key missing', async () => {
      mockSessionStorage.getItem.mockReturnValue(null);
      const { result } = renderHook(() => useCreateLiability(), { wrapper });
      await expect(result.current.mutateAsync({ name: 'x' } as any)).rejects.toThrow('Encryption key not found');
    });
  });

  describe('useUpdateLiability', () => {
    it('calls PUT /liabilities/:id', async () => {
      mockSessionStorage.getItem.mockReturnValue('test-key');
      mockedApiClient.put.mockResolvedValue({ data: { id: 1 } });

      const { result } = renderHook(() => useUpdateLiability(), { wrapper });
      await result.current.mutateAsync({ id: 1, data: { name: 'Updated' } as any });

      expect(mockedApiClient.put).toHaveBeenCalledWith('/liabilities/1', expect.any(Object), expect.any(Object));
    });
  });

  describe('useDeleteLiability', () => {
    it('calls DELETE /liabilities/:id', async () => {
      mockSessionStorage.getItem.mockReturnValue('test-key');
      mockedApiClient.delete.mockResolvedValue({});

      const { result } = renderHook(() => useDeleteLiability(), { wrapper });
      await result.current.mutateAsync(5);

      expect(mockedApiClient.delete).toHaveBeenCalledWith('/liabilities/5', expect.any(Object));
    });

    it('throws when encryption key missing', async () => {
      mockSessionStorage.getItem.mockReturnValue(null);
      const { result } = renderHook(() => useDeleteLiability(), { wrapper });
      await expect(result.current.mutateAsync(5)).rejects.toThrow('Encryption key not found');
    });
  });

  describe('useLiabilityTotals', () => {
    it('fetches liability totals', async () => {
      mockSessionStorage.getItem.mockReturnValue('test-key');
      mockedApiClient.get.mockResolvedValue({ data: { totalBalance: 50000, totalMinPayment: 1000 } });

      const { result } = renderHook(() => useLiabilityTotals(), { wrapper });
      await waitFor(() => expect(result.current.data).toBeDefined());
      expect(mockedApiClient.get).toHaveBeenCalledWith('/liabilities/totals', expect.any(Object));
    });
  });

  describe('useLiabilityTransactions', () => {
    it('fetches transactions for a liability', async () => {
      mockSessionStorage.getItem.mockReturnValue('test-key');
      mockedApiClient.get.mockResolvedValue({ data: [{ id: 1, amount: 500 }] });

      const { result } = renderHook(() => useLiabilityTransactions(10), { wrapper });
      await waitFor(() => expect(result.current.data).toBeDefined());
      expect(mockedApiClient.get).toHaveBeenCalledWith('/liabilities/10/transactions', expect.any(Object));
    });

    it('does not fetch when id is null', () => {
      const { result } = renderHook(() => useLiabilityTransactions(null), { wrapper });
      expect(result.current.data).toBeUndefined();
    });
  });
});
