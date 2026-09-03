/**
 * AssetAllocationChart Component
 * Task 4.3.6: Create AssetAllocationChart component with treemap visualization
 * 
 * Treemap showing asset allocation by type/category
 * Reference: image.png - Finary dashboard treemap
 */
import { Treemap, ResponsiveContainer, Tooltip } from 'recharts';
import type { IAssetAllocation } from '@/types/dashboard';
import { useFormatCurrency } from '@/hooks/useFormatCurrency';
import { ConvertedAmount } from '@/components/ui/ConvertedAmount';
import { useVisibility } from '@/context/VisibilityContext';
import { useTranslation } from 'react-i18next';
import { useNavigate } from 'react-router';
import { HelpTooltip } from '@/components/ui/HelpTooltip';

interface AssetAllocationChartProps {
  allocations: IAssetAllocation[];
  currency: string;
}

/**
 * Color map for different asset types
 * Based on design system chart colors (design.md Section 1.3.2)
 */
const ASSET_TYPE_COLORS: Record<string, string> = {
  STOCK: '#7b68ee',        // Purple
  ETF: '#9c27b0',          // Deep Purple
  CRYPTO: '#f5a623',       // Gold
  BOND: '#4a90e2',         // Blue
  MUTUAL_FUND: '#00c9a7',  // Teal
  REAL_ESTATE: '#ff6b7a',  // Coral
  COMMODITY: '#e91e63',    // Pink
  VEHICLE: '#ff9800',      // Orange
  JEWELRY: '#ffd700',      // Gold
  COLLECTIBLE: '#9575cd',  // Light Purple
  ELECTRONICS: '#64b5f6',  // Light Blue
  FURNITURE: '#81c784',    // Light Green
  OTHER: '#b0bec5',        // Gray
};

/**
 * Custom tooltip for treemap
 */
const CustomTooltip = ({ active, payload }: any) => {
  if (active && payload && payload.length) {
    const data = payload[0].payload;
    return (
      <div className="bg-surface rounded-lg shadow-lg p-4 border border-border">
        <p className="text-sm font-semibold text-text-primary mb-2">
          {data.typeName}
        </p>
        <p className="text-lg font-bold text-text-primary mb-1">
          <ConvertedAmount amount={data.totalValue} currency={data.currency} inline />
        </p>
        <p className="text-sm text-text-secondary mb-1">
          {data.percentage}% of portfolio
        </p>
        <p className="text-xs text-text-muted">
          {data.assetCount} {data.assetCount === 1 ? 'asset' : 'assets'}
        </p>
      </div>
    );
  }
  return null;
};

/**
 * Custom content renderer for treemap cells
 */
const CustomContent = (props: any) => {
  const { x, y, width, height, typeName, percentage, totalValue, currency, type, isVisible, formatFn, onNavigate } = props;

  // Only show text if cell is large enough
  const showText = width > 80 && height > 50;
  const showPercentage = width > 100 && height > 60;
  const showValue = width > 120 && height > 70 && isVisible;

  return (
    <g onClick={() => onNavigate?.(type)} style={{ cursor: 'pointer' }}>
      <rect
        x={x}
        y={y}
        width={width}
        height={height}
        style={{
          fill: ASSET_TYPE_COLORS[type] || ASSET_TYPE_COLORS.OTHER,
          stroke: '#1a1a1a',
          strokeWidth: 2,
          opacity: 0.9,
        }}
      />
      {showText && (
        <g>
          <text
            x={x + width / 2}
            y={y + height / 2 - (showValue ? 15 : showPercentage ? 10 : 0)}
            textAnchor="middle"
            fill="#ffffff"
            fontSize={14}
            fontWeight="600"
          >
            {typeName}
          </text>
          {showPercentage && (
            <text
              x={x + width / 2}
              y={y + height / 2 + 5}
              textAnchor="middle"
              fill="#ffffff"
              fontSize={12}
              opacity={0.9}
            >
              {percentage}%
            </text>
          )}
          {showValue && (
            <text
              x={x + width / 2}
              y={y + height / 2 + 22}
              textAnchor="middle"
              fill="#ffffff"
              fontSize={11}
              opacity={0.8}
            >
              {formatFn(totalValue, currency)}
            </text>
          )}
        </g>
      )}
    </g>
  );
};

