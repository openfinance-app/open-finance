import { ExactDecimal } from '@/utils/exact-decimal';
import type { FundingStepState } from '@/components/real-estate/wizard/types';

/** Clear choices that no longer apply when the purchase's funding changes. */
export function normalizePurchasePayment(
  funding: FundingStepState,
  price: string
): FundingStepState {
  const normalized =
    funding.source === 'existing'
      ? funding
      : {
          ...funding,
          existingMortgageId: undefined,
          loanAlreadyReceived: false,
          existingLoanReceiptId: undefined,
        };
  const cash = new ExactDecimal(price || '0').minus(
    funding.source !== 'none' && funding.route === 'direct' ? funding.loanAmount || '0' : '0'
  );
  return cash.lte(0)
    ? { ...normalized, paymentMode: 'new', existingPaymentId: undefined }
    : normalized;
}
