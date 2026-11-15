import { beforeEach, describe, expect, it, vi } from 'vitest';
import { fireEvent, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { renderWithProviders, mockAuthentication } from '@/test/test-utils';
import { FinancialFreedomCalculator } from '@/components/financial-freedom/FinancialFreedomCalculator';
import i18n from '@/test/i18n-test';

const query = vi.hoisted(() => ({ loading: false, error: null as string | null, missing: false }));
const financialData = vi.hoisted(() => ({
  totalSavings: 100000,
  averageMonthlyExpenses: 1000,
  currency: 'EUR',
  exactValues: {} as Record<string, string>,
}));

vi.mock('@/hooks/useUserFinancialData', () => ({
  useUserFinancialData: () => ({
    data: query.missing ? undefined : financialData,
    isLoading: query.loading,
    error: query.error,
  }),
}));
const tr = (key: string) => i18n.t(`financialFreedom.${key}`, { ns: 'tools' });
beforeEach(async () => {
  financialData.exactValues = {};
  query.loading = false;
  query.error = null;
  query.missing = false;
  mockAuthentication();
  await i18n.changeLanguage('en');
});

describe('financial freedom calculation workflow', () => {
  it.each([
    ['currentSavings', '0'],
    ['monthlyExpenses', '0'],
    ['monthlyContribution', '0'],
    ['expectedReturn', '0'],
    ['withdrawalRate', '4'],
    ['inflationRate', '2.5'],
  ])('clearing %s restores a usable calculation value', (field, expected) => {
    renderWithProviders(<FinancialFreedomCalculator />);
    const input = screen.getByLabelText(tr(`calculator.fields.${field}.label`));
    fireEvent.change(input, { target: { value: '6' } });
    fireEvent.change(input, { target: { value: '' } });
    fireEvent.blur(input);
    expect(input).toHaveValue(expected);
  });
  it('accepts custom withdrawal and inflation rates and toggles real-return projections', async () => {
    renderWithProviders(<FinancialFreedomCalculator />);
    fireEvent.change(screen.getByLabelText(tr('calculator.fields.withdrawalRate.label')), {
      target: { value: '3.5' },
    });
    fireEvent.change(screen.getByLabelText(tr('calculator.fields.inflationRate.label')), {
      target: { value: '3' },
    });
    const inflation = screen.getByRole('switch', {
      name: tr('calculator.fields.adjustForInflation'),
    });
    await userEvent.click(inflation);
    expect(inflation).toBeChecked();
    await userEvent.click(screen.getByRole('button', { name: tr('calculator.calculate') }));
    expect(await screen.findByText(tr('results.timeToFreedom'))).toBeVisible();
  });
  it.each(['loading', 'failed', 'missing'])(
    'keeps manual entry usable when actual data is %s',
    state => {
      query.loading = state === 'loading';
      query.error = state === 'failed' ? 'offline' : null;
      query.missing = state !== 'loading';
      renderWithProviders(<FinancialFreedomCalculator />);
      expect(screen.getByLabelText(tr('calculator.fields.currentSavings.label'))).toBeEnabled();
      if (state === 'loading') {
        expect(screen.getByText(tr('calculator.loadingData'))).toBeVisible();
        expect(
          screen.getByRole('button', { name: tr('calculator.useActualData.button') })
        ).toBeDisabled();
      } else {
        expect(
          screen.queryByRole('button', { name: tr('calculator.useActualData.button') })
        ).not.toBeInTheDocument();
        if (state === 'failed')
          expect(screen.getByText(tr('calculator.dataLoadError'))).toBeVisible();
      }
    }
  );
  it('uses actual account data, recalculates after edits, and resets the form', async () => {
    renderWithProviders(<FinancialFreedomCalculator />);
    await userEvent.click(
      screen.getByRole('button', { name: tr('calculator.useActualData.button') })
    );
    expect(screen.getByLabelText(tr('calculator.fields.currentSavings.label'))).toHaveValue(
      '100,000'
    );
    fireEvent.change(screen.getByLabelText(tr('calculator.fields.monthlyContribution.label')), {
      target: { value: '1500' },
    });
    await userEvent.click(screen.getByRole('button', { name: tr('calculator.calculate') }));
    await waitFor(() => expect(screen.getByText(tr('results.timeToFreedom'))).toBeInTheDocument());
    expect(screen.getByText(tr('results.targetAmount.title'))).toBeInTheDocument();
    expect(screen.getAllByText(tr('results.achievable')).length).toBeGreaterThan(0);
    await userEvent.click(screen.getByRole('button', { name: tr('calculator.reset') }));
    expect(screen.queryByText(tr('results.timeToFreedom'))).not.toBeInTheDocument();
  });
  it('shows an unreachable target when there is no return or monthly contribution', async () => {
    renderWithProviders(<FinancialFreedomCalculator />);
    for (const field of ['currentSavings', 'expectedReturn', 'monthlyContribution']) {
      fireEvent.change(screen.getByLabelText(tr(`calculator.fields.${field}.label`)), {
        target: { value: '0' },
      });
    }
    await userEvent.click(screen.getByRole('button', { name: tr('calculator.calculate') }));
    expect(await screen.findByText(tr('results.notAchievable'))).toBeInTheDocument();
  });
  it('autofills exact balances and expenses instead of their rounded numeric mirrors', async () => {
    financialData.exactValues = {
      totalSavings: '100000.000000000000000001',
      averageMonthlyExpenses: '1000.000000000000000001',
    };
    renderWithProviders(<FinancialFreedomCalculator />);
    await userEvent.click(
      screen.getByRole('button', { name: tr('calculator.useActualData.button') })
    );
    expect(screen.getByLabelText(tr('calculator.fields.currentSavings.label'))).toHaveValue(
      '100,000.000000000000000001'
    );
    expect(screen.getByLabelText(tr('calculator.fields.monthlyExpenses.label'))).toHaveValue(
      '1,000.000000000000000001'
    );
  });
});
