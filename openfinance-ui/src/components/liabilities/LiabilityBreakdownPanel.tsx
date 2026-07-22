/**
 * LiabilityBreakdownPanel Component
 * Task 22: Create LiabilityBreakdownPanel component showing full cost breakdown
 *
 * Requirement 2.1: Display comprehensive cost breakdown for a liability including
 * principal paid, interest paid, insurance paid, fees paid, and projections.
 *
 * Note: The linked transactions list has moved to the "Linked Payments" tab in
 * LiabilityDetailDialog to provide a cleaner separation of concerns.
 *
 * REQ-10.2: All monetary amounts are displayed via ConvertedAmount so they respect
 * the user's currency display mode (base / native / both). Breakdown amounts are
 * in the liability's native currency; the parent Liability object's exchangeRate /
 * baseCurrency / isConverted are used to derive the converted value client-side.
 */
import { TrendingDown, DollarSign, Shield, AlertCircle, Home } from 'lucide-react';
import type { ReactNode } from 'react';
import { useTranslation } from 'react-i18next';
import { ConvertedAmount } from '@/components/ui/ConvertedAmount';
import { useLiabilityBreakdown } from '@/hooks/useLiabilities';
import { multiply, percentage } from '@/utils/money';
import type { Liability } from '@/types/liability';

interface LiabilityBreakdownPanelProps {
  liability: Liability;
}

/**
 * Breakdown row for a single metric.
 * Uses ConvertedAmount so the display mode (base/native/both) is honoured.
 */
function BreakdownRow({
  label,
  value,
  liability,
  highlight,
  secondary,
}: {
  label: string;
  value: number | undefined;
  liability: Liability;
  highlight?: boolean;
  secondary?: boolean;
}) {
  if (value === undefined || value === null) return null;

  const convertedAmount =
    liability.isConverted && liability.exchangeRate != null
      ? multiply(value, liability.exchangeRate)
      : undefined;

  return (
    <div
      className={`flex justify-between items-center py-2 ${highlight
        ? 'font-semibold text-text-primary'
        : secondary
          ? 'text-text-tertiary'
          : 'text-text-secondary'
        }`}
    >
      <span className="text-sm">{label}</span>
      <span className={`text-sm font-mono ${highlight ? 'text-text-primary' : ''}`}>
        <ConvertedAmount
          amount={value}
          currency={liability.currency}
          convertedAmount={convertedAmount}
          baseCurrency={liability.baseCurrency}
          exchangeRate={liability.exchangeRate}
          isConverted={liability.isConverted}
          inline
        />
      </span>
    </div>
  );
}

/**
 * Section card with title and rows
 */
function BreakdownSection({
  title,
  icon,
  children,
}: {
  title: string;
  icon: ReactNode;
  children: ReactNode;
}) {
  return (
    <div className="bg-surface border border-border rounded-lg p-4">
      <div className="flex items-center gap-2 mb-3">
        <span className="text-primary">{icon}</span>
        <h4 className="text-sm font-semibold text-text-primary">{title}</h4>
      </div>
      <div className="divide-y divide-border">{children}</div>
    </div>
  );
}

/**
 * LiabilityBreakdownPanel displays the full cost breakdown for a liability.
 * Requirement 2.1: Show principal paid, interest paid, insurance paid, fees paid,
 * total paid, and projections for the remaining term.
 */
