import { formatExactNumber } from '@/utils/format';
import { useVisibility } from '@/context/VisibilityContext';
import { Fragment, useState } from 'react';
import { useTranslation } from 'react-i18next';
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { isAxiosError } from 'axios';
import { History, Undo2, Redo2, AlertCircle } from 'lucide-react';
import { PageHeader } from '@/components/layout/PageHeader';
import { EmptyState } from '@/components/layout/EmptyState';
import { LoadingSkeleton } from '@/components/LoadingComponents';
import { Pagination } from '@/components/ui/Pagination';
import { Button } from '@/components/ui/Button';
import { historyService } from '@/services/historyService';
import type { EntityType, OperationType } from '@/types/history';
import { useDocumentTitle } from '@/hooks/useDocumentTitle';
import { DEFAULT_PAGE_SIZE, PAGE_SIZE_OPTIONS } from '@/constants/pagination';
import { useDateFormatter } from '@/hooks/useDateFormatter';

interface FieldChange {
  field: string;
  before: unknown;
  after: unknown;
}

function changes(json?: string): FieldChange[] {
  if (!json) return [];
  try {
    const parsed: unknown = JSON.parse(json);
    if (!parsed || typeof parsed !== 'object' || Array.isArray(parsed)) return [];
    return Object.entries(parsed).flatMap(([field, value]) =>
      value && typeof value === 'object' && 'before' in value && 'after' in value
        ? [{ field, before: value.before, after: value.after }]
        : []
    );
  } catch {
    return [];
  }
}

function dateBoundary(value: string, end = false): string | undefined {
  if (!value) return undefined;
  const date = new Date(`${value}T00:00:00`);
  if (!Number.isFinite(date.getTime())) return undefined;
  if (end) date.setDate(date.getDate() + 1);
  return date.toISOString();
}

const entities: EntityType[] = [
  'ACCOUNT',
  'TRANSACTION',
  'ASSET',
  'LIABILITY',
  'REAL_ESTATE',
  'BUDGET',
  'CATEGORY',
  'PAYEE',
  'TRANSACTION_RULE',
  'RECURRING_TRANSACTION',
  'IMPORT',
];
const inputClass = 'h-10 rounded-md border border-input bg-background/50 px-3 py-2 text-sm';

