import { decimalValue } from '@/utils/exact-decimal';
import { ConvertedAmount } from '@/components/ui/ConvertedAmount';
import type { SearchResult } from '@/types/search';
import { DEFAULT_CURRENCY } from '@/utils/currency';

export function SearchResultAmount({ result }: { result: SearchResult }) {
  if (result.amount == null) return null;
  const recordedAmounts: { amount: number | string; currency: string }[] = [];
  for (const [amount, currency] of [
    [decimalValue(result, 'transferAmount') ?? result.transferAmount, result.transferCurrency],
    [decimalValue(result, 'originalAmount') ?? result.originalAmount, result.originalCurrency],
    ...(result.isConverted
      ? [
          [
            decimalValue(result, 'amountInBaseCurrency') ?? result.amountInBaseCurrency,
            result.baseCurrency,
          ] as const,
        ]
      : []),
  ] as const) {
    if (amount != null && currency) recordedAmounts.push({ amount, currency });
  }
  return (
    <ConvertedAmount
      amount={decimalValue(result, 'amount') ?? result.amount}
      currency={result.currency || DEFAULT_CURRENCY}
      convertedAmount={decimalValue(result, 'amountInBaseCurrency') ?? result.amountInBaseCurrency}
      baseCurrency={result.baseCurrency}
      exchangeRate={decimalValue(result, 'exchangeRate') ?? result.exchangeRate}
      isConverted={result.isConverted}
      conversionDate={result.resultType === 'TRANSACTION' ? result.date : undefined}
      recordedAmounts={recordedAmounts}
      inline
    />
  );
}