export function LiabilityBreakdownPanel({ liability }: LiabilityBreakdownPanelProps) {
  const { data: breakdown, isLoading: isLoadingBreakdown, error: breakdownError } = useLiabilityBreakdown(liability.id);
  const { t } = useTranslation('liabilities');

  if (isLoadingBreakdown) {
    return (
      <div className="space-y-3 animate-pulse">
        {[...Array(3)].map((_, i) => (
          <div key={i} className="h-28 bg-surface border border-border rounded-lg" />
        ))}
      </div>
    );
  }

  if (breakdownError) {
    return (
      <div className="flex items-center gap-2 p-4 bg-error/10 border border-error/20 rounded-lg text-error text-sm">
        <AlertCircle className="h-4 w-4 flex-shrink-0" />
        <span>{t('breakdown.loadError')}</span>
      </div>
    );
  }

  if (!breakdown) return null;

  const progressPercent =
    breakdown.principal > 0
      ? Math.min(100, Math.max(0, percentage(breakdown.principalPaid, breakdown.principal)))
      : 0;

  const rate = liability.exchangeRate;
  const isConverted = liability.isConverted;

  /** Helper: convert a native breakdown amount to base-currency if conversion is available */
  const toBase = (amount: number): number | undefined =>
    isConverted && rate != null ? multiply(amount, rate) : undefined;

  return (
    <div className="space-y-4">
      {/* Linked Asset */}
      {liability.linkedPropertyId && liability.linkedPropertyName && (
        <div className="bg-primary/5 border border-primary/20 rounded-lg p-3 flex items-center justify-between">
          <div className="flex items-center gap-3">
            <div className="p-2 bg-primary/10 rounded-full text-primary">
              <Home className="h-4 w-4" />
            </div>
            <div>
        <p className="text-xs text-text-tertiary font-medium uppercase tracking-wider">{t('breakdown.linkedAsset')}</p>
              <p className="text-sm font-medium text-text-primary">{liability.linkedPropertyName}</p>
            </div>
          </div>
        </div>
      )}

      {/* Institution */}
      {liability.institution && liability.institution.name && (
        <div className="bg-surface border border-border rounded-lg p-3 flex items-center justify-between">
          <div className="flex items-center gap-3">
            <div className="p-2 bg-primary/10 rounded-full text-primary">
              <span className="flex h-4 w-4 items-center justify-center shrink-0">
                {liability.institution.logo ? (
                  <img
                    src={liability.institution.logo}
                    alt=""
                    className="h-4 w-4 rounded object-contain bg-white"
                  />
                ) : (
                  <Home className="h-4 w-4" />
                )}
              </span>
            </div>
            <div>
              <p className="text-xs text-text-tertiary font-medium uppercase tracking-wider">{t('breakdown.institution')}</p>
              <p className="text-sm font-medium text-text-primary">{liability.institution.name}</p>
            </div>
          </div>
        </div>
      )}

      {/* Progress bar - paid off */}
      <div>
        <div className="flex justify-between text-xs text-text-secondary mb-1">
          <span>{t('breakdown.principalPaidOff')}</span>
          <span>{progressPercent.toFixed(1)}%</span>
        </div>
        <div className="h-2 bg-surface-elevated rounded-full overflow-hidden">
          <div
            className="h-full bg-success transition-all duration-300"
            style={{ width: `${progressPercent}%` }}
          />
        </div>
        <div className="flex justify-between text-xs text-text-tertiary mt-1">
          <span>
            <ConvertedAmount
              amount={breakdown.principalPaid}
              currency={liability.currency}
              convertedAmount={toBase(breakdown.principalPaid)}
              baseCurrency={liability.baseCurrency}
              exchangeRate={rate}
              isConverted={isConverted}
              inline
            />
            {' '}{t('breakdown.paid')}
          </span>
          <span>
            <ConvertedAmount
              amount={breakdown.currentBalance}
              currency={liability.currency}
              convertedAmount={toBase(breakdown.currentBalance)}
              baseCurrency={liability.baseCurrency}
              exchangeRate={rate}
              isConverted={isConverted}
              inline
            />
            {' '}{t('breakdown.remaining')}
          </span>
        </div>
      </div>

      {/* Amounts Paid to Date */}
      <BreakdownSection
        title={t('breakdown.amountsPaidTitle')}
        icon={<DollarSign className="h-4 w-4" />}
      >
        <BreakdownRow label={t('breakdown.principalPaid')} value={breakdown.principalPaid} liability={liability} />
        <BreakdownRow label={t('breakdown.interestPaid')} value={breakdown.interestPaid} liability={liability} />
        {breakdown.insurancePaid > 0 && (
          <BreakdownRow label={t('breakdown.insurancePaid')} value={breakdown.insurancePaid} liability={liability} />
        )}
        {breakdown.feesPaid > 0 && (
          <BreakdownRow label={t('breakdown.feesPaid')} value={breakdown.feesPaid} liability={liability} />
        )}
        <BreakdownRow label={t('breakdown.totalPaid')} value={breakdown.totalPaid} liability={liability} highlight />
      </BreakdownSection>

      {/* Projections */}
      <BreakdownSection
        title={t('breakdown.projectedTitle')}
        icon={<TrendingDown className="h-4 w-4" />}
      >
        <BreakdownRow label={t('breakdown.remainingBalance')} value={breakdown.currentBalance} liability={liability} />
        <BreakdownRow label={t('breakdown.projectedInterest')} value={breakdown.projectedInterest} liability={liability} />
        {breakdown.projectedInsurance > 0 && (
          <BreakdownRow label={t('breakdown.projectedInsurance')} value={breakdown.projectedInsurance} liability={liability} />
        )}
        <BreakdownRow
          label={t('breakdown.totalProjectedCost')}
          value={breakdown.totalProjectedCost}
          liability={liability}
          highlight
        />
      </BreakdownSection>

      {/* Insurance & One-time Fee configuration info if applicable */}
      {(liability.insurancePercentage || liability.additionalFees) && (
        <BreakdownSection
          title={t('breakdown.insuranceTitle')}
          icon={<Shield className="h-4 w-4" />}
        >
          {liability.insurancePercentage && (
            <BreakdownRow
              label={t('breakdown.annualInsuranceRate', { rate: liability.insurancePercentage.toFixed(2) })}
              value={liability.monthlyInsuranceCost}
              liability={liability}
            />
          )}
          {liability.additionalFees && (
            <BreakdownRow label={t('breakdown.oneTimeFee')} value={liability.additionalFees} liability={liability} />
          )}
        </BreakdownSection>
      )}
    </div>
  );
}
