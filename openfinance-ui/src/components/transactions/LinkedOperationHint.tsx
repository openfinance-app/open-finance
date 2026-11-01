import { Link2 } from 'lucide-react';
import { useTranslation } from 'react-i18next';
import type { Transaction } from '@/types/transaction';
import { linkedOperationSummary } from '@/utils/linked-operation';

/** Read from the list response; hints never fetch a holding or loan for each row. */
export function LinkedOperationHint({ transaction }: { transaction: Transaction }) {
  const { t } = useTranslation('transactions');
  const operation = linkedOperationSummary(transaction);
  if (!operation) return null;
  const label = [t(operation.labelKey), transaction.assetName].filter(Boolean).join(' · ');
  return (
    <p
      className="mt-1 flex min-w-0 items-center gap-1.5 text-xs text-text-secondary"
      title={`${t('linkedOperation.title')}: ${label}`}
    >
      <Link2 className="h-3.5 w-3.5 shrink-0" aria-hidden="true" />
      <span className="truncate">{label}</span>
    </p>
  );
}
