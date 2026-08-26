/**
 * TransactionList Component
 * Task 3.2.15: Create TransactionList component
 *
 * Displays transactions grouped by date with edit/delete actions.
 * Shows payee logos/avatars and category info on each transaction card.
 * Shows visual connectors between linked transfer transactions.
 */
import { useEffect, useState } from 'react';
import { ROW_HIGHLIGHT_SCROLL_DELAY_MS } from '@/constants/timing';
import { useTranslation } from 'react-i18next';
import { Edit2, Trash2, ArrowUpRight, ArrowDownLeft, ArrowRightLeft, Calendar, Scissors, ChevronDown, ChevronUp, CreditCard, Banknote, Landmark, Repeat, Globe, FileText, Wallet } from 'lucide-react';
import { Button } from '@/components/ui/Button';
import { Badge } from '@/components/ui/Badge';
import { ConvertedAmount } from '@/components/ui/ConvertedAmount';
import { groupByDate } from '@/utils/date';
import type { Transaction } from '@/types/transaction';
import type { PaymentMethod } from '@/types/transaction';
import type { Payee } from '@/types/payee';
import { useActivePayees } from '@/hooks/usePayees';
import { useUserSettings } from '@/hooks/useUserSettings';
import { cn } from '@/lib/utils';
import { SplitDetail } from './SplitDetail';
import { wasSelectJustClosed } from '@/utils/selectClickGuard';

interface TransactionListProps {
  transactions: Transaction[];
  onEdit: (transaction: Transaction) => void;
  onDelete: (transaction: Transaction) => void;
  highlightedId?: number | null;
  onViewDetail?: (transaction: Transaction) => void;
  /** Controls date-group ordering: 'asc' = oldest first, 'desc' = newest first (default) */
  sortDirection?: 'asc' | 'desc';
}

// Icon for transaction type
const getTransactionIcon = (type: Transaction['type']) => {
  switch (type) {
    case 'INCOME':
      return <ArrowDownLeft className="h-4 w-4" />;
    case 'EXPENSE':
      return <ArrowUpRight className="h-4 w-4" />;
    case 'TRANSFER':
      return <ArrowRightLeft className="h-4 w-4" />;
  }
};

// Color classes for transaction type
const getTransactionColor = (type: Transaction['type']) => {
  switch (type) {
    case 'INCOME':
      return 'text-success bg-success/10';
    case 'EXPENSE':
      return 'text-error bg-error/10';
    case 'TRANSFER':
      return 'text-primary bg-primary/10';
  }
};

// Icon for a payment method
const getPaymentMethodIcon = (method: PaymentMethod) => {
  switch (method) {
    case 'CASH':
      return <Banknote className="h-3.5 w-3.5" />;
    case 'CHEQUE':
      return <FileText className="h-3.5 w-3.5" />;
    case 'CREDIT_CARD':
    case 'DEBIT_CARD':
      return <CreditCard className="h-3.5 w-3.5" />;
    case 'BANK_TRANSFER':
    case 'DEPOSIT':
      return <Landmark className="h-3.5 w-3.5" />;
    case 'STANDING_ORDER':
    case 'DIRECT_DEBIT':
      return <Repeat className="h-3.5 w-3.5" />;
    case 'ONLINE':
      return <Globe className="h-3.5 w-3.5" />;
    default:
      return <Wallet className="h-3.5 w-3.5" />;
  }
};

// Check if transaction date is in the future
const isFutureDate = (dateString: string) => {
  const transactionDate = new Date(dateString);
  const today = new Date();
  today.setHours(0, 0, 0, 0);
  transactionDate.setHours(0, 0, 0, 0);
  return transactionDate > today;
};

/**
 * PayeeAvatarWithFallback — shows logo first, then falls back to initial avatar.
 * Handles broken image URLs by rendering both and hiding the broken one via JS.
 */
