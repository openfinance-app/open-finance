import { useNavigate } from 'react-router';
import { Building2 } from 'lucide-react';
import { useTranslation } from 'react-i18next';
import { useAccounts } from '@/hooks/useAccounts';
import { useAssets } from '@/hooks/useAssets';
import { useLiabilities } from '@/hooks/useLiabilities';
import { useSecondaryConversion } from '@/hooks/useSecondaryConversion';
import { ConvertedAmount } from '@/components/ui/ConvertedAmount';
import { cn } from '@/lib/utils';
import { DEFAULT_CURRENCY } from '@/utils/currency';
import { add, multiply, percentage, subtract, sum } from '@/utils/money';
import { formatDecimal } from '@/utils/format';

interface InstitutionGroup {
  id: number | null;
  name: string;
  logo?: string;
  totalBalance: number;
  accountCount: number;
}

export default function InstitutionBreakdown({
  baseCurrency = DEFAULT_CURRENCY,
}: {
  baseCurrency?: string;
}) {
  const { t } = useTranslation('dashboard');
  const navigate = useNavigate();
  const accounts = useAccounts();
  const assets = useAssets();
  const liabilities = useLiabilities();
  const { convert, secondaryCurrency, secondaryExchangeRate } =
    useSecondaryConversion(baseCurrency);
  const groups = new Map<number | null, InstitutionGroup>();
  let unavailable = false;
  const group = (institution?: { id: number; name: string; logo?: string }): InstitutionGroup => {
    const id = institution?.id ?? null;
    if (!groups.has(id))
      groups.set(id, {
        id,
        name: institution?.name ?? t('institutionBreakdown.noInstitution'),
        logo: institution?.logo,
        totalBalance: 0,
        accountCount: 0,
      });
    return groups.get(id)!;
  };
  const converted = (
    amount: number,
    currency: string,
    baseAmount?: number,
    isConverted?: boolean
  ): number | undefined => {
    if (currency === baseCurrency || amount === 0) return amount;
    if (baseAmount != null && isConverted) return baseAmount;
    unavailable = true;
    return undefined;
  };
  for (const account of accounts.data ?? []) {
    const row = group(account.institution);
    row.accountCount += 1;
    const value = converted(
      account.ownBalance,
      account.currency,
      account.exchangeRate != null ? multiply(account.ownBalance, account.exchangeRate) : undefined,
      account.isConverted
    );
    if (value == null) continue;
    row.totalBalance = add(row.totalBalance, value);
  }
  for (const asset of assets.data ?? []) {
    if (asset.acquisitionType === 'PLANNED') continue;
    const account = accounts.data?.find(a => a.id === asset.accountId);
    const value = converted(
      asset.totalValue,
      asset.currency,
      asset.valueInBaseCurrency,
      asset.isConverted
    );
    if (value != null) {
      const row = group(account?.institution);
      row.totalBalance = add(row.totalBalance, value);
    }
  }
  for (const liability of liabilities.data ?? []) {
    // Explicit account source identifies the same credit-card debt; never guess from its name.
    if (liability.representedByAccountId != null) continue;
    const row = group(liability.institution);
    const value = converted(
      liability.currentBalance,
      liability.currency,
      liability.balanceInBaseCurrency,
      liability.isConverted
    );
    if (value != null) row.totalBalance = subtract(row.totalBalance, value);
  }
  const rows = [...groups.values()].sort(
    (a, b) => b.totalBalance - a.totalBalance || a.name.localeCompare(b.name)
  );
  const grandTotal = sum(rows.map(row => row.totalBalance));
  const loading = accounts.isLoading || assets.isLoading || liabilities.isLoading;
  const error = accounts.error || assets.error || liabilities.error;
  const amount = (value: number) => (
    <ConvertedAmount
      amount={value}
      currency={baseCurrency}
      isConverted={false}
      secondaryAmount={convert(value)}
      secondaryCurrency={secondaryCurrency}
      secondaryExchangeRate={secondaryExchangeRate}
      inline
    />
  );

  if (loading) {
    return (
      <div
        className="bg-surface rounded-lg p-6 border border-border animate-pulse"
        role="status"
        aria-label={t('institutionBreakdown.loading')}
      >
        <div className="h-6 bg-surface-elevated rounded w-52 mb-4" />
        <div className="h-12 bg-surface-elevated rounded mb-4" />
        <div className="space-y-3">
          {[1, 2, 3].map(i => (
            <div key={i} className="h-10 bg-surface-elevated rounded" />
          ))}
        </div>
      </div>
    );
  }

  if (error) {
    return (
      <div className="bg-surface rounded-lg p-6 border border-red-500/50">
        <div className="flex items-center gap-2 mb-4">
          <Building2 className="h-5 w-5 text-red-500" />
          <h3 className="plate-label ">{t('institutionBreakdown.title')}</h3>
        </div>
        <p role="alert" className="text-sm text-red-500">
          {t('institutionBreakdown.loadError')}
        </p>
      </div>
    );
  }

  return (
    <div className="bg-surface rounded-lg p-6 border border-border hover:border-border/70 transition-colors h-full flex flex-col">
      <div className="flex items-center gap-2 mb-4">
        <Building2 className="h-5 w-5 text-primary" />
        <h3 className="plate-label ">{t('institutionBreakdown.title')}</h3>
      </div>
      {unavailable && (
        <p role="alert" className="text-sm text-warning mb-3">
          {t('institutionBreakdown.missingRates')}
        </p>
      )}
      {rows.length === 0 ? (
        !unavailable && <p className="text-sm text-text-secondary">{t('accountsCard.empty')}</p>
      ) : (
        <>
          <div className="mb-6">
            <div className="text-xs text-text-secondary mb-1">
              {t('institutionBreakdown.total')}
            </div>
            <div className="text-3xl font-bold text-primary font-mono">{amount(grandTotal)}</div>
          </div>
          <div className="space-y-4 flex-1 overflow-y-auto scrollbar-thin pr-2 min-h-0">
            {rows.map(row => {
              const clickable = row.id !== null && row.accountCount > 0;
              const RowTag = clickable ? 'button' : 'div';
              // Percentages are only meaningful against a positive total —
              // with a zero/negative net total they would all read 0.0%.
              const showPercent = grandTotal > 0;
              const percent = showPercent ? percentage(row.totalBalance, grandTotal) : 0;
              return (
                <RowTag
                  key={row.id ?? 'none'}
                  type={clickable ? 'button' : undefined}
                  onClick={
                    clickable
                      ? () => navigate(`/accounts?institution=${encodeURIComponent(row.name)}`)
                      : undefined
                  }
                  className={cn(
                    'w-full text-left py-1 rounded-lg px-2 -mx-2 transition-colors',
                    clickable && 'hover:bg-surface-elevated cursor-pointer'
                  )}
                  aria-label={
                    clickable
                      ? t('institutionBreakdown.viewAccounts', { institution: row.name })
                      : undefined
                  }
                >
                  <div className="flex items-center justify-between mb-2">
                    <div className="flex items-center gap-2 min-w-0">
                      {row.logo ? (
                        <img
                          src={row.logo}
                          alt=""
                          className="h-6 w-6 rounded object-contain bg-surface flex-shrink-0"
                        />
                      ) : (
                        <div className="h-6 w-6 rounded-full bg-primary/20 flex items-center justify-center flex-shrink-0">
                          <Building2 className="h-3.5 w-3.5 text-primary" />
                        </div>
                      )}
                      <span className="text-sm font-semibold text-text-primary truncate">
                        {row.name}
                      </span>
                      <span className="text-xs text-text-secondary flex-shrink-0">
                        ({t('institutionBreakdown.accountCount', { count: row.accountCount })})
                      </span>
                    </div>
                    <div className="text-sm font-mono text-text-primary flex-shrink-0 ml-2">
                      {amount(row.totalBalance)}
                    </div>
                  </div>
                  {showPercent && (
                    <>
                      <div className="w-full bg-surface-elevated rounded-full h-2 overflow-hidden">
                        <div
                          className="bg-primary h-full rounded-full progress-fill"
                          style={{
                            transform: `scaleX(${Math.max(0, Math.min(100, percent)) / 100})`,
                          }}
                        />
                      </div>
                      <div className="mt-1 text-xs text-text-secondary">
                        {t('institutionBreakdown.percentOfTotal', {
                          percent: formatDecimal(percent, 1),
                        })}
                      </div>
                    </>
                  )}
                </RowTag>
              );
            })}
          </div>
        </>
      )}
    </div>
  );
}
