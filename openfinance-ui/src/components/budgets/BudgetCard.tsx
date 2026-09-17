/**
 * BudgetCard Component
 * TASK-8.2.9: Create BudgetCard component with progress bar
 * TASK-8.4.3: Add click-to-detail navigation support
 *
 * Displays budget information with progress tracking and color coding
 */

import { Card } from '@/components/ui/Card';
import { Button } from '@/components/ui/Button';
import { Badge } from '@/components/ui/Badge';
import { ConvertedAmount } from '@/components/ui/ConvertedAmount';
import { Edit2, Trash2, Calendar, TrendingDown, ChevronRight } from 'lucide-react';
import { useSecondaryConversion } from '@/hooks/useSecondaryConversion';
import { formatPercentage } from '@/utils/format';
import { useTranslation } from 'react-i18next';
import type { BudgetProgressResponse } from '@/types/budget';
import { cn } from '@/lib/utils';
import { useAuthContext } from '@/context/AuthContext';

interface BudgetCardProps {
  budget: BudgetProgressResponse;
  onEdit: (budgetId: number) => void;
  onDelete: (budgetId: number) => void;
  /** Optional callback to navigate to the budget detail/history view */
  onViewDetail?: (budgetId: number) => void;
}

/**
 * Get progress bar color based on budget status
 */
function getProgressColor(status: string): string {
  switch (status) {
    case 'ON_TRACK':
      return 'bg-success';
    case 'WARNING':
      return 'bg-warning';
    case 'EXCEEDED':
      return 'bg-error';
    default:
      return 'bg-text-tertiary';
  }
}

/**
 * Get status badge variant based on budget status
 */
function getStatusVariant(status: string): 'success' | 'warning' | 'error' | 'default' {
  switch (status) {
    case 'ON_TRACK':
      return 'success';
    case 'WARNING':
      return 'warning';
    case 'EXCEEDED':
      return 'error';
    default:
      return 'default';
  }
}

