/**
 * AccountCard Component
 * Task 2.2.11: Create AccountCard component with edit/delete
 * Task 2.5.12: Updated to display institution logo
 * 
 * Displays account information in a card format
 */
import React from 'react';
import { useTranslation } from 'react-i18next';
import { Card } from '@/components/ui/Card';
import { Button } from '@/components/ui/Button';
import { Edit2, Trash2, Wallet, CreditCard, PiggyBank, TrendingUp, Banknote, Package, TrendingUp as TrendingIcon, RotateCcw, XCircle } from 'lucide-react';
import { ConvertedAmount } from '@/components/ui/ConvertedAmount';
import { useSecondaryConversion } from '@/hooks/useSecondaryConversion';
import type { Account, AccountType } from '@/types/account';
import { useInterestEstimate } from '@/hooks/useAccounts';
import { cn } from '@/lib/utils';
import { percentage } from '@/utils/money';

interface AccountCardProps {
  account: Account;
  onEdit: (account: Account) => void;
  onDelete: (account: Account) => void;
  onClose?: (account: Account) => void;
  onReopen?: (account: Account) => void;
  /** Called when the card body is clicked to open the detail view */
  onViewDetail?: (account: Account) => void;
}

// Icon mapping for account types
const accountTypeIcons: Record<AccountType, React.ReactNode> = {
  CHECKING: <Wallet className="h-5 w-5" />,
  SAVINGS: <PiggyBank className="h-5 w-5" />,
  CREDIT_CARD: <CreditCard className="h-5 w-5" />,
  INVESTMENT: <TrendingUp className="h-5 w-5" />,
  CASH: <Banknote className="h-5 w-5" />,
  OTHER: <Package className="h-5 w-5" />,
};

