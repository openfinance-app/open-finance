import { describe, expect, it } from 'vitest';
import { budgetPeriodEnd } from '@/utils/budget-dates';
import type { BudgetPeriod } from '@/types/budget';

describe('inclusive budget periods', () => {
  it.each<[string, BudgetPeriod, string]>([
    ['2026-09-21', 'WEEKLY', '2026-09-27'],
    ['2026-09-01', 'MONTHLY', '2026-09-30'],
    ['2026-01-31', 'MONTHLY', '2026-02-27'],
    ['2028-01-31', 'MONTHLY', '2028-02-28'],
    ['2026-07-01', 'QUARTERLY', '2026-09-30'],
    ['2026-01-01', 'YEARLY', '2026-12-31'],
    ['2028-02-29', 'YEARLY', '2029-02-27'],
    ['2026-03-28', 'WEEKLY', '2026-04-03'],
  ])('%s %s ends on %s', (start, period, end) => {
    expect(budgetPeriodEnd(start, period)).toBe(end);
  });

  it('ignores incomplete date input', () => {
    expect(budgetPeriodEnd('', 'MONTHLY')).toBe('');
  });
});
