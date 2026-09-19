import { TrendingUp, TrendingDown } from 'lucide-react';
import type { INetWorthSummary } from '../../types/dashboard';
import { formatDate } from '../../utils/date';
import { formatPercentage } from '@/utils/format';
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
 * NetWorthCard — the master instrument plate of the vault wall.
 * Guilloché engraving behind one calibrated readout; brass delta markers;
 * hairline rule separating the asset/liability sub-plates.
 */
export default function NetWorthCard({
  netWorth,
  periodLabel = 'last month',
  periodChange,
}: NetWorthCardProps) {
  const { t } = useTranslation('dashboard');
  const { data: settings } = useUserSettings();
  const {
    convert,
    secondaryCurrency: secCurrency,
    secondaryExchangeRate,
  } = useSecondaryConversion(netWorth.currency);
  // When periodChange is explicitly null, no comparison data exists for this period — don't fall back to monthly.
  // Only use monthly fallback when periodChange is undefined (prop not passed).
  const changeAmount =
    periodChange === null ? null : (periodChange?.amount ?? netWorth.monthlyChangeAmount);
  const changePercentage =
    periodChange === null ? null : (periodChange?.percentage ?? netWorth.monthlyChangePercentage);
  const hasComparison = changeAmount != null && changePercentage != null;
  const isPositiveChange = hasComparison && (changeAmount ?? 0) >= 0;
  const changeColor = isPositiveChange ? 'gain-positive' : 'gain-negative';
  const ChangeIcon = isPositiveChange ? TrendingUp : TrendingDown;

  return (
    <div className="relative overflow-hidden bg-surface rounded-[var(--radius-card)] p-6 border border-border shadow-plate hover:border-border-strong transition-colors h-full flex flex-col justify-between">
      {/* Engraved security rosette behind the readout */}
      <div
        aria-hidden="true"
        className="bg-guilloche pointer-events-none absolute -right-10 -top-10 h-64 w-64 opacity-70"
      />

      {/* Date Label — engraved plate marking */}
      <div className="plate-label relative mb-2">
        {formatDate(netWorth.date, settings?.dateFormat)}
      </div>

      {/* Net Worth Value — the calibrated readout */}
      <div className="relative mb-4">
        <ConvertedAmount
          amount={netWorth.netWorth}
          currency={netWorth.currency}
          isConverted={false}
          secondaryAmount={convert(netWorth.netWorth)}
          secondaryCurrency={secCurrency}
          secondaryExchangeRate={secondaryExchangeRate}
          animate
          className="text-[44px] leading-none font-bold text-text-primary font-mono tracking-[-0.02em]"
        />
        <div className="flex items-center gap-1 text-sm text-text-secondary mt-2">
          {t('metrics.netWorth')}
          <HelpTooltip text={t('metrics.netWorthTooltip')} side="right" />
        </div>
      </div>

      {/* Change Indicator */}
      {hasComparison ? (
        <div className={`relative flex items-center gap-2 ${changeColor}`}>
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
            {formatPercentage(changePercentage!)})
          </PrivateAmount>
          <span className="text-text-secondary text-sm ml-auto">
            {t('metrics.vsPeriod', { period: periodLabel })}
          </span>
        </div>
      ) : (
        <div className="relative flex items-center gap-2 text-text-muted text-sm">
          <span>{t('metrics.noComparisonData')}</span>
        </div>
      )}

      {/* Asset & Liability sub-plates */}
      <div className="relative mt-6 pt-4 border-t border-border grid grid-cols-2 gap-4">
        <div>
          <div className="flex items-center gap-1 plate-label mb-1.5">
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
          <div className="flex items-center gap-1 plate-label mb-1.5">
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
            className={`text-lg font-semibold font-mono ${netWorth.totalLiabilities > 0 ? 'gain-negative' : 'text-text-primary'}`}
          />
        </div>
      </div>
    </div>
  );
}
