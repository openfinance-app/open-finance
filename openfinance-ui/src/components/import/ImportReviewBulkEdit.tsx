import { useId, useState } from 'react';
import { useTranslation } from 'react-i18next';
import { Trash2, X } from 'lucide-react';
import { Button } from '@/components/ui/Button';
import { CategoryCombobox } from '@/components/ui/CategorySelect';
import { DateInput } from '@/components/ui/DateInput';
import { Input } from '@/components/ui/Input';
import { NumberInput } from '@/components/ui/NumberInput';
import { PayeeCombobox } from '@/components/ui/PayeeSelector';
import type { ImportTransactionDTO } from '@/types/import';
import { equalDecimals } from '@/utils/exact-decimal';
import { getCurrencySymbol } from '@/utils/currency';
import {
  MAX_IMPORT_AMOUNT,
  reviewedAmountChanges,
  updateReviewedDateAndAmount,
} from '@/utils/import-review';

interface Props {
  transactions: ImportTransactionDTO[];
  selected: Set<number>;
  partners: Map<number, number>;
  today: string;
  baseCurrency: string;
  onApply: (rows: ImportTransactionDTO[]) => void;
  onCategory: (name: string, id?: number) => void;
  onClear: () => void;
  onRemove: () => void;
}

interface BulkTextDraft {
  text: string;
  clear: boolean;
}

function BulkTextField({
  label,
  clearLabel,
  draft,
  onChange,
  maxLength,
  disabled,
}: {
  label: string;
  clearLabel: string;
  draft: BulkTextDraft;
  onChange: (draft: BulkTextDraft) => void;
  maxLength: number;
  disabled: boolean;
}) {
  const id = useId();
  return (
    <div>
      <label className="review-bulk-label" htmlFor={id}>
        {label}
      </label>
      <textarea
        id={id}
        rows={1}
        className="block min-h-10 w-full resize-y rounded-lg border border-border bg-surface px-3 py-2 text-sm text-text-primary focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-primary disabled:opacity-50"
        disabled={disabled || draft.clear}
        value={draft.clear ? '' : draft.text}
        maxLength={maxLength}
        onChange={e => onChange({ ...draft, text: e.target.value })}
      />
      <label className="mt-1 inline-flex items-center gap-2 text-xs text-text-secondary">
        <input
          type="checkbox"
          checked={draft.clear}
          disabled={disabled}
          onChange={e => onChange({ ...draft, clear: e.target.checked })}
        />
        {clearLabel}
      </label>
    </div>
  );
}

