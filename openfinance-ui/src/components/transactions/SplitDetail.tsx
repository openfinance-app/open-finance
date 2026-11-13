/**
 * SplitDetail Component
 * Task 13 (REQ-SPL-4.3)
 *
 * Renders the split lines belonging to a split transaction in the transaction list.
 * Each line shows a category pill, the optional description, and the amount (right-aligned).
 */
import { useTranslation } from 'react-i18next';
import { ConvertedAmount } from '@/components/ui/ConvertedAmount';
import { Badge } from '@/components/ui/Badge';
import { translateCategoryName } from '@/utils/categoryTranslation';
import type { Transaction, TransactionSplitResponse } from '@/types/transaction';
import { splitCurrencyAmounts } from '@/utils/split-currency-amounts';

interface SplitDetailProps {
  /** The list of split lines to render */
  splits: TransactionSplitResponse[];
  /** ISO 4217 currency code of the parent transaction */
  currency: string;
  transaction?: Transaction;
}

/**
 * CategorySplitPill — a compact category indicator (colored dot + name).
 */
function CategorySplitPill({
  name,
  color,
  icon,
}: {
  name?: string;
  color?: string;
  icon?: string;
}) {
  const { t } = useTranslation('transactions');
  const { t: tCategories } = useTranslation('categories');
  if (!name) {
    return <span className="text-xs text-text-tertiary italic">{t('splitDetail.noCategory')}</span>;
  }

  const displayName = translateCategoryName(tCategories, name);

  return (
    <span className="inline-flex items-center gap-1 text-xs text-text-secondary">
      {icon ? (
        <span className="text-sm leading-none" aria-hidden="true">
          {icon}
        </span>
      ) : (
        <span
          className="inline-block h-2 w-2 rounded-full shrink-0"
          style={{ backgroundColor: color || 'var(--color-primary, #6366f1)' }}
          aria-hidden="true"
        />
      )}
      <span>{displayName}</span>
    </span>
  );
}

/**
 * SplitDetail renders each split line of a split transaction.
 * Used inside TransactionItem when the user expands a split transaction (REQ-SPL-4.3).
 */
export function SplitDetail({ splits, currency, transaction }: SplitDetailProps) {
  const { t } = useTranslation('transactions');
  if (!splits || splits.length === 0) {
    return (
      <p className="text-xs text-text-tertiary px-2 py-1">{t('splitDetail.noSplitDetails')}</p>
    );
  }

  const equivalents = splitCurrencyAmounts(splits, transaction);
  return (
    <ul className="mt-2 space-y-1" aria-label="Split details">
      {splits.map((split, index) => (
        <li
          key={split.id}
          className="flex flex-col items-stretch gap-1.5 px-3 py-1.5 rounded-md bg-surface-elevated text-sm sm:flex-row sm:items-center sm:justify-between sm:gap-3"
        >
          {/* Left: category pill + optional description */}
          <div className="flex flex-col min-w-0">
            <CategorySplitPill
              name={split.categoryName}
              color={split.categoryColor}
              icon={split.categoryIcon}
            />
            {split.description && (
              <span className="text-xs text-text-tertiary truncate mt-0.5">
                {split.description}
              </span>
            )}
            {!!split.tags?.length && (
              <div className="flex flex-wrap gap-1 mt-1">
                {split.tags.map(tag => (
                  <Badge key={tag} variant="secondary">
                    {tag}
                  </Badge>
                ))}
              </div>
            )}
          </div>

          {/* Right: amount */}
          <span className="self-end font-mono text-sm text-text-primary shrink-0 sm:self-auto">
            <ConvertedAmount
              amount={split.amount}
              currency={currency}
              convertedAmount={
                equivalents[index].find(value => value.currency === transaction?.baseCurrency)
                  ?.amount
              }
              baseCurrency={transaction?.baseCurrency}
              exchangeRate={transaction?.exchangeRate}
              isConverted={transaction?.isConverted}
              conversionDate={transaction?.date}
              recordedAmounts={equivalents[index]}
              inline
            />
          </span>
        </li>
      ))}
    </ul>
  );
}
