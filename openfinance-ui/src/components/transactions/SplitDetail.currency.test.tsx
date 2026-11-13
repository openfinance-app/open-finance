import { beforeEach, describe, expect, it, vi } from 'vitest';
import { screen } from '@testing-library/react';
import { mockAuthentication, renderWithProviders } from '@/test/test-utils';
import { SplitDetail } from '@/components/transactions/SplitDetail';
import { splitCurrencyAmounts } from '@/utils/split-currency-amounts';
import type { Transaction } from '@/types/transaction';
import type { AmountDisplayMode } from '@/context/CurrencyDisplayContext';

const state = vi.hoisted(() => ({ mode: 'base' as AmountDisplayMode }));
vi.mock('@/context/CurrencyDisplayContext', async importOriginal => ({
  ...(await importOriginal<typeof import('@/context/CurrencyDisplayContext')>()),
  useCurrencyDisplay: () => ({ displayMode: state.mode, secondaryCurrency: null }),
}));

const transaction = {
  amount: 3,
  currency: 'USD',
  amountInBaseCurrency: 1,
  baseCurrency: 'EUR',
  exchangeRate: 1 / 3,
  isConverted: true,
  date: '2024-06-01',
} as Transaction;
const splits = [1, 2, 3].map(id => ({ id, transactionId: 4, amount: 1 }));

describe('split currency display', () => {
  beforeEach(() => {
    mockAuthentication();
    state.mode = 'base';
  });

  it.each(['base', 'native', 'both'] as const)('matches the parent in %s mode', mode => {
    state.mode = mode;
    renderWithProviders(<SplitDetail splits={splits} currency="USD" transaction={transaction} />);
    if (mode !== 'native') {
      expect(screen.getByText('€0.34')).toBeVisible();
      expect(screen.getAllByText('€0.33')).toHaveLength(2);
    } else expect(screen.queryByText('€0.34')).not.toBeInTheDocument();
    if (mode !== 'base') expect(screen.getAllByText('$1.00')).toHaveLength(3);
    else expect(screen.queryByText('$1.00')).not.toBeInTheDocument();
  });

  it('does not assign the whole parent total to a subset of split lines', () => {
    expect(splitCurrencyAmounts(splits.slice(0, 1), transaction)).toEqual([
      [{ amount: 0.33, currency: 'EUR' }],
    ]);
  });

  it('allows sub-cent parts to round to zero so they still sum to the parent', () => {
    renderWithProviders(
      <SplitDetail
        splits={splits}
        currency="USD"
        transaction={{ ...transaction, amountInBaseCurrency: 0.02 }}
      />
    );
    expect(screen.getAllByText('€0.01')).toHaveLength(2);
    expect(screen.getByText('€0.00')).toBeVisible();
  });

  it('preserves recorded source equivalents for secondary display', () => {
    const values = splitCurrencyAmounts(splits, {
      ...transaction,
      originalCurrency: 'GBP',
      originalAmount: 6,
    });
    expect(
      values.every(row => row.some(value => value.currency === 'GBP' && value.amount === 2))
    ).toBe(true);
  });

  it('does not invent base amounts when conversion failed', () => {
    expect(splitCurrencyAmounts(splits, { ...transaction, isConverted: false })).toEqual([
      [],
      [],
      [],
    ]);
  });
});
