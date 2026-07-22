/**
 * LiabilityDetailDialog Component
 * Unified tabbed dialog for viewing all details of a liability.
 *
 * Requirement 2.1: Display comprehensive cost breakdown including total cost,
 * principal paid, interest paid, insurance paid, fees paid, and projections.
 * Requirement 3.2: Display linked transactions in a dedicated tab.
 *
 * Merges the previous separate Amortization Schedule dialog and Breakdown dialog
 * into a single "Liability Details" view with three tabs:
 *   - Overview: cost breakdown + total cost hero card
 *   - Amortization Schedule: full payment schedule table
 *   - Linked Payments: transactions linked to this liability
 */
import { useState } from 'react';
import { CreditCard, RefreshCcw, AlertCircle } from 'lucide-react';
import { Dialog, DialogContent, DialogHeader, DialogTitle } from '@/components/ui/Dialog';
import { ConvertedAmount } from '@/components/ui/ConvertedAmount';
import { cn } from '@/lib/utils';
import { AmortizationSchedule } from '@/components/liabilities/AmortizationSchedule';
import { LiabilityBreakdownPanel } from '@/components/liabilities/LiabilityBreakdownPanel';
import { AttachmentList, AttachmentUpload } from '@/components/attachments';
import { AttachmentEntityType } from '@/types/attachment';
import { multiply } from '@/utils/money';
import { useAmortizationSchedule, useLiabilityBreakdown, useLiabilityTransactions } from '@/hooks/useLiabilities';
import { useFormatCurrency } from '@/hooks/useFormatCurrency';
import type { Liability } from '@/types/liability';

type DetailTab = 'overview' | 'schedule' | 'payments' | 'attachments';

interface LiabilityDetailDialogProps {
  /** The liability to show details for, or null to close the dialog */
  liability: Liability | null;
  /** Initial tab to show when the dialog opens (defaults to 'overview') */
  initialTab?: DetailTab;
  /** Called when the dialog should be closed */
  onClose: () => void;
}

/**
 * TotalCostHero renders a prominent summary card at the top of the Overview tab
 * showing the grand total cost of the liability broken down by component.
 *
 * Requirement 2.1: Total cost = principal + total interest + total insurance + fees
 */
