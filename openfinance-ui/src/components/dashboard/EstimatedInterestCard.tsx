import { useState, useMemo } from 'react';
import { PiggyBank, TrendingUp } from 'lucide-react';
import { useTranslation } from 'react-i18next';
import type { IEstimatedInterestSummary } from '@/types/dashboard';
import { ConvertedAmount } from '../ui/ConvertedAmount';
import { useSecondaryConversion } from '@/hooks/useSecondaryConversion';
import { DEFAULT_CURRENCY } from '@/utils/currency';
import { SimpleSelect } from '../ui/SimpleSelect';

interface EstimatedInterestCardProps {
    summary: IEstimatedInterestSummary;
    period: string;
}

/**
 * EstimatedInterestCard - Displays estimated interest earned across accounts
 * Dashboard Estimated Interest component
 */
export default function EstimatedInterestCard({ summary, period }: EstimatedInterestCardProps) {
    const [filterType, setFilterType] = useState<'ALL' | 'ACCOUNTS' | 'LIABILITIES'>('ALL');
    const { convert, secondaryCurrency: secCurrency, secondaryExchangeRate } = useSecondaryConversion(summary.currency || DEFAULT_CURRENCY);
    const { t } = useTranslation('dashboard');

    const periodLabel = t(`estimatedInterest.period.${period}`, {
        defaultValue: t('estimatedInterest.period.default'),
    });

    const filteredAccounts = useMemo(() => {
        if (!summary || !summary.accounts) return [];
        return summary.accounts.filter(acc => {
            if (filterType === 'ALL') return true;
            if (filterType === 'ACCOUNTS') return acc.projectedInterest >= 0;
            if (filterType === 'LIABILITIES') return acc.projectedInterest < 0;
            return true;
        });
    }, [summary, filterType]);

    const displayEarned = filterType === 'ALL' ? summary.totalEarned : filteredAccounts.reduce((sum, acc) => sum + (acc.interestEarned || 0), 0);
    const displayProjected = filterType === 'ALL' ? summary.totalProjected : filteredAccounts.reduce((sum, acc) => sum + (acc.projectedInterest || 0), 0);

    return (
        <div className="bg-surface rounded-lg p-6 border border-border h-full flex flex-col">
            <div className="flex items-center justify-between mb-4">
                <div className="flex-1">
                    <h3 className="text-lg font-semibold text-text-primary">{t('estimatedInterest.title')}</h3>
                    <p className="text-sm text-text-secondary">
                        {t('estimatedInterest.subtitle', { period: periodLabel })}
                    </p>
                </div>
                <div className="flex items-center gap-3">
                    <div className="w-36">
                        <SimpleSelect
                            value={filterType}
                            onChange={(e) => setFilterType(e.target.value as 'ALL' | 'ACCOUNTS' | 'LIABILITIES')}
                        >
                            <option value="ALL">{t('estimatedInterest.filter.all', 'All')}</option>
                            <option value="ACCOUNTS">{t('estimatedInterest.filter.accounts', 'Accounts')}</option>
                            <option value="LIABILITIES">{t('estimatedInterest.filter.liabilities', 'Liabilities')}</option>
                        </SimpleSelect>
                    </div>
                    <div className="flex items-center justify-center w-10 h-10 rounded-full bg-green-500/10 text-green-500 shrink-0">
                        <PiggyBank className="w-5 h-5" />
                    </div>
                </div>
            </div>

            <div className="space-y-6 flex-grow flex flex-col justify-between">
                {/* Total Earned - Main metric */}
                <div className="text-center p-4 bg-surface-elevated rounded-lg border border-border">
                    <p className="text-sm text-text-secondary mb-1">{t('estimatedInterest.totalEarned')}</p>
                    <p className="text-3xl font-bold text-green-500">
                        <ConvertedAmount
                            amount={displayEarned}
                            currency={summary.currency || DEFAULT_CURRENCY}
                            isConverted={false}
                            secondaryAmount={convert(displayEarned)}
                            secondaryCurrency={secCurrency}
                            secondaryExchangeRate={secondaryExchangeRate}
                            inline
                        />
                    </p>
                    <div className="mt-2 flex items-center justify-center text-xs text-text-secondary gap-1">
                        <TrendingUp className="w-3 h-3 text-blue-400" />
                        <span>{t('estimatedInterest.projectedNextYear')}: <span className="text-text-primary font-medium"><ConvertedAmount
                            amount={displayProjected}
                            currency={summary.currency || DEFAULT_CURRENCY}
                            isConverted={false}
                            secondaryAmount={convert(displayProjected)}
                            secondaryCurrency={secCurrency}
                            secondaryExchangeRate={secondaryExchangeRate}
                            inline
                        /></span></span>
                    </div>
                </div>

                {/* Account Breakdown */}
                <div className="flex-grow overflow-auto pr-2 scrollbar-thin space-y-3">
                    {filteredAccounts.length === 0 ? (
                        <div className="text-center text-text-muted py-4 text-sm">
                            {t('estimatedInterest.noData')}
                        </div>
                    ) : (
                        filteredAccounts.map((account, index) => (
                            <div key={`${account.accountId}-${account.projectedInterest < 0 ? 'liability' : 'account'}-${index}`} className="flex justify-between items-center p-3 hover:bg-surface-elevated rounded-lg transition-colors border border-transparent hover:border-border">
                                <div className="flex items-center gap-3 overflow-hidden">
                                    <div className="w-8 h-8 rounded bg-blue-500/10 flex items-center justify-center text-blue-500 shrink-0">
                                        <PiggyBank className="w-4 h-4" />
                                    </div>
                                    <span className="text-sm font-medium text-text-primary truncate">
                                        {account.accountName}
                                    </span>
                                </div>
                                <div className="text-right shrink-0">
                                    <div className="text-sm font-bold text-green-500">
                                        <ConvertedAmount
                                            amount={account.interestEarned}
                                            currency={summary.currency || DEFAULT_CURRENCY}
                                            isConverted={false}
                                            secondaryAmount={convert(account.interestEarned)}
                                            secondaryCurrency={secCurrency}
                                            secondaryExchangeRate={secondaryExchangeRate}
                                            inline
                                        />
                                    </div>
                                    <div className="text-xs text-text-muted">
                                        {t('estimatedInterest.projShort')}: <ConvertedAmount
                                            amount={account.projectedInterest}
                                            currency={summary.currency || DEFAULT_CURRENCY}
                                            isConverted={false}
                                            secondaryAmount={convert(account.projectedInterest)}
                                            secondaryCurrency={secCurrency}
                                            secondaryExchangeRate={secondaryExchangeRate}
                                            inline
                                        />
                                    </div>
                                </div>
                            </div>
                        ))
                    )}
                </div>
            </div>
        </div>
    );
}
