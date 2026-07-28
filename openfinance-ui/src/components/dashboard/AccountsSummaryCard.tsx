import { 
  Wallet, 
  CreditCard, 
  PiggyBank, 
  TrendingUp, 
  Banknote,
  HelpCircle 
} from 'lucide-react';
import { useNavigate } from 'react-router';
import { ROUTES } from '@/constants/routes';
import type { IAccountSummary } from '../../types/dashboard';
import type { AccountType } from '../../types/account';
import { ConvertedAmount } from '@/components/ui/ConvertedAmount';
import { useTranslation } from 'react-i18next';

interface AccountsSummaryCardProps {
  accounts: IAccountSummary[];
}

/**
 * Get icon component for account type
 */
const getAccountIcon = (type: AccountType) => {
  switch (type) {
    case 'CHECKING':
      return Wallet;
    case 'SAVINGS':
      return PiggyBank;
    case 'CREDIT_CARD':
      return CreditCard;
    case 'INVESTMENT':
      return TrendingUp;
    case 'CASH':
      return Banknote;
    default:
      return HelpCircle;
  }
};

/**
 * AccountsSummaryCard - List of user accounts with balances
 * 
 * Design:
 * - Each account: icon + name + balance (right-aligned, monospace)
 * - Display account type icons (lucide-react)
 * - Hover effect: slight background highlight
 * - Click to navigate to accounts page
 */
export default function AccountsSummaryCard({ accounts }: AccountsSummaryCardProps) {
  const navigate = useNavigate();
  const { t } = useTranslation('dashboard');

  if (accounts.length === 0) {
    return (
      <div className="bg-surface rounded-lg p-6 border border-border h-full flex flex-col">
        <h3 className="text-lg font-semibold text-text-primary mb-4">{t('accountsCard.title')}</h3>
        <div className="text-center py-8">
          <Wallet className="h-12 w-12 text-text-muted mx-auto mb-3" />
          <p className="text-text-secondary text-sm">{t('accountsCard.empty')}</p>
          <p className="text-text-muted text-xs mt-1">{t('accountsCard.emptySub')}</p>
        </div>
      </div>
    );
  }

  return (
    <div className="bg-surface rounded-lg p-6 border border-border h-full flex flex-col">
      <div className="flex items-center justify-between mb-4">
        <h3 className="text-lg font-semibold text-text-primary">{t('accountsCard.title')}</h3>
        <span className="text-xs text-text-secondary">{t('accountsCard.count', { count: accounts.length })}</span>
      </div>

      <div className="space-y-2 flex-1 overflow-y-auto min-h-0 pr-2 scrollbar-thin">
        {accounts.map((account) => {
          const Icon = getAccountIcon(account.type);
          const isNegative = account.balance < 0;
          
          return (
            <div
              key={account.id}
              onClick={() => navigate(ROUTES.ACCOUNTS)}
              onKeyDown={(e) => {
                if (e.key === 'Enter' || e.key === ' ') {
                  e.preventDefault();
                  navigate(ROUTES.ACCOUNTS);
                }
              }}
              role="button"
              tabIndex={0}
              aria-label={t('accountsCard.viewDetails', { name: account.name })}
              className="flex items-center gap-3 p-3 rounded-lg hover:bg-surface-elevated transition-colors cursor-pointer focus:outline-none focus:ring-2 focus:ring-primary focus:ring-offset-2 focus:ring-offset-surface"
            >
              {/* Icon */}
              <div className="flex-shrink-0">
                <Icon className="h-5 w-5 text-primary" />
              </div>

              {/* Account Name & Type */}
              <div className="flex-1 min-w-0">
                <div className="text-sm font-medium text-text-primary truncate">
                  {account.name}
                </div>
                <div className="text-xs text-text-secondary">
                  {account.type.replace('_', ' ')}
                </div>
              </div>

              {/* Balance */}
              <div className="text-right">
                <div className={`text-sm font-semibold font-mono ${
                  isNegative ? 'text-red-500' : 'text-text-primary'
                }`}>
                  {/* REQ-8.1: Display account balance using ConvertedAmount.
                      IAccountSummary lacks conversion fields, so ConvertedAmount
                      gracefully falls back to native currency display. */}
                  <ConvertedAmount
                    amount={account.balance}
                    currency={account.currency}
                    inline
                  />
                </div>
              </div>
            </div>
          );
        })}
      </div>
    </div>
  );
}
