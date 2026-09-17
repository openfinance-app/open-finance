import { describe, it, expect, beforeEach } from 'vitest';
import { renderHook, act } from '@testing-library/react';
import { useLoanCalculator } from '@/hooks/useLoanCalculator';
import { DEFAULT_LOAN_CALCULATOR_INPUT } from '@/types/calculator';

// ---------------------------------------------------------------------------
// Helpers
// ---------------------------------------------------------------------------

/** Tolerance for floating-point comparisons (±0.01). */
function approxEqual(actual: number, expected: number, tolerance = 0.01): boolean {
  return Math.abs(actual - expected) <= tolerance;
}

// ---------------------------------------------------------------------------
// Tests
// ---------------------------------------------------------------------------

describe('useLoanCalculator', () => {
  // -------------------------------------------------------------------------
  // Initial state
  // -------------------------------------------------------------------------
  describe('initial state', () => {
    it('starts with default input values', () => {
      const { result } = renderHook(() => useLoanCalculator());

      expect(result.current.input).toEqual(DEFAULT_LOAN_CALCULATOR_INPUT);
    });

    it('starts with null result', () => {
      const { result } = renderHook(() => useLoanCalculator());

      expect(result.current.result).toBeNull();
    });
  });

  // -------------------------------------------------------------------------
  // updateInput
  // -------------------------------------------------------------------------
  describe('updateInput', () => {
    it('updates principal', () => {
      const { result } = renderHook(() => useLoanCalculator());

      act(() => result.current.updateInput('principal', 100000));

      expect(result.current.input.principal).toBe(100000);
    });

    it('updates annualRate', () => {
      const { result } = renderHook(() => useLoanCalculator());

      act(() => result.current.updateInput('annualRate', 3.5));

      expect(result.current.input.annualRate).toBe(3.5);
    });

    it('updates years', () => {
      const { result } = renderHook(() => useLoanCalculator());

      act(() => result.current.updateInput('years', 30));

      expect(result.current.input.years).toBe(30);
    });

    it('does not alter other input fields when one is changed', () => {
      const { result } = renderHook(() => useLoanCalculator());

      act(() => result.current.updateInput('years', 30));

      expect(result.current.input.principal).toBe(DEFAULT_LOAN_CALCULATOR_INPUT.principal);
      expect(result.current.input.annualRate).toBe(DEFAULT_LOAN_CALCULATOR_INPUT.annualRate);
    });
  });

  // -------------------------------------------------------------------------
  // resetInputs
  // -------------------------------------------------------------------------
  describe('resetInputs', () => {
    it('restores default input values after changes', () => {
      const { result } = renderHook(() => useLoanCalculator());

      act(() => {
        result.current.updateInput('principal', 500000);
        result.current.updateInput('annualRate', 9);
        result.current.updateInput('years', 30);
      });

      act(() => result.current.resetInputs());

      expect(result.current.input).toEqual(DEFAULT_LOAN_CALCULATOR_INPUT);
    });

    it('clears the result', () => {
      const { result } = renderHook(() => useLoanCalculator());

      act(() => result.current.calculate());
      expect(result.current.result).not.toBeNull();

      act(() => result.current.resetInputs());
      expect(result.current.result).toBeNull();
    });
  });

  // -------------------------------------------------------------------------
  // calculate — monthly payment formula
  // -------------------------------------------------------------------------
  describe('calculate', () => {
    it('produces a non-null result', () => {
      const { result } = renderHook(() => useLoanCalculator());

      act(() => result.current.calculate());

      expect(result.current.result).not.toBeNull();
    });

    it('calculates the correct monthly payment for a known input', () => {
      // P=200000, r=5%, 20yr → monthly PMT ≈ 1319.91
      const { result } = renderHook(() => useLoanCalculator());

      act(() => result.current.calculate());

      expect(approxEqual(result.current.result!.monthlyPayment, 1319.91)).toBe(true);
    });

    it('calculates the correct total payment', () => {
      // totalPayment = principal + totalInterest
      const { result } = renderHook(() => useLoanCalculator());

      act(() => result.current.calculate());

      const { totalPayment, totalInterest } = result.current.result!;
      expect(
        approxEqual(totalPayment, DEFAULT_LOAN_CALCULATOR_INPUT.principal + totalInterest)
      ).toBe(true);
    });

    it('total interest is positive for a non-zero rate', () => {
      const { result } = renderHook(() => useLoanCalculator());

      act(() => result.current.calculate());

      expect(result.current.result!.totalInterest).toBeGreaterThan(0);
    });

    it('amortization schedule has totalPayments (years × 12) entries', () => {
      const { result } = renderHook(() => useLoanCalculator());

      act(() => result.current.calculate());

      const { years } = DEFAULT_LOAN_CALCULATOR_INPUT;
      expect(result.current.result!.amortizationSchedule).toHaveLength(years * 12);
    });

    it('first schedule entry has paymentNumber = 1', () => {
      const { result } = renderHook(() => useLoanCalculator());

      act(() => result.current.calculate());

      expect(result.current.result!.amortizationSchedule[0].paymentNumber).toBe(1);
    });

    it('last schedule entry has a remaining balance close to 0', () => {
      const { result } = renderHook(() => useLoanCalculator());

      act(() => result.current.calculate());

      const schedule = result.current.result!.amortizationSchedule;
      const lastEntry = schedule[schedule.length - 1];
      expect(Math.abs(lastEntry.remainingBalance)).toBeLessThanOrEqual(0.01);
    });

    it('remaining balance decreases monotonically', () => {
      const { result } = renderHook(() => useLoanCalculator());

      act(() => result.current.calculate());

      const schedule = result.current.result!.amortizationSchedule;
      for (let i = 1; i < schedule.length; i++) {
        expect(schedule[i].remainingBalance).toBeLessThanOrEqual(schedule[i - 1].remainingBalance);
      }
    });

    it('cumulative interest is monotonically increasing', () => {
      const { result } = renderHook(() => useLoanCalculator());

      act(() => result.current.calculate());

      const schedule = result.current.result!.amortizationSchedule;
      for (let i = 1; i < schedule.length; i++) {
        expect(schedule[i].cumulativeInterest).toBeGreaterThanOrEqual(
          schedule[i - 1].cumulativeInterest
        );
      }
    });

    it('higher interest rate produces higher total interest', () => {
      const { result: low } = renderHook(() => useLoanCalculator());
      const { result: high } = renderHook(() => useLoanCalculator());

      act(() => {
        low.current.updateInput('annualRate', 3);
      });
      act(() => {
        low.current.calculate();
      });

      act(() => {
        high.current.updateInput('annualRate', 8);
      });
      act(() => {
        high.current.calculate();
      });

      expect(high.current.result!.totalInterest).toBeGreaterThan(low.current.result!.totalInterest);
    });

    it('longer term produces higher total interest (same rate)', () => {
      const { result: short } = renderHook(() => useLoanCalculator());
      const { result: long } = renderHook(() => useLoanCalculator());

      act(() => {
        short.current.updateInput('years', 10);
      });
      act(() => {
        short.current.calculate();
      });

      act(() => {
        long.current.updateInput('years', 25);
      });
      act(() => {
        long.current.calculate();
      });

      expect(long.current.result!.totalInterest).toBeGreaterThan(
        short.current.result!.totalInterest
      );
    });

    it('zero interest rate: monthly payment equals principal divided by months', () => {
      const { result } = renderHook(() => useLoanCalculator());

      act(() => {
        result.current.updateInput('annualRate', 0);
        result.current.updateInput('principal', 12000);
        result.current.updateInput('years', 1);
      });
      act(() => {
        result.current.calculate();
      });

      // 12000 / 12 months = 1000
      expect(approxEqual(result.current.result!.monthlyPayment, 1000)).toBe(true);
    });

    it('zero interest rate: total interest is zero', () => {
      const { result } = renderHook(() => useLoanCalculator());

      act(() => {
        result.current.updateInput('annualRate', 0);
      });
      act(() => {
        result.current.calculate();
      });

      expect(result.current.result!.totalInterest).toBeLessThanOrEqual(0.01);
    });
  });
});
