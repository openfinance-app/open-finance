import { useMemo, useState } from 'react';
import {
  BarChart,
  Bar,
  LineChart,
  Line,
  XAxis,
  YAxis,
  Tooltip,
  ResponsiveContainer,
  CartesianGrid,
  Cell,
} from 'recharts';
import { useTranslation } from 'react-i18next';
import { TrendingUp, TrendingDown } from 'lucide-react';
import { useYearlyBalance } from '@/hooks/useDashboard';
import { useFormatCurrency } from '@/hooks/useFormatCurrency';
import { useVisibility } from '@/context/VisibilityContext';
import { PrivateAmount } from '@/components/ui/PrivateAmount';
import { HelpTooltip } from '@/components/ui/HelpTooltip';

type ViewMode = 'netWorth' | 'account' | 'institution';
type ChartType = 'bar' | 'line';

/** Encoded selection value: "netWorth" | "account:42" | "institution:7" */
type SelectionValue = string;

interface BalanceVariationCardProps {
  currency?: string;
}



const CustomTooltip = ({
  active,
  payload,
  format,
  currency,
}: {
  active?: boolean;
  payload?: { payload: { year: number; amount: number; variationPercentage: number | null } }[];
  format: (amount: number, currency?: string | null) => string;
  currency: string;
}) => {
  if (active && payload && payload.length) {
    const data = payload[0].payload;
    return (
      <div className="bg-surface-elevated border border-border rounded-lg p-3 shadow-lg">
        <p className="text-text-primary font-semibold text-sm mb-1">{data.year}</p>
        <p className="text-primary font-mono text-sm">
          <PrivateAmount inline>{format(data.amount, currency)}</PrivateAmount>
        </p>
        {data.variationPercentage != null && (
          <p
            className={`text-xs mt-1 font-semibold ${
              data.variationPercentage >= 0 ? 'text-green-500' : 'text-red-500'
            }`}
          >
            {data.variationPercentage >= 0 ? '+' : ''}
            {data.variationPercentage.toFixed(2)}%
          </p>
        )}
      </div>
    );
  }
  return null;
};

