import { formatDecimal, formatExactNumber } from '@/utils/format';
/**
 * CurrencyBreakdown - Shows asset distribution across multiple currencies
 * Sprint 6 - Task 6.2.16: Multi-currency dashboard summary
 *
 * Features:
 * - Groups cash and crypto units by the currency held
 * - Shows balance in each currency
 * - Converts foreign currencies to base currency using live exchange rates
 * - Displays percentage of total for each currency
 * - Shows grand total in base currency
 * - Progress bars for visual representation
 */

import { useState } from 'react';
import { Wallet } from 'lucide-react';
import { useTranslation } from 'react-i18next';
import { useNavigate } from 'react-router';
import { useAccounts } from '@/hooks/useAccounts';
import { useAssets } from '@/hooks/useAssets';
import { useLatestExchangeRates } from '@/hooks/useCurrency';
import { DEFAULT_CURRENCY, formatExchangeRate, isCryptoCurrency } from '@/utils/currency';
import { add, multiply, sum, percentage } from '@/utils/money';
import { ConvertedAmount } from '@/components/ui/ConvertedAmount';
import { useSecondaryConversion } from '@/hooks/useSecondaryConversion';
import { PrivateAmount } from '@/components/ui/PrivateAmount';
import { getCryptoCurrency } from '@/utils/asset-symbol';

interface CurrencyPosition {
  key: string;
  currency: string;
  balance: number;
  valuationCurrency: string;
  value: number;
  isCrypto: boolean;
  href: string;
}

interface CurrencyBalance {
  key: string;
  currency: string;
  balance: number;
  balanceInBase: number;
  accountCount: number;
  percentage?: number;
  rate?: number;
  isCrypto: boolean;
  href: string;
  isLoading: boolean;
  isUnavailable: boolean;
}

interface CurrencyBreakdownProps {
  baseCurrency?: string; // Default to 'USD' if not provided
}

/**
 * CurrencyBreakdown Component
 *
 * Displays cash and investment holdings by currency, keeping crypto quantities
 * separate from their price currency and converting valuations to the base currency.
 */
export default function CurrencyBreakdown({
  baseCurrency = DEFAULT_CURRENCY,
}: CurrencyBreakdownProps) {
  const { t } = useTranslation('dashboard');
  const [refreshKey] = useState(0);
  const { data: accounts, isLoading, error } = useAccounts();
  const { data: assets, isLoading: isLoadingAssets, error: assetError } = useAssets();

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
  if (error || assetError) {
    return (
      <div className="bg-surface rounded-lg p-6 border border-red-500/50">
        <div className="flex items-center justify-between mb-4">
          <h3 className="plate-label  flex items-center gap-2">
            <Wallet className="h-5 w-5" />
            {t('currencyBreakdown.title')}
          </h3>
        </div>
        <p className="text-sm text-red-500">{t('currencyBreakdown.loadError')}</p>
      </div>
    );
  }

  const ownedAssets = (assets ?? []).filter(
    asset => asset.isActive !== false && asset.acquisitionType !== 'PLANNED' && asset.totalValue > 0
  );
  if ((!accounts || accounts.length === 0) && ownedAssets.length === 0) {
    return (
      <div className="bg-surface rounded-lg p-6 border border-border h-full flex flex-col">
        <div className="flex items-center justify-between mb-4">
          <h3 className="plate-label  flex items-center gap-2">
            <Wallet className="h-5 w-5" />
            {t('currencyBreakdown.title')}
          </h3>
        </div>
        <p className="text-sm text-text-secondary">{t('currencyBreakdown.empty')}</p>
      </div>
    );
  }

  const positions: CurrencyPosition[] = [];
  (accounts ?? []).forEach(account => {
    // Account totals can include linked investments; count only their own positive cash.
    const cash = account.ownBalance ?? account.balance;
    if (account.isActive === false || cash <= 0) return;
    positions.push({
      key: account.currency,
      currency: account.currency,
      balance: cash,
      valuationCurrency: account.currency,
      value: cash,
      isCrypto: isCryptoCurrency(account.currency),
      href: `/assets?currency=${encodeURIComponent(account.currency)}`,
    });
  });

  // Crypto units belong to the held coin, while their recorded value keeps its price currency.
  ownedAssets.forEach(asset => {
    const coin = getCryptoCurrency(asset);
    const isCrypto = asset.type === 'CRYPTO';
    const currency = isCrypto ? coin || asset.name : asset.currency;
    positions.push({
      key: isCrypto && !coin ? `asset:${asset.id}` : currency,
      currency,
      balance: isCrypto ? asset.quantity : asset.totalValue,
      valuationCurrency: asset.currency,
      value: asset.totalValue,
      isCrypto,
      href: isCrypto
        ? coin
          ? `/assets?type=CRYPTO&symbol=${encodeURIComponent(coin)}`
          : `/assets?highlight=${asset.id}`
        : `/assets?currency=${encodeURIComponent(currency)}`,
    });
  });

  return (
    <CurrencyBreakdownContent
      positions={positions}
      baseCurrency={baseCurrency}
      refreshKey={refreshKey}
    />
  );
}

