import { useMemo, useState } from 'react';
import { isAxiosError } from 'axios';
import { useTranslation } from 'react-i18next';
import { AssetForm } from '@/components/assets/AssetForm';
import { FundingStep } from '@/components/real-estate/wizard/FundingStep';
import { ReviewStep } from '@/components/real-estate/wizard/ReviewStep';
import { normalizePurchasePayment } from '@/components/real-estate/wizard/purchase-funding';
import type { FundingStepState } from '@/components/real-estate/wizard/types';
import { useLiabilities } from '@/hooks/useLiabilities';
import { Button } from '@/components/ui/Button';
import { useAccounts } from '@/hooks/useAccounts';
import { useOwnershipOperation } from '@/hooks/useOwnershipOperation';
import { ExactDecimal } from '@/utils/exact-decimal';
import { apiErrorMessage } from '@/utils/api-error';
import { getToday } from '@/utils/date';
import type { Asset, AssetRequest } from '@/types/asset';

/** One purchase with a reviewed payment, optional borrowing and safe retry. */
export function AssetPurchaseFlow({
  plan,
  onClose,
  onPurchased,
}: {
  plan?: Asset;
  onClose: () => void;
  onPurchased: (id: number) => void;
}) {
  const { t } = useTranslation('assets');
  const [draft, setDraft] = useState<AssetRequest>();
  const [review, setReview] = useState(false);
  const [funding, setFunding] = useState<FundingStepState>({
    source: 'none',
    mortgageName: '',
    loanAmount: '',
    interestRate: '',
    route: 'direct',
    downPaymentAmount: '',
  });
  const { data: loans = [] } = useLiabilities();
  const [operationId] = useState(() => crypto.randomUUID());
  const [pending, setPending] = useState<object>();
  const purchase = useOwnershipOperation<{ assetId: number }>();
  const { data: accounts = [] } = useAccounts();
  const eligible = accounts.filter(account => account.currency === draft?.currency);
  const opening = useMemo(
    () =>
      plan
        ? {
            ...plan,
            acquisitionType: 'PURCHASE' as const,
            purchaseDate: getToday(),
            currentPrice: plan.purchasePrice,
            exactValues: {
              ...plan.exactValues,
              currentPrice: plan.exactValues?.purchasePrice ?? String(plan.purchasePrice),
            },
          }
        : undefined,
    [plan]
  );
  const details = useMemo(
    () =>
      draft
        ? ({
            ...opening,
            ...draft,
            id: plan?.id ?? 0,
            quantity: Number(draft.quantity),
            purchasePrice: Number(draft.purchasePrice),
            currentPrice: Number(draft.currentPrice),
            exactValues: {
              quantity: draft.quantity,
              purchasePrice: draft.purchasePrice,
              currentPrice: draft.currentPrice,
            },
          } as Asset)
        : opening,
    [draft, opening, plan?.id]
  );
  if (!review || !draft)
    return (
      <AssetForm
        asset={details}
        initialType="VEHICLE"
        kind="physical"
        purchaseMode
        submitLabel={t('ownership.reviewPayment')}
        onCancel={onClose}
        onSubmit={value => {
          setDraft({ ...value, acquisitionType: 'PURCHASE' });
          setReview(true);
          const total = new ExactDecimal(value.quantity).times(value.purchasePrice);
          setFunding(previous =>
            normalizePurchasePayment(
              {
                ...previous,
                existingPaymentId: undefined,
                existingLoanReceiptId: undefined,
                downPaymentAmount: ExactDecimal.max(
                  total.minus(previous.source === 'none' ? '0' : previous.loanAmount || '0'),
                  0
                ).toFixed(),
              },
              total.toFixed()
            )
          );
        }}
      />
    );
  const price = new ExactDecimal(draft.quantity).times(draft.purchasePrice);
  const loanAmount = new ExactDecimal(funding.source === 'none' ? '0' : funding.loanAmount || '0');
  const cashPayment = funding.route === 'direct' ? price.minus(loanAmount) : price;
  const selectedLoan = loans.find(loan => loan.id === funding.existingMortgageId);
  const amountsValid =
    loanAmount.gte(0) &&
    loanAmount.lte(price) &&
    loanAmount.plus(funding.downPaymentAmount || '0').eq(price);
  const missingAccount =
    cashPayment.gt(0) && !eligible.some(account => account.id === funding.downPaymentAccountId);
  const valid =
    price.gt(0) &&
    amountsValid &&
    !missingAccount &&
    draft.purchaseDate <= getToday() &&
    (funding.source === 'none' ||
      (loanAmount.gt(0) &&
        (funding.source === 'new'
          ? !!funding.mortgageName.trim()
          : selectedLoan?.currency === draft.currency))) &&
    (cashPayment.lte(0) ||
      funding.paymentMode !== 'existing' ||
      funding.existingPaymentId != null) &&
    (!funding.loanAlreadyReceived || funding.existingLoanReceiptId != null) &&
    (!funding.endDate || funding.endDate >= draft.purchaseDate);
  const updateFunding = (patch: Partial<FundingStepState>) =>
    setFunding(previous => {
      const next = { ...previous, ...patch };
      if ('source' in patch || 'loanAmount' in patch)
        next.downPaymentAmount = ExactDecimal.max(
          price.minus(next.source === 'none' ? '0' : next.loanAmount || '0'),
          0
        ).toFixed();
      if (
        'source' in patch ||
        'loanAmount' in patch ||
        'route' in patch ||
        'downPaymentAccountId' in patch
      ) {
        next.existingPaymentId = undefined;
        next.existingLoanReceiptId = undefined;
      }
      return normalizePurchasePayment(next, price.toFixed());
    });
  const submit = async () => {
    if (!valid || purchase.isPending) return;
    const data = pending ?? {
      operationId,
      asset: draft,
      plannedAssetId: plan?.id,
      accountId: funding.downPaymentAccountId,
      existingPaymentId:
        cashPayment.gt(0) && funding.paymentMode === 'existing'
          ? funding.existingPaymentId
          : undefined,
      existingLoanReceiptId: funding.loanAlreadyReceived
        ? funding.existingLoanReceiptId
        : undefined,
      loanAmount: loanAmount.toFixed(),
      route: funding.route === 'direct' ? 'DIRECT' : 'ACCOUNT',
      existingLoanId: funding.source === 'existing' ? funding.existingMortgageId : undefined,
      newLoan:
        funding.source === 'new'
          ? {
              name: funding.mortgageName.trim(),
              type: draft.type === 'VEHICLE' ? 'AUTO_LOAN' : 'LOAN',
              principal: loanAmount.toFixed(),
              currentBalance: '0',
              currency: draft.currency,
              startDate: draft.purchaseDate,
              interestRate: funding.interestRate ? Number(funding.interestRate) : undefined,
              minimumPayment: funding.minimumPayment || undefined,
              endDate: funding.endDate || undefined,
            }
          : undefined,
      paymentDescription: t('ownership.purchaseDescription', { name: draft.name }),
    };
    setPending(data);
    try {
      const result = await purchase.mutateAsync({ path: '/assets/purchase', data });
      onPurchased(result.assetId);
    } catch (error) {
      if (
        isAxiosError(error) &&
        error.response &&
        error.response.status >= 400 &&
        error.response.status < 500
      )
        setPending(undefined);
    }
  };
  return (
    <div className="space-y-5">
      <h3 className="font-semibold">{draft.name}</h3>
      <fieldset disabled={!!pending || purchase.isPending} className="space-y-4">
        <FundingStep
          kind="physical"
          funding={funding}
          currency={draft.currency}
          paymentDate={draft.purchaseDate}
          paymentAmount={cashPayment.toFixed()}
          onChange={updateFunding}
          locked={!!pending}
          accountRouteMissingAccount={missingAccount}
          fundingAmountsValid={amountsValid}
        />
      </fieldset>
      <ReviewStep
        kind="physical"
        property={{
          name: draft.name,
          address: '',
          propertyType: '',
          purchasePrice: price.toFixed(),
          purchaseDate: draft.purchaseDate,
          currentValue: new ExactDecimal(draft.quantity).times(draft.currentPrice).toFixed(),
          currency: draft.currency,
        }}
        funding={funding}
        account={accounts.find(account => account.id === funding.downPaymentAccountId)}
        mortgage={selectedLoan}
      />
      {purchase.error && (
        <p role="alert" className="text-sm text-error">
          {apiErrorMessage(purchase.error, t('ownership.error'))}
        </p>
      )}
      <div className="flex flex-wrap justify-between gap-3">
        <Button
          variant="secondary"
          disabled={!!pending || purchase.isPending}
          onClick={() => setReview(false)}
        >
          {t('ownership.previous')}
        </Button>
        <Button disabled={!valid} isLoading={purchase.isPending} onClick={() => void submit()}>
          {t('ownership.confirmPurchase')}
        </Button>
      </div>
    </div>
  );
}