function TotalCostHero({ liability }: { liability: Liability }) {
  const { data: breakdown } = useLiabilityBreakdown(liability.id);
  const { format: formatCurrency } = useFormatCurrency();

  if (!breakdown) return null;

  // Grand total = what has already been paid + what is still projected to be paid
  const grandTotal = breakdown.totalPaid + breakdown.totalProjectedCost;

  // Component totals across the full lifetime of the loan.
  // additionalFees is a one-time fee already counted in feesPaid; projectedFees is always 0.
  const totalInterest = breakdown.interestPaid + breakdown.projectedInterest;
  const totalInsurance = breakdown.insurancePaid + breakdown.projectedInsurance;
  const totalFees = breakdown.feesPaid; // one-time fee — no projected portion
  const totalPrincipal = breakdown.principal;

  /** Convert a native amount to base currency if the liability has conversion data */
  const toBase = (amount: number): number | undefined =>
    liability.isConverted && liability.exchangeRate != null
      ? multiply(amount, liability.exchangeRate)
      : undefined;

  return (
    <div className="bg-primary/10 border border-primary/20 rounded-lg p-5 mb-4">
      <div className="flex items-center justify-between mb-3">
        <h3 className="text-sm font-semibold text-text-primary uppercase tracking-wide">
          Total Lifetime Cost
        </h3>
        <span className="text-2xl font-bold font-mono text-text-primary">
          <ConvertedAmount
            amount={grandTotal}
            currency={liability.currency}
            convertedAmount={toBase(grandTotal)}
            baseCurrency={liability.baseCurrency}
            exchangeRate={liability.exchangeRate}
            isConverted={liability.isConverted}
          />
        </span>
      </div>
      {/* Breakdown bar */}
      <div className="flex rounded-full overflow-hidden h-3 mb-3">
        {totalPrincipal > 0 && (
          <div
            className="bg-primary transition-all"
            style={{ width: `${(totalPrincipal / grandTotal) * 100}%` }}
            title={`Principal: ${formatCurrency(totalPrincipal, liability.currency)}`}
          />
        )}
        {totalInterest > 0 && (
          <div
            className="bg-warning transition-all"
            style={{ width: `${(totalInterest / grandTotal) * 100}%` }}
            title={`Interest: ${formatCurrency(totalInterest, liability.currency)}`}
          />
        )}
        {totalInsurance > 0 && (
          <div
            className="bg-info transition-all"
            style={{ width: `${(totalInsurance / grandTotal) * 100}%` }}
            title={`Insurance: ${formatCurrency(totalInsurance, liability.currency)}`}
          />
        )}
        {totalFees > 0 && (
          <div
            className="bg-error transition-all"
            style={{ width: `${(totalFees / grandTotal) * 100}%` }}
            title={`One-time Fee: ${formatCurrency(totalFees, liability.currency)}`}
          />
        )}
      </div>
      {/* Legend */}
      <div className="grid grid-cols-2 gap-x-6 gap-y-1 text-xs">
        <div className="flex items-center justify-between">
          <span className="flex items-center gap-1.5 text-text-secondary">
            <span className="h-2 w-2 rounded-full bg-primary inline-block" />
            Principal
          </span>
          <span className="font-mono text-text-primary">
            <ConvertedAmount
              amount={totalPrincipal}
              currency={liability.currency}
              convertedAmount={toBase(totalPrincipal)}
              baseCurrency={liability.baseCurrency}
              exchangeRate={liability.exchangeRate}
              isConverted={liability.isConverted}
              inline
            />
          </span>
        </div>
        <div className="flex items-center justify-between">
          <span className="flex items-center gap-1.5 text-text-secondary">
            <span className="h-2 w-2 rounded-full bg-warning inline-block" />
            Interest
          </span>
          <span className="font-mono text-text-primary">
            <ConvertedAmount
              amount={totalInterest}
              currency={liability.currency}
              convertedAmount={toBase(totalInterest)}
              baseCurrency={liability.baseCurrency}
              exchangeRate={liability.exchangeRate}
              isConverted={liability.isConverted}
              inline
            />
          </span>
        </div>
        {totalInsurance > 0 && (
          <div className="flex items-center justify-between">
            <span className="flex items-center gap-1.5 text-text-secondary">
              <span className="h-2 w-2 rounded-full bg-info inline-block" />
              Insurance
            </span>
            <span className="font-mono text-text-primary">
              <ConvertedAmount
                amount={totalInsurance}
                currency={liability.currency}
                convertedAmount={toBase(totalInsurance)}
                baseCurrency={liability.baseCurrency}
                exchangeRate={liability.exchangeRate}
                isConverted={liability.isConverted}
                inline
              />
            </span>
          </div>
        )}
        {totalFees > 0 && (
          <div className="flex items-center justify-between">
            <span className="flex items-center gap-1.5 text-text-secondary">
              <span className="h-2 w-2 rounded-full bg-error inline-block" />
              One-time Fee
            </span>
            <span className="font-mono text-text-primary">
              <ConvertedAmount
                amount={totalFees}
                currency={liability.currency}
                convertedAmount={toBase(totalFees)}
                baseCurrency={liability.baseCurrency}
                exchangeRate={liability.exchangeRate}
                isConverted={liability.isConverted}
                inline
              />
            </span>
          </div>
        )}
      </div>
    </div>
  );
}

/**
 * LinkedPaymentsTab renders the list of transactions linked to a liability.
 * Requirement 3.2: Display linked transactions in a dedicated tab.
 */
