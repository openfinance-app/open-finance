/**
 * PortfolioPerformanceCards Component
 * Task 4.3.8: Create PortfolioPerformanceCards component with sparkline charts
 * 
 * Performance cards with mini sparkline charts
 * Reference: image.png - "Ma performance" cards from Finary dashboard
 */
import { LineChart, Line, ResponsiveContainer } from 'recharts';
import type { IPortfolioPerformance } from '@/types/dashboard';
import { TrendingUp, TrendingDown, Minus } from 'lucide-react';
import { ConvertedAmount } from '../ui/ConvertedAmount';
import { useSecondaryConversion } from '@/hooks/useSecondaryConversion';
import { SPARKLINE_COLORS } from '@/constants/colors';
import { useTranslation } from 'react-i18next';

interface PortfolioPerformanceCardsProps {
  performances: IPortfolioPerformance[];
  /** Human-readable label for the selected period, e.g. "last 30d", "2026-01-01 → 2026-03-03" */
  periodLabel?: string;
}

/**
 * Individual performance card with sparkline
 */
function PerformanceCard({ performance, t }: { performance: IPortfolioPerformance, t: any }) {
  const { label, currentValue, changeAmount, changePercentage, currency, sparklineData } = performance;
  const { convert, secondaryCurrency: secCurrency, secondaryExchangeRate } = useSecondaryConversion(currency);

  // Determine trend direction
  const isPositive = changeAmount >= 0;
  const isNeutral = changeAmount === 0;

  // Prepare sparkline data (recharts expects specific format)
  const chartData = sparklineData.map(point => ({
    value: point.value,
  }));

  // Determine sparkline color based on trend
  const sparklineColor = isPositive
    ? SPARKLINE_COLORS.positive
    : isNeutral
      ? SPARKLINE_COLORS.neutral
      : SPARKLINE_COLORS.negative;

  const TrendIcon = isPositive ? TrendingUp : isNeutral ? Minus : TrendingDown;

  /* Label comes from backend as English string — map via i18n key */
  const translatedLabel = t(`portfolioPerformance.labels.${label}`, { defaultValue: label });

  return (
    <div className="bg-surface rounded-lg p-4 border border-border hover:border-primary/50 transition-colors">
      {/* Label */}
      <p className="text-sm text-text-secondary mb-2">{translatedLabel}</p>

      {/* Current Value */}
      <p className="text-2xl font-bold text-text-primary font-mono mb-1">
        <ConvertedAmount
          amount={currentValue}
          currency={currency}
          isConverted={false}
          secondaryAmount={convert(currentValue)}
          secondaryCurrency={secCurrency}
          secondaryExchangeRate={secondaryExchangeRate}
          inline
        />
      </p>

      {/* Change Indicator */}
      <div className="flex items-center gap-2 mb-3">
        <TrendIcon 
          className={`h-4 w-4 ${
            isPositive ? 'text-green-500' : isNeutral ? 'text-text-muted' : 'text-red-500'
          }`} 
        />
        <span
          className={`text-sm font-semibold ${
            isPositive ? 'text-green-500' : isNeutral ? 'text-text-muted' : 'text-red-500'
          }`}
        >
          {isPositive && '+'}
          <ConvertedAmount
            amount={changeAmount}
            currency={currency}
            isConverted={false}
            secondaryAmount={convert(changeAmount)}
            secondaryCurrency={secCurrency}
            secondaryExchangeRate={secondaryExchangeRate}
            inline
          />
        </span>
        <span
          className={`text-xs ${
            isPositive ? 'text-green-500' : isNeutral ? 'text-text-muted' : 'text-red-500'
          }`}
        >
          ({isPositive && '+'}{changePercentage.toFixed(2)}%)
        </span>
      </div>

      {/* Sparkline Chart */}
      {chartData.length > 0 ? (
        <ResponsiveContainer width="100%" height={50} minWidth={0} debounce={50}>
          <LineChart data={chartData}>
            <Line
              type="monotone"
              dataKey="value"
              stroke={sparklineColor}
              strokeWidth={2}
              dot={false}
              isAnimationActive={false}
            />
          </LineChart>
        </ResponsiveContainer>
      ) : (
        <div className="h-[50px] flex items-center justify-center text-text-secondary text-xs">
          {t('portfolioPerformance.noData')}
        </div>
      )}
    </div>
  );
}

export default function PortfolioPerformanceCards({ performances, periodLabel }: PortfolioPerformanceCardsProps) {
  const { t } = useTranslation('dashboard');

  if (performances.length === 0) {
    return (
      <div className="bg-surface rounded-lg p-6 border border-border h-full flex flex-col">
        <h3 className="text-lg font-semibold text-text-primary mb-4">{t('portfolioPerformance.title')}</h3>
        <div className="flex items-center justify-center h-32 text-text-secondary">
          <div className="text-center">
            <p>{t('portfolioPerformance.empty')}</p>
            <p className="text-xs">{t('portfolioPerformance.emptySub')}</p>
          </div>
        </div>
      </div>
    );
  }

  return (
    <div className="bg-surface rounded-lg p-6 border border-border h-full flex flex-col space-y-4 overflow-y-auto scrollbar-thin">
      {/* Header */}
      <div>
        <h3 className="text-lg font-semibold text-text-primary mb-1">{t('portfolioPerformance.title')}</h3>
        <p className="text-sm text-text-secondary">
          {periodLabel 
            ? t('portfolioPerformance.metricsForPeriod', { period: periodLabel }) 
            : t('portfolioPerformance.subtitle')}
        </p>
      </div>

      {/* Performance Cards Grid */}
      <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 gap-4">
        {performances.map((performance, index) => (
          <PerformanceCard key={`${performance.label}-${index}`} performance={performance} t={t} />
        ))}
      </div>
    </div>
  );
}
