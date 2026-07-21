/**
 * AccountDetailModal Component
 *
 * Modal displaying detailed account information with tabbed sections:
 * - Overview: balance history chart + recent transactions
 * - Interest: interest rate variations (when enabled)
 * - Attachments: file attachments for the account
 *
 * Follows the same hand-rolled overlay pattern as AssetDetailModal.
 */
import { useState, useEffect } from 'react';
import { useTranslation } from 'react-i18next';
import {
  X,
  Edit2,
  Wallet,
  CreditCard,
  PiggyBank,
  TrendingUp,
  Banknote,
  Package,
  ArrowUpRight,
} from 'lucide-react';
import { useNavigate } from 'react-router';
import {
  LineChart,
  Line,
  XAxis,
  YAxis,
  CartesianGrid,
  Tooltip,
  ResponsiveContainer,
} from 'recharts';
import { format } from 'date-fns';
import { Button } from '@/components/ui/Button';
import { LoadingSkeleton } from '@/components/LoadingComponents';
import { Dialog, DialogContent, DialogHeader, DialogTitle } from '@/components/ui/Dialog';
import { AttachmentList, AttachmentUpload } from '@/components/attachments';
import { AttachmentEntityType } from '@/types/attachment';
import { ConvertedAmount } from '@/components/ui/ConvertedAmount';
import { InterestRateVariationsSection } from './InterestRateVariationsSection';
import { AccountForm } from './AccountForm';
import {
  useAccount,
  useAccountBalanceHistory,
  useUpdateAccount,
} from '@/hooks/useAccounts';
import { useTransactions } from '@/hooks/useTransactions';
import { useFormatCurrency } from '@/hooks/useFormatCurrency';
import { DEFAULT_CURRENCY } from '@/utils/currency';
import { cn } from '@/lib/utils';
import type { AccountType, AccountRequest } from '@/types/account';

// ─── Icon + name maps ───────────────────────────────────────────────────────

const accountTypeIcons: Record<AccountType, React.ReactNode> = {
  CHECKING: <Wallet className="h-5 w-5" />,
  SAVINGS: <PiggyBank className="h-5 w-5" />,
  CREDIT_CARD: <CreditCard className="h-5 w-5" />,
  INVESTMENT: <TrendingUp className="h-5 w-5" />,
  CASH: <Banknote className="h-5 w-5" />,
  OTHER: <Package className="h-5 w-5" />,
};

const periodOptions = [
  { value: '1M', label: '1M' },
  { value: '3M', label: '3M' },
  { value: '6M', label: '6M' },
  { value: '1Y', label: '1Y' },
  { value: 'ALL', label: 'All' },
];

// ─── Props ───────────────────────────────────────────────────────────────────

interface AccountDetailModalProps {
  accountId: number;
  onClose: () => void;
  onEdit?: () => void;
}

// ─── Component ───────────────────────────────────────────────────────────────

type Tab = 'overview' | 'interest' | 'attachments';