export default function BalanceVariationCard({ currency = 'EUR' }: BalanceVariationCardProps) {
  const { t } = useTranslation('dashboard');
  const { format } = useFormatCurrency();
  const { isAmountsVisible } = useVisibility();
  const { data: yearlyData, isLoading, error } = useYearlyBalance();

  const [selection, setSelection] = useState<SelectionValue>('netWorth');
  const [chartType, setChartType] = useState<ChartType>('bar');

  const effectiveCurrency = yearlyData?.currency ?? currency;

  // Parse selection value into viewMode + entityId
  const { viewMode, entityId } = useMemo((): { viewMode: ViewMode; entityId: number | null } => {
    if (selection === 'netWorth') return { viewMode: 'netWorth', entityId: null };
    if (selection.startsWith('account:'))
      return { viewMode: 'account', entityId: Number(selection.split(':')[1]) };
    if (selection.startsWith('institution:'))
      return { viewMode: 'institution', entityId: Number(selection.split(':')[1]) };
    return { viewMode: 'netWorth', entityId: null };
  }, [selection]);

  // Determine chart data based on selection
  const chartData = useMemo(() => {
    if (!yearlyData) return [];

    if (viewMode === 'netWorth') {
      return yearlyData.netWorth.map(dp => ({
        year: dp.year,
        amount: dp.amount,
        variationPercentage: dp.variationPercentage,
      }));
    }

    const entries = viewMode === 'account' ? yearlyData.accounts : yearlyData.institutions;
    if (!entries || entries.length === 0) return [];

    const entry = entries.find(e => e.id === entityId);
    if (entry) {
      return entry.data.map(dp => ({
        year: dp.year,
        amount: dp.amount,
        variationPercentage: dp.variationPercentage,
      }));
    }

    return [];
  }, [yearlyData, viewMode, entityId]);

  // Average yearly increase
  const averageIncrease = useMemo(() => {
    const variations = chartData
      .map((d: { variationPercentage: number | null }) => d.variationPercentage)
      .filter((v): v is number => v != null);
    if (variations.length === 0) return null;
    return variations.reduce((a, b) => a + b, 0) / variations.length;
  }, [chartData]);

  if (isLoading) {
    return (
      <div className="bg-surface rounded-lg p-6 border border-border h-full animate-pulse">
        <div className="h-6 bg-surface-elevated rounded w-48 mb-4" />
        <div className="h-75 bg-surface-elevated rounded" />
      </div>
    );
  }

  if (error || !yearlyData || yearlyData.years.length === 0) {
    return (
      <div className="bg-surface rounded-lg p-6 border border-border h-full flex flex-col items-center justify-center text-text-secondary">
        <p className="text-sm">{t('balanceVariation.noData')}</p>
      </div>
    );
  }

  return (
    <div className="bg-surface rounded-lg p-6 border border-border h-full flex flex-col">
      {/* Header */}
      <div className="flex items-center justify-between mb-4 flex-wrap gap-2">
        <div className="flex items-center gap-1">
          <h3 className="text-lg font-semibold text-text-primary">
            {t('balanceVariation.title')}
          </h3>
          <HelpTooltip text={t('balanceVariation.tooltip')} side="right" />
        </div>
      </div>

      {/* Controls */}
      <div className="flex flex-wrap items-center gap-3 mb-4">
        {/* Single grouped selector: Net Worth / Accounts / Institutions */}
        <select
          value={selection}
          onChange={e => setSelection(e.target.value)}
          className="bg-surface-elevated text-text-primary text-sm rounded-lg px-3 py-1.5 border border-border focus:outline-none focus:ring-1 focus:ring-primary max-w-64 truncate"
        >
          <option value="netWorth">{t('balanceVariation.viewNetWorth')}</option>
          {yearlyData.accounts.length > 0 && (
            <optgroup label={t('balanceVariation.viewAccounts')}>
              {yearlyData.accounts.map(a => (
                <option key={`account:${a.id}`} value={`account:${a.id}`}>
                  {a.name}
                </option>
              ))}
            </optgroup>
          )}
          {yearlyData.institutions.length > 0 && (
            <optgroup label={t('balanceVariation.viewInstitutions')}>
              {yearlyData.institutions.map(i => (
                <option key={`institution:${i.id}`} value={`institution:${i.id}`}>
                  {i.name}
                </option>
              ))}
            </optgroup>
          )}
        </select>

        {/* Chart type selector */}
        <select
          value={chartType}
          onChange={e => setChartType(e.target.value as ChartType)}
          className="bg-surface-elevated text-text-primary text-sm rounded-lg px-3 py-1.5 border border-border focus:outline-none focus:ring-1 focus:ring-primary"
        >
          <option value="bar">{t('balanceVariation.chartBar')}</option>
          <option value="line">{t('balanceVariation.chartLine')}</option>
        </select>
      </div>

      {/* Average increase summary */}
      {averageIncrease != null && (
        <div className="mb-4 p-3 bg-surface-elevated rounded-lg flex items-center gap-2">
          {averageIncrease >= 0 ? (
            <TrendingUp className="h-4 w-4 text-green-500" />
          ) : (
            <TrendingDown className="h-4 w-4 text-red-500" />
          )}
          <span className="text-sm text-text-secondary">
            {t('balanceVariation.averageIncrease')}:
          </span>
          <span
            className={`text-sm font-semibold ${
              averageIncrease >= 0 ? 'text-green-500' : 'text-red-500'
            }`}
          >
            {averageIncrease >= 0 ? '+' : ''}
            {averageIncrease.toFixed(2)}%{' '}
            {t('balanceVariation.perYear')}
          </span>
        </div>
      )}

      {/* Chart */}
      <div className="flex-1 min-h-70">
        <ResponsiveContainer width="100%" height="100%" minWidth={0} debounce={50}>
          {chartType === 'bar' ? (
            <BarChart data={chartData} margin={{ top: 5, right: 5, left: 5, bottom: 5 }}>
              <CartesianGrid strokeDasharray="3 3" stroke="#374151" opacity={0.3} />
              <XAxis
                dataKey="year"
                stroke="#9ca3af"
                tick={{ fill: '#9ca3af', fontSize: 12 }}
                tickFormatter={(v: number) => String(v)}
              />
              <YAxis
                stroke="#9ca3af"
                tick={{ fill: '#9ca3af', fontSize: 12 }}
                tickFormatter={(value: number) => {
                  if (!isAmountsVisible) return '••••';
                  if (Math.abs(value) >= 1_000_000)
                    return `${(value / 1_000_000).toFixed(1)}M`;
                  if (Math.abs(value) >= 1_000) return `${(value / 1_000).toFixed(0)}k`;
                  return value.toString();
                }}
              />
              <Tooltip
                content={<CustomTooltip format={format} currency={effectiveCurrency} />}
                cursor={{ fill: '#374151', opacity: 0.2 }}
              />
              <Bar dataKey="amount" radius={[8, 8, 0, 0]} maxBarSize={60}>
                {chartData.map(
                  (
                    entry: { year: number; amount: number; variationPercentage: number | null },
                    _index: number
                  ) => (
                    <Cell
                      key={`cell-${entry.year}`}
                      fill={
                        entry.variationPercentage == null
                          ? '#6b7280'
                          : entry.variationPercentage >= 0
                            ? '#10b981'
                            : '#ef4444'
                      }
                    />
                  )
                )}
              </Bar>
            </BarChart>
          ) : (
            <LineChart data={chartData} margin={{ top: 5, right: 5, left: 5, bottom: 5 }}>
              <CartesianGrid strokeDasharray="3 3" stroke="#374151" opacity={0.3} />
              <XAxis
                dataKey="year"
                stroke="#9ca3af"
                tick={{ fill: '#9ca3af', fontSize: 12 }}
                tickFormatter={(v: number) => String(v)}
              />
              <YAxis
                stroke="#9ca3af"
                tick={{ fill: '#9ca3af', fontSize: 12 }}
                tickFormatter={(value: number) => {
                  if (!isAmountsVisible) return '••••';
                  if (Math.abs(value) >= 1_000_000)
                    return `${(value / 1_000_000).toFixed(1)}M`;
                  if (Math.abs(value) >= 1_000) return `${(value / 1_000).toFixed(0)}k`;
                  return value.toString();
                }}
              />
              <Tooltip
                content={<CustomTooltip format={format} currency={effectiveCurrency} />}
                cursor={{ stroke: '#374151', strokeDasharray: '3 3' }}
              />
              <Line
                type="monotone"
                dataKey="amount"
                stroke="#7b68ee"
                strokeWidth={2}
                dot={{ fill: '#7b68ee', r: 4 }}
                activeDot={{ r: 6, fill: '#7b68ee' }}
              />
            </LineChart>
          )}
        </ResponsiveContainer>
      </div>

      {/* Year-by-year breakdown below chart */}
      <div className="mt-4 grid grid-cols-2 sm:grid-cols-3 md:grid-cols-4 lg:grid-cols-6 gap-2 text-xs">
        {chartData.map(
          (d: { year: number; amount: number; variationPercentage: number | null }) => (
            <div
              key={d.year}
              className="bg-surface-elevated rounded-md p-2 text-center"
            >
              <div className="text-text-secondary font-medium">{d.year}</div>
              <div className="text-text-primary font-mono font-semibold truncate">
                <PrivateAmount inline>{format(d.amount, effectiveCurrency)}</PrivateAmount>
              </div>
              {d.variationPercentage != null && (
                <div
                  className={`font-semibold ${
                    d.variationPercentage >= 0 ? 'text-green-500' : 'text-red-500'
                  }`}
                >
                  {d.variationPercentage >= 0 ? '+' : ''}
                  {d.variationPercentage.toFixed(1)}%
                </div>
              )}
            </div>
          )
        )}
      </div>
    </div>
  );
}
