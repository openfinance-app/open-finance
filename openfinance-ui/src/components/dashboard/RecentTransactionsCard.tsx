import { ArrowUpRight, ArrowDownRight, Calendar, Loader2 } from 'lucide-react';
import { useTranslation } from 'react-i18next';
import { useNavigate } from 'react-router';
import type { Transaction } from '../../types/transaction';
import type { Payee } from '../../types/payee';
import { formatDate } from '../../utils/date';
import { Badge } from '@/components/ui/Badge';
import { ConvertedAmount } from '@/components/ui/ConvertedAmount';
import { useActivePayees } from '@/hooks/usePayees';
import { useUserSettings } from '@/hooks/useUserSettings';

interface RecentTransactionsCardProps {
  transactions: Transaction[];
  /** Human-readable label for the selected period, e.g. "last 30d", "2026-01-01 → 2026-03-03" */
  periodLabel?: string;
  /** Whether transactions are being loaded */
  isLoading?: boolean;
}

// Check if transaction date is in the future
const isFutureDate = (dateString: string) => {
  const transactionDate = new Date(dateString);
  const today = new Date();
  today.setHours(0, 0, 0, 0);
  transactionDate.setHours(0, 0, 0, 0);
  return transactionDate > today;
};

/**
 * MiniPayeeAvatar — compact payee logo or initial avatar for the dashboard row.
 */
function MiniPayeeAvatar({
  payee,
  type,
}: {
  payee: Payee | undefined;
  type: Transaction['type'];
}) {
  const isIncome = type === 'INCOME';
  const isExpense = type === 'EXPENSE';
  const bgColor = isIncome
    ? 'bg-green-500/15 text-green-400 border-green-500/20'
    : isExpense
      ? 'bg-red-500/15 text-red-400 border-red-500/20'
      : 'bg-primary/15 text-primary border-primary/20';
  const TypeIcon = isIncome ? ArrowUpRight : ArrowDownRight;

  if (payee?.logo) {
    return (
      <div className="relative h-8 w-8 shrink-0">
        <img
          src={payee.logo}
          alt={payee.name}
          className="h-8 w-8 rounded-full object-contain bg-surface p-0.5 border border-border"
          onError={(e) => {
            (e.currentTarget as HTMLImageElement).classList.add('hidden');
            const fallback = (e.currentTarget as HTMLImageElement)
              .nextElementSibling as HTMLElement | null;
            if (fallback) fallback.classList.remove('hidden');
          }}
        />
        {/* Fallback initial */}
        <div
          className={`hidden absolute inset-0 h-8 w-8 items-center justify-center rounded-full border text-xs font-semibold ${bgColor}`}
          aria-hidden="true"
        >
          {payee.name.charAt(0).toUpperCase()}
        </div>
      </div>
    );
  }

  if (payee) {
    return (
      <div
        className={`h-8 w-8 shrink-0 flex items-center justify-center rounded-full border text-xs font-semibold ${bgColor}`}
        title={payee.name}
        aria-label={payee.name}
      >
        {payee.name.charAt(0).toUpperCase()}
      </div>
    );
  }

  // No payee — show transaction type icon
  return (
    <div
      className={`h-8 w-8 shrink-0 flex items-center justify-center rounded-full border ${bgColor}`}
    >
      {type !== 'TRANSFER' ? (
        <TypeIcon className="h-4 w-4" />
      ) : (
        <ArrowUpRight className="h-4 w-4" />
      )}
    </div>
  );
}

/**
 * TransactionsCard - Shows transactions for the selected period
 *
 * Design:
 * - Avatar | Description + payee·category | Category | Amount
 * - Amount color-coded: green (income), red (expense)
 * - Link to full transaction list at bottom
 * - Period label shown in subheading
 */
