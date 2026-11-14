import { ExactDecimal, decimalValue, exactSum } from '@/utils/exact-decimal';
/**
 * BudgetSummaryCard Component
 * TASK-8.2.7: Create budget summary card for BudgetsPage
 *
 * Displays current budget statistics at the top of the budgets page.
 * Shows global totals prominently and, when a filter is active, also shows
 * filtered sub-totals in a smaller secondary line below each metric.
 */

import { useMemo } from 'react';
import { useTranslation } from 'react-i18next';
import { Card, CardHeader, CardTitle, CardContent } from '@/components/ui/Card';
import { ConvertedAmount } from '@/components/ui/ConvertedAmount';
import { useSecondaryConversion } from '@/hooks/useSecondaryConversion';
import { formatPercentage } from '@/utils/format';
import { percentage } from '@/utils/money';
import { TrendingUp, TrendingDown, DollarSign, Target } from 'lucide-react';
import type { BudgetProgressResponse, BudgetSummaryResponse } from '@/types/budget';
import { cn } from '@/lib/utils';

interface BudgetSummaryCardProps {
  /** Current budgets for the selected period, excluding expired and future allowances. */
  summary: BudgetSummaryResponse;
  /** Number of listed budgets, including expired and future budgets. */
  listedBudgetCount?: number;
  /** Current budgets matching the list filters — drives secondary sub-totals. */
  filteredBudgets?: BudgetProgressResponse[];
}

