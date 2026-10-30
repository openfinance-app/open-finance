import { act, renderHook, waitFor } from '@testing-library/react';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { describe, it, expect, vi, beforeEach } from 'vitest';
import React from 'react';
import {
  useAssets,
  useAssetsSearch,
  useAsset,
  useCreateAsset,
  useUpdateAsset,
  useDeleteAsset,
  formatGainLoss,
  getAssetTypeName,
  getAssetTypeBadgeVariant,
  getConditionBadgeVariant,
} from './useAssets';
import '@/test/i18n-test';
import apiClient from '@/services/apiClient';
import { useAccount, useAccountsSearch } from '@/hooks/useAccounts';

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

describe('useAssets hooks', () => {
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

  const mockAsset = {
    id: 1,
    name: 'Apple Stock',
    type: 'STOCK',
    symbol: 'AAPL',
    currency: 'USD',
    currentPrice: 175.5,
    purchasePrice: 150.0,
  };

  // ── useAssets ─────────────────────────────────────────────────────────────
  describe('useAssets', () => {
    it('should fetch assets successfully', async () => {
      mockedApiClient.get.mockResolvedValue({ data: [mockAsset] });

      const { result } = renderHook(() => useAssets(), { wrapper });

      await waitFor(() => expect(result.current.isSuccess).toBe(true));
      expect(result.current.data).toEqual([mockAsset]);
    });

    it('should fetch assets with filters', async () => {
      mockedApiClient.get.mockResolvedValue({ data: [mockAsset] });

      const { result } = renderHook(() => useAssets({ type: 'STOCK', accountId: 1 }), { wrapper });

      await waitFor(() => expect(result.current.isSuccess).toBe(true));

      const calledUrl = mockedApiClient.get.mock.calls[0][0] as string;
      expect(calledUrl).toContain('accountId=1');
      expect(calledUrl).toContain('type=STOCK');
    });

    it('should throw error when encryption key is missing', async () => {
      mockSessionStorage.getItem.mockReturnValue(null);

      const { result } = renderHook(() => useAssets(), { wrapper });

      await waitFor(() => expect(result.current.isError).toBe(true));
      expect(result.current.error?.message).toBe('Encryption key not found');
    });
  });

  // ── useAssetsSearch ───────────────────────────────────────────────────────
  describe('useAssetsSearch', () => {
    const mockPaginatedResponse = {
      content: [mockAsset],
      totalElements: 1,
      totalPages: 1,
      number: 0,
      size: 20,
    };

    it('should fetch assets with search filters and pagination', async () => {
      mockedApiClient.get.mockResolvedValue({ data: mockPaginatedResponse });

      const { result } = renderHook(
        () => useAssetsSearch({ keyword: 'Apple', page: 0, size: 20 }),
        { wrapper }
      );

      await waitFor(() => expect(result.current.isSuccess).toBe(true));
      expect(result.current.data).toEqual(mockPaginatedResponse);
    });
  });

  // ── useAsset ──────────────────────────────────────────────────────────────
  describe('useAsset', () => {
    it('should fetch a single asset by ID', async () => {
      mockedApiClient.get.mockResolvedValue({ data: mockAsset });

      const { result } = renderHook(() => useAsset(1), { wrapper });

      await waitFor(() => expect(result.current.isSuccess).toBe(true));
      expect(mockedApiClient.get).toHaveBeenCalledWith('/assets/1', {
        headers: { 'X-Encryption-Session': 'test-encryption-key' },
      });
    });

    it('should be disabled when assetId is null', () => {
      const { result } = renderHook(() => useAsset(null), { wrapper });
      expect(result.current.isFetched).toBe(false);
    });
  });

  // ── useCreateAsset ────────────────────────────────────────────────────────
  describe('useCreateAsset', () => {
    it('should create an asset and invalidate queries', async () => {
      mockedApiClient.post.mockResolvedValue({ data: mockAsset });
      const invalidateSpy = vi.spyOn(queryClient, 'invalidateQueries');

      const { result } = renderHook(() => useCreateAsset(), { wrapper });

      result.current.mutate({ name: 'Apple Stock', type: 'STOCK' } as any);

      await waitFor(() => expect(result.current.isSuccess).toBe(true));

      expect(invalidateSpy).toHaveBeenCalledWith({ queryKey: ['assets'] });
    });
  });

  // ── useUpdateAsset ────────────────────────────────────────────────────────
  describe('useUpdateAsset', () => {
    it('refreshes linked account balances in both the account list and detail after changing value', async () => {
      queryClient.setDefaultOptions({ queries: { retry: false, staleTime: 5 * 60 * 1000 } });
      let balance = 100;
      mockedApiClient.get.mockImplementation(async (url: string) => {
        const account = { id: 2, balance, currency: 'EUR' };
        if (url === '/accounts/2') return { data: account };
        if (url.startsWith('/accounts/search?')) {
          return {
            data: { content: [account], totalElements: 1, totalPages: 1, number: 0, size: 20 },
          };
        }
        throw new Error(`Unexpected request: ${url}`);
      });
      mockedApiClient.put.mockImplementation(async () => {
        balance = 200;
        return { data: { ...mockAsset, accountId: 2, currentPrice: 200 } };
      });
      const { result } = renderHook(
        () => ({
          search: useAccountsSearch(),
          account: useAccount(2),
          update: useUpdateAsset(),
        }),
        { wrapper }
      );
      await waitFor(() => {
        expect(result.current.search.data?.content[0].balance).toBe(100);
        expect(result.current.account.data?.balance).toBe(100);
      });

      await act(async () => {
        await result.current.update.mutateAsync({
          id: 1,
          data: {
            accountId: 2,
            name: 'Holding',
            type: 'STOCK',
            quantity: '1',
            purchasePrice: '100',
            currentPrice: '200',
            currency: 'EUR',
            purchaseDate: '2024-01-01',
          },
        });
      });

      await waitFor(() => {
        expect(result.current.search.data?.content[0].balance).toBe(200);
        expect(result.current.account.data?.balance).toBe(200);
      });
    });

    it('should update an asset and invalidate queries', async () => {
      const updated = { ...mockAsset, name: 'Updated' };
      mockedApiClient.put.mockResolvedValue({ data: updated });
      const invalidateSpy = vi.spyOn(queryClient, 'invalidateQueries');

      const { result } = renderHook(() => useUpdateAsset(), { wrapper });

      result.current.mutate({ id: 1, data: { name: 'Updated' } as any });

      await waitFor(() => expect(result.current.isSuccess).toBe(true));

      expect(invalidateSpy).toHaveBeenCalledWith({ queryKey: ['assets'] });
      expect(invalidateSpy).toHaveBeenCalledWith({ queryKey: ['accounts'] });
    });
  });

  // ── useDeleteAsset ────────────────────────────────────────────────────────
  describe('useDeleteAsset', () => {
    it('should delete an asset and invalidate queries', async () => {
      mockedApiClient.delete.mockResolvedValue({});
      const invalidateSpy = vi.spyOn(queryClient, 'invalidateQueries');

      const { result } = renderHook(() => useDeleteAsset(), { wrapper });

      result.current.mutate(1);

      await waitFor(() => expect(result.current.isSuccess).toBe(true));

      expect(mockedApiClient.delete).toHaveBeenCalledWith('/assets/1', {
        headers: { 'X-Encryption-Session': 'test-encryption-key' },
      });
      expect(invalidateSpy).toHaveBeenCalledWith({ queryKey: ['assets'] });
    });
  });
});

