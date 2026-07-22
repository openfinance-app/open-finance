/**
 * CurrencyBreakdown - Shows asset distribution across multiple currencies
 * Sprint 6 - Task 6.2.16: Multi-currency dashboard summary
 * 
 * Features:
 * - Groups accounts by currency
 * - Shows balance in each currency
 * - Converts foreign currencies to base currency using live exchange rates
 * - Displays percentage of total for each currency
 * - Shows grand total in base currency
 * - Progress bars for visual representation
 */

import { useState } from 'react';
import { Wallet } from 'lucide-react';
import { useTranslation } from 'react-i18next';
import { useAccounts } from '@/hooks/useAccounts';
import { useAssets } from '@/hooks/useAssets';
import { useLatestExchangeRate } from '@/hooks/useCurrency';
import { DEFAULT_CURRENCY, formatExchangeRate } from '@/utils/currency';
import { add, multiply, sum, percentage } from '@/utils/money';
import { useFormatCurrency } from '@/hooks/useFormatCurrency';
import { PrivateAmount } from '../ui/PrivateAmount';
import { ConvertedAmount } from '@/components/ui/ConvertedAmount';
import { useSecondaryConversion } from '@/hooks/useSecondaryConversion';

interface CurrencyBalance {
  currency: string;
  balance: number;
  balanceInBase: number;
  accountCount: number;
  percentage: number;
  rate?: number;
}

interface CurrencyBreakdownProps {
  baseCurrency?: string; // Default to 'USD' if not provided
}

/**
 * CurrencyBreakdown Component
 * 
 * Displays a breakdown of account balances grouped by currency,
 * with conversion to base currency and percentage visualization.
 */
export default function CurrencyBreakdown({ baseCurrency = DEFAULT_CURRENCY }: CurrencyBreakdownProps) {
  const { t } = useTranslation('dashboard');
  const [refreshKey] = useState(0);
  const { data: accounts, isLoading, error } = useAccounts();
  const { data: assets, isLoading: isLoadingAssets } = useAssets();

  const allLoading = isLoading || isLoadingAssets;

  // Loading state
  if (allLoading) {
    return (
      <div className="bg-surface rounded-lg p-6 border border-border animate-pulse">
        <div className="h-6 bg-surface-elevated rounded w-40 mb-4"></div>
        <div className="h-12 bg-surface-elevated rounded mb-4"></div>
        <div className="space-y-3 flex-1 overflow-y-auto scrollbar-thin pr-2 min-h-0">
          <div className="h-10 bg-surface-elevated rounded"></div>
          <div className="h-10 bg-surface-elevated rounded"></div>
          <div className="h-10 bg-surface-elevated rounded"></div>
        </div>
      </div>
    );
  }

  // Error state
  if (error) {
    return (
      <div className="bg-surface rounded-lg p-6 border border-red-500/50">
        <div className="flex items-center justify-between mb-4">
          <h3 className="text-lg font-semibold text-text-primary flex items-center gap-2">
            <Wallet className="h-5 w-5" />
            {t('currencyBreakdown.title')}
          </h3>
        </div>
        <p className="text-sm text-red-500">
          {error instanceof Error ? error.message : t('currencyBreakdown.loadError')}
        </p>
      </div>
    );
  }

  // No accounts state
  if (!accounts || accounts.length === 0) {
    return (
      <div className="bg-surface rounded-lg p-6 border border-border h-full flex flex-col">
        <div className="flex items-center justify-between mb-4">
          <h3 className="text-lg font-semibold text-text-primary flex items-center gap-2">
            <Wallet className="h-5 w-5" />
            {t('currencyBreakdown.title')}
          </h3>
        </div>
        <p className="text-sm text-text-secondary">{t('accountsCard.empty')}</p>
      </div>
    );
  }

  // Group accounts by currency and calculate balances
  const currencyGroups = accounts.reduce((acc, account) => {
    // Only include positive balances (assets) — negative balances are liabilities and
    // should not inflate the currency breakdown total (matches Net Worth Card totalAssets)
    if (account.balance <= 0) return acc;
    if (!acc[account.currency]) {
      acc[account.currency] = { balance: 0, accountCount: 0 };
    }
    acc[account.currency].balance = add(acc[account.currency].balance, account.balance);
    acc[account.currency].accountCount += 1;
    return acc;
  }, {} as Record<string, { balance: number; accountCount: number }>);

  // Also include financial asset totalValue grouped by currency
  // Only include assets NOT linked to an account (linked asset values are already
  // added to the account balance by AccountService.toResponseWithDecryption)
  if (assets) {
    assets.forEach((asset) => {
      if (asset.accountId != null) return; // Skip: already counted in account balance
      if (!currencyGroups[asset.currency]) {
        currencyGroups[asset.currency] = { balance: 0, accountCount: 0 };
      }
      currencyGroups[asset.currency].balance = add(currencyGroups[asset.currency].balance, asset.totalValue);
      currencyGroups[asset.currency].accountCount += 1;
    });
  }

  // Convert to CurrencyBalance array with exchange rates
  const currencyBalances: CurrencyBalance[] = Object.entries(currencyGroups).map(
    ([currency, data]) => {
      // Only fetch exchange rate if currency is different from base
      const isForeignCurrency = currency !== baseCurrency;

      return {
        currency,
        balance: data.balance,
        balanceInBase: data.balance, // Will be updated below if foreign currency
        accountCount: data.accountCount,
        percentage: 0, // Will be calculated after totals
        rate: undefined,
        isForeignCurrency,
      };
    }
  );

  return (
    <CurrencyBreakdownContent
      currencyBalances={currencyBalances}
      baseCurrency={baseCurrency}
      refreshKey={refreshKey}
    />
  );
}