export default function AssetAllocationChart({ allocations, currency: _currency }: AssetAllocationChartProps) {
  const { isAmountsVisible } = useVisibility();
  const { format } = useFormatCurrency();
  const { t } = useTranslation('dashboard');
  const navigate = useNavigate();
  const navigateToType = (type: string) => navigate(`/assets?type=${encodeURIComponent(type)}`);
  // Prepare data for treemap (recharts expects 'name' and 'size' fields)
  const treemapData = allocations.map(allocation => {
    const translatedType = t(`assetTypes.${allocation.type}`, { defaultValue: allocation.typeName });
    return {
      name: translatedType,
      size: allocation.totalValue,
      typeName: translatedType,
      type: allocation.type,
      percentage: allocation.percentage.toFixed(2),
      totalValue: allocation.totalValue,
      assetCount: allocation.assetCount,
      currency: allocation.currency,
    };
  });

  if (treemapData.length === 0) {
    return (
      <div className="bg-surface rounded-lg p-6 border border-border h-full flex flex-col">
        <h3 className="text-lg font-semibold text-text-primary mb-4">{t('assetAllocation.title')}</h3>
        <div className="flex items-center justify-center flex-1 min-h-0 text-text-secondary">
          <p>{t('assetAllocation.empty')} {t('assetAllocation.emptySub')}</p>
        </div>
      </div>
    );
  }

  return (
    <div className="bg-surface rounded-lg p-6 border border-border h-full flex flex-col">
      {/* Header */}
      <div className="flex items-center gap-1 mb-6">
        <h3 className="text-lg font-semibold text-text-primary">{t('assetAllocation.title')}</h3>
        <HelpTooltip text={t('assetAllocation.tooltip')} side="right" />
      </div>

      {/* Treemap Chart */}
      <div className="w-full h-[300px]">
        <ResponsiveContainer width="100%" height="100%" minWidth={0} debounce={50}>
          <Treemap
            data={treemapData}
            dataKey="size"
            aspectRatio={4 / 3}
            stroke="#1a1a1a"
            fill="#8884d8"
            content={
              <CustomContent
                isVisible={isAmountsVisible}
                formatFn={format}
                onNavigate={navigateToType}
              />
            }
          >
            <Tooltip content={<CustomTooltip />} />
          </Treemap>
        </ResponsiveContainer>
      </div>

      {/* Legend */}
      <div className="mt-6 grid grid-cols-2 sm:grid-cols-3 md:grid-cols-4 gap-3">
        {allocations.map(allocation => (
          <button
            type="button"
            key={allocation.type}
            onClick={() => navigateToType(allocation.type)}
            className="flex items-center gap-2 text-left rounded hover:bg-surface-elevated transition-colors"
            aria-label={t('assetAllocation.viewAssetType', {
              type: t(`assetTypes.${allocation.type}`, { defaultValue: allocation.typeName }),
            })}
          >
            <div
              className="w-4 h-4 rounded"
              style={{ backgroundColor: ASSET_TYPE_COLORS[allocation.type] || ASSET_TYPE_COLORS.OTHER }}
            />
            <div className="flex-1 min-w-0">
              <p className="text-xs text-text-secondary truncate">{t(`assetTypes.${allocation.type}`, { defaultValue: allocation.typeName })}</p>
              <p className="text-sm font-semibold text-text-primary">
                {allocation.percentage.toFixed(1)}%
              </p>
            </div>
          </button>
        ))}
      </div>
    </div>
  );
}
