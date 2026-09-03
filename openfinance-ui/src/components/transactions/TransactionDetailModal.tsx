/**
 * TransactionDetailModal Component
 *
 * Modal displaying detailed information for a single transaction with
 * tabbed sections:
 * - Overview: all transaction fields, tags, notes
 * - Splits: split lines (shown only when hasSplits is true)
 * - Attachments: file attachments for the transaction
 *
 * Follows the same hand-rolled overlay pattern as AssetDetailModal /
 * AccountDetailModal.
 */
import { useState, useEffect } from 'react';
import { useTranslation } from 'react-i18next';
import { useNavigate } from 'react-router';
import {
  X,
  ArrowUpRight,
  ArrowDownLeft,
  ArrowRightLeft,
  Paperclip,
  Scissors,
  Receipt,
} from 'lucide-react';
import { format } from 'date-fns';
import { Button } from '@/components/ui/Button';
import { Badge } from '@/components/ui/Badge';
import { AttachmentList, AttachmentUpload } from '@/components/attachments';
import { AttachmentEntityType } from '@/types/attachment';
import { ConvertedAmount } from '@/components/ui/ConvertedAmount';
import { SplitDetail } from './SplitDetail';
import { useFormatCurrency } from '@/hooks/useFormatCurrency';
import { cn } from '@/lib/utils';
import type { Transaction } from '@/types/transaction';

// ─── Props ────────────────────────────────────────────────────────────────────

interface TransactionDetailModalProps {
  transaction: Transaction;
  onClose: () => void;
  onEdit?: (transaction: Transaction) => void;
}

// ─── Tab type ─────────────────────────────────────────────────────────────────

type Tab = 'overview' | 'splits' | 'attachments';

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