function LinkedPaymentsTab({ liability }: { liability: Liability }) {
  const { data: breakdown } = useLiabilityBreakdown(liability.id);
  const { data: transactions = [], isLoading, error } = useLiabilityTransactions(liability.id);

  if (isLoading) {
    return (
      <div className="space-y-2 animate-pulse py-4">
        {[...Array(4)].map((_, i) => (
          <div key={i} className="h-12 bg-surface border border-border rounded-lg" />
        ))}
      </div>
    );
  }

  if (error) {
    return (
      <div className="flex items-center gap-2 p-4 bg-error/10 border border-error/20 rounded-lg text-error text-sm">
        <AlertCircle className="h-4 w-4 flex-shrink-0" />
        <span>Failed to load linked transactions. Please try again.</span>
      </div>
    );
  }

  if (transactions.length === 0) {
    return (
      <div className="text-center py-12">
        <CreditCard className="h-10 w-10 text-text-tertiary mx-auto mb-3" />
        <p className="text-text-secondary text-sm">
          No transactions linked to this liability yet.
        </p>
        <p className="text-text-tertiary text-xs mt-1">
          Link expense transactions to track payments against this liability.
        </p>
      </div>
    );
  }

  /** Convert a native amount to base using the parent liability's rate */
  const toBase = (amount: number): number | undefined =>
    liability.isConverted && liability.exchangeRate != null
      ? multiply(amount, liability.exchangeRate)
      : undefined;

  return (
    <div className="space-y-3">
      {/* Summary banner */}
      {breakdown && breakdown.linkedTransactionCount > 0 && (
        <div className="flex items-center justify-between bg-surface border border-border rounded-lg px-4 py-3 text-sm">
          <span className="text-text-secondary">
            {breakdown.linkedTransactionCount}{' '}
            {breakdown.linkedTransactionCount === 1 ? 'transaction' : 'transactions'} linked
          </span>
          <span className="font-semibold font-mono text-text-primary">
            <ConvertedAmount
              amount={breakdown.linkedTransactionsTotalAmount}
              currency={liability.currency}
              convertedAmount={toBase(breakdown.linkedTransactionsTotalAmount)}
              baseCurrency={liability.baseCurrency}
              exchangeRate={liability.exchangeRate}
              isConverted={liability.isConverted}
              inline
            />
            {' '}total
          </span>
        </div>
      )}

      {/* Transaction list */}
      <div className="divide-y divide-border border border-border rounded-lg overflow-hidden">
        {transactions.map((tx) => (
          <div
            key={tx.id}
            className="flex items-center justify-between px-4 py-3 bg-surface hover:bg-surface-elevated transition-colors"
          >
            <div className="flex items-center gap-3 min-w-0">
              <RefreshCcw className="h-4 w-4 text-text-tertiary flex-shrink-0" />
              <div className="min-w-0">
                <div className="text-sm text-text-primary truncate">
                  {tx.description || tx.payee || `Transaction #${tx.id}`}
                </div>
                <div className="text-xs text-text-tertiary">
                  {new Date(tx.date).toLocaleDateString('en-US', {
                    year: 'numeric',
                    month: 'short',
                    day: 'numeric',
                  })}
                  {tx.payee && tx.description && (
                    <span className="ml-2 text-text-tertiary">{tx.payee}</span>
                  )}
                </div>
              </div>
            </div>
            <div className="text-sm font-mono font-semibold text-text-primary ml-4 flex-shrink-0">
              {/* Transactions carry their own currency; use liability conversion for badge */}
              <ConvertedAmount
                amount={tx.amount}
                currency={tx.currency}
                convertedAmount={
                  tx.currency === liability.currency ? toBase(tx.amount) : undefined
                }
                baseCurrency={liability.baseCurrency}
                exchangeRate={liability.exchangeRate}
                isConverted={tx.currency === liability.currency ? liability.isConverted : false}
                inline
              />
            </div>
          </div>
        ))}
      </div>
    </div>
  );
}

/**
 * LiabilityDetailDialog — unified tabbed dialog for liability details.
 *
 * Requirement 2.1: Overview tab shows cost breakdown with total cost hero card.
 * Requirement 3.2: Linked Payments tab shows all transactions linked to the liability.
 * Amortization Schedule tab shows the full payment schedule (only for interest-bearing loans).
 */