function PayeeAvatarWithFallback({ payee }: { payee: Payee }) {
  const initial = payee.name.charAt(0).toUpperCase();

  if (!payee.logo) {
    return (
      <div
        className="h-10 w-10 flex shrink-0 items-center justify-center rounded-full bg-primary/15 text-primary text-sm font-semibold border border-primary/20"
        title={payee.name}
        aria-label={payee.name}
      >
        {initial}
      </div>
    );
  }

  return (
    <div className="relative h-10 w-10 shrink-0">
      <img
        src={payee.logo}
        alt={payee.name}
        className="h-10 w-10 rounded-full object-contain bg-white p-0.5 border border-border"
        onError={(e) => {
          (e.currentTarget as HTMLImageElement).classList.add('hidden');
          const fallback = (e.currentTarget as HTMLImageElement).nextElementSibling as HTMLElement | null;
          if (fallback) fallback.classList.remove('hidden');
        }}
      />
      {/* Fallback initial avatar — hidden by default, shown if logo fails */}
      <div
        className="hidden h-10 w-10 items-center justify-center rounded-full bg-primary/15 text-primary text-sm font-semibold border border-primary/20 absolute inset-0"
        aria-hidden="true"
      >
        {initial}
      </div>
    </div>
  );
}

/**
 * CategoryPill — renders a compact chip with a color-tinted background and the
 * category icon (or a colored dot) plus the category name.
 */
function CategoryPill({
  name,
  color,
  icon,
}: {
  name: string;
  color?: string;
  icon?: string;
}) {
  const dotColor = color || 'var(--color-primary, #6366f1)';
  return (
    <span
      className="inline-flex items-center gap-1 rounded-full px-2 py-0.5 text-xs font-medium leading-none"
      style={{
        backgroundColor: `color-mix(in srgb, ${dotColor} 14%, transparent)`,
        color: dotColor,
      }}
    >
      {icon ? (
        <span className="text-sm leading-none" aria-hidden="true">
          {icon}
        </span>
      ) : (
        <span
          className="inline-block h-1.5 w-1.5 rounded-full shrink-0"
          style={{ backgroundColor: dotColor }}
          aria-hidden="true"
        />
      )}
      <span>{name}</span>
    </span>
  );
}

