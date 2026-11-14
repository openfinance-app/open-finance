import type { Transaction, TransactionSplitResponse } from '@/types/transaction';
import { decimalValue, ExactDecimal } from '@/utils/exact-decimal';
import { getMonetaryScale } from '@/utils/currency';

/** Allocate recorded equivalents without fetching a different rate for each split. */
export function splitCurrencyAmounts(
  splits: TransactionSplitResponse[],
  transaction?: Transaction
): { amount: string; currency: string }[][] {
  const result: { amount: string; currency: string }[][] = splits.map(() => []);
  if (!transaction) return result;
  const total = new ExactDecimal(decimalValue(transaction, 'amount') ?? 0);
  if (total.isZero()) return result;
  const equivalents = [
    ...(transaction.isConverted
      ? [
          {
            amount: decimalValue(transaction, 'amountInBaseCurrency'),
            currency: transaction.baseCurrency,
          },
        ]
      : []),
    { amount: decimalValue(transaction, 'transferAmount'), currency: transaction.transferCurrency },
    { amount: decimalValue(transaction, 'originalAmount'), currency: transaction.originalCurrency },
  ];
  for (const equivalent of equivalents) {
    if (equivalent.amount == null || !equivalent.currency) continue;
    // Allocate in settlement units, retaining any finer precision in the recorded total.
    const scale = Math.max(
      getMonetaryScale(equivalent.currency),
      new ExactDecimal(equivalent.amount).decimalPlaces()
    );
    const factor = new ExactDecimal(10).pow(scale);
    const amounts = splits.map(split =>
      new ExactDecimal(decimalValue(split, 'amount') ?? 0)
        .times(equivalent.amount!)
        .div(total)
        .times(factor)
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
      result[index].push({ amount: amount.div(factor).toFixed(), currency: equivalent.currency! })
    );
  }
  return result;
}
