import type { ImportTransactionDTO } from '@/types/import';
import { decimalField, equalDecimals, ExactDecimal, exactSum } from '@/utils/exact-decimal';

// Parsers store validation messages in the language used when the file was uploaded.
const fieldErrors = new Map<string, string>([
  ['Transaction date is required', 'dateRequired'],
  ['La date de la transaction est requise', 'dateRequired'],
  ['Transaction date cannot be in the future', 'dateFuture'],
  ['La date de la transaction ne peut pas être dans le futur', 'dateFuture'],
  ['Transaction amount is required', 'amountRequired'],
  ['Le montant de la transaction est requis', 'amountRequired'],
  ['Transaction amount cannot be zero', 'amountZero'],
  ['Le montant de la transaction ne peut pas être nul', 'amountZero'],
]);

export function reviewFieldErrorKey(error: string): string | undefined {
  return fieldErrors.get(error);
}

export const MAX_IMPORT_AMOUNT = '99999999999999999999999999.999999999999999999';

export function reviewTransferPartners(rows: ImportTransactionDTO[]): Map<number, number> {
  const pending = new Map<string, number>();
  const partners = new Map<number, number>();
  rows.forEach((row, index) => {
    if (!row.transfer || !row.transferGroupKey) return;
    const key = JSON.stringify([row.sourceUploadId, row.transferGroupKey]);
    const other = pending.get(key);
    if (other === undefined) {
      pending.set(key, index);
    } else {
      partners.set(index, other);
      partners.set(other, index);
      pending.delete(key);
    }
  });
  return partners;
}

export function shareTransferAmount(
  row: ImportTransactionDTO,
  other: ImportTransactionDTO
): boolean {
  return (
    row.amount != null &&
    other.amount != null &&
    (row.currency || row.reviewCurrency) === (other.currency || other.reviewCurrency) &&
    new ExactDecimal(decimalField(row, 'amount')).negated().eq(decimalField(other, 'amount'))
  );
}

/** Use a signed replacement on one selected leg. When both are selected, retain each direction. */
export function reviewedAmountChanges(
  rows: ImportTransactionDTO[],
  partners: Map<number, number>,
  selected: Set<number>,
  amount: string
): Map<number, string> {
  const changes = new Map<number, string>();
  const value = new ExactDecimal(amount);
  for (const index of selected) {
    const row = rows[index];
    const other = partners.get(index);
    if (other === undefined || row.amount == null || rows[other].amount == null) {
      changes.set(index, amount);
    } else if (selected.has(other)) {
      const negative = new ExactDecimal(decimalField(row, 'amount')).isNegative();
      changes.set(index, (negative ? value.abs().negated() : value.abs()).toFixed());
    } else {
      changes.set(index, amount);
      if (equalDecimals(decimalField(row, 'amount'), amount)) continue;
      const magnitude = shareTransferAmount(row, rows[other])
        ? value.abs()
        : new ExactDecimal(decimalField(rows[other], 'amount')).abs();
      changes.set(other, (value.isNegative() ? magnitude : magnitude.negated()).toFixed());
    }
  }
  return changes;
}

export function updateReviewedDateAndAmount(
  row: ImportTransactionDTO,
  date: string | null,
  amount?: string
): ImportTransactionDTO {
  const dateChanged = row.transactionDate !== date;
  const amountChanged = amount !== undefined && !equalDecimals(decimalField(row, 'amount'), amount);
  if (!dateChanged && !amountChanged) return row;

  let errors = row.validationErrors.filter(error => {
    const key = reviewFieldErrorKey(error);
    return (
      !(dateChanged && key?.startsWith('date')) && !(amountChanged && key?.startsWith('amount'))
    );
  });
  const corrected = {
    ...row,
    // The statement's historical FX fallback applies only to the original amount and date.
    ...(row.sourceAccountBalanceDelta != null && { sourceAccountBalanceDelta: null }),
    transactionDate: date,
    validationErrors: errors,
  };
  if (!amountChanged) return corrected;
  const magnitude = new ExactDecimal(amount).abs();
  if (row.splits?.length) {
    const amounts = row.splits.map(split =>
      new ExactDecimal(decimalField(split, 'amount', '0')).abs()
    );
    if (!magnitude.eq(exactSum(amounts.map(value => value.toFixed())))) {
      if (!errors.some(isSplitAmountError)) errors.push('SPLIT_INVALID: totals differ');
    } else if (!row.transfer && amounts.length >= 2 && amounts.every(value => value.gt(0))) {
      errors = errors.filter(error => !isSplitAmountError(error));
    }
  }
  return {
    ...corrected,
    amount,
    ...(row.exactValues?.amount != null && { exactValues: { ...row.exactValues, amount } }),
    ...(row.transferAmount != null &&
      row.amount != null &&
      equalDecimals(row.transferAmount, new ExactDecimal(row.amount).abs().toFixed()) && {
        transferAmount: magnitude.toFixed(),
      }),
    validationErrors: errors,
  };
}

const INFORMATIONAL_PREFIXES = [
  'AUTO-MATCH:',
  'AI_MATCH:',
  'AI_UNAVAILABLE:',
  'CATEGORY_SUGGESTION:',
  'CATEGORY_UNKNOWN:',
  'DUPLICATE:',
  'RULE_MATCH:',
];

/** Older QIF review drafts stored the mismatch message in the user's import language. */
export function isSplitAmountError(error: string): boolean {
  return (
    error.startsWith('SPLIT_INVALID:') ||
    /^Split amounts \([^)]+\) do not match transaction amount \([^)]+\)$/.test(error) ||
    /^Les montants ventilés \([^)]+\) ne correspondent pas au montant de la transaction \([^)]+\)$/.test(
      error
    )
  );
}

interface ImportReviewCounts {
  importable: number;
  categorized: number;
  duplicates: number;
  ruleSkipped: number;
  invalid: number;
}

export function getImportReviewCounts(
  transactions: ImportTransactionDTO[],
  skipDuplicates: boolean
): ImportReviewCounts {
  const counts = { importable: 0, categorized: 0, duplicates: 0, ruleSkipped: 0, invalid: 0 };
  for (const transaction of transactions) {
    const errors = transaction.validationErrors ?? [];
    if (errors.some(error => error.startsWith('RULE_SKIP:'))) {
      counts.ruleSkipped++;
    } else if (
      errors.some(error => !INFORMATIONAL_PREFIXES.some(prefix => error.startsWith(prefix)))
    ) {
      counts.invalid++;
    } else if (
      skipDuplicates &&
      (transaction.potentialDuplicate || errors.some(error => error.startsWith('DUPLICATE:')))
    ) {
      counts.duplicates++;
    } else {
      counts.importable++;
      if (
        transaction.category ||
        (transaction.splits?.length && transaction.splits.every(split => split.category))
      ) {
        counts.categorized++;
      }
    }
  }
  return counts;
}
