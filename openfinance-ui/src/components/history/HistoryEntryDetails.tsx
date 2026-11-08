import { useState } from 'react';
import { useQuery } from '@tanstack/react-query';
import { useTranslation } from 'react-i18next';
import { useVisibility } from '@/context/VisibilityContext';
import { useDateFormatter } from '@/hooks/useDateFormatter';
import { Button } from '@/components/ui/Button';
import { historyService } from '@/services/historyService';
import { formatExactNumber } from '@/utils/format';
import { historyFieldChanges } from '@/utils/history-changes';
import type { OperationHistoryResponse } from '@/types/history';

const amounts =
  /amount|balance|price|value|valuation|principal|payment|income|expense|cost|fee|limit|allocation|interest|rate|deposit|withdraw|profit|loss|rent|splits|quantity|percentage/i;
const enumFields = new Set([
  'type',
  'account_type',
  'transaction_type',
  'asset_type',
  'liability_type',
  'property_type',
  'movement_type',
  'acquisition_type',
  'condition',
  'frequency',
  'period',
  'interest_period',
  'credit_value_date_mode',
  'debit_value_date_mode',
  'day_count_basis',
  'status',
  'payment_method',
  'alert_type',
  'field',
  'operator',
  'action_type',
  'condition_type',
  'condition_match',
  'relationship',
]);

export function HistoryEntryDetails({ entry }: { entry: OperationHistoryResponse }) {
  const { t } = useTranslation('history');
  const { isAmountsVisible } = useVisibility();
  const { date } = useDateFormatter();
  const [page, setPage] = useState(0);
  const { data, isLoading, isError, refetch } = useQuery({
    queryKey: ['history', 'details', entry.id, entry.revision, page],
    queryFn: () => historyService.getDetails(entry.id, page),
  });
  const fallback = historyFieldChanges(entry.changedFieldsJson);
  const records = data?.content?.length
    ? data.content
    : fallback.length
      ? [
          {
            recordType: '',
            recordId: String(entry.entityId),
            entityLabel: entry.entityLabel,
            operationType: entry.operationType,
            context: {},
            fields: fallback,
          },
        ]
      : [];
  const fieldLabel = (field: string): string =>
    t(`fields.${field}`, {
      defaultValue: field.replaceAll('_', ' ').replace(/([a-z])([A-Z])/g, '$1 $2'),
    });
  const valueLabel = (field: string, value: unknown): string => {
    if (!isAmountsVisible && amounts.test(field)) return '••••';
    if (value == null || value === '') return '—';
    if (typeof value === 'boolean') return t(value ? 'yes' : 'no');
    if (typeof value === 'object') return JSON.stringify(value);
    if (
      /date$|until$|^valid_from$|^next_occurrence$|^warranty_expiration$/.test(field) &&
      /^\d{4}-\d\d-\d\d$/.test(String(value))
    ) {
      return date(String(value));
    }
    if (
      amounts.test(field) &&
      !/id$|^value$|^action_value[23]?$/i.test(field) &&
      /^-?\d+(\.\d+)?$/.test(String(value))
    ) {
      return formatExactNumber(value as string | number);
    }
    return enumFields.has(field)
      ? t(`values.${String(value)}`, { defaultValue: String(value) })
      : String(value);
  };

  return (
    <div className="space-y-5" aria-busy={isLoading}>
      {isLoading && records.length === 0 && <p role="status">{t('loadingDetails')}</p>}
      {isError && (
        <div role="alert" className="flex flex-wrap items-center gap-2 text-error">
          <span>{t('detailsError')}</span>
          <Button size="sm" variant="outline" onClick={() => void refetch()}>
            {t('retry')}
          </Button>
        </div>
      )}
      {records.map(record => (
        <section key={`${record.recordType}:${record.recordId}`} className="space-y-3 min-w-0">
          <div className="flex flex-wrap items-baseline gap-x-3 gap-y-1">
            <h3 className="font-semibold text-text-primary break-words">
              {record.entityLabel ||
                t(`records.${record.recordType}`, { defaultValue: t('record') })}
            </h3>
            <span className="text-xs text-text-secondary">
              {t(`operations.${record.operationType.toLowerCase()}`)}
              {' · '}
              {t(`records.${record.recordType}`, { defaultValue: t('record') })} #{record.recordId}
            </span>
          </div>
          {Object.keys(record.context).length > 0 && (
            <dl className="flex flex-wrap gap-x-6 gap-y-2 text-xs text-text-secondary">
              {Object.entries(record.context).map(([field, value]) => (
                <div key={field} className="min-w-0 max-w-full">
                  <dt className="inline">{fieldLabel(field)}: </dt>
                  <dd
                    className={`inline break-words ${amounts.test(field) ? 'font-mono tabular-nums' : ''}`}
                  >
                    {valueLabel(field, value)}
                  </dd>
                </div>
              ))}
            </dl>
          )}
          {record.fields.length > 0 ? (
            <dl className="divide-y divide-border border-y border-border">
              {record.fields.map(change => (
                <div
                  key={change.field}
                  className="grid gap-x-4 gap-y-1 py-3 sm:grid-cols-[minmax(8rem,1fr)_2fr_2fr]"
                >
                  <dt className="font-medium text-text-primary break-words">
                    {fieldLabel(change.field)}
                  </dt>
                  {(['before', 'after'] as const).map(side => (
                    <dd key={side} className="min-w-0 break-words [overflow-wrap:anywhere]">
                      <span className="text-text-secondary">{t(side)}: </span>
                      <span className={amounts.test(change.field) ? 'font-mono tabular-nums' : ''}>
                        {valueLabel(change.field, change[side])}
                      </span>
                    </dd>
                  ))}
                </div>
              ))}
            </dl>
          ) : (
            <p className="text-sm text-text-secondary">{t('noRecordedDetails')}</p>
          )}
        </section>
      ))}
      {!isLoading && !isError && records.length === 0 && <p>{t('noRecordedDetails')}</p>}
      {data && data.totalPages > 1 && (
        <nav
          aria-label={t('detailsPagination')}
          className="flex flex-wrap items-center justify-between gap-3"
        >
          <Button
            size="sm"
            variant="outline"
            disabled={page === 0}
            onClick={() => setPage(page - 1)}
          >
            {t('previous')}
          </Button>
          <span className="text-xs text-text-secondary">
            {t('detailsPage', {
              page: page + 1,
              pages: data.totalPages,
              count: data.totalElements,
            })}
          </span>
          <Button
            size="sm"
            variant="outline"
            disabled={page + 1 >= data.totalPages}
            onClick={() => setPage(page + 1)}
          >
            {t('next')}
          </Button>
        </nav>
      )}
      {(entry.affectedRecords ?? 0) > 1 && (
        <p className="text-xs text-text-secondary">
          {t('relatedRecords', { count: entry.affectedRecords })}
        </p>
      )}
    </div>
  );
}
