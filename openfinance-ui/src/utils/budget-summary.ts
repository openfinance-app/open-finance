import { format } from 'date-fns';
import { add, divide } from '@/utils/money';
import { decimalValue, ExactDecimal, exactSum } from '@/utils/exact-decimal';
import type { BudgetSummaryResponse } from '@/types/budget';

/** All dashboard figures must describe the same currently active dated budgets. */
export function activeBudgetSummary(
  summary: BudgetSummaryResponse,
  today = format(new Date(), 'yyyy-MM-dd')
): BudgetSummaryResponse {
  const budgets = summary.budgets.filter(b => b.startDate <= today && b.endDate >= today);
  const measured = budgets.filter(b => b.percentageSpent != null);
  const totalBudgeted = exactSum(budgets.map(b => decimalValue(b, 'budgeted') ?? 0));
  const totalSpent = exactSum(budgets.map(b => decimalValue(b, 'spent') ?? 0));
  const totalRemaining = new ExactDecimal(totalBudgeted).minus(totalSpent).toFixed();
  return {
    ...summary,
    budgets,
    totalBudgets: budgets.length,
    activeBudgets: budgets.length,
    totalBudgeted: Number(totalBudgeted),
    totalSpent: Number(totalSpent),
    totalRemaining: Number(totalRemaining),
    exactValues: { ...summary.exactValues, totalBudgeted, totalSpent, totalRemaining },
    averageSpentPercentage: measured.length
      ? divide(
          measured.reduce((total, b) => add(total, b.percentageSpent ?? 0), 0),
          measured.length
        )
      : 0,
  };
}
