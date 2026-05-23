import { TrendingUp, TrendingDown } from 'lucide-react';
import type { INetWorthSummary } from '../../types/dashboard';
import { formatDate } from '../../utils/date';
import { ConvertedAmount } from '@/components/ui/ConvertedAmount';
import { PrivateAmount } from '@/components/ui/PrivateAmount';
import { HelpTooltip } from '@/components/ui/HelpTooltip';
import { useUserSettings } from '@/hooks/useUserSettings';
import { useSecondaryConversion } from '@/hooks/useSecondaryConversion';
import { useTranslation } from 'react-i18next';

interface NetWorthCardProps {
  netWorth: INetWorthSummary;
  /** Human-readable label for the selected period, e.g. "Last 30d", "2026-01-01 → 2026-03-03" */
  periodLabel?: string;
  /** Override computed from the history chart's first/last data point, takes precedence over API monthlyChange fields */
  periodChange?: { amount: number; percentage: number } | null;
}

/**
 * NetWorthCard - Large prominent display of current net worth with trend
 *
 * Design based on Finary dashboard reference:
 * - Large net worth number (48px+, bold, white, monospace)
 * - Date label above (text-secondary, small)
 * - Change indicator with amount and percentage (green/red)
 * - Up/down arrow icon
 * - "vs {periodLabel}" suffix driven by the global period selector
 */
export default function NetWorthCard({ netWorth, periodLabel = 'last month', periodChange }: NetWorthCardProps) {
  const { t } = useTranslation('dashboard');
  const { data: settings } = useUserSettings();
  const { convert, secondaryCurrency: secCurrency, secondaryExchangeRate } = useSecondaryConversion(netWorth.currency);
  // When periodChange is explicitly null, no comparison data exists for this period — don't fall back to monthly.
  // Only use monthly fallback when periodChange is undefined (prop not passed).
  const changeAmount = periodChange === null ? null : (periodChange?.amount ?? netWorth.monthlyChangeAmount);
  const changePercentage = periodChange === null ? null : (periodChange?.percentage ?? netWorth.monthlyChangePercentage);
  const hasComparison = changeAmount != null && changePercentage != null;
  const isPositiveChange = hasComparison && (changeAmount ?? 0) >= 0;
  const changeColor = isPositiveChange ? 'text-green-500' : 'text-red-500';
  const ChangeIcon = isPositiveChange ? TrendingUp : TrendingDown;

  return (
    <div className="bg-surface rounded-lg p-6 border border-border hover:border-border/70 transition-colors h-full flex flex-col justify-between">
      {/* Date Label */}
      <div className="text-xs text-text-secondary mb-2">
        {formatDate(netWorth.date, settings?.dateFormat)}
      </div>

      {/* Net Worth Value */}
      <div className="mb-4">
        <ConvertedAmount
          amount={netWorth.netWorth}
          currency={netWorth.currency}
          isConverted={false}
          secondaryAmount={convert(netWorth.netWorth)}
          secondaryCurrency={secCurrency}
          secondaryExchangeRate={secondaryExchangeRate}
          className="text-5xl font-bold text-text-primary font-mono tracking-tight"
        />
        <div className="flex items-center gap-1 text-sm text-text-secondary mt-1">
          {t('metrics.netWorth')}
          <HelpTooltip
            text={t('metrics.netWorthTooltip')}
            side="right"
          />
        </div>
      </div>

      {/* Change Indicator */}
      {hasComparison ? (
        <div className={`flex items-center gap-2 ${changeColor}`}>
          <ChangeIcon className="h-5 w-5" />
          <span className="font-semibold font-mono inline-flex items-baseline gap-0">
            {isPositiveChange ? '+' : ''}
            <ConvertedAmount
              amount={changeAmount!}
              currency={netWorth.currency}
              isConverted={false}
              secondaryAmount={convert(changeAmount!)}
              secondaryCurrency={secCurrency}
              secondaryExchangeRate={secondaryExchangeRate}
              inline
            />
          </span>
          <PrivateAmount inline className="font-semibold">
            ({isPositiveChange ? '+' : ''}
            {changePercentage!.toFixed(2)}%)
          </PrivateAmount>
          <span className="text-text-secondary text-sm ml-auto">{t('metrics.vsPeriod', { period: periodLabel })}</span>
        </div>
      ) : (
        <div className="flex items-center gap-2 text-text-muted text-sm">
          <span>{t('metrics.noComparisonData', 'Nouveau')}</span>
        </div>
      )}

      {/* Asset & Liability Breakdown */}
      <div className="mt-6 pt-4 border-t border-border grid grid-cols-2 gap-4">
        <div>
          <div className="flex items-center gap-1 text-xs text-text-secondary mb-1">
            {t('metrics.totalAssets')}
            <HelpTooltip text={t('metrics.totalAssetsTooltip')} />
          </div>
          <ConvertedAmount
            amount={netWorth.totalAssets}
            currency={netWorth.currency}
            isConverted={false}
            secondaryAmount={convert(netWorth.totalAssets)}
            secondaryCurrency={secCurrency}
            secondaryExchangeRate={secondaryExchangeRate}
            className="text-lg font-semibold text-text-primary font-mono"
          />
        </div>
        <div>
          <div className="flex items-center gap-1 text-xs text-text-secondary mb-1">
            {t('metrics.totalLiabilities')}
            <HelpTooltip text={t('metrics.totalLiabilitiesTooltip')} />
          </div>
          <ConvertedAmount
            amount={netWorth.totalLiabilities}
            currency={netWorth.currency}
            isConverted={false}
            secondaryAmount={convert(netWorth.totalLiabilities)}
            secondaryCurrency={secCurrency}
            secondaryExchangeRate={secondaryExchangeRate}
            className={`text-lg font-semibold font-mono ${netWorth.totalLiabilities > 0 ? 'text-red-500' : 'text-text-primary'}`}
          />
        </div>
      </div>
    </div>
  );
}
