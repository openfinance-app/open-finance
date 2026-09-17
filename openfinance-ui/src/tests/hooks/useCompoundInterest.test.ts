import { describe, it, expect, vi, beforeEach } from 'vitest';
import { renderHook, act } from '@testing-library/react';
import { useCompoundInterest } from '@/hooks/useCompoundInterest';
import { DEFAULT_COMPOUND_INTEREST_INPUT } from '@/types/calculator';
import type { CompoundInterestResult } from '@/types/calculator';

// ---------------------------------------------------------------------------
// Mock the API module so tests don't make real HTTP calls
// ---------------------------------------------------------------------------
vi.mock('@/services/compoundInterestApi', () => ({
  calculateCompoundInterest: vi.fn(),
}));

import { calculateCompoundInterest } from '@/services/compoundInterestApi';
const mockCalculate = vi.mocked(calculateCompoundInterest);

// ---------------------------------------------------------------------------
// Fixture
// ---------------------------------------------------------------------------
const MOCK_RESULT: CompoundInterestResult = {
  finalBalance: 20000,
  principal: 10000,
  totalContributions: 2400,
  totalInterest: 7600,
  totalInvested: 12400,
  effectiveAnnualRate: 7.229,
  yearlyBreakdown: [
    {
      year: 1,
      startingBalance: 10000,
      contributions: 2400,
      interestEarned: 891.49,
      endingBalance: 13291.49,
      cumulativeInterest: 891.49,
      cumulativePrincipal: 12400,
    },
  ],
};

