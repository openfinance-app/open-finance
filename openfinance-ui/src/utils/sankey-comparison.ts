import { differenceInCalendarDays, parseISO, subDays } from 'date-fns';
import type { DateRange } from '@/components/ui/PeriodSelector';
import type { ICashflowSankeyNode } from '@/types/dashboard';
import { toLocalISODate } from '@/utils/date';
import { percentage, subtract } from '@/utils/money';

export interface ComparedFlowNode extends ICashflowSankeyNode {
  previousAmount?: number;
}

/** Inclusive, adjacent ranges of the same length, including across DST and leap days. */
export function previousSankeyPeriod(range: DateRange): DateRange {
  const start = parseISO(range.from);
  const days = differenceInCalendarDays(parseISO(range.to), start) + 1;
  return {
    from: toLocalISODate(subDays(start, days)),
    to: toLocalISODate(subDays(start, 1)),
  };
}

/** Annotate the selected period's categories without introducing flows from another period. */
export function compareFlowNodes(
  current: ICashflowSankeyNode[],
  previous?: ICashflowSankeyNode[]
): ComparedFlowNode[] {
  if (!previous) return current;
  const previousAmounts = new Map(previous.map(node => [node.categoryId ?? null, node.amount]));
  return current.map(node => ({
    ...node,
    previousAmount: previousAmounts.get(node.categoryId ?? null) ?? 0,
  }));
}

export function flowVariation(
  amount: number,
  previousAmount: number
): { change: number; percent: number | null } {
  const change = subtract(amount, previousAmount);
  return {
    change,
    // A percentage increase from zero is undefined, rather than 0% or 100%.
    percent:
      previousAmount === 0 ? (amount === 0 ? 0 : null) : percentage(change, previousAmount, 1),
  };
}