interface CurrencyBreakdownContentProps {
  currencyBalances: CurrencyBalance[];
  baseCurrency: string;
  refreshKey: number;
}

/**
 * Content component that handles exchange rate fetching for each currency
 */
function CurrencyBreakdownContent({
  currencyBalances,
  baseCurrency,
  refreshKey,
}: CurrencyBreakdownContentProps) {
  const { t } = useTranslation('dashboard');
  const { convert, secondaryCurrency: secCurrency, secondaryExchangeRate } = useSecondaryConversion(baseCurrency);
  // Fetch exchange rates for all foreign currencies
  const balancesWithRates = currencyBalances.map((currencyBalance) => {
    const isForeignCurrency = currencyBalance.currency !== baseCurrency;

    // eslint-disable-next-line react-hooks/rules-of-hooks
    const { data: exchangeRate, isLoading } = useLatestExchangeRate(
      currencyBalance.currency,
      baseCurrency,
      refreshKey
    );

    const rate = isForeignCurrency && exchangeRate ? exchangeRate.rate : 1;
    const balanceInBase = multiply(currencyBalance.balance, rate);

    return {
      ...currencyBalance,
      rate: isForeignCurrency ? rate : undefined,
      balanceInBase,
      isLoading,
    };
  });

  // Calculate grand total
  const grandTotal = sum(balancesWithRates.map((cb) => cb.balanceInBase));

  // Calculate percentages
  const balancesWithPercentages = balancesWithRates.map((cb) => {
    const pct = grandTotal > 0 ? percentage(cb.balanceInBase, grandTotal) : 0;
    return { ...cb, percentage: pct };
  });

  // Sort by balance in base currency (descending)
  const sortedBalances = balancesWithPercentages.sort(
    (a, b) => b.balanceInBase - a.balanceInBase
  );


  return (
    <div className="bg-surface rounded-lg p-6 border border-border hover:border-border/70 transition-colors h-full flex flex-col">
      {/* Header */}
      <div className="flex items-center justify-between mb-4">
        <h3 className="text-lg font-semibold text-text-primary flex items-center gap-2">
          <Wallet className="h-5 w-5 text-primary" />
          {t('currencyBreakdown.title')}
        </h3>
      </div>

      {/* Grand Total */}
      <div className="mb-6">
        <div className="text-xs text-text-secondary mb-1">{t('currencyBreakdown.total')}</div>
        <div className="text-3xl font-bold text-primary font-mono">
          <ConvertedAmount
            amount={grandTotal}
            currency={baseCurrency}
            isConverted={false}
            secondaryAmount={convert(grandTotal)}
            secondaryCurrency={secCurrency}
            secondaryExchangeRate={secondaryExchangeRate}
            inline
          />
        </div>
      </div>

      {/* Currency List */}
      <div className="space-y-3 flex-1 overflow-y-auto scrollbar-thin pr-2 min-h-0">
        {sortedBalances.map((currencyBalance) => (
          <CurrencyBalanceRow
            key={currencyBalance.currency}
            currencyBalance={currencyBalance}
            baseCurrency={baseCurrency}
          />
        ))}
      </div>

      {/* Footer Note */}
      {sortedBalances.some((cb) => cb.rate !== undefined) && (
        <div className="mt-4 pt-4 border-t border-border">
          <p className="text-xs text-text-secondary">
            {t('currencyBreakdown.footer')}
          </p>
        </div>
      )}
    </div>
  );
}

