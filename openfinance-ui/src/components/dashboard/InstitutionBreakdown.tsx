/**
 * InstitutionBreakdown – Dashboard card showing total balance per institution.
 *
 * INST-001: Dashboard missing breakdown by institution.
 *
 * Groups account balances (using balanceInBaseCurrency when available) by
 * financial institution.  Accounts with no institution are bucketed under a
 * "Direct / No Institution" entry.
 */

import { Building2 } from 'lucide-react';
import { useTranslation } from 'react-i18next';
import { useAccounts } from '@/hooks/useAccounts';
import { useAssets } from '@/hooks/useAssets';
import { ConvertedAmount } from '../ui/ConvertedAmount';
import { useSecondaryConversion } from '@/hooks/useSecondaryConversion';
import { DEFAULT_CURRENCY } from '@/utils/currency';

interface InstitutionBreakdownProps {
    baseCurrency?: string;
}

interface InstitutionGroup {
    id: number | null;
    name: string;
    logo?: string;
    totalBalance: number;   // in base currency
    accountCount: number;
    percentage: number;
}

export default function InstitutionBreakdown({
    baseCurrency = DEFAULT_CURRENCY,
}: InstitutionBreakdownProps) {
    const { t } = useTranslation('dashboard');
    const { data: accounts, isLoading: accountsLoading, error: accountsError } = useAccounts();
    const { data: assets, isLoading: assetsLoading, error: assetsError } = useAssets();
    const { convert, secondaryCurrency: secCurrency, secondaryExchangeRate } = useSecondaryConversion(baseCurrency);

    const isLoading = accountsLoading || assetsLoading;
    const error = accountsError || assetsError;

    /* ── Loading ─────────────────────────────────────────────────────────────── */
    if (isLoading) {
        return (
            <div className="bg-surface rounded-lg p-6 border border-border animate-pulse">
                <div className="h-6 bg-surface-elevated rounded w-52 mb-4" />
                <div className="h-12 bg-surface-elevated rounded mb-4" />
                <div className="space-y-3">
                    {[1, 2, 3].map((i) => (
                        <div key={i} className="h-10 bg-surface-elevated rounded" />
                    ))}
                </div>
            </div>
        );
    }

    /* ── Error ───────────────────────────────────────────────────────────────── */
    if (error) {
        return (
            <div className="bg-surface rounded-lg p-6 border border-red-500/50">
                <div className="flex items-center gap-2 mb-4">
                    <Building2 className="h-5 w-5 text-red-500" />
                    <h3 className="text-lg font-semibold text-text-primary">
                        {t('institutionBreakdown.title')}
                    </h3>
                </div>
                <p className="text-sm text-red-500">
                    {error instanceof Error ? error.message : t('institutionBreakdown.loadError')}
                </p>
            </div>
        );
    }

    /* ── Empty ───────────────────────────────────────────────────────────────── */
    const hasNoData = (!accounts || accounts.length === 0) && (!assets || assets.length === 0);
    if (hasNoData) {
        return (
            <div className="bg-surface rounded-lg p-6 border border-border h-full flex flex-col">
                <div className="flex items-center gap-2 mb-4">
                    <Building2 className="h-5 w-5 text-primary" />
                    <h3 className="text-lg font-semibold text-text-primary">
                        {t('institutionBreakdown.title')}
                    </h3>
                </div>
                <p className="text-sm text-text-secondary">{t('accountsCard.empty')}</p>
            </div>
        );
    }

    /* ── Aggregate ───────────────────────────────────────────────────────────── */
    const groupMap = new Map<number | null, InstitutionGroup>();

    // Process Accounts
    if (accounts) {
        for (const account of accounts) {
            const inst = account.institution ?? null;
            const key = inst?.id ?? null;
            const value = account.balanceInBaseCurrency ?? account.balance;

            if (groupMap.has(key)) {
                const g = groupMap.get(key)!;
                g.totalBalance += value;
                g.accountCount += 1;
            } else {
                groupMap.set(key, {
                    id: key,
                    name: inst?.name ?? t('institutionBreakdown.noInstitution'),
                    logo: inst?.logo,
                    totalBalance: value,
                    accountCount: 1,
                    percentage: 0,
                });
            }
        }
    }

    // Process Assets
    if (assets) {
        for (const asset of assets) {
            // Find account if linked
            const linkedAccount = asset.accountId ? accounts?.find(a => a.id === asset.accountId) : null;
            const inst = linkedAccount?.institution ?? null;
            const key = inst?.id ?? null;
            const value = asset.valueInBaseCurrency ?? asset.totalValue;

            if (groupMap.has(key)) {
                const g = groupMap.get(key)!;
                g.totalBalance += value;
                // We don't increment accountCount for assets, or maybe we should call it "Item Count"?
                // Let's keep accountCount as is for now or just not increment it.
            } else {
                groupMap.set(key, {
                    id: key,
                    name: inst?.name ?? t('institutionBreakdown.noInstitution'),
                    logo: inst?.logo,
                    totalBalance: value,
                    accountCount: 0, // Initial count for an asset-only entry
                    percentage: 0,
                });
            }
        }
    }

    const grandTotal = Array.from(groupMap.values()).reduce(
        (sum, g) => sum + g.totalBalance,
        0,
    );

    const groups: InstitutionGroup[] = Array.from(groupMap.values())
        .map((g) => ({
            ...g,
            percentage: grandTotal > 0 ? (g.totalBalance / grandTotal) * 100 : 0,
        }))
        .sort((a, b) => b.totalBalance - a.totalBalance);

    /* ── Render ──────────────────────────────────────────────────────────────── */
    return (
        <div className="bg-surface rounded-lg p-6 border border-border hover:border-border/70 transition-colors h-full flex flex-col">
            {/* Header */}
            <div className="flex items-center gap-2 mb-4">
                <Building2 className="h-5 w-5 text-primary" />
                <h3 className="text-lg font-semibold text-text-primary">
                    {t('institutionBreakdown.title')}
                </h3>
            </div>

            {/* Grand total */}
            <div className="mb-6">
                <div className="text-xs text-text-secondary mb-1">{t('institutionBreakdown.total')}</div>
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

            {/* Institution rows */}
            <div className="space-y-4 flex-1 overflow-y-auto scrollbar-thin pr-2 min-h-0">
                {groups.map((group) => (
                    <div key={group.id ?? 'none'} className="py-1">
                        {/* Logo + name + count */}
                        <div className="flex items-center justify-between mb-2">
                            <div className="flex items-center gap-2 min-w-0">
                                {group.logo ? (
                                    <img
                                        src={group.logo}
                                        alt=""
                                        className="h-6 w-6 rounded object-contain bg-surface flex-shrink-0"
                                    />
                                ) : (
                                    <div className="h-6 w-6 rounded-full bg-primary/20 flex items-center justify-center flex-shrink-0">
                                        <Building2 className="h-3.5 w-3.5 text-primary" />
                                    </div>
                                )}
                                <span className="text-sm font-semibold text-text-primary truncate">
                                    {group.name}
                                </span>
                                <span className="text-xs text-text-secondary flex-shrink-0">
                                    ({t('institutionBreakdown.accountCount', { count: group.accountCount })})
                                </span>
                            </div>

                            {/* Balance */}
                            <div className="text-sm font-mono text-text-primary flex-shrink-0 ml-2">
                                <ConvertedAmount
                                    amount={group.totalBalance}
                                    currency={baseCurrency}
                                    isConverted={false}
                                    secondaryAmount={convert(group.totalBalance)}
                                    secondaryCurrency={secCurrency}
                                    secondaryExchangeRate={secondaryExchangeRate}
                                    inline
                                />
                            </div>
                        </div>

                        {/* Progress bar */}
                        <div className="w-full bg-surface-elevated rounded-full h-2 overflow-hidden">
                            <div
                                className="bg-primary h-full rounded-full transition-all duration-300"
                                style={{ width: `${group.percentage}%` }}
                            />
                        </div>

                        {/* Percentage */}
                        <div className="mt-1 text-xs text-text-secondary">
                            {t('institutionBreakdown.percentOfTotal', { percent: group.percentage.toFixed(1) })}
                        </div>
                    </div>
                ))}
            </div>
        </div>
    );
}
