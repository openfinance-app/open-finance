/**
 * Market data hooks
 * Task 5.4.4: Market data integration hooks
 * 
 * Provides React Query hooks for market data operations
 */
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import apiClient from '@/services/apiClient';
import type { MarketQuote, HistoricalPrice, SymbolSearchResult, UpdatePriceResponse } from '@/types/market';

/**
 * Fetch real-time quote for a symbol
 */
export function useMarketQuote(symbol: string | null) {
  return useQuery<MarketQuote>({
    queryKey: ['market', 'quote', symbol],
    queryFn: async () => {
      if (!symbol) throw new Error('Symbol is required');

      const response = await apiClient.get<MarketQuote>(`/market/quote?symbol=${symbol}`);
      return response.data;
    },
    enabled: !!symbol,
    staleTime: 5 * 60 * 1000, // 5 minutes
    gcTime: 15 * 60 * 1000, // 15 minutes (formerly cacheTime)
  });
}

/**
 * Search for symbols
 */
export function useSymbolSearch(query: string) {
  return useQuery<SymbolSearchResult[]>({
    queryKey: ['market', 'search', query],
    queryFn: async () => {
      if (!query || query.length < 2) return [];

      const response = await apiClient.get<SymbolSearchResult[]>(`/market/search?q=${encodeURIComponent(query)}`);
      return response.data;
    },
    enabled: query.length >= 2,
    staleTime: 10 * 60 * 1000, // 10 minutes
  });
}

/**
 * Fetch historical prices for a symbol
 */
export function useHistoricalPrices(
  symbol: string | null,
  startDate: string,
  endDate: string
) {
  return useQuery<HistoricalPrice[]>({
    queryKey: ['market', 'history', symbol, startDate, endDate],
    queryFn: async () => {
      if (!symbol) throw new Error('Symbol is required');

      const params = new URLSearchParams({
        symbol,
        startDate,
        endDate,
      });

      const response = await apiClient.get<HistoricalPrice[]>(`/market/history?${params.toString()}`);
      return response.data;
    },
    enabled: !!symbol && !!startDate && !!endDate,
    staleTime: 60 * 60 * 1000, // 1 hour (historical data doesn't change)
  });
}

/**
 * Update price for a single asset
 */
export function useUpdateAssetPrice() {
  const queryClient = useQueryClient();

  return useMutation<UpdatePriceResponse, Error, number>({
    mutationFn: async (assetId: number) => {
      const response = await apiClient.post<UpdatePriceResponse>(`/market/assets/${assetId}/update-price`);
      return response.data;
    },
    onSuccess: () => {
      // Invalidate assets query to refetch with updated prices
      queryClient.invalidateQueries({ queryKey: ['assets'] });
      // Refresh dashboard cards (Net Worth, Total Assets) which cache with a long staleTime
      queryClient.invalidateQueries({ queryKey: ['dashboard'] });
    },
  });
}

/**
 * Update prices for all user assets with symbols
 * Calls a single bulk endpoint to avoid SQLite concurrent-write conflicts.
 */
export function useUpdateAllAssetPrices() {
  const queryClient = useQueryClient();

  return useMutation<{ updated: number; failed: number }, Error, number[]>({
    mutationFn: async (_assetIds: number[]) => {
      const response = await apiClient.post<{ message: string; updated: number }>(
        '/market/assets/refresh-all'
      );
      return { updated: response.data.updated, failed: 0 };
    },
    onSuccess: () => {
      // Invalidate assets query to refetch with updated prices
      queryClient.invalidateQueries({ queryKey: ['assets'] });
      // Refresh dashboard cards (Net Worth, Total Assets) which cache with a long staleTime
      queryClient.invalidateQueries({ queryKey: ['dashboard'] });
    },
  });
}