export function ImportReviewBulkEdit({
  transactions,
  selected,
  partners,
  today,
  baseCurrency,
  onApply,
  onCategory,
  onClear,
  onRemove,
}: Props) {
  const { t } = useTranslation('import');
  const id = useId();
  const [category, setCategory] = useState<{ name: string; id?: number }>({ name: '' });
  const [payee, setPayee] = useState('');
  const [tagText, setTagText] = useState('');
  const [date, setDate] = useState('');
  const [amount, setAmount] = useState('');
  const [description, setDescription] = useState<BulkTextDraft>({ text: '', clear: false });
  const [notes, setNotes] = useState<BulkTextDraft>({ text: '', clear: false });

  const rows = Array.from(selected, index => transactions[index]);
  const hasTransactions = rows.some(row => !row.openingBalance);
  const tags = hasTransactions
    ? tagText
        .split(',')
        .map(tag => tag.trim())
        .filter(Boolean)
    : [];
  const mergedTags = (row: ImportTransactionDTO) => [...new Set([...(row.tags ?? []), ...tags])];
  const invalidTags =
    tags.length > 0 &&
    (tags.some(tag => tag.length > 50) ||
      rows.some(row => !row.openingBalance && mergedTags(row).join(',').length > 500));
  const invalidZero = hasTransactions && equalDecimals(amount, 0);
  const editDescription = hasTransactions && (description.clear || !!description.text.trim());
  const editNotes = hasTransactions && (notes.clear || !!notes.text.trim());
  const canApply =
    !invalidZero &&
    !invalidTags &&
    !!(
      date ||
      amount ||
      editDescription ||
      editNotes ||
      category.name.trim() ||
      payee.trim() ||
      tags.length
    );
  const currencies = [
    ...new Set(rows.map(row => row.currency || row.reviewCurrency || baseCurrency)),
  ];
  const hasPair = [...selected].some(index => partners.has(index));

  function apply(): void {
    if (!canApply) return;
    const amounts = amount
      ? reviewedAmountChanges(transactions, partners, selected, amount)
      : new Map<number, string>();
    const updated = transactions.map((row, index) => {
      const amountChange = amounts.get(index);
      if (!selected.has(index))
        return amountChange === undefined
          ? row
          : updateReviewedDateAndAmount(row, row.transactionDate, amountChange);
      return {
        ...updateReviewedDateAndAmount(row, date || row.transactionDate, amountChange),
        ...(category.name.trim() && { category: category.name.trim() }),
        ...(payee.trim() && { payee: payee.trim() }),
        ...(editDescription &&
          !row.openingBalance && { description: description.clear ? '' : description.text }),
        ...(editNotes && !row.openingBalance && { memo: notes.clear ? null : notes.text }),
        ...(tags.length > 0 && !row.openingBalance && { tags: mergedTags(row) }),
      };
    });
    if (category.id != null) onCategory(category.name, category.id);
    onApply(updated);
  }

  return (
    <section
      aria-labelledby={`${id}-selected`}
      className="review-bulk space-y-3 rounded-lg border border-primary/30 bg-surface-elevated p-3"
    >
      <div className="flex items-center gap-2">
        <span id={`${id}-selected`} className="mr-auto text-sm font-semibold text-primary">
          {t('review.bulkAction.selected', { count: selected.size })}
        </span>
        <Button
          type="button"
          size="sm"
          variant="ghost"
          onClick={onRemove}
          title={t('review.actions.removeSelected')}
          className="text-error"
        >
          <Trash2 className="h-4 w-4" />
        </Button>
        <Button
          type="button"
          size="sm"
          variant="ghost"
          onClick={onClear}
          title={t('review.actions.clearSelection')}
        >
          <X className="h-4 w-4" />
        </Button>
      </div>
      <div className="review-bulk-fields">
        <div>
          <span className="review-bulk-label">{t('review.table.category')}</span>
          <CategoryCombobox
            value={category.name}
            onValueChange={(name, id) => setCategory({ name, id })}
            placeholder={t('review.bulkAction.setCategory')}
            className="w-full h-10"
          />
        </div>
        <div>
          <span className="review-bulk-label">{t('review.table.payee')}</span>
          <PayeeCombobox
            value={payee}
            onValueChange={setPayee}
            placeholder={t('review.bulkAction.setPayee')}
            className="w-full h-10"
          />
        </div>
        <div>
          <label className="review-bulk-label" htmlFor={`${id}-tags`}>
            {t('review.details.tags')}
          </label>
          <Input
            id={`${id}-tags`}
            disabled={!hasTransactions}
            aria-label={t('review.bulkAction.addTags')}
            placeholder={t('review.bulkAction.addTags')}
            value={tagText}
            onChange={e => setTagText(e.target.value)}
            error={invalidTags ? t('review.details.tagLength') : undefined}
          />
        </div>
        <BulkTextField
          label={t('review.table.description')}
          clearLabel={t('review.bulkAction.clearDescription')}
          draft={description}
          onChange={setDescription}
          maxLength={255}
          disabled={!hasTransactions}
        />
        <div>
          <label className="review-bulk-label" htmlFor={`${id}-date`}>
            {t('review.table.date')}
          </label>
          <DateInput form={id} id={`${id}-date`} value={date} max={today} onChange={setDate} />
        </div>
        <div>
          <label className="review-bulk-label" htmlFor={`${id}-amount`}>
            {t('review.table.amount')}{' '}
            {currencies.length === 1 && (
              <span
                className="font-normal text-text-secondary"
                title={currencies[0]}
                aria-hidden="true"
              >
                {getCurrencySymbol(currencies[0])}
              </span>
            )}
          </label>
          <NumberInput
            id={`${id}-amount`}
            form={id}
            value={amount}
            onChange={setAmount}
            calculable
            min={`-${MAX_IMPORT_AMOUNT}`}
            max={MAX_IMPORT_AMOUNT}
            maxFractionDigits={18}
            error={invalidZero ? t('validation.amountZero') : undefined}
            aria-describedby={`${id}-amount-hint`}
          />
          <div id={`${id}-amount-hint`} className="mt-1 text-xs text-text-secondary">
            {currencies.length > 1 && <p>{t('review.bulkAction.nativeAmounts')}</p>}
            {currencies.length === 1 && <span className="sr-only">{currencies[0]}</span>}
            {hasPair && <p>{t('review.bulkAction.transferAmounts')}</p>}
          </div>
        </div>
        <div className="review-bulk-notes">
          <BulkTextField
            label={t('review.table.notes')}
            clearLabel={t('review.bulkAction.clearNotes')}
            draft={notes}
            onChange={setNotes}
            maxLength={1000}
            disabled={!hasTransactions}
          />
        </div>
        <form
          id={id}
          className="flex items-start justify-end sm:self-center"
          onSubmit={event => {
            event.preventDefault();
            apply();
          }}
        >
          <Button type="submit" size="sm" className="w-full sm:w-auto" disabled={!canApply}>
            {t('review.bulkAction.applyButton', { count: selected.size })}
          </Button>
        </form>
      </div>
    </section>
  );
}