// Single transaction item component
function TransactionItem({
  transaction,
  onEdit,
  onDelete,
  isTransferSource,
  isTransferDest,
  showConnector,
  isHighlighted,
  payeesMap,
  onViewDetail,
}: {
  transaction: Transaction;
  onEdit: (t: Transaction) => void;
  onDelete: (t: Transaction) => void;
  isTransferSource?: boolean;
  isTransferDest?: boolean;
  showConnector?: boolean;
  isHighlighted?: boolean;
  payeesMap: Map<string, Payee>;
  onViewDetail?: (t: Transaction) => void;
}) {
  const { t } = useTranslation('common');
  const { t: tl } = useTranslation('transactions');

  useEffect(() => {
    if (isHighlighted) {
      const timer = setTimeout(() => {
        const element = document.getElementById(`transaction-${transaction.id}`);
        if (element) {
          element.scrollIntoView({ behavior: 'smooth', block: 'center' });
        }
      }, ROW_HIGHLIGHT_SCROLL_DELAY_MS);
      return () => clearTimeout(timer);
    }
  }, [isHighlighted, transaction.id]);

  // REQ-SPL-4.2: collapsed by default; expand to show split lines
  const [splitsExpanded, setSplitsExpanded] = useState(false);

  // Resolve payee object from the map (keyed by name)
  const payeeObj = transaction.payee ? payeesMap.get(transaction.payee) : undefined;

  // Category info — prefer full category object, fall back to denormalized backend fields
  const categoryName = transaction.category?.name || transaction.categoryName;
  const categoryColor = transaction.category?.color || transaction.categoryColor;
  const categoryIcon = transaction.category?.icon || transaction.categoryIcon;

  // The title line already shows the payee when there is no description, so only
  // repeat it on the metadata line when a description occupies the title.
  const showPayeeInMeta = Boolean(transaction.payee && transaction.description);
  const paymentMethodLabel = transaction.paymentMethod
    ? tl('form.paymentMethods.' + transaction.paymentMethod, {
        defaultValue: transaction.paymentMethod,
      })
    : undefined;

  return (
    <div
      id={`transaction-${transaction.id}`}
      className={cn(
        'flex items-center gap-3 p-4 bg-surface rounded-lg hover:bg-surface-elevated transition-all duration-300 group relative',
        isHighlighted &&
        'ring-2 ring-primary ring-offset-2 ring-offset-background bg-primary/5 shadow-lg scale-[1.02] z-30',
        onViewDetail && 'cursor-pointer',
      )}
      onClick={(e) => {
        if ((e.target as HTMLElement).closest('button')) return;
        // Prevent spurious click-through when a Radix Select dropdown closes
        // (the portal is removed before the click fires, causing it to land on the card)
        if (wasSelectJustClosed()) return;
        onViewDetail?.(transaction);
      }}
    >
      {/* Connector line for transfers */}
      {showConnector && (
        <div className="absolute left-[2.25rem] top-0 bottom-0 w-0.5 bg-primary/30 -translate-y-1/2 h-full" />
      )}

      {/* Left avatar area: payee logo/initial (if payee known) OR type-icon circle */}
      <div className="relative shrink-0">
        {payeeObj ? (
          <>
            {/* Payee avatar */}
            <PayeeAvatarWithFallback payee={payeeObj} />
            {/* Transaction-type badge overlaid at bottom-right of avatar - only for TRANSFERS per user request */}
            {transaction.type === 'TRANSFER' && (
              <div
                className={cn(
                  'absolute -bottom-0.5 -right-0.5 flex h-[1.1rem] w-[1.1rem] items-center justify-center rounded-full border-2 border-background z-10',
                  getTransactionColor(transaction.type),
                )}
                title={
                  transaction.type.charAt(0) + transaction.type.slice(1).toLowerCase()
                }
              >
                <span className="[&>svg]:h-2.5 [&>svg]:w-2.5">
                  {getTransactionIcon(transaction.type)}
                </span>
              </div>
            )}
          </>
        ) : (
          /* No payee — show the classic type-icon circle */
          <div
            className={cn(
              'flex h-10 w-10 shrink-0 items-center justify-center rounded-full z-10',
              getTransactionColor(transaction.type),
            )}
          >
            {getTransactionIcon(transaction.type)}
          </div>
        )}

        {/* Transfer source / destination connector dots */}
        {(isTransferSource || isTransferDest) && (
          <div
            className={cn(
              'absolute left-1/2 -translate-x-1/2 w-3 h-3 rounded-full border-2 border-background z-20',
              isTransferSource ? 'bg-error -bottom-1.5' : 'bg-success -top-1.5',
            )}
            title={
              isTransferSource
                ? tl('list.aria.transferSource')
                : tl('list.aria.transferDest')
            }
          />
        )}
      </div>

      {/* Main content */}
      <div className="flex-1 min-w-0">
        {/* Primary line: description */}
        <div className="flex items-center gap-2 flex-wrap">
          <p className="font-medium text-text-primary truncate">
            {transaction.description || transaction.payee || t('noDescription')}
          </p>
          {isFutureDate(transaction.date) && (
            <Badge
              variant="warning"
              className="shrink-0 flex items-center gap-1"
              title={`Scheduled for ${transaction.date}`}
            >
              <Calendar className="h-3 w-3" />
              <span className="text-xs">{tl('list.badges.future')}</span>
            </Badge>
          )}
          {/* REQ-SPL-4.1: "Split" badge when transaction.hasSplits is true */}
          {transaction.hasSplits && (
            <button
              type="button"
              onClick={() => setSplitsExpanded((prev) => !prev)}
              aria-expanded={splitsExpanded}
              aria-label={t('aria.toggleSplitDetails')}
              title="This transaction is split across multiple categories"
              className="shrink-0 inline-flex items-center gap-1 cursor-pointer select-none rounded-full bg-accent-blue/10 text-accent-blue border border-accent-blue/20 px-2.5 py-1 text-sm font-medium whitespace-nowrap transition-colors hover:bg-accent-blue/20"
            >
              <Scissors className="h-3 w-3" />
              <span className="text-xs">{tl('list.badges.split')}</span>
              {splitsExpanded ? (
                <ChevronUp className="h-3 w-3" />
              ) : (
                <ChevronDown className="h-3 w-3" />
              )}
            </button>
          )}
        </div>

        {/* Secondary line: payee name • category pill • payment method (replaces account name) */}
        <div className="flex items-center gap-1.5 mt-0.5 flex-wrap">
          {showPayeeInMeta && (
            <span className="text-sm text-text-secondary font-medium truncate max-w-[12rem]">
              {transaction.payee}
            </span>
          )}
          {showPayeeInMeta && categoryName && (
            <span className="text-text-tertiary text-xs" aria-hidden="true">
              ·
            </span>
          )}
          {categoryName && (
            <CategoryPill
              name={categoryName}
              color={categoryColor}
              icon={categoryIcon}
            />
          )}
          {/* For transfers show the destination account */}
          {transaction.type === 'TRANSFER' &&
            (transaction.toAccount?.name || transaction.toAccountName) && (
              <>
                {(showPayeeInMeta || categoryName) && (
                  <span className="text-text-tertiary text-xs" aria-hidden="true">
                    →
                  </span>
                )}
                <span className="text-xs text-text-secondary truncate">
                  {transaction.toAccount?.name || transaction.toAccountName}
                </span>
              </>
            )}
        </div>

        {/* Tags */}
        {(() => {
          const rawTags: unknown = transaction.tags;
          const tags = Array.isArray(rawTags)
            ? rawTags
            : typeof rawTags === 'string'
              ? rawTags
                .split(',')
                .map((tag: string) => tag.trim())
                .filter((tag: string) => tag.length > 0)
              : [];

          return tags.length > 0 ? (
            <div className="flex flex-wrap gap-1 mt-1.5">
              {tags.map((tag: string, index: number) => (
                <Badge key={index} variant="info" className="text-xs px-2 py-0.5">
                  {tag}
                </Badge>
              ))}
            </div>
          ) : null;
        })()}

        {/* REQ-SPL-4.3, REQ-SPL-4.4: inline split details (collapsed by default) */}
        {transaction.hasSplits && splitsExpanded && transaction.splits && (
          <SplitDetail splits={transaction.splits} currency={transaction.currency} />
        )}
      </div>

      {/* Amount + account name (secondary) */}
      <div className="text-right shrink-0">
        <p
          className={cn(
            'font-mono font-semibold',
            transaction.type === 'INCOME' && 'text-success',
            transaction.type === 'EXPENSE' && 'text-error',
            transaction.type === 'TRANSFER' && 'text-text-primary',
          )}
        >
          {transaction.type === 'INCOME' && '+'}
          {transaction.type === 'EXPENSE' && '-'}
          {/* Reference REQ-9.1: Display transaction amount with base-currency conversion when available */}
          <ConvertedAmount
            amount={transaction.amount}
            currency={transaction.currency}
            convertedAmount={transaction.amountInBaseCurrency}
            baseCurrency={transaction.baseCurrency}
            exchangeRate={transaction.exchangeRate}
            isConverted={transaction.isConverted}
            inline
          />
        </p>
        {/* Account name shown below amount as tertiary info */}
        {(transaction.account?.name || transaction.accountName) && (
          <p className="text-xs text-text-tertiary mt-0.5 truncate max-w-[8rem]">
            {transaction.account?.name ||
              transaction.accountName ||
              `Account #${transaction.accountId}`}
          </p>
        )}
        {/* Payment method shown below the account name */}
        {transaction.paymentMethod && paymentMethodLabel && (
          <span className="mt-0.5 inline-flex items-center justify-end gap-1 text-xs text-text-tertiary">
            {getPaymentMethodIcon(transaction.paymentMethod)}
            <span className="truncate max-w-32">{paymentMethodLabel}</span>
          </span>
        )}
      </div>

      {/* Action buttons */}
      <div
        className={cn(
          'flex items-center gap-1 transition-opacity duration-300 shrink-0',
          isHighlighted ? 'opacity-100' : 'opacity-0 group-hover:opacity-100',
        )}
      >
        <Button
          variant="ghost"
          size="sm"
          onClick={() => onEdit(transaction)}
          className="h-8 w-8 p-0"
          aria-label={t('aria.editTransaction')}
          title={
            transaction.transferId
              ? tl('list.aria.editTransfer')
              : tl('list.aria.editTransaction')
          }
        >
          <Edit2 className="h-4 w-4" />
        </Button>
        <Button
          variant="ghost"
          size="sm"
          onClick={() => onDelete(transaction)}
          className="h-8 w-8 p-0 text-error hover:text-error hover:bg-error/10"
          aria-label={t('aria.deleteTransaction')}
        >
          <Trash2 className="h-4 w-4" />
        </Button>
      </div>
    </div>
  );
}

