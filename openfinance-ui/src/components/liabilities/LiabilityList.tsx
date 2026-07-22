/**
 * LiabilityList Component
 * Task 6.2.1: Create LiabilityList component
 *
 * Displays liabilities with progress bars and responsive cards.
 * Requirement 2.1: Each card has a single "View Details" button that opens the
 * unified LiabilityDetailDialog (Overview / Amortization / Linked Payments tabs).
 */
import { useState, useEffect } from 'react';
import { useTranslation } from 'react-i18next';
import { Pencil, Trash2, CreditCard, BarChart2 } from 'lucide-react';
import { Badge } from '@/components/ui/Badge';
import { Button } from '@/components/ui/Button';
import { ConfirmationDialog } from '@/components/ConfirmationDialog';
import { ConvertedAmount } from '@/components/ui/ConvertedAmount';
import { cn } from '@/lib/utils';
import { multiply } from '@/utils/money';
import {
  getLiabilityTypeName,
  getLiabilityTypeBadgeVariant,
  calculateMonthsRemaining,
} from '@/hooks/useLiabilities';
import type { Liability } from '@/types/liability';

interface LiabilityListProps {
  liabilities: Liability[];
  onEdit: (liability: Liability) => void;
  onDelete: (liabilityId: number) => void;
  /** Requirement 2.1: Callback to open the unified details dialog for a liability */
  onViewDetails?: (liability: Liability) => void;
  highlightedId?: number | null;
}

