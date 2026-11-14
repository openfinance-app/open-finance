import { renderHook, act } from '@testing-library/react';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { describe, it, expect, vi, beforeEach } from 'vitest';
import React from 'react';
import { useOwnershipOperation } from './useOwnershipOperation';
import apiClient from '@/services/apiClient';

vi.mock('@/services/apiClient');
const mockedApiClient = apiClient as unknown as { post: ReturnType<typeof vi.fn> };

describe('useOwnershipOperation', () => {
  let queryClient: QueryClient;
  const wrapper = ({ children }: { children: React.ReactNode }) => (
    <QueryClientProvider client={queryClient}>{children}</QueryClientProvider>
  );

  beforeEach(() => {
    queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } });
    vi.clearAllMocks();
    sessionStorage.setItem('encryption_session', 'test-key');
  });

  it('posts to the caller-supplied path with encryption headers and returns data', async () => {
    mockedApiClient.post.mockResolvedValue({ data: { ok: true } });
    const { result } = renderHook(() => useOwnershipOperation<{ ok: boolean }>(), { wrapper });

    let data: unknown;
    await act(async () => {
      data = await result.current.mutateAsync({ path: '/assets/1/buy', data: { qty: 2 } });
    });

    expect(data).toEqual({ ok: true });
    expect(mockedApiClient.post).toHaveBeenCalledWith(
      '/assets/1/buy',
      { qty: 2 },
      {
        headers: { 'X-Encryption-Session': 'test-key' },
      }
    );
  });

  it('invalidates every ownership-affected query root once on success', async () => {
    const invalidate = vi.spyOn(queryClient, 'invalidateQueries').mockResolvedValue(undefined);
    mockedApiClient.post.mockResolvedValue({ data: undefined });
    const { result } = renderHook(() => useOwnershipOperation(), { wrapper });

    await act(async () => {
      await result.current.mutateAsync({ path: '/x', data: null });
    });

    const keys = invalidate.mock.calls.map(([arg]) =>
      JSON.stringify((arg as { queryKey: unknown[] }).queryKey)
    );
    expect(keys).toEqual([
      '["assets"]',
      '["realEstate"]',
      '["accounts"]',
      '["transactions"]',
      '["liabilities"]',
      '["assetFinancing"]',
      '["networth"]',
      '["dashboard"]',
      '["history"]',
      '["session-history-exists"]',
    ]);
  });

  it('propagates failures without invalidating', async () => {
    const invalidate = vi.spyOn(queryClient, 'invalidateQueries').mockResolvedValue(undefined);
    mockedApiClient.post.mockRejectedValue(new Error('denied'));
    const { result } = renderHook(() => useOwnershipOperation(), { wrapper });

    await act(async () => {
      await expect(result.current.mutateAsync({ path: '/x', data: null })).rejects.toThrow(
        'denied'
      );
    });
    expect(invalidate).not.toHaveBeenCalled();
  });
});
