/**
 * RecurringTransactionDetailModal Component
 *
 * Modal displaying detailed information for a recurring transaction with
 * tabbed sections:
 * - Overview: all fields (description, payee, category, frequency, schedule)
 * - Attachments: file attachments for the recurring transaction
 *
 * Follows the same hand-rolled overlay pattern as AssetDetailModal /
 * AccountDetailModal.
 */
import { useState, useEffect } from 'react';
import { useTranslation } from 'react-i18next';
import {
  X,
  ArrowUpRight,
  ArrowDownLeft,
  ArrowRightLeft,
  Calendar,
  Paperclip,
  Edit,
} from 'lucide-react';
import { format, formatDistanceToNow } from 'date-fns';
import { Button } from '@/components/ui/Button';
import { Badge } from '@/components/ui/Badge';
import { AttachmentList, AttachmentUpload } from '@/components/attachments';
import { AttachmentEntityType } from '@/types/attachment';
import { ConvertedAmount } from '@/components/ui/ConvertedAmount';
import { useAuthContext } from '@/context/AuthContext';
import { useLatestExchangeRate } from '@/hooks/useCurrency';
import { useLocale } from '@/context/LocaleContext';
import { cn } from '@/lib/utils';
import { multiply } from '@/utils/money';
import type { RecurringTransaction } from '@/types/recurringTransaction';

// ─── Props ────────────────────────────────────────────────────────────────────

interface RecurringTransactionDetailModalProps {
  recurringTransaction: RecurringTransaction;
  onClose: () => void;
  onEdit?: (recurringTransaction: RecurringTransaction) => void;
}

// ─── Tab type ─────────────────────────────────────────────────────────────────

type Tab = 'overview' | 'attachments';

// ─── Helpers ─────────────────────────────────────────────────────────────────

const TYPE_CONFIG = {
  INCOME: {
    label: 'Income',
    icon: <ArrowDownLeft className="h-5 w-5" />,
    color: 'text-success',
    bg: 'bg-success/10',
    sign: '+',
  },
  EXPENSE: {
    label: 'Expense',
    icon: <ArrowUpRight className="h-5 w-5" />,
    color: 'text-error',
    bg: 'bg-error/10',
    sign: '-',
  },
  TRANSFER: {
    label: 'Transfer',
    icon: <ArrowRightLeft className="h-5 w-5" />,
    color: 'text-primary',
    bg: 'bg-primary/10',
    sign: '',
  },
} as const;

// ─── Component ────────────────────────────────────────────────────────────────

