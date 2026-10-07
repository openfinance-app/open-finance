import { isAxiosError } from 'axios';
import { formatDecimal } from '@/utils/format';
/**
 * BudgetsPage Component
 * TASK-8.2.7: Create BudgetsPage component with list, filters, and add button
 * TASK-8.4.1: Add search bar and pagination to BudgetsPage
 * TASK-8.4.2: Refactor BudgetsPage filters to match AccountsPage pattern
 * TASK-8.6.11: Add Auto-Create button and BudgetWizard integration
 *
 * Main page for managing budgets with progress tracking, collapsible filters,
 * and pagination (default page size 20, options [10, 20, 50, 100]).
 */
import { useState, useMemo, useEffect } from 'react';
import { useTranslation } from 'react-i18next';
import { Plus, Filter, Wand2 } from 'lucide-react';
import { useSearchParams } from 'react-router';
import { Button } from '@/components/ui/Button';
import { Dialog, DialogContent, DialogHeader, DialogTitle } from '@/components/ui/Dialog';
import { PageHeader } from '@/components/layout/PageHeader';
import { EmptyState } from '@/components/layout/EmptyState';
import { LoadingSkeleton } from '@/components/LoadingComponents';
import { Pagination } from '@/components/ui/Pagination';
import { BudgetCard } from '@/components/budgets/BudgetCard';
import { BudgetForm } from '@/components/budgets/BudgetForm';
import { BudgetFilters } from '@/components/budgets/BudgetFilters';
import type { BudgetFiltersState } from '@/components/budgets/BudgetFilters';
import { BudgetSummaryCard } from '@/components/budgets/BudgetSummaryCard';
import { AlertBanner } from '@/components/budgets/AlertBanner';
import { BudgetWizard } from '@/components/budgets/BudgetWizard';
import { BudgetDetailModal } from '@/components/budgets/BudgetDetailModal';
import { ConvertedAmount } from '@/components/ui/ConvertedAmount';
import { ConfirmationDialog } from '@/components/ConfirmationDialog';
import { useDocumentTitle } from '@/hooks/useDocumentTitle';
import {
  useBudgetSummary,
  useCreateBudget,
  useUpdateBudget,
  useDeleteBudget,
  useBudget,
  useBudgets,
} from '@/hooks/useBudgets';
import type { BudgetRequest, BudgetResponse } from '@/types/budget';
import { DEFAULT_PAGE_SIZE, PAGE_SIZE_OPTIONS } from '@/constants/pagination';
import { matchesQuery } from '@/utils/searchMatch';
import { activeBudgetSummary } from '@/utils/budget-summary';

