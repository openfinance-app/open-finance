import { useState } from 'react';
import { useTranslation } from 'react-i18next';
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { History, Undo2, Redo2, AlertCircle } from 'lucide-react';
import { PageHeader } from '@/components/layout/PageHeader';
import { EmptyState } from '@/components/layout/EmptyState';
import { LoadingSkeleton } from '@/components/LoadingComponents';
import { Pagination } from '@/components/ui/Pagination';
import { Button } from '@/components/ui/Button';
import { historyService } from '@/services/historyService';
import type { EntityType, OperationHistoryResponse } from '@/types/history';
import { useDocumentTitle } from '@/hooks/useDocumentTitle';
import { useAuthContext } from '@/context/AuthContext';
import { format } from 'date-fns';

const PAGE_SIZE_OPTIONS = [10, 20, 50, 100];
const DEFAULT_PAGE_SIZE = 20;

export default function HistoryPage() {
  const { t } = useTranslation('history');
  useDocumentTitle(t('title'));
  const queryClient = useQueryClient();

  const [currentPage, setCurrentPage] = useState(0);
  const [pageSize, setPageSize] = useState(DEFAULT_PAGE_SIZE);
  const [entityTypeFilter, setEntityTypeFilter] = useState<EntityType | undefined>(undefined);

  const { sessionStartTime } = useAuthContext();

  const { data: historyPage, isLoading, error } = useQuery({
    queryKey: ['history', currentPage, pageSize, entityTypeFilter, sessionStartTime],
    queryFn: () => historyService.getHistory(currentPage, pageSize, entityTypeFilter, sessionStartTime ?? undefined),
    enabled: !!sessionStartTime,
  });

  const undoMutation = useMutation({
    mutationFn: (id: number) => historyService.undo(id),
    onSuccess: () => {
      // Refresh history and other relevant data
      queryClient.invalidateQueries({ queryKey: ['history'] });
      queryClient.invalidateQueries(); 
    },
  });

  const redoMutation = useMutation({
    mutationFn: (id: number) => historyService.redo(id),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['history'] });
      queryClient.invalidateQueries();
    },
  });

  const handleUndo = (id: number) => {
    undoMutation.mutate(id);
  };

  const handleRedo = (id: number) => {
    redoMutation.mutate(id);
  };

  const handlePageSizeChange = (size: number) => {
    setPageSize(size);
    setCurrentPage(0);
  };

  if (error) {
    return (
      <div className="p-8">
        <PageHeader title={t('title')} description={t('description')} />
        <div className="mt-6 p-4 bg-error/10 border border-error/20 rounded-lg text-error flex items-center">
          <AlertCircle className="w-5 h-5 mr-2" />
          {t('loadError', 'Error loading history')}
        </div>
      </div>
    );
  }

  if (!sessionStartTime) {
    return (
      <div className="p-8">
        <PageHeader title={t('title')} description={t('description')} />
        <EmptyState title={t('empty')} description="" icon={History} />
      </div>
    );
  }

  const getEntityOperationLabel = (item: OperationHistoryResponse) => {
    const typeLabel = t(`filters.${item.entityType.toLowerCase()}`);
    const opLabel = t(`operations.${item.operationType.toLowerCase()}`);
    return `${opLabel} ${typeLabel}`;
  };

  return (
    <div className="p-8">
      <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-4 mb-6">
        <PageHeader title={t('title')} description={t('description')} />
        
        <div className="flex items-center gap-3 shrink-0">
          <select 
            className="h-10 px-3 py-2 border rounded-md border-input bg-background/50 text-sm focus:outline-none focus:ring-2 focus:ring-primary/50"
            value={entityTypeFilter || ''}
            onChange={(e) => {
              setEntityTypeFilter(e.target.value ? (e.target.value as EntityType) : undefined);
              setCurrentPage(0);
            }}
          >
            <option value="">{t('filters.all')}</option>
            <option value="ACCOUNT">{t('filters.account')}</option>
            <option value="TRANSACTION">{t('filters.transaction')}</option>
            <option value="ASSET">{t('filters.asset')}</option>
            <option value="LIABILITY">{t('filters.liability')}</option>
            <option value="REAL_ESTATE">{t('filters.realEstate')}</option>
            <option value="BUDGET">{t('filters.budget')}</option>
            <option value="CATEGORY">{t('filters.category')}</option>
          </select>
        </div>
      </div>

      {isLoading && (
        <div className="space-y-4">
          {[...Array(5)].map((_, i) => (
            <LoadingSkeleton key={i} className="h-20" />
          ))}
        </div>
      )}

      {!isLoading && historyPage?.content.length === 0 && (
        <EmptyState
          title={t('empty')}
          description=""
          icon={History}
        />
      )}

      {!isLoading && historyPage && historyPage.content.length > 0 && (
        <div className="bg-card rounded-lg border border-border shadow-sm overflow-hidden">
          <table className="w-full text-sm text-left">
            <thead className="text-xs uppercase bg-background/50 text-muted-foreground border-b border-border">
              <tr>
                <th className="px-6 py-4">{t('operation')}</th>
                <th className="px-6 py-4">{t('name')}</th>
                <th className="px-6 py-4">{t('date')}</th>
                <th className="px-6 py-4">{t('status')}</th>
                <th className="px-6 py-4 text-right">{t('actions')}</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-border">
              {historyPage.content.map((item) => {
                const isUndone = !!item.undoneAt && !item.redoneAt;
                const isRedone = !!item.redoneAt;
                
                return (
                  <tr key={item.id} className="hover:bg-muted/50 transition-colors group">
                    <td className="px-6 py-4 font-medium">
                      {getEntityOperationLabel(item)}
                    </td>
                    <td className="px-6 py-4">
                      {item.entityLabel || '-'}
                    </td>
                    <td className="px-6 py-4 text-muted-foreground">
                      {item.operationDate ? format(new Date(item.operationDate), 'PP pp') : '-'}
                    </td>
                    <td className="px-6 py-4">
                      {isUndone ? (
                        <span className="inline-flex items-center px-2.5 py-0.5 rounded-full text-xs font-medium bg-warning/10 text-warning">
                          {t('badge.undone')}
                        </span>
                      ) : isRedone ? (
                        <span className="inline-flex items-center px-2.5 py-0.5 rounded-full text-xs font-medium bg-primary/10 text-primary">
                          {t('badge.redone')}
                        </span>
                      ) : (
                        <span className="inline-flex items-center px-2.5 py-0.5 rounded-full text-xs font-medium bg-success/10 text-success">
                          {t('badge.active')}
                        </span>
                      )}
                    </td>
                    <td className="px-6 py-4 text-right space-x-2">
                       <Button
                        variant="outline"
                        size="sm"
                        onClick={() => handleUndo(item.id)}
                        disabled={isUndone || undoMutation.isPending || redoMutation.isPending}
                        className="h-8 px-2"
                      >
                        <Undo2 className="w-4 h-4 mr-1" />
                        {t('undo')}
                      </Button>
                      <Button
                        variant="outline"
                        size="sm"
                        onClick={() => handleRedo(item.id)}
                        disabled={!isUndone || undoMutation.isPending || redoMutation.isPending}
                        className="h-8 px-2"
                      >
                        <Redo2 className="w-4 h-4 mr-1" />
                        {t('redo')}
                      </Button>
                    </td>
                  </tr>
                );
              })}
            </tbody>
          </table>
          
          {historyPage.totalPages > 1 && (
            <div className="p-4 border-t border-border">
              <Pagination
                currentPage={currentPage}
                totalPages={historyPage.totalPages}
                pageSize={pageSize}
                totalElements={historyPage.totalElements}
                onPageChange={setCurrentPage}
                onPageSizeChange={handlePageSizeChange}
                pageSizeOptions={PAGE_SIZE_OPTIONS}
              />
            </div>
          )}
        </div>
      )}
    </div>
  );
}
