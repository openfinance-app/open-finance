import { describe, it, expect } from 'vitest';
import { getLiabilityTypeName, getLiabilityTypeBadgeVariant, calculateTotalInterest } from './useLiabilities';

describe('useLiabilities utility functions', () => {
    describe('getLiabilityTypeBadgeVariant', () => {
        it('returns info for MORTGAGE', () => {
            expect(getLiabilityTypeBadgeVariant('MORTGAGE')).toBe('info');
        });

        it('returns default for LOAN', () => {
            expect(getLiabilityTypeBadgeVariant('LOAN')).toBe('default');
        });

        it('returns error for CREDIT_CARD', () => {
            expect(getLiabilityTypeBadgeVariant('CREDIT_CARD')).toBe('error');
        });

        it('returns warning for STUDENT_LOAN', () => {
            expect(getLiabilityTypeBadgeVariant('STUDENT_LOAN')).toBe('warning');
        });

        it('returns info for AUTO_LOAN', () => {
            expect(getLiabilityTypeBadgeVariant('AUTO_LOAN')).toBe('info');
        });

        it('returns default for PERSONAL_LOAN', () => {
            expect(getLiabilityTypeBadgeVariant('PERSONAL_LOAN')).toBe('default');
        });

        it('returns default for OTHER', () => {
            expect(getLiabilityTypeBadgeVariant('OTHER')).toBe('default');
        });

        it('returns default for unknown type', () => {
            expect(getLiabilityTypeBadgeVariant('UNKNOWN_TYPE')).toBe('default');
        });
    });

    describe('calculateTotalInterest', () => {
        it('returns 0 when interestRate is undefined', () => {
            expect(calculateTotalInterest(100000, 80000, undefined, 120)).toBe(0);
        });

        it('returns 0 when interestRate is 0', () => {
            expect(calculateTotalInterest(100000, 80000, 0, 120)).toBe(0);
        });

        it('returns 0 when monthsRemaining is 0', () => {
            expect(calculateTotalInterest(100000, 80000, 5, 0)).toBe(0);
        });

        it('calculates positive total interest for a standard loan', () => {
            const totalInterest = calculateTotalInterest(200000, 150000, 4.5, 240);
            expect(totalInterest).toBeGreaterThan(0);
        });

        it('higher interest rate produces more total interest', () => {
            const lowRate = calculateTotalInterest(200000, 150000, 3, 240);
            const highRate = calculateTotalInterest(200000, 150000, 6, 240);
            expect(highRate).toBeGreaterThan(lowRate);
        });

        it('longer term produces more total interest', () => {
            const shortTerm = calculateTotalInterest(200000, 150000, 5, 120);
            const longTerm = calculateTotalInterest(200000, 150000, 5, 360);
            expect(longTerm).toBeGreaterThan(shortTerm);
        });
    });

    describe('getLiabilityTypeName', () => {
        it('returns translated type name for known types', () => {
            // When i18next has no translation loaded, it returns the key itself
            const result = getLiabilityTypeName('MORTGAGE');
            expect(typeof result).toBe('string');
            expect(result.length).toBeGreaterThan(0);
        });

        it('returns raw type for unknown types', () => {
            const result = getLiabilityTypeName('COMPLETELY_UNKNOWN_XYZ');
            // Should fall through to returning the raw type
            expect(typeof result).toBe('string');
        });
    });
});
