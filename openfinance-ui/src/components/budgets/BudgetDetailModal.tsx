import { formatDecimal } from '@/utils/format';
/**
 * BudgetDetailModal Component
 *
 * Modal displaying detailed budget history with tabbed sections:
 * - Overview: summary strip + bar chart + period breakdown table
 *
 * Follows the same hand-rolled overlay pattern as AssetDetailModal /
 * AccountDetailModal.
 */
import { useEffect, useId, useState } from 'react';
import { createPortal } from 'react-dom';
import { useTranslation } from 'react-i18next';
import { X, TrendingUp, TrendingDown, Calendar, ChevronDown } from 'lucide-react';
import {
  BarChart,
  Bar,
  XAxis,
  YAxis,
  CartesianGrid,
  Tooltip,
  Legend,
  ResponsiveContainer,
} from 'recharts';
import { Card } from '@/components/ui/Card';
import { ConvertedAmount } from '@/components/ui/ConvertedAmount';
import { HelpTooltip } from '@/components/ui/HelpTooltip';
import { LoadingSkeleton } from '@/components/LoadingComponents';
import { AlertSettings } from '@/components/alerts/AlertSettings';
import { useBudget, useBudgetHistory } from '@/hooks/useBudgets';
import { useVisibility } from '@/context/VisibilityContext';
import { useFormatCurrency } from '@/hooks/useFormatCurrency';
import { useSecondaryConversion } from '@/hooks/useSecondaryConversion';
import { cn } from '@/lib/utils';
import { add, percentage } from '@/utils/money';
import type { BudgetHistoryEntry } from '@/types/budget';

function PeriodResult({ entry, currency }: { entry: BudgetHistoryEntry; currency: string }) {
  const { t } = useTranslation('budgets');
  const { convert, secondaryCurrency, secondaryExchangeRate } = useSecondaryConversion(currency);
  const amount = Math.abs(entry.remaining);
  const exceeded = entry.remaining < 0;
  const available = entry.available ?? add(entry.budgeted, entry.carryover ?? 0);
  const resultLabel = exceeded
    ? available > 0
      ? t('detail.overBudgetPercent', {
          percent: formatDecimal(percentage(amount, available, 1), 1),
        })
      : t('detail.overBudget')
    : t('card.remaining');
  return (
    <span
      className={cn(
        'inline-flex flex-col items-end gap-0.5 whitespace-nowrap',
        exceeded ? 'text-error' : 'text-success'
      )}
    >
      <span className="font-mono text-sm font-semibold tabular-nums">
        <ConvertedAmount
          amount={amount}
          currency={currency}
          isConverted={false}
          secondaryAmount={convert(amount)}
          secondaryCurrency={secondaryCurrency}
          secondaryExchangeRate={secondaryExchangeRate}
          inline
        />
      </span>
      <span className="max-w-32 whitespace-normal text-right text-xs">{resultLabel}</span>
    </span>
  );
}

// ─── Custom chart tooltip ────────────────────────────────────────────────────

interface TooltipPayloadItem {
  name: string;
  value: number;
  color: string;
  payload?: { period: BudgetHistoryEntry };
}

interface CustomTooltipProps {
  active?: boolean;
  payload?: TooltipPayloadItem[];
  label?: string;
  currency: string;
}