describe('useCompoundInterest', () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  // -------------------------------------------------------------------------
  // Initial state
  // -------------------------------------------------------------------------
  describe('initial state', () => {
    it('starts with default input values', () => {
      const { result } = renderHook(() => useCompoundInterest());

      expect(result.current.input).toEqual(DEFAULT_COMPOUND_INTEREST_INPUT);
    });

    it('starts with null result', () => {
      const { result } = renderHook(() => useCompoundInterest());

      expect(result.current.result).toBeNull();
    });

    it('starts not loading', () => {
      const { result } = renderHook(() => useCompoundInterest());

      expect(result.current.isLoading).toBe(false);
    });

    it('starts with no error', () => {
      const { result } = renderHook(() => useCompoundInterest());

      expect(result.current.error).toBeNull();
    });
  });

  // -------------------------------------------------------------------------
  // updateInput
  // -------------------------------------------------------------------------
  describe('updateInput', () => {
    it('updates a numeric field', () => {
      const { result } = renderHook(() => useCompoundInterest());

      act(() => result.current.updateInput('principal', 50000));

      expect(result.current.input.principal).toBe(50000);
    });

    it('updates annualRate', () => {
      const { result } = renderHook(() => useCompoundInterest());

      act(() => result.current.updateInput('annualRate', 9.5));

      expect(result.current.input.annualRate).toBe(9.5);
    });

    it('updates years', () => {
      const { result } = renderHook(() => useCompoundInterest());

      act(() => result.current.updateInput('years', 25));

      expect(result.current.input.years).toBe(25);
    });

    it('updates compoundingFrequency', () => {
      const { result } = renderHook(() => useCompoundInterest());

      act(() => result.current.updateInput('compoundingFrequency', 4));

      expect(result.current.input.compoundingFrequency).toBe(4);
    });

    it('updates contributionAtBeginning', () => {
      const { result } = renderHook(() => useCompoundInterest());

      act(() => result.current.updateInput('contributionAtBeginning', true));

      expect(result.current.input.contributionAtBeginning).toBe(true);
    });

    it('clears a previous error when a field is updated', async () => {
      mockCalculate.mockRejectedValueOnce(new Error('Server error'));
      const { result } = renderHook(() => useCompoundInterest());

      // Trigger an error first
      await act(async () => {
        await result.current.calculate();
      });
      expect(result.current.error).toBeTruthy();

      // Then update an input
      act(() => result.current.updateInput('principal', 20000));
      expect(result.current.error).toBeNull();
    });

    it('does not alter other input fields', () => {
      const { result } = renderHook(() => useCompoundInterest());

      act(() => result.current.updateInput('years', 30));

      expect(result.current.input.principal).toBe(DEFAULT_COMPOUND_INTEREST_INPUT.principal);
      expect(result.current.input.annualRate).toBe(DEFAULT_COMPOUND_INTEREST_INPUT.annualRate);
    });
  });

  // -------------------------------------------------------------------------
  // resetInputs
  // -------------------------------------------------------------------------
  describe('resetInputs', () => {
    it('restores default input values after changes', () => {
      const { result } = renderHook(() => useCompoundInterest());

      act(() => {
        result.current.updateInput('principal', 99999);
        result.current.updateInput('annualRate', 15);
        result.current.updateInput('years', 50);
      });

      act(() => result.current.resetInputs());

      expect(result.current.input).toEqual(DEFAULT_COMPOUND_INTEREST_INPUT);
    });

    it('clears the result', async () => {
      mockCalculate.mockResolvedValueOnce(MOCK_RESULT);
      const { result } = renderHook(() => useCompoundInterest());

      await act(async () => {
        await result.current.calculate();
      });
      expect(result.current.result).not.toBeNull();

      act(() => result.current.resetInputs());
      expect(result.current.result).toBeNull();
    });

    it('clears any error', async () => {
      mockCalculate.mockRejectedValueOnce(new Error('Oops'));
      const { result } = renderHook(() => useCompoundInterest());

      await act(async () => {
        await result.current.calculate();
      });
      expect(result.current.error).toBeTruthy();

      act(() => result.current.resetInputs());
      expect(result.current.error).toBeNull();
    });
  });

  // -------------------------------------------------------------------------
  // calculate — success
  // -------------------------------------------------------------------------
  describe('calculate — success', () => {
    it('calls calculateCompoundInterest with current input', async () => {
      mockCalculate.mockResolvedValueOnce(MOCK_RESULT);
      const { result } = renderHook(() => useCompoundInterest());

      await act(async () => {
        await result.current.calculate();
      });

      expect(mockCalculate).toHaveBeenCalledOnce();
      expect(mockCalculate).toHaveBeenCalledWith(DEFAULT_COMPOUND_INTEREST_INPUT);
    });

    it('sets result on success', async () => {
      mockCalculate.mockResolvedValueOnce(MOCK_RESULT);
      const { result } = renderHook(() => useCompoundInterest());

      await act(async () => {
        await result.current.calculate();
      });

      expect(result.current.result).toEqual(MOCK_RESULT);
    });

    it('clears loading state after success', async () => {
      mockCalculate.mockResolvedValueOnce(MOCK_RESULT);
      const { result } = renderHook(() => useCompoundInterest());

      await act(async () => {
        await result.current.calculate();
      });

      expect(result.current.isLoading).toBe(false);
    });

    it('clears any previous error on success', async () => {
      // First call fails
      mockCalculate.mockRejectedValueOnce(new Error('First error'));
      const { result } = renderHook(() => useCompoundInterest());
      await act(async () => {
        await result.current.calculate();
      });
      expect(result.current.error).toBeTruthy();

      // Second call succeeds
      mockCalculate.mockResolvedValueOnce(MOCK_RESULT);
      await act(async () => {
        await result.current.calculate();
      });
      expect(result.current.error).toBeNull();
    });
  });

  // -------------------------------------------------------------------------
  // calculate — failure
  // -------------------------------------------------------------------------
  describe('calculate — failure', () => {
    it('sets error message from Error instance', async () => {
      mockCalculate.mockRejectedValueOnce(new Error('Network timeout'));
      const { result } = renderHook(() => useCompoundInterest());

      await act(async () => {
        await result.current.calculate();
      });

      expect(result.current.error).toBe('Network timeout');
    });

    it('sets fallback error message for non-Error rejections', async () => {
      mockCalculate.mockRejectedValueOnce('string error');
      const { result } = renderHook(() => useCompoundInterest());

      await act(async () => {
        await result.current.calculate();
      });

      expect(result.current.error).toBe('Calculation failed. Please check your inputs.');
    });

    it('clears loading state after failure', async () => {
      mockCalculate.mockRejectedValueOnce(new Error('fail'));
      const { result } = renderHook(() => useCompoundInterest());

      await act(async () => {
        await result.current.calculate();
      });

      expect(result.current.isLoading).toBe(false);
    });

    it('leaves result null when calculation fails', async () => {
      mockCalculate.mockRejectedValueOnce(new Error('fail'));
      const { result } = renderHook(() => useCompoundInterest());

      await act(async () => {
        await result.current.calculate();
      });

      expect(result.current.result).toBeNull();
    });
  });
});
