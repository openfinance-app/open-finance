import type { ReactElement } from 'react';
import { useTranslation } from 'react-i18next';
import { PrivateAmount } from '@/components/ui/PrivateAmount';
import type { Transaction } from '@/types/transaction';
import { decimalField, equalDecimals } from '@/utils/exact-decimal';
import { useFormatCurrency } from '@/hooks/useFormatCurrency';

/** The received instruction belongs to the other posting, not to the sending amount field. */
export function TransferReceipt({
  transaction,
}: {
  transaction: Transaction;
}): ReactElement | null {
  const { t } = useTranslation('transactions');
  const { format } = useFormatCurrency();
  const currency = transaction.transferCurrency;
  if (!currency || transaction.transferAmount == null) return null;

  const converted =
    !!transaction.transferOriginalCurrency && transaction.transferOriginalAmount != null;
  const amount = decimalField(transaction, 'transferAmount');
  if (
    !converted &&
    currency === transaction.currency &&
    equalDecimals(amount, decimalField(transaction, 'amount'))
  )
    return null;

  const originalCurrency = converted ? transaction.transferOriginalCurrency! : currency;
  const originalAmount = converted ? decimalField(transaction, 'transferOriginalAmount') : amount;

  return (
    <dl className="flex flex-wrap items-baseline justify-between gap-x-4 gap-y-1 text-sm">
      <dt className="text-text-secondary">{t('form.recordedReceipt')}</dt>
      <dd className="min-w-0 break-words text-text-primary">
        <PrivateAmount inline className="font-mono tabular-nums">
          {format(originalAmount, originalCurrency, { showSymbol: false })} {originalCurrency}
          {converted && originalCurrency !== currency && (
            <span className="text-text-secondary">
              {' ≈ '}
              {format(amount, currency, { showSymbol: false })} {currency}
            </span>
          )}
        </PrivateAmount>
      </dd>
    </dl>
  );
}
