import { useState } from 'react';
import { ArrowDownLeft, ArrowUpRight } from 'lucide-react';
import { useTranslation } from 'react-i18next';
import { Button } from '@/components/ui/Button';
import { Dialog, DialogContent, DialogHeader, DialogTitle } from '@/components/ui/Dialog';
import { Tooltip, TooltipContent, TooltipProvider, TooltipTrigger } from '@/components/ui/Tooltip';
import { AssetBuyForm } from '@/components/assets/AssetBuyForm';
import { AssetSaleForm, type SaleTarget } from '@/components/assets/AssetSaleForm';
import { decimalField } from '@/utils/exact-decimal';
import type { Asset } from '@/types/asset';

export function AssetTradeActions({
  asset,
  onCompletePurchase,
}: {
  asset: Asset;
  onCompletePurchase?: (asset: Asset) => void;
}) {
  const { t } = useTranslation('assets');
  const [action, setAction] = useState<'buy' | 'sell' | null>(null);
  const physical =
    asset.isPhysical ||
    ['VEHICLE', 'JEWELRY', 'COLLECTIBLE', 'ELECTRONICS', 'FURNITURE'].includes(asset.type);
  const property = asset.type === 'REAL_ESTATE';
  const financial = !physical && !property;
  const planned = asset.acquisitionType === 'PLANNED';
  const canBuy = planned ? Boolean(onCompletePurchase) : financial && Boolean(asset.accountId);
  const canSell =
    !planned &&
    asset.ownershipStatus !== 'CLOSED' &&
    asset.quantity > 0 &&
    (!financial || Boolean(asset.accountId));
  if (asset.isActive === false || (property && !asset.realEstateId) || (!canBuy && !canSell))
    return null;
  const target: SaleTarget = {
    // The ownership endpoint uses the backing asset ID for property sales too.
    id: asset.id,
    name: asset.name,
    currency: asset.currency,
    quantity: decimalField(asset, 'quantity'),
    currentValue: decimalField(asset, 'totalValue'),
    purchaseDate: asset.purchaseDate,
    financial,
    property,
    accountId: asset.accountId,
  };
  return (
    <span className="inline-flex items-center gap-1" onClick={event => event.stopPropagation()}>
      <TooltipProvider>
        {(['buy', 'sell'] as const)
          .filter(kind => (kind === 'buy' ? canBuy : canSell))
          .map(kind => {
            const label = t(
              kind === 'sell'
                ? 'ownership.sell'
                : planned
                  ? 'ownership.completePurchase'
                  : 'ownership.buyMore'
            );
            const Icon = kind === 'buy' ? ArrowDownLeft : ArrowUpRight;
            return (
              <Tooltip key={kind}>
                <TooltipTrigger asChild>
                  <Button
                    variant="ghost"
                    size="sm"
                    className="h-11 w-11 p-0 sm:h-9 sm:w-9"
                    aria-label={`${label}: ${asset.name}`}
                    onClick={() => (planned ? onCompletePurchase?.(asset) : setAction(kind))}
                  >
                    <Icon className="h-4 w-4" />
                  </Button>
                </TooltipTrigger>
                <TooltipContent>{label}</TooltipContent>
              </Tooltip>
            );
          })}
      </TooltipProvider>
      <Dialog
        open={action !== null}
        onOpenChange={open => {
          if (!open) setAction(null);
        }}
      >
        <DialogContent className="sm:max-w-[650px] max-h-[90vh] overflow-y-auto">
          <DialogHeader>
            <DialogTitle>
              {t(action === 'buy' ? 'ownership.buyMore' : 'ownership.sell')} · {asset.name}
            </DialogTitle>
          </DialogHeader>
          {action === 'buy' && <AssetBuyForm target={target} onClose={() => setAction(null)} />}
          {action === 'sell' && <AssetSaleForm target={target} onClose={() => setAction(null)} />}
        </DialogContent>
      </Dialog>
    </span>
  );
}
