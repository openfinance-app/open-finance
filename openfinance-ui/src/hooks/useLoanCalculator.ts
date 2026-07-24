import { useState, useCallback } from 'react';
import {
    DEFAULT_LOAN_CALCULATOR_INPUT,
    type LoanCalculatorInput,
    type LoanCalculatorResult,
    type LoanAmortizationEntry,
} from '../types/calculator';
import { add, divide, multiply, pow, subtract } from '@/utils/money';

interface LoanCalculatorState {
    input: LoanCalculatorInput;
    result: LoanCalculatorResult | null;
}

export function useLoanCalculator() {
    const [state, setState] = useState<LoanCalculatorState>({
        input: DEFAULT_LOAN_CALCULATOR_INPUT,
        result: null,
    });

    const updateInput = useCallback(<K extends keyof LoanCalculatorInput>(
        key: K,
        value: LoanCalculatorInput[K]
    ) => {
        setState((prev) => ({
            ...prev,
            input: { ...prev.input, [key]: value },
        }));
    }, []);

    const resetInputs = useCallback(() => {
        setState({
            input: DEFAULT_LOAN_CALCULATOR_INPUT,
            result: null,
        });
    }, []);

    const calculate = useCallback(() => {
        const { principal, annualRate, years } = state.input;

        const monthlyRate = divide(divide(annualRate, 100), 12);
        const totalPayments = years * 12;

        let monthlyPayment = 0;

        if (monthlyRate === 0) {
            monthlyPayment = divide(principal, totalPayments);
        } else {
            const factor = pow(add(1, monthlyRate), totalPayments);
            monthlyPayment = divide(multiply(multiply(principal, monthlyRate), factor), subtract(factor, 1));
        }

        const schedule: LoanAmortizationEntry[] = [];
        let remainingBalance = principal;
        let cumulativeInterest = 0;
        let totalInterest = 0;

        for (let i = 1; i <= totalPayments; i++) {
            const interestPortion = multiply(remainingBalance, monthlyRate);
            let principalPortion = subtract(monthlyPayment, interestPortion);

            if (i === totalPayments) {
                principalPortion = remainingBalance;
                monthlyPayment = add(principalPortion, interestPortion);
            }

            remainingBalance = subtract(remainingBalance, principalPortion);
            if (remainingBalance < 0) remainingBalance = 0;

            cumulativeInterest = add(cumulativeInterest, interestPortion);

            schedule.push({
                paymentNumber: i,
                paymentAmount: monthlyPayment,
                principalPortion,
                interestPortion,
                remainingBalance,
                cumulativeInterest
            });
        }

        totalInterest = cumulativeInterest;
        const totalPayment = add(principal, totalInterest);

        setState(prev => ({
            ...prev,
            result: {
                monthlyPayment,
                totalInterest,
                totalPayment,
                amortizationSchedule: schedule
            }
        }));

    }, [state.input]);

    return {
        input: state.input,
        result: state.result,
        updateInput,
        resetInputs,
        calculate,
    };
}
