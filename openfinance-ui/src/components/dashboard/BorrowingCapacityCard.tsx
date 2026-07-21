import { Activity, TrendingUp, TrendingDown, AlertCircle } from 'lucide-react';
import type { IBorrowingCapacity } from '@/types/dashboard';
import { ConvertedAmount } from '@/components/ui/ConvertedAmount';
import { useSecondaryConversion } from '@/hooks/useSecondaryConversion';
import { DEFAULT_CURRENCY } from '@/utils/currency';
import { useTranslation } from 'react-i18next';

interface BorrowingCapacityCardProps {
    capacity: IBorrowingCapacity;
}

/**
 * BorrowingCapacityCard - Displays borrowing capacity analysis
 * Dashboard Borrowing Capacity Card component
 */
export default function BorrowingCapacityCard({ capacity }: BorrowingCapacityCardProps) {
    const { convert, secondaryCurrency: secCurrency, secondaryExchangeRate } = useSecondaryConversion(capacity.currency || DEFAULT_CURRENCY);
    const { t } = useTranslation('dashboard');

    // Determine health status color and icon
    const getHealthStatusStyle = (status: string) => {
        switch (status) {
            case 'EXCELLENT':
                return { color: 'text-green-500', bgColor: 'bg-green-500/10', icon: TrendingUp };
            case 'GOOD':
                return { color: 'text-blue-500', bgColor: 'bg-blue-500/10', icon: Activity };
            case 'FAIR':
                return { color: 'text-yellow-500', bgColor: 'bg-yellow-500/10', icon: AlertCircle };
            case 'POOR':
                return { color: 'text-red-500', bgColor: 'bg-red-500/10', icon: TrendingDown };
            default:
                return { color: 'text-text-muted', bgColor: 'bg-surface-elevated', icon: Activity };
        }
    };

    const healthStyle = getHealthStatusStyle(capacity.financialHealthStatus);
    const HealthIcon = healthStyle.icon;

    // Format percentage
    const formatPercentage = (value: number) => {
        return `${value.toFixed(1)}%`;
    };

    // Generate insight text
    const getInsightText = () => {
        if (capacity.debtToIncomeRatio <= 20) {
            return t('borrowingCapacity.insights.excellent');
        } else if (capacity.debtToIncomeRatio <= 35) {
            return t('borrowingCapacity.insights.good');
        } else if (capacity.debtToIncomeRatio <= 50) {
            return t('borrowingCapacity.insights.fair');
        } else {
            return t('borrowingCapacity.insights.poor');
        }
    };

    return (
        <div className="bg-surface rounded-lg p-6 border border-border h-full flex flex-col">
            <div className="flex items-center justify-between mb-4">
                <div>
                    <h3 className="text-lg font-semibold text-text-primary">{t('borrowingCapacity.title')}</h3>
                    <p className="text-sm text-text-secondary">
                        {t('borrowingCapacity.subtitle', { days: capacity.analysisPeriod })}
                    </p>
                </div>
                <div className={`flex items-center gap-2 px-3 py-1 rounded-full ${healthStyle.bgColor}`}>
                    <HealthIcon className={`w-4 h-4 ${healthStyle.color}`} />
                    <span className={`text-sm font-medium ${healthStyle.color}`}>
                        {t(`borrowingCapacity.status.${capacity.financialHealthStatus}`)}
                    </span>
                </div>
            </div>

            <div className="space-y-6 flex-grow overflow-y-auto scrollbar-thin pr-2 min-h-0">
                {/* Available Borrowing Capacity - Main metric */}
                <div className="text-center p-4 bg-surface-elevated rounded-lg border border-border">
                    <p className="text-sm text-text-secondary mb-1">{t('borrowingCapacity.available')}</p>
                    <p className="text-3xl font-bold text-primary">
                        <ConvertedAmount
                            amount={capacity.availableBorrowingCapacity}
                            currency={capacity.currency || DEFAULT_CURRENCY}
                            isConverted={false}
                            secondaryAmount={convert(capacity.availableBorrowingCapacity)}
                            secondaryCurrency={secCurrency}
                            secondaryExchangeRate={secondaryExchangeRate}
                            inline
                        />
                    </p>
                </div>

                {/* Debt-to-Income Ratio Gauge */}
                <div>
                    <div className="flex justify-between items-center mb-2">
                        <span className="text-sm font-medium text-text-secondary">{t('borrowingCapacity.ratio')}</span>
                        <span className={`text-lg font-bold ${healthStyle.color}`}>
                            {formatPercentage(capacity.debtToIncomeRatio)}
                        </span>
                    </div>

                    {/* Progress bar */}
                    <div className="w-full bg-surface-elevated rounded-full h-3 overflow-hidden">
                        <div
                            className={`h-full transition-all duration-500 ${capacity.debtToIncomeRatio <= 20
                                ? 'bg-green-500'
                                : capacity.debtToIncomeRatio <= 35
                                    ? 'bg-blue-500'
                                    : capacity.debtToIncomeRatio <= 50
                                        ? 'bg-yellow-500'
                                        : 'bg-red-500'
                                }`}
                            style={{ width: `${Math.min(capacity.debtToIncomeRatio, 100)}%` }}
                        />
                    </div>

                    {/* Threshold markers */}
                    <div className="flex justify-between text-xs text-text-muted mt-1">
                        <span>0%</span>
                        <span>20%</span>
                        <span>40%</span>
                        <span>60%</span>
                        <span>100%</span>
                    </div>
                </div>

                {/* Financial Breakdown */}
                <div className="grid grid-cols-2 gap-4">
                    <div className="space-y-1">
                        <p className="text-xs text-text-secondary">{t('borrowingCapacity.monthlyIncome')}</p>
                        <p className={`text-lg font-semibold ${capacity.monthlyIncome > 0 ? 'text-green-500' : 'text-text-primary'}`}>
                            <ConvertedAmount
                                amount={capacity.monthlyIncome}
                                currency={capacity.currency || DEFAULT_CURRENCY}
                                isConverted={false}
                                secondaryAmount={convert(capacity.monthlyIncome)}
                                secondaryCurrency={secCurrency}
                                secondaryExchangeRate={secondaryExchangeRate}
                                inline
                            />
                        </p>
                    </div>

                    <div className="space-y-1">
                        <p className="text-xs text-text-secondary">{t('borrowingCapacity.monthlyExpenses')}</p>
                        <p className="text-lg font-semibold text-orange-500">
                            <ConvertedAmount
                                amount={capacity.monthlyExpenses}
                                currency={capacity.currency || DEFAULT_CURRENCY}
                                isConverted={false}
                                secondaryAmount={convert(capacity.monthlyExpenses)}
                                secondaryCurrency={secCurrency}
                                secondaryExchangeRate={secondaryExchangeRate}
                                inline
                            />
                        </p>
                    </div>

                    <div className="space-y-1">
                        <p className="text-xs text-text-secondary">{t('borrowingCapacity.monthlyDebt')}</p>
                        <p className={`text-lg font-semibold ${capacity.monthlyDebtPayments > 0 ? 'text-red-500' : 'text-text-primary'}`}>
                            <ConvertedAmount
                                amount={capacity.monthlyDebtPayments}
                                currency={capacity.currency || DEFAULT_CURRENCY}
                                isConverted={false}
                                secondaryAmount={convert(capacity.monthlyDebtPayments)}
                                secondaryCurrency={secCurrency}
                                secondaryExchangeRate={secondaryExchangeRate}
                                inline
                            />
                        </p>
                    </div>

                    <div className="space-y-1">
                        <p className="text-xs text-text-secondary">{t('borrowingCapacity.recommended')}</p>
                        <p className="text-lg font-semibold text-blue-500">
                            <ConvertedAmount
                                amount={capacity.recommendedMaxBorrowing}
                                currency={capacity.currency || DEFAULT_CURRENCY}
                                isConverted={false}
                                secondaryAmount={convert(capacity.recommendedMaxBorrowing)}
                                secondaryCurrency={secCurrency}
                                secondaryExchangeRate={secondaryExchangeRate}
                                inline
                            />/mo
                        </p>
                    </div>
                </div>

                {/* Insight */}
                <div className="p-3 bg-surface-elevated border border-border rounded-lg">
                    <p className="text-sm text-text-secondary">
                        <span className="font-semibold text-primary">{t('borrowingCapacity.insight')}:</span> {getInsightText()}
                    </p>
                </div>
            </div>
        </div>
    );
}
