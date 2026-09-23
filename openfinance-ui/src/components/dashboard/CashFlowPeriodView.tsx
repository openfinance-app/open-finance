import { useMemo } from 'react';
import { useNavigate } from 'react-router';
import { endOfMonth, endOfYear, format, parseISO } from 'date-fns';
import { enUS, fr } from 'date-fns/locale';
import { useTranslation } from 'react-i18next';
import {
  Bar,
  CartesianGrid,
  ComposedChart,
  Line,
  ReferenceLine,
  ResponsiveContainer,
  Tooltip,
  XAxis,
  YAxis,
} from 'recharts';
import { ConvertedAmount } from '@/components/ui/ConvertedAmount';
import { useVisibility } from '@/context/VisibilityContext';
import { useNumberFormat } from '@/context/NumberFormatContext';
import type { CashFlowGranularity, ICashFlowPeriod } from '@/types/dashboard';
import { formatCurrency } from '@/utils/currency';
import { subtract } from '@/utils/money';
import { cn } from '@/lib/utils';

interface CashFlowPeriodViewProps {
  data: ICashFlowPeriod[];
  granularity: Exclude<CashFlowGranularity, 'DAY'>;
  baseCurrency: string;
}

function PeriodAmounts({ period, currency }: { period: ICashFlowPeriod; currency: string }) {
  const { t } = useTranslation('dashboard');
  const net = subtract(period.income, period.expense);
  return (
    <dl className="grid grid-cols-[auto_1fr] items-baseline gap-x-2 text-xs">
      {(
        [
          ['income', period.income, 'text-success'],
          ['expense', period.expense, 'text-error'],
          ['net', net, net < 0 ? 'text-error' : net > 0 ? 'text-success' : 'text-text-secondary'],
        ] as const
      ).map(([key, amount, color]) => (
        <div key={key} className="contents">
          <dt className="text-text-secondary">{t(`calendar.${key}`)}</dt>
          <dd className={cn('text-right font-medium tabular-nums', color)}>
            <ConvertedAmount amount={amount} currency={currency} inline />
          </dd>
        </div>
      ))}
    </dl>
  );
}

export default function CashFlowPeriodView({
  data,
  granularity,
  baseCurrency,
}: CashFlowPeriodViewProps) {
  const { t, i18n } = useTranslation('dashboard');
  const navigate = useNavigate();
  const { isAmountsVisible } = useVisibility();
  const { numberFormat } = useNumberFormat();
  const locale = i18n.language.startsWith('fr') ? fr : enUS;
  const chartData = useMemo(
    () =>
      data.map(period => ({
        ...period,
        label: format(parseISO(period.date), granularity === 'MONTH' ? 'MMM' : 'yyyy', { locale }),
        fullLabel: format(parseISO(period.date), granularity === 'MONTH' ? 'MMMM yyyy' : 'yyyy', {
          locale,
        }),
        net: subtract(period.income, period.expense),
      })),
    [data, granularity, locale]
  );

  return (
    <div className="grid flex-1 grid-cols-1 gap-4 @2xl:grid-cols-2">
      <div className="grid content-start grid-cols-2 gap-2 @4xl:grid-cols-3">
        {chartData.map(period => (
          <button
            key={period.date}
            type="button"
            aria-label={t('calendar.viewPeriodTransactions', { period: period.fullLabel })}
            onClick={() => {
              const date = parseISO(period.date);
              const end = granularity === 'MONTH' ? endOfMonth(date) : endOfYear(date);
              navigate(
                `/transactions?dateFrom=${period.date}&dateTo=${format(end, 'yyyy-MM-dd')}&excludeTransfers=true`
              );
            }}
            className="min-w-0 rounded-sm border border-border bg-surface p-1.5 text-left transition-colors hover:bg-surface-elevated focus-visible:outline-primary"
          >
            <span className="mb-1 block text-xs font-semibold capitalize text-text-primary">
              {period.fullLabel}
            </span>
            <PeriodAmounts period={period} currency={baseCurrency} />
          </button>
        ))}
      </div>
      <figure aria-label={t('calendar.chartTitle')} className="order-first min-w-0 @2xl:order-last">
        <figcaption className="mb-2 text-xs font-medium text-text-secondary">
          {t('calendar.chartTitle')}
        </figcaption>
        <div className="h-72 min-w-0" aria-hidden={!isAmountsVisible}>
          <ResponsiveContainer width="100%" height="100%" minWidth={0} debounce={50}>
            <ComposedChart
              data={chartData}
              margin={{ top: 8, right: 8, left: 0, bottom: 8 }}
              accessibilityLayer={isAmountsVisible}
            >
              <CartesianGrid strokeDasharray="3 3" stroke="var(--color-border)" vertical={false} />
              <XAxis
                dataKey="label"
                tick={{ fill: 'var(--color-text-secondary)', fontSize: 10 }}
                tickLine={false}
              />
              <YAxis
                width={65}
                tick={{ fill: 'var(--color-text-secondary)', fontSize: 10 }}
                tickLine={false}
                tickFormatter={(value: number) =>
                  isAmountsVisible
                    ? formatCurrency(value, baseCurrency, { compact: true, numberFormat })
                    : '••••'
                }
              />
              <ReferenceLine y={0} stroke="var(--color-border)" />
              <Tooltip
                content={({ active, payload }) => {
                  const period = payload?.[0]?.payload as (typeof chartData)[number] | undefined;
                  return active && period ? (
                    <div className="min-w-40 rounded-lg border border-border bg-surface-elevated p-3 shadow-lg">
                      <p className="mb-2 text-xs font-semibold capitalize text-text-primary">
                        {period.fullLabel}
                      </p>
                      <PeriodAmounts period={period} currency={baseCurrency} />
                    </div>
                  ) : null;
                }}
              />
              <Bar
                dataKey="income"
                name={t('calendar.income')}
                fill="var(--color-success)"
                radius={[3, 3, 0, 0]}
                maxBarSize={24}
                isAnimationActive={false}
              />
              <Bar
                dataKey="expense"
                name={t('calendar.expense')}
                fill="var(--color-error)"
                radius={[3, 3, 0, 0]}
                maxBarSize={24}
                isAnimationActive={false}
              />
              <Line
                dataKey="net"
                name={t('calendar.netTrend')}
                stroke="var(--color-primary)"
                strokeWidth={2}
                dot={{ r: 2 }}
                isAnimationActive={false}
              />
            </ComposedChart>
          </ResponsiveContainer>
        </div>
      </figure>
    </div>
  );
}
