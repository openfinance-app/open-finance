import { renderHook, waitFor, act } from '@testing-library/react';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { describe, it, expect, vi, beforeEach } from 'vitest';
import React from 'react';
import {
  useMarketQuote,
  useSymbolSearch,
  useHistoricalPrices,
  useUpdateAssetPrice,
  useUpdateAllAssetPrices,
} from './useMarketData';
import apiClient from '@/services/apiClient';

vi.mock('@/services/apiClient');
const mockedApiClient = apiClient as any;

describe('useMarketData hooks', () => {
  let queryClient: QueryClient;

  beforeEach(() => {
    queryClient = new QueryClient({
      defaultOptions: { queries: { retry: false } },
    });
    vi.clearAllMocks();
  });

  const wrapper = ({ children }: { children: React.ReactNode }) => (
    <QueryClientProvider client={queryClient}>{children}</QueryClientProvider>
  );

  // ── useMarketQuote ─────────────────────────────────────────────────
  describe('useMarketQuote', () => {
    it('should fetch market quote for a symbol', async () => {
      const mockQuote = {
        symbol: 'AAPL',
        price: 175.5,
        change: 2.3,
        changePercent: 1.33,
        volume: 50000000,
        marketCap: 2800000000000,
      };
      mockedApiClient.get.mockResolvedValue({ data: mockQuote });

      const { result } = renderHook(() => useMarketQuote('AAPL'), { wrapper });

      await waitFor(() => expect(result.current.isSuccess).toBe(true));
      expect(result.current.data).toEqual(mockQuote);
      expect(mockedApiClient.get).toHaveBeenCalledWith('/market/quote?symbol=AAPL');
    });

    it('should be disabled when symbol is null', () => {
      const { result } = renderHook(() => useMarketQuote(null), { wrapper });
      expect(result.current.fetchStatus).toBe('idle');
    });
  });

  // ── useSymbolSearch ─────────────────────────────────────────────────
  describe('useSymbolSearch', () => {
    it('should search for symbols', async () => {
      const mockResults = [
        { symbol: 'AAPL', name: 'Apple Inc.', exchange: 'NASDAQ', type: 'EQUITY' },
        { symbol: 'AAPX', name: 'Apple Derivative', exchange: 'NYSE', type: 'ETF' },
      ];
      mockedApiClient.get.mockResolvedValue({ data: mockResults });

      const { result } = renderHook(() => useSymbolSearch('AAP'), { wrapper });

      await waitFor(() => expect(result.current.isSuccess).toBe(true));
      expect(result.current.data).toEqual(mockResults);
    });

    it('should not search when query is less than 2 characters', () => {
      const { result } = renderHook(() => useSymbolSearch('A'), { wrapper });
      expect(result.current.fetchStatus).toBe('idle');
    });

    it('should not search with empty query', () => {
      const { result } = renderHook(() => useSymbolSearch(''), { wrapper });
      expect(result.current.fetchStatus).toBe('idle');
    });
  });

  // ── useHistoricalPrices ─────────────────────────────────────────────────
  describe('useHistoricalPrices', () => {
    it('should fetch historical prices for a symbol', async () => {
      const mockPrices = [
        {
          date: '2024-01-01',
          close: 170.0,
          open: 168.0,
          high: 172.0,
          low: 167.0,
          volume: 30000000,
        },
        {
          date: '2024-01-02',
          close: 175.5,
          open: 170.0,
          high: 176.0,
          low: 169.0,
          volume: 40000000,
        },
      ];
      mockedApiClient.get.mockResolvedValue({ data: mockPrices });

      const { result } = renderHook(() => useHistoricalPrices('AAPL', '2024-01-01', '2024-01-31'), {
        wrapper,
      });

      await waitFor(() => expect(result.current.isSuccess).toBe(true));
      expect(result.current.data).toEqual(mockPrices);
      const url = mockedApiClient.get.mock.calls[0][0] as string;
      expect(url).toContain('symbol=AAPL');
      expect(url).toContain('startDate=2024-01-01');
      expect(url).toContain('endDate=2024-01-31');
    });

    it('should be disabled when symbol is null', () => {
      const { result } = renderHook(() => useHistoricalPrices(null, '2024-01-01', '2024-01-31'), {
        wrapper,
      });
      expect(result.current.fetchStatus).toBe('idle');
    });
  });

  // ── useUpdateAssetPrice ─────────────────────────────────────────────────
  describe('useUpdateAssetPrice', () => {
    it('should update price for a single asset', async () => {
      const mockResponse = { assetId: 1, symbol: 'AAPL', newPrice: 175.5, previousPrice: 170.0 };
      mockedApiClient.post.mockResolvedValue({ data: mockResponse });

      const { result } = renderHook(() => useUpdateAssetPrice(), { wrapper });

      await act(async () => {
        result.current.mutate(1);
      });

      await waitFor(() => expect(result.current.isSuccess).toBe(true));
      expect(result.current.data).toEqual(mockResponse);
      expect(mockedApiClient.post).toHaveBeenCalledWith('/market/assets/1/update-price');
    });

    it('should invalidate assets query on success', async () => {
      mockedApiClient.post.mockResolvedValue({ data: {} });
      const invalidateSpy = vi.spyOn(queryClient, 'invalidateQueries');

      const { result } = renderHook(() => useUpdateAssetPrice(), { wrapper });

      await act(async () => {
        result.current.mutate(1);
      });

      await waitFor(() => expect(result.current.isSuccess).toBe(true));
      expect(invalidateSpy).toHaveBeenCalledWith({ queryKey: ['assets'] });
    });
  });

  // ── useUpdateAllAssetPrices ─────────────────────────────────────────────────
  describe('useUpdateAllAssetPrices', () => {
    it('should update prices for all assets', async () => {
      mockedApiClient.post.mockResolvedValue({ data: { updated: 3, message: 'Success' } });

      const { result } = renderHook(() => useUpdateAllAssetPrices(), { wrapper });

      await act(async () => {
        result.current.mutate([1, 2, 3]);
      });

      await waitFor(() => expect(result.current.isSuccess).toBe(true));
      expect(result.current.data).toEqual({ updated: 3, failed: 0 });
      expect(mockedApiClient.post).toHaveBeenCalledTimes(1);
      expect(mockedApiClient.post).toHaveBeenCalledWith('/market/assets/refresh-all');
    });

    it('should handle server errors during bulk update', async () => {
      const errorMessage = 'Server error';
      mockedApiClient.post.mockRejectedValue(new Error(errorMessage));

      const { result } = renderHook(() => useUpdateAllAssetPrices(), { wrapper });

      await act(async () => {
        result.current.mutate([1, 2, 3]);
      });

      await waitFor(() => expect(result.current.isError).toBe(true));
      expect(result.current.error?.message).toBe(errorMessage);
    });
  });
});
