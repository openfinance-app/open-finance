/**
 * Currency management hooks
 * Sprint 6 - Task 6.2.11: Multi-currency support frontend
 * 
 * Provides React Query hooks for currency and exchange rate operations
 */
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import apiClient from '@/services/apiClient';
import { multiply } from '@/utils/money';
import type {
  Currency,
  ExchangeRate,
  ConvertRequest,
  ConvertResponse,
  UpdateRatesResponse,
} from '../types/currency';

/**
 * Get all active currencies
 */
export function useCurrencies() {
  return useQuery({
    queryKey: ['currencies'],
    queryFn: async () => {
      const response = await apiClient.get<Currency[]>('/currencies');
      return response.data;
    },
    staleTime: 1000 * 60 * 60, // 1 hour (currencies don't change often)
  });
}

/**
 * Get all currencies (including inactive)
 */
export function useAllCurrencies() {
  return useQuery({
    queryKey: ['currencies', 'all'],
    queryFn: async () => {
      const response = await apiClient.get<Currency[]>('/currencies/all');
      return response.data;
    },
    staleTime: 1000 * 60 * 60, // 1 hour
  });
}

/**
 * Get exchange rate for specific date
 */
export function useExchangeRate(from: string, to: string, date?: string) {
  return useQuery({
    queryKey: ['exchangeRate', from, to, date],
    queryFn: async () => {
      const params = new URLSearchParams({ from, to });
      if (date) {
        params.append('date', date);
      }
      const response = await apiClient.get<ExchangeRate>('/currencies/exchange-rates', { params });
      return response.data;
    },
    enabled: !!from && !!to,
    staleTime: 1000 * 60 * 15, // 15 minutes
  });
}

/**
 * Get latest exchange rate
 * 
 * @param from - Source currency code
 * @param to - Target currency code
 * @param refreshKey - Optional key to force refetch (increment to refresh)
 */
export function useLatestExchangeRate(from: string, to: string, refreshKey?: number) {
  return useQuery({
    queryKey: ['exchangeRate', 'latest', from, to, refreshKey],
    queryFn: async () => {
      const params = new URLSearchParams({ from, to });
      const response = await apiClient.get<ExchangeRate>('/currencies/exchange-rates/latest', { params });
      return response.data;
    },
    enabled: !!from && !!to && from !== to, // Don't fetch if same currency
    staleTime: 1000 * 60 * 15, // 15 minutes
  });
}

/**
 * Convert currency amount
 */
export function useConvertCurrency() {
  return useMutation({
    mutationFn: async (request: ConvertRequest) => {
      const response = await apiClient.post<ConvertResponse>('/currencies/convert', request);
      return response.data;
    },
  });
}

/**
 * Update exchange rates (admin only)
 */
export function useUpdateExchangeRates() {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: async () => {
      const response = await apiClient.post<UpdateRatesResponse>('/currencies/exchange-rates/update');
      return response.data;
    },
    onSuccess: () => {
      // Invalidate all exchange rate queries to refetch with new rates
      queryClient.invalidateQueries({ queryKey: ['exchangeRate'] });
    },
  });
}

/**
 * Helper hook to convert an amount between currencies
 * Returns null if conversion not possible
 */
export function useConvertAmount(amount: number, from: string, to: string) {
  const { data: rate } = useLatestExchangeRate(from, to);

  if (!rate || from === to) {
    return from === to ? amount : null;
  }

  return multiply(amount, rate.rate);
}

/**
 * Helper hook to format amount in a specific currency
 */
export function useCurrencyFormat(code: string) {
  const { data: currencies } = useCurrencies();
  
  const currency = currencies?.find(c => c.code === code);
  
  return (amount: number) => {
    if (!currency) {
      return `${code} ${amount.toFixed(2)}`;
    }

    const formattedAmount = new Intl.NumberFormat('en-US', {
      minimumFractionDigits: 2,
      maximumFractionDigits: 2,
    }).format(Math.abs(amount));

    const sign = amount < 0 ? '-' : '';

    // Special case for CHF (symbol after)
    if (code === 'CHF') {
      return `${sign}${formattedAmount} ${currency.symbol}`;
    }

    return `${sign}${currency.symbol}${formattedAmount}`;
  };
}