export function BudgetSummaryCard({
  summary,
  listedBudgetCount = summary.totalBudgets,
  filteredBudgets,
}: BudgetSummaryCardProps) {
  const { t } = useTranslation('budgets');
  const displayCurrency = summary.currency;
  const {
    convert: convertNumber,
    convertExact: convert = convertNumber,
    secondaryCurrency: secCurrency,
    secondaryExchangeRate,
  } = useSecondaryConversion(displayCurrency);
  const isOverBudget = summary.totalSpent > summary.totalBudgeted;
  // Actual spent % for the "Total Spent" card (aggregate ratio)
  const spentPercentage = percentage(summary.totalSpent, summary.totalBudgeted);
  // Average of individual budget percentages for the "Avg. Spent" card
  const avgSpentPercentage = summary.averageSpentPercentage;

  // Derive filtered totals when a subset is provided and differs from the full set
  const isFiltered =
    filteredBudgets !== undefined && filteredBudgets.length !== summary.budgets.length;

  const filteredTotals = useMemo(() => {
    if (!filteredBudgets) return null;
    const filteredTotalBudgeted = exactSum(
      filteredBudgets.map(b => decimalValue(b, 'budgeted') ?? 0)
    );
    const filteredTotalSpent = exactSum(filteredBudgets.map(b => decimalValue(b, 'spent') ?? 0));
    const filteredTotalRemaining = new ExactDecimal(filteredTotalBudgeted)
      .minus(filteredTotalSpent)
      .toFixed();
    const filteredSpentPct = percentage(filteredTotalSpent, filteredTotalBudgeted);
    return { filteredTotalBudgeted, filteredTotalSpent, filteredTotalRemaining, filteredSpentPct };
  }, [filteredBudgets]);

  return (
    <Card className="mb-6">
      <CardHeader>
        <CardTitle>{t('summary.title')}</CardTitle>
      </CardHeader>
      <CardContent>
        {summary.activeBudgets === 0 && (
          <p className="mb-5 text-sm text-text-secondary">{t('summary.noActiveBudgets')}</p>
        )}
        <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-4 gap-6">
          {/* Total Budgeted */}
          <div className="space-y-2">
            <div className="flex items-center gap-2 text-text-secondary">
              <Target className="h-4 w-4" />
              <span className="text-sm font-medium">{t('summary.totalBudgeted')}</span>
            </div>
            <p className="text-2xl font-bold font-mono text-text-primary">
              <ConvertedAmount
                amount={decimalValue(summary, 'totalBudgeted') ?? summary.totalBudgeted}
                currency={displayCurrency}
                isConverted={false}
                secondaryAmount={convert(
                  decimalValue(summary, 'totalBudgeted') ?? summary.totalBudgeted
                )}
                secondaryCurrency={secCurrency}
                secondaryExchangeRate={secondaryExchangeRate}
                inline
                animate
              />
            </p>
            <p className="text-xs text-text-tertiary">
              {t('summary.activeCount', {
                active: summary.activeBudgets,
                total: listedBudgetCount,
              })}
            </p>
            {isFiltered && filteredTotals && (
              <p className="text-xs font-mono text-text-tertiary">
                {t('summary.filtered')}{' '}
                <ConvertedAmount
                  amount={
                    decimalValue(filteredTotals, 'filteredTotalBudgeted') ??
                    filteredTotals.filteredTotalBudgeted
                  }
                  currency={displayCurrency}
                  isConverted={false}
                  secondaryAmount={convert(
                    decimalValue(filteredTotals, 'filteredTotalBudgeted') ??
                      filteredTotals.filteredTotalBudgeted
                  )}
                  secondaryCurrency={secCurrency}
                  secondaryExchangeRate={secondaryExchangeRate}
                  inline
                />
                <span className="ml-1">
                  {t('summary.budgetsCount', { count: filteredBudgets!.length })}
                </span>
              </p>
            )}
          </div>

          {/* Total Spent */}
          <div className="space-y-2">
            <div className="flex items-center gap-2 text-text-secondary">
              <DollarSign className="h-4 w-4" />
              <span className="text-sm font-medium">{t('summary.totalSpent')}</span>
            </div>
            <p className="text-2xl font-bold font-mono text-text-primary">
              <ConvertedAmount
                amount={decimalValue(summary, 'totalSpent') ?? summary.totalSpent}
                currency={displayCurrency}
                isConverted={false}
                secondaryAmount={convert(decimalValue(summary, 'totalSpent') ?? summary.totalSpent)}
                secondaryCurrency={secCurrency}
                secondaryExchangeRate={secondaryExchangeRate}
                inline
                animate
              />
            </p>
            <p className="text-xs text-text-tertiary">
              {formatPercentage(spentPercentage, 1)} {t('summary.ofBudget')}
            </p>
            {isFiltered && filteredTotals && (
              <p className="text-xs font-mono text-text-tertiary">
                {t('summary.filtered')}{' '}
                <ConvertedAmount
                  amount={
                    decimalValue(filteredTotals, 'filteredTotalSpent') ??
                    filteredTotals.filteredTotalSpent
                  }
                  currency={displayCurrency}
                  isConverted={false}
                  secondaryAmount={convert(
                    decimalValue(filteredTotals, 'filteredTotalSpent') ??
                      filteredTotals.filteredTotalSpent
                  )}
                  secondaryCurrency={secCurrency}
                  secondaryExchangeRate={secondaryExchangeRate}
                  inline
                />{' '}
                ({formatPercentage(filteredTotals.filteredSpentPct, 1)})
              </p>
            )}
          </div>

          {/* Remaining */}
          <div className="space-y-2">
            <div className="flex items-center gap-2 text-text-secondary">
              {isOverBudget ? (
                <TrendingDown className="h-4 w-4" />
              ) : (
                <TrendingUp className="h-4 w-4" />
              )}
              <span className="text-sm font-medium">
                {isOverBudget ? t('summary.overBudget') : t('summary.remaining')}
              </span>
            </div>
            <p
              className={cn(
                'text-2xl font-bold font-mono',
                isOverBudget ? 'text-error' : 'text-success'
              )}
            >
              <ConvertedAmount
                amount={new ExactDecimal(
                  decimalValue(summary, 'totalRemaining') ?? summary.totalRemaining
                )
                  .abs()
                  .toFixed()}
                currency={displayCurrency}
                isConverted={false}
                secondaryAmount={convert(
                  new ExactDecimal(
                    decimalValue(summary, 'totalRemaining') ?? summary.totalRemaining
                  )
                    .abs()
                    .toFixed()
                )}
                secondaryCurrency={secCurrency}
                secondaryExchangeRate={secondaryExchangeRate}
                inline
                animate
              />
            </p>
            <p className="text-xs text-text-tertiary">
              {isOverBudget ? t('summary.exceededBudget') : t('summary.availableToSpend')}
            </p>
            {isFiltered && filteredTotals && (
              <p className={cn('text-xs font-mono text-text-tertiary')}>
                {t('summary.filtered')}{' '}
                <ConvertedAmount
                  amount={new ExactDecimal(
                    decimalValue(filteredTotals, 'filteredTotalRemaining') ??
                      filteredTotals.filteredTotalRemaining
                  )
                    .abs()
                    .toFixed()}
                  currency={displayCurrency}
                  isConverted={false}
                  secondaryAmount={convert(
                    new ExactDecimal(
                      decimalValue(filteredTotals, 'filteredTotalRemaining') ??
                        filteredTotals.filteredTotalRemaining
                    )
                      .abs()
                      .toFixed()
                  )}
                  secondaryCurrency={secCurrency}
                  secondaryExchangeRate={secondaryExchangeRate}
                  inline
                />
                {new ExactDecimal(filteredTotals.filteredTotalRemaining).lt(0)
                  ? ` ${t('summary.over')}`
                  : ` ${t('summary.remainingLabel')}`}
              </p>
            )}
          </div>

          {/* Average Spent */}
          <div className="space-y-2">
            <div className="flex items-center gap-2 text-text-secondary">
              <Target className="h-4 w-4" />
              <span className="text-sm font-medium">{t('summary.avgSpent')}</span>
            </div>
            <p className="text-2xl font-bold font-mono text-text-primary">
              {formatPercentage(avgSpentPercentage, 1)}
            </p>
            <div className="h-1.5 bg-surface-elevated rounded-full overflow-hidden">
              <div
                className={cn(
                  'h-full rounded-full progress-fill',
                  avgSpentPercentage < 75
                    ? 'bg-success'
                    : avgSpentPercentage < 100
                      ? 'bg-warning'
                      : 'bg-error'
                )}
                style={{ transform: `scaleX(${Math.min(avgSpentPercentage, 100) / 100})` }}
              />
            </div>
            {isFiltered && filteredTotals && (
              <p className="text-xs font-mono text-text-tertiary">
                {t('summary.filtered')} {formatPercentage(filteredTotals.filteredSpentPct, 1)}
              </p>
            )}
          </div>
        </div>
      </CardContent>
    </Card>
  );
}
