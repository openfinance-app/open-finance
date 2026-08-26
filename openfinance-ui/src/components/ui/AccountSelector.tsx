/**
 * AccountSelector component
 *
 * A dropdown component for selecting accounts with search functionality.
 * Supports filtering by account type and shows account balance.
 * 
 * BUG-08 fixed: ACCOUNT_TYPE_LABELS now uses uppercase keys (CHECKING, SAVINGS…)
 * to match the backend API enum values.
 */
import { useState, useMemo } from 'react';
import { useTranslation } from 'react-i18next';
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from '@/components/ui/Select';
import { ConvertedAmount } from '@/components/ui/ConvertedAmount';
import { useAccounts } from '@/hooks/useAccounts';
import { Loader2, Search, Wallet } from 'lucide-react';
import type { Account } from '@/types/account';
import { markSelectInteraction } from '@/utils/selectClickGuard';

interface AccountSelectorProps {
  value?: number;
  onValueChange: (value: number | undefined) => void;
  placeholder?: string;
  disabled?: boolean;
  className?: string;
  /**
   * Filter accounts by type
   */
  accountType?: 'all' | 'active' | 'closed';
  /**
   * Show "None" option at the top
   */
  allowNone?: boolean;
  /**
   * Account id to exclude from the list (e.g. the counterpart account in a
   * transfer, so the same account cannot be chosen for both sides).
   */
  excludeAccountId?: number;
}

