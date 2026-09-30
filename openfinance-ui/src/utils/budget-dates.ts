import { addMonths, addWeeks, addYears, format, isValid, parseISO, subDays } from 'date-fns';
import type { BudgetPeriod } from '@/types/budget';

/** Budget ranges include both endpoints. Parse date-only inputs in local time. */
export function budgetPeriodEnd(startDate: string, period: BudgetPeriod): string {
  const start = parseISO(startDate);
  if (!isValid(start)) return '';
  const nextStart =
    period === 'WEEKLY'
      ? addWeeks(start, 1)
      : period === 'YEARLY'
        ? addYears(start, 1)
        : addMonths(start, period === 'QUARTERLY' ? 3 : 1);
  return format(subDays(nextStart, 1), 'yyyy-MM-dd');
}
