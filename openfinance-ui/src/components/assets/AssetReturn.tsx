import { useTranslation } from 'react-i18next';
import type { Asset } from '@/types/asset';
import { ConvertedAmount } from '@/components/ui/ConvertedAmount';
import { PrivateAmount } from '@/components/ui/PrivateAmount';
import { formatPercentage, getGainLossColor } from '@/utils/portfolio';
import { multiply } from '@/utils/money';

/** A missing baseline is unknown, never a zero return or a gift counted as profit. */
export function AssetReturn({
  asset,
  mode = 'both',
}: {
  asset: Asset;
  mode?: 'amount' | 'percentage' | 'both';
}) {
  const { t } = useTranslation('assets');
  if (
    asset.ownershipStatus === 'CLOSED' ||
    asset.unrealizedGain == null ||
    (mode === 'percentage' && asset.gainPercentage == null)
  ) {
    return <span className="text-text-muted">{t('notApplicable')}</span>;
  }
  const gain = asset.unrealizedGain;
  const percentage =
    asset.gainPercentage == null
      ? t('notApplicable')
      : formatPercentage(asset.gainPercentage * 100);
  return (
    <span className={getGainLossColor(gain)}>
      {mode !== 'percentage' && (
        <>
          {gain >= 0 ? '+' : '-'}
          <ConvertedAmount
            inline
            amount={Math.abs(gain)}
            currency={asset.currency}
            convertedAmount={
              asset.isConverted && asset.exchangeRate
                ? multiply(Math.abs(gain), asset.exchangeRate)
                : undefined
            }
            baseCurrency={asset.baseCurrency}
            exchangeRate={asset.exchangeRate}
            isConverted={asset.isConverted}
          />
        </>
      )}
      {mode !== 'amount' && (
        <PrivateAmount>{mode === 'both' ? ` (${percentage})` : percentage}</PrivateAmount>
      )}
    </span>
  );
}
