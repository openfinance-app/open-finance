/**
 * BudgetDetailModal Component
 *
 * Modal displaying detailed budget history with tabbed sections:
 * - Overview: summary strip + bar chart + period breakdown table
 *
 * Follows the same hand-rolled overlay pattern as AssetDetailModal /
 * AccountDetailModal.
 */
import { useEffect } from 'react';
import { useTranslation } from 'react-i18next';
import { X, TrendingUp, TrendingDown, AlertTriangle, CheckCircle, Calendar } from 'lucide-react';
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
import { LoadingSkeleton } from '@/components/LoadingComponents';
import { useBudget, useBudgetHistory } from '@/hooks/useBudgets';
import { useVisibility } from '@/context/VisibilityContext';
import { useAuthContext } from '@/context/AuthContext';
import { useFormatCurrency } from '@/hooks/useFormatCurrency';
import { useSecondaryConversion } from '@/hooks/useSecondaryConversion';
import { cn } from '@/lib/utils';
import type { BudgetHistoryEntry, BudgetStatus } from '@/types/budget';

// ─── Status helpers ───────────────────────────────────────────────────────────

function StatusBadge({ status }: { status: BudgetStatus }) {
  const { t } = useTranslation('budgets');
  const STATUS_CONFIG: Record<
    BudgetStatus,
    { label: string; className: string; icon: React.ReactNode }
  > = {
    ON_TRACK: {
      label: t('card.status.onTrack'),
      className: 'text-success bg-success/10 border border-success/20',
      icon: <CheckCircle className="h-3.5 w-3.5" />,
    },
    WARNING: {
      label: t('card.status.warning'),
      className: 'text-warning bg-warning/10 border border-warning/20',
      icon: <AlertTriangle className="h-3.5 w-3.5" />,
    },
    EXCEEDED: {
      label: t('card.status.exceeded'),
      className: 'text-error bg-error/10 border border-error/20',
      icon: <TrendingUp className="h-3.5 w-3.5" />,
    },
  };
  const config = STATUS_CONFIG[status] ?? STATUS_CONFIG.ON_TRACK;
  return (
    <span
      className={cn(
        'inline-flex items-center gap-1 px-2 py-0.5 rounded-full text-xs font-medium',
        config.className
      )}
    >
      {config.icon}
      {config.label}
    </span>
  );
}

// ─── Custom chart tooltip ────────────────────────────────────────────────────

interface TooltipPayloadItem {
  name: string;
  value: number;
  color: string;
}

interface CustomTooltipProps {
  active?: boolean;
  payload?: TooltipPayloadItem[];
  label?: string;
  currency: string;
}

function CustomTooltip({ active, payload, label, currency }: CustomTooltipProps) {
  const {
    convert,
    secondaryCurrency: secCurrency,
    secondaryExchangeRate,
  } = useSecondaryConversion(currency);
  if (!active || !payload || payload.length === 0) return null;
  return (
    <div className="bg-surface-elevated border border-border rounded-lg p-3 shadow-lg text-sm">
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
    </div>
  );
}

// ─── History table row ────────────────────────────────────────────────────────

