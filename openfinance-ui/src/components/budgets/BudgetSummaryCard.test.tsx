import { beforeEach, describe, expect, it } from 'vitest';
import { screen, within } from '@testing-library/react';
import { renderWithProviders, mockAuthentication } from '@/test/test-utils';
import { BudgetSummaryCard } from '@/components/budgets/BudgetSummaryCard';
import type { BudgetSummaryResponse, BudgetProgressResponse } from '@/types/budget';
import i18n from '@/test/i18n-test';

const tr = (key: string) => i18n.t(`budgets:summary.${key}`);
const budget = (id: number, budgeted: number, spent: number): BudgetProgressResponse => ({
  budgetId: id,
  categoryName: 'Food',
  budgeted,
  spent,
  remaining: budgeted - spent,
  percentageSpent: (spent / budgeted) * 100,
  currency: 'USD',
  period: 'MONTHLY',
  startDate: '2026-01-01',
  endDate: '2026-01-31',
  daysRemaining: 10,
  status: spent > budgeted ? 'EXCEEDED' : 'ON_TRACK',
});
const summary = (): BudgetSummaryResponse => ({
  period: 'MONTHLY',
  totalBudgets: 2,
  activeBudgets: 2,
  totalBudgeted: 1000,
  totalSpent: 600,
  totalRemaining: 400,
  averageSpentPercentage: 55,
  currency: 'USD',
  budgets: [budget(1, 100, 120), budget(2, 900, 480)],
});
beforeEach(async () => {
  mockAuthentication();
  await i18n.changeLanguage('en');
});

describe('budget summary', () => {
  it('keeps overall totals visible while showing a filtered overspent category', () => {
    renderWithProviders(
      <BudgetSummaryCard
        summary={summary()}
        listedBudgetCount={5}
        filteredBudgets={[budget(1, 100, 120)]}
      />
    );
    const budgeted = screen.getByText(tr('totalBudgeted')).parentElement!.parentElement!;
    expect(within(budgeted).getByText('$1,000.00')).toBeInTheDocument();
    expect(within(budgeted).getByText('$100.00')).toBeInTheDocument();
    expect(
      within(budgeted).getByText(i18n.t('budgets:summary.activeCount', { active: 2, total: 5 }))
    ).toBeInTheDocument();
    const spent = screen.getByText(tr('totalSpent')).parentElement!.parentElement!;
    expect(spent).toHaveTextContent('60.0%');
    expect(spent).toHaveTextContent('120.0%');
    expect(screen.getByText(tr('avgSpent')).parentElement!.parentElement!).toHaveTextContent(
      '55.0%'
    );
    const remaining = screen.getByText(tr('remaining')).parentElement!.parentElement!;
    expect(remaining).toHaveTextContent('$400.00');
    expect(remaining).toHaveTextContent('$20.00');
    expect(remaining).toHaveTextContent(tr('over'));
  });
  it('shows the overspend magnitude and does not duplicate unfiltered totals', () => {
    const data = {
      ...summary(),
      totalSpent: 1200,
      totalRemaining: -200,
      averageSpentPercentage: 120,
    };
    renderWithProviders(<BudgetSummaryCard summary={data} filteredBudgets={data.budgets} />);
    expect(screen.getByText(tr('overBudget')).parentElement!.parentElement!).toHaveTextContent(
      '$200.00'
    );
    expect(screen.queryByText(tr('filtered'), { exact: false })).not.toBeInTheDocument();
  });
  it('renders an empty current period without NaN or infinity percentages', () => {
    const { container } = renderWithProviders(
      <BudgetSummaryCard
        summary={{
          ...summary(),
          totalBudgets: 0,
          activeBudgets: 0,
          totalBudgeted: 0,
          totalSpent: 0,
          totalRemaining: 0,
          averageSpentPercentage: 0,
          budgets: [],
        }}
        filteredBudgets={[]}
      />
    );
    expect(screen.getByText(tr('noActiveBudgets'))).toBeInTheDocument();
    expect(container).not.toHaveTextContent(/NaN|Infinity/);
  });
});
