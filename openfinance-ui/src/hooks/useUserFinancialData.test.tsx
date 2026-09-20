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
              { totalValue: 1000, currency: 'EUR' },
              {
                totalValue: 100000,
                currency: 'JPY',
                valueInBaseCurrency: 600,
                baseCurrency: 'EUR',
                isConverted: true,
              },
              { totalValue: 99999, currency: 'EUR', acquisitionType: 'PLANNED' },
            ]
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
          ? [{ totalValue: 0, quantity: 100, currentPrice: 50, currency: 'EUR' }]
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
                totalValue: 100000,
                currency: 'JPY',
                valueInBaseCurrency: 100000,
                baseCurrency: 'EUR',
                isConverted: false,
              },
            ]
          : { expenses: 0 },
    }));
    const { result } = renderFinancialData();
    await waitFor(() => expect(result.current.error).not.toBeNull());
    expect(result.current.data).toBeNull();
  });

  it('handles an empty portfolio and zero expenses', async () => {
    mockGet.mockImplementation(async url => ({ data: url === '/assets' ? [] : { expenses: 0 } }));
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
    mockGet.mockImplementation(async url => ({ data: url === '/assets' ? [] : { expenses: 60 } }));
    const { result } = renderFinancialData();
    await waitFor(() => expect(result.current.data?.averageMonthlyExpenses).toBe(10));
    mockGet.mockImplementation(async url => ({ data: url === '/assets' ? [] : { expenses: 120 } }));
    await act(async () => {
      await result.current.refetch();
    });
    await waitFor(() => expect(result.current.data?.averageMonthlyExpenses).toBe(20));
  });
});
