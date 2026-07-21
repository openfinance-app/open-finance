import { BarChart, Bar, XAxis, YAxis, Tooltip, ResponsiveContainer, CartesianGrid } from 'recharts';
import { useTranslation } from 'react-i18next';
import type { ICashFlow } from '../../types/dashboard';
import { useFormatCurrency } from '@/hooks/useFormatCurrency';
import { DEFAULT_CURRENCY } from '@/utils/currency';
import { PrivateAmount } from '../ui/PrivateAmount';
import { HelpTooltip } from '@/components/ui/HelpTooltip';
import { useVisibility } from '../../context/VisibilityContext';

interface CashFlowChartProps {
  cashFlow: ICashFlow;
  period?: number;
  currency?: string;
}

// Custom tooltip
const CustomTooltip = ({ active, payload, format, currency }: any) => {
  if (active && payload && payload.length) {
    const data = payload[0].payload;
    return (
      <div className="bg-surface-elevated border border-border rounded-lg p-3 shadow-lg">
        <p className="text-text-primary font-semibold text-sm mb-1">{data.name}</p>
        <p className="text-primary font-mono text-sm">
          <PrivateAmount inline>
            {format(data.amount, currency)}
          </PrivateAmount>
        </p>
      </div>
    );
  }
  return null;
};

/**
 * CashFlowChart - Bar chart showing income vs expenses
 * 
 * Design:
 * - Green bars for income, red bars for expenses
 * - Dark theme with minimal grid lines
 * - Tooltip on hover with exact values
 */
export default function CashFlowChart({ 
  cashFlow, 
  period = 30,
  currency = DEFAULT_CURRENCY 
}: CashFlowChartProps) {
  const { t } = useTranslation('dashboard');
  const { isAmountsVisible } = useVisibility();
  const { format } = useFormatCurrency();
  // Prepare data for the chart
  const data = [
    {
      name: t('cashFlowChart.income'),
      amount: cashFlow.income,
      fill: '#10b981', // green-500
    },
    {
      name: t('cashFlowChart.expenses'),
      amount: cashFlow.expenses,
      fill: '#ef4444', // red-500
    },
  ];

  return (
    <div className="bg-surface rounded-lg p-6 border border-border h-full flex flex-col">
      <div className="flex items-center justify-between mb-4">
        <div className="flex items-center gap-1">
          <h3 className="text-lg font-semibold text-text-primary">{t('cashFlowChart.title')}</h3>
          <HelpTooltip
            text={t('cashFlowChart.tooltip')}
            side="right"
          />
        </div>
        <span className="text-xs text-text-secondary">{t('cashFlowChart.lastDays', { days: period })}</span>
      </div>

      {/* Net Cash Flow Summary */}
      <div className="mb-6 p-4 bg-surface-elevated rounded-lg">
        <div className="text-xs text-text-secondary mb-1">{t('cashFlowChart.net')}</div>
        <div className={`text-2xl font-bold font-mono ${
          cashFlow.netCashFlow >= 0 ? 'text-green-500' : 'text-red-500'
        }`}>
          {cashFlow.netCashFlow >= 0 ? '+' : ''}
          <PrivateAmount inline>
            {format(cashFlow.netCashFlow, currency)}
          </PrivateAmount>
        </div>
      </div>

      {/* Chart */}
      <div className="w-full h-[300px]">
        <ResponsiveContainer width="100%" height="100%" minWidth={0} debounce={50}>
          <BarChart data={data} margin={{ top: 5, right: 5, left: 5, bottom: 5 }}>
            <CartesianGrid strokeDasharray="3 3" stroke="#374151" opacity={0.3} />
            <XAxis 
              dataKey="name" 
              stroke="#9ca3af"
              tick={{ fill: '#9ca3af', fontSize: 12 }}
            />
            <YAxis 
              stroke="#9ca3af"
              tick={{ fill: '#9ca3af', fontSize: 12 }}
              tickFormatter={(value) => {
                if (!isAmountsVisible) {
                  return '••••';
                }
                if (value >= 1000) {
                  return `${(value / 1000).toFixed(0)}k`;
                }
                return value.toString();
              }}
            />
            <Tooltip 
              content={<CustomTooltip format={format} currency={currency} />} 
              cursor={{ fill: '#374151', opacity: 0.2 }} 
            />
            <Bar dataKey="amount" radius={[8, 8, 0, 0]} maxBarSize={100} />
          </BarChart>
        </ResponsiveContainer>
      </div>

      {/* Income/Expense Breakdown */}
      <div className="mt-4 grid grid-cols-2 gap-4 text-sm">
        <div className="flex items-center justify-between">
          <span className="text-text-secondary">{t('cashFlowChart.income')}:</span>
          <span className="text-green-500 font-semibold font-mono">
            <PrivateAmount inline>
              {format(cashFlow.income, currency)}
            </PrivateAmount>
          </span>
        </div>
        <div className="flex items-center justify-between">
          <span className="text-text-secondary">{t('cashFlowChart.expenses')}:</span>
          <span className="text-red-500 font-semibold font-mono">
            <PrivateAmount inline>
              {format(cashFlow.expenses, currency)}
            </PrivateAmount>
          </span>
        </div>
      </div>
    </div>
  );
}