export default function HistoryPage() {
  const { t } = useTranslation('history');
  const { dateTime } = useDateFormatter();
  const { isAmountsVisible } = useVisibility();
  useDocumentTitle(t('title'));
  const queryClient = useQueryClient();
  const [currentPage, setCurrentPage] = useState(0);
  const [pageSize, setPageSize] = useState(DEFAULT_PAGE_SIZE);
  const [entityType, setEntityType] = useState<EntityType | undefined>();
  const [operationType, setOperationType] = useState<OperationType | undefined>();
  const [since, setSince] = useState('');
  const [until, setUntil] = useState('');
  const [expanded, setExpanded] = useState<number | null>(null);
  const [notice, setNotice] = useState('');
  const {
    data: historyPage,
    isLoading,
    error,
  } = useQuery({
    queryKey: ['history', currentPage, pageSize, entityType, operationType, since, until],
    queryFn: () =>
      historyService.getHistory(
        currentPage,
        pageSize,
        entityType,
        dateBoundary(since),
        operationType,
        dateBoundary(until, true)
      ),
  });
  const mutation = useMutation({
    mutationFn: ({ id, redo }: { id: number; redo: boolean }) =>
      redo ? historyService.redo(id) : historyService.undo(id),
    onMutate: () => setNotice(''),
    onSuccess: (_, variables) => {
      setNotice(t(variables.redo ? 'redoSuccess' : 'undoSuccess'));
      void queryClient.invalidateQueries();
    },
    onError: () => {
      void queryClient.invalidateQueries({ queryKey: ['history'] });
    },
  });
  const mutationError = mutation.error
    ? isAxiosError<{ message?: string }>(mutation.error) && mutation.error.response?.data.message
      ? mutation.error.response.data.message
      : t('actionError')
    : null;
  const entityLabel = (type: EntityType): string =>
    t(`filters.${type === 'REAL_ESTATE' ? 'realEstate' : type.toLowerCase()}`);
  const valueLabel = (field: string, value: unknown): string =>
    !isAmountsVisible &&
    /amount|balance|price|value|principal|payment|income|expense|cost|allocation|interest|rate|deposit|withdraw|profit|loss|rent|splits/i.test(
      field
    )
      ? '••••'
      : value === null || value === undefined || value === ''
        ? '—'
        : typeof value === 'boolean'
          ? t(value ? 'yes' : 'no')
          : typeof value === 'object'
            ? JSON.stringify(value)
            : (typeof value === 'number' ||
                  (typeof value === 'string' && /^-?\d+(\.\d+)?$/.test(value))) &&
                !/id$/i.test(field) &&
                /amount|balance|price|value|principal|payment|income|expense|cost|allocation|interest|rate|deposit|withdraw|profit|loss|rent|quantity|percentage/i.test(
                  field
                )
              ? formatExactNumber(value)
              : String(value);

  return (
    <div className="p-4 sm:p-8 space-y-6">
      <PageHeader title={t('title')} description={t('description')} />
      <div className="flex flex-wrap items-end gap-3">
        <label className="flex flex-col gap-1 text-sm">
          {t('entityType')}
          <select
            className={inputClass}
            aria-label={t('entityType')}
            value={entityType ?? ''}
            onChange={event => {
              setEntityType((event.target.value as EntityType) || undefined);
              setCurrentPage(0);
            }}
          >
            <option value="">{t('filters.all')}</option>
            {entities.map(type => (
              <option key={type} value={type}>
                {entityLabel(type)}
              </option>
            ))}
          </select>
        </label>
        <label className="flex flex-col gap-1 text-sm">
          {t('operation')}
          <select
            className={inputClass}
            aria-label={t('operation')}
            value={operationType ?? ''}
            onChange={event => {
              setOperationType((event.target.value as OperationType) || undefined);
              setCurrentPage(0);
            }}
          >
            <option value="">{t('allOperations')}</option>
            {(['CREATE', 'UPDATE', 'DELETE'] as const).map(type => (
              <option key={type} value={type}>
                {t(`operations.${type.toLowerCase()}`)}
              </option>
            ))}
          </select>
        </label>
        <label className="flex flex-col gap-1 text-sm">
          {t('fromDate')}
          <input
            type="date"
            className={inputClass}
            value={since}
            max={until || undefined}
            onChange={event => {
              setSince(event.target.value);
              setCurrentPage(0);
            }}
          />
        </label>
        <label className="flex flex-col gap-1 text-sm">
          {t('toDate')}
          <input
            type="date"
            className={inputClass}
            value={until}
            min={since || undefined}
            onChange={event => {
              setUntil(event.target.value);
              setCurrentPage(0);
            }}
          />
        </label>
      </div>
      {(error || mutationError) && (
        <div
          role="alert"
          className="p-4 bg-error/10 border border-error/20 rounded-lg text-error flex items-center gap-2"
        >
          <AlertCircle className="w-5 h-5 shrink-0" />
          {mutationError || t('loadError')}
        </div>
      )}
      {notice && (
        <p role="status" className="text-sm text-success">
          {notice}
        </p>
      )}
      {isLoading && (
        <div className="space-y-4">
          {Array.from({ length: 5 }, (_, index) => (
            <LoadingSkeleton key={index} className="h-20" />
          ))}
        </div>
      )}
      {!isLoading && !error && historyPage?.content.length === 0 && (
        <EmptyState title={t('empty')} description="" icon={History} />
      )}
      {!isLoading && historyPage && historyPage.content.length > 0 && (
        <div className="bg-card rounded-lg border border-border shadow-sm overflow-hidden">
          <div className="overflow-x-auto">
            <table className="w-full text-sm text-left">
              <thead className="text-xs uppercase bg-background/50 text-muted-foreground border-b border-border">
                <tr>
                  <th className="px-4 py-4">{t('operation')}</th>
                  <th className="px-4 py-4">{t('name')}</th>
                  <th className="px-4 py-4">{t('date')}</th>
                  <th className="px-4 py-4">{t('status')}</th>
                  <th className="px-4 py-4 text-right">{t('actions')}</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-border">
                {historyPage.content.map(item => {
                  const undone = !!item.undoneAt && !item.redoneAt;
                  const canAct = undone ? item.canRedo : item.canUndo;
                  const fields = changes(item.changedFieldsJson);
                  const reason = item.unavailableReason
                    ? t(`unavailableReasons.${item.unavailableReason}`)
                    : t('unavailable');
                  return (
                    <Fragment key={item.id}>
                      <tr className="hover:bg-muted/50">
                        <td className="px-4 py-4 font-medium whitespace-nowrap">
                          {t(`operations.${item.operationType.toLowerCase()}`)}{' '}
                          {entityLabel(item.entityType)}
                        </td>
                        <td className="px-4 py-4 min-w-40">
                          {item.entityLabel || '—'}
                          {(fields.length > 0 || (item.affectedRecords ?? 0) > 1) && (
                            <Button
                              variant="ghost"
                              size="sm"
                              className="block mt-1 h-7 px-0"
                              aria-expanded={expanded === item.id}
                              aria-controls={`history-details-${item.id}`}
                              onClick={() => setExpanded(expanded === item.id ? null : item.id)}
                            >
                              {t(expanded === item.id ? 'hideChanges' : 'showChanges')}
                            </Button>
                          )}
                        </td>
                        <td className="px-4 py-4 text-muted-foreground whitespace-nowrap">
                          {dateTime(item.createdAt)}
                        </td>
                        <td className="px-4 py-4">
                          <span
                            className={`inline-flex px-2.5 py-0.5 rounded-full text-xs font-medium ${undone ? 'bg-warning/10 text-warning' : item.redoneAt ? 'bg-primary/10 text-primary' : 'bg-success/10 text-success'}`}
                          >
                            {t(
                              undone
                                ? 'badge.undone'
                                : item.redoneAt
                                  ? 'badge.redone'
                                  : 'badge.active'
                            )}
                          </span>
                        </td>
                        <td className="px-4 py-4 text-right">
                          <Button
                            variant="outline"
                            size="sm"
                            onClick={() => mutation.mutate({ id: item.id, redo: undone })}
                            disabled={!canAct || mutation.isPending}
                            title={!canAct ? reason : undefined}
                            className="h-8 px-2"
                          >
                            {undone ? (
                              <Redo2 className="w-4 h-4 mr-1" />
                            ) : (
                              <Undo2 className="w-4 h-4 mr-1" />
                            )}
                            {t(undone ? 'redo' : 'undo')}
                          </Button>
                          {!canAct && (
                            <p className="mt-2 text-xs text-muted-foreground min-w-44 max-w-64 ml-auto">
                              {reason}
                            </p>
                          )}
                        </td>
                      </tr>
                      {expanded === item.id &&
                        (fields.length > 0 || (item.affectedRecords ?? 0) > 1) && (
                          <tr id={`history-details-${item.id}`}>
                            <td colSpan={5} className="px-4 py-4 bg-muted/20">
                              <dl className="space-y-2">
                                {fields.map(field => (
                                  <div
                                    key={field.field}
                                    className="grid gap-2 sm:grid-cols-[minmax(8rem,1fr)_2fr_2fr]"
                                  >
                                    <dt className="font-medium">
                                      {t(`fields.${field.field}`, {
                                        defaultValue: field.field.replaceAll('_', ' '),
                                      })}
                                    </dt>
                                    <dd className="break-words">
                                      <span className="text-muted-foreground">{t('before')}: </span>
                                      {valueLabel(field.field, field.before)}
                                    </dd>
                                    <dd className="break-words">
                                      <span className="text-muted-foreground">{t('after')}: </span>
                                      {valueLabel(field.field, field.after)}
                                    </dd>
                                  </div>
                                ))}
                              </dl>
                              {(item.affectedRecords ?? 0) > 1 && (
                                <p className="mt-3 text-xs text-muted-foreground">
                                  {t('relatedRecords', { count: item.affectedRecords })}
                                </p>
                              )}
                            </td>
                          </tr>
                        )}
                    </Fragment>
                  );
                })}
              </tbody>
            </table>
          </div>
          {historyPage.totalElements > 0 && (
            <div className="p-4 border-t border-border">
              <Pagination
                currentPage={currentPage}
                totalPages={historyPage.totalPages}
                pageSize={pageSize}
                totalElements={historyPage.totalElements}
                onPageChange={setCurrentPage}
                onPageSizeChange={size => {
                  setPageSize(size);
                  setCurrentPage(0);
                }}
                pageSizeOptions={PAGE_SIZE_OPTIONS}
              />
            </div>
          )}
        </div>
      )}
    </div>
  );
}
