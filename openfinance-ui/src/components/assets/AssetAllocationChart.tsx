/**
 * AssetAllocationChart Component
 * Task 5.4.2: Create AssetAllocationChart component
 * 
 * Pie chart showing asset allocation by type with percentages
 */
import { PieChart, Pie, Cell, ResponsiveContainer, Legend, Tooltip } from 'recharts';
import type { AssetAllocation } from '@/utils/portfolio';
import { formatPercentage } from '@/utils/portfolio';
import { useFormatCurrency } from '@/hooks/useFormatCurrency';
import { ASSET_TYPE_COLORS, ASSET_TYPE_COLOR_FALLBACKS } from '@/constants/colors';

interface AssetAllocationChartProps {
  data: AssetAllocation[];
}

/**
 * Get color for asset type
 */
const getColor = (type: string, index: number): string => {
  return ASSET_TYPE_COLORS[type] || ASSET_TYPE_COLOR_FALLBACKS[index % ASSET_TYPE_COLOR_FALLBACKS.length];
};

/**
 * Get display name for asset type
 */
const getAssetTypeLabel = (type: string): string => {
  const labels: Record<string, string> = {
    STOCK: 'Stock',
    ETF: 'ETF',
    CRYPTO: 'Cryptocurrency',
    BOND: 'Bond',
    MUTUAL_FUND: 'Mutual Fund',
    REAL_ESTATE: 'Real Estate',
    COMMODITY: 'Commodity',
    OTHER: 'Other',
  };
  return labels[type] || type;
};

/**
 * Custom tooltip for the pie chart
 */
interface CustomTooltipProps {
  active?: boolean;
  payload?: Array<{
    name: string;
    value: number;
    payload: AssetAllocation;
  }>;
  formatFn: (amount: number, currency?: string | null) => string;
}

function CustomTooltip({ active, payload, formatFn }: CustomTooltipProps) {
  if (active && payload && payload.length > 0) {
    const data = payload[0].payload;
    return (
      <div className="bg-surface border border-border rounded-lg p-3 shadow-lg">
        <p className="text-sm font-medium text-foreground mb-1">
          {getAssetTypeLabel(data.type)}
        </p>
        <p className="text-sm text-muted-foreground">
          Value: {formatFn(data.value)}
        </p>
        <p className="text-sm text-muted-foreground">
          Percentage: {formatPercentage(data.percentage, false)}
        </p>
        <p className="text-sm text-muted-foreground">
          Assets: {data.count}
        </p>
      </div>
    );
  }
  return null;
}

/**
 * Custom label for pie chart slices
 */

function renderCustomLabel({
  cx,
  cy,
  midAngle,
  innerRadius,
  outerRadius,
  percent,
}: any) {
  // Only show label if percentage is >= 5%
  if (percent < 0.05) return null;

  const RADIAN = Math.PI / 180;
  const radius = innerRadius + (outerRadius - innerRadius) * 0.5;
  const x = cx + radius * Math.cos(-midAngle * RADIAN);
  const y = cy + radius * Math.sin(-midAngle * RADIAN);

  return (
    <text
      x={x}
      y={y}
      fill="white"
      textAnchor={x > cx ? 'start' : 'end'}
      dominantBaseline="central"
      className="text-xs font-medium"
    >
      {`${(percent * 100).toFixed(0)}%`}
    </text>
  );
}

/**
 * Custom legend renderer
 */
interface LegendProps {
  payload?: Array<{
    value: string;
    type: string;
    color: string;
    payload: AssetAllocation;
  }>;
}

function CustomLegend({ payload }: LegendProps) {
  if (!payload) return null;

  return (
    <div className="flex flex-wrap justify-center gap-3 mt-4">
      {payload.map((entry, index) => (
        <div key={`legend-${index}`} className="flex items-center gap-2">
          <div
            className="w-3 h-3 rounded-full"
            style={{ backgroundColor: entry.color }}
          />
          <span className="text-xs text-muted-foreground">
            {getAssetTypeLabel(entry.payload.type)}
          </span>
        </div>
      ))}
    </div>
  );
}

export function AssetAllocationChart({ data }: AssetAllocationChartProps) {
  const { format } = useFormatCurrency();
  // Empty state
  if (!data || data.length === 0) {
    return (
      <div className="flex items-center justify-center h-[300px] text-muted-foreground">
        <div className="text-center">
          <PieChart className="h-12 w-12 mx-auto mb-2 text-muted-foreground/30" />
          <p className="text-sm">No allocation data available</p>
        </div>
      </div>
    );
  }

  return (
    <div className="w-full h-[400px]">
      <ResponsiveContainer width="100%" height="100%" minWidth={0}>
        <PieChart>
          <Pie
            data={data}
            cx="50%"
            cy="50%"
            labelLine={false}
            label={renderCustomLabel}
            outerRadius={120}
            innerRadius={60}
            fill="#8884d8"
            dataKey="value"
            animationBegin={0}
            animationDuration={800}
          >
            {data.map((entry, index) => (
              <Cell
                key={`cell-${index}`}
                fill={getColor(entry.type, index)}
                className="stroke-background hover:opacity-80 transition-opacity cursor-pointer"
                strokeWidth={2}
              />
            ))}
          </Pie>
          <Tooltip content={<CustomTooltip formatFn={format} />} />
          <Legend content={<CustomLegend />} />
        </PieChart>
      </ResponsiveContainer>
    </div>
  );
}
