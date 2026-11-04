import type { ReactNode } from 'react';
import { act, renderHook, waitFor } from '@testing-library/react';
import { beforeEach, afterEach, describe, expect, it, vi } from 'vitest';
import { AllProviders, createTestQueryClient, mockAuthentication } from '@/test/test-utils';
import apiClient from '@/services/apiClient';
import {
  useDisburseLiability,
  useRepaymentPreview,
  useAmortizationSchedule,
} from '@/hooks/useLiabilities';
import type { Liability } from '@/types/liability';

beforeEach(() => mockAuthentication());
afterEach(() => vi.restoreAllMocks());

function setup() {
  const queryClient = createTestQueryClient();
  const wrapper = ({ children }: { children: ReactNode }) => (
    <AllProviders queryClient={queryClient}>{children}</AllProviders>
  );
  return { queryClient, wrapper };
}

describe('liability payment queries', () => {
  it('preserves exact disbursement decimals and refreshes every affected balance after success', async () => {
    const { queryClient, wrapper } = setup();
    const affected = [
      'liabilities',
      'transactions',
      'accounts',
      'networth',
      'dashboard',
      'assets',
      'assetFinancing',
      'realEstate',
    ];
    [...affected, 'categories'].forEach(key =>
      queryClient.setQueryData([key, 9], { cached: true })
    );
    const saved = { id: 9, currentBalance: '123.123456789012345678' };
    const post = vi.spyOn(apiClient, 'post').mockResolvedValue({ data: saved });
    const hook = renderHook(useDisburseLiability, { wrapper });
    const request = {
      directRealEstateId: 4,
      trancheId: 2,
      amount: '123.123456789012345678',
      date: '2026-01-05',
      notes: 'Seller payment',
    };
    await act(async () => {
      expect(await hook.result.current.mutateAsync({ liabilityId: 9, request })).toEqual(saved);
    });
    expect(post).toHaveBeenCalledWith(
      '/liabilities/9/disburse',
      request,
      expect.objectContaining({ headers: expect.any(Object) })
    );
    affected.forEach(key =>
      expect(queryClient.getQueryState([key, 9])?.isInvalidated, key).toBe(true)
    );
    expect(queryClient.getQueryState(['categories', 9])?.isInvalidated).toBe(false);
  });

  it('does not refresh balances when the bank rejects a disbursement', async () => {
    const { queryClient, wrapper } = setup();
    queryClient.setQueryData(['accounts'], { balance: 100 });
    vi.spyOn(apiClient, 'post').mockRejectedValue(new Error('Tranche exhausted'));
    const hook = renderHook(useDisburseLiability, { wrapper });
    await act(async () => {
      await expect(
        hook.result.current.mutateAsync({
          liabilityId: 9,
          request: { amount: '101', date: '2026-01-05', toAccountId: 1 },
        })
      ).rejects.toThrow('Tranche exhausted');
    });
    expect(queryClient.getQueryState(['accounts'])?.isInvalidated).toBe(false);
  });

  it('waits for a complete repayment request, then sends the exact amount and date', async () => {
    const { wrapper } = setup();
    const preview = {
      total: 100,
      principal: 80,
      interest: 15,
      insurance: 5,
      interestOnly: false,
      exactValues: { total: '100.000000000000000001' },
    };
    const get = vi.spyOn(apiClient, 'get').mockResolvedValue({ data: preview });
    const hook = renderHook(
      ({ id, total, date }: { id?: number; total: string; date?: string }) =>
        useRepaymentPreview(id, total, date),
      {
        wrapper,
        initialProps: { id: undefined, total: '100', date: undefined } as {
          id?: number;
          total: string;
          date?: string;
        },
      }
    );
    expect(get.mock.calls.filter(([url]) => url.includes('/liabilities/'))).toHaveLength(0);
    hook.rerender({ id: 9, total: '-1', date: '2026-02-01' });
    expect(get.mock.calls.filter(([url]) => url.includes('/liabilities/'))).toHaveLength(0);
    hook.rerender({ id: 9, total: '100.000000000000000001', date: '2026-02-01' });
    await waitFor(() => expect(hook.result.current.data).toEqual(preview));
    expect(get).toHaveBeenCalledWith(
      '/liabilities/9/repayment-preview',
      expect.objectContaining({ params: { total: '100.000000000000000001', date: '2026-02-01' } })
    );
  });

  it('maps interest-only and insured amortization rows without losing their breakdown', async () => {
    const { wrapper } = setup();
    vi.spyOn(apiClient, 'get').mockResolvedValue({
      data: [
        {
          paymentNumber: 1,
          paymentDate: '2026-01-01',
          paymentAmount: 15,
          principalPortion: 0,
          interestPortion: 10,
          insurancePortion: 5,
          remainingBalance: 100,
          interestOnlyPhase: true,
        },
        {
          paymentNumber: 2,
          paymentDate: '2026-02-01',
          paymentAmount: 110,
          principalPortion: 100,
          interestPortion: 10,
          remainingBalance: 0,
        },
      ],
    });
    const liability: Liability = {
      id: 9,
      name: 'Mortgage',
      principal: 100,
      currentBalance: 100,
      currency: 'EUR',
      fundingStatus: 'ACTIVE',
      type: 'MORTGAGE',
      startDate: '2026-01-01',
      createdAt: '2026-01-01',
      updatedAt: '2026-01-01',
    };
    const hook = renderHook(() => useAmortizationSchedule(liability), { wrapper });
    await waitFor(() => expect(hook.result.current.isSuccess).toBe(true));
    expect(hook.result.current.data).toMatchObject({
      liabilityId: 9,
      liabilityName: 'Mortgage',
      totalInterest: 20,
      totalAmount: 125,
      monthlyPayment: 15,
      totalPayments: 2,
      interestRate: 0,
      currency: 'EUR',
      payments: [
        { principalPayment: 0, interestPayment: 10, insurancePayment: 5, interestOnlyPhase: true },
        {
          principalPayment: 100,
          interestPayment: 10,
          insurancePayment: 0,
          interestOnlyPhase: false,
        },
      ],
    });
  });
});
