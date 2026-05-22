/**
 * SplitTransactionForm Component
 * Task 12 (REQ-SPL-3.2, REQ-SPL-3.3, REQ-SPL-3.4, REQ-SPL-3.5, REQ-SPL-3.7, REQ-SPL-3.8)
 *
 * Renders a list of split entries for a transaction, allowing the user to allocate
 * the parent transaction's amount across multiple categories.
 */
import { Plus, Trash2, AlertCircle } from 'lucide-react';
import { Trans, useTranslation } from 'react-i18next';
import { Button } from '@/components/ui/Button';
import { Input } from '@/components/ui/Input';
import { CategorySelect } from '@/components/ui/CategorySelect';
import { useFormatCurrency } from '@/hooks/useFormatCurrency';
import type { TransactionSplitRequest } from '@/types/transaction';
import type { TransactionType } from '@/types/transaction';

/** ±0.01 tolerance when checking split sum vs. total amount (REQ-SPL-1.2) */
const SPLIT_TOLERANCE = 0.01;

interface SplitTransactionFormProps {
  /** The parent transaction total amount */
  totalAmount: number;
  /** ISO 4217 currency code, e.g. "EUR" */
  currency: string;
  /** INCOME or EXPENSE — used to filter category options */
  transactionType: TransactionType;
  /** Current list of split lines managed by the parent form */
  splits: TransactionSplitRequest[];
  /** Called whenever the split list changes */
  onChange: (splits: TransactionSplitRequest[]) => void;
}

/**
 * Returns true when the absolute difference between two numbers is within the
 * allowed split tolerance (REQ-SPL-1.2: ±0.01).
 */
function withinTolerance(a: number, b: number): boolean {
  return Math.abs(a - b) <= SPLIT_TOLERANCE;
}

/**
 * SplitTransactionForm renders an editable list of split entries.
 * Each entry has a category selector, an amount input, an optional description
 * field, and a remove button.  A running total and validation banner are shown
 * so the user always knows how much is left to allocate.
 */
export function SplitTransactionForm({
  totalAmount,
  currency,
  transactionType,
  splits,
  onChange,
}: SplitTransactionFormProps) {
  const { t } = useTranslation('transactions');
  const { format: formatCurrency } = useFormatCurrency();
  // REQ-SPL-3.3: computed running total
  const splitTotal = splits.reduce((sum, s) => sum + (Number(s.amount) || 0), 0);
  const remaining = totalAmount - splitTotal;
  const isValid = withinTolerance(splitTotal, totalAmount);

  // REQ-SPL-3.7: add a new blank split line
  const handleAddSplit = () => {
    onChange([
      ...splits,
      {
        categoryId: undefined,
        amount: remaining > 0 ? Math.round(remaining * 100) / 100 : 0,
        description: undefined,
      },
    ]);
  };

  // REQ-SPL-3.8: remove a split line by index
  const handleRemove = (index: number) => {
    onChange(splits.filter((_, i) => i !== index));
  };

  const handleChangeField = <K extends keyof TransactionSplitRequest>(
    index: number,
    field: K,
    value: TransactionSplitRequest[K],
  ) => {
    const updated = splits.map((s, i) => (i === index ? { ...s, [field]: value } : s));
    onChange(updated);
  };

  return (
    <div className="space-y-3">
      {/* Split entry rows */}
      {splits.map((split, index) => (
        <div
          key={index}
          className="grid grid-cols-[1fr_auto_auto] gap-2 items-start p-3 rounded-lg bg-surface border border-border"
        >
          {/* Category selector — full row width on small screens */}
          <div className="col-span-3">
            <CategorySelect
              value={split.categoryId}
              onValueChange={(value) => handleChangeField(index, 'categoryId', value)}
              placeholder={t('splitForm.categoryPlaceholder')}
              type={transactionType}
              allowNone={true}
              allowCreateInline
              inferredType={transactionType ?? 'EXPENSE'}
            />
          </div>

          {/* Amount input */}
          <div>
            <Input
              type="number"
              step="0.01"
              min="0.01"
              value={split.amount === 0 ? '' : split.amount.toString()}
              onChange={(e) =>
                handleChangeField(index, 'amount', e.target.value ? Number(e.target.value) : 0)
              }
              placeholder="0.00"
              aria-label={`Split ${index + 1} amount`}
              className="font-mono"
            />
          </div>

          {/* Description input */}
          <div>
            <Input
              value={split.description ?? ''}
              onChange={(e) =>
                handleChangeField(index, 'description', e.target.value || undefined)
              }
              placeholder={t('splitForm.notesPlaceholder')}
              maxLength={255}
              aria-label={`Split ${index + 1} description`}
            />
          </div>

          {/* Remove button */}
          <div className="flex items-center justify-end">
            <Button
              type="button"
              variant="ghost"
              size="sm"
              className="h-9 w-9 p-0 text-error hover:text-error hover:bg-error/10"
              onClick={() => handleRemove(index)}
              aria-label={`Remove split ${index + 1}`}
              disabled={splits.length <= 2}
              title={splits.length <= 2 ? t('splitForm.removeDisabledTitle') : t('splitForm.removeTitle')}
            >
              <Trash2 className="h-4 w-4" />
            </Button>
          </div>
        </div>
      ))}

      {/* Add split button — REQ-SPL-3.7 */}
      <Button
        type="button"
        variant="ghost"
        size="sm"
        onClick={handleAddSplit}
        className="w-full border border-dashed border-border hover:border-primary text-text-secondary hover:text-primary"
      >
        <Plus className="h-4 w-4 mr-2" />
        {t('splitForm.addSplit')}
      </Button>

      {/* Running total summary — REQ-SPL-3.3, REQ-SPL-3.4 */}
      <div className="rounded-lg border border-border bg-surface-elevated p-3 text-sm">
        <div className="flex justify-between text-text-secondary">
          <span>{t('splitForm.transactionTotal')}</span>
          <span className="font-mono">{formatCurrency(totalAmount, currency)}</span>
        </div>
        <div className="flex justify-between text-text-secondary mt-1">
          <span>{t('splitForm.splitTotal')}</span>
          <span className="font-mono">{formatCurrency(splitTotal, currency)}</span>
        </div>
        <div
          className={`flex justify-between font-medium mt-1 pt-1 border-t border-border ${
            isValid
              ? 'text-success'
              : remaining > 0
                ? 'text-warning'
                : 'text-error'
          }`}
        >
          <span>{isValid ? t('splitForm.balanced') : remaining > 0 ? t('splitForm.remaining') : t('splitForm.overBy')}</span>
          <span className="font-mono">
            {isValid ? '✓' : formatCurrency(Math.abs(remaining), currency)}
          </span>
        </div>
      </div>

      {/* Validation error banner — REQ-SPL-3.5 */}
      {splits.length > 0 && !isValid && (
        <div
          role="alert"
          className="flex items-center gap-2 rounded-lg border border-error/30 bg-error/5 px-3 py-2 text-sm text-error"
        >
          <AlertCircle className="h-4 w-4 shrink-0" />
          <span>
            <Trans
              t={t}
              i18nKey={remaining > 0 ? 'splitForm.validation.short' : 'splitForm.validation.over'}
              values={{
                total: formatCurrency(totalAmount, currency),
                amount: formatCurrency(Math.abs(remaining), currency),
              }}
              components={{ bold: <strong /> }}
            />
          </span>
        </div>
      )}
    </div>
  );
}
