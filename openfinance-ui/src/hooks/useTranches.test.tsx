/**
 * Tests for the useTranches hook (Task 8).
 * Mirrors the apiClient-mocking patterns of useTransactions.test.tsx.
 */
import { renderHook, waitFor, act } from '@testing-library/react';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { describe, it, expect, vi, beforeEach } from 'vitest';
import React from 'react';
import {
  getTrancheLabel,
  useCreateTranche,
  useReverseDirectDraw,
  useTranches,
} from './useTranches';
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

const mockTranches = [
  {
    id: 1,
    liabilityId: 5,
    trancheNo: 1,
    plannedAmount: 100000,
    drawnAmount: 100000,
    remaining: 40000,
    status: 'DRAWN',
    currency: 'USD',
  },
  {
    id: 2,
    liabilityId: 5,
    trancheNo: 2,
    plannedAmount: 50000,
    drawnAmount: null,
    remaining: 50000,
    status: 'PLANNED',
    currency: 'USD',
  },
];

describe('useTranches', () => {
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
    <QueryClientProvider client={queryClient}>{children}</QueryClientProvider>
  );

  it('fetches tranches from the liability tranches endpoint', async () => {
    mockedApiClient.get.mockResolvedValue({ data: mockTranches });

    const { result } = renderHook(() => useTranches(5), { wrapper });

    await waitFor(() => {
      expect(result.current.isSuccess).toBe(true);
    });

    expect(mockedApiClient.get).toHaveBeenCalledWith('/liabilities/5/tranches', {
      headers: {
        'X-Encryption-Session': 'test-encryption-key',
      },
    });
    expect(result.current.data).toEqual(mockTranches);
  });

  it('does not fetch when the liability id is null', async () => {
    const { result } = renderHook(() => useTranches(null), { wrapper });

    expect(result.current.isLoading).toBe(false);
    expect(mockedApiClient.get).not.toHaveBeenCalled();
  });

  it('exposes the error when the request fails', async () => {
    mockedApiClient.get.mockRejectedValue(new Error('boom'));

    const { result } = renderHook(() => useTranches(5), { wrapper });

    await waitFor(() => {
      expect(result.current.isError).toBe(true);
    });
    expect(result.current.error).toBeInstanceOf(Error);
  });

  it('surfaces a required-id error instead of fetching tranches for id 0', async () => {
    const { result } = renderHook(() => useTranches(0), { wrapper });

    await waitFor(() => {
      expect(result.current.isError).toBe(true);
    });
    expect(result.current.error).toEqual(new Error('Liability ID is required'));
    expect(mockedApiClient.get).not.toHaveBeenCalled();
  });
});

describe('useCreateTranche', () => {
  let queryClient: QueryClient;

  beforeEach(() => {
    queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } });
    vi.clearAllMocks();
    mockSessionStorage.getItem.mockReturnValue('test-encryption-key');
  });

  const wrapper = ({ children }: { children: React.ReactNode }) => (
    <QueryClientProvider client={queryClient}>{children}</QueryClientProvider>
  );

  it('posts the tranche request and refreshes tranche and liability queries', async () => {
    const invalidate = vi.spyOn(queryClient, 'invalidateQueries').mockResolvedValue(undefined);
    mockedApiClient.post.mockResolvedValue({ data: mockTranches[1] });

    const { result } = renderHook(() => useCreateTranche(), { wrapper });
    let created: unknown;
    await act(async () => {
      created = await result.current.mutateAsync({
        liabilityId: 5,
        request: { plannedAmount: '50000', interestOnly: true, interestOnlyUntil: '2027-01-01' },
      });
    });

    expect(created).toEqual(mockTranches[1]);
    expect(mockedApiClient.post).toHaveBeenCalledWith(
      '/liabilities/5/tranches',
      { plannedAmount: '50000', interestOnly: true, interestOnlyUntil: '2027-01-01' },
      { headers: { 'X-Encryption-Session': 'test-encryption-key' } }
    );
    expect(invalidate).toHaveBeenCalledWith({ queryKey: ['liabilities', 5, 'tranches'] });
    expect(invalidate).toHaveBeenCalledWith({ queryKey: ['liabilities'] });
  });

  it('does not invalidate when creation fails', async () => {
    const invalidate = vi.spyOn(queryClient, 'invalidateQueries').mockResolvedValue(undefined);
    mockedApiClient.post.mockRejectedValue(new Error('invalid'));

    const { result } = renderHook(() => useCreateTranche(), { wrapper });
    await act(async () => {
      await expect(
        result.current.mutateAsync({ liabilityId: 5, request: { plannedAmount: 1 } })
      ).rejects.toThrow('invalid');
    });
    expect(invalidate).not.toHaveBeenCalled();
  });
});

describe('useReverseDirectDraw', () => {
  let queryClient: QueryClient;

  beforeEach(() => {
    queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } });
    vi.clearAllMocks();
    mockSessionStorage.getItem.mockReturnValue('test-encryption-key');
  });

  const wrapper = ({ children }: { children: React.ReactNode }) => (
    <QueryClientProvider client={queryClient}>{children}</QueryClientProvider>
  );

  it('posts the reversal date and refreshes asset/liability surfaces', async () => {
    const invalidate = vi.spyOn(queryClient, 'invalidateQueries').mockResolvedValue(undefined);
    mockedApiClient.post.mockResolvedValue({ data: {} });

    const { result } = renderHook(() => useReverseDirectDraw(), { wrapper });
    await act(async () => {
      await result.current.mutateAsync({ trancheId: 9, date: '2026-02-01' });
    });

    expect(mockedApiClient.post).toHaveBeenCalledWith(
      '/tranches/9/reverse',
      { date: '2026-02-01' },
      { headers: { 'X-Encryption-Session': 'test-encryption-key' } }
    );
    const keys = invalidate.mock.calls.map(([arg]) =>
      JSON.stringify((arg as { queryKey: unknown[] }).queryKey)
    );
    expect(keys).toEqual([
      '["liabilities"]',
      '["realEstate"]',
      '["assets"]',
      '["dashboard"]',
      '["networth"]',
      '["assetFinancing"]',
    ]);
  });
});

describe('getTrancheLabel', () => {
  it('formats tranche numbers as T<n>', () => {
    expect(getTrancheLabel(1)).toBe('T1');
    expect(getTrancheLabel(12)).toBe('T12');
    expect(getTrancheLabel(0)).toBe('T0');
  });

  it('uses an em dash for missing tranche numbers', () => {
    expect(getTrancheLabel(null)).toBe('—');
    expect(getTrancheLabel(undefined)).toBe('—');
  });
});
