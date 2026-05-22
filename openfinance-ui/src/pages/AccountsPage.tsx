/**
 * AccountsPage Component
 * Task 2.2.9: Create AccountsPage component with list, cards, and add button
 * Task 4.4.6: Document title management
 * BUG-019-001: Account deletion prevention and filtering
 * 
 * Main page for managing user accounts with filters and pagination
 */
import { useState, useMemo, useEffect } from 'react';
import { useSearchParams } from 'react-router';
import { Plus, Filter } from 'lucide-react';
import { useTranslation } from 'react-i18next';
import { Button } from '@/components/ui/Button';
import { Dialog, DialogContent, DialogHeader, DialogTitle } from '@/components/ui/Dialog';
import { Pagination } from '@/components/ui/Pagination';
import { PageHeader } from '@/components/layout/PageHeader';
import { EmptyState } from '@/components/layout/EmptyState';
import { LoadingSkeleton } from '@/components/LoadingComponents';
import { AccountCard } from '@/components/accounts/AccountCard';
import { AccountForm } from '@/components/accounts/AccountForm';
import { AccountFilters } from '@/components/accounts/AccountFilters';
import { AccountDetailModal } from '@/components/accounts/AccountDetailModal';
import { ConfirmationDialog } from '@/components/ConfirmationDialog';
import { useDocumentTitle } from '@/hooks/useDocumentTitle';
import { useFormatCurrency } from '@/hooks/useFormatCurrency';
import { PrivateAmount } from '@/components/ui/PrivateAmount';
import { ConvertedAmount } from '@/components/ui/ConvertedAmount';
import {
  useAccountsSearch,
  useCreateAccount,
  useUpdateAccount,
  useCloseAccount,
  useReopenAccount,
  usePermanentDeleteAccount,
} from '@/hooks/useAccounts';
import type { Account, AccountRequest, AccountFilters as Filters } from '@/types/account';

/** Check if any meaningful filter (beyond pagination/sort) is active */
function hasActiveFilters(filters: Filters): boolean {
  return !!(
    filters.keyword ||
    filters.type ||
    filters.currency ||
    filters.institution ||
    filters.isActive !== undefined ||
    filters.balanceMin !== undefined ||
    filters.balanceMax !== undefined ||
    filters.lowBalance
  );
}

const DEFAULT_PAGE_SIZE = 20;

