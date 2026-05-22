/**
 * TransactionsPage Component
 * Task 3.2.13: Create TransactionsPage component
 * Task 4.4.6: Document title management
 * 
 * Main page for viewing and managing transactions with filters and pagination
 */
import { useEffect, useState } from 'react';
import { useLocation, useNavigate, useSearchParams } from 'react-router';
import { Plus, Filter } from 'lucide-react';
import { useTranslation } from 'react-i18next';
import { Button } from '@/components/ui/Button';
import { Dialog, DialogContent, DialogHeader, DialogTitle } from '@/components/ui/Dialog';
import { Pagination } from '@/components/ui/Pagination';
import { PageHeader } from '@/components/layout/PageHeader';
import { EmptyState } from '@/components/layout/EmptyState';
import { LoadingSkeleton } from '@/components/LoadingComponents';
import { TransactionList } from '@/components/transactions/TransactionList';
import { TransactionForm } from '@/components/transactions/TransactionForm';
import { TransactionFilters } from '@/components/transactions/TransactionFilters';
import { TransactionDetailModal } from '@/components/transactions/TransactionDetailModal';
import { ConfirmationDialog } from '@/components/ConfirmationDialog';
import { useDocumentTitle } from '@/hooks/useDocumentTitle';
import {
  useTransactions,
  useCreateTransaction,
  useCreateTransfer,
  useUpdateTransaction,
  useUpdateTransfer,
  useDeleteTransaction,
  useCategories,
} from '@/hooks/useTransactions';
import { useAccounts } from '@/hooks/useAccounts';
import type { Transaction, TransactionRequest, TransactionFilters as Filters } from '@/types/transaction';