export function AccountSelector({
  value,
  onValueChange,
  placeholder,
  disabled = false,
  className,
  accountType = 'active',
  allowNone = true,
  excludeAccountId,
}: AccountSelectorProps) {
  const { data: accounts, isLoading, isError } = useAccounts(accountType);
  const [searchQuery, setSearchQuery] = useState('');
  const [isOpen, setIsOpen] = useState(false);
  const { t } = useTranslation('accounts');

  const resolvedPlaceholder = placeholder ?? t('card.searchAccounts');

  // Filter accounts by search query and excluded id
  const filteredAccounts = useMemo(() => {
    if (!accounts) return [];

    const withoutExcluded =
      excludeAccountId !== undefined
        ? accounts.filter((account) => account.id !== excludeAccountId)
        : accounts;

    const normalizedQuery = searchQuery.trim().toLowerCase();
    if (!normalizedQuery) return withoutExcluded;

    return withoutExcluded.filter(
      (account) =>
        account.name.toLowerCase().includes(normalizedQuery) ||
        (account.institution?.name && account.institution.name.toLowerCase().includes(normalizedQuery))
    );
  }, [accounts, searchQuery, excludeAccountId]);

  // Group accounts by type — uses uppercase keys to match API enum values
  const groupedAccounts = useMemo(() => {
    const groups: Record<string, Account[]> = {};

    filteredAccounts.forEach((account) => {
      const type = account.type || 'OTHER';
      if (!groups[type]) {
        groups[type] = [];
      }
      groups[type].push(account);
    });

    // Sort each group by name
    Object.keys(groups).forEach((type) => {
      groups[type].sort((a, b) => a.name.localeCompare(b.name));
    });

    return groups;
  }, [filteredAccounts]);

  // Get sorted types — use translated labels for sorting
  const sortedTypes = useMemo(() => {
    return Object.keys(groupedAccounts).sort((a, b) => {
      const labelA = t(`form.types.${a}`, a);
      const labelB = t(`form.types.${b}`, b);
      return labelA.localeCompare(labelB);
    });
  }, [groupedAccounts, t]);

  // Find selected account
  const selectedAccount = useMemo(() => {
    if (!value || !accounts) return null;
    return accounts.find((a) => a.id === value) || null;
  }, [value, accounts]);

  const handleValueChange = (val: string) => {
    if (val === '__none__') {
      onValueChange(undefined);
    } else {
      onValueChange(val ? Number(val) : undefined);
    }
  };

  if (isLoading) {
    return (
      <div className="flex h-10 w-full items-center justify-center rounded-lg border border-border bg-surface px-3 py-2">
        <Loader2 className="h-4 w-4 animate-spin text-text-muted" />
        <span className="ml-2 text-sm text-text-muted">{t('card.loadingAccounts')}</span>
      </div>
    );
  }

  if (isError || !accounts) {
    return (
      <div className="flex h-10 w-full items-center justify-center rounded-lg border border-error bg-surface px-3 py-2">
        <span className="text-sm text-error">{t('card.failedToLoad')}</span>
      </div>
    );
  }

  return (
    <Select
      value={value?.toString() || (allowNone ? '__none__' : '')}
      onValueChange={handleValueChange}
      disabled={disabled}
      open={isOpen}
      onOpenChange={(open) => {
        setIsOpen(open);
        if (!open) {
          markSelectInteraction();
          setSearchQuery('');
        }
      }}
    >
      <SelectTrigger className={className}>
        <SelectValue placeholder={resolvedPlaceholder}>
          {selectedAccount ? (
            <span className="flex items-center gap-2">
              <Wallet className="h-4 w-4 text-text-muted" />
              <span>{selectedAccount.name}</span>
              {selectedAccount.institution?.name && (
                <span className="text-text-muted text-xs">
                  ({selectedAccount.institution.name})
                </span>
              )}
            </span>
          ) : (
            <span className="text-text-muted">{t('card.none')}</span>
          )}
        </SelectValue>
      </SelectTrigger>
      <SelectContent
        className="p-0 flex flex-col"
        viewportClassName="p-1"
        headerSlot={
          <div className="shrink-0 border-b border-border bg-surface p-2">
            <div className="relative">
              <Search className="pointer-events-none absolute left-2.5 top-1/2 h-4 w-4 -translate-y-1/2 text-text-muted" />
              <input
                type="text"
                value={searchQuery}
                onChange={(event) => setSearchQuery(event.target.value)}
                onKeyDown={(event) => event.stopPropagation()}
                placeholder={t('card.searchAccounts')}
                className="h-9 w-full rounded-md border border-border bg-background pl-8 pr-2 text-sm text-text-primary placeholder:text-text-muted focus:outline-none focus:ring-2 focus:ring-primary"
                autoFocus={isOpen}
              />
            </div>
          </div>
        }
      >
        {/* "None" option */}
        {allowNone && (
          <SelectItem value="__none__" className="gap-2">
            <span className="flex items-center gap-2">
              <Wallet className="h-4 w-4 text-text-muted" />
              <span className="text-text-muted">{t('card.none')}</span>
            </span>
          </SelectItem>
        )}

        {/* Grouped accounts */}
        {sortedTypes.map((type) => (
          <div key={type} className="mt-2">
            {/* Type header — translated using uppercase key */}
            <div className="px-2 py-1 text-xs font-semibold text-text-muted">
              {t(`form.types.${type}`, type)}
            </div>

            {/* Accounts in this group */}
            {groupedAccounts[type].map((account) => (
              <SelectItem
                key={account.id}
                value={account.id.toString()}
                className="gap-2"
              >
                <div className="flex items-center justify-between w-full gap-2">
                  <span className="flex items-center gap-2 min-w-0">
                    <Wallet className="h-4 w-4 text-text-muted shrink-0" />
                    <span className="text-sm truncate">{account.name}</span>
                  </span>
                  {account.balance !== undefined && (
                    <span className="text-xs text-text-muted shrink-0 whitespace-nowrap pt-0.5">
                      <ConvertedAmount
                        amount={account.balance}
                        currency={account.currency}
                        inline
                        compact
                      />
                    </span>
                  )}
                </div>
              </SelectItem>
            ))}
          </div>
        ))}

        {/* Empty state */}
        {sortedTypes.length === 0 && (
          <div className="p-2 text-center text-sm text-text-muted">
            {t('card.noAccountsMatch')}
          </div>
        )}
      </SelectContent>
    </Select>
  );
}
