import type { ReactElement } from 'react';
import { ConvertedAmount } from '@/components/ui/ConvertedAmount';
import type { Transaction } from '@/types/transaction';

/** Uses the recorded transfer/source amounts before requesting a dated market estimate. */
export function TransactionAmount({
  transaction,
  inline = false,
  className,
}: {
  transaction: Transaction;
  inline?: boolean;
  className?: string;
}): ReactElement {
  const recordedAmounts: { amount: number; currency: string }[] = [];
  if (transaction.transferAmount != null && transaction.transferCurrency) {
    recordedAmounts.push({
      amount: transaction.transferAmount,
      currency: transaction.transferCurrency,
    });
  }
  if (transaction.originalAmount != null && transaction.originalCurrency) {
    recordedAmounts.push({
      amount: transaction.originalAmount,
      currency: transaction.originalCurrency,
    });
  }
  if (transaction.amountInBaseCurrency != null && transaction.baseCurrency) {
    recordedAmounts.push({
      amount: transaction.amountInBaseCurrency,
      currency: transaction.baseCurrency,
    });
  }
  return (
    <ConvertedAmount
      conversionDate={transaction.date}
      amount={transaction.amount}
      currency={transaction.currency}
      convertedAmount={transaction.amountInBaseCurrency}
      baseCurrency={transaction.baseCurrency}
      exchangeRate={transaction.exchangeRate}
      isConverted={transaction.isConverted}
      recordedAmounts={recordedAmounts}
      inline={inline}
      className={className}
    />
  );
}
