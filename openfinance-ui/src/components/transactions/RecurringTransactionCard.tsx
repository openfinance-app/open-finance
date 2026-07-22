/**
 * RecurringTransactionCard Component
 * Task 12.2.10: Create RecurringTransactionCard component
 * 
 * Displays individual recurring transaction with frequency, next occurrence, and pause/resume functionality
 */
import { Calendar, Pause, Play, Edit, Trash2, TrendingUp, TrendingDown, ArrowRightLeft } from 'lucide-react';
import { formatDistanceToNow, format } from 'date-fns';
import { useTranslation } from 'react-i18next';
import { Card } from '@/components/ui/Card';
import { Button } from '@/components/ui/Button';
import { Badge } from '@/components/ui/Badge';
import { ConvertedAmount } from '@/components/ui/ConvertedAmount';
import { useAuthContext } from '@/context/AuthContext';
import { useLatestExchangeRate } from '@/hooks/useCurrency';
import { useLocale } from '@/context/LocaleContext';
import { multiply } from '@/utils/money';
import type { RecurringTransaction } from '@/types/recurringTransaction';

interface RecurringTransactionCardProps {
  recurringTransaction: RecurringTransaction;
  onEdit?: (recurringTransaction: RecurringTransaction) => void;
  onDelete?: (recurringTransaction: RecurringTransaction) => void;
  onPause?: (id: number) => void;
  onResume?: (id: number) => void;
  onViewDetail?: (recurringTransaction: RecurringTransaction) => void;
  isPausing?: boolean;
  isResuming?: boolean;
}

