import type { DateRange } from '@/components/ui/PeriodSelector';
import type { ICashflowSankeyNode } from '@/types/dashboard';
import { previousCustomPeriod } from '@/utils/dashboard-period';
import { percentage, subtract } from '@/utils/money';

export interface ComparedFlowNode extends ICashflowSankeyNode {
  previousAmount?: number;
}

/** Calendar-aware fallback for standalone custom-range diagrams. */
export function previousSankeyPeriod(range: DateRange): DateRange {
  return previousCustomPeriod(range);
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
