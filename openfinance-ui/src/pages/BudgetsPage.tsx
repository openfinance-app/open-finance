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
import { useState, useMemo } from 'react';
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
import { ConfirmationDialog } from '@/components/ConfirmationDialog';
import { useDocumentTitle } from '@/hooks/useDocumentTitle';
import {
  useBudgetSummary,
  useCreateBudget,
  useUpdateBudget,
  useDeleteBudget,
  useBudget,
} from '@/hooks/useBudgets';
import type { BudgetRequest, BudgetResponse } from '@/types/budget';

const PAGE_SIZE_OPTIONS = [10, 20, 50, 100];
const DEFAULT_PAGE_SIZE = 20;

export default function BudgetsPage() {
  const { t } = useTranslation('budgets');
  useDocumentTitle(t('title'));
  const [searchParams] = useSearchParams();

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
    filters.period === undefined || filters.period === ''
      ? undefined
      : filters.period;

  const { data: summary, isLoading: summaryLoading, error } = useBudgetSummary(periodFilter);
  const { data: editingBudget, isLoading: editBudgetLoading } = useBudget(editingBudgetId);

  const createBudget = useCreateBudget();
  const updateBudget = useUpdateBudget();
  const deleteBudget = useDeleteBudget();

  // All budgets from the summary response
  const allBudgetProgress = useMemo(() => {
    if (!summary) return [];
    return summary.budgets;
  }, [summary]);

  // Apply keyword filter (client-side, case-insensitive match on category name)
  const filteredBudgets = useMemo(() => {
    const query = (filters.keyword || '').trim().toLowerCase();
    if (!query) return allBudgetProgress;
    return allBudgetProgress.filter((b) =>
      b.categoryName.toLowerCase().includes(query)
    );
  }, [allBudgetProgress, filters.keyword]);

  // Paginate the filtered results
  const totalElements = filteredBudgets.length;
  const totalPages = Math.max(1, Math.ceil(totalElements / pageSize));
  const paginatedBudgets = useMemo(() => {
    const start = currentPage * pageSize;
    return filteredBudgets.slice(start, start + pageSize);
  }, [filteredBudgets, currentPage, pageSize]);

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
  const budgetAlerts = useMemo(() => {
    if (!allBudgetProgress) return [];
    return allBudgetProgress
      .filter(
        (budget) =>
          (budget.status === 'WARNING' || budget.status === 'EXCEEDED') &&
          !dismissedAlerts.has(budget.budgetId)
      )
      .slice(0, 3); // Show max 3 alerts
  }, [allBudgetProgress, dismissedAlerts]);

  const handleCreate = () => {
    setEditingBudgetId(null);
    setFormError(null);
    setIsFormOpen(true);
  };

  const handleEdit = (budgetId: number) => {
    setEditingBudgetId(budgetId);
    setFormError(null);
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
      if (editingBudgetId) {
        await updateBudget.mutateAsync({ id: editingBudgetId, data });
      } else {
        await createBudget.mutateAsync(data);
      }
      setIsFormOpen(false);
      setEditingBudgetId(null);
    } catch (err: any) {
      console.error('Failed to save budget:', err);
      setFormError(err.response?.data?.message || err.message || t('saveError'));
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
  };

  const handleDismissAlert = (budgetId: number) => {
    setDismissedAlerts((prev) => {
      const next = new Set(prev).add(budgetId);
      localStorage.setItem('dismissed_budget_alerts', JSON.stringify([...next]));
      return next;
    });
  };

  const deletingBudgetName = allBudgetProgress.find(
    (b) => b.budgetId === deletingBudgetId
  )?.categoryName;

  if (error) {
    return (
      <div className="p-8">
        <PageHeader title={t('title')} />
        <div className="mt-6 p-4 bg-error/10 border border-error/20 rounded-lg text-error">
          {t('loadError')}
        </div>
      </div>
    );
  }

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
      {budgetAlerts.length > 0 && (
        <div className="space-y-3 mb-6">
          {budgetAlerts.map((budget) => (
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
                  ? t('alerts.exceededMessage', { pct: budget.percentageSpent.toFixed(1), categoryName: budget.categoryName })
                  : t('alerts.warningMessage', { pct: budget.percentageSpent.toFixed(1), categoryName: budget.categoryName })
              }
              onDismiss={() => handleDismissAlert(budget.budgetId)}
            />
          ))}
        </div>
      )}

      {/* Summary Card */}
      {!summaryLoading && summary && summary.totalBudgets > 0 && (
        <BudgetSummaryCard
          summary={summary}
          filteredBudgets={hasActiveFilters ? filteredBudgets : undefined}
        />
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
      {!summaryLoading && allBudgetProgress.length === 0 && (
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
      {!summaryLoading && allBudgetProgress.length > 0 && filteredBudgets.length === 0 && (
        <EmptyState
          title={t('empty.noMatch')}
          description={
            hasActiveFilters
              ? t('empty.noMatchDescription')
              : t('empty.noBudgetsFound')
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
      {!summaryLoading && paginatedBudgets.length > 0 && (
        <>
          <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-4">
            {paginatedBudgets.map((budget) => (
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
                currentPage={currentPage}
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
        onOpenChange={(open) => {
          if (!open && (createBudget.isPending || updateBudget.isPending)) return;
          setIsFormOpen(open);
        }}
      >
        <DialogContent className="sm:max-w-[500px] max-h-[90vh] overflow-y-auto">
          <DialogHeader>
            <DialogTitle>{editingBudgetId ? t('dialogs.editTitle') : t('dialogs.createTitle')}</DialogTitle>
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
            />
          )}
        </DialogContent>
      </Dialog>

      {/* Delete Confirmation Dialog */}
      <ConfirmationDialog
        open={!!deletingBudgetId}
        onOpenChange={(open) => !open && setDeletingBudgetId(null)}
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
        <BudgetDetailModal
          budgetId={detailBudgetId}
          onClose={() => setDetailBudgetId(null)}
        />
      )}
    </div>
  );
}