export function LiabilityList({
  liabilities,
  onEdit,
  onDelete,
  onViewDetails,
  highlightedId
}: LiabilityListProps) {
  const [deletingLiability, setDeletingLiability] = useState<Liability | null>(null);
  const { t: tc } = useTranslation('common');
  const { t } = useTranslation('liabilities');

  useEffect(() => {
    if (highlightedId && liabilities.length > 0) {
      const timer = setTimeout(() => {
        const element = document.getElementById(`liability-${highlightedId}`);
        if (element) {
          element.scrollIntoView({ behavior: 'smooth', block: 'center' });
        }
      }, 150);
      return () => clearTimeout(timer);
    }
  }, [highlightedId, liabilities]);

  const handleDeleteClick = (liability: Liability) => {
    setDeletingLiability(liability);
  };

  const handleConfirmDelete = () => {
    if (deletingLiability) {
      onDelete(deletingLiability.id);
      setDeletingLiability(null);
    }
  };

  // Calculate progress percentage
  const calculateProgress = (current: number, principal: number): number => {
    if (principal === 0) return 0;
    const paidOff = ((principal - current) / principal) * 100;
    return Math.max(0, Math.min(100, paidOff));
  };

  if (liabilities.length === 0) {
    return (
      <div className="text-center py-12">
        <CreditCard className="h-12 w-12 text-text-tertiary mx-auto mb-4" />
        <p className="text-text-secondary">
          {t('list.empty')}
        </p>
      </div>
    );
  }

  return (
    <>
      {/* Results Count */}
      <div className="text-sm text-text-secondary mb-4">
        {t('list.showingCount', { count: liabilities.length })}
      </div>

      {/* Liability Cards */}
      <div className="space-y-4">
        {liabilities.map((liability) => {
          const progress = calculateProgress(liability.currentBalance, liability.principal);
          const monthsRemaining = calculateMonthsRemaining(liability.endDate);

          return (
            <div
              key={liability.id}
              id={`liability-${liability.id}`}
              className={cn(
                "p-5 bg-surface border border-border rounded-lg hover:border-primary/50 transition-all duration-300 relative",
                highlightedId === liability.id && "ring-2 ring-primary ring-offset-2 ring-offset-background bg-primary/5 shadow-lg scale-[1.01] z-30"
              )}
            >
              {/* Header */}
              <div className="flex items-start justify-between mb-4">
                <div className="flex-1">
                  <div className="flex items-center gap-2 mb-1">
                    <h3 className="font-semibold text-lg text-text-primary">
                      {liability.name}
                    </h3>
                    <Badge
                      variant={getLiabilityTypeBadgeVariant(liability.type)}
                      size="sm"
                    >
                      {getLiabilityTypeName(liability.type)}
                    </Badge>
                  </div>
                  {(liability.notes || liability.institution) && (
                    <p className="text-sm text-text-tertiary line-clamp-1">
                      {liability.institution?.name ? `${liability.institution.name}${liability.notes ? ' • ' : ''}` : ''}
                      {liability.notes || ''}
                    </p>
                  )}
                </div>
                <div className="flex gap-2 ml-4">
                  {/* Requirement 2.1: Single "View Details" button opens unified dialog */}
                  {onViewDetails && (
                    <Button
                      variant="ghost"
                      size="sm"
                      onClick={() => onViewDetails(liability)}
                      aria-label={tc('aria.viewLiabilityDetails')}
                       title={tc('viewPropertyDetails')}
                    >
                      <BarChart2 className="h-4 w-4" />
                    </Button>
                  )}
                  <Button
                    variant="ghost"
                    size="sm"
                    onClick={() => onEdit(liability)}
                    aria-label={tc('aria.editLiability')}
                  >
                    <Pencil className="h-4 w-4" />
                  </Button>
                  <Button
                    variant="ghost"
                    size="sm"
                    onClick={() => handleDeleteClick(liability)}
                    aria-label={tc('aria.deleteLiability')}
                  >
                    <Trash2 className="h-4 w-4 text-error" />
                  </Button>
                </div>
              </div>

              {/* Balance Information */}
              <div className="grid grid-cols-2 md:grid-cols-4 gap-4 mb-4">
                <div>
                  <div className="text-xs text-text-secondary mb-1">{t('list.currentBalance')}</div>
                  {/* REQ-10.2: Display liability balance with base-currency conversion hint */}
                  <div className="text-sm font-semibold text-text-primary font-mono">
                    <ConvertedAmount
                      amount={liability.currentBalance}
                      currency={liability.currency}
                      convertedAmount={liability.balanceInBaseCurrency}
                      baseCurrency={liability.baseCurrency}
                      exchangeRate={liability.exchangeRate}
                      isConverted={liability.isConverted}
                      secondaryAmount={liability.balanceInSecondaryCurrency}
                      secondaryCurrency={liability.secondaryCurrency}
                      inline
                    />
                  </div>
                </div>
                <div>
                  <div className="text-xs text-text-secondary mb-1">{t('list.originalPrincipal')}</div>
                  <div className="text-sm font-mono text-text-tertiary">
                    <ConvertedAmount
                      amount={liability.principal}
                      currency={liability.currency}
                      convertedAmount={
                        liability.isConverted && liability.exchangeRate
                          ? multiply(liability.principal, liability.exchangeRate)
                          : undefined
                      }
                      baseCurrency={liability.baseCurrency}
                      exchangeRate={liability.exchangeRate}
                      isConverted={liability.isConverted}
                      inline
                    />
                  </div>
                </div>
                {/* Group interest rate and insurance rate into a single column */}
                {(liability.interestRate != null || (liability.insurancePercentage != null && liability.insurancePercentage > 0)) && (
                  <div>
                    <div className="text-xs text-text-secondary mb-1">
                      {liability.insurancePercentage && liability.insurancePercentage > 0
                        ? t('list.interestInsurance')
                        : t('list.interestRate')}
                    </div>
                    <div className="text-sm font-mono text-text-primary">
                      {liability.interestRate != null ? `${liability.interestRate.toFixed(2)}%` : '—'}
                      {liability.insurancePercentage && liability.insurancePercentage > 0 && (
                        <span className="text-text-tertiary"> / {liability.insurancePercentage.toFixed(2)}%</span>
                      )}
                    </div>
                  </div>
                )}
                {liability.minimumPayment !== undefined && liability.minimumPayment > 0 && (
                  <div>
                    <div className="text-xs text-text-secondary mb-1">{t('list.monthlyPayment')}</div>
                    <div className="text-sm font-mono text-text-primary">
                      <ConvertedAmount
                        amount={liability.minimumPayment}
                        currency={liability.currency}
                        convertedAmount={
                          liability.isConverted && liability.exchangeRate
                            ? multiply(liability.minimumPayment, liability.exchangeRate)
                            : undefined
                        }
                        baseCurrency={liability.baseCurrency}
                        exchangeRate={liability.exchangeRate}
                        isConverted={liability.isConverted}
                        inline
                      />
                    </div>
                  </div>
                )}
              </div>

              {/* Progress Bar */}
              <div className="mb-3">
                <div className="flex justify-between text-xs text-text-secondary mb-1">
                   <span>{t('list.paidOff')}</span>
                  <span>{progress.toFixed(1)}%</span>
                </div>
                <div className="h-2 bg-surface-elevated rounded-full overflow-hidden">
                  <div
                    className="h-full bg-success transition-all duration-300"
                    style={{ width: `${progress}%` }}
                  />
                </div>
              </div>

              {/* End Date Info */}
              {liability.endDate && (
                <div className="text-xs text-text-tertiary">
                  {monthsRemaining > 0 ? (
                    <>
                      {t('list.monthsRemaining', { count: monthsRemaining })}
                      {' • '}
                      {t('list.ends', { date: new Date(liability.endDate).toLocaleDateString() })}
                    </>
                  ) : (
                    <>{t('list.ended', { date: new Date(liability.endDate).toLocaleDateString() })}</>
                  )}
                </div>
              )}
            </div>
          );
        })}
      </div>

      {/* Delete Confirmation Dialog */}
      <ConfirmationDialog
        open={!!deletingLiability}
        onOpenChange={(open) => !open && setDeletingLiability(null)}
        onConfirm={handleConfirmDelete}
        title={t('dialogs.deleteTitle')}
        description={t('dialogs.deleteDescription', { name: deletingLiability?.name })}
        confirmText={t('dialogs.deleteConfirm')}
        variant="danger"
      />
    </>
  );
}
