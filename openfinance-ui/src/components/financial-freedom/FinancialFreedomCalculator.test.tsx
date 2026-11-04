import { beforeEach, describe, expect, it, vi } from 'vitest';
import { fireEvent, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { renderWithProviders, mockAuthentication } from '@/test/test-utils';
import { FinancialFreedomCalculator } from '@/components/financial-freedom/FinancialFreedomCalculator';
import i18n from '@/test/i18n-test';

vi.mock('@/hooks/useUserFinancialData', () => ({
  useUserFinancialData: () => ({
    data: { totalSavings: 100000, averageMonthlyExpenses: 1000, currency: 'EUR' },
    isLoading: false,
    error: null,
  }),
}));
const tr = (key: string) => i18n.t(`financialFreedom.${key}`, { ns: 'tools' });
beforeEach(async () => {
  mockAuthentication();
  await i18n.changeLanguage('en');
});

describe('financial freedom calculation workflow', () => {
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
});
