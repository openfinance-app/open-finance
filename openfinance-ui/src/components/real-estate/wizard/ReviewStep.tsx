import { useTranslation } from 'react-i18next';
import { ConvertedAmount } from '@/components/ui/ConvertedAmount';
import { ExactDecimal, decimalField } from '@/utils/exact-decimal';
import type { Account } from '@/types/account';
import type { Liability } from '@/types/liability';
import type { FundingStepState, PropertyStepState } from './types';

interface ReviewStepProps {
  property: PropertyStepState;
  funding: FundingStepState;
  account?: Account;
  mortgage?: Liability;
  kind?: 'property' | 'physical';
}

/** Review the actual bank movements and their net effect before committing the purchase. */
export function ReviewStep({
  property,
  funding,
  account,
  mortgage,
  kind = 'property',
}: ReviewStepProps) {
  const { t } = useTranslation('realEstate');
  const { t: ta } = useTranslation('assets');
  const price = new ExactDecimal(property.purchasePrice || '0');
  const value = new ExactDecimal(property.currentValue || '0');
  const loan = new ExactDecimal(funding.source === 'none' ? '0' : funding.loanAmount || '0');
  const accountPayment =
    funding.source !== 'none' && funding.route === 'direct' ? price.minus(loan) : price;
  const paymentAlreadyRecorded = accountPayment.gt(0) && funding.paymentMode === 'existing';
  const newDebit = paymentAlreadyRecorded ? new ExactDecimal(0) : accountPayment;
  const loanCredit =
    funding.source !== 'none' && funding.route === 'account' && !funding.loanAlreadyReceived
      ? loan
      : new ExactDecimal(0);
  const newDebt = funding.loanAlreadyReceived ? new ExactDecimal(0) : loan;
  const cashChange = loanCredit.minus(newDebit);
  const cashBefore = account
    ? new ExactDecimal(decimalField(account, account.ownBalance == null ? 'balance' : 'ownBalance'))
    : null;
  const debtBefore =
    funding.source === 'existing' && mortgage
      ? new ExactDecimal(decimalField(mortgage, 'currentBalance'))
      : new ExactDecimal(0);
  const amount = (number: InstanceType<typeof ExactDecimal>) => (
    <ConvertedAmount
      className="font-mono tabular-nums"
      inline
      amount={number.toNumber()}
      currency={property.currency}
    />
  );

  return (
    <div className="space-y-3">
      {paymentAlreadyRecorded && (
        <p className="text-sm text-text-secondary">{ta('ownership.linkHint')}</p>
      )}
      {funding.loanAlreadyReceived && (
        <p className="text-sm text-text-secondary">{t('wizard.existingReceiptHint')}</p>
      )}
      {!value.eq(price) && (
        <p
          role="status"
          className="text-sm text-warning rounded-lg border border-warning/30 bg-warning/5 p-3"
        >
          {t(value.isZero() ? 'wizard.zeroValuationReview' : 'wizard.valuationReview')}
        </p>
      )}
      <dl className="grid grid-cols-[minmax(0,1fr)_minmax(0,1fr)] gap-x-4 gap-y-2 text-sm border border-border rounded-lg p-4 bg-surface [&>dd]:text-right [&>dd]:break-words [&>dt]:text-text-secondary">
        <dt>{kind === 'physical' ? ta('form.assetName') : t('form.propertyName')}</dt>
        <dd>{property.name}</dd>
        <dt>{t('form.purchasePrice')}</dt>
        <dd>{amount(price)}</dd>
        <dt>{t('form.currentValue')}</dt>
        <dd>{amount(value)}</dd>
        <dt>{t('wizard.fundingSource')}</dt>
        <dd>
          {t(`wizard.funding.${funding.source}`)}
          {funding.source !== 'none' && (
            <div>{funding.source === 'new' ? funding.mortgageName : mortgage?.name}</div>
          )}
        </dd>
        {account && (
          <>
            <dt>{t('wizard.paidFrom')}</dt>
            <dd>{account.name}</dd>
          </>
        )}
        {funding.source !== 'none' &&
          funding.route === 'account' &&
          !funding.loanAlreadyReceived && (
            <>
              <dt>{t('wizard.loanCredit')}</dt>
              <dd>{amount(loan)}</dd>
            </>
          )}
        <dt>{t('wizard.accountPayment')}</dt>
        <dd>{amount(newDebit.negated())}</dd>
        <dt>{t('wizard.cashChange')}</dt>
        <dd>{amount(cashChange)}</dd>
        {cashBefore && (
          <>
            <dt>{t('wizard.cashAfter')}</dt>
            <dd>{amount(cashBefore.plus(cashChange))}</dd>
          </>
        )}
        {funding.source !== 'none' && (
          <>
            <dt>{t('wizard.debtAfter')}</dt>
            <dd>{amount(debtBefore.plus(newDebt))}</dd>
          </>
        )}
        <dt className="border-t border-border pt-2">{t('wizard.netWorthChange')}</dt>
        <dd className="border-t border-border pt-2 font-semibold">
          {amount(value.plus(cashChange).minus(newDebt))}
        </dd>
      </dl>
    </div>
  );
}
