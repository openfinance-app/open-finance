import type { ReactNode } from 'react';
import { act, renderHook, waitFor } from '@testing-library/react';
import { vi, describe, it, expect, beforeEach } from 'vitest';
import { AllProviders, createTestQueryClient, mockAuthentication } from '@/test/test-utils';
import { useUserFinancialData } from '@/hooks/useUserFinancialData';
import apiClient from '@/services/apiClient';

vi.mock('@/services/apiClient');
const mockGet = vi.mocked(apiClient.get);

function renderFinancialData() {
  const client = createTestQueryClient();
  return renderHook(() => useUserFinancialData(), {
    wrapper: ({ children }: { children: ReactNode }) => (
      <AllProviders queryClient={client}>{children}</AllProviders>
    ),
  });
}

describe('useUserFinancialData', () => {
  beforeEach(() => {
    vi.resetAllMocks();
    mockAuthentication();
  });

  it('combines converted holdings and dated expenses in the reporting currency', async () => {
    mockGet.mockImplementation(async url => ({
      data:
        url === '/assets'
          ? [
              { type: 'STOCK', totalValue: 1000, currency: 'EUR' },
              {
                type: 'STOCK',
                totalValue: 100000,
                currency: 'JPY',
                valueInBaseCurrency: 600,
                baseCurrency: 'EUR',
                isConverted: true,
              },
              { type: 'STOCK', totalValue: 99999, currency: 'EUR', acquisitionType: 'PLANNED' },
            ]
          : url === '/accounts'
            ? []
            : { expenses: 360 },
    }));
    const { result } = renderFinancialData();
    await waitFor(() =>
      expect(result.current.data).toEqual({
        totalSavings: 1600,
        averageMonthlyExpenses: 60,
        currency: 'EUR',
      })
    );
    expect(mockGet).toHaveBeenCalledWith('/dashboard/cashflow', {
      params: {
        startDate: expect.stringMatching(/^\d{4}-\d{2}-\d{2}$/),
        endDate: expect.stringMatching(/^\d{4}-\d{2}-\d{2}$/),
      },
    });
  });

  it('keeps a zero valuation rather than substituting quantity times price', async () => {
    mockGet.mockImplementation(async url => ({
      data:
        url === '/assets'
          ? [{ type: 'STOCK', totalValue: 0, quantity: 100, currentPrice: 50, currency: 'EUR' }]
          : url === '/accounts'
            ? []
            : { expenses: 0 },
    }));
    const { result } = renderFinancialData();
    await waitFor(() => expect(result.current.data?.totalSavings).toBe(0));
  });

  it('does not autofill totals when a foreign holding cannot be converted', async () => {
    mockGet.mockImplementation(async url => ({
      data:
        url === '/assets'
          ? [
              {
                type: 'STOCK',
                totalValue: 100000,
                currency: 'JPY',
                valueInBaseCurrency: 100000,
                baseCurrency: 'EUR',
                isConverted: false,
              },
            ]
          : url === '/accounts'
            ? []
            : { expenses: 0 },
    }));
    const { result } = renderFinancialData();
    await waitFor(() => expect(result.current.error).not.toBeNull());
    expect(result.current.data).toBeNull();
  });

  it('handles an empty portfolio and zero expenses', async () => {
    mockGet.mockImplementation(async url => ({
      data: url === '/assets' ? [] : url === '/accounts' ? [] : { expenses: 0 },
    }));
    const { result } = renderFinancialData();
    await waitFor(() =>
      expect(result.current.data).toEqual({
        totalSavings: 0,
        averageMonthlyExpenses: 0,
        currency: 'EUR',
      })
    );
  });

  it('exposes a rate or API failure instead of a partial financial total', async () => {
    mockGet.mockRejectedValue(new Error('Rate unavailable'));
    const { result } = renderFinancialData();
    await waitFor(() => expect(result.current.error).toBe('Rate unavailable'));
    expect(result.current.data).toBeNull();
    expect(result.current.isLoading).toBe(false);
  });

  it('refreshes the totals on demand', async () => {
    mockGet.mockImplementation(async url => ({
      data: url === '/assets' ? [] : url === '/accounts' ? [] : { expenses: 60 },
    }));
    const { result } = renderFinancialData();
    await waitFor(() => expect(result.current.data?.averageMonthlyExpenses).toBe(10));
    mockGet.mockImplementation(async url => ({
      data: url === '/assets' ? [] : url === '/accounts' ? [] : { expenses: 120 },
    }));
    await act(async () => {
      await result.current.refetch();
    });
    await waitFor(() => expect(result.current.data?.averageMonthlyExpenses).toBe(20));
  });
  it('includes cash, converts foreign own balances, and counts linked holdings only once', async () => {
    mockGet.mockImplementation(async url => ({
      data:
        url === '/accounts'
          ? [
              { currency: 'EUR', ownBalance: 12000, balance: 12000, isActive: true },
              { currency: 'EUR', ownBalance: 200, balance: 1200, isActive: true },
              {
                currency: 'USD',
                ownBalance: 100,
                balance: 100,
                baseCurrency: 'EUR',
                exchangeRate: 0.8,
                isActive: true,
              },
              { currency: 'EUR', ownBalance: -300, balance: -300, isActive: true },
              { currency: 'EUR', ownBalance: 99999, balance: 99999, isActive: false },
            ]
          : url === '/assets'
            ? [
                { type: 'STOCK', currency: 'EUR', totalValue: 1000 },
                { type: 'REAL_ESTATE', currency: 'EUR', totalValue: 300000 },
                { type: 'VEHICLE', currency: 'EUR', totalValue: 20000 },
                { type: 'STOCK', currency: 'EUR', totalValue: 5000, acquisitionType: 'PLANNED' },
              ]
            : { expenses: 6000 },
    }));
    const { result } = renderFinancialData();
    await waitFor(() =>
      expect(result.current.data).toEqual({
        totalSavings: 12980,
        averageMonthlyExpenses: 1000,
        currency: 'EUR',
      })
    );
  });

  it('includes a bank-only household with no assets', async () => {
    mockGet.mockImplementation(async url => ({
      data:
        url === '/accounts'
          ? [{ currency: 'EUR', ownBalance: 12000, balance: 12000, isActive: true }]
          : url === '/assets'
            ? []
            : { expenses: 6000 },
    }));
    const { result } = renderFinancialData();
    await waitFor(() => expect(result.current.data?.totalSavings).toBe(12000));
  });
});