export function RecurringTransactionCard({
  recurringTransaction,
  onEdit,
  onDelete,
  onPause,
  onResume,
  onViewDetail,
  isPausing = false,
  isResuming = false,
}: RecurringTransactionCardProps) {
  const { baseCurrency } = useAuthContext();
  const { t } = useTranslation('recurring');
  const { dateFnsLocale } = useLocale();

  const getStatusText = () => {
    if (recurringTransaction.isEnded) return t('status.ended');
    if (!recurringTransaction.isActive) return t('status.paused');
    if (recurringTransaction.isDue) return t('status.dueNow');
    if (recurringTransaction.daysUntilNext <= 7 && recurringTransaction.daysUntilNext >= 0) return t('status.dueSoon');
    return t('status.active');
  };

  // Fetch live exchange rate for currency → baseCurrency conversion
  const isSameCurrency = recurringTransaction.currency === baseCurrency;
  const { data: exchangeRateData } = useLatestExchangeRate(
    recurringTransaction.currency,
    baseCurrency
  );
  const exchangeRate = isSameCurrency ? 1 : (exchangeRateData?.rate ?? undefined);
  const convertedAmount = exchangeRate !== undefined
    ? multiply(recurringTransaction.amount, exchangeRate)
    : undefined;
  const isConverted = isSameCurrency || (exchangeRate !== undefined && convertedAmount !== undefined);

  const isTransfer = recurringTransaction.type === 'TRANSFER';
  const isIncome = recurringTransaction.type === 'INCOME';
  const isExpense = recurringTransaction.type === 'EXPENSE';

  const getTypeIcon = () => {
    if (isTransfer) return <ArrowRightLeft className="h-5 w-5" />;
    if (isIncome) return <TrendingUp className="h-5 w-5 text-green-500" />;
    return <TrendingDown className="h-5 w-5 text-red-500" />;
  };

  const getAmountColor = () => {
    if (isIncome) return 'text-green-500';
    if (isExpense) return 'text-red-500';
    return 'text-blue-500';
  };

  const formatNextOccurrence = () => {
    try {
      const date = new Date(recurringTransaction.nextOccurrence);
      return formatDistanceToNow(date, { addSuffix: true, locale: dateFnsLocale });
    } catch {
      return recurringTransaction.nextOccurrence;
    }
  };

  return (
    <Card className="p-4 hover:shadow-md transition-shadow">
      <div
        className="flex items-start justify-between gap-4 cursor-pointer"
        onClick={(e) => {
          if ((e.target as HTMLElement).closest('button')) return;
          onViewDetail?.(recurringTransaction);
        }}
      >
        {/* Left: Icon and Details */}
        <div className="flex items-start gap-3 flex-1 min-w-0">
          <div className="mt-1 flex-shrink-0">{getTypeIcon()}</div>

          <div className="flex-1 min-w-0">
            {/* Description */}
            <h3 className="font-semibold text-base truncate">{recurringTransaction.description}</h3>

            {/* Account and Category Info */}
            <div className="flex flex-wrap items-center gap-2 mt-1 text-sm text-muted-foreground">
              <span className="truncate">{recurringTransaction.accountName}</span>
              {isTransfer && recurringTransaction.toAccountName && (
                <>
                  <ArrowRightLeft className="h-3 w-3" />
                  <span className="truncate">{recurringTransaction.toAccountName}</span>
                </>
              )}
              {recurringTransaction.categoryName && (
                <>
                  <span>•</span>
                  <span className="truncate">{recurringTransaction.categoryName}</span>
                </>
              )}
            </div>

            {/* Frequency and Next Occurrence */}
            <div className="flex flex-wrap items-center gap-2 mt-2">
              <Badge variant="default">
                {t('filters.' + recurringTransaction.frequency.toLowerCase())}
              </Badge>
              <Badge variant="info">
                {getStatusText()}
              </Badge>
              {!recurringTransaction.isEnded && (
                <span className="text-xs text-muted-foreground flex items-center gap-1">
                  <Calendar className="h-3 w-3" />
                  <span>{t('card.next', { date: formatNextOccurrence() })}</span>
                </span>
              )}
            </div>

            {/* End Date */}
            {recurringTransaction.endDate && (
              <div className="text-xs text-muted-foreground mt-1">
                {recurringTransaction.isEnded
                  ? t('card.endedOn', { date: format(new Date(recurringTransaction.endDate), 'PP', { locale: dateFnsLocale }) })
                  : t('card.endsOn', { date: format(new Date(recurringTransaction.endDate), 'PP', { locale: dateFnsLocale }) })}
              </div>
            )}

            {/* Notes (if present) */}
            {recurringTransaction.notes && (
              <p className="text-xs text-muted-foreground mt-2 line-clamp-2">{recurringTransaction.notes}</p>
            )}
          </div>
        </div>

        {/* Right: Amount and Actions */}
        <div className="flex flex-col items-end gap-2 flex-shrink-0">
          {/* Amount */}
          <div className={`text-lg font-semibold ${getAmountColor()}`}>
            {isIncome && '+'}
            {isExpense && '-'}
            <ConvertedAmount
              amount={recurringTransaction.amount}
              currency={recurringTransaction.currency}
              baseCurrency={baseCurrency}
              convertedAmount={convertedAmount}
              exchangeRate={exchangeRate}
              isConverted={isConverted}
              inline
            />
          </div>

          {/* Action Buttons */}
          <div className="flex items-center gap-1">
            {/* Pause/Resume Button */}
            {!recurringTransaction.isEnded && (
              <>
                {recurringTransaction.isActive ? (
                  <Button
                    variant="ghost"
                    size="sm"
                    onClick={() => onPause?.(recurringTransaction.id)}
                    disabled={isPausing}
                    title={t('card.aria.pause')}
                  >
                    <Pause className="h-4 w-4" />
                  </Button>
                ) : (
                  <Button
                    variant="ghost"
                    size="sm"
                    onClick={() => onResume?.(recurringTransaction.id)}
                    disabled={isResuming}
                    title={t('card.aria.resume')}
                  >
                    <Play className="h-4 w-4" />
                  </Button>
                )}
              </>
            )}

            {/* Edit Button */}
            <Button
              variant="ghost"
              size="sm"
              onClick={() => onEdit?.(recurringTransaction)}
              title={t('card.aria.edit')}
            >
              <Edit className="h-4 w-4" />
            </Button>

            {/* Delete Button */}
            <Button
              variant="ghost"
              size="sm"
              onClick={() => onDelete?.(recurringTransaction)}
              title={t('card.aria.delete')}
              className="text-destructive hover:text-destructive"
            >
              <Trash2 className="h-4 w-4" />
            </Button>
          </div>
        </div>
      </div>
    </Card>
  );
}
