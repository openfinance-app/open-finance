import { useState } from 'react';
import { useTranslation } from 'react-i18next';
import { Button } from '@/components/ui/Button';
import { ConvertedAmount } from '@/components/ui/ConvertedAmount';
import { useDateFormatter } from '@/hooks/useDateFormatter';
import { AssetBuyForm } from '@/components/assets/AssetBuyForm';
import { AssetSaleForm, type SaleTarget } from '@/components/assets/AssetSaleForm';
import { LinkPurchasePayment } from '@/components/assets/LinkPurchasePayment';

interface Props {
  target: SaleTarget;
  acquisitionType?: string;
  closed?: boolean;
  inactive?: boolean;
  soldDate?: string;
  saleProceeds?: number;
  realizedGain?: number | null;
  onComplete?: () => void;
  showTradeActions?: boolean;
}
export function OwnershipActions({
  target,
  acquisitionType,
  closed,
  inactive,
  soldDate,
  saleProceeds,
  realizedGain,
  onComplete,
  showTradeActions = true,
}: Props) {
  const { t } = useTranslation('assets');
  const { date } = useDateFormatter();
  const [buying, setBuying] = useState(false);
  const [selling, setSelling] = useState(false);
  if (showTradeActions && buying && !inactive)
    return <AssetBuyForm target={target} onClose={() => setBuying(false)} />;
  if (closed)
    return (
      <section className="space-y-3 rounded-lg border border-border p-4">
        <h3 className="font-semibold">
          {t('ownership.closed')}
          {soldDate ? ` · ${date(soldDate)}` : ''}
        </h3>
        <p className="text-sm text-text-secondary">{t('ownership.closedHint')}</p>
        {target.property && <p className="text-sm text-warning">{t('ownership.debtRetained')}</p>}
        <dl className="grid grid-cols-2 gap-2 text-sm [&>dd]:text-right">
          {saleProceeds != null && (
            <>
              <dt>{t('ownership.netProceeds')}</dt>
              <dd>
                <ConvertedAmount
                  className="font-mono tabular-nums"
                  inline
                  amount={saleProceeds}
                  currency={target.currency}
                />
              </dd>
            </>
          )}
          <dt>{t('ownership.realizedGain')}</dt>
          <dd>
            {realizedGain == null ? (
              t('ownership.unknownGain')
            ) : (
              <ConvertedAmount
                className="font-mono tabular-nums"
                inline
                amount={realizedGain}
                currency={target.currency}
              />
            )}
          </dd>
        </dl>
        {showTradeActions && target.financial && target.accountId && !inactive && (
          <Button type="button" variant="secondary" size="sm" onClick={() => setBuying(true)}>
            {t('ownership.buyMore')}
          </Button>
        )}
      </section>
    );
  if (inactive) return null;
  if (showTradeActions && selling)
    return <AssetSaleForm target={target} onClose={() => setSelling(false)} />;
  if (acquisitionType === 'PLANNED')
    return onComplete ? (
      <Button onClick={onComplete}>{t('ownership.completePurchase')}</Button>
    ) : null;
  const canLinkPurchasePayment =
    (!acquisitionType || acquisitionType === 'PURCHASE') && !target.financial;
  if (!showTradeActions && !canLinkPurchasePayment) return null;
  return (
    <div className="flex flex-wrap items-start gap-3">
      {canLinkPurchasePayment && (
        <LinkPurchasePayment
          assetId={target.id}
          currency={target.currency}
          purchaseDate={target.purchaseDate}
        />
      )}
      {showTradeActions && target.financial && target.accountId && (
        <Button type="button" variant="secondary" size="sm" onClick={() => setBuying(true)}>
          {t('ownership.buyMore')}
        </Button>
      )}
      {showTradeActions && (
        <Button type="button" variant="secondary" size="sm" onClick={() => setSelling(true)}>
          {t('ownership.sell')}
        </Button>
      )}
    </div>
  );
}
