/**
 * Wizard step 3 (Task 9): read-only summary of the purchase before confirming.
 */
import { useTranslation } from 'react-i18next';
import type { FundingStepState, PropertyStepState } from './types';

interface ReviewStepProps {
  property: PropertyStepState;
  funding: FundingStepState;
}

export function ReviewStep({ property, funding }: ReviewStepProps) {
  const { t } = useTranslation('realEstate');

  return (
    <dl className="grid grid-cols-2 gap-x-6 gap-y-2 text-sm border border-border rounded-lg p-4 bg-surface">
      <dt className="text-text-secondary">{t('form.propertyName')}</dt>
      <dd className="text-right text-text-primary">{property.name}</dd>
      <dt className="text-text-secondary">{t('form.purchasePrice')}</dt>
      <dd className="text-right text-text-primary font-mono">
        {property.purchasePrice} {property.currency}
      </dd>
      <dt className="text-text-secondary">{t('form.currentValue')}</dt>
      <dd className="text-right text-text-primary font-mono">
        {property.currentValue} {property.currency}
      </dd>
      <dt className="text-text-secondary">{t('wizard.fundingSource')}</dt>
      <dd className="text-right text-text-primary">
        {t(`wizard.funding.${funding.source}`)}
        {funding.source !== 'none' && funding.loanAmount
          ? ` — ${funding.loanAmount} ${property.currency}`
          : ''}
      </dd>
      <dt className="text-text-secondary">{t('wizard.downPaymentAmount')}</dt>
      <dd className="text-right text-text-primary font-mono">
        {funding.downPaymentAmount || '0'} {property.currency}
      </dd>
      <dt className="text-text-secondary">{t('wizard.accountPayment')}</dt>
      <dd className="text-right text-text-primary font-mono">
        {funding.source !== 'none' && funding.route === 'direct'
          ? funding.downPaymentAmount || '0'
          : property.purchasePrice}{' '}
        {property.currency}
      </dd>
    </dl>
  );
}
