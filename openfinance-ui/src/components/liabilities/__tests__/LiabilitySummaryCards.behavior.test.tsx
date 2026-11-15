import { beforeEach, describe, expect, it, vi } from 'vitest';
import { screen, within } from '@testing-library/react';
import { LiabilitySummaryCards } from '@/components/liabilities/LiabilitySummaryCards';
import type { Liability } from '@/types/liability';
import { mockAuthentication, renderWithProviders } from '@/test/test-utils';
import i18n from '@/test/i18n-test';

vi.mock('@/context/AuthContext', async original => ({
  ...(await original<typeof import('@/context/AuthContext')>()),
  useAuthContext: () => ({ baseCurrency: 'EUR' }),
}));
const debt: Liability = {
  id: 1,
  name: 'Loan',
  type: 'PERSONAL_LOAN',
  currency: 'EUR',
  principal: 1000,
  currentBalance: 100,
  interestRate: 4,
  minimumPayment: 10,
  startDate: '2025-01-01',
  createdAt: '',
  updatedAt: '',
};
function card(key: string) {
  return screen.getByText(i18n.t(`summary.${key}`, { ns: 'liabilities' })).parentElement!;
}

describe('Debt totals distinguish known payments, missing rates and filtered amounts', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    mockAuthentication();
  });

  it.each([
    [undefined, undefined, 'incomplete'],
    [undefined, 0, 'incomplete'],
    [undefined, -10, 'incomplete'],
    [undefined, 10, '10.00'],
    [0, 10, '0.00'],
    [15, 10, '15.00'],
    [null, 10, 'incomplete'],
  ] as const)(
    'uses effective payment %s ahead of minimum %s without masking unknown amounts',
    (effectiveMonthlyPayment, minimumPayment, expected) => {
      renderWithProviders(
        <LiabilitySummaryCards
          liabilities={[{ ...debt, effectiveMonthlyPayment, minimumPayment }]}
        />
      );
      expect(card('monthlyPayments')).toHaveTextContent(
        expected === 'incomplete'
          ? i18n.t('summary.incompletePayments', { ns: 'liabilities' })
          : `€${expected}`
      );
      if (expected === 'incomplete')
        expect(card('monthlyPayments')).toHaveTextContent(
          i18n.t('summary.missingPayments', { ns: 'liabilities', count: 1 })
        );
    }
  );

  it.each([0, -10])(
    'a settled or credit-balance debt (%s) has no upcoming payment',
    currentBalance => {
      renderWithProviders(
        <LiabilitySummaryCards
          liabilities={[
            {
              ...debt,
              currentBalance,
              effectiveMonthlyPayment: undefined,
              minimumPayment: undefined,
            },
          ]}
        />
      );
      expect(card('monthlyPayments')).toHaveTextContent('€0.00');
      expect(card('monthlyPayments')).toHaveTextContent(
        i18n.t('summary.noPaymentsSet', { ns: 'liabilities' })
      );
    }
  );

  it.each([undefined, 120])(
    'converts a foreign balance using the recorded value %s or the rate',
    balanceInBaseCurrency => {
      renderWithProviders(
        <LiabilitySummaryCards
          liabilities={[
            {
              ...debt,
              currency: 'USD',
              isConverted: true,
              exchangeRate: 1.2,
              balanceInBaseCurrency,
            },
          ]}
        />
      );
      expect(card('totalLiabilities')).toHaveTextContent('€120.00');
      expect(card('originalPrincipal')).toHaveTextContent('€1,200.00');
      expect(card('monthlyPayments')).toHaveTextContent('€12.00');
      expect(card('avgInterestRate')).toHaveTextContent('4.00%');
    }
  );

  it.each([false, true, undefined])(
    'excludes foreign liabilities without a usable rate (converted=%s)',
    isConverted => {
      renderWithProviders(
        <LiabilitySummaryCards
          liabilities={[
            debt,
            { ...debt, id: 2, currency: 'USD', isConverted, exchangeRate: undefined },
            { ...debt, id: 3, currency: 'GBP' },
          ]}
        />
      );
      expect(card('totalLiabilities')).toHaveTextContent('€100.00');
      expect(card('totalLiabilities')).toHaveTextContent('Excl. USD, GBP (no rate)');
      expect(card('originalPrincipal')).toHaveTextContent('€1,000.00');
      expect(card('monthlyPayments')).toHaveTextContent('€10.00');
    }
  );

  it('preserves exact base-currency totals and payments through conversion and summation', () => {
    renderWithProviders(
      <LiabilitySummaryCards
        liabilities={[
          {
            ...debt,
            currentBalance: 9007199254740992,
            principal: 9007199254740992,
            minimumPayment: 9007199254740992,
            exactValues: {
              currentBalance: '9007199254740992.01',
              principal: '9007199254740992.01',
              minimumPayment: '9007199254740992.01',
            },
          },
          {
            ...debt,
            id: 2,
            currency: 'USD',
            isConverted: true,
            exchangeRate: 0.5,
            currentBalance: 0.02,
            principal: 0.02,
            effectiveMonthlyPayment: 0.02,
            exactValues: {
              currentBalance: '0.02',
              principal: '0.02',
              effectiveMonthlyPayment: '0.02',
              exchangeRate: '0.5',
            },
          },
        ]}
      />
    );
    for (const key of ['totalLiabilities', 'originalPrincipal', 'monthlyPayments'])
      expect(card(key)).toHaveTextContent('€9,007,199,254,740,992.02');
  });

  it('weights interest by converted debt and omits interest-free balances from that average', () => {
    renderWithProviders(
      <LiabilitySummaryCards
        liabilities={[
          debt,
          { ...debt, id: 2, currency: 'USD', isConverted: true, exchangeRate: 2, interestRate: 10 },
          { ...debt, id: 3, currentBalance: 10000, interestRate: 0 },
        ]}
      />
    );
    expect(card('avgInterestRate')).toHaveTextContent('8.00%');
    expect(card('avgInterestRate')).toHaveTextContent(
      i18n.t('summary.withInterest', { ns: 'liabilities', count: 2 })
    );
  });

  it.each([
    { liabilities: [] },
    { liabilities: [{ ...debt, interestRate: 0 }] },
    { liabilities: [{ ...debt, currentBalance: 0 }] },
  ])('does not divide by zero when no positive balance bears interest', ({ liabilities }) => {
    renderWithProviders(<LiabilitySummaryCards liabilities={liabilities} />);
    expect(card('avgInterestRate')).toHaveTextContent('N/A');
    expect(card('avgInterestRate')).not.toHaveTextContent(/NaN|Infinity/);
  });

  it.each([undefined, false, true])(
    'uses the explicit filter state %s even when result counts match',
    isActiveFilter => {
      renderWithProviders(
        <LiabilitySummaryCards
          liabilities={[debt]}
          filteredLiabilities={[
            { ...debt, currentBalance: 25, principal: 50, minimumPayment: undefined },
          ]}
          isActiveFilter={isActiveFilter}
        />
      );
      expect(card('totalLiabilities')).toHaveTextContent('€100.00');
      if (isActiveFilter === true)
        expect(within(card('totalLiabilities')).getByText('€25.00')).toBeInTheDocument();
      else expect(within(card('totalLiabilities')).queryByText('€25.00')).not.toBeInTheDocument();
    }
  );

  it('shows the empty filtered set without erasing the global totals', () => {
    renderWithProviders(<LiabilitySummaryCards liabilities={[debt]} filteredLiabilities={[]} />);
    expect(card('totalLiabilities')).toHaveTextContent('€100.00');
    expect(card('totalLiabilities')).toHaveTextContent('€0.00');
    expect(card('avgInterestRate')).toHaveTextContent('N/A');
    expect(card('monthlyPayments')).toHaveTextContent('€0.00');
  });

  it('marks a filtered payment total as incomplete independently of global display', () => {
    const unknown = { ...debt, id: 2, minimumPayment: undefined };
    renderWithProviders(
      <LiabilitySummaryCards liabilities={[debt, unknown]} filteredLiabilities={[unknown]} />
    );
    expect(
      within(card('monthlyPayments')).getAllByText(
        i18n.t('summary.incompletePayments', { ns: 'liabilities' }),
        { exact: false }
      )
    ).toHaveLength(2);
  });
});
