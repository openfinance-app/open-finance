import { useTranslation } from 'react-i18next';
import { ConvertedAmount } from '@/components/ui/ConvertedAmount';
import { useTransactionSummary } from '@/hooks/useTransactions';
import type { TransactionFilters } from '@/types/transaction';

export function TransactionSummaryHeader({ filters }: { filters: TransactionFilters }) {
  const { t } = useTranslation('transactions');
  const { data, isLoading, error } = useTransactionSummary(filters);
  return (
    <section
      aria-label={t('summary.title')}
      aria-busy={isLoading}
      className="mb-6 rounded-xl border border-border bg-surface p-4 shadow-plate"
    >
      <p className="mb-3 text-xs text-text-secondary">{t('summary.scope')}</p>
      {error ? (
        <p role="status">{t('summary.unavailable')}</p>
      ) : !data ? (
        <div className="h-12 animate-pulse rounded bg-surface-elevated" />
      ) : (
        <dl className="grid grid-cols-2 gap-4 sm:grid-cols-3 xl:grid-cols-5">
          <div>
            <dt className="plate-label">{t('summary.count')}</dt>
            <dd className="mt-1 text-xl font-mono font-semibold">{data.transactionCount}</dd>
          </div>
          {(
            [
              ['income', data.incomeAmount],
              ['expenses', data.expenseAmount],
              ['transfers', data.transferNetAmount],
              ['net', data.netAmount],
            ] as const
          ).map(([label, amount]) => (
            <div key={label} className="min-w-0">
              <dt className="plate-label">{t(`summary.${label}`)}</dt>
              <dd className="mt-1 break-words text-xl font-mono font-semibold">
                {amount == null ? (
                  '—'
                ) : (
                  <ConvertedAmount amount={amount} currency={data.currency} />
                )}
              </dd>
            </div>
          ))}
        </dl>
      )}
      {data && data.netAmount == null && (
        <p className="mt-2 text-sm text-text-secondary">{t('summary.conversionUnavailable')}</p>
      )}
    </section>
  );
}