function HistoryRow({ entry, currency }: { entry: BudgetHistoryEntry; currency: string }) {
  const {
    convert,
    secondaryCurrency: secCurrency,
    secondaryExchangeRate,
  } = useSecondaryConversion(currency);
  const isExceeded = entry.status === 'EXCEEDED';
  const isWarning = entry.status === 'WARNING';

  return (
    <tr
      className={cn(
        'border-b border-border/50 last:border-0 transition-colors hover:bg-surface/50',
        isExceeded && 'bg-error/5',
        isWarning && 'bg-warning/5'
      )}
    >
      <td className="py-3 px-4 text-sm font-medium text-text-primary whitespace-nowrap">
        {entry.label}
      </td>
      <td className="py-3 px-4 text-sm text-text-secondary whitespace-nowrap">
        {entry.periodStart} – {entry.periodEnd}
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
        <span className={cn(isExceeded ? 'text-error font-semibold' : 'text-text-primary')}>
          <ConvertedAmount
            amount={entry.spent}
            currency={currency}
            isConverted={false}
            secondaryAmount={convert(entry.spent)}
            secondaryCurrency={secCurrency}
            secondaryExchangeRate={secondaryExchangeRate}
            inline
          />
        </span>
      </td>
      <td className="py-3 px-4 text-sm text-right font-mono tabular-nums">
        <span className={cn(entry.remaining < 0 ? 'text-error font-semibold' : 'text-success')}>
          <ConvertedAmount
            amount={entry.remaining}
            currency={currency}
            isConverted={false}
            secondaryAmount={convert(entry.remaining)}
            secondaryCurrency={secCurrency}
            secondaryExchangeRate={secondaryExchangeRate}
            inline
          />
        </span>
      </td>
      <td className="py-3 px-4 text-sm text-right font-mono tabular-nums">
        <div className="flex items-center justify-end gap-2">
          <div className="w-20 h-1.5 rounded-full bg-surface-elevated overflow-hidden hidden sm:block">
            <div
              className={cn(
                'h-full rounded-full transition-all',
                isExceeded ? 'bg-error' : isWarning ? 'bg-warning' : 'bg-success'
              )}
              style={{ width: `${Math.min(entry.percentageSpent, 100)}%` }}
            />
          </div>
          <span className={cn(isExceeded ? 'text-error font-semibold' : 'text-text-primary')}>
            {entry.percentageSpent.toFixed(1)}%
          </span>
        </div>
      </td>
      <td className="py-3 px-4 text-sm">
        <StatusBadge status={entry.status} />
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
  const { isAmountsVisible } = useVisibility();
  const { baseCurrency } = useAuthContext();
  const { format: formatCurrency } = useFormatCurrency();
  const {
    convert,
    secondaryCurrency: secCurrency,
    secondaryExchangeRate,
  } = useSecondaryConversion(baseCurrency);
  const { t } = useTranslation('budgets');
  const { t: tc } = useTranslation('common');

  const { data: budget, isLoading: isBudgetLoading } = useBudget(budgetId);
  const { data: historyData, isLoading: isHistoryLoading } = useBudgetHistory(budgetId);

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
    [chartBudgetedKey]: entry.budgeted,
    [chartSpentKey]: entry.spent,
  }));

  const overallPercentage =
    historyData && historyData.totalBudgeted > 0
      ? (historyData.totalSpent / historyData.totalBudgeted) * 100
      : 0;
  const isOverBudget = historyData ? historyData.totalSpent > historyData.totalBudgeted : false;

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center">
      {/* Backdrop */}
      <div className="absolute inset-0 bg-background/80 backdrop-blur-sm" onClick={onClose} />

      {/* Modal panel */}
      <div className="relative bg-surface border border-border rounded-lg shadow-lg max-w-4xl w-full max-h-[90vh] overflow-y-auto m-4">
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
        <div className="p-6">
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
                      currency={baseCurrency}
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
                      currency={baseCurrency}
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
                      amount={historyData.totalBudgeted - historyData.totalSpent}
                      currency={baseCurrency}
                      isConverted={false}
                      secondaryAmount={convert(historyData.totalBudgeted - historyData.totalSpent)}
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
                      {overallPercentage.toFixed(1)}%
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
                            ? formatCurrency(v, baseCurrency, { compact: true })
                            : '••••••'
                        }
                      />
                      <Tooltip content={<CustomTooltip currency={baseCurrency} />} />
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
                        fill="#f5a623"
                        radius={[3, 3, 0, 0]}
                        maxBarSize={40}
                      />
                    </BarChart>
                  </ResponsiveContainer>
                </Card>
              )}

              {/* History table */}
              <Card className="overflow-hidden">
                <div className="px-4 py-3 border-b border-border">
                  <h2 className="text-sm font-semibold text-text-primary">
                    {t('detail.periodBreakdown', { count: historyData.history.length })}
                  </h2>
                </div>

                {historyData.history.length === 0 ? (
                  <div className="p-8 text-center text-text-muted text-sm">
                    {t('detail.noHistory')}
                  </div>
                ) : (
                  <div className="overflow-x-auto">
                    <table className="w-full min-w-[640px]">
                      <thead>
                        <tr className="border-b border-border bg-surface-elevated">
                          <th className="py-2.5 px-4 text-left text-xs font-semibold text-text-muted uppercase tracking-wide">
                            {t('detail.columns.period')}
                          </th>
                          <th className="py-2.5 px-4 text-left text-xs font-semibold text-text-muted uppercase tracking-wide">
                            {t('detail.columns.dateRange')}
                          </th>
                          <th className="py-2.5 px-4 text-right text-xs font-semibold text-text-muted uppercase tracking-wide">
                            {t('card.budgeted')}
                          </th>
                          <th className="py-2.5 px-4 text-right text-xs font-semibold text-text-muted uppercase tracking-wide">
                            {t('card.spent')}
                          </th>
                          <th className="py-2.5 px-4 text-right text-xs font-semibold text-text-muted uppercase tracking-wide">
                            {t('card.remaining')}
                          </th>
                          <th className="py-2.5 px-4 text-right text-xs font-semibold text-text-muted uppercase tracking-wide">
                            {t('detail.columns.pctUsed')}
                          </th>
                          <th className="py-2.5 px-4 text-left text-xs font-semibold text-text-muted uppercase tracking-wide">
                            {t('detail.columns.status')}
                          </th>
                        </tr>
                      </thead>
                      <tbody>
                        {historyData.history.map((entry, idx) => (
                          <HistoryRow
                            key={`${entry.periodStart}-${idx}`}
                            entry={entry}
                            currency={baseCurrency}
                          />
                        ))}
                      </tbody>
                    </table>
                  </div>
                )}
              </Card>
            </>
          )}
        </div>
      </div>
    </div>
  );
}
