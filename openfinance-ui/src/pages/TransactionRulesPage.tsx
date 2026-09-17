/**
 * TransactionRulesPage
 *
 * Main page for managing transaction import rules. Combines the RuleList
 * table with RuleForm dialog for creating and editing rules. Also provides
 * a delete confirmation dialog.
 *
 * Requirement: REQ-TR-6.1 through REQ-TR-6.6
 */
import { useState } from 'react';
import { Plus } from 'lucide-react';
import { useTranslation } from 'react-i18next';
import { Button } from '@/components/ui/Button';
import { PageHeader } from '@/components/layout/PageHeader';
import { LoadingSkeleton } from '@/components/LoadingComponents';
import { ConfirmationDialog } from '@/components/ConfirmationDialog';
import { RuleList } from '@/components/transaction-rules/RuleList';
import { RuleForm } from '@/components/transaction-rules/RuleForm';
import { useDocumentTitle } from '@/hooks/useDocumentTitle';
import {
  useTransactionRules,
  useCreateRule,
  useUpdateRule,
  useDeleteRule,
  useToggleRule,
} from '@/hooks/useTransactionRules';
import type { TransactionRule, TransactionRuleRequest } from '@/types/transactionRules';

/**
 * Page component for viewing and managing transaction rules.
 * Requirement: REQ-TR-6.1 through REQ-TR-6.6
 */
export default function TransactionRulesPage() {
  const { t } = useTranslation('rules');
  useDocumentTitle(t('title'));

  // ---------------------------------------------------------------------------
  // Remote state
  // ---------------------------------------------------------------------------
  const { data: rules = [], isLoading, error } = useTransactionRules();
  const createRule = useCreateRule();
  const updateRule = useUpdateRule();
  const deleteRule = useDeleteRule();
  const toggleRule = useToggleRule();

  // ---------------------------------------------------------------------------
  // Local UI state
  // ---------------------------------------------------------------------------
  const [isFormOpen, setIsFormOpen] = useState(false);
  const [editingRule, setEditingRule] = useState<TransactionRule | null>(null);
  const [deletingRule, setDeletingRule] = useState<TransactionRule | null>(null);
  const [submitError, setSubmitError] = useState<string | null>(null);

  const isMutating =
    createRule.isPending || updateRule.isPending || deleteRule.isPending || toggleRule.isPending;

  // ---------------------------------------------------------------------------
  // Handlers
  // ---------------------------------------------------------------------------

  const handleCreate = () => {
    setEditingRule(null);
    setIsFormOpen(true);
  };

  const handleEdit = (rule: TransactionRule) => {
    setEditingRule(rule);
    setIsFormOpen(true);
  };

  const handleFormSubmit = async (data: TransactionRuleRequest) => {
    setSubmitError(null);
    try {
      if (editingRule) {
        await updateRule.mutateAsync({ id: editingRule.id, data });
      } else {
        await createRule.mutateAsync(data);
      }
      setIsFormOpen(false);
      setEditingRule(null);
    } catch (err: unknown) {
      console.error('Failed to save rule:', err);
      const message =
        (err as { response?: { data?: { message?: string } } })?.response?.data?.message ||
        t('form.errors.saveFailed');
      setSubmitError(message);
    }
  };

  const handleDeleteRequest = (rule: TransactionRule) => {
    setDeletingRule(rule);
  };

  const handleDeleteConfirm = async () => {
    if (!deletingRule) return;
    try {
      await deleteRule.mutateAsync(deletingRule.id);
      setDeletingRule(null);
    } catch (err) {
      console.error('Failed to delete rule:', err);
    }
  };

  const handleToggle = async (rule: TransactionRule) => {
    try {
      await toggleRule.mutateAsync(rule.id);
    } catch (err) {
      console.error('Failed to toggle rule:', err);
    }
  };

  // ---------------------------------------------------------------------------
  // Render
  // ---------------------------------------------------------------------------

  return (
    <div className="space-y-6">
      <PageHeader
        title={t('title')}
        description={t('description')}
        actions={
          <Button onClick={handleCreate}>
            <Plus size={20} className="mr-2" />
            {t('addRule')}
          </Button>
        }
      />

      {/* Stats bar */}
      <div className="grid grid-cols-1 md:grid-cols-3 gap-4">
        <div className="bg-surface rounded-xl p-4 border border-border">
          <div className="text-2xl font-bold text-text-primary">{rules.length}</div>
          <div className="text-sm text-text-secondary">{t('summary.totalRules')}</div>
        </div>
        <div className="bg-surface rounded-xl p-4 border border-border">
          <div className="text-2xl font-bold text-green-600">
            {rules.filter(r => r.isEnabled).length}
          </div>
          <div className="text-sm text-text-secondary">{t('summary.activeRules')}</div>
        </div>
        <div className="bg-surface rounded-xl p-4 border border-border">
          <div className="text-2xl font-bold text-text-secondary">
            {rules.filter(r => !r.isEnabled).length}
          </div>
          <div className="text-sm text-text-secondary">{t('summary.inactiveRules')}</div>
        </div>
      </div>

      {/* Rule list */}
      {isLoading ? (
        <LoadingSkeleton className="h-64" />
      ) : error ? (
        <div className="bg-surface rounded-xl border border-border p-8 text-center">
          <p className="text-text-secondary">{t('loadError')}</p>
        </div>
      ) : (
        <RuleList
          rules={rules}
          onEdit={handleEdit}
          onDelete={handleDeleteRequest}
          onToggle={handleToggle}
          onCreateFirst={handleCreate}
          isMutating={isMutating}
        />
      )}

      {/* Create / Edit dialog */}
      <RuleForm
        open={isFormOpen}
        onOpenChange={open => {
          setIsFormOpen(open);
          if (!open) {
            setEditingRule(null);
            setSubmitError(null);
          }
        }}
        rule={editingRule}
        onSubmit={handleFormSubmit}
        isLoading={createRule.isPending || updateRule.isPending}
        submitError={submitError}
        existingRules={rules}
      />

      {/* Delete confirmation */}
      <ConfirmationDialog
        open={!!deletingRule}
        onOpenChange={open => !open && setDeletingRule(null)}
        title={t('dialogs.delete.title')}
        description={t('dialogs.delete.description', { name: deletingRule?.name })}
        confirmText={t('dialogs.delete.confirmText')}
        onConfirm={handleDeleteConfirm}
        variant="danger"
      />
    </div>
  );
}
