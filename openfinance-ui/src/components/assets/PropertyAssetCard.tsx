import { AssetTradeActions } from '@/components/assets/AssetTradeActions';
import { useTranslation } from 'react-i18next';
import { Home } from 'lucide-react';
import type { Asset } from '@/types/asset';
import { Button } from '@/components/ui/Button';
import { Badge } from '@/components/ui/Badge';
import { Card } from '@/components/ui/Card';
import { ConvertedAmount } from '@/components/ui/ConvertedAmount';
import { multiply } from '@/utils/money';

/** The property record owns editing and lifecycle actions, including its retained backing asset. */
export function PropertyAssetCard({
  asset,
  onView,
  onCompletePurchase,
}: {
  asset: Asset;
  onView: (asset: Asset) => void;
  onCompletePurchase?: (asset: Asset) => void;
}) {
  const { t } = useTranslation('assets');
  const planned = asset.acquisitionType === 'PLANNED';
  const value = planned ? asset.totalCost : asset.totalValue;
  return (
    <Card className="p-5 space-y-4" id={`asset-${asset.id}`}>
      <div className="flex items-start gap-3">
        <Home className="mt-1 h-5 w-5 shrink-0 text-primary" aria-hidden="true" />
        <div className="min-w-0 flex-1">
          <h4 className="font-medium break-words">{asset.name}</h4>
          {(asset.ownershipStatus === 'CLOSED' || asset.isActive === false) && (
            <Badge variant="default" size="sm">
              {t(asset.ownershipStatus === 'CLOSED' ? 'ownership.sold' : 'inactive')}
            </Badge>
          )}
          {planned && (
            <Badge variant="default" size="sm">
              {t('form.acquisitionTypes.PLANNED')}
            </Badge>
          )}
        </div>
      </div>
      <div>
        <p className="text-sm text-text-secondary">
          {t(planned ? 'form.targetPrice' : 'detail.metrics.currentValue')}
        </p>
        <ConvertedAmount
          amount={value}
          currency={asset.currency}
          convertedAmount={
            asset.isConverted && asset.exchangeRate
              ? multiply(value, asset.exchangeRate)
              : undefined
          }
          baseCurrency={asset.baseCurrency}
          exchangeRate={asset.exchangeRate}
          isConverted={asset.isConverted}
          className="text-xl font-semibold"
        />
      </div>
      <div className="flex flex-wrap items-center justify-end gap-2">
        <AssetTradeActions asset={asset} onCompletePurchase={onCompletePurchase} />
        <Button variant="secondary" size="sm" onClick={() => onView(asset)}>
          {t('manageProperty')}
        </Button>
      </div>
    </Card>
  );
}
