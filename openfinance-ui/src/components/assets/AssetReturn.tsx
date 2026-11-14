import { decimalValue, ExactDecimal } from '@/utils/exact-decimal';
import { useTranslation } from 'react-i18next';
import type { Asset } from '@/types/asset';
import { ConvertedAmount } from '@/components/ui/ConvertedAmount';
import { PrivateAmount } from '@/components/ui/PrivateAmount';
import { formatPercentage, getGainLossColor } from '@/utils/portfolio';

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
  const gain = new ExactDecimal(decimalValue(asset, 'unrealizedGain') ?? 0);
  const percentage =
    asset.gainPercentage == null
      ? t('notApplicable')
      : formatPercentage(asset.gainPercentage * 100);
  return (
    <span className={getGainLossColor(gain.toNumber())}>
      {mode !== 'percentage' && (
        <>
          {gain.gte(0) ? '+' : '-'}
          <ConvertedAmount
            inline
            amount={gain.abs().toFixed()}
            currency={asset.currency}
            convertedAmount={
              asset.isConverted && asset.exchangeRate
                ? gain.abs().times(decimalValue(asset, 'exchangeRate')!).toFixed()
                : undefined
            }
            baseCurrency={asset.baseCurrency}
            exchangeRate={decimalValue(asset, 'exchangeRate') ?? asset.exchangeRate}
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
