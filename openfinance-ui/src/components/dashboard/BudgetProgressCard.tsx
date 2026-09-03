/**
 * BudgetProgressCard — Dashboard widget
 *
 * Shows an at-a-glance budget health summary for the current period:
 * - Aggregate spent / budgeted bar with a percentage
 * - Per-budget mini progress rows (up to 5, with a "View all" link)
 * - Color-coded status indicators (ON_TRACK / WARNING / EXCEEDED)
 */

import { useNavigate } from 'react-router';
import { ROUTES } from '@/constants/routes';
import { useTranslation } from 'react-i18next';
import { Target, TrendingDown, ArrowRight, AlertTriangle, CheckCircle2, XCircle } from 'lucide-react';
import { Card, CardHeader, CardTitle, CardContent } from '@/components/ui/Card';
import { ConvertedAmount } from '@/components/ui/ConvertedAmount';
import { HelpTooltip } from '@/components/ui/HelpTooltip';
import { useSecondaryConversion } from '@/hooks/useSecondaryConversion';
import { useBudgetSummary } from '@/hooks/useBudgets';
import { useAuthContext } from '@/context/AuthContext';
import { cn } from '@/lib/utils';
import { formatPercentage } from '@/utils/format';
import type { BudgetProgressResponse, BudgetStatus } from '@/types/budget';

// ── helpers ──────────────────────────────────────────────────────────────────

function statusColor(status: BudgetStatus) {
  switch (status) {
    case 'ON_TRACK': return 'bg-success';
    case 'WARNING':  return 'bg-warning';
    case 'EXCEEDED': return 'bg-error';
    default:         return 'bg-text-tertiary';
  }
}

function statusTextColor(status: BudgetStatus) {
  switch (status) {
    case 'ON_TRACK': return 'text-success';
    case 'WARNING':  return 'text-warning';
    case 'EXCEEDED': return 'text-error';
    default:         return 'text-text-tertiary';
  }
}

function StatusIcon({ status }: { status: BudgetStatus }) {
  const cls = cn('h-3.5 w-3.5 shrink-0', statusTextColor(status));
  switch (status) {
    case 'ON_TRACK': return <CheckCircle2 className={cls} />;
    case 'WARNING':  return <AlertTriangle className={cls} />;
    case 'EXCEEDED': return <XCircle      className={cls} />;
    default:         return null;
  }
}

// ── sub-components ────────────────────────────────────────────────────────────

interface BudgetRowProps {
  budget: BudgetProgressResponse;
  baseCurrency: string;
  onOpen: () => void;
}

function BudgetRow({ budget, baseCurrency, onOpen }: BudgetRowProps) {
  const pct = Math.min(budget.percentageSpent, 100);
  const { convert, secondaryCurrency: secCurrency, secondaryExchangeRate } = useSecondaryConversion(baseCurrency);
  const { t } = useTranslation('dashboard');

  return (
    <button
      type="button"
      onClick={onOpen}
      className="w-full text-left space-y-1 rounded-lg px-2 py-1 -mx-2 hover:bg-surface-elevated transition-colors"
      aria-label={t('cards.budgetCard.viewDetails')}
    >
      {/* Label row */}
      <div className="flex items-center justify-between gap-2">
        <div className="flex items-center gap-1.5 min-w-0">
          <StatusIcon status={budget.status} />
          <span className="text-xs text-text-primary truncate">{budget.categoryName}</span>
          {budget.daysRemaining < 0 && (
            <span className="text-xs text-text-tertiary shrink-0">(Expired)</span>
          )}
        </div>
        <div className="flex items-center gap-1.5 shrink-0">
          <span className={cn('text-xs font-mono font-medium', statusTextColor(budget.status))}>
            {formatPercentage(budget.percentageSpent, 0)}
          </span>
          <span className="text-xs text-text-tertiary font-mono">
              <ConvertedAmount
                amount={budget.spent}
                currency={baseCurrency}
                isConverted={false}
                secondaryAmount={convert(budget.spent)}
                secondaryCurrency={secCurrency}
                secondaryExchangeRate={secondaryExchangeRate}
                inline
              />
              {' / '}
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
      </div>

      {/* Progress bar */}
      <div className="h-1.5 bg-surface-elevated rounded-full overflow-hidden">
        <div
          className={cn('h-full rounded-full transition-all duration-500', statusColor(budget.status))}
          style={{ width: `${pct}%` }}
        />
      </div>
    </button>
  );
}