export default function RecentTransactionsCard({
  transactions,
  periodLabel,
  isLoading,
}: RecentTransactionsCardProps) {
  const { t } = useTranslation('dashboard');
  const navigate = useNavigate();
  const { data: settings } = useUserSettings();
  // Fetch payees to resolve logos for avatars
  const { data: payees = [] } = useActivePayees();
  const payeesMap = new Map<string, Payee>(payees.map((p) => [p.name, p]));

  if (isLoading) {
    return (
      <div className="bg-surface rounded-lg p-6 border border-border h-full flex flex-col">
        <h3 className="text-lg font-semibold text-text-primary mb-1">{t('transactions.title')}</h3>
        {periodLabel && <p className="text-xs text-text-secondary mb-4">{periodLabel}</p>}
        <div className="flex-1 flex items-center justify-center">
          <Loader2 className="h-6 w-6 text-text-muted animate-spin" />
        </div>
      </div>
    );
  }

  if (transactions.length === 0) {
    return (
      <div className="bg-surface rounded-lg p-6 border border-border h-full flex flex-col">
        <h3 className="text-lg font-semibold text-text-primary mb-1">{t('transactions.title')}</h3>
        {periodLabel && <p className="text-xs text-text-secondary mb-4">{periodLabel}</p>}
        <div className="text-center py-8">
          <ArrowUpRight className="h-12 w-12 text-text-muted mx-auto mb-3" />
          <p className="text-text-secondary text-sm">{t('transactions.empty.title')}</p>
          <p className="text-text-muted text-xs mt-1">
            {periodLabel
              ? t('transactions.empty.period', { period: periodLabel })
              : t('transactions.empty.first')}
          </p>
        </div>
      </div>
    );
  }

  return (
    <div className="bg-surface rounded-lg p-6 border border-border h-full flex flex-col">
      <div className="flex items-start justify-between mb-4">
        <div>
          <h3 className="text-lg font-semibold text-text-primary leading-tight">
            {t('transactions.title')}
          </h3>
          {periodLabel && (
            <p className="text-xs text-text-secondary mt-0.5">{periodLabel}</p>
          )}
        </div>
        <a
          href="/transactions"
          className="text-xs text-primary hover:text-primary/80 transition-colors shrink-0 mt-1"
        >
          {t('transactions.viewAll')} →
        </a>
      </div>

      <div className="space-y-1 flex-1 overflow-y-auto min-h-0 pr-2 scrollbar-thin">
        {transactions.map((transaction) => {
          const isIncome = transaction.type === 'INCOME';
          const isExpense = transaction.type === 'EXPENSE';
          const amountColor = isIncome
            ? 'text-green-500'
            : isExpense
              ? 'text-red-500'
              : 'text-text-primary';

          // Resolve payee object for logo
          const payeeObj = transaction.payee
            ? payeesMap.get(transaction.payee)
            : undefined;

          // Category metadata
          const categoryName =
            transaction.category?.name || transaction.categoryName;
          const categoryColor =
            transaction.category?.color || transaction.categoryColor;
          const categoryIcon =
            transaction.category?.icon || transaction.categoryIcon;

          return (
            <div
              key={transaction.id}
              role="button"
              tabIndex={0}
              aria-label={t('transactions.viewTransaction')}
              onClick={() => navigate(`/transactions?highlight=${transaction.id}`)}
              onKeyDown={(e) => {
                if (e.key === 'Enter' || e.key === ' ') {
                  e.preventDefault();
                  navigate(`/transactions?highlight=${transaction.id}`);
                }
              }}
              className="flex items-center gap-3 py-2 px-3 rounded-lg hover:bg-surface-elevated transition-colors cursor-pointer"
            >
              {/* Payee avatar / type icon */}
              <MiniPayeeAvatar payee={payeeObj} type={transaction.type} />

              {/* Description + payee · category sub-line */}
              <div className="flex-1 min-w-0">
                <div className="flex items-center gap-1.5 flex-wrap">
                  <div className="text-sm text-text-primary truncate">
                    {transaction.description || transaction.payee || t('common:noDescription')}
                  </div>
                  {isFutureDate(transaction.date) && (
                    <Badge
                      variant="warning"
                      className="shrink-0 flex items-center gap-1"
                      title={t('transactions.future.title', { date: transaction.date })}
                    >
                      <Calendar className="h-3 w-3" />
                      <span className="text-xs">{t('transactions.future.badge')}</span>
                    </Badge>
                  )}
                </div>
                {/* Sub-line: payee · category  */}
                <div className="flex items-center gap-1 mt-0.5 flex-wrap">
                  {transaction.payee && (
                    <span className="text-xs text-text-secondary font-medium truncate max-w-[9rem]">
                      {transaction.payee}
                    </span>
                  )}
                  {transaction.payee && categoryName && (
                    <span className="text-text-muted text-xs" aria-hidden="true">
                      ·
                    </span>
                  )}
                  {categoryName && (
                    <span className="inline-flex items-center gap-1 text-xs text-text-secondary">
                      {categoryIcon ? (
                        <span className="text-sm leading-none" aria-hidden="true">
                          {categoryIcon}
                        </span>
                      ) : (
                        <span
                          className="inline-block h-1.5 w-1.5 rounded-full shrink-0"
                          style={{
                            backgroundColor:
                              categoryColor || 'var(--color-primary, #6366f1)',
                          }}
                          aria-hidden="true"
                        />
                      )}
                      {categoryName}
                    </span>
                  )}
                </div>
              </div>

              {/* Date */}
              <div className="hidden sm:block shrink-0 w-16 text-right">
                <div className="text-xs text-text-muted">
                  {formatDate(transaction.date, settings?.dateFormat)}
                </div>
              </div>

              {/* Amount */}
              <div className="flex items-center gap-0.5 shrink-0">
                <span className={`text-sm font-semibold font-mono ${amountColor}`}>
                  {isExpense && '-'}
                  {isIncome && '+'}
                </span>
                <ConvertedAmount
                  inline
                  amount={transaction.amount}
                  currency={transaction.currency}
                  convertedAmount={transaction.amountInBaseCurrency}
                  baseCurrency={transaction.baseCurrency}
                  exchangeRate={transaction.exchangeRate}
                  isConverted={transaction.isConverted}
                  className={`text-sm font-semibold font-mono ${amountColor}`}
                />
              </div>
            </div>
          );
        })}
      </div>
    </div>
  );
}
