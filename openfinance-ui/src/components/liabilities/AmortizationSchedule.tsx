import { Fragment, useState } from 'react';
import { CheckCircle2, ChevronLeft, ChevronRight, Clock3, Download } from 'lucide-react';
import { useTranslation } from 'react-i18next';
import { Link } from 'react-router';
import { Button } from '@/components/ui/Button';
import { CurrencyAmount } from '@/components/ui/CurrencyAmount';
import { useDateFormatter } from '@/hooks/useDateFormatter';
import { useIsMobile } from '@/hooks/useBreakpoint';
import { DEFAULT_PAGE_SIZE } from '@/constants/pagination';
import { getMonetaryScale } from '@/utils/currency';
import { sum, toFixedDecimal } from '@/utils/money';
import { formatDecimal } from '@/utils/format';
import { buildTransactionsLink } from '@/utils/navigation';
import type { AmortizationPayment, AmortizationSchedule as Schedule } from '@/types/liability';

interface AmortizationScheduleProps {
  schedule: Schedule;
  onClose?: () => void;
  contractualEndDate?: string;
}

type FilterOption = 'all' | 'paid' | 'remaining';
type PaymentRow = AmortizationPayment & { status: Exclude<FilterOption, 'all'> };

export function AmortizationSchedule({
  schedule,
  onClose,
  contractualEndDate,
}: AmortizationScheduleProps) {
  const { t } = useTranslation('liabilities');
  const { t: tc } = useTranslation('common');
  const { date: formatDate } = useDateFormatter();
  const isMobile = useIsMobile();
  const [filter, setFilter] = useState<FilterOption>('all');
  const [page, setPage] = useState(0);
  const history = schedule.history ?? [];
  const forecast = schedule.payments ?? [];
  const rows: PaymentRow[] = [
    ...history.map(payment => ({ ...payment, status: 'paid' as const })),
    ...forecast.map(payment => ({ ...payment, status: 'remaining' as const })),
  ];
  const visible = rows.filter(payment => filter === 'all' || payment.status === filter);
  const pageCount = Math.ceil(visible.length / DEFAULT_PAGE_SIZE);
  const currentPage = Math.min(page, Math.max(0, pageCount - 1));
  const displayed = visible.slice(
    currentPage * DEFAULT_PAGE_SIZE,
    (currentPage + 1) * DEFAULT_PAGE_SIZE
  );
  const emptyMessage = t(
    schedule.fundingStatus === 'UNDRAWN'
      ? 'amortization.undrawn'
      : schedule.fundingStatus === 'PAID'
        ? 'amortization.paidOff'
        : schedule.currentBalance === 0
          ? 'amortization.noBalance'
          : 'amortization.noSchedule'
  );

  if (!rows.length) {
    return (
      <div className="p-8 text-center bg-surface border border-border rounded-lg text-text-secondary">
        {emptyMessage}
      </div>
    );
  }

  const lastPayment = forecast.at(-1);
  const incomplete = !!lastPayment && lastPayment.remainingBalance > 0;
  const pastContract =
    !!contractualEndDate && !!lastPayment && lastPayment.paymentDate > contractualEndDate;
  const hasCharges = history.some(
    payment => (payment.feePayment ?? 0) !== 0 || (payment.otherCharges ?? 0) !== 0
  );
  const moneyColumns = [
    { key: 'payment', value: (p: PaymentRow) => p.paymentAmount },
    { key: 'principal', value: (p: PaymentRow) => p.principalPayment },
    { key: 'interest', value: (p: PaymentRow) => p.interestPayment },
    { key: 'insurance', value: (p: PaymentRow) => p.insurancePayment ?? 0 },
    ...(hasCharges
      ? [
          {
            key: 'charges',
            value: (p: PaymentRow) => sum([p.feePayment ?? 0, p.otherCharges ?? 0]),
          },
        ]
      : []),
    { key: 'remainingBalance', value: (p: PaymentRow) => p.remainingBalance },
  ];

  // Phase boundaries belong to the forecast, independently of recorded payment numbers.
  const phases = new Map<number, { testId: string; label: string; firstPayment: number }>();
  if (forecast.some(payment => payment.interestOnlyPhase)) {
    for (let i = 0; i < forecast.length; ) {
      const interestOnly = !!forecast[i].interestOnlyPhase;
      let end = i + 1;
      while (end < forecast.length && !!forecast[end].interestOnlyPhase === interestOnly) end++;
      const phase = {
        firstPayment: forecast[i].paymentNumber,
        testId: interestOnly ? 'phase-interest-only' : 'phase-amortizing',
        label: interestOnly
          ? end === forecast.length
            ? t('schedule.interestOnlyPhaseOpen')
            : t('schedule.interestOnlyPhase', { date: formatDate(forecast[end - 1].paymentDate) })
          : t('amortization.phaseAmortizing'),
      };
      for (let n = i; n < end; n++) phases.set(forecast[n].paymentNumber, phase);
      i = end;
    }
  }

  const phaseFor = (payment: PaymentRow, index: number) => {
    const phase = payment.status === 'remaining' ? phases.get(payment.paymentNumber) : undefined;
    return phase && (index === 0 || phase.firstPayment === payment.paymentNumber)
      ? phase
      : undefined;
  };

  const rowKey = (payment: PaymentRow) =>
    `${payment.status}-${payment.transactionId ?? payment.paymentNumber}`;
  const paymentDate = (payment: PaymentRow) =>
    payment.transactionId != null ? (
      <Link
        to={buildTransactionsLink({
          liabilityId: schedule.liabilityId,
          highlight: payment.transactionId,
        })}
        className="text-primary underline underline-offset-4 focus-visible:ring-2 focus-visible:ring-primary"
      >
        {formatDate(payment.paymentDate)}
      </Link>
    ) : (
      formatDate(payment.paymentDate)
    );
  const status = (payment: PaymentRow) => (
    <span
      className={`inline-flex items-center gap-1.5 whitespace-nowrap text-xs font-medium ${payment.status === 'paid' ? 'text-success' : 'text-text-secondary'}`}
    >
      {payment.status === 'paid' ? (
        <CheckCircle2 className="h-3.5 w-3.5" aria-hidden="true" />
      ) : (
        <Clock3 className="h-3.5 w-3.5" aria-hidden="true" />
      )}
      {t(`amortization.${payment.status}`)}
    </span>
  );

  const handleExportCSV = () => {
    const headers = [
      'Status',
      'Payment #',
      'Transaction ID',
      'Payment Date',
      'Payment Amount',
      'Principal',
      'Interest',
      'Insurance',
      'Fees',
      'Other Charges',
      'Remaining Balance',
    ];
    const money = (amount: number) => toFixedDecimal(amount, getMonetaryScale(schedule.currency));
    const csv = [
      headers.join(','),
      ...visible.map(payment =>
        [
          payment.status === 'paid' ? 'Paid' : 'Remaining',
          payment.status === 'remaining' ? payment.paymentNumber : '',
          payment.transactionId ?? '',
          payment.paymentDate,
          ...[
            payment.paymentAmount,
            payment.principalPayment,
            payment.interestPayment,
            payment.insurancePayment ?? 0,
            payment.feePayment ?? 0,
            payment.otherCharges ?? 0,
            payment.remainingBalance,
          ].map(money),
        ].join(',')
      ),
    ].join('\n');
    const url = URL.createObjectURL(new Blob([csv], { type: 'text/csv' }));
    const link = document.createElement('a');
    link.href = url;
    link.download = `amortization-${schedule.liabilityName.replace(/\s+/g, '-')}-${new Date().toISOString().split('T')[0]}.csv`;
    document.body.appendChild(link);
    link.click();
    document.body.removeChild(link);
    URL.revokeObjectURL(url);
  };

  const summaries = [
    { label: 'col.principal', amount: schedule.principal },
    { label: 'interestRate', text: `${formatDecimal(schedule.interestRate, 2)}%` },
    { label: 'monthlyPayment', amount: schedule.monthlyPayment },
    { label: 'term', text: `${schedule.termMonths} ${t('amortization.months')}` },
    { label: 'totalInterest', amount: schedule.totalInterest },
    { label: 'totalAmount', amount: schedule.totalAmount },
    { label: 'totalPayments', text: forecast.length },
    {
      label: 'interestPrincipalRatio',
      text:
        schedule.principal > 0
          ? `${formatDecimal((schedule.totalInterest / schedule.principal) * 100, 1)}%`
          : '—',
    },
  ];

  return (
    <div className="space-y-5">
      {(pastContract || incomplete) && (
        <p
          role="alert"
          className="p-3 rounded-lg border border-warning/30 bg-warning/10 text-sm text-text-primary"
        >
          {incomplete
            ? t('amortization.incomplete')
            : t('amortization.pastContract', {
                contract: formatDate(contractualEndDate!),
                payoff: formatDate(lastPayment!.paymentDate),
              })}
        </p>
      )}
      {forecast.length > 0 ? (
        <section className="bg-surface border border-border rounded-[var(--radius-card)] p-4">
          <h3 className="plate-label mb-4">{t('amortization.remainingForecast')}</h3>
          <dl className="grid grid-cols-2 md:grid-cols-4 gap-4 text-sm">
            {summaries.map(summary => (
              <div key={summary.label}>
                <dt className="text-text-secondary mb-1">{t(`amortization.${summary.label}`)}</dt>
                <dd className="font-semibold text-text-primary">
                  {summary.amount != null ? (
                    <CurrencyAmount amount={summary.amount} currency={schedule.currency} inline />
                  ) : (
                    summary.text
                  )}
                </dd>
              </div>
            ))}
          </dl>
        </section>
      ) : (
        <p className="text-sm text-text-secondary">{emptyMessage}</p>
      )}

      <div className="flex flex-wrap items-center justify-between gap-3">
        <div
          role="group"
          aria-label={t('amortization.statusFilter')}
          className="flex flex-wrap gap-1 rounded-lg border border-border p-1"
        >
          {(['all', 'paid', 'remaining'] as const).map(option => (
            <button
              key={option}
              type="button"
              aria-pressed={filter === option}
              onClick={() => {
                setFilter(option);
                setPage(0);
              }}
              className={`rounded-md px-3 py-2 text-sm font-medium focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-primary ${filter === option ? 'bg-primary/15 text-primary' : 'text-text-secondary hover:bg-surface-elevated'}`}
            >
              {t(`amortization.${option === 'all' ? 'filterAll' : option}`)}{' '}
              <span className="ml-1 tabular-nums">
                {option === 'all'
                  ? rows.length
                  : option === 'paid'
                    ? history.length
                    : forecast.length}
              </span>
            </button>
          ))}
        </div>
        <Button
          variant="secondary"
          size="sm"
          onClick={handleExportCSV}
          disabled={!visible.length}
          className="gap-2"
        >
          <Download className="h-4 w-4" aria-hidden="true" />
          {t('amortization.exportCsv')}
        </Button>
      </div>
      <p className="text-xs text-text-secondary">{t('amortization.historyHint')}</p>
      {pageCount > 1 && (
        <nav
          aria-label={t('amortization.title')}
          className="flex flex-wrap items-center justify-between gap-2"
        >
          <p className="text-xs text-text-secondary tabular-nums" aria-live="polite">
            {tc('pagination.showing')} {currentPage * DEFAULT_PAGE_SIZE + 1} {tc('pagination.to')}{' '}
            {Math.min((currentPage + 1) * DEFAULT_PAGE_SIZE, visible.length)} {tc('pagination.of')}{' '}
            {visible.length}
          </p>
          <div className="flex items-center gap-2">
            <Button
              type="button"
              variant="ghost"
              size="icon"
              aria-label={tc('previous')}
              disabled={currentPage === 0}
              onClick={() => setPage(currentPage - 1)}
            >
              <ChevronLeft className="h-4 w-4" aria-hidden="true" />
            </Button>
            <select
              value={currentPage}
              onChange={event => setPage(Number(event.target.value))}
              aria-label={tc('pagination.page', { current: currentPage + 1, total: pageCount })}
              className="h-11 md:h-9 rounded-lg border border-border bg-surface px-3 text-sm text-text-primary focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-primary"
            >
              {Array.from({ length: pageCount }, (_, index) => (
                <option key={index} value={index}>
                  {tc('pagination.page', { current: index + 1, total: pageCount })}
                </option>
              ))}
            </select>
            <Button
              type="button"
              variant="ghost"
              size="icon"
              aria-label={tc('next')}
              disabled={currentPage === pageCount - 1}
              onClick={() => setPage(currentPage + 1)}
            >
              <ChevronRight className="h-4 w-4" aria-hidden="true" />
            </Button>
          </div>
        </nav>
      )}

      {!visible.length ? (
        <p role="status" className="p-6 text-center text-text-secondary text-sm">
          {t(filter === 'paid' ? 'amortization.noHistory' : 'amortization.noRemaining')}
        </p>
      ) : (
        <>
          {!isMobile ? (
            <div
              key={`table-${filter}-${currentPage}`}
              role="region"
              aria-label={t('amortization.title')}
              tabIndex={0}
              className="max-h-[min(24rem,45dvh)] overflow-auto overscroll-contain rounded-lg border border-border focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-primary"
            >
              <table className="w-full text-sm whitespace-nowrap">
                <thead className="sticky top-0 z-10 bg-surface-elevated text-text-secondary">
                  <tr>
                    <th scope="col" className="text-left px-3 py-3 font-medium">
                      {t('amortization.col.status')}
                    </th>
                    <th scope="col" className="text-left px-3 py-3 font-medium">
                      {t('amortization.col.date')}
                    </th>
                    {moneyColumns.map(column => (
                      <th
                        scope="col"
                        key={column.key}
                        className="text-right px-3 py-3 font-medium"
                        title={
                          column.key === 'remainingBalance'
                            ? t('amortization.closingBalanceHint')
                            : undefined
                        }
                      >
                        {t(`amortization.col.${column.key}`)}
                      </th>
                    ))}
                  </tr>
                </thead>
                <tbody>
                  {displayed.map((payment, index) => {
                    const phase = phaseFor(payment, index);
                    return (
                      <Fragment key={rowKey(payment)}>
                        {phase && (
                          <tr data-testid={phase.testId}>
                            <td
                              colSpan={moneyColumns.length + 2}
                              className="px-3 py-2 bg-info/10 text-info text-xs font-medium"
                            >
                              {phase.label}
                            </td>
                          </tr>
                        )}
                        <tr className="border-t border-border bg-surface hover:bg-surface-elevated">
                          <td className="px-3 py-3">{status(payment)}</td>
                          <td className="px-3 py-3 text-text-secondary">{paymentDate(payment)}</td>
                          {moneyColumns.map(column => (
                            <td
                              key={column.key}
                              className="px-3 py-3 text-right font-mono tabular-nums text-text-primary"
                            >
                              <CurrencyAmount
                                amount={column.value(payment)}
                                currency={schedule.currency}
                                conversionDate={
                                  payment.status === 'paid' ? payment.paymentDate : undefined
                                }
                                inline
                              />
                            </td>
                          ))}
                        </tr>
                      </Fragment>
                    );
                  })}
                </tbody>
              </table>
            </div>
          ) : (
            <div
              key={`cards-${filter}-${currentPage}`}
              role="region"
              aria-label={t('amortization.title')}
              tabIndex={0}
              className="space-y-3 max-h-[55dvh] overflow-y-auto overscroll-contain focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-primary"
            >
              {displayed.map((payment, index) => {
                const phase = phaseFor(payment, index);
                return (
                  <Fragment key={rowKey(payment)}>
                    {phase && (
                      <p
                        data-testid={phase.testId}
                        className="p-2 bg-info/10 text-info text-xs font-medium"
                      >
                        {phase.label}
                      </p>
                    )}
                    <div className="bg-surface border border-border rounded-lg p-3">
                      <div className="flex flex-wrap items-center justify-between gap-2 border-b border-border pb-3 mb-3 text-sm">
                        {status(payment)}
                        {paymentDate(payment)}
                      </div>
                      <dl className="grid grid-cols-2 gap-3 text-sm">
                        {moneyColumns.map(column => (
                          <div key={column.key}>
                            <dt className="text-xs text-text-secondary mb-1">
                              {t(`amortization.col.${column.key}`)}
                            </dt>
                            <dd className="font-mono tabular-nums text-text-primary">
                              <CurrencyAmount
                                amount={column.value(payment)}
                                currency={schedule.currency}
                                conversionDate={
                                  payment.status === 'paid' ? payment.paymentDate : undefined
                                }
                                inline
                              />
                            </dd>
                          </div>
                        ))}
                      </dl>
                    </div>
                  </Fragment>
                );
              })}
            </div>
          )}
        </>
      )}
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
