import { PurchasePaymentSelector } from '@/components/assets/PurchasePaymentSelector';
/**
 * Wizard step 2 (Task 9): funding source (new/existing mortgage or cash), disbursement
 * route and the optional down payment.
 */
import { useTranslation } from 'react-i18next';
import { HelpTooltip } from '@/components/ui/HelpTooltip';
import { Input } from '@/components/ui/Input';
import { NumberInput } from '@/components/ui/NumberInput';
import { AccountSelector } from '@/components/ui/AccountSelector';
import { LiabilitySelector } from '@/components/ui/LiabilitySelector';
import type { FundingStepState } from './types';

interface FundingStepProps {
  funding: FundingStepState;
  kind?: 'physical' | 'property';
  currency: string;
  paymentDate?: string;
  paymentAmount?: string;
  onChange: (patch: Partial<FundingStepState>) => void;
  /** True once a previous confirm attempt created the property — its mortgage link is fixed. */
  locked: boolean;
  accountRouteMissingAccount: boolean;
  fundingAmountsValid: boolean;
}

export function FundingStep({
  funding,
  kind = 'property',
  currency,
  paymentDate,
  paymentAmount,
  onChange,
  locked,
  accountRouteMissingAccount,
  fundingAmountsValid,
}: FundingStepProps) {
  const { t } = useTranslation('realEstate');
  const { t: ta } = useTranslation('assets');

  return (
    <div className="space-y-4">
      <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
        <div>
          <div className="mb-1.5 flex items-center gap-2">
            <label htmlFor="wizard-source" className="text-sm font-medium">
              {t('wizard.fundingSource')} *
            </label>
            {locked && <HelpTooltip text={t('wizard.fundingLocked')} />}
          </div>
          <select
            id="wizard-source"
            value={funding.source}
            onChange={e =>
              onChange({
                source: e.target.value as FundingStepState['source'],
                loanAlreadyReceived: false,
                existingLoanReceiptId: undefined,
              })
            }
            disabled={locked}
            className="w-full h-11 md:h-10 px-3 pr-8 rounded-lg bg-surface border border-border text-text-primary disabled:opacity-50 text-base md:text-sm placeholder:text-text-muted hover:border-border/80 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-primary focus-visible:ring-offset-2 focus-visible:ring-offset-background disabled:cursor-not-allowed disabled:opacity-50 transition-colors duration-150"
          >
            <option value="new">{t('wizard.funding.new')}</option>
            <option value="existing">{t('wizard.funding.existing')}</option>
            <option value="none">{t('wizard.funding.none')}</option>
          </select>
        </div>
        {funding.source !== 'none' && !funding.loanAlreadyReceived && (
          <div>
            <div className="mb-1.5 flex items-center gap-2">
              <label htmlFor="wizard-route" className="text-sm font-medium">
                {t('wizard.disbursementRoute')}
              </label>
              {funding.route === 'direct' && <HelpTooltip text={t('wizard.valuationPolicy')} />}
            </div>
            <select
              id="wizard-route"
              value={funding.route}
              onChange={e => onChange({ route: e.target.value as FundingStepState['route'] })}
              disabled={locked}
              className="w-full h-11 md:h-10 px-3 pr-8 rounded-lg bg-surface border border-border text-text-primary disabled:opacity-50 text-base md:text-sm placeholder:text-text-muted hover:border-border/80 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-primary focus-visible:ring-offset-2 focus-visible:ring-offset-background disabled:cursor-not-allowed disabled:opacity-50 transition-colors duration-150"
            >
              <option value="direct">{t('wizard.route.direct')}</option>
              <option value="account">{t('wizard.route.account')}</option>
            </select>
          </div>
        )}
      </div>

      {funding.source !== 'none' && (
        <div className="grid grid-cols-1 md:grid-cols-3 gap-4">
          {funding.source === 'new' && (
            <div>
              <label htmlFor="wizard-mortgage-name" className="block text-sm font-medium mb-1.5">
                {t('wizard.loanName')} *
              </label>
              <Input
                className="h-11 text-base md:h-10 md:text-sm"
                id="wizard-mortgage-name"
                value={funding.mortgageName}
                onChange={e => onChange({ mortgageName: e.target.value })}
              />
            </div>
          )}
          <div>
            <label htmlFor="wizard-loan" className="block text-sm font-medium mb-1.5">
              {funding.source === 'new'
                ? `${t('wizard.loanAmount')} *`
                : t(funding.loanAlreadyReceived ? 'wizard.loanAmount' : 'wizard.disbursedAmount')}
            </label>
            <NumberInput
              className="h-11 font-mono text-base tabular-nums md:h-10 md:text-sm"
              id="wizard-loan"
              value={funding.loanAmount}
              onChange={v => onChange({ loanAmount: v })}
              placeholder="0.00"
              min="0.01"
            />
          </div>
          {funding.source === 'new' && (
            <div>
              <label htmlFor="wizard-rate" className="block text-sm font-medium mb-1.5">
                {t('wizard.interestRate')}
              </label>
              <NumberInput
                className="h-11 font-mono text-base tabular-nums md:h-10 md:text-sm"
                id="wizard-rate"
                value={funding.interestRate}
                onChange={v => onChange({ interestRate: v })}
                placeholder="3.5"
                min="0"
              />
            </div>
          )}
        </div>
      )}

      {funding.source === 'existing' && (
        <div>
          <label htmlFor="wizard-existing-loan" className="block text-sm font-medium mb-1.5">
            {t('wizard.existingLoan')}
          </label>
          <LiabilitySelector
            id="wizard-existing-loan"
            value={funding.existingMortgageId}
            onValueChange={v =>
              onChange({ existingMortgageId: v, existingLoanReceiptId: undefined })
            }
            placeholder={t('wizard.chooseLoan')}
            liabilityFilter={l =>
              (kind === 'physical' ? l.type !== 'CREDIT_CARD' : l.type === 'MORTGAGE') &&
              !l.representedByAccountId &&
              l.currency === currency
            }
            disabled={locked}
          />
        </div>
      )}

      {funding.source === 'new' && (
        <details className="border-t border-border pt-3 text-sm">
          <summary className="cursor-pointer py-2 font-medium">{t('wizard.knownTerms')}</summary>
          <p className="py-2 text-text-secondary">{t('wizard.termsHint')}</p>
          <div className="grid gap-4 md:grid-cols-2">
            <div>
              <label htmlFor="wizard-monthly" className="mb-1.5 block">
                {t('wizard.monthlyPayment')}
              </label>
              <NumberInput
                id="wizard-monthly"
                className="h-11 font-mono text-base tabular-nums md:h-10 md:text-sm"
                value={funding.minimumPayment ?? ''}
                min="0"
                onChange={minimumPayment => onChange({ minimumPayment })}
              />
            </div>
            <div>
              <label htmlFor="wizard-end" className="mb-1.5 block">
                {t('wizard.endDate')}
              </label>
              <Input
                id="wizard-end"
                type="date"
                min={paymentDate}
                className="h-11 text-base md:h-10 md:text-sm"
                value={funding.endDate ?? ''}
                onChange={event => onChange({ endDate: event.target.value })}
              />
            </div>
          </div>
        </details>
      )}
      {funding.source === 'existing' && (
        <div className="space-y-2 text-sm">
          <label htmlFor="wizard-receipt-mode" className="block font-medium">
            {t('wizard.receiptTiming')}
          </label>
          <select
            id="wizard-receipt-mode"
            className="h-11 w-full rounded-lg border border-border bg-background px-3 text-base md:h-10 md:text-sm"
            value={funding.loanAlreadyReceived ? 'received' : 'new'}
            onChange={event =>
              onChange({
                loanAlreadyReceived: event.target.value === 'received',
                existingLoanReceiptId: undefined,
                ...(event.target.value === 'received' ? { route: 'account' as const } : {}),
              })
            }
          >
            <option value="new">{t('wizard.newReceipt')}</option>
            <option value="received">{t('wizard.alreadyReceived')}</option>
          </select>
          {funding.loanAlreadyReceived && (
            <p className="text-text-secondary">{t('wizard.existingReceiptHint')}</p>
          )}
        </div>
      )}
      <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
        <div>
          <div className="mb-1.5 flex items-center gap-2">
            <label htmlFor="wizard-account" className="text-sm font-medium">
              {t('wizard.paidFrom')}
            </label>
            <HelpTooltip text={t('wizard.fundingCurrency', { currency })} />
          </div>
          {/* Shared account field: it is the SOURCE of the down-payment expense and, when the
              disbursement route is "bank pays my account", also the TARGET account the loan
              funds are paid into (toAccountId of the disbursement). */}
          <AccountSelector
            id="wizard-account"
            disabled={locked}
            className="h-11 text-base md:h-10 md:text-sm"
            currency={currency}
            value={funding.downPaymentAccountId}
            onValueChange={v => onChange({ downPaymentAccountId: v })}
            placeholder={t('wizard.paidFrom')}
          />
        </div>
        <div>
          <div className="mb-1.5 flex items-center gap-2">
            <label htmlFor="wizard-down" className="text-sm font-medium">
              {t(funding.source === 'none' ? 'wizard.purchasePayment' : 'wizard.downPaymentAmount')}
            </label>
            <HelpTooltip
              text={t(
                funding.source === 'none'
                  ? 'wizard.cashPaymentHelp'
                  : 'wizard.downPaymentCalculated'
              )}
            />
          </div>
          <NumberInput
            className="h-11 font-mono text-base tabular-nums md:h-10 md:text-sm"
            id="wizard-down"
            value={funding.downPaymentAmount}
            onChange={v => onChange({ downPaymentAmount: v })}
            placeholder="0.00"
            min="0"
          />
        </div>
      </div>

      {funding.source === 'existing' &&
        funding.loanAlreadyReceived &&
        funding.existingMortgageId &&
        funding.downPaymentAccountId && (
          <PurchasePaymentSelector
            key={`receipt-${funding.existingMortgageId}-${funding.downPaymentAccountId}-${funding.loanAmount}`}
            currency={currency}
            accountId={funding.downPaymentAccountId}
            loanId={funding.existingMortgageId}
            amount={funding.loanAmount}
            date={paymentDate}
            value={funding.existingLoanReceiptId}
            onChange={payment => onChange({ existingLoanReceiptId: payment.id })}
          />
        )}
      {accountRouteMissingAccount && (
        <p role="alert" className="text-sm text-error">
          {t('wizard.routeAccountRequired')}
        </p>
      )}
      {!fundingAmountsValid && (
        <p role="alert" className="text-sm text-error">
          {t('wizard.fundingMismatch')}
        </p>
      )}
      {Number(paymentAmount) > 0 && (
        <fieldset disabled={locked} className="space-y-3 border-t border-border pt-4">
          <div className="space-y-1 text-sm">
            <label htmlFor="wizard-payment-mode" className="block">
              {ta('ownership.payment')}
            </label>
            <select
              id="wizard-payment-mode"
              className="h-11 w-full rounded-lg border border-border bg-background p-2 text-base md:h-10 md:text-sm"
              value={funding.paymentMode ?? 'new'}
              onChange={event =>
                onChange({
                  paymentMode: event.target.value as 'new' | 'existing',
                  existingPaymentId: undefined,
                })
              }
            >
              <option value="new">{ta('ownership.newPayment')}</option>
              <option value="existing">{ta('ownership.existingPayment')}</option>
            </select>
          </div>
          {funding.paymentMode === 'existing' && (
            <>
              <p className="text-sm text-text-secondary">{ta('ownership.linkHint')}</p>
              {funding.downPaymentAccountId && (
                <PurchasePaymentSelector
                  key={`${funding.downPaymentAccountId}-${paymentDate}-${paymentAmount}`}
                  allowEarlier
                  currency={currency}
                  accountId={funding.downPaymentAccountId}
                  amount={paymentAmount}
                  date={paymentDate}
                  value={funding.existingPaymentId}
                  onChange={payment => onChange({ existingPaymentId: payment.id })}
                />
              )}
            </>
          )}
        </fieldset>
      )}
    </div>
  );
}
