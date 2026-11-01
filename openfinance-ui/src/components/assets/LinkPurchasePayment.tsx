import { useState } from 'react';
import { useTranslation } from 'react-i18next';
import { PurchasePaymentSelector } from '@/components/assets/PurchasePaymentSelector';
import { Button } from '@/components/ui/Button';
import { useOwnershipOperation } from '@/hooks/useOwnershipOperation';
import { apiErrorMessage } from '@/utils/api-error';

export function LinkPurchasePayment({
  assetId,
  currency,
}: {
  assetId: number;
  currency: string;
  purchaseDate: string;
}) {
  const { t } = useTranslation('assets');
  const [open, setOpen] = useState(false);
  const [paymentId, setPaymentId] = useState<number>();
  const link = useOwnershipOperation();
  if (!open)
    return (
      <Button
        type="button"
        variant="secondary"
        size="sm"
        onClick={() => {
          link.reset();
          setOpen(true);
        }}
      >
        {t('ownership.linkPayment')}
      </Button>
    );
  return (
    <div className="space-y-4 border-t border-border py-4">
      <p className="text-sm text-text-secondary">{t('ownership.linkHint')}</p>
      <PurchasePaymentSelector
        currency={currency}
        value={paymentId}
        onChange={payment => setPaymentId(payment.id)}
      />
      {link.error && (
        <p role="alert" className="text-sm text-error">
          {apiErrorMessage(link.error, t('ownership.saveError'))}
        </p>
      )}
      <div className="flex flex-wrap justify-end gap-3">
        <Button
          type="button"
          variant="secondary"
          disabled={link.isPending}
          onClick={() => setOpen(false)}
        >
          {t('ownership.cancel')}
        </Button>
        <Button
          type="button"
          disabled={!paymentId || link.isPending}
          isLoading={link.isPending}
          onClick={async () => {
            try {
              await link.mutateAsync({
                path: `/assets/${assetId}/purchase-payment`,
                data: { transactionId: paymentId },
              });
              setOpen(false);
              setPaymentId(undefined);
            } catch {
              /* Retain selection for correction. */
            }
          }}
        >
          {t('ownership.linkPayment')}
        </Button>
      </div>
    </div>
  );
}