function CustomTooltip({ active, payload, label, currency }: CustomTooltipProps) {
  const { t } = useTranslation('budgets');
  const {
    convert,
    secondaryCurrency: secCurrency,
    secondaryExchangeRate,
  } = useSecondaryConversion(currency);
  if (!active || !payload || payload.length === 0) return null;
  const period = payload[0].payload?.period;
  return (
    <div
      role="tooltip"
      className="bg-surface-elevated border border-border rounded-lg p-3 shadow-lg text-sm"
    >
      <p className="font-semibold text-text-primary mb-2">{label}</p>
      {payload.map(entry => (
        <p key={entry.name} style={{ color: entry.color }} className="mb-0.5">
          {entry.name}:{' '}
          <ConvertedAmount
            amount={entry.value}
            currency={currency}
            isConverted={false}
            secondaryAmount={convert(entry.value)}
            secondaryCurrency={secCurrency}
            secondaryExchangeRate={secondaryExchangeRate}
            inline
          />
        </p>
      ))}
      {period && !period.future && (
        <>
          {(period.carryover ?? 0) !== 0 && (
            <p className="text-text-secondary">
              {t('card.carryover')}:{' '}
              <ConvertedAmount amount={period.carryover ?? 0} currency={currency} inline />
            </p>
          )}
          <div className="mt-2 flex items-center justify-between gap-6 border-t border-border pt-2">
            <span className="text-text-secondary">{t('detail.columns.result')}</span>
            <PeriodResult entry={period} currency={currency} />
          </div>
        </>
      )}
      {period?.future && <p className="mt-2 text-text-muted">{t('detail.future')}</p>}
    </div>
  );
}

// ─── History table row ────────────────────────────────────────────────────────

function HistoryRow({ entry, currency }: { entry: BudgetHistoryEntry; currency: string }) {
  const { t } = useTranslation('budgets');
  const {
    convert,
    secondaryCurrency: secCurrency,
    secondaryExchangeRate,
  } = useSecondaryConversion(currency);
  const isExceeded = !entry.future && entry.remaining < 0;

  return (
    <tr
      className={cn(
        'border-b border-border/50 last:border-0 transition-colors hover:bg-surface/50',
        isExceeded && 'bg-error/5'
      )}
    >
      <td className="min-w-28 px-3 py-3 text-sm font-medium text-text-primary sm:px-4">
        <span className="whitespace-nowrap">{entry.label}</span>
        <span className="mt-1 block text-xs font-normal text-text-secondary">
          <time dateTime={entry.periodStart}>{entry.periodStart}</time>
          <span className="hidden sm:inline"> – </span>
          <time className="block sm:inline" dateTime={entry.periodEnd}>
            {entry.periodEnd}
          </time>
        </span>
      </td>
      <td className="min-w-28 px-3 py-3 text-right sm:px-4">
        {entry.future ? (
          <span className="inline-flex flex-col gap-0.5 text-text-muted">
            <span>—</span>
            <span className="text-xs">{t('detail.future')}</span>
          </span>
        ) : (
          <PeriodResult entry={entry} currency={currency} />
        )}
      </td>
      <td className="py-3 px-4 text-sm text-right text-text-primary font-mono tabular-nums">
        <ConvertedAmount
          amount={entry.budgeted}
          currency={currency}
          isConverted={false}
          secondaryAmount={convert(entry.budgeted)}
          secondaryCurrency={secCurrency}
          secondaryExchangeRate={secondaryExchangeRate}
          inline
        />
      </td>
      <td className="py-3 px-4 text-sm text-right font-mono tabular-nums">
        {entry.future ? (
          '—'
        ) : (
          <ConvertedAmount amount={entry.carryover ?? 0} currency={currency} inline />
        )}
      </td>
      <td className="py-3 px-4 text-sm text-right text-text-primary font-mono tabular-nums">
        {entry.future ? (
          '—'
        ) : (
          <ConvertedAmount
            amount={entry.spent}
            currency={currency}
            isConverted={false}
            secondaryAmount={convert(entry.spent)}
            secondaryCurrency={secCurrency}
            secondaryExchangeRate={secondaryExchangeRate}
            inline
          />
        )}
      </td>
    </tr>
  );
}

// ─── Props ────────────────────────────────────────────────────────────────────

interface BudgetDetailModalProps {
  budgetId: number;
  onClose: () => void;
}

// ─── Component ────────────────────────────────────────────────────────────────

