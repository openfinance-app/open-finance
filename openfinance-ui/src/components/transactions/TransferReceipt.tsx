import type { ReactElement } from 'react';
import { useTranslation } from 'react-i18next';
import { PrivateAmount } from '@/components/ui/PrivateAmount';
import type { Transaction } from '@/types/transaction';
import { decimalField, equalDecimals, ExactDecimal } from '@/utils/exact-decimal';
import { formatExactNumber } from '@/utils/format';
import { getCurrencyDecimals } from '@/utils/currency';

/** The received instruction belongs to the other posting, not to the sending amount field. */
export function TransferReceipt({
  transaction,
}: {
  transaction: Transaction;
}): ReactElement | null {
  const { t } = useTranslation('transactions');
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
          {formatExactNumber(originalAmount)} {originalCurrency}
          {converted && originalCurrency !== currency && (
            <span className="text-text-secondary">
              {' ≈ '}
              {formatExactNumber(
                new ExactDecimal(amount).toDecimalPlaces(getCurrencyDecimals(currency)).toFixed()
              )}{' '}
              {currency}
            </span>
          )}
        </PrivateAmount>
      </dd>
    </dl>
  );
}