export function AccountCard({ account, onEdit, onDelete, onClose, onReopen, onViewDetail }: AccountCardProps) {
  const isNegative = account.balance < 0;
  const isClosed = !account.isActive;
  const { convert, secondaryCurrency: secCurrency, secondaryExchangeRate } = useSecondaryConversion(account.currency);
  const { t } = useTranslation('accounts');
  const { t: tc } = useTranslation('common');

  const { data: interestData } = useInterestEstimate(
    account.isInterestEnabled ? account.id : null,
    '1Y'
  );

  const handleCardClick = (e: React.MouseEvent) => {
    // Don't trigger detail view if clicking on action buttons
    if ((e.target as HTMLElement).closest('button')) {
      return;
    }
    onViewDetail?.(account);
  };

  return (
    <Card
      className="p-6 hover:bg-surface-elevated transition-colors duration-150 group cursor-pointer"
      onClick={handleCardClick}
    >
      <div className="flex items-start gap-4">
        {/* Icon */}
        <div className="flex h-12 w-12 shrink-0 items-center justify-center rounded-xl bg-primary/10 text-primary">
          {account.institution?.logo ? (
            <img
              src={account.institution.logo}
              alt=""
              className="h-8 w-8 rounded object-contain bg-surface"
            />
          ) : (
            accountTypeIcons[account.type]
          )}
        </div>

        {/* Info + Actions */}
        <div className="flex-1 min-w-0">
          <div className="flex items-start justify-between gap-2">
            <div className="min-w-0">
              <h3 className="text-lg font-semibold text-text-primary truncate">
                {account.name}
              </h3>
              <p className="text-sm text-text-secondary mt-0.5">
                {t(`form.types.${account.type}`)}
              </p>
            </div>

            {/* Actions */}
            <div className="flex items-center gap-1 shrink-0 opacity-0 group-hover:opacity-100 transition-opacity duration-150">
          <Button
            variant="ghost"
            size="sm"
            onClick={(e) => {
              e.stopPropagation();
              onEdit(account);
            }}
            className="h-8 w-8 p-0"
            aria-label={tc('aria.editAccount')}
          >
            <Edit2 className="h-4 w-4" />
          </Button>
          
          {/* Show reopen button for closed accounts */}
          {isClosed && onReopen && (
            <Button
              variant="ghost"
              size="sm"
              onClick={(e) => {
                e.stopPropagation();
                onReopen(account);
              }}
              className="h-8 w-8 p-0 text-success hover:text-success hover:bg-success/10"
              aria-label="Reopen account"
              title="Reopen account"
            >
              <RotateCcw className="h-4 w-4" />
            </Button>
          )}
          
          {/* Show close button for active accounts */}
          {!isClosed && onClose && (
            <Button
              variant="ghost"
              size="sm"
              onClick={(e) => {
                e.stopPropagation();
                onClose(account);
              }}
              className="h-8 w-8 p-0 text-warning hover:text-warning hover:bg-warning/10"
              aria-label={tc('aria.closeAccount')}
              title="Close account"
            >
              <XCircle className="h-4 w-4" />
            </Button>
          )}
          
          {/* Delete button - show for all accounts */}
          <Button
            variant="ghost"
            size="sm"
            onClick={(e) => {
              e.stopPropagation();
              onDelete(account);
            }}
            className="h-8 w-8 p-0 text-error hover:text-error hover:bg-error/10"
            aria-label={tc('aria.deleteAccount')}
            title="Delete permanently"
          >
            <Trash2 className="h-4 w-4" />
          </Button>
            </div>
          </div>

          {account.institution && (
            <p className="text-sm text-text-tertiary mt-1 truncate">
              {account.institution.name}
            </p>
          )}

          {account.description && (
            <p className="text-sm text-text-tertiary mt-2 line-clamp-2">
              {account.description}
            </p>
          )}
        </div>
      </div>

      {/* Balance + Interest in one row */}
      <div className="mt-4 pt-4 border-t border-border flex items-start justify-between gap-3">
        {/* Left: balance */}
        <div className="min-w-0">
          <p className="text-sm text-text-secondary mb-1">{t('card.balance')}</p>
          <div
            className={cn(
              'text-2xl font-bold font-mono',
              isNegative ? 'text-error' : 'text-text-primary'
            )}
          >
            {/* Reference REQ-7.1, REQ-7.3: Display account balance with base and secondary currency tooltip */}
             <ConvertedAmount
               amount={account.balance}
               currency={account.currency}
               convertedAmount={account.balanceInBaseCurrency}
               baseCurrency={account.baseCurrency}
               exchangeRate={account.exchangeRate}
               isConverted={account.isConverted}
               secondaryAmount={account.balanceInSecondaryCurrency}
               secondaryCurrency={account.secondaryCurrency}
             />
          </div>
        </div>

        {/* Right: interest stats (only when enabled) */}
        {account.isInterestEnabled && (
          <div className="shrink-0 text-right space-y-1 pt-0.5">
            {/* Earned so far */}
            <div>
              <p className="text-xs text-text-muted leading-none mb-0.5">{t('card.earnedSoFar')}</p>
              <p className="text-sm font-mono font-medium text-text-secondary">
                <ConvertedAmount
                  amount={interestData?.historicalAccumulated ?? 0}
                  currency={account.currency}
                  isConverted={false}
                  secondaryAmount={convert(interestData?.historicalAccumulated ?? 0)}
                  secondaryCurrency={secCurrency}
                  secondaryExchangeRate={secondaryExchangeRate}
                  inline
                />
              </p>
            </div>
            {/* Projection */}
            <div>
              <p className="text-xs text-text-muted leading-none mb-0.5">{t('card.projectedNet')}</p>
              <p className="text-sm font-mono font-semibold text-success flex items-center justify-end gap-1">
                <TrendingIcon className="h-3 w-3" />
                <ConvertedAmount
                  amount={interestData?.estimate ?? 0}
                  currency={account.currency}
                  isConverted={false}
                  secondaryAmount={convert(interestData?.estimate ?? 0)}
                  secondaryCurrency={secCurrency}
                  secondaryExchangeRate={secondaryExchangeRate}
                  inline
                />
                {account.balance > 0 && (interestData?.estimate ?? 0) > 0 && (
                  <span className="text-xs font-medium text-success/70">
                    ({percentage(interestData?.estimate ?? 0, account.balance).toFixed(2)}%)
                  </span>
                )}
              </p>
            </div>
          </div>
        )}
      </div>

      {/* Status indicator */}
      {!account.isActive && (
        <div className="mt-3 inline-flex items-center px-2 py-1 rounded-md bg-surface-elevated text-text-muted text-xs font-medium">
          {t('card.inactive')}
        </div>
      )}
    </Card>
  );
}