export default function AccountsPage() {
  const { t } = useTranslation('accounts');
  useDocumentTitle(t('title'));
  const { format: formatCurrency } = useFormatCurrency();
  const [searchParams, setSearchParams] = useSearchParams();
  const lowBalanceParam = searchParams.get('lowBalance') === '1';
  const highlightId = searchParams.get('highlight') ? parseInt(searchParams.get('highlight')!) : null;

  const [filters, setFilters] = useState<Filters>({
    page: 0,
    size: DEFAULT_PAGE_SIZE,
    sort: 'name,asc',
    lowBalance: lowBalanceParam || undefined,
  });
  const [showFilters, setShowFilters] = useState(lowBalanceParam);
  const [isFormOpen, setIsFormOpen] = useState(false);
  const [editingAccount, setEditingAccount] = useState<Account | null>(null);
  const [detailAccountId, setDetailAccountId] = useState<number | null>(highlightId);

  // Auto-open modal when highlight param is present
  useEffect(() => {
    if (highlightId) {
      setDetailAccountId(highlightId);
    }
  }, [highlightId]);

  // Close filter panel on Escape key
  useEffect(() => {
    const handler = (e: KeyboardEvent) => {
      if (e.key === 'Escape' && showFilters) setShowFilters(false);
    };
    window.addEventListener('keydown', handler);
    return () => window.removeEventListener('keydown', handler);
  }, [showFilters]);


  // Dialog state for close/reopen/delete actions
  const [actionAccount, setActionAccount] = useState<Account | null>(null);
  const [actionType, setActionType] = useState<'close' | 'reopen' | 'permanent-delete' | null>(null);
  const [actionError, setActionError] = useState<string | null>(null);

  const { data: accountsPage, isLoading: isSearchLoading, error } = useAccountsSearch(filters);
  // Fetch all accounts (unfiltered) for global totals in the summary card
  const { data: allAccountsPage } = useAccountsSearch({ page: 0, size: 10000, sort: 'name,asc' });
  // Fetch all filtered accounts for correct totals
  const { data: allFilteredAccountsPage, isLoading: isFilteredLoading } = useAccountsSearch({ ...filters, page: 0, size: 10000 });
  const createAccount = useCreateAccount();
  const updateAccount = useUpdateAccount();
  const closeAccount = useCloseAccount();
  const reopenAccount = useReopenAccount();
  const permanentDeleteAccount = usePermanentDeleteAccount();

  const accounts = accountsPage?.content || [];
  const allAccounts = allAccountsPage?.content || [];
  const allFilteredAccounts = allFilteredAccountsPage?.content || [];

  const isLoading = isSearchLoading || isFilteredLoading;

  const handleFiltersChange = (newFilters: Filters) => {
    setFilters({ ...newFilters, page: 0, size: filters.size || DEFAULT_PAGE_SIZE });
  };

  const handlePageChange = (page: number) => {
    setFilters({ ...filters, page });
  };

  const handlePageSizeChange = (size: number) => {
    setFilters({ ...filters, page: 0, size });
  };

  const handleCreate = () => {
    setEditingAccount(null);
    setIsFormOpen(true);
  };

  const handleEdit = (account: Account) => {
    setEditingAccount(account);
    setIsFormOpen(true);
  };

  const handleDelete = (account: Account) => {
    setActionAccount(account);
    setActionType('permanent-delete');
    setActionError(null);
  };

  const handleClose = (account: Account) => {
    setActionAccount(account);
    setActionType('close');
    setActionError(null);
  };

  const handleReopen = (account: Account) => {
    setActionAccount(account);
    setActionType('reopen');
    setActionError(null);
  };

  const handleFormSubmit = async (data: AccountRequest) => {
    try {
      if (editingAccount) {
        await updateAccount.mutateAsync({ id: editingAccount.id, data });
      } else {
        await createAccount.mutateAsync(data);
      }
      setIsFormOpen(false);
      setEditingAccount(null);
    } catch (error) {
      console.error('Failed to save account:', error);
    }
  };

  const handleFormCancel = () => {
    setIsFormOpen(false);
    setEditingAccount(null);
  };

  // Handle confirm for the action dialog
  const handleConfirmAction = async () => {
    if (!actionAccount || !actionType) return;

    try {
      setActionError(null);

      switch (actionType) {
        case 'close':
          await closeAccount.mutateAsync(actionAccount.id);
          break;
        case 'reopen':
          await reopenAccount.mutateAsync(actionAccount.id);
          break;
        case 'permanent-delete':
          await permanentDeleteAccount.mutateAsync(actionAccount.id);
          break;
      }

      setActionAccount(null);
      setActionType(null);
    } catch (error: any) {
      console.error(`Failed to ${actionType} account:`, error);
      const errorMessage = error?.response?.data?.message ||
        error?.message ||
        `Failed to ${actionType === 'close' ? 'close' : actionType === 'reopen' ? 'reopen' : 'delete'} account. Please try again.`;
      setActionError(errorMessage);
    }
  };

  // Close action dialog
  const handleCloseActionDialog = () => {
    setActionAccount(null);
    setActionType(null);
    setActionError(null);
  };

  // Check if error is a decryption failure (wrong master password)
  const isDecryptionError = error &&
    (error as any).response?.status === 400 &&
    (error as any).response?.data?.message?.includes('Decryption failed');

  // Calculate total balances grouped by currency — all accounts (global) and current filtered page
  // NOTE: These useMemo hooks MUST be declared before any early return to satisfy React's Rules of Hooks

  /**
   * Per-currency summary: native total + converted (base-currency) total.
   * Used so ConvertedAmount can show base vs native according to user display preference.
   */
  const allTotalsByCurrency = useMemo(() => allAccounts.reduce((acc, account) => {
    const currency = account.currency;
    if (!acc[currency]) {
      acc[currency] = {
        nativeTotal: 0,
        baseCurrencyTotal: 0,
        baseCurrency: account.baseCurrency,
        hasConversion: false,
      };
    }
    acc[currency].nativeTotal += account.balance;
    if (account.isConverted && account.balanceInBaseCurrency != null) {
      acc[currency].baseCurrencyTotal += account.balanceInBaseCurrency;
      acc[currency].hasConversion = true;
      if (account.baseCurrency) acc[currency].baseCurrency = account.baseCurrency;
    }
    return acc;
  }, {} as Record<string, { nativeTotal: number; baseCurrencyTotal: number; baseCurrency?: string; hasConversion: boolean }>), [allAccounts]);

  /**
   * Filtered-page per-currency native totals (shown as secondary when filters are active).
   * Now calculated from allFilteredAccounts instead of just the current page.
   */
  const filteredTotalsByCurrency = useMemo(() => allFilteredAccounts.reduce((acc, account) => {
    const currency = account.currency;
    if (!acc[currency]) {
      acc[currency] = {
        nativeTotal: 0,
        baseCurrencyTotal: 0,
        baseCurrency: account.baseCurrency,
        hasConversion: false,
      };
    }
    acc[currency].nativeTotal += account.balance;
    if (account.isConverted && account.balanceInBaseCurrency != null) {
      acc[currency].baseCurrencyTotal += account.balanceInBaseCurrency;
      acc[currency].hasConversion = true;
      if (account.baseCurrency) acc[currency].baseCurrency = account.baseCurrency;
    }
    return acc;
  }, {} as Record<string, { nativeTotal: number; baseCurrencyTotal: number; baseCurrency?: string; hasConversion: boolean }>), [allFilteredAccounts]);

  const isFiltered = hasActiveFilters(filters);

  // Early return for error states — placed AFTER all hooks to satisfy React's Rules of Hooks
  if (error) {
    return (
      <div className="p-8">
        <PageHeader title={t('title')} />
        {isDecryptionError ? (
          <div className="mt-6 p-6 bg-error/10 border border-error/20 rounded-lg">
            <h3 className="text-lg font-semibold text-error mb-2">
              {t('decryptError.title')}
            </h3>
            <p className="text-text-secondary mb-4">
              {t('decryptError.description')}
            </p>
            <div className="flex gap-3">
              <Button
                variant="destructive"
                onClick={() => {
                  sessionStorage.clear();
                  localStorage.clear();
                  window.location.href = '/login';
                }}
              >
                {t('decryptError.logoutButton')}
              </Button>
            </div>
          </div>
        ) : (
          <div className="mt-6 p-4 bg-error/10 border border-error/20 rounded-lg text-error">
            {t('loadError')}
          </div>
        )}
      </div>
    );
  }

  // Determine dialog content based on action type
  const getDialogContent = () => {
    if (!actionAccount || !actionType) return null;

    const isLoading = closeAccount.isPending || reopenAccount.isPending || permanentDeleteAccount.isPending;

    switch (actionType) {
      case 'close':
        return {
          title: t('dialogs.close.title'),
          description: t('dialogs.close.description', { name: actionAccount.name }),
          confirmText: t('dialogs.close.confirmText'),
          variant: 'warning' as const,
          loading: isLoading,
        };
      case 'reopen':
        return {
          title: t('dialogs.reopen.title'),
          description: t('dialogs.reopen.description', { name: actionAccount.name }),
          confirmText: t('dialogs.reopen.confirmText'),
          variant: 'info' as const,
          loading: isLoading,
        };
      case 'permanent-delete':
        return {
          title: t('dialogs.delete.title'),
          description: t('dialogs.delete.description', { name: actionAccount.name }),
          confirmText: t('dialogs.delete.confirmText'),
          variant: 'danger' as const,
          loading: isLoading,
        };
      default:
        return null;
    }
  };

  const dialogContent = getDialogContent();

  return (
    <div className="p-8">
      {/* Header */}
      <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-4 mb-6">
        <PageHeader title={t('title')} description={t('description')} />
        <div className="flex items-center gap-3 shrink-0">
          {/* Filter Toggle Button */}
          <Button
            variant={showFilters ? 'primary' : 'outline'}
            onClick={() => setShowFilters(!showFilters)}
          >
            <Filter className="h-4 w-4 mr-2" />
            {t('common:filters')}
          </Button>
          <Button variant="primary" onClick={handleCreate}>
            <Plus className="h-4 w-4 mr-2" />
            {t('addAccount')}
          </Button>
        </div>
      </div>

      {/* Filters Panel */}
      {showFilters && (
        <div className="mb-6">
          <AccountFilters filters={filters} onFiltersChange={handleFiltersChange} />
        </div>
      )}

      {/* Total Balance Summary */}
      {!isLoading && allAccounts && allAccounts.length > 0 && (
        <div className="mb-6 p-6 rounded-lg bg-surface border border-border">
          <h3 className="text-lg font-semibold mb-4 text-text-primary">{t('totalBalance')}</h3>
          <div className="space-y-2">
            {Object.entries(allTotalsByCurrency).map(([currency, totals]) => (
              <div key={currency} className="flex items-center justify-between">
                <span className="text-text-secondary">{currency}:</span>
                <div className="text-right">
                  {/* Global total is always the primary figure */}
                  <ConvertedAmount
                    className="text-xl font-mono font-semibold text-text-primary"
                    amount={totals.nativeTotal}
                    currency={currency}
                    convertedAmount={totals.hasConversion ? totals.baseCurrencyTotal : undefined}
                    baseCurrency={totals.baseCurrency}
                    isConverted={totals.hasConversion}
                  />
                  {/* Show filtered subtotal as secondary when a filter is active */}
                  {isFiltered && filteredTotalsByCurrency[currency] && (
                    <div className="text-sm font-mono text-text-tertiary mt-0.5">
                      {t('filtered')}&nbsp;
                      <PrivateAmount inline>
                        {formatCurrency(filteredTotalsByCurrency[currency].nativeTotal, currency)}
                      </PrivateAmount>
                      {filteredTotalsByCurrency[currency].hasConversion && filteredTotalsByCurrency[currency].baseCurrencyTotal !== undefined && (
                        <span className="ml-1 text-xs text-text-tertiary/70">
                          ({formatCurrency(filteredTotalsByCurrency[currency].baseCurrencyTotal!, filteredTotalsByCurrency[currency].baseCurrency!)})
                        </span>
                      )}
                      {allFilteredAccountsPage && allAccounts && (
                        <span className="ml-1">
                          {t('filteredCount', { filtered: allFilteredAccountsPage.totalElements, total: allAccounts.length })}
                        </span>
                      )}
                    </div>
                  )}
                </div>
              </div>
            ))}
          </div>
        </div>
      )}

      {/* Loading State */}
      {isLoading && (
        <div className="grid grid-cols-1 lg:grid-cols-2 gap-4">
          {[...Array(6)].map((_, i) => (
            <LoadingSkeleton key={i} className="h-48" />
          ))}
        </div>
      )}

      {/* Empty State */}
      {!isLoading && accounts && accounts.length === 0 && (
        <EmptyState
          title={t('empty.noResults')}
          description={filters.keyword || filters.type || filters.currency || filters.institution
            ? t('empty.noMatch')
            : t('empty.noAccounts')}
          action={{
            label: t('addAccount'),
            onClick: handleCreate,
          }}
        />
      )}

      {/* Accounts Grid */}
      {!isLoading && accounts && accounts.length > 0 && (
        <>
          <div className="grid grid-cols-1 lg:grid-cols-2 gap-4 mb-6">
            {accounts.map((account) => (
              <AccountCard
                key={account.id}
                account={account}
                onEdit={handleEdit}
                onDelete={handleDelete}
                onClose={handleClose}
                onReopen={handleReopen}
                onViewDetail={(a) => setDetailAccountId(a.id)}
              />
            ))}
          </div>

          {/* Pagination */}
          {accountsPage && accountsPage.totalPages > 1 && (
            <Pagination
              currentPage={accountsPage.number}
              totalPages={accountsPage.totalPages}
              pageSize={accountsPage.size}
              totalElements={accountsPage.totalElements}
              onPageChange={handlePageChange}
              onPageSizeChange={handlePageSizeChange}
            />
          )}
        </>
      )}

      {/* Create/Edit Dialog */}
      <Dialog open={isFormOpen} onOpenChange={setIsFormOpen}>
        <DialogContent className="sm:max-w-[560px] max-h-[90vh] overflow-y-auto">
          <DialogHeader>
            <DialogTitle>
              {editingAccount ? t('dialogs.editTitle') : t('dialogs.createTitle')}
            </DialogTitle>
          </DialogHeader>
          <AccountForm
            account={editingAccount || undefined}
            onSubmit={handleFormSubmit}
            onCancel={handleFormCancel}
            isLoading={createAccount.isPending || updateAccount.isPending}
            existingAccountNames={allAccounts
              .filter((a) => a.id !== editingAccount?.id)
              .map((a) => a.name)}
          />
        </DialogContent>
      </Dialog>

      {/* Action Confirmation Dialog (Close/Reopen/Delete) */}
      {dialogContent && (
        <ConfirmationDialog
          open={!!actionAccount && !!actionType}
          onOpenChange={(open) => {
            if (!open) {
              handleCloseActionDialog();
            }
          }}
          onConfirm={actionError ? handleCloseActionDialog : handleConfirmAction}
          title={actionError ? t('dialogs.actionFailed') : dialogContent.title}
          description={
            actionError
              ? `${dialogContent.description}\n\nError: ${actionError}`
              : dialogContent.description
          }
          confirmText={actionError ? t('dialogs.actionFailed') : dialogContent.confirmText}
          variant={actionError ? 'warning' : dialogContent.variant}
          loading={dialogContent.loading}
        />
      )}

      {/* Account Detail Modal */}
      {detailAccountId !== null && (
        <AccountDetailModal
          accountId={detailAccountId}
          onClose={() => {
            setDetailAccountId(null);
            if (highlightId) {
              const newParams = new URLSearchParams(searchParams);
              newParams.delete('highlight');
              setSearchParams(newParams, { replace: true });
            }
          }}
        />
      )}
    </div>
  );
}