interface CurrencyBalanceRowProps {
  currencyBalance: CurrencyBalance & { isLoading?: boolean };
  baseCurrency: string;
}

/**
 * Row component for displaying a single currency balance
 */
function CurrencyBalanceRow({ currencyBalance, baseCurrency }: CurrencyBalanceRowProps) {
  const { t } = useTranslation('dashboard');
  const isForeignCurrency = currencyBalance.currency !== baseCurrency;
  const isLoading = currencyBalance.isLoading;
  const { format } = useFormatCurrency();
  const { convert, secondaryCurrency: secCurrency, secondaryExchangeRate } = useSecondaryConversion(baseCurrency);

  return (
    <div className="py-2">
      {/* Currency info and balance */}
      <div className="flex items-center justify-between mb-2">
        <div className="flex items-center gap-2">
          <span className="text-sm font-semibold text-text-primary">
            {currencyBalance.currency}
          </span>
          <span className="text-xs text-text-secondary">
            ({currencyBalance.accountCount} {t('currencyBreakdown.item', { count: currencyBalance.accountCount })})
          </span>
        </div>
        <div className="text-right">
          {isForeignCurrency ? (
            <>
              {/* REQ-8.2: Show base-currency total as primary; native amount as secondary */}
              {isLoading ? (
                <div className="text-sm font-mono text-text-secondary">{t('currencyBreakdown.converting')}</div>
              ) : (
                <div className="text-sm font-mono text-text-primary">
                  <ConvertedAmount
                    amount={currencyBalance.balanceInBase}
                    currency={baseCurrency}
                    isConverted={false}
                    secondaryAmount={convert(currencyBalance.balanceInBase)}
                    secondaryCurrency={secCurrency}
                    secondaryExchangeRate={secondaryExchangeRate}
                    inline
                  />
                </div>
              )}
              <div className="text-xs text-text-secondary">
                <PrivateAmount inline>{format(currencyBalance.balance, currencyBalance.currency)}</PrivateAmount>
              </div>
            </>
          ) : (
            <div className="text-sm font-mono text-text-primary">
              <ConvertedAmount
                amount={currencyBalance.balance}
                currency={currencyBalance.currency}
                isConverted={false}
                secondaryAmount={convert(currencyBalance.balance)}
                secondaryCurrency={secCurrency}
                secondaryExchangeRate={secondaryExchangeRate}
                inline
              />
            </div>
          )}
        </div>
      </div>

      {/* Progress bar */}
      <div className="w-full bg-surface-elevated rounded-full h-2 overflow-hidden">
        <div
          className="bg-primary h-full rounded-full transition-all duration-300"
          style={{ width: `${currencyBalance.percentage}%` }}
        />
      </div>

      {/* Percentage */}
      <div className="flex items-center justify-between mt-1">
        <span className="text-xs text-text-secondary">
          {t('currencyBreakdown.percentOfTotal', { percent: currencyBalance.percentage.toFixed(1) })}
        </span>
        {isForeignCurrency && currencyBalance.rate && (
          <span className="text-xs text-text-muted">
            {t('currencyBreakdown.rate', { rate: formatExchangeRate(currencyBalance.rate) })}
          </span>
        )}
      </div>
    </div>
  );
}
