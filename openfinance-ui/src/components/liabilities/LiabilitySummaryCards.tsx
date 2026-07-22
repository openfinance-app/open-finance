/**
 * LiabilitySummaryCards Component
 * Task 6.2.1: Summary cards for liabilities overview
 *
 * Displays 4 key metrics: Total Liabilities, Total Principal, Average Interest Rate, Monthly Payments.
 * All monetary totals are aggregated in the user's base currency using the conversion fields
 * (balanceInBaseCurrency / exchangeRate / isConverted) provided by the backend.
 *
 * Liabilities whose currency differs from the user's base currency AND for which no exchange
 * rate conversion is available (isConverted=false) are excluded from the numeric total to avoid
 * silently mixing incompatible currencies.  A warning badge lists the excluded currencies.
 */
import { useMemo } from 'react';
import { useTranslation } from 'react-i18next';
import { ConvertedAmount } from '@/components/ui/ConvertedAmount';
import { useAuthContext } from '@/context/AuthContext';
import { useSecondaryConversion } from '@/hooks/useSecondaryConversion';
import { add, multiply, sum, divide } from '@/utils/money';
import type { Liability } from '@/types/liability';

interface LiabilitySummaryCardsProps {
  /** All liabilities (unfiltered) — used for the primary global totals */
  liabilities: Liability[];
  /** Currently filtered / displayed liabilities — shown as smaller secondary totals when provided and different */
  filteredLiabilities?: Liability[];
  /** Whether a meaningful filter is actively applied */
  isActiveFilter?: boolean;
}

interface BaseCurrencyTotals {
  totalBalance: number;
  totalPrincipal: number;
  totalMinimumPayment: number;
  liabilitiesWithInterest: { balance: number; rate: number }[];
  /** Currencies that could not be converted and were excluded from totals */
  excludedCurrencies: Set<string>;
}

/**
 * Aggregate liabilities into base-currency totals.
 *
 * Inclusion rules:
 *  - isConverted=true  → use balanceInBaseCurrency (already in base currency)
 *  - isConverted=false AND currency===baseCurrency → use native amount (same currency)
 *  - isConverted=false AND currency!==baseCurrency → SKIP (add to excludedCurrencies)
 */
function aggregateToBaseCurrency(liabilities: Liability[], baseCurrency: string): BaseCurrencyTotals {
  return liabilities.reduce<BaseCurrencyTotals>(
    (acc, liability) => {
      const rate = liability.exchangeRate ?? 1;
      const useConverted = liability.isConverted === true && liability.exchangeRate != null;
      const isNativeCurrency = liability.currency === baseCurrency;

      if (!useConverted && !isNativeCurrency) {
        // Cannot convert — exclude from totals to prevent mixing currencies
        acc.excludedCurrencies.add(liability.currency);
        return acc;
      }

      // Balance
      const balance = useConverted
        ? (liability.balanceInBaseCurrency ?? multiply(liability.currentBalance, rate))
        : liability.currentBalance;

      // Principal (no pre-converted field from backend — multiply by rate)
      const principal = useConverted ? multiply(liability.principal, rate) : liability.principal;

      // Minimum payment
      const minimumPayment = useConverted
        ? multiply(liability.minimumPayment ?? 0, rate)
        : (liability.minimumPayment ?? 0);

      acc.totalBalance = add(acc.totalBalance, balance);
      acc.totalPrincipal = add(acc.totalPrincipal, principal);
      acc.totalMinimumPayment = add(acc.totalMinimumPayment, minimumPayment);

      if (liability.interestRate && liability.interestRate > 0) {
        acc.liabilitiesWithInterest.push({ balance, rate: liability.interestRate });
      }

      return acc;
    },
    {
      totalBalance: 0,
      totalPrincipal: 0,
      totalMinimumPayment: 0,
      liabilitiesWithInterest: [],
      excludedCurrencies: new Set<string>(),
    }
  );
}

/** Calculate weighted average interest rate string */
function weightedAvgRate(totals: BaseCurrencyTotals): string {
  if (totals.liabilitiesWithInterest.length === 0) return 'N/A';

  const totalWeightedRate = sum(
    totals.liabilitiesWithInterest.map((item) => multiply(item.balance, item.rate))
  );
  const totalBalance = sum(totals.liabilitiesWithInterest.map((item) => item.balance));

  if (totalBalance === 0) return 'N/A';
  return `${divide(totalWeightedRate, totalBalance).toFixed(2)}%`;
}

