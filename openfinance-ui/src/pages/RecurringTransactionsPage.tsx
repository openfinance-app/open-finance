/**
 * RecurringTransactionsPage Component
 * Task 12.2.8: Create RecurringTransactionsPage component
 * 
 * Main page for viewing and managing recurring transactions
 */
import { useState } from 'react';
import { useTranslation } from 'react-i18next';
import { Plus, Filter, Calendar, Search } from 'lucide-react';
import { Button } from '@/components/ui/Button';
import { Dialog, DialogContent, DialogHeader, DialogTitle } from '@/components/ui/Dialog';
import { Pagination } from '@/components/ui/Pagination';
import { PageHeader } from '@/components/layout/PageHeader';
import { EmptyState } from '@/components/layout/EmptyState';
import { LoadingSkeleton } from '@/components/LoadingComponents';
import { RecurringTransactionCard } from '@/components/transactions/RecurringTransactionCard';
import { RecurringTransactionDetailModal } from '@/components/transactions/RecurringTransactionDetailModal';
import { ConfirmationDialog } from '@/components/ConfirmationDialog';
import { useDocumentTitle } from '@/hooks/useDocumentTitle';
import { AccountSelector } from '@/components/ui/AccountSelector';
import {
  useRecurringTransactionsPaged,
  useCreateRecurringTransaction,
  useUpdateRecurringTransaction,
  useDeleteRecurringTransaction,
  usePauseRecurringTransaction,
  useResumeRecurringTransaction,
} from '@/hooks/useRecurringTransactions';
import { useAccounts } from '@/hooks/useAccounts';
import { useCategories } from '@/hooks/useTransactions';
import type {
  RecurringTransaction,
  RecurringTransactionRequest,
  RecurringTransactionFilters,
} from '@/types/recurringTransaction';
import { Badge } from '@/components/ui/Badge';
import { RecurringTransactionForm } from '@/components/transactions/RecurringTransactionForm';
import { Input } from '@/components/ui/Input';
import { FETCH_ALL_PAGE_SIZE } from '@/constants/pagination';

