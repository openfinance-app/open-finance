import { describe, expect, it } from 'vitest';
import { activeBudgetSummary } from '@/utils/budget-summary';
import type { BudgetProgressResponse, BudgetSummaryResponse } from '@/types/budget';

const budget = (
  id: number,
  start: string,
  end: string,
  amount: number,
  spent: number
): BudgetProgressResponse => ({
  budgetId: id,
  categoryName: `Category ${id}`,
  period: 'MONTHLY',
  currency: 'EUR',
  startDate: start,
  endDate: end,
  budgeted: amount,
  spent,
  remaining: amount - spent,
  percentageSpent: (spent / amount) * 100,
  daysRemaining: 0,
  status: 'ON_TRACK',
});

describe('current dashboard budget population', () => {
  it('includes both endpoints and excludes past and future budgets from every figure', () => {
    const summary: BudgetSummaryResponse = {
      period: null,
      currency: 'EUR',
      totalBudgeted: 0,
      totalSpent: 0,
      totalRemaining: 0,
      averageSpentPercentage: 0,
      totalBudgets: 4,
      activeBudgets: 2,
      budgets: [
        budget(1, '2026-09-01', '2026-09-30', 300, 406.076),
        budget(2, '2026-09-30', '2026-10-29', 12000, 0),
        budget(3, '2026-08-01', '2026-08-31', 280, 180),
        budget(4, '2026-10-01', '2026-10-31', 200, 0),
      ],
    };
    const result = activeBudgetSummary(summary, '2026-09-30');
    expect(result.budgets.map(b => b.budgetId)).toEqual([1, 2]);
    expect(result.totalBudgets).toBe(2);
    expect(result.activeBudgets).toBe(2);
    expect(result.totalBudgeted).toBe(12300);
    expect(result.totalSpent).toBe(406.076);
    expect(result.totalRemaining).toBe(11893.924);
    expect(activeBudgetSummary(summary, '2027-01-01').totalBudgeted).toBe(0);
  });
});
