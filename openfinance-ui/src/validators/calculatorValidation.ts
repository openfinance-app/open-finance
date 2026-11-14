import { ExactDecimal } from '@/utils/exact-decimal';
import i18n from '@/i18n';
import type {
  CompoundInterestInput,
  EarlyPayoffInput,
  FreedomCalculatorInput,
  LoanCalculatorInput,
} from '@/types/calculator';
import { CALCULATION_LIMITS } from '@/types/calculator';

const between = (value: number | string, min: number, max: number): boolean => {
  try {
    const amount = new ExactDecimal(value);
    return amount.isFinite() && amount.gte(min) && amount.lte(max);
  } catch {
    return false;
  }
};
const money = (value: number | string, max: number): boolean => {
  try {
    const amount = new ExactDecimal(value);
    return amount.isFinite() && amount.gte(0) && amount.lt(max) && amount.decimalPlaces() <= 18;
  } catch {
    return false;
  }
};
const error = (key: string): string => i18n.t(`validation.${key}`, { ns: 'tools' });

export function validateCompoundInterest(input: CompoundInterestInput): string | null {
  if (!money(input.principal, 1e15)) return error('principal');
  if (!between(input.annualRate, 0, 100)) return error('rate');
  if (!Number.isInteger(input.years) || !between(input.years, 1, 100)) {
    return error('wholeYears');
  }
  if (![1, 2, 4, 12, 52, 365].includes(input.compoundingFrequency)) return error('frequency');
  if (!money(input.regularContribution, 1e12)) return error('contribution');
  return null;
}

export function validateLoan(input: LoanCalculatorInput): string | null {
  if (!money(input.principal, 1e15) || new ExactDecimal(input.principal).isZero())
    return error('loanPrincipal');
  if (!between(input.annualRate, 0, 100)) return error('rate');
  const months = input.years * 12;
  if (!between(months, 1, 1200) || Math.abs(months - Math.round(months)) > 1e-8) {
    return error('loanTerm');
  }
  return null;
}

export function validateFreedom(input: FreedomCalculatorInput): string | null {
  if (!money(input.currentSavings, 1e15)) return error('savings');
  if (!money(input.monthlyExpenses, 1e12)) return error('expenses');
  if (!money(input.monthlyContribution ?? 0, 1e12)) return error('contribution');
  if (
    !between(
      input.withdrawalRate ?? 4,
      CALCULATION_LIMITS.MIN_WITHDRAWAL_RATE,
      CALCULATION_LIMITS.MAX_WITHDRAWAL_RATE
    )
  )
    return error('withdrawal');
  if (
    !between(
      input.expectedAnnualReturn,
      CALCULATION_LIMITS.MIN_RETURN_RATE,
      CALCULATION_LIMITS.MAX_RETURN_RATE
    )
  )
    return error('return');
  if (!between(input.inflationRate ?? 2.5, -99, 100)) return error('inflation');
  return null;
}

export function validateEarlyPayoff(input: EarlyPayoffInput): string | null {
  if (!money(input.loanBalance, 1e15) || new ExactDecimal(input.loanBalance).isZero())
    return error('loanPrincipal');
  if (!between(input.annualRate, 0, 100)) return error('rate');
  if (
    !Number.isInteger(input.remainingYears) ||
    !between(input.remainingYears, 0, 100) ||
    !Number.isInteger(input.remainingMonthsExtra) ||
    !between(input.remainingMonthsExtra, 0, 11)
  ) {
    return error('remainingTerm');
  }
  const months = input.remainingYears * 12 + input.remainingMonthsExtra;
  if (!between(months, 1, 1200)) return error('remainingTerm');
  if (!money(input.monthlyExtraPayment, 1e15)) return error('contribution');
  if (
    input.lumpSumPayments.some(
      payment =>
        !Number.isInteger(payment.month) ||
        !between(payment.month, 1, months) ||
        !money(payment.amount, 1e15)
    )
  ) {
    return error('lumpSum');
  }
  return null;
}