export default function RecurringTransactionsPage() {
  const { t } = useTranslation('recurring');
  useDocumentTitle(t('title'));
  
  const [filters, setFilters] = useState<RecurringTransactionFilters>({ page: 0, size: 20, sort: 'nextOccurrence,asc' });
  const [showFilters, setShowFilters] = useState(false);
  const [isFormOpen, setIsFormOpen] = useState(false);
  const [editingTransaction, setEditingTransaction] = useState<RecurringTransaction | null>(null);
  const [deletingTransaction, setDeletingTransaction] = useState<RecurringTransaction | null>(null);
  const [detailTransaction, setDetailTransaction] = useState<RecurringTransaction | null>(null);
  const [formError, setFormError] = useState<string | null>(null);

  const { data: recurringTransactionsPage, isLoading, error } = useRecurringTransactionsPaged(filters);
  // Fetch all (unfiltered) for global stat totals
  const { data: allRecurringPage } = useRecurringTransactionsPaged({ page: 0, size: FETCH_ALL_PAGE_SIZE, sort: 'nextOccurrence,asc' });
  // Fetch all matching current filters (no pagination) for filtered stats
  const { data: allFilteredPage } = useRecurringTransactionsPaged({ ...filters, page: 0, size: FETCH_ALL_PAGE_SIZE });
  const { data: accounts = [] } = useAccounts();
  const { data: categories = [] } = useCategories();
  const createMutation = useCreateRecurringTransaction();
  const updateMutation = useUpdateRecurringTransaction();
  const deleteMutation = useDeleteRecurringTransaction();
  const pauseMutation = usePauseRecurringTransaction();
  const resumeMutation = useResumeRecurringTransaction();

  const recurringTransactions = recurringTransactionsPage?.content || [];
  const allRecurring = allRecurringPage?.content || [];
  const allFiltered = allFilteredPage?.content || [];

  const handleCreate = () => {
    setEditingTransaction(null);
    setFormError(null);
    setIsFormOpen(true);
  };

  const handleEdit = (recurringTransaction: RecurringTransaction) => {
    setDetailTransaction(null);
    setEditingTransaction(recurringTransaction);
    setFormError(null);
    setIsFormOpen(true);
  };

  const handleViewDetail = (recurringTransaction: RecurringTransaction) => {
    setDetailTransaction(recurringTransaction);
  };

  const handleDelete = (recurringTransaction: RecurringTransaction) => {
    setDeletingTransaction(recurringTransaction);
  };

  const handleFormSubmit = async (data: RecurringTransactionRequest) => {
    setFormError(null); // Clear previous errors
    try {
      if (editingTransaction) {
        await updateMutation.mutateAsync({ id: editingTransaction.id, data });
      } else {
        await createMutation.mutateAsync(data);
      }
      setIsFormOpen(false);
      setEditingTransaction(null);
    } catch (error: any) {
      // Extract user-friendly error message
      const errorMessage = 
        error?.response?.data?.message || 
        error?.response?.data?.error ||
        error?.message || 
        t('saveError');
      
      setFormError(errorMessage);
      console.error('Failed to save recurring transaction:', error);
    }
  };

  const handleConfirmDelete = async () => {
    if (!deletingTransaction) return;

    try {
      await deleteMutation.mutateAsync(deletingTransaction.id);
      setDeletingTransaction(null);
    } catch (error) {
      console.error('Failed to delete recurring transaction:', error);
    }
  };

  const handleFormCancel = () => {
    setIsFormOpen(false);
    setEditingTransaction(null);
    setFormError(null);
  };

  const handlePause = async (id: number) => {
    try {
      await pauseMutation.mutateAsync(id);
    } catch (error) {
      console.error('Failed to pause recurring transaction:', error);
    }
  };

  const handleResume = async (id: number) => {
    try {
      await resumeMutation.mutateAsync(id);
    } catch (error) {
      console.error('Failed to resume recurring transaction:', error);
    }
  };

  const handleFilterChange = (key: keyof RecurringTransactionFilters, value: any) => {
    setFilters((prev) => ({
      ...prev,
      [key]: value === 'all' ? undefined : value,
    }));
  };

  const handleFiltersChange = (newFilters: RecurringTransactionFilters) => {
    setFilters({ ...newFilters, page: 0, size: filters.size || 20 });
  };

  const handlePageChange = (page: number) => {
    setFilters({ ...filters, page });
  };

  const handlePageSizeChange = (size: number) => {
    setFilters({ ...filters, page: 0, size });
  };

  const clearFilters = () => {
    setFilters({ page: 0, size: filters.size || 20, sort: filters.sort || 'nextOccurrence,asc' });
  };

  /** True when any meaningful filter (beyond pagination/sort) is active */
  const isFiltered = !!(filters.search || filters.type || filters.frequency ||
    filters.isActive !== undefined || filters.accountId);

  // Global stats — always across the full unfiltered dataset
  const activeCount = allRecurring.filter((rt) => rt.isActive && !rt.isEnded).length;
  const pausedCount = allRecurring.filter((rt) => !rt.isActive && !rt.isEnded).length;
  const endedCount = allRecurring.filter((rt) => rt.isEnded).length;
  const dueCount = allRecurring.filter((rt) => rt.isDue).length;

  // Filtered stats — counts within the current filter selection
  const filteredActiveCount = allFiltered.filter((rt) => rt.isActive && !rt.isEnded).length;
  const filteredPausedCount = allFiltered.filter((rt) => !rt.isActive && !rt.isEnded).length;
  const filteredEndedCount = allFiltered.filter((rt) => rt.isEnded).length;
  const filteredDueCount = allFiltered.filter((rt) => rt.isDue).length;

  if (error) {
    return (
      <div className="container mx-auto px-4 py-8">
        <div className="text-center text-red-500">
          {t('loadError', { message: error.message })}
        </div>
      </div>
    );
  }

  return (
    <div className="container mx-auto px-4 py-8">
      {/* Page Header */}
      <PageHeader
        title={t('title')}
        description={t('description')}
      />

      {/* Action Bar */}
      <div className="flex flex-col sm:flex-row items-start sm:items-center justify-between gap-4 mb-6">
        {/* Statistics */}
        <div className="flex flex-wrap items-center gap-2">
          <Badge variant="success">
            {isFiltered
              ? t('badges.activeFiltered', { filtered: filteredActiveCount, total: activeCount })
              : t('badges.active', { count: activeCount })}
          </Badge>
          <Badge variant="info">
            {isFiltered
              ? t('badges.pausedFiltered', { filtered: filteredPausedCount, total: pausedCount })
              : t('badges.paused', { count: pausedCount })}
          </Badge>
          <Badge variant="default">
            {isFiltered
              ? t('badges.endedFiltered', { filtered: filteredEndedCount, total: endedCount })
              : t('badges.ended', { count: endedCount })}
          </Badge>
          {(isFiltered ? filteredDueCount : dueCount) > 0 && (
            <Badge variant="error">
              {isFiltered
                ? t('badges.dueFiltered', { filtered: filteredDueCount, total: dueCount })
                : t('badges.due', { count: dueCount })}
            </Badge>
          )}
        </div>

        {/* Actions */}
        <div className="flex items-center gap-2">
          <Button variant="secondary" size="sm" onClick={() => setShowFilters(!showFilters)}>
            <Filter className="h-4 w-4 mr-2" />
            {t('filters.label')}
          </Button>
          <Button onClick={handleCreate}>
            <Plus className="h-4 w-4 mr-2" />
            {t('addRecurring')}
          </Button>
        </div>
      </div>

      {/* Filters Panel */}
      {showFilters && (
        <div className="mb-6 p-4 bg-surface rounded-lg border border-border">
          {/* Search */}
          <div className="mb-4">
            <label htmlFor="search" className="block text-sm font-medium mb-2">{t('filters.search')}</label>
            <div className="relative">
              <Search className="absolute left-3 top-1/2 -translate-y-1/2 h-4 w-4 text-text-tertiary" />
              <Input
                id="search"
                type="text"
                placeholder={t('filters.searchPlaceholder')}
                value={filters.search || ''}
                onChange={(e: React.ChangeEvent<HTMLInputElement>) => handleFiltersChange({ ...filters, search: e.target.value })}
                className="pl-10"
              />
            </div>
          </div>

          <div className="grid grid-cols-1 sm:grid-cols-2 md:grid-cols-4 gap-4">
            {/* Account Filter - using AccountSelector */}
            <div>
              <label className="block text-sm font-medium mb-2">{t('filters.account')}</label>
              <AccountSelector
                value={filters.accountId}
                onValueChange={(val) => handleFiltersChange({ ...filters, accountId: val })}
                placeholder={t('filters.allAccounts')}
                allowNone={true}
              />
            </div>

            {/* Type Filter */}
            <div>
              <label className="block text-sm font-medium mb-2">{t('filters.type')}</label>
              <select
                className="w-full px-3 py-2 bg-background border border-border rounded-md"
                value={filters.type || 'all'}
                onChange={(e) => handleFilterChange('type', e.target.value)}
              >
                <option value="all">{t('filters.allTypes')}</option>
                <option value="INCOME">{t('filters.income')}</option>
                <option value="EXPENSE">{t('filters.expense')}</option>
                <option value="TRANSFER">{t('filters.transfer')}</option>
              </select>
            </div>

            {/* Frequency Filter */}
            <div>
              <label className="block text-sm font-medium mb-2">{t('filters.frequency')}</label>
              <select
                className="w-full px-3 py-2 bg-background border border-border rounded-md"
                value={filters.frequency || 'all'}
                onChange={(e) => handleFilterChange('frequency', e.target.value)}
              >
                <option value="all">{t('filters.allFrequencies')}</option>
                <option value="DAILY">{t('filters.daily')}</option>
                <option value="WEEKLY">{t('filters.weekly')}</option>
                <option value="BIWEEKLY">{t('filters.biweekly')}</option>
                <option value="MONTHLY">{t('filters.monthly')}</option>
                <option value="QUARTERLY">{t('filters.quarterly')}</option>
                <option value="YEARLY">{t('filters.yearly')}</option>
              </select>
            </div>

            {/* Status Filter */}
            <div>
              <label className="block text-sm font-medium mb-2">{t('filters.status')}</label>
              <select
                className="w-full px-3 py-2 bg-background border border-border rounded-md"
                value={filters.isActive === undefined ? 'all' : filters.isActive ? 'active' : 'paused'}
                onChange={(e) =>
                  handleFilterChange('isActive', e.target.value === 'all' ? undefined : e.target.value === 'active')
                }
              >
                <option value="all">{t('filters.allStatus')}</option>
                <option value="active">{t('filters.activeOnly')}</option>
                <option value="paused">{t('filters.pausedOnly')}</option>
              </select>
            </div>
          </div>

          <div className="mt-4 flex justify-end">
            <Button variant="ghost" size="sm" onClick={clearFilters}>
              {t('filters.clearFilters')}
            </Button>
          </div>
        </div>
      )}

      {/* Loading State */}
      {isLoading && (
        <div className="space-y-4">
          {[...Array(3)].map((_, i) => (
            <LoadingSkeleton key={i} className="h-32" />
          ))}
        </div>
      )}

      {/* Recurring Transactions List */}
      {!isLoading && recurringTransactions.length === 0 ? (
        isFiltered ? (
          <EmptyState
            icon={Calendar}
            title={t('empty.noResults')}
            description={t('empty.noResultsDescription')}
            action={{
              label: t('filters.clearFilters'),
              onClick: clearFilters,
            }}
          />
        ) : (
          <EmptyState
            icon={Calendar}
            title={t('empty.noRecurring')}
            description={t('empty.description')}
            action={{
              label: t('empty.addFirstCta'),
              onClick: handleCreate,
            }}
          />
        )
      ) : (
        <>
          <div className="space-y-4">
            {recurringTransactions.map((recurringTransaction) => (
              <RecurringTransactionCard
                key={recurringTransaction.id}
                recurringTransaction={recurringTransaction}
                onEdit={handleEdit}
                onDelete={handleDelete}
                onPause={handlePause}
                onResume={handleResume}
                onViewDetail={handleViewDetail}
                isPausing={pauseMutation.isPending}
                isResuming={resumeMutation.isPending}
              />
            ))}
          </div>

          {/* Pagination Controls */}
          {recurringTransactionsPage && recurringTransactionsPage.totalPages > 1 && (
            <Pagination
              currentPage={recurringTransactionsPage.number}
              totalPages={recurringTransactionsPage.totalPages}
              pageSize={recurringTransactionsPage.size}
              totalElements={recurringTransactionsPage.totalElements}
              onPageChange={handlePageChange}
              onPageSizeChange={handlePageSizeChange}
            />
          )}
        </>
      )}

      {/* Form Dialog */}
      <Dialog open={isFormOpen} onOpenChange={setIsFormOpen}>
        <DialogContent className="max-w-2xl max-h-[90vh] overflow-y-auto">
          <DialogHeader>
            <DialogTitle>
              {editingTransaction ? t('dialogs.editTitle') : t('dialogs.createTitle')}
            </DialogTitle>
          </DialogHeader>
          <RecurringTransactionForm
            recurringTransaction={editingTransaction}
            accounts={accounts}
            categories={categories}
            onSubmit={handleFormSubmit}
            onCancel={handleFormCancel}
            isSubmitting={createMutation.isPending || updateMutation.isPending}
            error={formError}
          />
        </DialogContent>
      </Dialog>

      {/* Delete Confirmation Dialog */}
      <ConfirmationDialog
        open={!!deletingTransaction}
        onOpenChange={(open) => !open && setDeletingTransaction(null)}
        title={t('dialogs.delete.title')}
        description={t('dialogs.delete.description', { description: deletingTransaction?.description })}
        confirmText={t('dialogs.delete.confirmText')}
        onConfirm={handleConfirmDelete}
        variant="danger"
        loading={deleteMutation.isPending}
      />

      {/* Recurring Transaction Detail Modal */}
      {detailTransaction && (
        <RecurringTransactionDetailModal
          recurringTransaction={detailTransaction}
          onClose={() => setDetailTransaction(null)}
          onEdit={(rt) => handleEdit(rt)}
        />
      )}
    </div>
  );
}
