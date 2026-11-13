import { useEffect, useRef, useState } from 'react';
import { useTranslation } from 'react-i18next';
import { ChevronDown } from 'lucide-react';
import { Button } from '@/components/ui/Button';
import { Input } from '@/components/ui/Input';
import { ConvertedAmount } from '@/components/ui/ConvertedAmount';
import { useDateFormatter } from '@/hooks/useDateFormatter';
import {
  useLiabilityConversionPayments,
  type ConversionPaymentCandidate,
  type LiabilityConversionCandidate,
} from '@/hooks/useLiabilityConversion';
import type { LoanComponent } from '@/types/transaction';
import {
  conversionPaymentTotals,
  paymentSelectionComplete,
  selectConversionPayment,
  type SelectedConversionPayment,
} from '@/components/liabilities/liability-conversion-payments';

interface Props {
  source: LiabilityConversionCandidate;
  selected: SelectedConversionPayment[];
  onChange: (payments: SelectedConversionPayment[]) => void;
  active: boolean;
  onReadyChange: (ready: boolean) => void;
}

export function LiabilityConversionPayments({
  source,
  selected,
  onChange,
  active,
  onReadyChange,
}: Props) {
  const { t } = useTranslation('liabilities');
  const [input, setInput] = useState(source.name);
  const [search, setSearch] = useState(source.name);
  const query = useLiabilityConversionPayments(source.accountId, search, active);
  const initialized = useRef(false);
  useEffect(() => {
    onReadyChange(!query.isPending && !query.isError && !query.isFetching);
  }, [query.isPending, query.isError, query.isFetching, onReadyChange]);
  useEffect(() => {
    if (active && !initialized.current && query.data && !query.isError) {
      initialized.current = true;
      onChange(query.data.items.map(selectConversionPayment));
    }
  }, [active, query.data, query.isError, onChange]);

  const shown = query.data?.items ?? [];
  const extraSelected = selected.filter(
    payment => !shown.some(item => item.transactionId === payment.candidate.transactionId)
  );
  const toggle = (candidate: ConversionPaymentCandidate, checked: boolean) => {
    onChange(
      checked
        ? [...selected, selectConversionPayment(candidate)]
        : selected.filter(payment => payment.candidate.transactionId !== candidate.transactionId)
    );
  };
  const reload = async () => {
    const previousIds = new Set(shown.map(item => item.transactionId));
    const result = await query.refetch();
    if (result.data && !result.isError && initialized.current)
      onChange(
        selected.flatMap(payment => {
          const fresh = result.data.items.find(
            item => item.transactionId === payment.candidate.transactionId
          );
          if (fresh)
            return [
              fresh.sourceRevision === payment.candidate.sourceRevision
                ? payment
                : selectConversionPayment(fresh),
            ];
          return previousIds.has(payment.candidate.transactionId) ? [] : [payment];
        })
      );
  };

  return (
    <div className="space-y-4">
      <p className="text-sm text-text-secondary">{t('conversion.payments.hint')}</p>
      <div className="flex flex-col gap-2 sm:flex-row sm:items-end">
        <div className="min-w-0 flex-1">
          <Input
            id="conversion-payment-search"
            label={t('conversion.payments.search')}
            value={input}
            onChange={event => setInput(event.target.value)}
            onKeyDown={event => {
              if (event.key === 'Enter') {
                event.preventDefault();
                setSearch(input);
              }
            }}
          />
        </div>
        <Button
          type="button"
          variant="outline"
          onClick={() => setSearch(input)}
          disabled={query.isFetching}
        >
          {t('conversion.payments.find')}
        </Button>
        <Button
          type="button"
          variant="ghost"
          onClick={() => void reload()}
          disabled={query.isFetching}
        >
          {t('conversion.payments.reload')}
        </Button>
      </div>
      {query.isFetching && (
        <p role="status" className="text-sm">
          {t('conversion.payments.loading')}
        </p>
      )}
      {query.isError && (
        <p role="alert" className="text-sm text-error">
          {t('conversion.payments.error')}
        </p>
      )}
      {!query.isPending && !query.isError && !shown.length && (
        <p role="status" className="text-sm text-text-secondary">
          {t('conversion.payments.empty')}
        </p>
      )}
      {query.data?.truncated && (
        <p role="status" className="text-sm text-warning">
          {t('conversion.payments.truncated')}
        </p>
      )}
      {!!shown.length && !query.isError && (
        <div className="flex flex-wrap gap-3">
          <Button
            type="button"
            variant="ghost"
            size="sm"
            onClick={() =>
              onChange([
                ...extraSelected,
                ...shown.map(
                  item =>
                    selected.find(
                      payment => payment.candidate.transactionId === item.transactionId
                    ) ?? selectConversionPayment(item)
                ),
              ])
            }
          >
            {t('conversion.payments.selectAll')}
          </Button>
          <Button type="button" variant="ghost" size="sm" onClick={() => onChange([])}>
            {t('conversion.payments.clear')}
          </Button>
        </div>
      )}
      <ConversionPaymentSummary payments={selected} currency={source.currency} />
      <div
        className="max-h-[min(36dvh,24rem)] overflow-y-auto overscroll-contain rounded-lg border border-border divide-y divide-border"
        aria-label={t('conversion.payments.list')}
      >
        {[...extraSelected.map(payment => payment.candidate), ...(!query.isError ? shown : [])].map(
          candidate => (
            <PaymentRow
              key={candidate.transactionId}
              candidate={candidate}
              selected={selected.find(
                payment => payment.candidate.transactionId === candidate.transactionId
              )}
              onToggle={checked => toggle(candidate, checked)}
              onChange={payment =>
                onChange(
                  selected.map(previous =>
                    previous.candidate.transactionId === candidate.transactionId
                      ? payment
                      : previous
                  )
                )
              }
            />
          )
        )}
      </div>
    </div>
  );
}

