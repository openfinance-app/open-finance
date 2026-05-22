/**
 * NetWorthTrendChart Component
 * Task 4.3.7: Create NetWorthTrendChart component
 * 
 * Line chart showing net worth trend over time with gold stroke
 */
import { XAxis, YAxis, CartesianGrid, Tooltip, ResponsiveContainer, Area, AreaChart } from 'recharts';
import type { INetWorthSummary } from '@/types/dashboard';
import { TrendingUp, TrendingDown, Minus } from 'lucide-react';
import { PrivateAmount } from '../ui/PrivateAmount';
import { useVisibility } from '@/context/VisibilityContext';
import { useUserSettings } from '@/hooks/useUserSettings';
import { formatDate as globalFormatDate } from '@/utils/date';
import { useFormatCurrency } from '@/hooks/useFormatCurrency';
import { useTranslation } from 'react-i18next';

interface NetWorthTrendChartProps {
  data: INetWorthSummary[];
  currency: string;
  /** Human-readable label for the selected period, e.g. "last 30d", "2026-01-01 → 2026-03-03" */
  periodLabel?: string;
}

interface CustomTooltipProps {
  active?: boolean;
  // eslint-disable-next-line @typescript-eslint/no-explicit-any
  payload?: any[];
  /** Base currency to use for formatting — passed from the chart component */
  currency: string;
  /** Formatting function injected by the parent (respects user's number format preference) */
  formatFn: (amount: number, currency?: string | null) => string;
}

/**
 * Custom tooltip for the net worth trend chart.
 * Uses the chart-level `currency` prop (= user's base currency) rather than
 * per-data-point `data.currency`, which may reflect the original native currency.
 * Requirement REQ-5.1: amounts are always displayed in the user's base currency.
 */
const CustomTooltip = ({ active, payload, currency, dateFormat, formatFn }: CustomTooltipProps & { dateFormat?: string }) => {
  if (active && payload && payload.length) {
    const data = payload[0].payload;
    const change = data.netWorth - (data.previousNetWorth || data.netWorth);
    const changePercent = data.previousNetWorth
      ? ((change / data.previousNetWorth) * 100).toFixed(2)
      : '0.00';

    return (
      <div className="bg-surface rounded-lg shadow-lg p-4 border border-border">
        <p className="text-sm font-semibold text-text-primary mb-2">
          {globalFormatDate(data.date, dateFormat)}
        </p>
        <p className="text-lg font-bold text-text-primary mb-1">
          <PrivateAmount inline>
            {formatFn(data.netWorth, currency)}
          </PrivateAmount>
        </p>
        {data.previousNetWorth && (
          <p className={`text-sm ${change >= 0 ? 'text-green-600' : 'text-red-600'}`}>
            <PrivateAmount inline>
              {change >= 0 ? '+' : ''}{formatFn(change, currency)}
            </PrivateAmount> ({change >= 0 ? '+' : ''}{changePercent}%)
          </p>
        )}
      </div>
    );
  }
  return null;
};

/**
 * Format currency for Y-axis
 */
const formatYAxis = (value: number, isVisible: boolean) => {
  if (!isVisible) {
    return '••••';
  }
  // Abbreviate large numbers
  if (Math.abs(value) >= 1000000) {
    return `${(value / 1000000).toFixed(1)}M`;
  }
  if (Math.abs(value) >= 1000) {
    return `${(value / 1000).toFixed(0)}K`;
  }
  return value.toFixed(0);
};