export function BudgetCard({ budget, onEdit, onDelete, onViewDetail }: BudgetCardProps) {
  const { baseCurrency } = useAuthContext();
  const {
    convert,
    secondaryCurrency: secCurrency,
    secondaryExchangeRate,
  } = useSecondaryConversion(baseCurrency);
  const { t } = useTranslation('budgets');
  const { t: tc } = useTranslation('common');
  const isOverBudget = budget.percentageSpent > 100;
  const progressWidth = Math.min(budget.percentageSpent, 100);

  return (
    <Card
      className={cn(
        'p-6 transition-colors duration-150 group',
        onViewDetail ? 'hover:bg-surface-elevated cursor-pointer' : 'hover:bg-surface-elevated'
      )}
      onClick={onViewDetail ? () => onViewDetail(budget.budgetId) : undefined}
      role={onViewDetail ? 'button' : undefined}
      aria-label={
        onViewDetail ? t('card.viewHistory', { category: budget.categoryName }) : undefined
      }
    >
      {/* Header */}
      <div className="flex items-start justify-between mb-4">
        <div className="flex-1 min-w-0">
          <h3 className="text-lg font-semibold text-text-primary truncate">
            {budget.categoryName}
          </h3>
          <p className="text-sm text-text-secondary mt-0.5">
            {t('form.periods.' + budget.period)} {t('card.budget')}
          </p>
        </div>

        {/* Actions */}
        <div className="flex items-center gap-1 ml-4">
          <div className="flex items-center gap-1 opacity-0 group-hover:opacity-100 transition-opacity duration-150">
            <Button
              variant="ghost"
              size="sm"
              onClick={e => {
                e.stopPropagation();
                onEdit(budget.budgetId);
              }}
              className="h-8 w-8 p-0"
              aria-label={tc('aria.editBudget')}
            >
              <Edit2 className="h-4 w-4" />
            </Button>
            <Button
              variant="ghost"
              size="sm"
              onClick={e => {
                e.stopPropagation();
                onDelete(budget.budgetId);
              }}
              className="h-8 w-8 p-0 text-error hover:text-error hover:bg-error/10"
              aria-label={tc('aria.deleteBudget')}
            >
              <Trash2 className="h-4 w-4" />
            </Button>
          </div>
          {onViewDetail && (
            <ChevronRight className="h-4 w-4 text-text-tertiary opacity-0 group-hover:opacity-100 transition-opacity duration-150 ml-1" />
          )}
        </div>
      </div>

      {/* Budget Amount */}
      <div className="space-y-1 mb-4">
        <div className="flex items-center justify-between">
          <span className="text-sm text-text-secondary">{t('card.budgeted')}</span>
          <span className="text-sm font-medium font-mono text-text-primary">
            <ConvertedAmount
              amount={budget.budgeted}
              currency={baseCurrency}
              isConverted={false}
              secondaryAmount={convert(budget.budgeted)}
              secondaryCurrency={secCurrency}
              secondaryExchangeRate={secondaryExchangeRate}
              inline
            />
          </span>
        </div>
        <div className="flex items-center justify-between">
          <span className="text-sm text-text-secondary">{t('card.spent')}</span>
          <span className="text-sm font-medium font-mono text-text-primary">
            <ConvertedAmount
              amount={budget.spent}
              currency={baseCurrency}
              isConverted={false}
              secondaryAmount={convert(budget.spent)}
              secondaryCurrency={secCurrency}
              secondaryExchangeRate={secondaryExchangeRate}
              inline
            />
          </span>
        </div>
        <div className="flex items-center justify-between">
          <span className="text-sm text-text-secondary">{t('card.remaining')}</span>
          <span
            className={cn(
              'text-sm font-medium font-mono',
              isOverBudget ? 'text-error' : 'text-success'
            )}
          >
            <ConvertedAmount
              amount={budget.remaining}
              currency={baseCurrency}
              isConverted={false}
              secondaryAmount={convert(budget.remaining)}
              secondaryCurrency={secCurrency}
              secondaryExchangeRate={secondaryExchangeRate}
              inline
            />
          </span>
        </div>
      </div>

      {/* Progress Bar */}
      <div className="mb-4">
        <div className="flex items-center justify-between mb-2">
          <span className="text-xs text-text-secondary">{t('card.progress')}</span>
          <span className="text-xs font-medium font-mono text-text-primary">
            {formatPercentage(budget.percentageSpent, 1)}
          </span>
        </div>
        <div className="h-2 bg-surface-elevated rounded-full overflow-hidden">
          <div
            className={cn(
              'h-full transition-all duration-300 rounded-full',
              getProgressColor(budget.status)
            )}
            style={{ width: `${progressWidth}%` }}
          />
        </div>
        {isOverBudget && (
          <div className="flex items-center gap-1.5 mt-2 text-error">
            <TrendingDown className="h-3.5 w-3.5" />
            <span className="text-xs font-medium">
              {t('card.overBudgetBy')}{' '}
              <ConvertedAmount
                amount={Math.abs(budget.remaining)}
                currency={baseCurrency}
                isConverted={false}
                secondaryAmount={convert(Math.abs(budget.remaining))}
                secondaryCurrency={secCurrency}
                secondaryExchangeRate={secondaryExchangeRate}
                inline
              />
            </span>
          </div>
        )}
      </div>

      {/* Footer with Status and Days Remaining */}
      <div className="flex items-center justify-between pt-4 border-t border-border">
        <Badge variant={getStatusVariant(budget.status)} size="sm">
          {t(
            `card.status.${budget.status === 'ON_TRACK' ? 'onTrack' : budget.status.toLowerCase()}`
          )}
        </Badge>
        <div className="flex items-center gap-1.5 text-text-secondary">
          <Calendar className="h-3.5 w-3.5" />
          <span className="text-xs">
            {budget.daysRemaining > 0
              ? t('card.daysLeft', { count: budget.daysRemaining })
              : t('card.expired')}
          </span>
        </div>
      </div>
    </Card>
  );
}