export function LiabilitySummaryCards({ liabilities, filteredLiabilities, isActiveFilter }: LiabilitySummaryCardsProps) {
  const { baseCurrency } = useAuthContext();
  const { convert, secondaryCurrency: secCurrency, secondaryExchangeRate } = useSecondaryConversion(baseCurrency);
  const { t } = useTranslation('liabilities');

  const globalTotals = useMemo(
    () => aggregateToBaseCurrency(liabilities, baseCurrency),
    [liabilities, baseCurrency]
  );
  const filteredTotals = useMemo(
    () => (filteredLiabilities ? aggregateToBaseCurrency(filteredLiabilities, baseCurrency) : null),
    [filteredLiabilities, baseCurrency]
  );

  /** Whether a meaningful filter is active */
  const isFiltered = isActiveFilter !== undefined
    ? isActiveFilter
    : (filteredLiabilities !== undefined && filteredLiabilities.length !== liabilities.length);

  /** Comma-separated list of currencies excluded from the global total */
  const excludedNote = globalTotals.excludedCurrencies.size > 0
    ? `Excl. ${Array.from(globalTotals.excludedCurrencies).join(', ')} (no rate)`
    : null;

  return (
    <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-4 gap-4">
      {/* Card 1: Total Liabilities */}
      <div className="p-4 bg-surface border border-border rounded-lg hover:border-primary/30 transition-colors">
        <div className="text-sm text-text-secondary mb-1">{t('summary.totalLiabilities')}</div>
        <div className="text-2xl font-bold text-text-primary">
          <ConvertedAmount
            amount={globalTotals.totalBalance}
            currency={baseCurrency}
            isConverted={false}
            secondaryAmount={convert(globalTotals.totalBalance)}
            secondaryCurrency={secCurrency}
            secondaryExchangeRate={secondaryExchangeRate}
            inline
          />
        </div>
        <div className="text-xs text-text-tertiary mt-1">
          {t('summary.liabilityCount', { count: liabilities.length })}
        </div>
        {excludedNote && (
          <div className="text-xs text-warning mt-1" title="These currencies could not be converted">
            ⚠ {excludedNote}
          </div>
        )}
        {isFiltered && filteredTotals && (
          <div className="text-xs text-text-tertiary mt-1">
            {t('summary.filtered')}: <ConvertedAmount
              amount={filteredTotals.totalBalance}
              currency={baseCurrency}
              isConverted={false}
              secondaryAmount={convert(filteredTotals.totalBalance)}
              secondaryCurrency={secCurrency}
              secondaryExchangeRate={secondaryExchangeRate}
              inline
            />
          </div>
        )}
      </div>

      {/* Card 2: Total Principal */}
      <div className="p-4 bg-surface border border-border rounded-lg hover:border-primary/30 transition-colors">
        <div className="text-sm text-text-secondary mb-1">{t('summary.originalPrincipal')}</div>
        <div className="text-2xl font-bold text-text-primary">
          <ConvertedAmount
            amount={globalTotals.totalPrincipal}
            currency={baseCurrency}
            isConverted={false}
            secondaryAmount={convert(globalTotals.totalPrincipal)}
            secondaryCurrency={secCurrency}
            secondaryExchangeRate={secondaryExchangeRate}
            inline
          />
        </div>
        <div className="text-xs text-text-tertiary mt-1">
          {t('summary.liabilityCount', { count: liabilities.length })}
        </div>
        {isFiltered && filteredTotals && (
          <div className="text-xs text-text-tertiary">
            {t('summary.filtered')}: <ConvertedAmount
              amount={filteredTotals.totalPrincipal}
              currency={baseCurrency}
              isConverted={false}
              secondaryAmount={convert(filteredTotals.totalPrincipal)}
              secondaryCurrency={secCurrency}
              secondaryExchangeRate={secondaryExchangeRate}
              inline
            />
            {filteredLiabilities && (
              <span className="ml-1">{t('summary.shownCount', { count: filteredLiabilities.length })}</span>
            )}
          </div>
        )}
      </div>

      {/* Card 3: Average Interest Rate */}
      <div className="p-4 bg-surface border border-border rounded-lg hover:border-primary/30 transition-colors">
        <div className="text-sm text-text-secondary mb-1">{t('summary.avgInterestRate')}</div>
        <div className="text-2xl font-bold text-text-primary">
          {weightedAvgRate(globalTotals)}
        </div>
        <div className="text-xs text-text-tertiary mt-1">
          {globalTotals.liabilitiesWithInterest.length > 0
            ? t('summary.withInterest', { count: globalTotals.liabilitiesWithInterest.length })
            : t('summary.noInterestSet')}
        </div>
        {isFiltered && filteredTotals && (
          <div className="text-xs text-text-tertiary">
            {t('summary.filtered')}: {weightedAvgRate(filteredTotals)}
          </div>
        )}
      </div>

      {/* Card 4: Monthly Payments */}
      <div className="p-4 bg-surface border border-border rounded-lg hover:border-primary/30 transition-colors">
        <div className="text-sm text-text-secondary mb-1">{t('summary.monthlyPayments')}</div>
        <div className="text-2xl font-bold text-text-primary">
          <ConvertedAmount
            amount={globalTotals.totalMinimumPayment}
            currency={baseCurrency}
            isConverted={false}
            secondaryAmount={convert(globalTotals.totalMinimumPayment)}
            secondaryCurrency={secCurrency}
            secondaryExchangeRate={secondaryExchangeRate}
            inline
          />
        </div>
        <div className="text-xs text-text-tertiary mt-1">
          {globalTotals.totalMinimumPayment > 0 ? t('summary.minimumDue') : t('summary.noPaymentsSet')}
        </div>
        {isFiltered && filteredTotals && (
          <div className="text-xs text-text-tertiary">
            {t('summary.filteredLabel')}<ConvertedAmount
              amount={filteredTotals.totalMinimumPayment}
              currency={baseCurrency}
              isConverted={false}
              secondaryAmount={convert(filteredTotals.totalMinimumPayment)}
              secondaryCurrency={secCurrency}
              secondaryExchangeRate={secondaryExchangeRate}
              inline
            />
          </div>
        )}
      </div>
    </div>
  );
}

