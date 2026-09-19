/**
 * AmortizationSchedule Component
 * Task 6.1.13: Display payment schedule table with filtering and export
 *
 * Shows detailed breakdown of each payment: principal, interest, remaining balance
 */
import { Fragment, useState } from 'react';
import { Download, ChevronDown, ChevronUp } from 'lucide-react';
import { useTranslation } from 'react-i18next';
import { Button } from '@/components/ui/Button';
import { ConvertedAmount } from '@/components/ui/ConvertedAmount';
import type { AmortizationSchedule as AmortizationScheduleType } from '@/types/liability';
interface AmortizationScheduleProps {
  schedule: AmortizationScheduleType;
  onClose?: () => void;
}

type FilterOption = 'all' | '12' | '24';

export function AmortizationSchedule({ schedule, onClose }: AmortizationScheduleProps) {
  const { t, i18n } = useTranslation('liabilities');
  const { t: tc } = useTranslation('common');
  const [filterOption, setFilterOption] = useState<FilterOption>('all');
  const [isExpanded, setIsExpanded] = useState(false);

  if (!schedule?.payments) {
    return (
      <div className="p-8 text-center bg-surface border border-border rounded-lg text-text-secondary">
        {t('amortization.noSchedule')}
      </div>
    );
  }

  // Filter payments based on selected option
  const getFilteredPayments = () => {
    switch (filterOption) {
      case '12':
        return schedule.payments.slice(0, 12);
      case '24':
        return schedule.payments.slice(0, 24);
      default:
        return schedule.payments;
    }
  };

  const filteredPayments = getFilteredPayments();
  const showExpandButton = filterOption === 'all' && schedule.payments.length > 12;

  // Display only first 12 payments when collapsed in 'all' mode
  const displayedPayments =
    showExpandButton && !isExpanded ? filteredPayments.slice(0, 12) : filteredPayments;

  // Two-phase schedules (interest-only tranches): group consecutive payments by phase and label
  // each group's first row. Computed from the FULL payment list so a truncated view still gets
  // the right interest-only end date (or the open-ended label when the phase never ends).
  const phaseLabelsByNumber = new Map<number, { testId: string; label: string }>();
  if (schedule.payments.some(p => p.interestOnlyPhase)) {
    let i = 0;
    while (i < schedule.payments.length) {
      const phase = !!schedule.payments[i].interestOnlyPhase;
      let j = i;
      while (j < schedule.payments.length && !!schedule.payments[j].interestOnlyPhase === phase) {
        j++;
      }
      const label = phase
        ? j >= schedule.payments.length
          ? t('schedule.interestOnlyPhaseOpen')
          : t('schedule.interestOnlyPhase', { date: schedule.payments[j - 1].paymentDate })
        : t('amortization.phaseAmortizing');
      phaseLabelsByNumber.set(schedule.payments[i].paymentNumber, {
        testId: phase ? 'phase-interest-only' : 'phase-amortizing',
        label,
      });
      i = j;
    }
  }

  const phaseLabelFor = (paymentNumber: number) => phaseLabelsByNumber.get(paymentNumber);

  // Export to CSV
  const handleExportCSV = () => {
    const headers = [
      'Payment #',
      'Payment Date',
      'Payment Amount',
      'Principal',
      'Interest',
      'Remaining Balance',
    ];

    const csvData = [
      headers.join(','),
      ...filteredPayments.map(payment =>
        [
          payment.paymentNumber,
          payment.paymentDate,
          payment.paymentAmount.toFixed(2),
          payment.principalPayment.toFixed(2),
          payment.interestPayment.toFixed(2),
          payment.remainingBalance.toFixed(2),
        ].join(',')
      ),
    ].join('\n');

    const blob = new Blob([csvData], { type: 'text/csv' });
    const url = URL.createObjectURL(blob);
    const link = document.createElement('a');
    link.href = url;
    link.download = `amortization-${schedule.liabilityName.replace(/\s+/g, '-')}-${new Date().toISOString().split('T')[0]}.csv`;
    document.body.appendChild(link);
    link.click();
    document.body.removeChild(link);
    URL.revokeObjectURL(url);
  };

  return (
    <div className="space-y-6">
      {/* Summary Header */}
      <div className="bg-surface border border-border rounded-[var(--radius-card)] shadow-plate p-6">
        <h3 className="plate-label  mb-4">
          {schedule.liabilityName} - {t('amortization.title')}
        </h3>

        <div className="grid grid-cols-2 md:grid-cols-4 gap-4 text-sm">
          <div>
            <div className="text-text-secondary mb-1">{t('amortization.col.principal')}</div>
            <div className="font-semibold text-text-primary">
              <ConvertedAmount amount={schedule.principal} currency={schedule.currency} inline />
            </div>
          </div>
          <div>
            <div className="text-text-secondary mb-1">{t('amortization.interestRate')}</div>
            <div className="font-semibold text-text-primary">
              {schedule.interestRate.toFixed(2)}%
            </div>
          </div>
          <div>
            <div className="text-text-secondary mb-1">{t('amortization.monthlyPayment')}</div>
            <div className="font-semibold text-text-primary">
              <ConvertedAmount
                amount={schedule.monthlyPayment}
                currency={schedule.currency}
                inline
              />
            </div>
          </div>
          <div>
            <div className="text-text-secondary mb-1">{t('amortization.term')}</div>
            <div className="font-semibold text-text-primary">
              {schedule.termMonths} {t('amortization.months')}
            </div>
          </div>
          <div>
            <div className="text-text-secondary mb-1">{t('amortization.totalInterest')}</div>
            <div className="font-semibold text-warning">
              <ConvertedAmount
                amount={schedule.totalInterest}
                currency={schedule.currency}
                inline
              />
            </div>
          </div>
          <div>
            <div className="text-text-secondary mb-1">{t('amortization.totalAmount')}</div>
            <div className="font-semibold text-text-primary">
              <ConvertedAmount amount={schedule.totalAmount} currency={schedule.currency} inline />
            </div>
          </div>
          <div>
            <div className="text-text-secondary mb-1">{t('amortization.totalPayments')}</div>
            <div className="font-semibold text-text-primary">{schedule.payments.length}</div>
          </div>
          <div>
            <div className="text-text-secondary mb-1">
              {t('amortization.interestPrincipalRatio')}
            </div>
            <div className="font-semibold text-text-primary">
              {((schedule.totalInterest / schedule.principal) * 100).toFixed(1)}%
            </div>
          </div>
        </div>
      </div>

      {/* Filters and Export */}
      <div className="flex flex-wrap items-center justify-between gap-4">
        <div className="flex gap-2">
          <button
            onClick={() => setFilterOption('all')}
            className={`px-4 py-2 rounded-lg text-sm font-medium transition-colors ${
              filterOption === 'all'
                ? 'bg-primary text-white'
                : 'bg-surface border border-border text-text-secondary hover:border-primary/50 hover:text-text-primary'
            }`}
          >
            {t('amortization.filterAll')}
          </button>
          <button
            onClick={() => setFilterOption('12')}
            className={`px-4 py-2 rounded-lg text-sm font-medium transition-colors ${
              filterOption === '12'
                ? 'bg-primary text-white'
                : 'bg-surface border border-border text-text-secondary hover:border-primary/50 hover:text-text-primary'
            }`}
          >
            {t('amortization.filter12')}
          </button>
          <button
            onClick={() => setFilterOption('24')}
            className={`px-4 py-2 rounded-lg text-sm font-medium transition-colors ${
              filterOption === '24'
                ? 'bg-primary text-white'
                : 'bg-surface border border-border text-text-secondary hover:border-primary/50 hover:text-text-primary'
            }`}
          >
            {t('amortization.filter24')}
          </button>
        </div>

        <Button
          variant="secondary"
          size="sm"
          onClick={handleExportCSV}
          className="flex items-center gap-2"
        >
          <Download className="h-4 w-4" />
          {t('amortization.exportCsv')}
        </Button>
      </div>

      {/* Desktop Table View */}
      <div className="hidden md:block overflow-x-auto">
        <table className="w-full bg-surface border border-border rounded-lg">
          <thead>
            <tr className="border-b border-border bg-surface-elevated">
              <th className="text-left py-3 px-4 text-sm font-medium text-text-secondary">
                {t('amortization.col.paymentNumber')}
              </th>
              <th className="text-left py-3 px-4 text-sm font-medium text-text-secondary">
                {t('amortization.col.date')}
              </th>
              <th className="text-right py-3 px-4 text-sm font-medium text-text-secondary">
                {t('amortization.col.payment')}
              </th>
              <th className="text-right py-3 px-4 text-sm font-medium text-text-secondary">
                {t('amortization.col.principal')}
              </th>
              <th className="text-right py-3 px-4 text-sm font-medium text-text-secondary">
                {t('amortization.col.interest')}
              </th>
              <th className="text-right py-3 px-4 text-sm font-medium text-text-secondary">
                {t('amortization.col.remainingBalance')}
              </th>
            </tr>
          </thead>
          <tbody>
            {displayedPayments.map((payment, index) => {
              // Guard the percent division: a zero-payment row (fully-categorized payment)
              // has no meaningful breakdown — hide the percents instead of rendering NaN%.
              const principalPercent =
                payment.paymentAmount > 0
                  ? (payment.principalPayment / payment.paymentAmount) * 100
                  : null;
              const interestPercent =
                payment.paymentAmount > 0
                  ? (payment.interestPayment / payment.paymentAmount) * 100
                  : null;
              const phaseLabel = phaseLabelFor(payment.paymentNumber);

              return (
                <Fragment key={payment.paymentNumber}>
                  {phaseLabel && (
                    <tr data-testid={phaseLabel.testId}>
                      <td
                        colSpan={6}
                        className="py-2 px-4 text-xs font-semibold uppercase tracking-wide bg-info/10 border-y border-info/30 text-info"
                      >
                        {phaseLabel.label}
                      </td>
                    </tr>
                  )}
                  <tr
                    className={`border-b border-border hover:bg-surface-elevated transition-colors ${
                      index % 2 === 0 ? 'bg-surface' : 'bg-background'
                    }`}
                  >
                    <td className="py-3 px-4 text-sm font-medium text-text-primary">
                      {payment.paymentNumber}
                    </td>
                    <td className="py-3 px-4 text-sm text-text-secondary">
                      {new Date(payment.paymentDate).toLocaleDateString(i18n.language, {
                        year: 'numeric',
                        month: 'short',
                        day: 'numeric',
                      })}
                    </td>
                    <td className="py-3 px-4 text-sm font-mono text-right text-text-primary">
                      <ConvertedAmount
                        amount={payment.paymentAmount}
                        currency={schedule.currency}
                        inline
                      />
                    </td>
                    <td className="py-3 px-4 text-right">
                      <div className="text-sm font-mono text-success">
                        <ConvertedAmount
                          amount={payment.principalPayment}
                          currency={schedule.currency}
                          inline
                        />
                      </div>
                      <div className="text-xs text-text-tertiary">
                        {principalPercent != null && `${principalPercent.toFixed(1)}%`}
                      </div>
                    </td>
                    <td className="py-3 px-4 text-right">
                      <div className="text-sm font-mono text-warning">
                        <ConvertedAmount
                          amount={payment.interestPayment}
                          currency={schedule.currency}
                          inline
                        />
                      </div>
                      <div className="text-xs text-text-tertiary">
                        {interestPercent != null && `${interestPercent.toFixed(1)}%`}
                      </div>
                    </td>
                    <td className="py-3 px-4 text-sm font-mono text-right text-text-secondary">
                      <ConvertedAmount
                        amount={payment.remainingBalance}
                        currency={schedule.currency}
                        inline
                      />
                    </td>
                  </tr>
                </Fragment>
              );
            })}
          </tbody>
        </table>

        {/* Expand/Collapse Button */}
        {showExpandButton && (
          <div className="flex justify-center mt-4">
            <Button
              variant="ghost"
              size="sm"
              onClick={() => setIsExpanded(!isExpanded)}
              className="flex items-center gap-2"
            >
              {isExpanded ? (
                <>
                  <ChevronUp className="h-4 w-4" />
                  {t('amortization.showLess')}
                </>
              ) : (
                <>
                  <ChevronDown className="h-4 w-4" />
                  {t('amortization.showAll', { count: schedule.payments.length })}
                </>
              )}
            </Button>
          </div>
        )}
      </div>

      {/* Mobile Card View */}
      <div className="md:hidden space-y-3">
        {displayedPayments.map(payment => {
          const principalPercent =
            payment.paymentAmount > 0
              ? (payment.principalPayment / payment.paymentAmount) * 100
              : null;
          const interestPercent =
            payment.paymentAmount > 0
              ? (payment.interestPayment / payment.paymentAmount) * 100
              : null;
          const phaseLabel = phaseLabelFor(payment.paymentNumber);

          return (
            <Fragment key={payment.paymentNumber}>
              {phaseLabel && (
                <p
                  data-testid={phaseLabel.testId}
                  className="px-3 py-1.5 rounded-md bg-info/10 border border-info/30 text-info text-xs font-semibold uppercase tracking-wide"
                >
                  {phaseLabel.label}
                </p>
              )}
              <div className="bg-surface border border-border rounded-[var(--radius-card)] shadow-plate p-4 space-y-3">
                {/* Header */}
                <div className="flex justify-between items-center pb-2 border-b border-border">
                  <div>
                    <div className="text-sm font-medium text-text-primary">
                      {t('amortization.paymentNumber', { number: payment.paymentNumber })}
                    </div>
                    <div className="text-xs text-text-secondary">
                      {new Date(payment.paymentDate).toLocaleDateString(i18n.language, {
                        year: 'numeric',
                        month: 'short',
                        day: 'numeric',
                      })}
                    </div>
                  </div>
                  <div className="text-right">
                    <div className="text-sm font-mono font-medium text-text-primary">
                      <ConvertedAmount
                        amount={payment.paymentAmount}
                        currency={schedule.currency}
                        inline
                      />
                    </div>
                    <div className="text-xs text-text-secondary">
                      {t('amortization.totalPayment')}
                    </div>{' '}
                  </div>
                </div>

                {/* Breakdown */}
                <div className="grid grid-cols-2 gap-3">
                  <div>
                    <div className="text-xs text-text-secondary mb-1">
                      {t('amortization.col.principal')}
                    </div>
                    <div className="text-sm font-mono text-success">
                      <ConvertedAmount
                        amount={payment.principalPayment}
                        currency={schedule.currency}
                        inline
                      />
                    </div>
                    <div className="text-xs text-text-tertiary">
                      {principalPercent != null && `${principalPercent.toFixed(1)}%`}
                    </div>
                  </div>
                  <div>
                    <div className="text-xs text-text-secondary mb-1">
                      {t('amortization.col.interest')}
                    </div>
                    <div className="text-sm font-mono text-warning">
                      <ConvertedAmount
                        amount={payment.interestPayment}
                        currency={schedule.currency}
                        inline
                      />
                    </div>
                    <div className="text-xs text-text-tertiary">
                      {interestPercent != null && `${interestPercent.toFixed(1)}%`}
                    </div>
                  </div>
                </div>

                {/* Remaining Balance */}
                <div className="pt-2 border-t border-border">
                  <div className="text-xs text-text-secondary mb-1">
                    {t('amortization.remainingBalance')}
                  </div>
                  <div className="text-sm font-mono text-text-primary">
                    <ConvertedAmount
                      amount={payment.remainingBalance}
                      currency={schedule.currency}
                      inline
                    />
                  </div>
                </div>
              </div>
            </Fragment>
          );
        })}

        {/* Mobile Expand/Collapse */}
        {showExpandButton && (
          <div className="flex justify-center">
            <Button
              variant="ghost"
              size="sm"
              onClick={() => setIsExpanded(!isExpanded)}
              className="flex items-center gap-2"
            >
              {isExpanded ? (
                <>
                  <ChevronUp className="h-4 w-4" />
                  {t('amortization.showLess')}
                </>
              ) : (
                <>
                  <ChevronDown className="h-4 w-4" />
                  {t('amortization.showAll', { count: schedule.payments.length })}
                </>
              )}
            </Button>
          </div>
        )}
      </div>

      {/* Close Button (if in modal) */}
      {onClose && (
        <div className="flex justify-end pt-4 border-t border-border">
          <Button variant="secondary" onClick={onClose}>
            {tc('close')}
          </Button>
        </div>
      )}
    </div>
  );
}