// ── main card ─────────────────────────────────────────────────────────────────

const MAX_VISIBLE = 5;

export default function BudgetProgressCard() {
  const { t } = useTranslation('dashboard');
  const navigate = useNavigate();
  const { baseCurrency } = useAuthContext();
  const { convert, secondaryCurrency: secCurrency, secondaryExchangeRate } = useSecondaryConversion(baseCurrency);

  // Fetch summary for the current (default) period — no period filter keeps it current
  const { data: summary, isLoading, isError } = useBudgetSummary();

  // ── loading skeleton ───────────────────────────────────────────────────────
  if (isLoading) {
    return (
      <Card className="h-full flex flex-col">
        <CardHeader>
          <CardTitle className="flex items-center gap-2">
            <Target className="h-4 w-4 text-primary" />
            {t('cards.budgetCard.title')}
          </CardTitle>
        </CardHeader>
        <CardContent className="flex-1 space-y-4">
          {[...Array(4)].map((_, i) => (
            <div key={i} className="space-y-1 animate-pulse">
              <div className="flex justify-between">
                <div className="h-3 bg-surface-elevated rounded w-28" />
                <div className="h-3 bg-surface-elevated rounded w-20" />
              </div>
              <div className="h-1.5 bg-surface-elevated rounded-full" />
            </div>
          ))}
        </CardContent>
      </Card>
    );
  }

  // ── error / empty states ───────────────────────────────────────────────────
  if (isError || !summary) {
    return (
      <Card className="h-full flex flex-col items-center justify-center gap-3 p-6">
        <Target className="h-8 w-8 text-text-tertiary" />
        <p className="text-sm text-text-secondary text-center">
          {isError ? t('cards.budgetCard.loadError') : t('cards.budgetCard.noBudgets')}
        </p>
        <button
          onClick={() => navigate(ROUTES.BUDGET)}
          className="text-xs text-primary hover:underline font-medium"
        >
          {t('cards.budgetCard.setUp')} →
        </button>
      </Card>
    );
  }

  if (summary.totalBudgets === 0) {
    return (
      <Card className="h-full flex flex-col items-center justify-center gap-3 p-6">
        <Target className="h-8 w-8 text-text-tertiary" />
        <p className="text-sm text-text-secondary text-center">{t('cards.budgetCard.noBudgets')}</p>
        <button
          onClick={() => navigate(ROUTES.BUDGET)}
          className="text-xs text-primary hover:underline font-medium"
        >
          {t('cards.budgetCard.createFirst')} →
        </button>
      </Card>
    );
  }

  // ── derived values ─────────────────────────────────────────────────────────
  const globalPct     = Math.min(summary.averageSpentPercentage, 100);
  const exceededCount = summary.budgets.filter((b) => b.status === 'EXCEEDED').length;
  const warningCount  = summary.budgets.filter((b) => b.status === 'WARNING').length;

  // Sort: exceeded first, then warning, then on-track; within each group sort by pct desc
  const sorted = [...summary.budgets].sort((a, b) => {
    const order: Record<BudgetStatus, number> = { EXCEEDED: 0, WARNING: 1, ON_TRACK: 2 };
    const diff = order[a.status] - order[b.status];
    if (diff !== 0) return diff;
    return b.percentageSpent - a.percentageSpent;
  });
  const visible  = sorted.slice(0, MAX_VISIBLE);
  const hasMore  = sorted.length > MAX_VISIBLE;

  // Overall bar color
  const overallPct = summary.averageSpentPercentage;
  const overallBarColor =
    overallPct >= 100 ? 'bg-error' :
    overallPct >= 75  ? 'bg-warning' :
    'bg-success';

  // ── render ─────────────────────────────────────────────────────────────────
  return (
    <Card className="h-full flex flex-col overflow-hidden">
      <CardHeader className="pb-3 shrink-0">
        <div className="flex items-center justify-between">
          <CardTitle className="flex items-center gap-2 text-base">
            <Target className="h-4 w-4 text-primary shrink-0" />
            {t('cards.budgetCard.title')}
            <HelpTooltip
              text={t('cards.budgetCard.tooltip')}
              side="right"
            />
          </CardTitle>
          <button
            onClick={() => navigate(ROUTES.BUDGET)}
            className="flex items-center gap-1 text-xs text-primary hover:underline font-medium"
          >
            {t('cards.budgetCard.viewAll')}
            <ArrowRight className="h-3 w-3" />
          </button>
        </div>
      </CardHeader>

      <CardContent className="flex-1 flex flex-col gap-4 pt-0 min-h-0">

        {/* ── Global aggregate bar ──────────────────────────────────────────── */}
        <div className="shrink-0 rounded-lg bg-surface-elevated/50 border border-border p-3 space-y-2">
          <div className="flex items-center justify-between">
            <span className="text-xs font-medium text-text-secondary">{t('cards.budgetCard.overall')}</span>
            <div className="flex items-center gap-2">
              {exceededCount > 0 && (
                <span className="flex items-center gap-1 text-xs text-error font-medium">
                  <TrendingDown className="h-3 w-3" />
                  {exceededCount} {t('cards.budgetCard.over')}
                </span>
              )}
              {warningCount > 0 && (
                <span className="flex items-center gap-1 text-xs text-warning font-medium">
                  <AlertTriangle className="h-3 w-3" />
                  {warningCount} {t('cards.budgetCard.warning')}
                </span>
              )}
            </div>
          </div>

          {/* Aggregate progress bar */}
          <div className="h-2.5 bg-surface rounded-full overflow-hidden">
            <div
              className={cn('h-full rounded-full transition-all duration-500', overallBarColor)}
              style={{ width: `${globalPct}%` }}
            />
          </div>

          {/* Aggregate numbers */}
          <div className="flex items-center justify-between text-xs">
            <span className="font-mono text-text-secondary">
              <ConvertedAmount
                amount={summary.totalSpent}
                currency={baseCurrency}
                isConverted={false}
                secondaryAmount={convert(summary.totalSpent)}
                secondaryCurrency={secCurrency}
                secondaryExchangeRate={secondaryExchangeRate}
                inline
              />
              {' ' + t('cards.budgetCard.spent')}
            </span>
            <span className={cn(
              'font-mono font-semibold',
              overallPct >= 100 ? 'text-error' : overallPct >= 75 ? 'text-warning' : 'text-success'
            )}>
              {formatPercentage(summary.averageSpentPercentage, 1)}
            </span>
            <span className="font-mono text-text-secondary">
              {t('cards.budgetCard.of') + ' '}
              <ConvertedAmount
                amount={summary.totalBudgeted}
                currency={baseCurrency}
                isConverted={false}
                secondaryAmount={convert(summary.totalBudgeted)}
                secondaryCurrency={secCurrency}
                secondaryExchangeRate={secondaryExchangeRate}
                inline
              />
            </span>
          </div>
        </div>

        {/* ── Individual budget rows ────────────────────────────────────────── */}
        <div className="flex-1 overflow-y-auto min-h-0 space-y-3 pr-1">
          {visible.map((budget) => (
            <BudgetRow
              key={budget.budgetId}
              budget={budget}
              baseCurrency={baseCurrency}
              onOpen={() => navigate(`/budget?open=${budget.budgetId}`)}
            />
          ))}

          {hasMore && (
            <button
              onClick={() => navigate(ROUTES.BUDGET)}
              className="w-full text-center text-xs text-text-tertiary hover:text-primary transition-colors py-1"
            >
              {t('cards.budgetCard.more', { count: sorted.length - MAX_VISIBLE })}
            </button>
          )}
        </div>

        {/* ── Footer stats ──────────────────────────────────────────────────── */}
        <div className="shrink-0 flex items-center justify-between pt-3 border-t border-border text-xs text-text-tertiary">
          <span>{t('cards.budgetCard.active', { count: summary.activeBudgets })}</span>
          <span className={cn(
            'font-mono font-medium',
            summary.totalRemaining < 0 ? 'text-error' : 'text-success'
          )}>
            <ConvertedAmount
              amount={Math.abs(summary.totalRemaining)}
              currency={baseCurrency}
              isConverted={false}
              secondaryAmount={convert(Math.abs(summary.totalRemaining))}
              secondaryCurrency={secCurrency}
              secondaryExchangeRate={secondaryExchangeRate}
              inline
            />
            {summary.totalRemaining < 0 ? ' ' + t('cards.budgetCard.over') : ' ' + t('cards.budgetCard.remaining')}
          </span>
        </div>
      </CardContent>
    </Card>
  );
}