// ── Utility function tests ────────────────────────────────────────────────────
describe('Asset utility functions', () => {
  describe('formatGainLoss', () => {
    it('should format positive gain with green color', () => {
      const result = formatGainLoss(500, 10.5);
      expect(result.color).toBe('text-green-500');
      expect(result.formatted).toContain('+');
      expect(result.formatted).toContain('10.50%');
    });

    it('should format negative loss with red color', () => {
      const result = formatGainLoss(-200, -5.25);
      expect(result.color).toBe('text-red-500');
      expect(result.formatted).toContain('-5.25%');
    });

    it('should format zero gain with muted color', () => {
      const result = formatGainLoss(0, 0);
      expect(result.color).toBe('text-text-muted');
    });
  });

  describe('getAssetTypeName', () => {
    it('should return correct name for known types', () => {
      expect(getAssetTypeName('STOCK')).toBe('Stock');
      expect(getAssetTypeName('ETF')).toBe('ETF');
      expect(getAssetTypeName('CRYPTO')).toBe('Cryptocurrency');
      expect(getAssetTypeName('REAL_ESTATE')).toBe('Real Estate');
      expect(getAssetTypeName('VEHICLE')).toBe('Vehicle');
      expect(getAssetTypeName('JEWELRY')).toBe('Jewelry');
    });

    it('should return raw type for unknown types', () => {
      expect(getAssetTypeName('UNKNOWN_TYPE')).toBe('UNKNOWN_TYPE');
    });
  });

  describe('getAssetTypeBadgeVariant', () => {
    it('should return correct badge variant for known types', () => {
      expect(getAssetTypeBadgeVariant('STOCK')).toBe('info');
      expect(getAssetTypeBadgeVariant('CRYPTO')).toBe('warning');
      expect(getAssetTypeBadgeVariant('BOND')).toBe('success');
      expect(getAssetTypeBadgeVariant('MUTUAL_FUND')).toBe('default');
    });

    it('should return "default" for unknown types', () => {
      expect(getAssetTypeBadgeVariant('UNKNOWN')).toBe('default');
    });
  });

  describe('getConditionBadgeVariant', () => {
    it('should return correct variant for known conditions', () => {
      expect(getConditionBadgeVariant('NEW')).toBe('success');
      expect(getConditionBadgeVariant('EXCELLENT')).toBe('info');
      expect(getConditionBadgeVariant('GOOD')).toBe('warning');
      expect(getConditionBadgeVariant('POOR')).toBe('error');
    });

    it('should return "default" for unknown conditions', () => {
      expect(getConditionBadgeVariant('UNKNOWN')).toBe('default');
    });
  });
});
