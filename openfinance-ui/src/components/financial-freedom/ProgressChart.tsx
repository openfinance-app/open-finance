import {
  Line,
  XAxis,
  YAxis,
  CartesianGrid,
  Tooltip,
  Legend,
  ResponsiveContainer,
  ReferenceLine,
  Area,
  ComposedChart,
} from 'recharts';
import { getCurrencySymbol } from '@/utils/currency';
import { ConvertedAmount } from '@/components/ui/ConvertedAmount';
import type { YearlyProjection } from '../../types/calculator';

interface ProgressChartProps {
  data: YearlyProjection[];
  currentSavings: number;
  targetAmount: number;
  currency: string;
  yearsToFreedom?: number;
}

/**
 * ProgressChart Component
 * 
 * Visualizes the path to financial freedom showing:
 * - Projected savings growth over time
 * - Target amount reference line
 * - Current savings starting point
 * - Years until financial freedom
 */
export function ProgressChart({
  data,
  currentSavings,
  targetAmount,
  currency,
  yearsToFreedom,
}: ProgressChartProps) {
  // Prepare chart data
  const chartData = data.map((item) => ({
    year: item.year,
    savings: item.endingBalance,
    contributions: item.contributions,
    target: targetAmount,
    isFreedomYear: yearsToFreedom && item.year <= yearsToFreedom,
  }));



  // Custom tooltip component
  const CustomTooltip = ({ active, payload, label }: {
    active?: boolean;
    payload?: Array<{ value: number; dataKey: string; color: string }>;
    label?: string;
  }) => {
    if (active && payload && payload.length) {
      return (
        <div className="bg-surface border border-border rounded-lg p-3 shadow-lg">
          <p className="font-medium text-text-primary mb-2">Year {label}</p>
          {payload.map((entry, index) => (
            <p key={index} className="text-sm" style={{ color: entry.color }}>
              {entry.dataKey === 'target' ? 'Target Amount: ' : `${entry.dataKey}: `}
              <ConvertedAmount amount={entry.value} currency={currency} inline />
            </p>
          ))}
        </div>
      );
    }
    return null;
  };

  return (
    <div className="h-[400px] w-full">
      <ResponsiveContainer width="100%" height={400} minWidth={0}>
        <ComposedChart
          data={chartData}
          margin={{ top: 20, right: 30, left: 20, bottom: 20 }}
        >
          <CartesianGrid strokeDasharray="3 3" stroke="var(--color-border)" />
          <XAxis
            dataKey="year"
            stroke="var(--color-text-secondary)"
            fontSize={12}
            tickLine={false}
            axisLine={{ stroke: 'var(--color-border)' }}
            label={{
              value: 'Years from Now',
              position: 'insideBottom',
              offset: -10,
              fill: 'var(--color-text-secondary)',
              fontSize: 12,
            }}
          />
          <YAxis
            stroke="var(--color-text-secondary)"
            fontSize={12}
            tickLine={false}
            axisLine={{ stroke: 'var(--color-border)' }}
            tickFormatter={(value) => {
              const sym = getCurrencySymbol(currency);
              if (value >= 1000000) {
                return `${sym}${(value / 1000000).toFixed(1)}M`;
              }
              if (value >= 1000) {
                return `${sym}${(value / 1000).toFixed(0)}K`;
              }
              return `${sym}${value}`;
            }}
          />
          <Tooltip content={<CustomTooltip />} />
          <Legend
            wrapperStyle={{ paddingTop: '20px' }}
            formatter={(value) => (
              <span style={{ color: 'var(--color-text-primary)', fontSize: 12 }}>
                {value}
              </span>
            )}
          />

          {/* Target Amount Reference Line */}
          <ReferenceLine
            y={targetAmount}
            stroke="var(--color-warning)"
            strokeDasharray="5 5"
            label={{
              value: 'Target',
              position: 'insideTopRight',
              fill: 'var(--color-warning)',
              fontSize: 12,
            }}
          />

          {/* Current Savings Starting Point */}
          <ReferenceLine
            y={currentSavings}
            stroke="var(--color-primary)"
            strokeWidth={2}
            label={{
              value: 'Current',
              position: 'insideBottomRight',
              fill: 'var(--color-primary)',
              fontSize: 12,
            }}
          />

          {/* Financial Freedom Year Vertical Line */}
          {yearsToFreedom && yearsToFreedom > 0 && (
            <ReferenceLine
              x={yearsToFreedom}
              stroke="var(--color-success)"
              strokeWidth={2}
              label={{
                value: 'Freedom!',
                position: 'insideTopLeft',
                fill: 'var(--color-success)',
                fontSize: 12,
              }}
            />
          )}

          {/* Area for savings growth */}
          <Area
            type="monotone"
            dataKey="savings"
            name="Projected Savings"
            fill="var(--color-primary)"
            fillOpacity={0.2}
            stroke="var(--color-primary)"
            strokeWidth={2}
            dot={false}
          />

          {/* Line for total contributions */}
          <Line
            type="monotone"
            dataKey="contributions"
            name="Total Contributions"
            stroke="var(--color-text-secondary)"
            strokeWidth={2}
            strokeDasharray="5 5"
            dot={false}
          />
        </ComposedChart>
      </ResponsiveContainer>
    </div>
  );
}
