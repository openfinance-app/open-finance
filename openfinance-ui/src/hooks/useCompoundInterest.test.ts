import { describe, it, expect, vi, beforeEach } from 'vitest';
import { renderHook, act } from '@testing-library/react';
import { useCompoundInterest } from './useCompoundInterest';

// Mock the API call
vi.mock('../services/compoundInterestApi', () => ({
    calculateCompoundInterest: vi.fn(),
}));

import { calculateCompoundInterest } from '../services/compoundInterestApi';
const mockCalculate = vi.mocked(calculateCompoundInterest);

describe('useCompoundInterest', () => {
    beforeEach(() => {
        vi.clearAllMocks();
    });

    it('initialises with default input and no result', () => {
        const { result } = renderHook(() => useCompoundInterest());

        expect(result.current.input.principal).toBe(10000);
        expect(result.current.input.annualRate).toBe(7);
        expect(result.current.input.compoundingFrequency).toBe(12);
        expect(result.current.input.years).toBe(10);
        expect(result.current.result).toBeNull();
        expect(result.current.isLoading).toBe(false);
        expect(result.current.error).toBeNull();
    });

    it('updates individual input fields and clears error', () => {
        const { result } = renderHook(() => useCompoundInterest());

        act(() => {
            result.current.updateInput('principal', 50000);
        });

        expect(result.current.input.principal).toBe(50000);
        expect(result.current.input.annualRate).toBe(7); // unchanged
    });

    it('resets inputs to defaults', () => {
        const { result } = renderHook(() => useCompoundInterest());

        act(() => {
            result.current.updateInput('principal', 99999);
            result.current.updateInput('years', 20);
        });

        act(() => {
            result.current.resetInputs();
        });

        expect(result.current.input.principal).toBe(10000);
        expect(result.current.input.years).toBe(10);
        expect(result.current.result).toBeNull();
        expect(result.current.error).toBeNull();
    });

    it('calculate sets loading state and stores result on success', async () => {
        const mockResult = {
            finalBalance: 19671.51,
            principal: 10000,
            totalContributions: 0,
            totalInterest: 9671.51,
            totalInvested: 10000,
            effectiveAnnualRate: 7.23,
            yearlyBreakdown: [],
        };
        mockCalculate.mockResolvedValue(mockResult);

        const { result } = renderHook(() => useCompoundInterest());

        await act(async () => {
            await result.current.calculate();
        });

        expect(mockCalculate).toHaveBeenCalledTimes(1);
        expect(result.current.result).toEqual(mockResult);
        expect(result.current.isLoading).toBe(false);
        expect(result.current.error).toBeNull();
    });

    it('calculate stores error message on failure', async () => {
        mockCalculate.mockRejectedValue(new Error('Network error'));

        const { result } = renderHook(() => useCompoundInterest());

        await act(async () => {
            await result.current.calculate();
        });

        expect(result.current.error).toBe('Network error');
        expect(result.current.result).toBeNull();
        expect(result.current.isLoading).toBe(false);
    });

    it('calculate stores generic message for non-Error exceptions', async () => {
        mockCalculate.mockRejectedValue('string error');

        const { result } = renderHook(() => useCompoundInterest());

        await act(async () => {
            await result.current.calculate();
        });

        expect(result.current.error).toBe(
            'Calculation failed. Please check your inputs.'
        );
    });

    it('updateInput clears previous error', async () => {
        mockCalculate.mockRejectedValue(new Error('fail'));

        const { result } = renderHook(() => useCompoundInterest());

        await act(async () => {
            await result.current.calculate();
        });

        expect(result.current.error).toBe('fail');

        act(() => {
            result.current.updateInput('principal', 5000);
        });

        expect(result.current.error).toBeNull();
    });
});
