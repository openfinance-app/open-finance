import { useId, useState } from 'react';
import { useTranslation } from 'react-i18next';
import { useTransactions } from '@/hooks/useTransactions';
import { Input } from '@/components/ui/Input';
import { Button } from '@/components/ui/Button';
import { ConvertedAmount } from '@/components/ui/ConvertedAmount';
import { useDateFormatter } from '@/hooks/useDateFormatter';
import { ExactDecimal, decimalField } from '@/utils/exact-decimal';
import type { Transaction } from '@/types/transaction';

interface Props {
  currency: string;
  accountId?: number;
  amount?: string;
  date?: string;
  fromDate?: string;
  loanId?: number;
  allowEarlier?: boolean;
  value?: number;
  onChange: (payment: Transaction) => void;
}

export function PurchasePaymentSelector({
  currency,
  accountId,
  amount,
  date,
  fromDate,
  loanId,
  allowEarlier = false,
  value,
  onChange,
}: Props) {
  const { t } = useTranslation('assets');
  const { date: formatDate } = useDateFormatter();
  const id = useId();
  const [search, setSearch] = useState('');
  const [page, setPage] = useState(0);
  const result = useTransactions({
    type: loanId ? 'INCOME' : 'EXPENSE',
    accountId,
    dateFrom: allowEarlier || loanId ? fromDate : (date ?? fromDate),
    dateTo: date,
    keyword: search || undefined,
    excludeTransfers: true,
    page,
    size: 20,
    sort: 'date,desc',
  });
  const eligible = (result.data?.content ?? []).filter(
    payment =>
      payment.currency === currency &&
      !payment.transferId &&
      !payment.assetId &&
      !payment.realEstateId &&
      (loanId
        ? payment.liabilityId === loanId && payment.movementType === 'DISBURSEMENT'
        : !payment.liabilityId && !payment.movementType) &&
      payment.investmentQuantity == null &&
      (amount == null || new ExactDecimal(decimalField(payment, 'amount')).eq(amount))
  );
  return (
    <fieldset className="space-y-3">
      <legend className="mb-2 text-sm font-medium">
        {t(loanId ? 'ownership.chooseReceipt' : 'ownership.choosePayment')}
      </legend>
      <Input
        className="h-11 text-base md:h-10 md:text-sm"
        aria-label={t('ownership.searchPayments')}
        placeholder={t('ownership.searchPayments')}
        value={search}
        onChange={event => {
          setSearch(event.target.value);
          setPage(0);
        }}
      />
      {result.isLoading ? (
        <p role="status" className="text-sm">
          {t('ownership.loading')}
        </p>
      ) : result.error ? (
        <p role="alert" className="text-sm text-error">
          {t('ownership.loadPaymentsError')}
        </p>
      ) : eligible.length === 0 ? (
        <p className="text-sm text-text-secondary">{t('ownership.noPayments')}</p>
      ) : (
        <div className="divide-y divide-border">
          {eligible.map(payment => (
            <label key={payment.id} className="flex cursor-pointer items-start gap-3 py-3 text-sm">
              <input
                type="radio"
                name={id}
                checked={value === payment.id}
                onChange={() => onChange(payment)}
                className="mt-1 accent-primary"
              />
              <span className="min-w-0 flex-1">
                <span className="block break-words">{payment.description}</span>
                <span className="text-text-secondary">
                  {formatDate(payment.date)} · {payment.accountName}
                </span>
              </span>
              <ConvertedAmount
                className="font-mono tabular-nums"
                amount={payment.amount}
                currency={payment.currency}
                inline
              />
            </label>
          ))}
        </div>
      )}
      {(page > 0 || (result.data?.totalPages ?? 0) > 1) && (
        <div className="flex justify-between gap-3">
          <Button
            type="button"
            size="sm"
            variant="secondary"
            disabled={page === 0}
            onClick={() => setPage(page - 1)}
          >
            {t('ownership.previous')}
          </Button>
          <Button
            type="button"
            size="sm"
            variant="secondary"
            disabled={page + 1 >= (result.data?.totalPages ?? 0)}
            onClick={() => setPage(page + 1)}
          >
            {t('ownership.next')}
          </Button>
        </div>
      )}
    </fieldset>
  );
}
