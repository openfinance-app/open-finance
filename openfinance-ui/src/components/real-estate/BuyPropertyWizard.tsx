/** A purchase commits once on the server; retries keep the same operation identifier and payload. */
import { useState } from 'react';
import { useTranslation } from 'react-i18next';
import { isAxiosError } from 'axios';
import { Building2, Home, Landmark, Wallet } from 'lucide-react';
import { Button } from '@/components/ui/Button';
import { useLiabilities } from '@/hooks/useLiabilities';
import { usePurchaseProperty, type PropertyPurchaseRequest } from '@/hooks/useRealEstate';
import { useAuthContext } from '@/context/AuthContext';
import { DEFAULT_CURRENCY } from '@/utils/currency';
import { getToday } from '@/utils/date';
import { add } from '@/utils/money';
import { PropertyStep } from './wizard/PropertyStep';
import { FundingStep } from './wizard/FundingStep';
import { ReviewStep } from './wizard/ReviewStep';
import type { Account } from '@/types/account';
import type { FundingStepState, PropertyStepState } from './wizard/types';
import type { RealEstatePropertyRequest } from '@/types/realEstate';

const STEP_ICONS = [Building2, Landmark, Wallet];

export function BuyPropertyWizard({
  accounts,
  onClose,
}: {
  accounts: Account[];
  onClose: () => void;
}) {
  const { t } = useTranslation('realEstate');
  const { baseCurrency } = useAuthContext();
  const today = getToday();
  const [step, setStep] = useState(0);
  const [isSubmitting, setIsSubmitting] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const [property, setProperty] = useState<PropertyStepState>({
    name: '',
    address: '',
    propertyType: 'RESIDENTIAL',
    purchasePrice: '',
    purchaseDate: today,
    currentValue: '0',
    currency: baseCurrency || DEFAULT_CURRENCY,
  });
  const [funding, setFunding] = useState<FundingStepState>({
    source: 'new',
    mortgageName: '',
    loanAmount: '',
    interestRate: '',
    existingMortgageId: undefined,
    route: 'direct',
    downPaymentAmount: '',
    downPaymentAccountId: undefined,
  });

  const [operationId] = useState(() => crypto.randomUUID());
  const [pendingRequest, setPendingRequest] = useState<PropertyPurchaseRequest | null>(null);
  const { data: liabilities = [] } = useLiabilities();
  const purchase = usePurchaseProperty();

  const updateProperty = (patch: Partial<PropertyStepState>) =>
    setProperty(prev => ({ ...prev, ...patch }));
  const updateFunding = (patch: Partial<FundingStepState>) =>
    setFunding(prev => ({ ...prev, ...patch }));

  const price = Number(property.purchasePrice);
  const loan = funding.source === 'none' ? 0 : Number(funding.loanAmount);
  const down = Number(funding.downPaymentAmount);
  const cashPayment = funding.source !== 'none' && funding.route === 'direct' ? down : price;
  const fundingAmountsValid =
    Number.isFinite(loan) &&
    Number.isFinite(down) &&
    loan >= 0 &&
    down >= 0 &&
    add(loan, down) === price;

  // The 'account' disbursement route sends the funds to a checking account — one must be
  // selected, otherwise the request would go out with an undefined toAccountId.
  const accountRouteMissingAccount = cashPayment > 0 && funding.downPaymentAccountId == null;

  const selectedAccount = accounts.find(a => a.id === funding.downPaymentAccountId);
  const needsAccount = cashPayment > 0;
  const fundingCurrencyValid =
    (!needsAccount || selectedAccount?.currency === property.currency) &&
    (funding.source !== 'existing' ||
      liabilities.find(l => l.id === funding.existingMortgageId)?.currency === property.currency);

  const fundingStepValid =
    funding.source === 'new'
      ? funding.mortgageName.trim() !== '' && loan > 0
      : funding.source === 'existing'
        ? funding.existingMortgageId != null && loan > 0
        : true;

  const stepValid =
    step === 0
      ? property.name.trim() !== '' &&
        property.address.trim() !== '' &&
        price > 0 &&
        Number(property.currentValue) >= 0
      : step === 1
        ? fundingStepValid &&
          fundingAmountsValid &&
          !accountRouteMissingAccount &&
          fundingCurrencyValid
        : true;

  const fundingLocked = pendingRequest !== null;

  const purchaseRequest = (): PropertyPurchaseRequest => ({
    operationId,
    property: {
      ...property,
      name: property.name.trim(),
      address: property.address.trim(),
      propertyType: property.propertyType as RealEstatePropertyRequest['propertyType'],
      isActive: true,
    },
    newMortgage:
      funding.source === 'new'
        ? {
            name: funding.mortgageName.trim(),
            type: 'MORTGAGE',
            principal: funding.loanAmount,
            currentBalance: '0',
            interestRate: funding.interestRate ? Number(funding.interestRate) : undefined,
            startDate: property.purchaseDate,
            currency: property.currency,
          }
        : undefined,
    existingMortgageId: funding.source === 'existing' ? funding.existingMortgageId : undefined,
    loanAmount: funding.source === 'none' ? '0' : funding.loanAmount,
    downPayment: funding.downPaymentAmount || '0',
    route: funding.route === 'direct' ? 'DIRECT' : 'ACCOUNT',
    accountId: funding.downPaymentAccountId,
    paymentDescription: t('wizard.purchasePaymentDescription', { name: property.name }),
  });

  const handleConfirm = async () => {
    setIsSubmitting(true);
    setError(null);
    try {
      if (!fundingAmountsValid || accountRouteMissingAccount)
        throw new Error(t('wizard.fundingMismatch'));
      if (!fundingCurrencyValid)
        throw new Error(t('wizard.fundingCurrency', { currency: property.currency }));
      const request = pendingRequest ?? purchaseRequest();
      setPendingRequest(request);
      await purchase.mutateAsync(request);
      onClose();
    } catch (e) {
      if (isAxiosError(e) && e.response && e.response.status >= 400 && e.response.status < 500) {
        setPendingRequest(null);
      }
      setError(
        isAxiosError<{ message?: string }>(e) && e.response?.data.message
          ? e.response.data.message
          : e instanceof Error
            ? e.message
            : t('wizard.error')
      );
    } finally {
      setIsSubmitting(false);
    }
  };

  return (
    <div className="space-y-6" data-testid="buy-property-wizard">
      {/* Stepper */}
      <ol className="flex items-center gap-2 text-sm">
        {['property', 'funding', 'review'].map((key, i) => {
          const Icon = STEP_ICONS[i];
          const active = step === i;
          return (
            <li
              key={key}
              className={`flex items-center gap-1.5 px-3 py-1.5 rounded-md border ${
                active
                  ? 'border-primary text-primary bg-primary/10'
                  : i < step
                    ? 'border-primary/30 text-primary'
                    : 'border-border text-text-secondary'
              }`}
            >
              <Icon className="h-4 w-4" />
              {t(`wizard.steps.${key}`)}
            </li>
          );
        })}
      </ol>

      {/* Step 1: property */}
      {step === 0 && <PropertyStep property={property} onChange={updateProperty} today={today} />}

      {/* Step 2: funding */}
      {step === 1 && (
        <FundingStep
          currency={property.currency}
          funding={funding}
          onChange={updateFunding}
          locked={fundingLocked}
          accountRouteMissingAccount={accountRouteMissingAccount}
          fundingAmountsValid={fundingAmountsValid}
        />
      )}

      {/* Step 3: review */}
      {step === 2 && <ReviewStep property={property} funding={funding} />}

      {error && (
        <p role="alert" className="text-sm text-error">
          {error}
        </p>
      )}

      {/* Actions */}
      <div className="flex justify-between pt-4 border-t border-border">
        <Button
          variant="ghost"
          type="button"
          onClick={step === 0 ? onClose : () => setStep(s => s - 1)}
          disabled={isSubmitting || fundingLocked}
        >
          {step === 0 ? t('form.cancel') : t('wizard.back')}
        </Button>
        {step < 2 ? (
          <Button
            variant="primary"
            type="button"
            disabled={!stepValid}
            onClick={() => setStep(s => s + 1)}
          >
            {t('wizard.next')}
          </Button>
        ) : (
          <Button variant="primary" type="button" isLoading={isSubmitting} onClick={handleConfirm}>
            <Home className="h-4 w-4 mr-1.5" />
            {t('wizard.confirm')}
          </Button>
        )}
      </div>
    </div>
  );
}