function PaymentRow({
  candidate,
  selected,
  onToggle,
  onChange,
}: {
  candidate: ConversionPaymentCandidate;
  selected?: SelectedConversionPayment;
  onToggle: (checked: boolean) => void;
  onChange: (payment: SelectedConversionPayment) => void;
}) {
  const { t } = useTranslation(['liabilities', 'categories', 'transactions']);
  const { date } = useDateFormatter();
  const description = candidate.description || t('conversion.payments.unnamed');
  const category = (name?: string, key?: string) =>
    key
      ? t(key, { ns: 'categories', defaultValue: name || key })
      : name || t('conversion.payments.uncategorized');
  const incomplete = selected && !paymentSelectionComplete(selected);
  return (
    <div className="min-w-0 p-3">
      <label className="flex min-h-11 cursor-pointer items-start gap-3">
        <input
          type="checkbox"
          checked={!!selected}
          onChange={event => onToggle(event.target.checked)}
          aria-label={t('conversion.payments.select', { description, date: date(candidate.date) })}
          className="mt-1 h-5 w-5 shrink-0 accent-primary"
        />
        <span className="min-w-0 flex-1">
          <span className="block break-words font-medium">{description}</span>
          <span className="block break-words text-sm text-text-secondary">
            {date(candidate.date)} · {candidate.accountName}
          </span>
        </span>
        <ConvertedAmount
          amount={candidate.amount}
          currency={candidate.currency}
          className="shrink-0 font-mono text-sm tabular-nums"
        />
      </label>
      {selected && (
        <details className="group mt-2" open={incomplete ? true : undefined}>
          <summary className="flex min-h-11 cursor-pointer list-none items-center justify-between gap-3 text-sm [&::-webkit-details-marker]:hidden">
            <span className={incomplete ? 'text-warning' : 'text-text-secondary'}>
              {t(incomplete ? 'conversion.payments.classify' : 'conversion.payments.breakdown')}
            </span>
            <ChevronDown className="h-4 w-4 shrink-0 group-open:rotate-180" aria-hidden="true" />
          </summary>
          <div className="space-y-3 pb-2">
            {candidate.notes && (
              <p className="break-words text-sm text-text-secondary">{candidate.notes}</p>
            )}
            {candidate.splits.length ? (
              candidate.splits.map(part => (
                <ComponentField
                  key={part.splitId}
                  id={`payment-${candidate.transactionId}-split-${part.splitId}`}
                  label={category(part.categoryName, part.categoryNameKey)}
                  amount={part.amount}
                  currency={candidate.currency}
                  value={
                    selected.splits.find(split => split.splitId === part.splitId)?.component || ''
                  }
                  onChange={component =>
                    onChange({
                      ...selected,
                      splits: selected.splits.map(split =>
                        split.splitId === part.splitId ? { ...split, component } : split
                      ),
                    })
                  }
                />
              ))
            ) : (
              <ComponentField
                id={`payment-${candidate.transactionId}-component`}
                label={category(candidate.categoryName, candidate.categoryNameKey)}
                amount={candidate.amount}
                currency={candidate.currency}
                value={selected.component}
                onChange={component => onChange({ ...selected, component })}
              />
            )}
          </div>
        </details>
      )}
    </div>
  );
}

