/**
 * BuyPropertyWizard Component (Task 9)
 *
 * Optional guided flow for acquiring a property in three steps:
 *  1. Property — name, address, type, price/date/value/currency
 *  2. Funding — new/existing mortgage (or cash), disbursement route
 *     (bank pays the seller directly vs. pays my account) and an optional
 *     down payment from a checking account
 *  3. Review — executes the endpoint sequence and closes
 *
 * Endpoint sequence (documented decision): create liability → create property
 * (carrying mortgageId) → disburse → down-payment purchase
 * transaction. A direct disbursement needs the property to exist, so the
 * disbursement always follows the property creation.
 *
 * Retry dedupe: resources created by a failed confirm attempt (liability,
 * property, completed disbursement) are memoized in createdIds and reused, so
 * a retry never duplicates them or re-disburses an already-disbursed loan.
 */
import { useState } from 'react';
import { useTranslation } from 'react-i18next';
import { Building2, Home, Landmark, Wallet } from 'lucide-react';
import { Button } from '@/components/ui/Button';
import { useLiabilities, useCreateLiability, useDisburseLiability } from '@/hooks/useLiabilities';
import { useCreateProperty } from '@/hooks/useRealEstate';
import { useCreateTransaction } from '@/hooks/useTransactions';
import { useAuthContext } from '@/context/AuthContext';
import { DEFAULT_CURRENCY } from '@/utils/currency';
import { getToday } from '@/utils/date';
import { PropertyStep } from './wizard/PropertyStep';
import { FundingStep } from './wizard/FundingStep';
import { ReviewStep } from './wizard/ReviewStep';
import type { Account } from '@/types/account';
import type { CreatedIdsState, FundingStepState, PropertyStepState } from './wizard/types';
import type { RealEstatePropertyRequest } from '@/types/realEstate';
import type { TransactionRequest } from '@/types/transaction';

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

  // Memoized creation results: when a confirm attempt fails after the liability/property were
  // created, a retry must reuse them instead of creating duplicates.
  const [createdIds, setCreatedIds] = useState<CreatedIdsState>({});

  const { data: liabilities = [] } = useLiabilities();
  const createLiability = useCreateLiability();
  const createProperty = useCreateProperty();
  const createTransaction = useCreateTransaction();
  const disburse = useDisburseLiability();

  const updateProperty = (patch: Partial<PropertyStepState>) =>
    setProperty(prev => ({ ...prev, ...patch }));
  const updateFunding = (patch: Partial<FundingStepState>) =>
    setFunding(prev => ({ ...prev, ...patch }));

  const price = Number(property.purchasePrice);
  const loan = Number(funding.loanAmount);
  const down = Number(funding.downPaymentAmount);

  // The 'account' disbursement route sends the funds to a checking account — one must be
  // selected, otherwise the request would go out with an undefined toAccountId.
  const accountRouteMissingAccount =
    funding.source !== 'none' &&
    funding.route === 'account' &&
    funding.downPaymentAccountId == null;

  const selectedAccount = accounts.find(a => a.id === funding.downPaymentAccountId);
  const needsAccount = down > 0 || (funding.source !== 'none' && funding.route === 'account');
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
        ? fundingStepValid && !accountRouteMissingAccount && fundingCurrencyValid
        : true;

  // Once a confirm attempt has created the property, its mortgage link is fixed: switching
  // the funding source on a retry would leave the property linked to the wrong liability.
  const fundingLocked = createdIds.propertyId != null;

  /** Resolves the mortgage to link (creating it on the 'new' path, reusing on retry). */
  const ensureMortgageId = async (): Promise<number | undefined> => {
    // Prefer the explicit selection on the 'existing' path; the created liability is only
    // reused on the 'new' path (the funding source is locked once the property exists, so
    // the two can no longer diverge between attempts).
    let mortgageId: number | undefined =
      funding.source === 'existing'
        ? funding.existingMortgageId
        : funding.source === 'new'
          ? createdIds.liabilityId
          : undefined;
    if (funding.source === 'new' && mortgageId == null) {
      const liability = await createLiability.mutateAsync({
        name: funding.mortgageName.trim(),
        type: 'MORTGAGE',
        principal: funding.loanAmount,
        currentBalance: '0',
        interestRate: funding.interestRate ? Number(funding.interestRate) : undefined,
        startDate: property.purchaseDate,
        currency: property.currency,
      });
      mortgageId = liability.id;
      setCreatedIds(prev => ({ ...prev, liabilityId: liability.id }));
    }
    return mortgageId;
  };

  /** Creates the property (carrying the mortgage link), reusing a previous attempt's ID. */
  const ensurePropertyId = async (mortgageId: number | undefined): Promise<number> => {
    if (createdIds.propertyId != null) {
      return createdIds.propertyId;
    }
    const created = await createProperty.mutateAsync({
      name: property.name.trim(),
      address: property.address.trim(),
      propertyType: property.propertyType as RealEstatePropertyRequest['propertyType'],
      purchasePrice: property.purchasePrice,
      purchaseDate: property.purchaseDate,
      currentValue: property.currentValue,
      currency: property.currency,
      mortgageId: mortgageId ?? null,
      rentalIncome: null,
      notes: null,
      documents: null,
      latitude: null,
      longitude: null,
      isActive: true,
    });
    setCreatedIds(prev => ({ ...prev, propertyId: created.id }));
    return created.id;
  };

  /** Disburses the loan — a disbursement that already completed is never replayed on retry. */
  const disburseIfNeeded = async (mortgageId: number | undefined, propertyId: number) => {
    if (mortgageId == null || loan <= 0 || createdIds.disbursedLiabilityId === mortgageId) {
      return;
    }
    await disburse.mutateAsync({
      liabilityId: mortgageId,
      request: {
        directRealEstateId: funding.route === 'direct' ? propertyId : undefined,
        toAccountId: funding.route === 'account' ? funding.downPaymentAccountId : undefined,
        amount: loan,
        date: property.purchaseDate,
      },
    });
    setCreatedIds(prev => ({ ...prev, disbursedLiabilityId: mortgageId }));
  };

  /** Records the optional down payment as a purchase expense on the property. */
  const createDownPayment = async (propertyId: number) => {
    if (funding.downPaymentAccountId == null || down <= 0) {
      return;
    }
    const request: TransactionRequest = {
      accountId: funding.downPaymentAccountId,
      type: 'EXPENSE',
      amount: down,
      currency: property.currency,
      date: property.purchaseDate,
      description: t('wizard.downPaymentDescription', { name: property.name }),
      realEstateId: propertyId,
    };
    await createTransaction.mutateAsync(request);
  };

  const handleConfirm = async () => {
    setIsSubmitting(true);
    setError(null);
    try {
      if (!fundingCurrencyValid)
        throw new Error(t('wizard.fundingCurrency', { currency: property.currency }));
      // Sequence (documented decision): liability → property (mortgageId) → disburse →
      // down-payment. Each step reuses what a previous failed attempt already created.
      const mortgageId = await ensureMortgageId();
      const propertyId = await ensurePropertyId(mortgageId);
      await disburseIfNeeded(mortgageId, propertyId);
      await createDownPayment(propertyId);
      onClose();
    } catch (e) {
      setError(e instanceof Error ? e.message : t('wizard.error'));
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
          disabled={isSubmitting}
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
