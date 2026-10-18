import { useState } from 'react';
import { useTranslation } from 'react-i18next';
import { ConfirmationDialog } from '@/components/ConfirmationDialog';
import { Button } from '@/components/ui/Button';
import { useCategoryDeletionImpact, useDeleteCategory } from '@/hooks/useTransactions';

interface CategoryDeleteDialogProps {
  category: { id: number; name: string } | null;
  onClose: () => void;
}

export function CategoryDeleteDialog({ category, onClose }: CategoryDeleteDialogProps) {
  const { t } = useTranslation('categories');
  const { data: impact, isFetching, isError, refetch } = useCategoryDeletionImpact(category?.id);
  const deletion = useDeleteCategory();
  const [failure, setFailure] = useState<{ categoryId: number; message: string } | null>(null);
  const error = failure?.categoryId === category?.id ? failure?.message : null;

  const handleConfirm = async (): Promise<void> => {
    if (!category || !impact?.deletable || isFetching || isError) return;
    setFailure(null);
    try {
      await deletion.mutateAsync(category.id);
      onClose();
    } catch (cause) {
      const response = cause as { response?: { data?: { message?: string } } };
      setFailure({
        categoryId: category.id,
        message: response.response?.data?.message ?? t('dialogs.delete.failed'),
      });
      void refetch();
    }
  };

  const references = impact
    ? ([
        ['transactions', impact.transactionCount],
        ['splits', impact.splitCount],
        ['recurring', impact.recurringCount],
        ['payees', impact.payeeCount],
      ] as const)
    : [];

  return (
    <ConfirmationDialog
      open={category !== null}
      onOpenChange={open => {
        if (!open) {
          setFailure(null);
          onClose();
        }
      }}
      title={t('dialogs.delete.title')}
      description={t('dialogs.delete.description', { name: category?.name })}
      confirmText={t('dialogs.delete.confirmText')}
      onConfirm={handleConfirm}
      variant="danger"
      loading={deletion.isPending}
      confirmDisabled={!impact?.deletable || isFetching || isError}
    >
      {isFetching && <p role="status">{t('dialogs.delete.checking')}</p>}
      {isError && (
        <div role="alert">
          <p>{t('dialogs.delete.checkFailed')}</p>
          <Button variant="ghost" onClick={() => void refetch()}>
            {t('dialogs.delete.retry')}
          </Button>
        </div>
      )}
      {impact && !isFetching && !isError && (
        <div className="space-y-3 text-sm">
          {impact.budgetCount > 0 && (
            <p className="rounded-md bg-warning/10 p-3 text-warning" role="note">
              {t('dialogs.delete.budgets', { count: impact.budgetCount })}
            </p>
          )}
          {!impact.deletable && (
            <div role="alert" className="space-y-2 text-error">
              <p>{t('dialogs.delete.blocked')}</p>
              <ul className="list-disc space-y-1 pl-5">
                {impact.system && <li>{t('dialogs.delete.system')}</li>}
                {impact.hasChildren && <li>{t('dialogs.delete.children')}</li>}
                {references
                  .filter(([, count]) => count > 0)
                  .map(([kind, count]) => (
                    <li key={kind}>{t(`dialogs.delete.${kind}`, { count })}</li>
                  ))}
              </ul>
            </div>
          )}
        </div>
      )}
      {error && (
        <p role="alert" className="text-sm text-error">
          {error}
        </p>
      )}
    </ConfirmationDialog>
  );
}
