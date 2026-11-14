import { useState, useCallback } from 'react';
import { validateLoan } from '@/validators/calculatorValidation';
import {
  DEFAULT_LOAN_CALCULATOR_INPUT,
  type LoanCalculatorInput,
  type LoanCalculatorResult,
  type LoanAmortizationEntry,
} from '../types/calculator';
import { calculatedAmounts, ExactDecimal } from '@/utils/exact-decimal';

interface LoanCalculatorState {
  input: LoanCalculatorInput;
  result: LoanCalculatorResult | null;
  error: string | null;
}

export function useLoanCalculator() {
  const [state, setState] = useState<LoanCalculatorState>({
    input: DEFAULT_LOAN_CALCULATOR_INPUT,
    result: null,
    error: null,
  });

  const updateInput = useCallback(
    <K extends keyof LoanCalculatorInput>(key: K, value: LoanCalculatorInput[K]) => {
      setState(prev => ({
        ...prev,
        input: { ...prev.input, [key]: value },
        result: null,
        error: null,
      }));
    },
    []
  );

  const resetInputs = useCallback(() => {
    setState({
      input: DEFAULT_LOAN_CALCULATOR_INPUT,
      result: null,
      error: null,
    });
  }, []);

  const calculate = useCallback(() => {
    const { principal, annualRate, years } = state.input;
    const error = validateLoan(state.input);
    if (error) {
      setState(prev => ({ ...prev, result: null, error }));
      return;
    }

    const monthlyRate = new ExactDecimal(annualRate).div(1200);
    const totalPayments = Math.round(years * 12);
    const balance = new ExactDecimal(principal);
    const factor = monthlyRate.plus(1).pow(totalPayments);
    const monthlyPayment = monthlyRate.isZero()
      ? balance.div(totalPayments)
      : balance.times(monthlyRate).times(factor).div(factor.minus(1));
    const schedule: LoanAmortizationEntry[] = [];
    let remainingBalance = balance;
    let cumulativeInterest = new ExactDecimal(0);
    for (let i = 1; i <= totalPayments; i++) {
      const interestPortion = remainingBalance.times(monthlyRate);
      const principalPortion =
        i === totalPayments
          ? remainingBalance
          : ExactDecimal.min(remainingBalance, monthlyPayment.minus(interestPortion));
      remainingBalance = ExactDecimal.max(0, remainingBalance.minus(principalPortion));
      cumulativeInterest = cumulativeInterest.plus(interestPortion);
      schedule.push({
        paymentNumber: i,
        ...calculatedAmounts({
          paymentAmount: principalPortion.plus(interestPortion),
          principalPortion,
          interestPortion,
          remainingBalance,
          cumulativeInterest,
        }),
      });
    }

    setState(prev => ({
      ...prev,
      error: null,
      result: {
        ...calculatedAmounts({
          monthlyPayment,
          totalInterest: cumulativeInterest,
          totalPayment: balance.plus(cumulativeInterest),
        }),
        amortizationSchedule: schedule,
      },
    }));
  }, [state.input]);

  return {
    input: state.input,
    result: state.result,
    error: state.error,
    updateInput,
    resetInputs,
    calculate,
  };
}
