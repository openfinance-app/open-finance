import type {
  ConversionPaymentCandidate,
  LiabilityConversionPayment,
} from '@/hooks/useLiabilityConversion';
import type { LoanComponent } from '@/types/transaction';
import { decimalField, ExactDecimal } from '@/utils/exact-decimal';

export interface SelectedConversionPayment {
  candidate: ConversionPaymentCandidate;
  component: LoanComponent | '';
  splits: Array<{ splitId: number; component: LoanComponent | '' }>;
}

export function selectConversionPayment(
  candidate: ConversionPaymentCandidate
): SelectedConversionPayment {
  return {
    candidate,
    component: candidate.splits.length ? '' : candidate.suggestedComponent || '',
    splits: candidate.splits.map(split => ({
      splitId: split.splitId,
      component: split.suggestedComponent || '',
    })),
  };
}

export function paymentSelectionComplete(payment: SelectedConversionPayment): boolean {
  return payment.splits.length
    ? payment.splits.every(split => !!split.component)
    : !!payment.component;
}

export function conversionPaymentRequest(
  payment: SelectedConversionPayment
): LiabilityConversionPayment {
  if (!paymentSelectionComplete(payment)) throw new Error('Incomplete loan payment classification');
  return {
    transactionId: payment.candidate.transactionId,
    sourceRevision: payment.candidate.sourceRevision,
    component: payment.component || undefined,
    splits: payment.splits.map(split => ({
      splitId: split.splitId,
      component: split.component as LoanComponent,
    })),
  };
}

export function conversionPaymentTotals(payments: SelectedConversionPayment[]) {
  const totals = {
    PRINCIPAL: new ExactDecimal(0),
    INTEREST: new ExactDecimal(0),
    INSURANCE: new ExactDecimal(0),
    FEE: new ExactDecimal(0),
    OTHER: new ExactDecimal(0),
    total: new ExactDecimal(0),
  };
  for (const payment of payments) {
    totals.total = totals.total.plus(decimalField(payment.candidate, 'amount'));
    if (!payment.splits.length && payment.component) {
      totals[payment.component] = totals[payment.component].plus(
        decimalField(payment.candidate, 'amount')
      );
    }
    for (const part of payment.splits) {
      const source = payment.candidate.splits.find(split => split.splitId === part.splitId);
      if (source && part.component)
        totals[part.component] = totals[part.component].plus(decimalField(source, 'amount'));
    }
  }
  return totals;
}