export function TransactionList({
  transactions,
  onEdit,
  onDelete,
  highlightedId,
  onViewDetail,
  sortDirection = 'desc',
}: TransactionListProps) {
  const { t } = useTranslation('transactions');
  const { data: settings } = useUserSettings();
  // Fetch payees to resolve logos and display names from payee name strings
  const { data: payees = [] } = useActivePayees();

  // Build a name → Payee lookup map for O(1) access in each row
  const payeesMap = new Map<string, Payee>(payees.map((p) => [p.name, p]));

  // Group transactions by date
  const grouped = groupByDate(transactions, settings?.dateFormat);
  const sortedDates = Array.from(grouped.keys()).sort((a, b) => {
    const dateA = new Date(a).getTime();
    const dateB = new Date(b).getTime();
    return sortDirection === 'asc' ? dateA - dateB : dateB - dateA;
  });

  if (transactions.length === 0) {
    return (
      <div className="text-center py-12 text-text-secondary">
        {t('empty.noResults')}
      </div>
    );
  }

  return (
    <div className="space-y-6">
      {sortedDates.map((dateKey) => {
        const dateTransactions = grouped.get(dateKey) || [];
        const renderedTransferIds = new Set<string>();

        return (
          <div key={dateKey}>
            {/* Date group header */}
            <h3 className="text-sm font-semibold text-text-secondary mb-3 sticky top-0 bg-background py-2">
              {dateKey}
            </h3>

            <div className="space-y-2">
              {dateTransactions.map((transaction) => {
                // Handle transfer pairs
                if (transaction.transferId) {
                  if (renderedTransferIds.has(transaction.transferId)) {
                    return null; // already rendered as part of a pair
                  }

                  const pairTransaction = dateTransactions.find(
                    (t) =>
                      t.transferId === transaction.transferId &&
                      t.id !== transaction.id,
                  );

                  if (pairTransaction) {
                    renderedTransferIds.add(transaction.transferId);
                    const sourceTx =
                      transaction.type === 'EXPENSE' ? transaction : pairTransaction;
                    const destTx =
                      transaction.type === 'INCOME' ? transaction : pairTransaction;

                    return (
                      <div
                        key={`transfer-${transaction.transferId}`}
                        className="relative"
                      >
                        <TransactionItem
                          transaction={sourceTx}
                          onEdit={onEdit}
                          onDelete={onDelete}
                          isTransferSource
                          isHighlighted={highlightedId === sourceTx.id}
                          payeesMap={payeesMap}
                          onViewDetail={onViewDetail}
                        />
                        <div className="mt-2">
                          <TransactionItem
                            transaction={destTx}
                            onEdit={onEdit}
                            onDelete={onDelete}
                            isTransferDest
                            showConnector
                            isHighlighted={highlightedId === destTx.id}
                            payeesMap={payeesMap}
                            onViewDetail={onViewDetail}
                          />
                        </div>
                      </div>
                    );
                  }
                }

                // Regular (non-transfer) transaction
                return (
                  <TransactionItem
                    key={transaction.id}
                    transaction={transaction}
                    onEdit={onEdit}
                    onDelete={onDelete}
                    isHighlighted={highlightedId === transaction.id}
                    payeesMap={payeesMap}
                    onViewDetail={onViewDetail}
                  />
                );
              })}
            </div>
          </div>
        );
      })}
    </div>
  );
}
