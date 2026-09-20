/**
 * Wizard step 2 (Task 9): funding source (new/existing mortgage or cash), disbursement
 * route and the optional down payment.
 */
import { useTranslation } from 'react-i18next';
import { Input } from '@/components/ui/Input';
import { NumberInput } from '@/components/ui/NumberInput';
import { AccountSelector } from '@/components/ui/AccountSelector';
import { LiabilitySelector } from '@/components/ui/LiabilitySelector';
import type { FundingStepState } from './types';

interface FundingStepProps {
  funding: FundingStepState;
  currency: string;
  onChange: (patch: Partial<FundingStepState>) => void;
  /** True once a previous confirm attempt created the property — its mortgage link is fixed. */
  locked: boolean;
  accountRouteMissingAccount: boolean;
}

export function FundingStep({
  funding,
  currency,
  onChange,
  locked,
  accountRouteMissingAccount,
}: FundingStepProps) {
  const { t } = useTranslation('realEstate');

  return (
    <div className="space-y-4">
      <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
        <div>
          <label htmlFor="wizard-source" className="block text-sm font-medium mb-1.5">
            {t('wizard.fundingSource')} *
          </label>
          <select
            id="wizard-source"
            value={funding.source}
            onChange={e => onChange({ source: e.target.value as FundingStepState['source'] })}
            disabled={locked}
            className="w-full h-10 px-3 pr-8 rounded-lg bg-surface border border-border text-text-primary disabled:opacity-50 text-sm placeholder:text-text-muted hover:border-border/80 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-primary focus-visible:ring-offset-2 focus-visible:ring-offset-background disabled:cursor-not-allowed disabled:opacity-50 transition-colors duration-150"
          >
            <option value="new">{t('wizard.funding.new')}</option>
            <option value="existing">{t('wizard.funding.existing')}</option>
            <option value="none">{t('wizard.funding.none')}</option>
          </select>
          {locked && (
            <p className="text-xs text-text-secondary mt-1">{t('wizard.fundingLocked')}</p>
          )}
        </div>
        <div>
          <label htmlFor="wizard-route" className="block text-sm font-medium mb-1.5">
            {t('wizard.disbursementRoute')}
          </label>
          <select
            id="wizard-route"
            value={funding.route}
            onChange={e => onChange({ route: e.target.value as FundingStepState['route'] })}
            disabled={funding.source === 'none'}
            className="w-full h-10 px-3 pr-8 rounded-lg bg-surface border border-border text-text-primary disabled:opacity-50 text-sm placeholder:text-text-muted hover:border-border/80 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-primary focus-visible:ring-offset-2 focus-visible:ring-offset-background disabled:cursor-not-allowed disabled:opacity-50 transition-colors duration-150"
          >
            <option value="direct">{t('wizard.route.direct')}</option>
            <option value="account">{t('wizard.route.account')}</option>
          </select>
          {funding.source !== 'none' && funding.route === 'direct' && (
            <p className="text-xs text-text-secondary mt-1">{t('wizard.valuationPolicy')}</p>
          )}
        </div>
      </div>

      {funding.source !== 'none' && (
        <div className="grid grid-cols-1 md:grid-cols-3 gap-4">
          {funding.source === 'new' && (
            <div>
              <label htmlFor="wizard-mortgage-name" className="block text-sm font-medium mb-1.5">
                {t('wizard.mortgageName')} *
              </label>
              <Input
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
                : t('wizard.disbursedAmount')}
            </label>
            <NumberInput
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
          <label className="block text-sm font-medium mb-1.5">{t('wizard.existingMortgage')}</label>
          <LiabilitySelector
            value={funding.existingMortgageId}
            onValueChange={v => onChange({ existingMortgageId: v })}
            placeholder={t('form.selectMortgage')}
            liabilityFilter={l => l.type === 'MORTGAGE' && l.currency === currency}
            disabled={locked}
          />
        </div>
      )}

      <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
        <div>
          <label className="block text-sm font-medium mb-1.5">
            {t('wizard.downPaymentAccount')}
          </label>
          <p className="text-xs text-text-secondary mb-1">
            {t('wizard.fundingCurrency', { currency })}
          </p>
          {/* Shared account field: it is the SOURCE of the down-payment expense and, when the
              disbursement route is "bank pays my account", also the TARGET account the loan
              funds are paid into (toAccountId of the disbursement). */}
          <AccountSelector
            currency={currency}
            value={funding.downPaymentAccountId}
            onValueChange={v => onChange({ downPaymentAccountId: v })}
            placeholder={t('wizard.downPaymentAccount')}
          />
        </div>
        <div>
          <label htmlFor="wizard-down" className="block text-sm font-medium mb-1.5">
            {t('wizard.downPaymentAmount')}
          </label>
          <NumberInput
            id="wizard-down"
            value={funding.downPaymentAmount}
            onChange={v => onChange({ downPaymentAmount: v })}
            placeholder="0.00"
            min="0"
          />
        </div>
      </div>

      {accountRouteMissingAccount && (
        <p role="alert" className="text-sm text-error">
          {t('wizard.routeAccountRequired')}
        </p>
      )}
    </div>
  );
}
