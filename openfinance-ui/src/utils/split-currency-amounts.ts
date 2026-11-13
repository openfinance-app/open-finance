import type { Transaction, TransactionSplitResponse } from '@/types/transaction';
import { ExactDecimal } from '@/utils/exact-decimal';
import { getMonetaryScale } from '@/utils/currency';

/** Allocate recorded equivalents without fetching a different rate for each split. */
export function splitCurrencyAmounts(
  splits: TransactionSplitResponse[],
  transaction?: Transaction
): { amount: number; currency: string }[][] {
  const result: { amount: number; currency: string }[][] = splits.map(() => []);
  if (!transaction || transaction.amount === 0) return result;
  const equivalents = [
    ...(transaction.isConverted
      ? [{ amount: transaction.amountInBaseCurrency, currency: transaction.baseCurrency }]
      : []),
    { amount: transaction.transferAmount, currency: transaction.transferCurrency },
    { amount: transaction.originalAmount, currency: transaction.originalCurrency },
  ];
  for (const equivalent of equivalents) {
    if (equivalent.amount == null || !equivalent.currency) continue;
    const factor = new ExactDecimal(10).pow(getMonetaryScale(equivalent.currency));
    const amounts = splits.map(split =>
      new ExactDecimal(split.amount).times(equivalent.amount!).div(transaction.amount).times(factor)
    );
    // Display allocations may round to zero; input split allocation requires positive amounts.
    const units = amounts.map(amount => amount.toDecimalPlaces(0, ExactDecimal.ROUND_DOWN));
    let remainder = amounts
      .reduce((total, amount) => total.plus(amount), new ExactDecimal(0))
      .toDecimalPlaces(0)
      .minus(units.reduce((total, amount) => total.plus(amount), new ExactDecimal(0)));
    const order = amounts
      .map((amount, index) => ({ index, fraction: amount.minus(units[index]).abs() }))
      .sort((a, b) => b.fraction.comparedTo(a.fraction) || a.index - b.index);
    for (const { index } of order) {
      if (remainder.isZero()) break;
      const adjustment = remainder.isPositive() ? 1 : -1;
      units[index] = units[index].plus(adjustment);
      remainder = remainder.minus(adjustment);
    }
    units.forEach((amount, index) =>
      result[index].push({ amount: amount.div(factor).toNumber(), currency: equivalent.currency! })
    );
  }
  return result;
}