interface CurrencyBreakdownContentProps {
  positions: CurrencyPosition[];
  baseCurrency: string;
  refreshKey: number;
}

/**
 * Content component that handles exchange rate fetching for each currency
 */
function CurrencyBreakdownContent({
  positions,
  baseCurrency,
  refreshKey,
}: CurrencyBreakdownContentProps) {
  const { t } = useTranslation('dashboard');
  const {
    convert,
    secondaryCurrency: secCurrency,
    secondaryExchangeRate,
  } = useSecondaryConversion(baseCurrency);
  const navigate = useNavigate();
  // Convert recorded valuations before grouping: BTC holdings may be priced in EUR and USD.
  const valuationCurrencies = [...new Set(positions.map(position => position.valuationCurrency))];
  const rates = useLatestExchangeRates(valuationCurrencies, baseCurrency, refreshKey);
  const groups = new Map<string, CurrencyBalance>();
  positions.forEach(position => {
    const isForeignValuation = position.valuationCurrency !== baseCurrency;
    const quote = rates[valuationCurrencies.indexOf(position.valuationCurrency)];
    const rate = isForeignValuation ? quote?.data?.rate : 1;
    const hasRate = rate != null && Number.isFinite(rate) && rate > 0;
    const isLoading = !hasRate && !!quote?.isLoading;
    const group = groups.get(position.key) ?? {
      key: position.key,
      currency: position.currency,
      balance: 0,
      balanceInBase: 0,
      accountCount: 0,
      isCrypto: position.isCrypto,
      href: position.href,
      isLoading: false,
      isUnavailable: false,
    };
    group.balance = add(group.balance, position.balance);
    group.balanceInBase = add(group.balanceInBase, hasRate ? multiply(position.value, rate) : 0);
    group.accountCount += 1;
    group.isCrypto ||= position.isCrypto;
    group.isLoading ||= isLoading;
    group.isUnavailable ||= !hasRate && !isLoading;
    if (!position.isCrypto && isForeignValuation && hasRate) group.rate = rate;
    if (position.isCrypto) group.href = position.href;
    groups.set(position.key, group);
  });

  const balancesWithRates = [...groups.values()];
  const isLoadingRates = balancesWithRates.some(balance => balance.isLoading);
  const missingRates = balancesWithRates.some(balance => balance.isUnavailable);
  const totalsReady = !isLoadingRates && !missingRates;
  const grandTotal = sum(balancesWithRates.map(cb => cb.balanceInBase));

  // Calculate percentages
  const balancesWithPercentages = balancesWithRates.map(cb => {
    const pct = totalsReady
      ? grandTotal > 0
        ? percentage(cb.balanceInBase, grandTotal)
        : 0
      : undefined;
    return { ...cb, percentage: pct };
  });

  // Sort by balance in base currency (descending)
  const sortedBalances = balancesWithPercentages.sort((a, b) => b.balanceInBase - a.balanceInBase);

  return (
    <div className="bg-surface rounded-lg p-6 border border-border hover:border-border/70 transition-colors h-full flex flex-col">
      {/* Header */}
      <div className="flex items-center justify-between mb-4">
        <h3 className="plate-label  flex items-center gap-2">
          <Wallet className="h-5 w-5 text-primary" />
          {t('currencyBreakdown.title')}
        </h3>
      </div>

      {/* Grand Total */}
      <div className="mb-6">
        <div className="text-xs text-text-secondary mb-1">{t('currencyBreakdown.total')}</div>
        <div className="text-3xl font-bold text-primary font-mono">
          {totalsReady ? (
            <ConvertedAmount
              amount={grandTotal}
              currency={baseCurrency}
              isConverted={false}
              secondaryAmount={convert(grandTotal)}
              secondaryCurrency={secCurrency}
              secondaryExchangeRate={secondaryExchangeRate}
              inline
            />
          ) : (
            <span className="text-sm" role={missingRates ? 'alert' : 'status'}>
              {t(
                missingRates
                  ? 'currencyBreakdown.valuationUnavailable'
                  : 'currencyBreakdown.converting'
              )}
            </span>
          )}
        </div>
      </div>

      {/* Currency List */}
      <div className="space-y-3 flex-1 overflow-y-auto scrollbar-thin pr-2 min-h-0">
        {sortedBalances.map(currencyBalance => (
          <CurrencyBalanceRow
            key={currencyBalance.key}
            currencyBalance={currencyBalance}
            baseCurrency={baseCurrency}
            onOpen={() => navigate(currencyBalance.href)}
          />
        ))}
      </div>

      {/* Footer Note */}
      {valuationCurrencies.some(currency => currency !== baseCurrency) && (
        <div className="mt-4 pt-4 border-t border-border">
          <p className="text-xs text-text-secondary">{t('currencyBreakdown.footer')}</p>
        </div>
      )}
    </div>
  );
}

