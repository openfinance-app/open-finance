import { useMemo } from 'react';
import { ResponsiveContainer, Treemap, Tooltip } from 'recharts';
import { useTranslation } from 'react-i18next';
import type { INetWorthAllocation } from '@/types/dashboard';
import { PrivateAmount } from '../ui/PrivateAmount';
import {
  NET_WORTH_TREEMAP_LIABILITY_COLORS,
  NET_WORTH_TREEMAP_ASSET_COLORS,
} from '@/constants/colors';
import { HelpTooltip } from '@/components/ui/HelpTooltip';

interface NetWorthAllocationChartProps {
    allocations: INetWorthAllocation[];
    currency: string;
}

const CustomTooltip = ({ active, payload, formatFn }: any) => {
    if (active && payload && payload.length) {
        const data = payload[0].payload;
        return (
            <div className="bg-popover text-popover-foreground p-2 rounded-md shadow-md text-xs border">
                <p className="font-semibold">{data.name}</p>
                <p>
                    <PrivateAmount inline>
                        {formatFn(data.originalValue, data.currency)}
                    </PrivateAmount>
                </p>
                <p>{Math.abs(data.percentage ?? 0).toFixed(1)}%</p>
                <p className={data.isLiability ? 'text-red-500' : 'text-green-500'}>
                    {data.isLiability ? formatFn('liability') : formatFn('asset')}
                </p>
            </div>
        );
    }
    return null;
};

const CustomizedContent = (props: any) => {
    const { depth, x, y, width, height, index, name, percentage } = props;

    return (
        <g>
            <rect
                x={x}
                y={y}
                width={width}
                height={height}
                style={{
                    fill: props.isLiability
                        ? NET_WORTH_TREEMAP_LIABILITY_COLORS[index % 3] // Red shades for liabilities
                        : NET_WORTH_TREEMAP_ASSET_COLORS[index % 5], // Diverse colors for assets
                    stroke: '#fff',
                    strokeWidth: 2 / (depth + 1e-10),
                    strokeOpacity: 1 / (depth + 1e-10),
                }}
            />
            {width > 50 && height > 30 && (
                <text
                    x={x + width / 2}
                    y={y + height / 2}
                    textAnchor="middle"
                    fill="#fff"
                    fontSize={12}
                >
                    {name}
                </text>
            )}
            {width > 50 && height > 30 && (
                <text
                    x={x + width / 2}
                    y={y + height / 2 + 14}
                    textAnchor="middle"
                    fill="#fff"
                    fontSize={10}
                >
                    {percentage != null ? Math.abs(percentage).toFixed(1) : '0.0'}%
                </text>
            )}
        </g>
    );
};

export default function NetWorthAllocationChart({ allocations, currency: _currency }: NetWorthAllocationChartProps) {
    const { t } = useTranslation('dashboard');
    // Determine if we have any data
    const hasData = allocations && allocations.length > 0;

    // Transform data for Treemap
    // We need to handle liabilities (negative values) for visualization
    // Treemaps generally expect positive values for sizing. 
    // We'll use absolute values for size, but color code them.
    const chartData = useMemo(() => {
        if (!allocations) return [];
        return allocations.map((item) => ({
            name: t(`allocationCategories.${item.category.replace(/[^a-zA-Z0-9]/g, '_')}`, { defaultValue: item.category }),
            value: Math.abs(item.value), // Use absolute value for chart sizing
            originalValue: item.value,
            percentage: item.percentage,
            isLiability: item.isLiability,
            currency: item.currency,
        }));
    }, [allocations, t]);

    if (!hasData) {
        return (
            <div className="bg-surface rounded-lg p-6 border border-border h-full flex flex-col">
                <div className="mb-4">
                    <h3 className="text-lg font-semibold text-text-primary">{t('netWorthAllocation.title')}</h3>
                    <p className="text-sm text-text-secondary">{t('netWorthAllocation.noData')}</p>
                </div>
                <div className="h-[300px] flex items-center justify-center text-text-muted">
                    {t('netWorthAllocation.emptyDescription')}
                </div>
            </div>
        );
    }

    return (
        <div className="bg-surface rounded-lg p-6 border border-border h-full flex flex-col">
            <div className="flex items-center gap-1 mb-6">
                <h3 className="text-lg font-semibold text-text-primary">{t('netWorthAllocation.title')}</h3>
                <HelpTooltip text={t('netWorthAllocation.tooltip')} side="right" />
            </div>
            <div className="w-full h-[300px]">
                <ResponsiveContainer width="100%" height="100%" minWidth={0} debounce={50}>
                    <Treemap
                        data={chartData}
                        dataKey="value"
                        aspectRatio={4 / 3}
                        stroke="#fff"
                        fill="#8884d8"
                        content={<CustomizedContent />}
                    >
                        <Tooltip content={<CustomTooltip formatFn={(key: string) => t(`netWorthAllocation.${key}`)} />} />
                    </Treemap>
                </ResponsiveContainer>
            </div>

            {/* Legend */}
            <div className="mt-4 grid grid-cols-2 md:grid-cols-3 gap-2 text-sm">
                {allocations.map((item, index) => (
                    <div key={item.category} className="flex items-center gap-2">
                        <div
                            className="w-3 h-3 rounded-full"
                            style={{
                                backgroundColor: item.isLiability
                                    ? NET_WORTH_TREEMAP_LIABILITY_COLORS[index % 3]
                                    : NET_WORTH_TREEMAP_ASSET_COLORS[index % 5]
                            }}
                        />
                        <span className="truncate text-text-secondary">{t(`allocationCategories.${item.category.replace(/[^a-zA-Z0-9]/g, '_')}`, { defaultValue: item.category })}</span>
                        <span className="text-text-muted ml-auto">{Math.abs(item.percentage ?? 0).toFixed(1)}%</span>
                    </div>
                ))}
            </div>
        </div>
    );
}
