import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { mockAuthentication, renderWithProviders } from '@/test/test-utils';
import { useDecimalPlaces } from '@/context/DecimalPlacesContext';
import type { AmountDisplayMode } from '@/context/CurrencyDisplayContext';
import { CurrencyAmount } from '@/components/ui/CurrencyAmount';
import { ConvertedAmount } from '@/components/ui/ConvertedAmount';
import { SearchResultAmount } from '@/components/search/SearchResultAmount';
import { TransferReceipt } from '@/components/transactions/TransferReceipt';
import { BudgetCard } from '@/components/budgets/BudgetCard';
import { setDecimalPlacesOverride } from '@/utils/currency';
import type { SearchResult } from '@/types/search';
import type { Transaction } from '@/types/transaction';
import type { BudgetProgressResponse } from '@/types/budget';

const state = vi.hoisted(() => ({
  mode: 'base' as AmountDisplayMode,
  secondary: null as string | null,
}));
const settings = { userId: 1, decimalPlacesOverrideEnabled: true, preferredDecimalPlaces: 4 };
vi.mock('@/hooks/useUserSettings', () => ({
  useUserSettings: () => ({ data: settings, isLoading: false }),
  useUpdateUserSettings: () => ({ mutateAsync: vi.fn() }),
}));
vi.mock('@/context/CurrencyDisplayContext', async importOriginal => ({
  ...(await importOriginal<typeof import('@/context/CurrencyDisplayContext')>()),
  useCurrencyDisplay: () => ({ displayMode: state.mode, secondaryCurrency: state.secondary }),
}));
vi.mock('@/hooks/useSecondaryConversion', () => ({
  useSecondaryConversion: () => ({ convert: () => null, secondaryCurrency: null }),
}));

function Controls() {
  const { setDecimalPlaces, setOverrideEnabled } = useDecimalPlaces();
  return (
    <>
      <button onClick={() => setDecimalPlaces(2)}>Two decimals</button>
      <button onClick={() => setDecimalPlaces(4)}>Four decimals</button>
      <button onClick={() => setOverrideEnabled(false)}>Currency defaults</button>
    </>
  );
}

const exact = '1.475349999999999999';

describe('monetary precision across surfaces', () => {
  beforeEach(() => {
    mockAuthentication();
    state.mode = 'base';
    state.secondary = null;
    localStorage.setItem('open_finance_number_format', '1,234.56');
    localStorage.setItem(
      'open_finance_decimal_places',
      JSON.stringify({ enabled: true, places: 4 })
    );
  });
  afterEach(() => {
    setDecimalPlacesOverride(null);
    localStorage.removeItem('open_finance_number_format');
    localStorage.removeItem('open_finance_decimal_places');
  });

  it('updates search, budget, receipt and currency amounts without losing their source digits', async () => {
    const result = {
      amount: Number(exact),
      currency: 'EUR',
      exactValues: { amount: exact },
    } as SearchResult;
    renderWithProviders(
      <>
        <Controls />
        <section aria-label="Search">
          <SearchResultAmount result={result} />
        </section>
        <section aria-label="Budget">
          <BudgetCard
            budget={
              {
                budgetId: 1,
                categoryName: 'Food',
                currency: 'EUR',
                budgeted: Number(exact),
                spent: 0,
                remaining: Number(exact),
                period: 'MONTHLY',
                status: 'ON_TRACK',
                exactValues: { budgeted: exact, remaining: exact },
              } as BudgetProgressResponse
            }
            onEdit={vi.fn()}
            onDelete={vi.fn()}
          />
        </section>
        <section aria-label="Receipt">
          <TransferReceipt
            transaction={
              {
                amount: 2,
                currency: 'USD',
                transferCurrency: 'EUR',
                transferAmount: Number(exact),
                exactValues: { transferAmount: exact },
              } as Transaction
            }
          />
        </section>
        <section aria-label="JPY">
          <CurrencyAmount amount={exact} currency="JPY" isConverted={false} />
        </section>
        <section aria-label="BTC">
          <CurrencyAmount amount={exact} currency="BTC" isConverted={false} />
        </section>
      </>
    );
    const user = userEvent.setup();
    for (const [button, expected] of [
      [null, '1.4753'],
      ['Two decimals', '1.48'],
      ['Four decimals', '1.4753'],
    ] as const) {
      if (button) await user.click(screen.getByRole('button', { name: button }));
      for (const label of ['Search', 'Budget', 'Receipt', 'JPY', 'BTC']) {
        expect(screen.getByRole('region', { name: label })).toHaveTextContent(expected);
      }
    }
    await user.click(screen.getByRole('button', { name: 'Currency defaults' }));
    expect(screen.getByRole('region', { name: 'Search' })).toHaveTextContent('€1.48');
    expect(screen.getByRole('region', { name: 'JPY' })).toHaveTextContent('¥1');
    expect(screen.getByRole('region', { name: 'BTC' })).toHaveTextContent('₿1.47535000');
    expect(result.exactValues?.amount).toBe(exact);
  });

  it.each(['native', 'base', 'both'] as const)(
    'retains exact native, base and secondary values in %s mode',
    mode => {
      state.mode = mode;
      state.secondary = 'GBP';
      renderWithProviders(
        <section aria-label="Converted">
          <ConvertedAmount
            amount="1.234449999999999999"
            currency="USD"
            convertedAmount={exact}
            baseCurrency="EUR"
            isConverted
            showComparisons
            recordedAmounts={[{ amount: '2.345649999999999999', currency: 'GBP' }]}
          />
        </section>
      );
      const region = screen.getByRole('region', { name: 'Converted' });
      expect(region).toHaveTextContent('$1.2344');
      expect(region).toHaveTextContent('€1.4753');
      expect(region).toHaveTextContent('£2.3456');
    }
  );

  it('uses an exact supplied FX rate without an intermediate numeric round trip', () => {
    renderWithProviders(
      <section aria-label="FX">
        <CurrencyAmount
          amount="1"
          currency="USD"
          baseCurrency="EUR"
          exchangeRate={exact}
          isConverted
        />
      </section>
    );
    expect(within(screen.getByRole('region', { name: 'FX' })).getByText('€1.4753')).toBeVisible();
  });
});
