import { useTranslation } from 'react-i18next';
import { isAxiosError } from 'axios';
import { TransactionForm } from '@/components/transactions/TransactionForm';
import { Button } from '@/components/ui/Button';
import { Dialog, DialogContent, DialogHeader, DialogTitle } from '@/components/ui/Dialog';
import { useAccounts } from '@/hooks/useAccounts';
import { useCategories, useCreateTransaction, useCreateTransfer } from '@/hooks/useTransactions';
import type { TransactionRequest } from '@/types/transaction';

export function CreateTransactionDialog({
  onClose,
  onCloseAutoFocus,
}: {
  onClose: () => void;
  onCloseAutoFocus?: () => void;
}) {
  const { t } = useTranslation('transactions');
  const accounts = useAccounts();
  const categories = useCategories();
  const createTransaction = useCreateTransaction();
  const createTransfer = useCreateTransfer();
  const isPending = createTransaction.isPending || createTransfer.isPending;
  const error = createTransaction.error || createTransfer.error;
  const errorMessage =
    isAxiosError(error) && typeof error.response?.data?.message === 'string'
      ? error.response.data.message
      : t('common:errors.saveFailed');

  const handleSubmit = (data: TransactionRequest) => {
    createTransaction.reset();
    createTransfer.reset();
    const mutation = data.type === 'TRANSFER' ? createTransfer : createTransaction;
    mutation.mutate(data, { onSuccess: onClose });
  };

  return (
    <Dialog open onOpenChange={open => !open && !isPending && onClose()}>
      <DialogContent
        className="sm:max-w-3xl max-h-[90vh] overflow-y-auto"
        onCloseAutoFocus={event => {
          if (onCloseAutoFocus) {
            event.preventDefault();
            onCloseAutoFocus();
          }
        }}
      >
        <DialogHeader>
          <DialogTitle>{t('dialogs.createTitle')}</DialogTitle>
        </DialogHeader>
        {accounts.isLoading || categories.isLoading ? (
          <p role="status" className="py-8 text-center text-text-secondary">
            {t('common:loading')}
          </p>
        ) : accounts.isError || categories.isError ? (
          <div className="space-y-3">
            <p role="alert" className="text-error">
              {t('common:errors.loadFailed')}
            </p>
            <Button
              variant="secondary"
              onClick={() => {
                void accounts.refetch();
                void categories.refetch();
              }}
            >
              {t('common:errorBoundary.tryAgain')}
            </Button>
          </div>
        ) : (
          <>
            {error && (
              <p role="alert" className="text-sm text-error">
                {errorMessage}
              </p>
            )}
            <TransactionForm
              accounts={accounts.data ?? []}
              categories={categories.data ?? []}
              onSubmit={handleSubmit}
              onCancel={onClose}
              isLoading={isPending}
            />
          </>
        )}
      </DialogContent>
    </Dialog>
  );
}
