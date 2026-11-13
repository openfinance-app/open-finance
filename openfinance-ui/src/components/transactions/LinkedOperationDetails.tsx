import type { ReactNode } from 'react';
import { Link } from 'react-router';
import { useQuery } from '@tanstack/react-query';
import { useTranslation } from 'react-i18next';
import { PrivateAmount } from '@/components/ui/PrivateAmount';
import { cn } from '@/lib/utils';
import { useAsset } from '@/hooks/useAssets';
import { useLiability } from '@/hooks/useLiabilities';
import { useProperty } from '@/hooks/useRealEstate';
import { useDateFormatter } from '@/hooks/useDateFormatter';
import { getTransferSource } from '@/hooks/useTransactions';
import type { Transaction } from '@/types/transaction';
import { decimalField, equalDecimals, ExactDecimal } from '@/utils/exact-decimal';
import { formatExactNumber } from '@/utils/format';
import { linkedOperationSummary } from '@/utils/linked-operation';

function Field({
  label,
  children,
  className,
}: {
  label: string;
  children: ReactNode;
  className?: string;
}) {
  return (
    <div className={cn('min-w-0', className)}>
      <dt className="mb-1 text-xs text-muted-foreground uppercase tracking-wide">{label}</dt>
      <dd className="break-words text-sm font-medium text-text-primary">{children}</dd>
    </div>
  );
}

function RecordedAmount({ value, currency }: { value: string; currency?: string }) {
  return (
    <PrivateAmount inline className="max-w-full break-words font-mono tabular-nums">
      {formatExactNumber(value)}
      {currency ? ` ${currency}` : ''}
    </PrivateAmount>
  );
}

export function LinkedOperationDetails({
  transaction,
  onNavigate,
}: {
  transaction: Transaction;
  onNavigate?: () => void;
}) {
  const { t } = useTranslation('transactions');
  const { t: ta } = useTranslation('assets');
  const { date: formatDate } = useDateFormatter();
  // The receiving leg has no holding link of its own. Resolve the booked source,
  // rather than describing the recipient's cash credit as a second unit movement.
  const incomingTransfer = !!transaction.transferId && transaction.type === 'INCOME';
  const source = useQuery({
    queryKey: ['transactions', 'transfer-source', transaction.transferId],
    queryFn: () => getTransferSource(transaction.transferId!),
    enabled: incomingTransfer,
  });
  const booked = incomingTransfer && source.data ? source.data : transaction;
  const { data: asset } = useAsset(booked.assetId ?? null);
  const { data: liability } = useLiability(booked.liabilityId ?? null);
  const { data: property } = useProperty(booked.realEstateId ?? 0);
  const operation = linkedOperationSummary(booked);
  if (!operation) return null;

  const quantity =
    booked.investmentQuantity == null
      ? null
      : new ExactDecimal(decimalField(booked, 'investmentQuantity'));
  const currency = booked.investmentCurrency || asset?.currency || booked.currency;
  const distinctInvestmentValue =
    quantity &&
    booked.investmentValue != null &&
    (currency !== transaction.currency ||
      !equalDecimals(decimalField(booked, 'investmentValue'), decimalField(transaction, 'amount')));
  const waitingForSource = incomingTransfer && !source.data;
  const linkClass = 'text-primary underline-offset-4 hover:underline';

  return (
    <Field
      label={t('linkedOperation.operation')}
      className="sm:col-span-2 border-t border-border pt-4"
    >
      <span>{t(operation.labelKey)}</span>
      {booked.assetId && (
        <>
          {' · '}
          <Link
            to={`/assets?highlight=${booked.assetId}`}
            onClick={onNavigate}
            className={linkClass}
          >
            {booked.assetName ||
              asset?.name ||
              t('linkedOperation.details.assetFallback', { id: booked.assetId })}
          </Link>
        </>
      )}
      {booked.liabilityId && (
        <>
          {' · '}
          <Link
            to={`/liabilities?highlight=${booked.liabilityId}`}
            onClick={onNavigate}
            className={linkClass}
          >
            {liability?.name ||
              t('linkedOperation.details.loanFallback', { id: booked.liabilityId })}
          </Link>
        </>
      )}
      {booked.realEstateId && (
        <>
          {' · '}
          <Link
            to={`/real-estate?highlight=${booked.realEstateId}`}
            onClick={onNavigate}
            className={linkClass}
          >
            {property?.name ||
              t('linkedOperation.details.propertyFallback', { id: booked.realEstateId })}
          </Link>
        </>
      )}
      {waitingForSource && (
        <p className="mt-1 text-sm font-normal text-text-secondary" role="status">
          {t(
            source.isError
              ? 'linkedOperation.details.fundingUnavailable'
              : 'linkedOperation.details.loadingFunding'
          )}
          {source.isError && (
            <button
              type="button"
              onClick={() => void source.refetch()}
              className="ml-2 text-primary underline underline-offset-4"
            >
              {t('linkedOperation.details.retry')}
            </button>
          )}
        </p>
      )}
      {(quantity ||
        booked.saleFees != null ||
        booked.valuationChange != null ||
        (booked.liabilityId &&
          booked.movementType === 'REPAYMENT' &&
          booked.principalAmount != null)) && (
        <dl className="mt-3 grid grid-cols-1 gap-x-4 gap-y-3 sm:grid-cols-2">
          {booked.valuationChange != null && (
            <Field label={ta('costEntry.valueChange')}>
              <RecordedAmount
                value={decimalField(booked, 'valuationChange')}
                currency={
                  property?.currency ??
                  asset?.currency ??
                  booked.originalCurrency ??
                  booked.currency
                }
              />
            </Field>
          )}
          {booked.saleFees != null && (
            <Field label={ta('ownership.fees')}>
              <RecordedAmount value={decimalField(booked, 'saleFees')} currency={booked.currency} />
            </Field>
          )}
          {quantity && (
            <Field
              label={t(
                quantity.isNegative()
                  ? 'linkedOperation.details.unitsRemoved'
                  : 'linkedOperation.details.unitsAdded'
              )}
            >
              <RecordedAmount value={quantity.abs().toFixed()} />
            </Field>
          )}
          {distinctInvestmentValue && (
            <Field label={t('linkedOperation.details.recordedValue')}>
              <RecordedAmount value={decimalField(booked, 'investmentValue')} currency={currency} />
            </Field>
          )}
          {quantity && booked.investmentPrice != null && (
            <Field
              label={t(
                booked.investmentPriceSource === 'HISTORICAL_CLOSE'
                  ? 'linkedOperation.details.estimatedUnitPrice'
                  : 'linkedOperation.details.recordedUnitPrice'
              )}
            >
              <RecordedAmount value={decimalField(booked, 'investmentPrice')} currency={currency} />
              {booked.investmentPriceDate && booked.investmentPriceDate !== booked.date && (
                <span
                  className="ml-1 text-xs text-text-secondary"
                  title={t('investment.priceDate')}
                >
                  ({formatDate(booked.investmentPriceDate)})
                </span>
              )}
            </Field>
          )}
          {booked.liabilityId &&
            booked.movementType === 'REPAYMENT' &&
            booked.principalAmount != null && (
              <Field label={t('form.repaymentPreviewPrincipal')}>
                <RecordedAmount
                  value={decimalField(booked, 'principalAmount')}
                  currency={liability?.currency}
                />
                {!liability?.currency && (
                  <span className="ml-1 text-xs text-text-secondary">
                    {t('linkedOperation.details.loanCurrency')}
                  </span>
                )}
              </Field>
            )}
        </dl>
      )}
    </Field>
  );
}
