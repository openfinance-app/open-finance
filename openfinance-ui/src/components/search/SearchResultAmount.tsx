import { ConvertedAmount } from '@/components/ui/ConvertedAmount';
import type { SearchResult } from '@/types/search';
import { DEFAULT_CURRENCY } from '@/utils/currency';

export function SearchResultAmount({ result }: { result: SearchResult }) {
  if (result.amount == null) return null;
  const recordedAmounts: { amount: number; currency: string }[] = [];
  for (const [amount, currency] of [
    [result.transferAmount, result.transferCurrency],
    [result.originalAmount, result.originalCurrency],
    ...(result.isConverted ? [[result.amountInBaseCurrency, result.baseCurrency] as const] : []),
  ] as const) {
    if (amount != null && currency) recordedAmounts.push({ amount, currency });
  }
  return (
    <ConvertedAmount
      amount={result.amount}
      currency={result.currency || DEFAULT_CURRENCY}
      convertedAmount={result.amountInBaseCurrency}
      baseCurrency={result.baseCurrency}
      exchangeRate={result.exchangeRate}
      isConverted={result.isConverted}
      conversionDate={result.resultType === 'TRANSACTION' ? result.date : undefined}
      recordedAmounts={recordedAmounts}
      inline
    />
  );
}