export function TransactionDetailModal({
  transaction,
  onClose,
  onEdit,
}: TransactionDetailModalProps) {
  const [activeTab, setActiveTab] = useState<Tab>('overview');
  const navigate = useNavigate();
  const { format: formatCurrency } = useFormatCurrency();
  const { t: tc } = useTranslation('common');
  const { t } = useTranslation('transactions');

  const handleAccountClick = () => {
    if (!transaction.accountId) return;
    onClose();
    navigate(`/accounts?highlight=${transaction.accountId}`);
  };

  // Close on Escape
  useEffect(() => {
    const handler = (e: KeyboardEvent) => {
      if (e.key === 'Escape') onClose();
    };
    window.addEventListener('keydown', handler);
    return () => window.removeEventListener('keydown', handler);
  }, [onClose]);

  const typeConfig = TYPE_CONFIG[transaction.type];

  // Resolve tags to an array regardless of server format
  const tags: string[] = Array.isArray(transaction.tags)
    ? transaction.tags
    : typeof transaction.tags === 'string'
      ? (transaction.tags as string)
          .split(',')
          .map((t: string) => t.trim())
          .filter(Boolean)
      : [];

  const tabs: { key: Tab; label: string }[] = [
    { key: 'overview', label: t('detail.tabs.overview') },
    ...(transaction.hasSplits ? [{ key: 'splits' as Tab, label: t('detail.tabs.splits') }] : []),
    { key: 'attachments', label: t('detail.tabs.attachments') },
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
                {transaction.description || transaction.payee || 'Transaction'}
              </h2>
              <p className="text-sm text-muted-foreground">
                {t('form.types.' + transaction.type)}
                {transaction.payee && ` · ${transaction.payee}`}
                {' · '}
                {format(new Date(transaction.date), 'MMMM d, yyyy')}
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
                  onEdit(transaction);
                }}
                className="hidden sm:flex"
              >
                <Receipt className="h-4 w-4 mr-1.5" />
                {t('detail.edit')}
              </Button>
            )}
            <button
              onClick={onClose}
              className="p-2 rounded-lg hover:bg-background transition-colors"
              aria-label={tc('aria.closeModal')}
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
              <p className="text-sm text-muted-foreground mb-1">{t('detail.amountLabel')}</p>
              <p
                className={cn(
                  'text-3xl font-bold font-mono',
                  typeConfig.color,
                )}
              >
                {typeConfig.sign}
                <ConvertedAmount
                  amount={transaction.amount}
                  currency={transaction.currency}
                  convertedAmount={transaction.amountInBaseCurrency}
                  baseCurrency={transaction.baseCurrency}
                  exchangeRate={transaction.exchangeRate}
                  isConverted={transaction.isConverted}
                />
              </p>
            </div>
            <div className="flex gap-2 flex-wrap justify-end">
              {transaction.isReconciled && (
                <Badge variant="success">{t('detail.badges.reconciled')}</Badge>
              )}
              {transaction.hasSplits && (
                <Badge variant="info" className="inline-flex items-center gap-1">
                  <Scissors className="h-3 w-3" />
                  {t('detail.badges.split')}
                </Badge>
              )}
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
              {/* Core fields */}
              <div className="bg-background border border-border rounded-lg p-6">
                <h3 className="text-base font-semibold text-foreground mb-4">{t('detail.sections.details')}</h3>
                <dl className="grid grid-cols-1 sm:grid-cols-2 gap-4">
                  <div>
                    <dt className="text-xs text-muted-foreground uppercase tracking-wide mb-1">
                      {t('detail.fields.date')}
                    </dt>
                    <dd className="text-sm font-medium text-foreground">
                      {format(new Date(transaction.date), 'MMMM d, yyyy')}
                    </dd>
                  </div>
                  <div>
                    <dt className="text-xs text-muted-foreground uppercase tracking-wide mb-1">
                      {t('detail.fields.type')}
                    </dt>
                    <dd className="text-sm font-medium text-foreground">
                      {t('form.types.' + transaction.type)}
                    </dd>
                  </div>
                  <div>
                    <dt className="text-xs text-muted-foreground uppercase tracking-wide mb-1">
                      {t('detail.fields.account')}
                    </dt>
                    <dd className="text-sm font-medium text-foreground">
                      <button
                        type="button"
                        onClick={handleAccountClick}
                        className="text-primary hover:underline focus:outline-none"
                        aria-label={t('detail.aria.viewAccount')}
                      >
                        {transaction.account?.name ||
                          transaction.accountName ||
                          `Account #${transaction.accountId}`}
                      </button>
                    </dd>
                  </div>
                  {transaction.type === 'TRANSFER' &&
                    (transaction.toAccount?.name || transaction.toAccountName) && (
                      <div>
                        <dt className="text-xs text-muted-foreground uppercase tracking-wide mb-1">
                          {t('detail.fields.toAccount')}
                        </dt>
                        <dd className="text-sm font-medium text-foreground">
                          {transaction.toAccount?.name || transaction.toAccountName}
                        </dd>
                      </div>
                    )}
                  {transaction.payee && (
                    <div>
                      <dt className="text-xs text-muted-foreground uppercase tracking-wide mb-1">
                        {t('detail.fields.payee')}
                      </dt>
                      <dd className="text-sm font-medium text-foreground">
                        {transaction.payee}
                      </dd>
                    </div>
                  )}
                  {transaction.description && (
                    <div className="sm:col-span-2">
                      <dt className="text-xs text-muted-foreground uppercase tracking-wide mb-1">
                        {t('detail.fields.description')}
                      </dt>
                      <dd className="text-sm font-medium text-foreground">
                        {transaction.description}
                      </dd>
                    </div>
                  )}
                  {(transaction.category?.name || transaction.categoryName) && (
                    <div>
                      <dt className="text-xs text-muted-foreground uppercase tracking-wide mb-1">
                        {t('detail.fields.category')}
                      </dt>
                      <dd className="text-sm font-medium text-foreground">
                        {transaction.category?.icon && (
                          <span className="mr-1">{transaction.category.icon}</span>
                        )}
                        {transaction.category?.name || transaction.categoryName}
                      </dd>
                    </div>
                  )}
                  {transaction.paymentMethod && (
                    <div>
                      <dt className="text-xs text-muted-foreground uppercase tracking-wide mb-1">
                        {t('detail.fields.paymentMethod')}
                      </dt>
                      <dd className="text-sm font-medium text-foreground">
                        {t('form.paymentMethods.' + transaction.paymentMethod, { defaultValue: transaction.paymentMethod })}
                      </dd>
                    </div>
                  )}
                  {transaction.currency && (
                    <div>
                      <dt className="text-xs text-muted-foreground uppercase tracking-wide mb-1">
                        {t('detail.fields.currency')}
                      </dt>
                      <dd className="text-sm font-medium text-foreground">
                        {transaction.currency}
                        {transaction.isConverted && transaction.exchangeRate && (
                           <span className="ml-2 text-xs text-muted-foreground">
                             (1 {transaction.currency} ={' '}
                             {formatCurrency(
                               transaction.exchangeRate,
                               transaction.baseCurrency ?? transaction.currency,
                             )}
                             )
                           </span>
                        )}
                      </dd>
                    </div>
                  )}
                </dl>
              </div>

              {/* Notes */}
              {transaction.notes && (
                <div className="bg-background border border-border rounded-lg p-6">
                  <h3 className="text-base font-semibold text-foreground mb-3">{t('detail.sections.notes')}</h3>
                  <p className="text-sm text-foreground whitespace-pre-wrap">
                    {transaction.notes}
                  </p>
                </div>
              )}

              {/* Tags */}
              {tags.length > 0 && (
                <div className="bg-background border border-border rounded-lg p-6">
                  <h3 className="text-base font-semibold text-foreground mb-3">{t('detail.sections.tags')}</h3>
                  <div className="flex flex-wrap gap-2">
                    {tags.map((tag, i) => (
                      <Badge key={i} variant="info">
                        {tag}
                      </Badge>
                    ))}
                  </div>
                </div>
              )}
            </div>
          )}

          {/* ── Splits Tab ── */}
          {activeTab === 'splits' && transaction.hasSplits && (
            <div className="bg-background border border-border rounded-lg p-6">
              <h3 className="text-base font-semibold text-foreground mb-4">{t('detail.sections.splitLines')}</h3>
              {transaction.splits && transaction.splits.length > 0 ? (
                <SplitDetail splits={transaction.splits} currency={transaction.currency} />
              ) : (
                <p className="text-sm text-muted-foreground">
                  {t('splitDetail.noSplitDetails')}
                </p>
              )}
            </div>
          )}

          {/* ── Attachments Tab ── */}
          {activeTab === 'attachments' && (
            <div className="space-y-4">
              <AttachmentList
                entityType={AttachmentEntityType.TRANSACTION}
                entityId={transaction.id}
              />
              <AttachmentUpload
                entityType={AttachmentEntityType.TRANSACTION}
                entityId={transaction.id}
              />
            </div>
          )}
        </div>
      </div>
    </div>
  );
}
