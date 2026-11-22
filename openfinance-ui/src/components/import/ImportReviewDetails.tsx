import { LinkedText } from '@/components/ui/LinkedText';
import { useRef, useState } from 'react';
import { useTranslation } from 'react-i18next';
import { Button } from '@/components/ui/Button';
import { CategoryCombobox } from '@/components/ui/CategorySelect';
import { ConvertedAmount } from '@/components/ui/ConvertedAmount';
import {
  Dialog,
  DialogContent,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from '@/components/ui/Dialog';
import { SplitTransactionForm } from '@/components/transactions/SplitTransactionForm';
import { TagInput } from '@/components/transactions/TagInput';
import { usePopularTags } from '@/hooks/useTransactionTags';
import type { ImportSplit, ImportTransactionDTO } from '@/types/import';
import type { TransactionSplitRequest } from '@/types/transaction';
import { isSplitAmountError } from '@/utils/import-review';
import { categoryPath, type CategoryPathNode } from '@/utils/category-path';
import { decimalValue, ExactDecimal, exactSum } from '@/utils/exact-decimal';

interface ReviewLine extends TransactionSplitRequest {
  key?: number;
  source?: ImportSplit;
  categoryName?: string;
}

function lineFromSource(source: ImportSplit, key: number): ReviewLine {
  const amount = decimalValue(source, 'amount');
  return {
    source,
    key,
    categoryName: source.category ?? '',
    amount: amount == null ? '' : new ExactDecimal(amount).abs().toFixed(),
    description: source.memo ?? '',
    tags: source.tags ?? [],
  };
}

function toImportSplit(line: ReviewLine): ImportSplit {
  const source = line.source;
  const category = line.categoryName?.trim() ?? '';
  const memo = line.description ?? '';
  const tags = line.tags ?? [];
  if (!source) return { category, amount: line.amount, memo, tags };
  const renamed = (line.categoryName ?? '') !== (source.category ?? '');
  return {
    ...source,
    ...(renamed && { category, sourceCategoryId: null }),
    ...(!unchangedAmount(line) && { amount: line.amount }),
    ...(memo !== (source.memo ?? '') && { memo }),
    ...(line.tags !== source.tags && (tags.length > 0 || source.tags != null) && { tags }),
  };
}

// Imported expense lines can be signed and may carry extra decimal precision.
function unchangedAmount(line: ReviewLine): boolean {
  if (!line.source) return false;
  const amount = decimalValue(line.source, 'amount');
  return amount == null ? line.amount === '' : new ExactDecimal(amount).abs().eq(line.amount || 0);
}

interface Props {
  transaction: ImportTransactionDTO;
  currency: string;
  categories: CategoryPathNode[];
  onClose: () => void;
  onSave: (row: ImportTransactionDTO, mappings: Record<string, number>) => void;
}

export function ImportReviewDetails({ transaction, currency, categories, onClose, onSave }: Props) {
  const { t } = useTranslation('import');
  const { t: tx } = useTranslation('transactions');
  const { data: suggestions = [] } = usePopularTags();
  const [tags, setTags] = useState(transaction.tags ?? []);
  const [lines, setLines] = useState<ReviewLine[]>(() =>
    (transaction.splits ?? []).map(lineFromSource)
  );
  const [splitsEdited, setSplitsEdited] = useState(false);
  const nextKey = useRef(lines.length);
  const amount = decimalValue(transaction, 'amount');
  const total = new ExactDecimal(amount ?? 0).abs();
  const label = transaction.payee || transaction.description;
  const allocationChanged =
    splitsEdited &&
    (lines.length !== (transaction.splits?.length ?? 0) ||
      lines.some(
        (line, index) => line.source !== transaction.splits?.[index] || !unchangedAmount(line)
      ));
  const canSplit = !transaction.transfer && total.gt(0);
  const validLines =
    lines.length >= 2 &&
    lines.every(line => {
      const amount = new ExactDecimal(line.amount || 0);
      return amount.gt(0) && amount.dp() <= 18 && amount.lt('1e26');
    });
  const invalidAllocation =
    allocationChanged &&
    lines.length > 0 &&
    (!canSplit || !validLines || !total.eq(exactSum(lines.map(line => line.amount))));
  const longTags =
    tags.join(',').length > 500 || lines.some(line => (line.tags ?? []).join(',').length > 500);

  function changeLines(next: ReviewLine[]) {
    setLines(next.map(line => (line.key == null ? { ...line, key: nextKey.current++ } : line)));
    setSplitsEdited(true);
  }

  function save() {
    const mappings: Record<string, number> = {};
    for (const line of lines) {
      if (line.categoryId != null && line.categoryName)
        mappings[line.categoryName] = line.categoryId;
    }
    onSave(
      {
        ...transaction,
        tags,
        ...(splitsEdited
          ? {
              splits: lines.map(toImportSplit),
              ...(allocationChanged && {
                validationErrors: transaction.validationErrors.filter(
                  error => !isSplitAmountError(error)
                ),
              }),
            }
          : {}),
      },
      mappings
    );
  }

  return (
    <Dialog
      open
      onOpenChange={open => {
        if (!open) onClose();
      }}
    >
      <DialogContent className="max-w-xl max-h-[min(80dvh,42rem)] max-sm:max-h-[calc(100dvh-1.5rem)] grid-rows-[auto_minmax(0,1fr)_auto] gap-3 p-4 sm:p-5">
        <DialogHeader className="pr-6 text-left">
          <DialogTitle>{t('review.details.title')}</DialogTitle>
          <p className="text-sm text-text-secondary [overflow-wrap:anywhere] max-h-16 overflow-y-auto">
            <LinkedText
              text={
                transaction.memo && transaction.memo !== label
                  ? [label, transaction.memo].filter(Boolean).join(' · ')
                  : label
              }
            />
            {(label || transaction.memo) && amount != null && ' · '}
            {amount != null && <ConvertedAmount amount={amount} currency={currency} inline />}
          </p>
        </DialogHeader>
        <form
          id="import-review-details"
          className="space-y-3 overflow-y-auto min-h-0 px-1 py-1"
          onKeyDown={event => {
            if (
              event.key === 'Enter' &&
              !event.defaultPrevented &&
              event.target instanceof HTMLInputElement &&
              event.target.getAttribute('role') === 'combobox'
            ) {
              event.preventDefault();
              event.target.blur();
            }
          }}
          onSubmit={event => {
            event.preventDefault();
            if (!invalidAllocation && !longTags) save();
          }}
        >
          <div className="space-y-1">
            <p className="text-sm font-medium">{t('review.details.tags')}</p>
            <TagInput
              value={tags}
              onChange={setTags}
              suggestions={suggestions}
              commitOnBlur
              aria-label={t('review.details.tags')}
              placeholder={t('review.details.tagPlaceholder')}
            />
          </div>
          {(canSplit || lines.length > 0) && (
            <section className="space-y-1 border-t border-border pt-2">
              <div className="flex items-center justify-between gap-2">
                <h3 className="text-sm font-medium">{t('review.table.splitLines')}</h3>
                {lines.length > 0 ? (
                  <Button type="button" size="sm" variant="ghost" onClick={() => changeLines([])}>
                    {tx('form.removeSplit')}
                  </Button>
                ) : (
                  <Button
                    type="button"
                    size="sm"
                    variant="outline"
                    onClick={() =>
                      changeLines([
                        { amount: '', categoryName: transaction.category ?? '' },
                        { amount: '' },
                      ])
                    }
                  >
                    {tx('form.splitTransaction')}
                  </Button>
                )}
              </div>
              {lines.length > 0 && canSplit && (
                <SplitTransactionForm
                  compact
                  totalAmount={total.toFixed()}
                  currency={currency}
                  splits={lines}
                  onChange={changeLines}
                  tagSuggestions={suggestions}
                  commitTagsOnBlur
                  splitKey={index => lines[index].key!}
                  renderCategory={index => (
                    <CategoryCombobox
                      value={lines[index].categoryName ?? ''}
                      placeholder={tx('splitForm.categoryLabel', { index: index + 1 })}
                      onValueChange={(name, id) => {
                        const path =
                          id == null ? name : categoryPath(categories, id, 'display') || name;
                        if (path === lines[index].categoryName && id == null) return;
                        changeLines(
                          lines.map((line, i) =>
                            i === index ? { ...line, categoryName: path, categoryId: id } : line
                          )
                        );
                      }}
                    />
                  )}
                />
              )}
              {invalidAllocation &&
                (!canSplit || (!validLines && lines.every(line => line.amount !== ''))) && (
                  <p role="alert" className="text-sm text-error">
                    {t('review.details.splitAmounts')}
                  </p>
                )}
            </section>
          )}
          {longTags && (
            <p role="alert" className="text-sm text-error">
              {t('review.details.tagLength')}
            </p>
          )}
        </form>
        <DialogFooter className="flex-row justify-end gap-2 border-t border-border pt-3 sm:space-x-0">
          <Button type="button" variant="secondary" onClick={onClose}>
            {t('review.actions.cancel')}
          </Button>
          <Button
            type="submit"
            form="import-review-details"
            disabled={invalidAllocation || longTags}
          >
            {t('review.actions.save')}
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  );
}
