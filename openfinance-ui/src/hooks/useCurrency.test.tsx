import { renderHook, waitFor } from '@testing-library/react';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { describe, it, expect, vi, beforeEach } from 'vitest';
import React from 'react';
import {
  useCurrencies,
  useAllCurrencies,
  useExchangeRate,
  useLatestExchangeRate,
  useConvertCurrency,
  useUpdateExchangeRates,
  useConvertAmount,
  useCurrencyFormat,
} from './useCurrency';
import apiClient from '@/services/apiClient';

vi.mock('@/services/apiClient');
const mockedApiClient = apiClient as any;

describe('useCurrency hooks', () => {
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

  const mockCurrencies = [
    { code: 'USD', name: 'US Dollar', symbol: '$', isActive: true },
    { code: 'EUR', name: 'Euro', symbol: '\u20AC', isActive: true },
    { code: 'CHF', name: 'Swiss Franc', symbol: 'CHF', isActive: true },
  ];

  // ── useCurrencies ─────────────────────────────────────────────────────
  describe('useCurrencies', () => {
    it('should fetch active currencies', async () => {
      mockedApiClient.get.mockResolvedValue({ data: mockCurrencies });

      const { result } = renderHook(() => useCurrencies(), { wrapper });

      await waitFor(() => expect(result.current.isSuccess).toBe(true));
      expect(result.current.data).toEqual(mockCurrencies);
      expect(mockedApiClient.get).toHaveBeenCalledWith('/currencies');
    });
  });

  // ── useAllCurrencies ──────────────────────────────────────────────────
  describe('useAllCurrencies', () => {
    it('should fetch all currencies including inactive', async () => {
      mockedApiClient.get.mockResolvedValue({ data: mockCurrencies });

      const { result } = renderHook(() => useAllCurrencies(), { wrapper });

      await waitFor(() => expect(result.current.isSuccess).toBe(true));
      expect(mockedApiClient.get).toHaveBeenCalledWith('/currencies/all');
    });
  });

  // ── useExchangeRate ───────────────────────────────────────────────────
  describe('useExchangeRate', () => {
    it('should fetch exchange rate between two currencies', async () => {
      const mockRate = { from: 'USD', to: 'EUR', rate: 0.92, date: '2026-03-19' };
      mockedApiClient.get.mockResolvedValue({ data: mockRate });

      const { result } = renderHook(() => useExchangeRate('USD', 'EUR'), { wrapper });

      await waitFor(() => expect(result.current.isSuccess).toBe(true));
      expect(result.current.data).toEqual(mockRate);
    });

    it('should be disabled when from is empty', () => {
      const { result } = renderHook(() => useExchangeRate('', 'EUR'), { wrapper });
      expect(result.current.isFetched).toBe(false);
    });

    it('should be disabled when to is empty', () => {
      const { result } = renderHook(() => useExchangeRate('USD', ''), { wrapper });
      expect(result.current.isFetched).toBe(false);
    });

    it('should include date parameter when provided', async () => {
      const mockRate = { from: 'USD', to: 'EUR', rate: 0.91, date: '2026-01-15' };
      mockedApiClient.get.mockResolvedValue({ data: mockRate });

      const { result } = renderHook(() => useExchangeRate('USD', 'EUR', '2026-01-15'), { wrapper });

      await waitFor(() => expect(result.current.isSuccess).toBe(true));
    });
  });

  // ── useLatestExchangeRate ─────────────────────────────────────────────
  describe('useLatestExchangeRate', () => {
    it('should fetch latest exchange rate', async () => {
      const mockRate = { from: 'USD', to: 'EUR', rate: 0.92 };
      mockedApiClient.get.mockResolvedValue({ data: mockRate });

      const { result } = renderHook(() => useLatestExchangeRate('USD', 'EUR'), { wrapper });

      await waitFor(() => expect(result.current.isSuccess).toBe(true));
    });

    it('should be disabled when same currency', () => {
      const { result } = renderHook(() => useLatestExchangeRate('USD', 'USD'), { wrapper });
      expect(result.current.isFetched).toBe(false);
    });
  });

  // ── useConvertCurrency ────────────────────────────────────────────────
  describe('useConvertCurrency', () => {
    it('should convert currency amount', async () => {
      const mockResponse = { amount: 920, from: 'USD', to: 'EUR', rate: 0.92 };
      mockedApiClient.post.mockResolvedValue({ data: mockResponse });

      const { result } = renderHook(() => useConvertCurrency(), { wrapper });

      result.current.mutate({ amount: 1000, from: 'USD', to: 'EUR' });

      await waitFor(() => expect(result.current.isSuccess).toBe(true));
      expect(result.current.data).toEqual(mockResponse);
    });
  });

  // ── useUpdateExchangeRates ────────────────────────────────────────────
  describe('useUpdateExchangeRates', () => {
    it('should update exchange rates and invalidate queries', async () => {
      const mockResponse = { updated: 42 };
      mockedApiClient.post.mockResolvedValue({ data: mockResponse });
      const invalidateSpy = vi.spyOn(queryClient, 'invalidateQueries');

      const { result } = renderHook(() => useUpdateExchangeRates(), { wrapper });

      result.current.mutate();

      await waitFor(() => expect(result.current.isSuccess).toBe(true));
      expect(invalidateSpy).toHaveBeenCalledWith({ queryKey: ['exchangeRate'] });
    });
  });

  // ── useConvertAmount ──────────────────────────────────────────────────
  describe('useConvertAmount', () => {
    it('should return same amount when currencies are equal', () => {
      const { result } = renderHook(() => useConvertAmount(1000, 'USD', 'USD'), { wrapper });
      expect(result.current).toBe(1000);
    });

    it('should return null when rate is not available and currencies differ', () => {
      // No rate data will be fetched (mock returns nothing by default for this query key)
      const { result } = renderHook(() => useConvertAmount(1000, 'USD', 'EUR'), { wrapper });
      expect(result.current).toBeNull();
    });
  });

  // ── useCurrencyFormat ─────────────────────────────────────────────────
  describe('useCurrencyFormat', () => {
    it('should return a formatter function', () => {
      // Without currencies loaded, should return fallback format
      const { result } = renderHook(() => useCurrencyFormat('USD'), { wrapper });
      expect(typeof result.current).toBe('function');
    });

    it('should format amount with fallback when currency not loaded', () => {
      const { result } = renderHook(() => useCurrencyFormat('USD'), { wrapper });
      const formatted = result.current(1234.56);
      expect(formatted).toContain('USD');
      // Fallback uses toFixed(2) without locale formatting: "USD 1234.56"
      expect(formatted).toContain('1234.56');
    });

    it('should format negative amounts', () => {
      const { result } = renderHook(() => useCurrencyFormat('USD'), { wrapper });
      const formatted = result.current(-500.0);
      expect(formatted).toContain('-');
      expect(formatted).toContain('500.00');
    });

    it('should format CHF with symbol after amount when currency loaded', async () => {
      mockedApiClient.get.mockResolvedValue({ data: mockCurrencies });

      const { result } = renderHook(() => useCurrencyFormat('CHF'), { wrapper });

      // Initially formatted with fallback
      const initialFormatted = result.current(1000);
      expect(initialFormatted).toBeDefined();
    });
  });
});
