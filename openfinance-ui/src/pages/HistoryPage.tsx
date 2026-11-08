import { HistoryEntryDetails } from '@/components/history/HistoryEntryDetails';
import { historyFieldChanges } from '@/utils/history-changes';
import { useViewState } from '@/hooks/useViewState';
import { Fragment, useState } from 'react';
import { useTranslation } from 'react-i18next';
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { isAxiosError } from 'axios';
import { History, Undo2, Redo2, AlertCircle, CheckCircle2 } from 'lucide-react';
import { PageHeader } from '@/components/layout/PageHeader';
import { EmptyState } from '@/components/layout/EmptyState';
import { LoadingSkeleton } from '@/components/LoadingComponents';
import { Pagination } from '@/components/ui/Pagination';
import { Button } from '@/components/ui/Button';
import { Badge } from '@/components/ui/Badge';
import { historyService } from '@/services/historyService';
import type { EntityType, OperationType } from '@/types/history';
import { useDocumentTitle } from '@/hooks/useDocumentTitle';
import { DEFAULT_PAGE_SIZE, PAGE_SIZE_OPTIONS } from '@/constants/pagination';
import { useDateFormatter } from '@/hooks/useDateFormatter';

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
const inputClass =
  'h-10 rounded-lg border border-border bg-background px-3 py-2 text-sm text-text-primary shadow-slot transition-colors hover:border-border-strong';

export default function HistoryPage() {
  const { t } = useTranslation('history');
  const { dateTime } = useDateFormatter();
  useDocumentTitle(t('title'));
  const queryClient = useQueryClient();
  const [currentPage, setCurrentPage] = useViewState('currentPage', 0);
  const [pageSize, setPageSize] = useViewState('pageSize', DEFAULT_PAGE_SIZE);
  const [entityType, setEntityType] = useViewState<EntityType | undefined>('entityType', undefined);
  const [operationType, setOperationType] = useViewState<OperationType | undefined>(
    'operationType',
    undefined
  );
  const [since, setSince] = useViewState('since', '');
  const [until, setUntil] = useViewState('until', '');
  const [expanded, setExpanded] = useViewState<number | null>('expanded', null);
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
  const hasActiveFilters = !!entityType || !!operationType || since !== '' || until !== '';
  const handleClearFilters = () => {
    setEntityType(undefined);
    setOperationType(undefined);
    setSince('');
    setUntil('');
    setCurrentPage(0);
  };

  return (
    <div className="space-y-5">
      <PageHeader title={t('title')} description={t('description')} />
      <div className="flex flex-wrap items-end gap-3 rounded-xl border border-border bg-surface p-3 shadow-plate">
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
        <p
          role="status"
          className="flex items-center gap-2 rounded-lg border border-success/20 bg-success/10 p-4 text-sm text-success"
        >
          <CheckCircle2 className="h-5 w-5 shrink-0" aria-hidden="true" />
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
        <EmptyState
          title={t('empty')}
          description={hasActiveFilters ? t('emptyHint') : ''}
          icon={History}
          action={
            hasActiveFilters ? { label: t('clearFilters'), onClick: handleClearFilters } : undefined
          }
        />
      )}
      {!isLoading && historyPage && historyPage.content.length > 0 && (
        <div className="bg-surface rounded-xl border border-border shadow-plate overflow-hidden">
          <div className="overflow-x-auto">
            <table className="block w-full text-sm text-left sm:table">
              <thead className="hidden plate-label bg-background border-b border-border sm:table-header-group">
                <tr>
                  <th className="px-4 py-3 font-semibold">{t('operation')}</th>
                  <th className="px-4 py-3 font-semibold">{t('name')}</th>
                  <th className="px-4 py-3 font-semibold">{t('date')}</th>
                  <th className="px-4 py-3 font-semibold">{t('status')}</th>
                  <th className="px-4 py-3 font-semibold text-right">{t('actions')}</th>
                </tr>
              </thead>
              <tbody className="block divide-y divide-border sm:table-row-group">
                {historyPage.content.map(item => {
                  const undone = !!item.undoneAt && !item.redoneAt;
                  const canAct = undone ? item.canRedo : item.canUndo;
                  const fields = historyFieldChanges(item.changedFieldsJson);
                  const reason = item.unavailableReason
                    ? t(`unavailableReasons.${item.unavailableReason}`)
                    : t('unavailable');
                  return (
                    <Fragment key={item.id}>
                      <tr className="grid grid-cols-[minmax(0,1fr)_auto] hover:bg-surface-elevated transition-colors sm:table-row">
                        <td className="col-start-1 row-start-1 px-4 pt-4 font-medium sm:py-4 sm:whitespace-nowrap">
                          {t(`operations.${item.operationType.toLowerCase()}`)}{' '}
                          {entityLabel(item.entityType)}
                        </td>
                        <td className="col-start-1 row-start-2 min-w-0 break-words px-4 py-2 sm:min-w-40 sm:py-4">
                          {item.entityLabel || `${entityLabel(item.entityType)} #${item.entityId}`}
                          {fields.length > 0 && (
                            <p className="mt-1 text-xs text-text-secondary">
                              {fields
                                .slice(0, 3)
                                .map(field =>
                                  t(`fields.${field.field}`, {
                                    defaultValue: field.field.replaceAll('_', ' '),
                                  })
                                )
                                .join(' · ')}
                              {fields.length > 3 ? '…' : ''}
                            </p>
                          )}
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
                        </td>
                        <td className="col-start-1 row-start-3 px-4 pb-4 text-text-secondary tabular-nums sm:py-4 sm:whitespace-nowrap">
                          {dateTime(item.createdAt)}
                        </td>
                        <td className="col-start-2 row-start-1 px-4 pt-4 text-right sm:py-4 sm:text-left">
                          <Badge
                            size="sm"
                            variant={undone ? 'warning' : item.redoneAt ? 'info' : 'success'}
                          >
                            {t(
                              undone
                                ? 'badge.undone'
                                : item.redoneAt
                                  ? 'badge.redone'
                                  : 'badge.active'
                            )}
                          </Badge>
                        </td>
                        <td className="col-start-2 row-start-2 row-span-2 px-4 py-2 text-right sm:py-4">
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
                            <p className="mt-2 text-xs text-text-secondary max-w-28 sm:min-w-44 sm:max-w-64 ml-auto">
                              {reason}
                            </p>
                          )}
                        </td>
                      </tr>
                      {expanded === item.id && (
                        <tr id={`history-details-${item.id}`} className="block sm:table-row">
                          <td colSpan={5} className="block px-4 py-4 bg-background sm:table-cell">
                            <HistoryEntryDetails entry={item} />
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
