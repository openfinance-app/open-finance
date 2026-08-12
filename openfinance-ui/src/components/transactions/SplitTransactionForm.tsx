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
import {
  fromMinorUnits,
  multiply,
  sumToDecimals,
  toMinorUnits,
  distributeRemainder,
} from '@/utils/money';
import { getCurrencyDecimals } from '@/utils/currency';
import type { TransactionSplitRequest } from '@/types/transaction';
import type { TransactionType } from '@/types/transaction';

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
  /** Account currency to convert to on save; when different from `currency`, a converted total is shown */
  accountCurrency?: string;
  /** 1 unit of `currency` = `exchangeRate` units of `accountCurrency` (latest rate) */
  exchangeRate?: number;
}

/**
 * Splits must sum EXACTLY to the total (REQ-SPL-1.2). Comparison is done on integer minor units at
 * the currency's precision, so it is float-safe and currency-aware (JPY = 0 decimals, crypto = 8).
 */
function isExactMatch(a: number, b: number, decimals: number): boolean {
  return toMinorUnits(a, decimals) === toMinorUnits(b, decimals);
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
  accountCurrency,
  exchangeRate,
}: SplitTransactionFormProps) {
  const { t } = useTranslation('transactions');
  const { format: formatCurrency } = useFormatCurrency();
  // Cap at 4 dp to mirror the backend split scale (@Digits(fraction = 4)); crypto's 8-dp display
  // precision is not usable for split line amounts.
  const decimals = Math.min(getCurrencyDecimals(currency), 4);
  // REQ-SPL-3.3: running total via exact integer minor-units arithmetic.
  const splitTotal = sumToDecimals(
    splits.map(s => Number(s.amount) || 0),
    decimals
  );
  const remaining = fromMinorUnits(
    toMinorUnits(totalAmount, decimals) - toMinorUnits(splitTotal, decimals),
    decimals
  );
  const isValid = isExactMatch(splitTotal, totalAmount, decimals);

  // REQ-SPL-1.2: allocate the leftover across lines so the splits sum exactly to the total.
  const handleDistribute = () => {
    const amounts = distributeRemainder(
      totalAmount,
      splits.map(s => Number(s.amount) || 0),
      decimals
    );
    onChange(splits.map((s, i) => ({ ...s, amount: amounts[i] })));
  };

  // Show an ≈ converted total in the account currency when the user is entering splits in a
  // different currency than the account (the amounts themselves are converted on save).
  const showConverted =
    !!accountCurrency && accountCurrency !== currency && typeof exchangeRate === 'number';

  // REQ-SPL-3.7: add a new blank split line
  const handleAddSplit = () => {
    onChange([
      ...splits,
      {
        categoryId: undefined,
        // `remaining` is already precisely rounded to 2 decimals (see above) — no further
        // Math.round(x * 100) / 100 needed, which would reintroduce the float rounding bug.
        amount: remaining > 0 ? remaining : 0,
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
              step="any"
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

      {splits.length >= 2 && !isValid && (
        <Button
          type="button"
          variant="ghost"
          size="sm"
          onClick={handleDistribute}
          className="w-full border border-dashed border-border hover:border-primary text-text-secondary hover:text-primary"
        >
          {t('splitForm.distribute')}
        </Button>
      )}

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
        {showConverted && (
          <div className="mt-1 text-right text-xs text-text-secondary font-mono">
            ≈ {formatCurrency(multiply(splitTotal, exchangeRate as number), accountCurrency)}
          </div>
        )}
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