export default function TransactionsPage() {
  const { t } = useTranslation('transactions');
  useDocumentTitle(t('title'));
  const location = useLocation();
  const navigate = useNavigate();
  const [searchParams] = useSearchParams();
  const highlightId = searchParams.get('highlight') ? parseInt(searchParams.get('highlight')!) : null;
  const categoryId = searchParams.get('category') ? parseInt(searchParams.get('category')!) : null;
  const noCategoryParam = searchParams.get('noCategory') === '1';
  const noPayeeParam = searchParams.get('noPayee') === '1';

  const [filters, setFilters] = useState<Filters>({
    page: 0,
    size: 20,
    categoryId: categoryId || undefined,
    noCategory: noCategoryParam || undefined,
    noPayee: noPayeeParam || undefined,
  });
  const [showFilters, setShowFilters] = useState(noCategoryParam || noPayeeParam);
  const [isFormOpen, setIsFormOpen] = useState(false);
  const [editingTransaction, setEditingTransaction] = useState<Transaction | null>(null);
  const [deletingTransaction, setDeletingTransaction] = useState<Transaction | null>(null);
  const [detailTransaction, setDetailTransaction] = useState<Transaction | null>(null);
  const { data: transactionsPage, isLoading, error } = useTransactions(filters);
  const { data: accounts = [] } = useAccounts();
  const { data: categories = [] } = useCategories();
  const createTransaction = useCreateTransaction();
  const createTransfer = useCreateTransfer();
  const updateTransaction = useUpdateTransaction();
  const updateTransfer = useUpdateTransfer();
  const deleteTransaction = useDeleteTransaction();

  const transactions = transactionsPage?.content || [];

  useEffect(() => {
    if ((location.state as { openForm?: boolean } | null)?.openForm) {
      setIsFormOpen(true);
      navigate(location.pathname, { replace: true, state: null });
    }
  }, [location.pathname, location.state, navigate]);

  // Handle category from query param after categories are loaded
  useEffect(() => {
    if (categoryId && filters.categoryId !== categoryId) {
      setFilters(prev => ({ ...prev, categoryId, page: 0 }));
      setShowFilters(true);
    } else if (categoryId && !showFilters) {
      setShowFilters(true);
    }
  }, [categoryId, filters.categoryId, showFilters]);

  const handleCreate = () => {
    setEditingTransaction(null);
    setIsFormOpen(true);
  };

  const handleEdit = (transaction: Transaction) => {
    setEditingTransaction(transaction);
    setIsFormOpen(true);
  };

  const handleDelete = (transaction: Transaction) => {
    setDeletingTransaction(transaction);
  };

  const handleFormSubmit = async (data: TransactionRequest) => {
    try {
      if (editingTransaction) {
        // Check if this is a transfer transaction
        if (editingTransaction.transferId) {
          // Update transfer atomically using the new endpoint
          await updateTransfer.mutateAsync({
            transferId: editingTransaction.transferId,
            data: {
              fromAccountId: data.accountId,
              toAccountId: data.toAccountId!,
              amount: data.amount,
              currency: data.currency,
              date: data.date,
              description: data.description,
              notes: data.notes,
              payee: data.payee,
              tags: data.tags,
              isReconciled: editingTransaction.isReconciled,
              paymentMethod: data.paymentMethod
            },
          });
        } else {
          // Update regular transaction
          await updateTransaction.mutateAsync({ id: editingTransaction.id, data });
        }
      } else {
        // Create new transaction (or transfer)
        if (data.type === 'TRANSFER') {
          await createTransfer.mutateAsync(data);
        } else {
          await createTransaction.mutateAsync(data);
        }
      }
      setIsFormOpen(false);
      setEditingTransaction(null);
    } catch (error) {
      console.error('Failed to save transaction:', error);
    }
  };

  const handleConfirmDelete = async () => {
    if (!deletingTransaction) return;

    try {
      await deleteTransaction.mutateAsync(deletingTransaction.id);
      setDeletingTransaction(null);
    } catch (error) {
      console.error('Failed to delete transaction:', error);
    }
  };

  const handleFormCancel = () => {
    setIsFormOpen(false);
    setEditingTransaction(null);
  };

  const handleFiltersChange = (newFilters: Filters) => {
    setFilters({ ...newFilters, page: 0, size: filters.size || 20 });
  };

  const handlePageChange = (page: number) => {
    setFilters({ ...filters, page });
  };

  const handlePageSizeChange = (size: number) => {
    setFilters({ ...filters, page: 0, size });
  };

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
        <div className="flex gap-2 shrink-0">
          <Button variant="ghost" onClick={() => setShowFilters(!showFilters)}>
            <Filter className="h-4 w-4 mr-2" />
            {t('filters')}
          </Button>
          <Button variant="primary" onClick={handleCreate}>
            <Plus className="h-4 w-4 mr-2" />
            {t('addTransaction')}
          </Button>
        </div>
      </div>

      {/* Filters */}
      {showFilters && (
        <div className="mb-6">
          <TransactionFilters
            filters={filters}
            onFiltersChange={handleFiltersChange}
          />
        </div>
      )}

      {/* Loading State */}
      {isLoading && (
        <div className="space-y-2">
          {[...Array(10)].map((_, i) => (
            <LoadingSkeleton key={i} className="h-20" />
          ))}
        </div>
      )}

      {/* Empty State */}
      {!isLoading && transactions.length === 0 && (
        <EmptyState
          title={t('empty.noResults')}
          description={
            filters.keyword
              ? t('empty.noKeywordMatch')
              : Object.entries(filters).some(
                  ([k, v]) =>
                    !['page', 'size', 'sort'].includes(k) &&
                    v !== undefined &&
                    v !== null &&
                    v !== '',
                )
              ? t('empty.noMatch')
              : t('empty.noTransactions')
          }
          action={
            !Object.entries(filters).some(
              ([k, v]) =>
                !['page', 'size', 'sort'].includes(k) &&
                v !== undefined &&
                v !== null &&
                v !== '',
            )
              ? {
                label: t('empty.addCta'),
                onClick: handleCreate,
              }
              : undefined
          }
        />
      )}

      {/* Transactions List */}
      {!isLoading && transactions.length > 0 && (
        <>
          <TransactionList
            transactions={transactions}
            onEdit={handleEdit}
            onDelete={handleDelete}
            highlightedId={highlightId}
            sortDirection={filters.sort?.endsWith(',asc') ? 'asc' : 'desc'}
            onViewDetail={(tx) => setDetailTransaction(tx)}
          />

          {/* Pagination Controls */}
          {transactionsPage && (
            <Pagination
              currentPage={transactionsPage.number}
              totalPages={transactionsPage.totalPages}
              pageSize={transactionsPage.size}
              totalElements={transactionsPage.totalElements}
              onPageChange={handlePageChange}
              onPageSizeChange={handlePageSizeChange}
            />
          )}
        </>
      )}

      {/* Create/Edit Dialog */}
      <Dialog open={isFormOpen} onOpenChange={setIsFormOpen}>
        <DialogContent className="sm:max-w-150 max-h-[90vh] overflow-y-auto">
          <DialogHeader>
            <DialogTitle>
              {editingTransaction ? t('dialogs.editTitle') : t('dialogs.createTitle')}
            </DialogTitle>
          </DialogHeader>
          <TransactionForm
            transaction={editingTransaction || undefined}
            accounts={accounts}
            categories={categories}
            onSubmit={handleFormSubmit}
            onCancel={handleFormCancel}
            isLoading={
              createTransaction.isPending ||
              createTransfer.isPending ||
              updateTransaction.isPending ||
              updateTransfer.isPending
            }
          />
        </DialogContent>
      </Dialog>

      {/* Delete Confirmation Dialog */}
      <ConfirmationDialog
        open={!!deletingTransaction}
        onOpenChange={(open) => !open && setDeletingTransaction(null)}
        onConfirm={handleConfirmDelete}
        title={t('dialogs.delete.title')}
        description={t('dialogs.delete.description')}
        confirmText={t('dialogs.delete.confirmText')}
        cancelText={t('form.cancel')}
        variant="danger"
        loading={deleteTransaction.isPending}
      />

      {/* Transaction Detail Modal */}
      {detailTransaction && (
        <TransactionDetailModal
          transaction={detailTransaction}
          onClose={() => setDetailTransaction(null)}
          onEdit={(tx) => {
            setDetailTransaction(null);
            handleEdit(tx);
          }}
        />
      )}
    </div>
  );
}