export default function NetWorthTrendChart({ data, currency, periodLabel }: NetWorthTrendChartProps) {
  const { isAmountsVisible } = useVisibility();
  const { data: settings } = useUserSettings();
  const { format } = useFormatCurrency();
  const { t } = useTranslation('dashboard');
  // Add previousNetWorth for change calculation in tooltip
  const chartData = data.map((point, index) => ({
    ...point,
    previousNetWorth: index > 0 ? data[index - 1].netWorth : null,
  }));

  if (data.length === 0) {
    return (
      <div className="bg-surface rounded-lg p-6 border border-border h-full flex flex-col">
        <h3 className="text-lg font-semibold text-text-primary mb-4">{t('netWorthTrend.title')}</h3>
        <div className="flex items-center justify-center flex-1 min-h-0 text-text-secondary">
          <p>No historical data available to show trend.</p>
        </div>
      </div>
    );
  }

  // Calculate overall trend
  const firstValue = data[0]?.netWorth || 0;
  const lastValue = data[data.length - 1]?.netWorth || 0;
  const overallChange = lastValue - firstValue;
  const overallChangePercent = firstValue !== 0
    ? ((overallChange / Math.abs(firstValue)) * 100).toFixed(2)
    : '0.00';

  const getTrendIcon = () => {
    if (overallChange > 0) return <TrendingUp className="h-5 w-5 text-green-500" />;
    if (overallChange < 0) return <TrendingDown className="h-5 w-5 text-red-500" />;
    return <Minus className="h-5 w-5 text-text-muted" />;
  };

  // Calculate days covered for subtitle
  const daysCovered = data.length > 0 
    ? Math.ceil((new Date(data[data.length - 1]?.date).getTime() - new Date(data[0]?.date).getTime()) / (1000 * 60 * 60 * 24))
    : 0;

  return (
    <div className="bg-surface rounded-lg p-6 border border-border h-full flex flex-col">
      {/* Header */}
      <div className="flex items-center justify-between mb-6">
        <div>
          <h3 className="text-lg font-semibold text-text-primary mb-1">{t('netWorthTrend.title')}</h3>
          <p className="text-sm text-text-secondary">
            {periodLabel
              ? t('netWorthTrend.periodTrend', { period: periodLabel })
              : t('netWorthTrend.dataPointsDays', { count: data.length, days: daysCovered })}
          </p>
        </div>

        {/* Overall Change */}
        <div className="flex items-center gap-2">
          {getTrendIcon()}
          <div className="text-right">
            <p className={`text-sm font-semibold ${overallChange >= 0 ? 'text-green-500' : 'text-red-500'}`}>
          <PrivateAmount inline>
            {overallChange >= 0 ? '+' : ''}{format(overallChange, currency)}
          </PrivateAmount>
            </p>
            <p className={`text-xs ${overallChange >= 0 ? 'text-green-500' : 'text-red-500'}`}>
              {overallChange >= 0 ? '+' : ''}{overallChangePercent}%
            </p>
          </div>
        </div>
      </div>

      {/* Chart */}
      <div className="w-full h-[300px]">
        <ResponsiveContainer width="100%" height="100%" minWidth={0} debounce={50}>
          <AreaChart data={chartData} margin={{ top: 10, right: 10, left: 0, bottom: 0 }}>
            <defs>
              <linearGradient id="colorNetWorth" x1="0" y1="0" x2="0" y2="1">
                <stop offset="5%" stopColor="#f5a623" stopOpacity={0.3} />
                <stop offset="95%" stopColor="#f5a623" stopOpacity={0} />
              </linearGradient>
            </defs>
            <CartesianGrid strokeDasharray="3 3" stroke="#2a2a2a" vertical={false} />
            <XAxis
              dataKey="date"
              tickFormatter={(value) => globalFormatDate(value, settings?.dateFormat)}
              stroke="#666"
              style={{ fontSize: '12px' }}
              tickLine={false}
            />
            <YAxis
              tickFormatter={(value) => formatYAxis(value, isAmountsVisible)}
              stroke="#666"
              style={{ fontSize: '12px' }}
              tickLine={false}
              axisLine={false}
            />
            <Tooltip content={<CustomTooltip currency={currency} dateFormat={settings?.dateFormat} formatFn={format} />} />
            <Area
              type="monotone"
              dataKey="netWorth"
              stroke="#f5a623"
              strokeWidth={2}
              fill="url(#colorNetWorth)"
              dot={false}
              activeDot={{ r: 6, fill: '#f5a623' }}
            />
          </AreaChart>
        </ResponsiveContainer>
      </div>
    </div>
  );
}