export default function BudgetsPage() {
  const { t } = useTranslation('budgets');
  const { t: tc } = useTranslation('common');
  useDocumentTitle(t('title'));
  const [searchParams, setSearchParams] = useSearchParams();

  // Deep-link from budget alert notification: pre-populate keyword filter
  const alertKeywordParam = searchParams.get('alertKeyword') || undefined;

  // Unified filter state (mirrors AccountsPage pattern)
  const [filters, setFilters] = useState<BudgetFiltersState>({
    keyword: alertKeywordParam,
  });
  const [showFilters, setShowFilters] = useState(!!alertKeywordParam);

  const [currentPage, setCurrentPage] = useState(0);
  const [pageSize, setPageSize] = useState(DEFAULT_PAGE_SIZE);
  const [isFormOpen, setIsFormOpen] = useState(false);
  const [formError, setFormError] = useState<string | null>(null);
  const [formFieldErrors, setFormFieldErrors] = useState<Record<string, string>>({});
  const [editingBudgetId, setEditingBudgetId] = useState<number | null>(null);
  const [deletingBudgetId, setDeletingBudgetId] = useState<number | null>(null);
  const [detailBudgetId, setDetailBudgetId] = useState<number | null>(null);
  const [dismissedAlerts, setDismissedAlerts] = useState<Set<number>>(() => {
    try {
      const stored = localStorage.getItem('dismissed_budget_alerts');
      if (stored) return new Set(JSON.parse(stored) as number[]);
    } catch {
      // ignore parse errors
    }
    return new Set();
  });
  /** Controls visibility of the Auto-Create BudgetWizard dialog (TASK-8.6.11) */
  const [showWizard, setShowWizard] = useState(false);

  // Derive the period filter for the API call
  const periodFilter =
    filters.period === undefined || filters.period === '' ? undefined : filters.period;

  const { data: summary, isLoading: summaryLoading, error } = useBudgetSummary(periodFilter);
  const currentSummary = summary ? activeBudgetSummary(summary) : undefined;
  // The native list does not require exchange rates and remains editable if totals fail.
  const { data: nativeBudgets, isLoading: nativeLoading } = useBudgets(periodFilter, !!error);
  const { data: editingBudget, isLoading: editBudgetLoading } = useBudget(editingBudgetId);

  const createBudget = useCreateBudget();
  const updateBudget = useUpdateBudget();
  const deleteBudget = useDeleteBudget();

  // All budgets from the summary response
  const allBudgetProgress = useMemo(() => {
    if (!summary) return [];
    return summary.budgets;
  }, [summary]);

  // Detail loads by ID, independently of the list's filters and reporting conversion.
  const openParam = Number(searchParams.get('open'));
  useEffect(() => {
    if (!Number.isSafeInteger(openParam) || openParam <= 0) return;
    setFilters({});
    setCurrentPage(0);
    setDetailBudgetId(openParam);
    const next = new URLSearchParams(searchParams);
    next.delete('open');
    next.delete('alertKeyword');
    setSearchParams(next, { replace: true });
  }, [openParam, searchParams, setSearchParams]);

  const filteredNativeBudgets = (nativeBudgets ?? []).filter(
    budget =>
      !(filters.keyword || '').trim() ||
      matchesQuery(budget.categoryName, (filters.keyword || '').trim(), !!filters.keywordRegex)
  );

  // Apply keyword filter (client-side, case-insensitive or regex match on category name)
  const filteredBudgets = useMemo(() => {
    const query = (filters.keyword || '').trim();
    if (!query) return allBudgetProgress;
    return allBudgetProgress.filter(b =>
      matchesQuery(b.categoryName, query, !!filters.keywordRegex)
    );
  }, [allBudgetProgress, filters.keyword, filters.keywordRegex]);

  // Paginate the filtered results
  const totalElements = filteredBudgets.length;
  const totalPages = Math.max(1, Math.ceil(totalElements / pageSize));
  const visiblePage = Math.min(currentPage, totalPages - 1);
  if (!summaryLoading && currentPage !== visiblePage) setCurrentPage(visiblePage);
  const paginatedBudgets = useMemo(() => {
    const start = visiblePage * pageSize;
    return filteredBudgets.slice(start, start + pageSize);
  }, [filteredBudgets, visiblePage, pageSize]);

  // Determine whether any filter is currently active
  const hasActiveFilters =
    (filters.keyword !== undefined && filters.keyword !== '') ||
    (filters.period !== undefined && filters.period !== '');

  /** Handle changes from the BudgetFilters panel; resets to page 0. */
  const handleFiltersChange = (newFilters: BudgetFiltersState) => {
    setFilters(newFilters);
    setCurrentPage(0);
  };

  const handlePageSizeChange = (size: number) => {
    setPageSize(size);
    setCurrentPage(0);
  };

  // Alert banners for budgets that need attention (WARNING or EXCEEDED)
  const budgetAlerts = (currentSummary?.budgets ?? [])
    .filter(
      budget =>
        (budget.status === 'WARNING' || budget.status === 'EXCEEDED') &&
        !dismissedAlerts.has(budget.budgetId)
    )
    .slice(0, 3); // Show max 3 alerts

  const handleCreate = () => {
    setEditingBudgetId(null);
    setFormError(null);
    setFormFieldErrors({});
    setIsFormOpen(true);
  };

  const handleEdit = (budgetId: number) => {
    setEditingBudgetId(budgetId);
    setFormError(null);
    setFormFieldErrors({});
    setIsFormOpen(true);
  };

  const handleDelete = (budgetId: number) => {
    setDeletingBudgetId(budgetId);
  };

  const handleViewDetail = (budgetId: number) => {
    setDetailBudgetId(budgetId);
  };

  const handleFormSubmit = async (data: BudgetRequest) => {
    try {
      setFormError(null);
      setFormFieldErrors({});
      if (editingBudgetId) {
        await updateBudget.mutateAsync({ id: editingBudgetId, data });
      } else {
        await createBudget.mutateAsync(data);
      }
      setIsFormOpen(false);
      setEditingBudgetId(null);
    } catch (err: unknown) {
      const response = isAxiosError<{
        message?: string;
        validationErrors?: Record<string, string>;
      }>(err)
        ? err.response?.data
        : undefined;
      setFormFieldErrors(response?.validationErrors ?? {});
      setFormError(
        response?.validationErrors
          ? null
          : response?.message || (err instanceof Error ? err.message : t('saveError'))
      );
    }
  };

  const handleConfirmDelete = async () => {
    if (!deletingBudgetId) return;
    try {
      await deleteBudget.mutateAsync(deletingBudgetId);
      setDeletingBudgetId(null);
    } catch (err) {
      console.error('Failed to delete budget:', err);
    }
  };

  const handleFormCancel = () => {
    setIsFormOpen(false);
    setEditingBudgetId(null);
    setFormError(null);
    setFormFieldErrors({});
  };

  const handleDismissAlert = (budgetId: number) => {
    setDismissedAlerts(prev => {
      const next = new Set(prev).add(budgetId);
      localStorage.setItem('dismissed_budget_alerts', JSON.stringify([...next]));
      return next;
    });
  };

  const deletingBudgetName =
    allBudgetProgress.find(b => b.budgetId === deletingBudgetId)?.categoryName ??
    nativeBudgets?.find(b => b.id === deletingBudgetId)?.categoryName;

  return (
    <div className="p-8">
      {/* Header */}
      <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-4 mb-6">
        <PageHeader title={t('title')} description={t('description')} />
        <div className="flex flex-wrap items-center gap-3 shrink-0">
          {/* Filter toggle button — primary when panel is open, outline when closed */}
          <Button
            variant={showFilters ? 'primary' : 'outline'}
            onClick={() => setShowFilters(!showFilters)}
          >
            <Filter className="h-4 w-4 mr-2" />
            {t('filters.label')}
          </Button>
          {/* Auto-Create button — opens BudgetWizard for analysis-driven bulk creation (TASK-8.6.11) */}
          <Button variant="outline" onClick={() => setShowWizard(true)}>
            <Wand2 className="h-4 w-4 mr-2" />
            {t('autoCreate')}
          </Button>
          <Button variant="primary" onClick={handleCreate}>
            <Plus className="h-4 w-4 mr-2" />
            {t('addBudget')}
          </Button>
        </div>
      </div>

      {/* Collapsible Filters Panel */}
      {showFilters && (
        <div className="mb-6">
          <BudgetFilters filters={filters} onFiltersChange={handleFiltersChange} />
        </div>
      )}

      {/* Alerts */}
      {!error && budgetAlerts.length > 0 && (
        <div className="space-y-3 mb-6">
          {budgetAlerts.map(budget => (
            <AlertBanner
              key={budget.budgetId}
              variant={budget.status === 'EXCEEDED' ? 'error' : 'warning'}
              title={
                budget.status === 'EXCEEDED'
                  ? t('alerts.exceeded', { categoryName: budget.categoryName })
                  : t('alerts.warning', { categoryName: budget.categoryName })
              }
              message={
                budget.status === 'EXCEEDED'
                  ? t('alerts.exceededMessage', {
                      pct: formatDecimal(budget.percentageSpent, 1),
                      categoryName: budget.categoryName,
                    })
                  : t('alerts.warningMessage', {
                      pct: formatDecimal(budget.percentageSpent, 1),
                      categoryName: budget.categoryName,
                    })
              }
              onDismiss={() => handleDismissAlert(budget.budgetId)}
            />
          ))}
        </div>
      )}

      {/* Summary Card */}
      {!error && !summaryLoading && summary && summary.totalBudgets > 0 && currentSummary && (
        <BudgetSummaryCard
          summary={currentSummary}
          filteredBudgets={
            hasActiveFilters
              ? currentSummary.budgets.filter(budget =>
                  filteredBudgets.some(filtered => filtered.budgetId === budget.budgetId)
                )
              : undefined
          }
        />
      )}

      {error && (
        <div className="space-y-4">
          <div
            role="alert"
            className="p-4 bg-error/10 border border-error/20 rounded-lg text-error"
          >
            {t('summaryUnavailable')}
          </div>
          {nativeLoading ? (
            <LoadingSkeleton className="h-32" />
          ) : !nativeBudgets ? (
            <p className="text-error">{t('loadError')}</p>
          ) : (
            <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-4">
              {filteredNativeBudgets.map(budget => (
                <div
                  key={budget.id}
                  className="p-4 rounded-lg border border-border bg-surface space-y-3"
                >
                  <h2 className="font-semibold">{budget.categoryName}</h2>
                  <p className="text-sm text-text-secondary">
                    {t(`form.periods.${budget.period}`)} · {budget.startDate} – {budget.endDate}
                  </p>
                  <ConvertedAmount
                    amount={Number(budget.amount)}
                    currency={budget.currency}
                    inline
                  />
                  <div className="flex gap-2">
                    <Button variant="outline" onClick={() => handleEdit(budget.id)}>
                      {tc('aria.editBudget')}
                    </Button>
                    <Button variant="outline" onClick={() => handleDelete(budget.id)}>
                      {tc('aria.deleteBudget')}
                    </Button>
                  </div>
                </div>
              ))}
            </div>
          )}
        </div>
      )}

      {/* Loading State */}
      {summaryLoading && (
        <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-4">
          {[...Array(6)].map((_, i) => (
            <LoadingSkeleton key={i} className="h-64" />
          ))}
        </div>
      )}

      {/* Empty State — no budgets at all */}
      {!error && !summaryLoading && allBudgetProgress.length === 0 && (
        <EmptyState
          title={t('empty.noBudgets')}
          description={t('empty.addFirst')}
          action={{
            label: t('addBudget'),
            onClick: handleCreate,
          }}
        />
      )}

      {/* Empty State — filters returned no results */}
      {!error &&
        !summaryLoading &&
        allBudgetProgress.length > 0 &&
        filteredBudgets.length === 0 && (
          <EmptyState
            title={t('empty.noMatch')}
            description={
              hasActiveFilters ? t('empty.noMatchDescription') : t('empty.noBudgetsFound')
            }
            action={
              hasActiveFilters
                ? {
                    label: t('empty.clearFilters'),
                    onClick: () => handleFiltersChange({}),
                  }
                : undefined
            }
          />
        )}

      {/* Budgets Grid */}
      {!error && !summaryLoading && paginatedBudgets.length > 0 && (
        <>
          <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-4">
            {paginatedBudgets.map(budget => (
              <BudgetCard
                key={budget.budgetId}
                budget={budget}
                onEdit={handleEdit}
                onDelete={handleDelete}
                onViewDetail={handleViewDetail}
              />
            ))}
          </div>

          {/* Pagination — shown whenever totalPages > 1 (matches AccountsPage) */}
          {totalPages > 1 && (
            <div className="mt-6">
              <Pagination
                currentPage={visiblePage}
                totalPages={totalPages}
                pageSize={pageSize}
                totalElements={totalElements}
                onPageChange={setCurrentPage}
                onPageSizeChange={handlePageSizeChange}
                pageSizeOptions={PAGE_SIZE_OPTIONS}
              />
            </div>
          )}
        </>
      )}

      {/* Create/Edit Dialog */}
      <Dialog
        open={isFormOpen}
        onOpenChange={open => {
          if (!open && (createBudget.isPending || updateBudget.isPending)) return;
          setIsFormOpen(open);
        }}
      >
        <DialogContent className="sm:max-w-[500px] max-h-[90vh] overflow-y-auto">
          <DialogHeader>
            <DialogTitle>
              {editingBudgetId ? t('dialogs.editTitle') : t('dialogs.createTitle')}
            </DialogTitle>
          </DialogHeader>
          {editingBudgetId && editBudgetLoading ? (
            <LoadingSkeleton className="h-40" />
          ) : (
            <BudgetForm
              key={editingBudgetId ?? 'new'}
              budget={editingBudgetId ? (editingBudget as BudgetResponse | undefined) : undefined}
              onSubmit={handleFormSubmit}
              onCancel={handleFormCancel}
              isLoading={createBudget.isPending || updateBudget.isPending}
              serverError={formError}
              serverFieldErrors={formFieldErrors}
            />
          )}
        </DialogContent>
      </Dialog>

      {/* Delete Confirmation Dialog */}
      <ConfirmationDialog
        open={!!deletingBudgetId}
        onOpenChange={open => !open && setDeletingBudgetId(null)}
        onConfirm={handleConfirmDelete}
        title={t('dialogs.delete.title')}
        description={t('dialogs.delete.description', { name: deletingBudgetName })}
        confirmText={t('dialogs.delete.confirmText')}
        variant="danger"
        loading={deleteBudget.isPending}
      />

      {/* Auto-Create Wizard Dialog (TASK-8.6.11) — REQ-2.9.1.5 */}
      <BudgetWizard open={showWizard} onClose={() => setShowWizard(false)} />

      {/* Budget Detail Modal */}
      {detailBudgetId !== null && (
        <BudgetDetailModal budgetId={detailBudgetId} onClose={() => setDetailBudgetId(null)} />
      )}
    </div>
  );
}