function ComponentField({
  id,
  label,
  amount,
  currency,
  value,
  onChange,
}: {
  id: string;
  label: string;
  amount: number;
  currency: string;
  value: LoanComponent | '';
  onChange: (value: LoanComponent | '') => void;
}) {
  const { t } = useTranslation(['liabilities', 'transactions']);
  return (
    <div className="grid min-w-0 gap-2 sm:grid-cols-2 sm:items-center">
      <label htmlFor={id} className="min-w-0 break-words text-sm">
        {label} ·{' '}
        <ConvertedAmount amount={amount} currency={currency} className="font-mono tabular-nums" />
      </label>
      <select
        id={id}
        value={value}
        onChange={event => onChange(event.target.value as LoanComponent | '')}
        className="h-11 min-w-0 w-full rounded-lg border border-border bg-background px-3 text-base focus-visible:ring-2 focus-visible:ring-primary md:text-sm"
      >
        <option value="">{t('conversion.payments.chooseComponent')}</option>
        {(['PRINCIPAL', 'INTEREST', 'INSURANCE', 'FEE', 'OTHER'] as const).map(component => (
          <option key={component} value={component}>
            {t(`splitForm.loanComponents.${component}`, { ns: 'transactions' })}
          </option>
        ))}
      </select>
    </div>
  );
}

export function ConversionPaymentSummary({
  payments,
  currency,
}: {
  payments: SelectedConversionPayment[];
  currency: string;
}) {
  const { t } = useTranslation(['liabilities', 'transactions']);
  const totals = conversionPaymentTotals(payments);
  return (
    <div className="space-y-3 border-t border-border pt-4" aria-live="polite">
      <p className="text-sm font-medium">
        {t('conversion.payments.selected', { count: payments.length })} ·{' '}
        <ConvertedAmount
          amount={totals.total.toNumber()}
          currency={currency}
          className="font-mono tabular-nums"
        />
      </p>
      {!!payments.length && (
        <dl className="grid gap-x-6 gap-y-2 text-sm sm:grid-cols-2">
          {(['PRINCIPAL', 'INTEREST', 'INSURANCE', 'FEE', 'OTHER'] as const)
            .filter(component => component !== 'OTHER' || totals.OTHER.gt(0))
            .map(component => (
              <div key={component} className="flex flex-wrap justify-between gap-2">
                <dt className="text-text-secondary">
                  {t(`splitForm.loanComponents.${component}`, { ns: 'transactions' })}
                </dt>
                <dd>
                  <ConvertedAmount
                    amount={totals[component].toNumber()}
                    currency={currency}
                    className="font-mono tabular-nums"
                  />
                </dd>
              </div>
            ))}
        </dl>
      )}
    </div>
  );
}
