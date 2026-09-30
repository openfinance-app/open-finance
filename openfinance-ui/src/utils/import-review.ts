import type { ImportTransactionDTO } from '@/types/import';

const INFORMATIONAL_PREFIXES = [
  'AUTO-MATCH:',
  'AI_MATCH:',
  'CATEGORY_SUGGESTION:',
  'CATEGORY_UNKNOWN:',
  'DUPLICATE:',
  'RULE_MATCH:',
];

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
