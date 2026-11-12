import type { ReactNode } from 'react';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { renderHook, waitFor } from '@testing-library/react';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { useAccountsSearch } from '@/hooks/useAccounts';
import { useAssetsSearch } from '@/hooks/useAssets';
import { useLiabilitiesPaged } from '@/hooks/useLiabilities';
import { usePropertiesSearch } from '@/hooks/useRealEstate';
import { useTransactions, useTransactionSummary } from '@/hooks/useTransactions';
import apiClient from '@/services/apiClient';
import { mockAuthentication } from '@/test/test-utils';

vi.mock('@/services/apiClient');
vi.mock('@/hooks/useSecurityConfig', () => ({
  useSecurityConfig: () => ({ data: { encryptionEnabled: false }, isError: false }),
  resolveEncryptionEnabled: () => false,
}));
vi.mock('@/context/AuthContext', async importOriginal => ({
  ...(await importOriginal<typeof import('@/context/AuthContext')>()),
  useAuthContext: () => ({ baseCurrency: 'EUR', user: { id: 1 } }),
}));

describe('Amount filter requests across financial surfaces', () => {
  let client: QueryClient;
  beforeEach(() => {
    vi.clearAllMocks();
    mockAuthentication();
    client = new QueryClient({ defaultOptions: { queries: { retry: false } } });
    vi.mocked(apiClient.get).mockResolvedValue({
      data: { content: [], totalPages: 0, totalElements: 0 },
    });
  });
  const wrapper = ({ children }: { children: ReactNode }) => (
    <QueryClientProvider client={client}>{children}</QueryClientProvider>
  );

  it.each([
    ['accounts/search', 'balanceExact', useAccountsSearch],
    ['assets/search', 'valueExact', useAssetsSearch],
    ['liabilities/paged', 'balanceExact', useLiabilitiesPaged],
    ['real-estate/search', 'valueExact', usePropertiesSearch],
    ['transactions/search', 'amountExact', useTransactions],
    ['transactions/summary', 'amountExact', useTransactionSummary],
  ] as const)(
    'sends exact zero and separates base/native query state for %s',
    async (path, parameter, useSurface) => {
      const { result, rerender } = renderHook(
        ({ mode }: { mode: 'BASE' | 'NATIVE' }) =>
          useSurface({
            balanceExact: 0,
            valueExact: 0,
            exactAmount: 0,
            amountCurrencyMode: mode,
          }),
        { wrapper, initialProps: { mode: 'NATIVE' } }
      );
      await waitFor(() => expect(result.current.isSuccess).toBe(true));
      let request = new URL(vi.mocked(apiClient.get).mock.calls.at(-1)![0], 'http://localhost');
      expect(request.pathname).toBe(`/${path}`);
      expect(request.searchParams.get(parameter)).toBe('0');
      expect(request.searchParams.get('amountCurrencyMode')).toBe('NATIVE');
      rerender({ mode: 'BASE' });
      await waitFor(() => expect(vi.mocked(apiClient.get)).toHaveBeenCalledTimes(2));
      request = new URL(vi.mocked(apiClient.get).mock.calls.at(-1)![0], 'http://localhost');
      expect(request.searchParams.get('amountCurrencyMode')).toBe('BASE');
    }
  );

  it.each([
    ['assets', useAssetsSearch],
    ['real-estate', usePropertiesSearch],
  ] as const)('includes every matching page in %s summaries', async (_surface, useSurface) => {
    vi.mocked(apiClient.get)
      .mockResolvedValueOnce({ data: { content: [{ id: 1 }], totalPages: 2, totalElements: 2 } })
      .mockResolvedValueOnce({ data: { content: [{ id: 2 }], totalPages: 2, totalElements: 2 } });
    const { result } = renderHook(
      () => useSurface({ page: 3, size: 1, valueExact: 500, amountCurrencyMode: 'NATIVE' }, true),
      { wrapper }
    );
    await waitFor(() => expect(result.current.isSuccess).toBe(true));
    expect(result.current.data?.content.map(row => row.id)).toEqual([1, 2]);
    const requests = vi
      .mocked(apiClient.get)
      .mock.calls.map(([path]) => new URL(path, 'http://localhost'));
    expect(requests.map(url => url.searchParams.get('page'))).toEqual(['0', '1']);
    for (const url of requests) {
      expect(url.searchParams.get('valueExact')).toBe('500');
      expect(url.searchParams.get('amountCurrencyMode')).toBe('NATIVE');
    }
  });

  it('keeps the same amount criteria while fetching every page for liability summaries', async () => {
    vi.mocked(apiClient.get)
      .mockResolvedValueOnce({ data: { content: [{ id: 1 }], totalPages: 2, totalElements: 2 } })
      .mockResolvedValueOnce({ data: { content: [{ id: 2 }], totalPages: 2, totalElements: 2 } });
    const { result } = renderHook(
      () =>
        useLiabilitiesPaged(
          { page: 8, size: 1, balanceMin: 2500, amountCurrencyMode: 'NATIVE' },
          true
        ),
      { wrapper }
    );
    await waitFor(() => expect(result.current.isSuccess).toBe(true));
    expect(result.current.data?.content.map(row => row.id)).toEqual([1, 2]);
    const requests = vi
      .mocked(apiClient.get)
      .mock.calls.map(([path]) => new URL(path, 'http://localhost'));
    expect(requests.map(url => url.searchParams.get('page'))).toEqual(['0', '1']);
    for (const url of requests) {
      expect(url.searchParams.get('balanceMin')).toBe('2500');
      expect(url.searchParams.get('amountCurrencyMode')).toBe('NATIVE');
    }
  });
});