interface CurrencyBalanceRowProps {
  currencyBalance: CurrencyBalance;
  baseCurrency: string;
  onOpen: () => void;
}

/**
 * Row component for displaying a single currency balance
 */
function CurrencyBalanceRow({ currencyBalance, baseCurrency, onOpen }: CurrencyBalanceRowProps) {
  const { t } = useTranslation('dashboard');
  const isForeignCurrency = currencyBalance.currency !== baseCurrency;
  const isLoading = currencyBalance.isLoading;
  const {
    convert,
    secondaryCurrency: secCurrency,
    secondaryExchangeRate,
  } = useSecondaryConversion(baseCurrency);

  return (
    <button
      type="button"
      onClick={onOpen}
      className="w-full text-left py-2 rounded-lg hover:bg-surface-elevated transition-colors px-2 -mx-2"
      aria-label={t('currencyBreakdown.viewAssets', { currency: currencyBalance.currency })}
    >
      {/* Currency info and balance */}
      <div className="flex items-center justify-between mb-2">
        <div className="flex items-center gap-2">
          <span className="text-sm font-semibold text-text-primary">
            {currencyBalance.currency}
          </span>
          <span className="text-xs text-text-secondary">
            ({currencyBalance.accountCount}{' '}
            {t('currencyBreakdown.item', { count: currencyBalance.accountCount })})
          </span>
        </div>
        <div className="text-right">
          {isForeignCurrency || currencyBalance.isCrypto ? (
            <>
              {/* REQ-8.2: Show base-currency total as primary; native amount as secondary */}
              {isLoading || currencyBalance.isUnavailable ? (
                <div className="text-sm font-mono text-text-secondary">
                  {t(
                    currencyBalance.isUnavailable
                      ? 'currencyBreakdown.valuationUnavailable'
                      : 'currencyBreakdown.converting'
                  )}
                </div>
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
                {currencyBalance.isCrypto ? (
                  <PrivateAmount inline>
                    {formatExactNumber(currencyBalance.balance)} {currencyBalance.currency}
                  </PrivateAmount>
                ) : (
                  <ConvertedAmount
                    amount={currencyBalance.balance}
                    currency={currencyBalance.currency}
                    inline
                  />
                )}
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
          style={{ width: `${currencyBalance.percentage ?? 0}%` }}
        />
      </div>

      {/* Percentage */}
      <div className="flex items-center justify-between mt-1">
        <span className="text-xs text-text-secondary">
          {currencyBalance.percentage == null
            ? '—'
            : t('currencyBreakdown.percentOfTotal', {
                percent: formatDecimal(currencyBalance.percentage, 1),
              })}
        </span>
        {isForeignCurrency && !currencyBalance.isCrypto && currencyBalance.rate && (
          <span className="text-xs text-text-muted">
            {t('currencyBreakdown.rate', { rate: formatExchangeRate(currencyBalance.rate) })}
          </span>
        )}
      </div>
    </button>
  );
}
