import { format } from 'date-fns';
import { add, subtract, divide } from '@/utils/money';
import type { BudgetSummaryResponse } from '@/types/budget';

/** All dashboard figures must describe the same currently active dated budgets. */
export function activeBudgetSummary(
  summary: BudgetSummaryResponse,
  today = format(new Date(), 'yyyy-MM-dd')
): BudgetSummaryResponse {
  const budgets = summary.budgets.filter(b => b.startDate <= today && b.endDate >= today);
  const totalBudgeted = budgets.reduce((total, b) => add(total, b.budgeted), 0);
  const totalSpent = budgets.reduce((total, b) => add(total, b.spent), 0);
  return {
    ...summary,
    budgets,
    totalBudgets: budgets.length,
    activeBudgets: budgets.length,
    totalBudgeted,
    totalSpent,
    totalRemaining: subtract(totalBudgeted, totalSpent),
    averageSpentPercentage: budgets.length
      ? divide(
          budgets.reduce((total, b) => add(total, b.percentageSpent), 0),
          budgets.length
        )
      : 0,
  };
}