export function LiabilityDetailDialog({
  liability,
  initialTab = 'overview',
  onClose,
}: LiabilityDetailDialogProps) {
  const [activeTab, setActiveTab] = useState<DetailTab>(initialTab);

  // Fetch amortization schedule only when schedule tab is active and liability has an interest rate
  const hasSchedule = (liability?.interestRate ?? 0) > 0;
  const { data: amortizationSchedule, isLoading: isLoadingSchedule } = useAmortizationSchedule(
    activeTab === 'schedule' && hasSchedule ? liability : null
  );

  if (!liability) return null;

  return (
    <Dialog open={!!liability} onOpenChange={(open) => !open && onClose()}>
      <DialogContent className="sm:max-w-[800px] max-h-[90vh] overflow-y-auto">
        <DialogHeader>
          <DialogTitle>{liability.name} — Details</DialogTitle>
        </DialogHeader>

        <div className="w-full">
          {/* Tabs */}
          <div className="border-b border-border mb-4">
            <div className="flex flex-wrap gap-2 text-sm">
              <button
                onClick={() => setActiveTab('overview')}
                className={cn(
                  'px-4 py-2 font-medium border-b-2 transition-colors',
                  activeTab === 'overview'
                    ? 'border-primary text-primary'
                    : 'border-transparent text-text-secondary hover:text-text-primary'
                )}
              >
                Overview
              </button>
              {hasSchedule && (
                <button
                  onClick={() => setActiveTab('schedule')}
                  className={cn(
                    'px-4 py-2 font-medium border-b-2 transition-colors',
                    activeTab === 'schedule'
                      ? 'border-primary text-primary'
                      : 'border-transparent text-text-secondary hover:text-text-primary'
                  )}
                >
                  Amortization Schedule
                </button>
              )}
              <button
                onClick={() => setActiveTab('payments')}
                className={cn(
                  'px-4 py-2 font-medium border-b-2 transition-colors',
                  activeTab === 'payments'
                    ? 'border-primary text-primary'
                    : 'border-transparent text-text-secondary hover:text-text-primary'
                )}
              >
                Linked Payments
              </button>
              <button
                onClick={() => setActiveTab('attachments')}
                className={cn(
                  'px-4 py-2 font-medium border-b-2 transition-colors',
                  activeTab === 'attachments'
                    ? 'border-primary text-primary'
                    : 'border-transparent text-text-secondary hover:text-text-primary'
                )}
              >
                Attachments
              </button>
            </div>
          </div>

          <div className="py-2 space-y-4">
            {/* Tab 1: Overview */}
            {activeTab === 'overview' && (
              <div>
                <TotalCostHero liability={liability} />
                <LiabilityBreakdownPanel liability={liability} />
              </div>
            )}

            {/* Tab 2: Amortization Schedule */}
            {activeTab === 'schedule' && hasSchedule && (
              <div>
                {isLoadingSchedule && (
                  <div className="py-12 text-center text-text-secondary text-sm">
                    Loading amortization schedule…
                  </div>
                )}
                {amortizationSchedule && (
                  <AmortizationSchedule schedule={amortizationSchedule} />
                )}
              </div>
            )}

            {/* Tab 3: Linked Payments */}
            {activeTab === 'payments' && (
              <div>
                <LinkedPaymentsTab liability={liability} />
              </div>
            )}

            {/* Tab 4: Attachments */}
            {activeTab === 'attachments' && (
              <div className="space-y-4">
                <AttachmentList
                  entityType={AttachmentEntityType.LIABILITY}
                  entityId={liability.id}
                />
                <AttachmentUpload
                  entityType={AttachmentEntityType.LIABILITY}
                  entityId={liability.id}
                />
              </div>
            )}
          </div>
        </div>
      </DialogContent>
    </Dialog>
  );
}