export function RecurringTransactionDetailModal({
  recurringTransaction,
  onClose,
  onEdit,
}: RecurringTransactionDetailModalProps) {
  const { t } = useTranslation('common');
  const { t: tr } = useTranslation('recurring');
  const [activeTab, setActiveTab] = useState<Tab>('overview');
  const { baseCurrency } = useAuthContext();
  const { dateFnsLocale } = useLocale();

  const isSameCurrency = recurringTransaction.currency === baseCurrency;
  const { data: exchangeRateData } = useLatestExchangeRate(
    recurringTransaction.currency,
    baseCurrency,
  );
  const exchangeRate = isSameCurrency ? 1 : (exchangeRateData?.rate ?? undefined);
  const convertedAmount =
    exchangeRate !== undefined ? multiply(recurringTransaction.amount, exchangeRate) : undefined;
  const isConverted =
    isSameCurrency || (exchangeRate !== undefined && convertedAmount !== undefined);

  // Close on Escape
  useEffect(() => {
    const handler = (e: KeyboardEvent) => {
      if (e.key === 'Escape') onClose();
    };
    window.addEventListener('keydown', handler);
    return () => window.removeEventListener('keydown', handler);
  }, [onClose]);

  const typeConfig = TYPE_CONFIG[recurringTransaction.type];
  const statusText = (() => {
    if (recurringTransaction.isEnded) return tr('status.ended');
    if (!recurringTransaction.isActive) return tr('status.paused');
    if (recurringTransaction.isDue) return tr('status.dueNow');
    if (recurringTransaction.daysUntilNext <= 7 && recurringTransaction.daysUntilNext >= 0) return tr('status.dueSoon');
    return tr('status.active');
  })();

  const formatNextOccurrence = () => {
    try {
      const date = new Date(recurringTransaction.nextOccurrence);
      return `${format(date, 'PP', { locale: dateFnsLocale })} (${formatDistanceToNow(date, { addSuffix: true, locale: dateFnsLocale })})`;
    } catch {
      return recurringTransaction.nextOccurrence;
    }
  };

  const tabs: { key: Tab; label: string }[] = [
    { key: 'overview', label: tr('detail.tabs.overview') },
    { key: 'attachments', label: tr('detail.tabs.attachments') },
  ];

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center">
      {/* Backdrop */}
      <div
        className="absolute inset-0 bg-background/80 backdrop-blur-sm"
        onClick={onClose}
      />

      {/* Modal panel */}
      <div className="relative bg-surface border border-border rounded-lg shadow-lg max-w-2xl w-full max-h-[90vh] overflow-y-auto m-4">
        {/* ── Header ── */}
        <div className="sticky top-0 z-10 bg-surface border-b border-border px-6 py-4 flex items-center justify-between">
          <div className="flex items-center gap-3">
            <div
              className={cn(
                'flex h-10 w-10 items-center justify-center rounded-xl shrink-0',
                typeConfig.bg,
                typeConfig.color,
              )}
            >
              {typeConfig.icon}
            </div>
            <div>
              <h2 className="text-xl font-bold text-foreground">
                {recurringTransaction.description}
              </h2>
              <p className="text-sm text-muted-foreground">
                {tr('types.' + recurringTransaction.type)} · {tr('filters.' + recurringTransaction.frequency.toLowerCase())}
                {recurringTransaction.payee && ` · ${recurringTransaction.payee}`}
              </p>
            </div>
          </div>

          <div className="flex items-center gap-2">
            {onEdit && (
              <Button
                variant="secondary"
                size="sm"
                onClick={() => {
                  onClose();
                  onEdit(recurringTransaction);
                }}
                className="hidden sm:flex"
              >
                <Edit className="h-4 w-4 mr-1.5" />
                {tr('detail.edit')}
              </Button>
            )}
            <button
              onClick={onClose}
              className="p-2 rounded-lg hover:bg-background transition-colors"
              aria-label={t('aria.closeModal')}
            >
              <X className="h-5 w-5 text-muted-foreground" />
            </button>
          </div>
        </div>

        {/* ── Content ── */}
        <div className="p-6">
          {/* Amount hero */}
          <div className="mb-6 p-4 bg-background border border-border rounded-lg flex items-center justify-between">
            <div>
              <p className="text-sm text-muted-foreground mb-1">{tr('detail.amountLabel')}</p>
              <div className={cn('text-3xl font-bold font-mono', typeConfig.color)}>
                {typeConfig.sign}
                <ConvertedAmount
                  amount={recurringTransaction.amount}
                  currency={recurringTransaction.currency}
                  baseCurrency={baseCurrency}
                  convertedAmount={convertedAmount}
                  exchangeRate={exchangeRate}
                  isConverted={isConverted}
                />
              </div>
            </div>
            <div className="flex gap-2 flex-wrap justify-end">
              <Badge
                variant={
                  recurringTransaction.isEnded
                    ? 'default'
                    : recurringTransaction.isActive
                      ? 'success'
                      : 'info'
                }
              >
                {statusText}
              </Badge>
            </div>
          </div>

          {/* Tabs */}
          <div className="border-b border-border mb-6">
            <div className="flex gap-4">
              {tabs.map((tab) => (
                <button
                  key={tab.key}
                  onClick={() => setActiveTab(tab.key)}
                  className={cn(
                    'px-4 py-2 text-sm font-medium border-b-2 transition-colors',
                    activeTab === tab.key
                      ? 'border-primary text-primary'
                      : 'border-transparent text-muted-foreground hover:text-foreground',
                  )}
                >
                  {tab.key === 'attachments' ? (
                    <span className="inline-flex items-center gap-1.5">
                      <Paperclip className="h-3.5 w-3.5" />
                      {tab.label}
                    </span>
                  ) : (
                    tab.label
                  )}
                </button>
              ))}
            </div>
          </div>

          {/* ── Overview Tab ── */}
          {activeTab === 'overview' && (
            <div className="space-y-6">
              {/* Schedule section */}
              <div className="bg-background border border-border rounded-lg p-6">
                <h3 className="text-base font-semibold text-foreground mb-4 flex items-center gap-2">
                  <Calendar className="h-4 w-4 text-muted-foreground" />
                  {tr('detail.sections.schedule')}
                </h3>
                <dl className="grid grid-cols-1 sm:grid-cols-2 gap-4">
                  <div>
                    <dt className="text-xs text-muted-foreground uppercase tracking-wide mb-1">
                      {tr('detail.fields.frequency')}
                    </dt>
                    <dd className="text-sm font-medium text-foreground">
                      {tr('filters.' + recurringTransaction.frequency.toLowerCase())}
                    </dd>
                  </div>
                  {!recurringTransaction.isEnded && (
                    <div>
                      <dt className="text-xs text-muted-foreground uppercase tracking-wide mb-1">
                        {tr('detail.fields.nextOccurrence')}
                      </dt>
                      <dd className="text-sm font-medium text-foreground">
                        {formatNextOccurrence()}
                      </dd>
                    </div>
                  )}
                  {recurringTransaction.endDate && (
                    <div>
                      <dt className="text-xs text-muted-foreground uppercase tracking-wide mb-1">
                        {recurringTransaction.isEnded ? tr('detail.fields.endedOn') : tr('detail.fields.endDate')}
                      </dt>
                      <dd className="text-sm font-medium text-foreground">
                        {format(new Date(recurringTransaction.endDate), 'PP', { locale: dateFnsLocale })}
                      </dd>
                    </div>
                  )}
                  <div>
                    <dt className="text-xs text-muted-foreground uppercase tracking-wide mb-1">
                      {tr('detail.fields.created')}
                    </dt>
                    <dd className="text-sm font-medium text-foreground">
                      {format(new Date(recurringTransaction.createdAt), 'PP', { locale: dateFnsLocale })}
                    </dd>
                  </div>
                </dl>
              </div>

              {/* Details section */}
              <div className="bg-background border border-border rounded-lg p-6">
                <h3 className="text-base font-semibold text-foreground mb-4">{tr('detail.sections.details')}</h3>
                <dl className="grid grid-cols-1 sm:grid-cols-2 gap-4">
                  <div>
                    <dt className="text-xs text-muted-foreground uppercase tracking-wide mb-1">
                      {tr('detail.fields.type')}
                    </dt>
                    <dd className="text-sm font-medium text-foreground">
                      {tr('types.' + recurringTransaction.type)}
                    </dd>
                  </div>
                  <div>
                    <dt className="text-xs text-muted-foreground uppercase tracking-wide mb-1">
                      {tr('detail.fields.account')}
                    </dt>
                    <dd className="text-sm font-medium text-foreground">
                      {recurringTransaction.accountName}
                    </dd>
                  </div>
                  {recurringTransaction.type === 'TRANSFER' &&
                    recurringTransaction.toAccountName && (
                      <div>
                        <dt className="text-xs text-muted-foreground uppercase tracking-wide mb-1">
                          {tr('detail.fields.toAccount')}
                        </dt>
                        <dd className="text-sm font-medium text-foreground">
                          {recurringTransaction.toAccountName}
                        </dd>
                      </div>
                    )}
                  {recurringTransaction.payee && (
                    <div>
                      <dt className="text-xs text-muted-foreground uppercase tracking-wide mb-1">
                        {tr('detail.fields.payee')}
                      </dt>
                      <dd className="text-sm font-medium text-foreground">
                        {recurringTransaction.payee}
                      </dd>
                    </div>
                  )}
                  {recurringTransaction.categoryName && (
                    <div>
                      <dt className="text-xs text-muted-foreground uppercase tracking-wide mb-1">
                        {tr('detail.fields.category')}
                      </dt>
                      <dd className="text-sm font-medium text-foreground">
                        {recurringTransaction.categoryIcon && (
                          <span className="mr-1">{recurringTransaction.categoryIcon}</span>
                        )}
                        {recurringTransaction.categoryName}
                      </dd>
                    </div>
                  )}
                  <div>
                    <dt className="text-xs text-muted-foreground uppercase tracking-wide mb-1">
                      {tr('detail.fields.currency')}
                    </dt>
                    <dd className="text-sm font-medium text-foreground">
                      {recurringTransaction.currency}
                    </dd>
                  </div>
                </dl>
              </div>

              {/* Notes */}
              {recurringTransaction.notes && (
                <div className="bg-background border border-border rounded-lg p-6">
                  <h3 className="text-base font-semibold text-foreground mb-3">{tr('detail.sections.notes')}</h3>
                  <p className="text-sm text-foreground whitespace-pre-wrap">
                    {recurringTransaction.notes}
                  </p>
                </div>
              )}
            </div>
          )}

          {/* ── Attachments Tab ── */}
          {activeTab === 'attachments' && (
            <div className="space-y-4">
              <AttachmentList
                entityType={AttachmentEntityType.RECURRING_TRANSACTION}
                entityId={recurringTransaction.id}
              />
              <AttachmentUpload
                entityType={AttachmentEntityType.RECURRING_TRANSACTION}
                entityId={recurringTransaction.id}
              />
            </div>
          )}
        </div>
      </div>
    </div>
  );
}
