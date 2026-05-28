import { describe, it, expect, vi, beforeEach } from 'vitest';
import { renderHook } from '@testing-library/react';

vi.mock('@/context/CurrencyDisplayContext', () => ({
    useCurrencyDisplay: () => ({
        secondaryCurrency: 'EUR',
    }),
}));
vi.mock('@/hooks/useCurrency', () => ({
    useLatestExchangeRate: (from: string, to: string) => ({
        data: from && to && from !== to ? { rate: 0.85 } : null,
    }),
}));

import { useSecondaryConversion } from './useSecondaryConversion';

describe('useSecondaryConversion', () => {
    beforeEach(() => {
        vi.clearAllMocks();
    });

    it('returns secondary currency when from differs', () => {
        const { result } = renderHook(() => useSecondaryConversion('USD'));
        expect(result.current.secondaryCurrency).toBe('EUR');
    });

    it('returns exchange rate', () => {
        const { result } = renderHook(() => useSecondaryConversion('USD'));
        expect(result.current.secondaryExchangeRate).toBe(0.85);
    });

    it('converts amount using rate', () => {
        const { result } = renderHook(() => useSecondaryConversion('USD'));
        expect(result.current.convert(100)).toBeCloseTo(85);
    });

    it('returns null when from is null', () => {
        const { result } = renderHook(() => useSecondaryConversion(null));
        expect(result.current.secondaryCurrency).toBeNull();
        expect(result.current.convert(100)).toBeNull();
    });

    it('returns null when from equals secondary', () => {
        const { result } = renderHook(() => useSecondaryConversion('EUR'));
        expect(result.current.secondaryCurrency).toBeNull();
        expect(result.current.secondaryExchangeRate).toBeNull();
    });

    it('convert returns null for null amount', () => {
        const { result } = renderHook(() => useSecondaryConversion('USD'));
        expect(result.current.convert(null)).toBeNull();
    });
});