export function AccountDetailModal({
  accountId,
  onClose,
  onEdit,
}: AccountDetailModalProps) {
  const navigate = useNavigate();
  const [period, setPeriod] = useState('3M');
  const [activeTab, setActiveTab] = useState<Tab>('overview');
  const [isEditModalOpen, setIsEditModalOpen] = useState(false);
  const { format: formatCurrency } = useFormatCurrency();
  const { t: tc } = useTranslation('common');
  const { t } = useTranslation('accounts');

  const { data: account, isLoading: isLoadingAccount } = useAccount(accountId);
  const updateAccount = useUpdateAccount();
  const { data: balanceHistory, isLoading: isLoadingHistory } =
    useAccountBalanceHistory(accountId, period);
  const { data: transactionsData, isLoading: isLoadingTransactions } =
    useTransactions({ accountId, size: 10, sort: 'date,desc' });

  // Close on Escape
  useEffect(() => {
    const handler = (e: KeyboardEvent) => {
      if (e.key === 'Escape' && !isEditModalOpen) onClose();
    };
    window.addEventListener('keydown', handler);
    return () => window.removeEventListener('keydown', handler);
  }, [onClose, isEditModalOpen]);

  const handleEditSubmit = async (data: AccountRequest) => {
    if (!account) return;
    try {
      await updateAccount.mutateAsync({ id: account.id, data });
      setIsEditModalOpen(false);
      onEdit?.();
    } catch (err) {
      console.error('Failed to update account:', err);
    }
  };

  const chartCurrency =
    account?.isConverted && account?.baseCurrency
      ? account.baseCurrency
      : account?.currency ?? DEFAULT_CURRENCY;

  const transactions = transactionsData?.content ?? [];

  // Determine which tabs to show
  const tabs: { key: Tab; label: string }[] = [
    { key: 'overview', label: t('detail.overview') },
    ...(account?.isInterestEnabled ? [{ key: 'interest' as Tab, label: t('detail.interest') }] : []),
    { key: 'attachments', label: t('detail.attachments') },
  ];

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center">
      {/* Backdrop */}
      <div
        className="absolute inset-0 bg-background/80 backdrop-blur-sm"
        onClick={onClose}
      />

      {/* Modal panel */}
      <div className="relative bg-surface border border-border rounded-lg shadow-lg max-w-4xl w-full max-h-[90vh] overflow-y-auto m-4">
        {/* ── Header ── */}
        <div className="sticky top-0 z-10 bg-surface border-b border-border px-6 py-4 flex items-center justify-between">
          <div className="flex items-center gap-3">
            {account && (
              <div className="flex h-10 w-10 items-center justify-center rounded-xl bg-primary/10 text-primary shrink-0">
                {account.institution?.logo ? (
                  <img
                    src={account.institution.logo}
                    alt=""
                    className="h-7 w-7 rounded object-contain"
                  />
                ) : (
                  accountTypeIcons[account.type]
                )}
              </div>
            )}
            <div>
              {isLoadingAccount ? (
                <LoadingSkeleton className="h-6 w-40" />
              ) : (
                <>
                  <h2 className="text-xl font-bold text-foreground">{account?.name}</h2>
                  <p className="text-sm text-muted-foreground">
                    {account ? t('form.types.' + account.type) : ''} · {account?.currency}
                    {account?.institution && ` · ${account.institution.name}`}
                  </p>
                </>
              )}
            </div>
          </div>

          <div className="flex items-center gap-2">
            <Button
              variant="secondary"
              size="sm"
              onClick={() => setIsEditModalOpen(true)}
              className="hidden sm:flex"
            >
              <Edit2 className="h-4 w-4 mr-1.5" />
              {t('detail.edit')}
            </Button>
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
          {isLoadingAccount ? (
            <div className="space-y-4">
              <LoadingSkeleton className="h-10 w-full" />
              <LoadingSkeleton className="h-64 w-full" />
            </div>
          ) : !account ? (
            <div className="p-4 bg-error/10 border border-error/20 rounded-lg text-error text-sm">
              {t('detail.failedToLoad')}
            </div>
          ) : (
            <>
              {/* Current Balance Hero */}
              <div className="mb-6 p-4 bg-background border border-border rounded-lg flex items-center justify-between">
                <div>
                  <p className="text-sm text-muted-foreground mb-1">{t('detail.currentBalance')}</p>
                  <ConvertedAmount
                    amount={account.balance}
                    currency={account.currency}
                    convertedAmount={account.balanceInBaseCurrency}
                    baseCurrency={account.baseCurrency}
                    exchangeRate={account.exchangeRate}
                    isConverted={account.isConverted}
                    secondaryAmount={account.balanceInSecondaryCurrency}
                    secondaryCurrency={account.secondaryCurrency}
                    className={cn(
                      'text-3xl font-bold font-mono',
                      account.balance < 0 ? 'text-error' : 'text-foreground'
                    )}
                  />
                </div>
                {!account.isActive && (
                  <span className="inline-flex items-center px-2.5 py-1 rounded-full text-xs font-medium bg-surface-elevated text-muted-foreground border border-border">
                    {t('detail.closed')}
                  </span>
                )}
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
                          : 'border-transparent text-muted-foreground hover:text-foreground'
                      )}
                    >
                      {tab.label}
                    </button>
                  ))}
                </div>
              </div>

              {/* ── Overview Tab ── */}
              {activeTab === 'overview' && (
                <div className="space-y-6">
                  {/* Balance History Chart */}
                  <div className="bg-background border border-border rounded-lg p-6">
                    <div className="flex items-center justify-between mb-4">
                      <h3 className="text-base font-semibold text-foreground">Balance History</h3>
                      <div className="flex gap-2">
                        {periodOptions.map((opt) => (
                          <button
                            key={opt.value}
                            onClick={() => setPeriod(opt.value)}
                            className={cn(
                              'px-3 py-1 rounded text-sm transition-colors',
                              period === opt.value
                                ? 'bg-primary text-background font-medium'
                                : 'bg-surface text-muted-foreground hover:text-foreground hover:bg-surface-elevated'
                            )}
                          >
                            {opt.label}
                          </button>
                        ))}
                      </div>
                    </div>

                    {isLoadingHistory ? (
                      <LoadingSkeleton className="h-52 w-full" />
                    ) : balanceHistory && balanceHistory.length > 0 ? (
                      <div className="h-52">
                        <ResponsiveContainer width="100%" height="100%" minWidth={0}>
                          <LineChart
                            data={balanceHistory}
                            margin={{ top: 4, right: 20, left: 0, bottom: 4 }}
                          >
                            <CartesianGrid strokeDasharray="3 3" stroke="#333" />
                            <XAxis
                              dataKey="date"
                              stroke="#666"
                              tick={{ fill: '#666', fontSize: 11 }}
                              tickFormatter={(v) => format(new Date(v), 'MMM d')}
                            />
                            <YAxis
                              stroke="#666"
                              tick={{ fill: '#666', fontSize: 11 }}
                              tickFormatter={(v) =>
                                formatCurrency(v, chartCurrency, { compact: true })
                              }
                            />
                            <Tooltip
                              contentStyle={{
                                backgroundColor: '#1a1a1a',
                                border: '1px solid #333',
                                borderRadius: '8px',
                                color: '#fff',
                              }}
                              labelFormatter={(v) => format(new Date(v), 'MMMM d, yyyy')}
                              formatter={(v: number | undefined) => [
                                v !== undefined ? formatCurrency(v, chartCurrency) : '',
                                t('detail.balance'),
                              ]}
                            />
                            <Line
                              type="monotone"
                              dataKey="balance"
                              stroke="#f5a623"
                              strokeWidth={2}
                              dot={false}
                              activeDot={{ r: 4, fill: '#f5a623' }}
                            />
                          </LineChart>
                        </ResponsiveContainer>
                      </div>
                    ) : (
                      <div className="h-52 flex items-center justify-center text-muted-foreground text-sm">
                        {t('detail.noBalanceHistory')}
                      </div>
                    )}
                  </div>

                  {/* Recent Transactions */}
                  <div className="bg-background border border-border rounded-lg p-6">
                    <h3 className="text-base font-semibold text-foreground mb-4">
                      {t('detail.recentTransactions')}
                    </h3>

                    {isLoadingTransactions ? (
                      <LoadingSkeleton className="h-40 w-full" />
                    ) : transactions.length > 0 ? (
                      <div className="space-y-2">
                        {transactions.map((tx) => {
                          const txConverted =
                            account.isConverted && account.exchangeRate && account.baseCurrency
                              ? tx.amount * account.exchangeRate
                              : undefined;
                          return (
                            <div
                              key={tx.id}
                              className="flex items-center justify-between p-3 rounded-lg hover:bg-surface-elevated transition-colors"
                            >
                              <div className="flex items-center gap-3">
                                <div className="w-9 h-9 rounded-full bg-surface-elevated flex items-center justify-center shrink-0">
                                  {tx.category?.icon ? (
                                    <span className="text-base">{tx.category.icon}</span>
                                  ) : (
                                    <span className="text-muted-foreground text-sm">$</span>
                                  )}
                                </div>
                                <div>
                                  <p className="font-medium text-sm text-foreground">
                                    {tx.payee || tx.description || 'Transaction'}
                                  </p>
                                  <p className="text-xs text-muted-foreground">
                                    {format(new Date(tx.date), 'MMM d, yyyy')}
                                    {tx.category && ` · ${tx.category.name}`}
                                  </p>
                                </div>
                              </div>
                              <span
                                className={cn(
                                  'font-mono font-semibold text-sm',
                                  tx.type === 'EXPENSE' ? 'text-error' : 'text-success'
                                )}
                              >
                                {tx.type === 'INCOME' && '+'}
                                {tx.type === 'EXPENSE' && '-'}
                                <ConvertedAmount
                                  inline
                                  amount={tx.amount}
                                  currency={tx.currency}
                                  convertedAmount={txConverted}
                                  baseCurrency={account.baseCurrency}
                                  exchangeRate={account.exchangeRate}
                                  isConverted={account.isConverted}
                                />
                              </span>
                            </div>
                          );
                        })}
                      </div>
                    ) : (
                      <p className="text-center text-muted-foreground text-sm py-6">
                        {t('detail.noTransactions')}
                      </p>
                    )}

                    {transactionsData && transactionsData.totalElements > 0 && (
                      <div className="mt-4 pt-4 border-t border-border text-center">
                        <Button
                          variant="ghost"
                          size="sm"
                          onClick={() => {
                            onClose();
                            navigate(`/transactions?accountId=${account.id}`);
                          }}
                        >
                          <ArrowUpRight className="h-4 w-4 mr-1.5" />
                          {t('detail.viewAllTransactions')}
                        </Button>
                      </div>
                    )}
                  </div>

                  {/* Account Details */}
                  <div className="bg-background border border-border rounded-lg p-6">
                    <h3 className="text-base font-semibold text-foreground mb-4">{t('detail.details')}</h3>
                    <dl className="grid grid-cols-1 sm:grid-cols-2 gap-4">
                      <div>
                        <dt className="text-xs text-muted-foreground uppercase tracking-wide mb-1">
                          {t('detail.accountNumber')}
                        </dt>
                        <dd className="text-sm font-medium text-foreground">
                          {account.accountNumber || '—'}
                        </dd>
                      </div>
                      <div>
                        <dt className="text-xs text-muted-foreground uppercase tracking-wide mb-1">
                          {t('detail.type')}
                        </dt>
                        <dd className="text-sm font-medium text-foreground">
                          {t('form.types.' + account.type)}
                        </dd>
                      </div>
                      <div>
                        <dt className="text-xs text-muted-foreground uppercase tracking-wide mb-1">
                          {t('detail.currency')}
                        </dt>
                        <dd className="text-sm font-medium text-foreground">{account.currency}</dd>
                      </div>
                      {account.institution && (
                        <div>
                          <dt className="text-xs text-muted-foreground uppercase tracking-wide mb-1">
                            {t('detail.institution')}
                          </dt>
                          <dd className="text-sm font-medium text-foreground">
                            {account.institution.name}
                          </dd>
                        </div>
                      )}
                      {account.description && (
                        <div className="col-span-full">
                          <dt className="text-xs text-muted-foreground uppercase tracking-wide mb-1">
                            {t('detail.description')}
                          </dt>
                          <dd className="text-sm text-foreground">{account.description}</dd>
                        </div>
                      )}
                    </dl>
                  </div>
                </div>
              )}

              {/* ── Interest Tab ── */}
              {activeTab === 'interest' && account.isInterestEnabled && (
                <InterestRateVariationsSection
                  accountId={account.id}
                  accountBalance={account.balance}
                  accountCurrency={account.currency}
                  accountInterestPeriod={account.interestPeriod}
                />
              )}

              {/* ── Attachments Tab ── */}
              {activeTab === 'attachments' && (
                <div className="space-y-4">
                  <AttachmentList
                    entityType={AttachmentEntityType.ACCOUNT}
                    entityId={account.id}
                  />
                  <AttachmentUpload
                    entityType={AttachmentEntityType.ACCOUNT}
                    entityId={account.id}
                  />
                </div>
              )}
            </>
          )}
        </div>
      </div>

      {/* Edit Dialog */}
      {account && (
        <Dialog open={isEditModalOpen} onOpenChange={setIsEditModalOpen}>
          <DialogContent className="sm:max-w-[560px] max-h-[90vh] overflow-y-auto">
            <DialogHeader>
              <DialogTitle>{t('detail.editTitle')}</DialogTitle>
            </DialogHeader>
            <AccountForm
              account={account}
              onSubmit={handleEditSubmit}
              onCancel={() => setIsEditModalOpen(false)}
              isLoading={updateAccount.isPending}
            />
          </DialogContent>
        </Dialog>
      )}
    </div>
  );
}
