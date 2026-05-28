import { describe, it, expect, beforeEach } from 'vitest';
import { renderHook, act } from '@testing-library/react';
import { useLoanCalculator } from './useLoanCalculator';

describe('useLoanCalculator', () => {
    it('initialises with default input and no result', () => {
        const { result } = renderHook(() => useLoanCalculator());

        expect(result.current.input.principal).toBe(200000);
        expect(result.current.input.annualRate).toBe(5.0);
        expect(result.current.input.years).toBe(20);
        expect(result.current.result).toBeNull();
    });

    it('updates individual input fields', () => {
        const { result } = renderHook(() => useLoanCalculator());

        act(() => {
            result.current.updateInput('principal', 100000);
        });

        expect(result.current.input.principal).toBe(100000);
        expect(result.current.input.annualRate).toBe(5.0); // unchanged
    });

    it('resets inputs to defaults and clears result', () => {
        const { result } = renderHook(() => useLoanCalculator());

        act(() => {
            result.current.updateInput('principal', 50000);
            result.current.calculate();
        });

        expect(result.current.result).not.toBeNull();

        act(() => {
            result.current.resetInputs();
        });

        expect(result.current.input.principal).toBe(200000);
        expect(result.current.result).toBeNull();
    });

    it('calculates correct monthly payment for a standard loan', () => {
        const { result } = renderHook(() => useLoanCalculator());

        act(() => {
            result.current.updateInput('principal', 100000);
            result.current.updateInput('annualRate', 6);
            result.current.updateInput('years', 30);
        });

        act(() => {
            result.current.calculate();
        });

        const res = result.current.result!;
        // Standard 100k loan at 6% over 30 years ≈ $599.55/mo
        expect(res.monthlyPayment).toBeCloseTo(599.55, 0);
        expect(res.totalPayment).toBeGreaterThan(100000);
        expect(res.totalInterest).toBeGreaterThan(0);
        expect(res.totalPayment).toBeCloseTo(res.totalInterest + 100000, 0);
    });

    it('handles zero interest rate (interest-free loan)', () => {
        const { result } = renderHook(() => useLoanCalculator());

        act(() => {
            result.current.updateInput('principal', 12000);
            result.current.updateInput('annualRate', 0);
            result.current.updateInput('years', 1);
        });

        act(() => {
            result.current.calculate();
        });

        const res = result.current.result!;
        expect(res.monthlyPayment).toBeCloseTo(1000, 2);
        expect(res.totalInterest).toBe(0);
        expect(res.totalPayment).toBeCloseTo(12000, 2);
    });

    it('generates correct amortization schedule length', () => {
        const { result } = renderHook(() => useLoanCalculator());

        act(() => {
            result.current.updateInput('years', 5);
        });

        act(() => {
            result.current.calculate();
        });

        expect(result.current.result!.amortizationSchedule).toHaveLength(60);
    });

    it('amortization schedule ends with zero remaining balance', () => {
        const { result } = renderHook(() => useLoanCalculator());

        act(() => {
            result.current.calculate();
        });

        const schedule = result.current.result!.amortizationSchedule;
        const lastEntry = schedule[schedule.length - 1];
        expect(lastEntry.remainingBalance).toBeCloseTo(0, 2);
    });

    it('amortization cumulative interest matches totalInterest', () => {
        const { result } = renderHook(() => useLoanCalculator());

        act(() => {
            result.current.calculate();
        });

        const schedule = result.current.result!.amortizationSchedule;
        const lastEntry = schedule[schedule.length - 1];
        expect(lastEntry.cumulativeInterest).toBeCloseTo(
            result.current.result!.totalInterest,
            2
        );
    });

    it('principal portion increases over time (standard amortization)', () => {
        const { result } = renderHook(() => useLoanCalculator());

        act(() => {
            result.current.updateInput('annualRate', 5);
        });

        act(() => {
            result.current.calculate();
        });

        const schedule = result.current.result!.amortizationSchedule;
        // First payment's principal portion should be less than last payment's
        expect(schedule[0].principalPortion).toBeLessThan(
            schedule[schedule.length - 2].principalPortion
        );
    });
});
