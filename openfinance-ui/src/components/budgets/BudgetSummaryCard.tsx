/**
 * BudgetSummaryCard Component
 * TASK-8.2.7: Create budget summary card for BudgetsPage
 * 
 * Displays aggregate budget statistics at the top of the budgets page.
 * Shows global totals prominently and, when a filter is active, also shows
 * filtered sub-totals in a smaller secondary line below each metric.
 */

import { useMemo } from 'react';
import { useTranslation } from 'react-i18next';
import { Card, CardHeader, CardTitle, CardContent } from '@/components/ui/Card';
import { ConvertedAmount } from '@/components/ui/ConvertedAmount';
import { useSecondaryConversion } from '@/hooks/useSecondaryConversion';
import { formatPercentage } from '@/utils/format';
import { percentage, sum, subtract } from '@/utils/money';
import { TrendingUp, TrendingDown, DollarSign, Target } from 'lucide-react';
import type { BudgetProgressResponse, BudgetSummaryResponse } from '@/types/budget';
import { cn } from '@/lib/utils';
import { useAuthContext } from '@/context/AuthContext';

interface BudgetSummaryCardProps {
  /** Global budget summary (all budgets for the selected period) */
  summary: BudgetSummaryResponse;
  /** Currently displayed / filtered budget progress items — drives secondary sub-totals */
  filteredBudgets?: BudgetProgressResponse[];
}

export function BudgetSummaryCard({ summary, filteredBudgets }: BudgetSummaryCardProps) {
  const { t } = useTranslation('budgets');
  const { baseCurrency } = useAuthContext();
  const { convert, secondaryCurrency: secCurrency, secondaryExchangeRate } = useSecondaryConversion(baseCurrency);
  const isOverBudget = summary.totalSpent > summary.totalBudgeted;
  // Actual spent % for the "Total Spent" card (aggregate ratio)
  const spentPercentage = percentage(summary.totalSpent, summary.totalBudgeted);
  // Average of individual budget percentages for the "Avg. Spent" card
  const avgSpentPercentage = summary.averageSpentPercentage;

  // Derive filtered totals when a subset is provided and differs from the full set
  const isFiltered =
    filteredBudgets !== undefined &&
    filteredBudgets.length !== summary.budgets.length;

  const filteredTotals = useMemo(() => {
    if (!filteredBudgets) return null;
    const filteredTotalBudgeted = sum(filteredBudgets.map((b) => b.budgeted));
    const filteredTotalSpent = sum(filteredBudgets.map((b) => b.spent));
    const filteredTotalRemaining = subtract(filteredTotalBudgeted, filteredTotalSpent);
    const filteredSpentPct = percentage(filteredTotalSpent, filteredTotalBudgeted);
    return { filteredTotalBudgeted, filteredTotalSpent, filteredTotalRemaining, filteredSpentPct };
  }, [filteredBudgets]);

  return (
    <Card className="mb-6">
      <CardHeader>
        <CardTitle>{t('summary.title')}</CardTitle>
      </CardHeader>
      <CardContent>
        <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-4 gap-6">
          {/* Total Budgeted */}
          <div className="space-y-2">
            <div className="flex items-center gap-2 text-text-secondary">
              <Target className="h-4 w-4" />
              <span className="text-sm font-medium">{t('summary.totalBudgeted')}</span>
            </div>
            <p className="text-2xl font-bold font-mono text-text-primary">
              <ConvertedAmount
                amount={summary.totalBudgeted}
                currency={baseCurrency}
                isConverted={false}
                secondaryAmount={convert(summary.totalBudgeted)}
                secondaryCurrency={secCurrency}
                secondaryExchangeRate={secondaryExchangeRate}
                inline
              />
            </p>
            <p className="text-xs text-text-tertiary">
              {t('summary.activeCount', { active: summary.activeBudgets, total: summary.totalBudgets })}
            </p>
            {isFiltered && filteredTotals && (
              <p className="text-xs font-mono text-text-tertiary">
                 {t('summary.filtered')} <ConvertedAmount
                  amount={filteredTotals.filteredTotalBudgeted}
                  currency={baseCurrency}
                  isConverted={false}
                  secondaryAmount={convert(filteredTotals.filteredTotalBudgeted)}
                  secondaryCurrency={secCurrency}
                  secondaryExchangeRate={secondaryExchangeRate}
                  inline
                />
                <span className="ml-1">{t('summary.budgetsCount', { count: filteredBudgets!.length })}</span>
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
                amount={summary.totalSpent}
                currency={baseCurrency}
                isConverted={false}
                secondaryAmount={convert(summary.totalSpent)}
                secondaryCurrency={secCurrency}
                secondaryExchangeRate={secondaryExchangeRate}
                inline
              />
            </p>
            <p className="text-xs text-text-tertiary">
              {formatPercentage(spentPercentage, 1)} {t('summary.ofBudget')}
            </p>
            {isFiltered && filteredTotals && (
              <p className="text-xs font-mono text-text-tertiary">
                 {t('summary.filtered')} <ConvertedAmount
                  amount={filteredTotals.filteredTotalSpent}
                  currency={baseCurrency}
                  isConverted={false}
                  secondaryAmount={convert(filteredTotals.filteredTotalSpent)}
                  secondaryCurrency={secCurrency}
                  secondaryExchangeRate={secondaryExchangeRate}
                  inline
                />
                {' '}({formatPercentage(filteredTotals.filteredSpentPct, 1)})
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
                amount={Math.abs(summary.totalRemaining)}
                currency={baseCurrency}
                isConverted={false}
                secondaryAmount={convert(Math.abs(summary.totalRemaining))}
                secondaryCurrency={secCurrency}
                secondaryExchangeRate={secondaryExchangeRate}
                inline
              />
            </p>
            <p className="text-xs text-text-tertiary">
              {isOverBudget ? t('summary.exceededBudget') : t('summary.availableToSpend')}
            </p>
            {isFiltered && filteredTotals && (
              <p className={cn(
                'text-xs font-mono text-text-tertiary'
              )}>
                 {t('summary.filtered')} <ConvertedAmount
                  amount={Math.abs(filteredTotals.filteredTotalRemaining)}
                  currency={baseCurrency}
                  isConverted={false}
                  secondaryAmount={convert(Math.abs(filteredTotals.filteredTotalRemaining))}
                  secondaryCurrency={secCurrency}
                  secondaryExchangeRate={secondaryExchangeRate}
                  inline
                />
                {filteredTotals.filteredTotalRemaining < 0 ? ` ${t('summary.over')}` : ` ${t('summary.remainingLabel')}`}
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
                  'h-full transition-all duration-300 rounded-full',
                  avgSpentPercentage < 75
                    ? 'bg-success'
                    : avgSpentPercentage < 100
                      ? 'bg-warning'
                      : 'bg-error'
                )}
                style={{ width: `${Math.min(avgSpentPercentage, 100)}%` }}
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