export function BudgetDetailModal({ budgetId, onClose }: BudgetDetailModalProps) {
  const [isBreakdownExpanded, setIsBreakdownExpanded] = useState(false);
  const breakdownId = useId();
  const { isAmountsVisible } = useVisibility();
  const { format: formatCurrency } = useFormatCurrency();
  const { t } = useTranslation('budgets');
  const { t: tc } = useTranslation('common');

  const { data: budget, isLoading: isBudgetLoading } = useBudget(budgetId);
  const { data: historyData, isLoading: isHistoryLoading } = useBudgetHistory(budgetId);
  const currency = historyData?.currency ?? budget?.currency;
  const {
    convert,
    secondaryCurrency: secCurrency,
    secondaryExchangeRate,
  } = useSecondaryConversion(currency);

  const isLoading = isBudgetLoading || isHistoryLoading;

  // Close on Escape
  useEffect(() => {
    const handler = (e: KeyboardEvent) => {
      if (e.key === 'Escape') onClose();
    };
    window.addEventListener('keydown', handler);
    return () => window.removeEventListener('keydown', handler);
  }, [onClose]);

  // Chart data
  const chartBudgetedKey = t('card.budgeted');
  const chartSpentKey = t('card.spent');
  const chartData = (historyData?.history ?? []).map(entry => ({
    name: entry.label,
    period: entry,
    [chartBudgetedKey]: entry.budgeted,
    [chartSpentKey]: entry.future ? null : entry.spent,
  }));

  const totalAvailable = historyData
    ? historyData.totalBudgeted + (historyData.initialCarryover ?? 0)
    : 0;
  const overallPercentage =
    historyData && totalAvailable > 0 ? (historyData.totalSpent / totalAvailable) * 100 : 0;
  const isOverBudget = historyData ? historyData.totalSpent > totalAvailable : false;

  return createPortal(
    <div className="fixed inset-0 z-50 flex items-center justify-center">
      {/* Backdrop */}
      <div className="absolute inset-0 bg-background/80 backdrop-blur-sm" onClick={onClose} />

      {/* Modal panel */}
      <div
        role="dialog"
        aria-modal="true"
        aria-label={t('detail.historyTitle', {
          category: historyData?.categoryName ?? budget?.categoryName ?? '',
        })}
        className="relative bg-surface border border-border rounded-lg shadow-lg max-w-4xl w-full max-h-[90vh] overflow-y-auto m-4"
      >
        {/* ── Header ── */}
        <div className="sticky top-0 z-10 bg-surface border-b border-border px-6 py-4 flex items-center justify-between">
          <div className="flex items-center gap-3">
            <div className="flex h-10 w-10 items-center justify-center rounded-xl bg-primary/10 text-primary shrink-0">
              <Calendar className="h-5 w-5" />
            </div>
            <div>
              {isLoading ? (
                <LoadingSkeleton className="h-6 w-48" />
              ) : (
                <>
                  <h2 className="text-xl font-bold text-foreground">
                    {t('detail.historyTitle', {
                      category: historyData?.categoryName ?? budget?.categoryName ?? '',
                    })}
                  </h2>
                  {historyData && (
                    <p className="text-sm text-muted-foreground">
                      {t('detail.periodLabel', {
                        period: t('form.periods.' + historyData.period),
                        startDate: historyData.startDate,
                        endDate: historyData.endDate,
                      })}
                    </p>
                  )}
                </>
              )}
            </div>
          </div>

          <div className="flex items-center gap-2">
            <button
              onClick={onClose}
              className="p-2 rounded-lg hover:bg-background transition-colors"
              aria-label={tc('aria.closeModal')}
            >
              <X className="h-5 w-5 text-muted-foreground" />
            </button>
          </div>
        </div>

        {/* ── Content ── */}
        <div className="p-4 sm:p-6">
          {isLoading ? (
            <div className="space-y-4">
              <LoadingSkeleton className="h-32 w-full" />
              <LoadingSkeleton className="h-64 w-full" />
              <LoadingSkeleton className="h-80 w-full" />
            </div>
          ) : !historyData ? (
            <div className="p-4 bg-error/10 border border-error/20 rounded-lg text-error text-sm">
              {t('detail.loadError')}
            </div>
          ) : (
            <>
              {/* Summary strip */}
              <div className="grid grid-cols-2 md:grid-cols-4 gap-4 mb-6">
                <Card className="p-4">
                  <p className="text-xs text-text-muted mb-1 uppercase tracking-wide">
                    {t('summary.totalBudgeted')}
                  </p>
                  <p className="text-xl font-bold text-text-primary font-mono">
                    <ConvertedAmount
                      amount={historyData.totalBudgeted}
                      currency={historyData.currency}
                      isConverted={false}
                      secondaryAmount={convert(historyData.totalBudgeted)}
                      secondaryCurrency={secCurrency}
                      secondaryExchangeRate={secondaryExchangeRate}
                      inline
                    />
                  </p>
                </Card>
                <Card className="p-4">
                  <p className="text-xs text-text-muted mb-1 uppercase tracking-wide">
                    {t('summary.totalSpent')}
                  </p>
                  <p
                    className={cn(
                      'text-xl font-bold font-mono',
                      isOverBudget ? 'text-error' : 'text-text-primary'
                    )}
                  >
                    <ConvertedAmount
                      amount={historyData.totalSpent}
                      currency={historyData.currency}
                      isConverted={false}
                      secondaryAmount={convert(historyData.totalSpent)}
                      secondaryCurrency={secCurrency}
                      secondaryExchangeRate={secondaryExchangeRate}
                      inline
                    />
                  </p>
                </Card>
                <Card className="p-4">
                  <p className="text-xs text-text-muted mb-1 uppercase tracking-wide">
                    {t('summary.remaining')}
                  </p>
                  <p
                    className={cn(
                      'text-xl font-bold font-mono',
                      isOverBudget ? 'text-error' : 'text-success'
                    )}
                  >
                    <ConvertedAmount
                      amount={
                        historyData.totalBudgeted +
                        (historyData.initialCarryover ?? 0) -
                        historyData.totalSpent
                      }
                      currency={historyData.currency}
                      isConverted={false}
                      secondaryAmount={convert(
                        historyData.totalBudgeted +
                          (historyData.initialCarryover ?? 0) -
                          historyData.totalSpent
                      )}
                      secondaryCurrency={secCurrency}
                      secondaryExchangeRate={secondaryExchangeRate}
                      inline
                    />
                  </p>
                </Card>
                <Card className="p-4">
                  <p className="text-xs text-text-muted mb-1 uppercase tracking-wide">
                    {t('detail.overallUsage')}
                  </p>
                  <div className="flex items-center gap-2">
                    {isOverBudget ? (
                      <TrendingUp className="h-5 w-5 text-error" />
                    ) : (
                      <TrendingDown className="h-5 w-5 text-success" />
                    )}
                    <p
                      className={cn(
                        'text-xl font-bold font-mono',
                        isOverBudget ? 'text-error' : 'text-success'
                      )}
                    >
                      {formatDecimal(overallPercentage, 1)}%
                    </p>
                  </div>
                </Card>
              </div>

              {/* Bar chart */}
              {chartData.length > 0 && (
                <Card className="p-4 mb-6">
                  <h2 className="text-sm font-semibold text-text-primary mb-4">
                    {t('detail.spendingByPeriod')}
                  </h2>
                  <ResponsiveContainer width="100%" height={240} minWidth={0}>
                    <BarChart data={chartData} margin={{ top: 4, right: 16, left: 0, bottom: 4 }}>
                      <CartesianGrid strokeDasharray="3 3" stroke="rgba(255,255,255,0.06)" />
                      <XAxis
                        dataKey="name"
                        tick={{ fill: 'var(--color-text-secondary)', fontSize: 11 }}
                        axisLine={false}
                        tickLine={false}
                      />
                      <YAxis
                        tick={{ fill: 'var(--color-text-secondary)', fontSize: 11 }}
                        axisLine={false}
                        tickLine={false}
                        tickFormatter={(v: number) =>
                          isAmountsVisible
                            ? formatCurrency(v, historyData.currency, { compact: true })
                            : '••••••'
                        }
                      />
                      <Tooltip content={<CustomTooltip currency={historyData.currency} />} />
                      <Legend
                        wrapperStyle={{
                          fontSize: 12,
                          color: 'var(--color-text-secondary)',
                        }}
                      />
                      <Bar
                        dataKey={chartBudgetedKey}
                        fill="#c8881a"
                        radius={[3, 3, 0, 0]}
                        maxBarSize={40}
                      />
                      <Bar
                        dataKey={chartSpentKey}
                        fill="#c5a254"
                        radius={[3, 3, 0, 0]}
                        maxBarSize={40}
                      />
                    </BarChart>
                  </ResponsiveContainer>
                </Card>
              )}

              {/* History table */}
              <Card padding="none" className="mb-6 overflow-hidden">
                <div className="flex items-center gap-2 px-4 py-3">
                  <h2 className="min-w-0 flex-1 text-sm font-semibold text-text-primary">
                    <button
                      type="button"
                      aria-expanded={isBreakdownExpanded}
                      aria-controls={breakdownId}
                      onClick={() => setIsBreakdownExpanded(expanded => !expanded)}
                      className="flex w-full items-center gap-2 rounded-sm text-left focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-primary"
                    >
                      <ChevronDown
                        aria-hidden="true"
                        className={cn(
                          'h-4 w-4 shrink-0 transition-transform',
                          !isBreakdownExpanded && '-rotate-90'
                        )}
                      />
                      {t('detail.periodBreakdown', { count: historyData.history.length })}
                    </button>
                  </h2>
                  <HelpTooltip text={t('detail.allowanceExplanation')} />
                </div>

                <div
                  id={breakdownId}
                  hidden={!isBreakdownExpanded}
                  className="border-t border-border"
                >
                  {(historyData.initialCarryover ?? 0) !== 0 && (
                    <p className="px-4 py-3 text-sm text-text-secondary">
                      {t('detail.initialCarry')}{' '}
                      <ConvertedAmount
                        amount={historyData.initialCarryover ?? 0}
                        currency={historyData.currency}
                        inline
                      />
                    </p>
                  )}
                  {historyData.history.length === 0 ? (
                    <div className="p-8 text-center text-text-muted text-sm">
                      {t('detail.noHistory')}
                    </div>
                  ) : (
                    <div className="overflow-x-auto">
                      <table className="w-full min-w-[640px]">
                        <thead>
                          <tr className="border-b border-border bg-surface-elevated">
                            <th className="py-2.5 px-3 sm:px-4 text-left text-xs font-semibold text-text-muted uppercase tracking-wide">
                              {t('detail.columns.period')}
                            </th>
                            <th className="py-2.5 px-3 sm:px-4 text-right text-xs font-semibold text-text-muted uppercase tracking-wide">
                              {t('detail.columns.result')}
                            </th>
                            <th className="py-2.5 px-4 text-right text-xs font-semibold text-text-muted uppercase tracking-wide">
                              {t('card.budgeted')}
                            </th>
                            <th className="py-2.5 px-4 text-right text-xs font-semibold text-text-muted uppercase tracking-wide">
                              {t('card.carryover')}
                            </th>
                            <th className="py-2.5 px-4 text-right text-xs font-semibold text-text-muted uppercase tracking-wide">
                              {t('card.spent')}
                            </th>
                          </tr>
                        </thead>
                        <tbody>
                          {historyData.history.map((entry, idx) => (
                            <HistoryRow
                              key={`${entry.periodStart}-${idx}`}
                              entry={entry}
                              currency={historyData.currency}
                            />
                          ))}
                        </tbody>
                      </table>
                    </div>
                  )}
                </div>
              </Card>
              <Card className="p-4 sm:p-6">
                <AlertSettings budgetId={budgetId} />
              </Card>
            </>
          )}
        </div>
      </div>
    </div>,
    document.body
  );
}
