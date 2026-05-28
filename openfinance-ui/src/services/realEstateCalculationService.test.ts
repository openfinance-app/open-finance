import { describe, it, expect } from 'vitest';
import { RealEstateCalculationService } from './realEstateCalculationService';
import type { BuyRentInputs, BuyRentResults } from '@/types/realEstateTools';

const makeInputs = (overrides?: Partial<BuyRentInputs>): BuyRentInputs => ({
    purchase: {
        propertyPrice: 200000,
        renovationAmount: 0,
        isNewProperty: false,
        notaryFeesPercent: 7,
        agencyFees: 10000,
        downPayment: 50000,
        loanDuration: 20,
        interestRate: 3.5,
        totalInsurance: 10000,
        applicationFees: 500,
        guaranteeFees: 2000,
        accountFees: 200,
        propertyTax: 1200,
        coOwnershipCharges: 2400,
        maintenancePercent: 1,
        homeInsurance: 300,
        bankFees: 0,
        garbageTax: 200,
        ...overrides?.purchase,
    },
    rental: {
        monthlyRent: 800,
        monthlyCharges: 100,
        securityDeposit: 800,
        rentalInsurance: 150,
        garbageTax: 100,
        initialSavings: 50000,
        monthlySavings: 200,
        ...overrides?.rental,
    },
    resale: {
        targetYear: 10,
        desiredProfit: 50000,
        resaleFeesPercent: 8,
        ...overrides?.resale,
    },
    market: {
        priceEvolution: 2,
        rentEvolution: 2,
        investmentReturn: 4,
        inflation: 2,
        ...overrides?.market,
    },
    ...overrides,
});

describe('RealEstateCalculationService', () => {
    describe('isValidResaleYear', () => {
        it('returns true for valid year within loan duration', () => {
            const inputs = makeInputs();
            expect(RealEstateCalculationService.isValidResaleYear(inputs)).toBe(true);
        });

        it('returns false for year 0', () => {
            const inputs = makeInputs({ resale: { targetYear: 0, desiredProfit: 50000, resaleFeesPercent: 8 } });
            expect(RealEstateCalculationService.isValidResaleYear(inputs)).toBe(false);
        });

        it('returns false for year beyond loan duration', () => {
            const inputs = makeInputs({ resale: { targetYear: 25, desiredProfit: 50000, resaleFeesPercent: 8 } });
            expect(RealEstateCalculationService.isValidResaleYear(inputs)).toBe(false);
        });
    });

    describe('calculateDerivedValues', () => {
        it('returns total price, borrowed amount, monthly payment', () => {
            const inputs = makeInputs();
            const result = RealEstateCalculationService.calculateDerivedValues(inputs);
            expect(result.totalPrice).toBeGreaterThan(0);
            expect(result.borrowedAmount).toBeGreaterThan(0);
            expect(result.monthlyPayment).toBeGreaterThan(0);
            expect(result.minimumDownPayment).toBe(500 + 2000 + 200); // app + guarantee + account fees
        });

        it('suggests monthly savings when buying is more expensive', () => {
            const inputs = makeInputs();
            const result = RealEstateCalculationService.calculateDerivedValues(inputs);
            expect(result.suggestedMonthlySavings).toBeGreaterThanOrEqual(0);
        });
    });

    describe('calculateBuyRentComparison', () => {
        it('returns yearly results and summary', () => {
            const inputs = makeInputs();
            const result = RealEstateCalculationService.calculateBuyRentComparison(inputs);
            expect(result.years).toHaveLength(20); // loan duration
            expect(result.summary).toBeDefined();
            expect(result.summary.buy).toBeDefined();
            expect(result.summary.rent).toBeDefined();
            expect(result.summary.comparison).toBeDefined();
        });

        it('each year has buy and rent costs', () => {
            const inputs = makeInputs();
            const result = RealEstateCalculationService.calculateBuyRentComparison(inputs);
            const year1 = result.years[0];
            expect(year1.year).toBe(1);
            expect(year1.buy.annualCost).toBeGreaterThan(0);
            expect(year1.rent.annualCost).toBeGreaterThan(0);
        });

        it('cumulative costs generally increase', () => {
            const inputs = makeInputs();
            const result = RealEstateCalculationService.calculateBuyRentComparison(inputs);
            const lastYear = result.years[result.years.length - 1];
            expect(lastYear.buy.cumulativeCost).toBeGreaterThan(result.years[0].buy.cumulativeCost);
            expect(lastYear.rent.cumulativeCost).toBeGreaterThan(result.years[0].rent.cumulativeCost);
        });
    });

    describe('getRecommendation', () => {
        it('returns buy recommendation when buy wins', () => {
            const results = {
                years: [{ year: 1 }],
                summary: {
                    buy: {},
                    rent: {},
                    comparison: { winner: 'buy', netWorthDifference: 10000, netExpenseDifference: 0 },
                },
            } as unknown as BuyRentResults;
            const msg = RealEstateCalculationService.getRecommendation(results, 'EUR');
            expect(msg).toContain('achat');
        });

        it('returns rent recommendation when rent wins', () => {
            const results = {
                years: [{ year: 1 }],
                summary: {
                    buy: {},
                    rent: {},
                    comparison: { winner: 'rent', netWorthDifference: 0, netExpenseDifference: 5000 },
                },
            } as unknown as BuyRentResults;
            const msg = RealEstateCalculationService.getRecommendation(results, 'EUR');
            expect(msg).toContain('location');
        });
    });

    describe('exportToCSV', () => {
        it('returns CSV with headers and rows', () => {
            const results = {
                years: [
                    {
                        year: 1,
                        buy: { annualCost: 15000, cumulativeCost: 65000, propertyValue: 204000, remainingCapital: 170000, minimumResalePrice: 180000 },
                        rent: { annualCost: 11000, cumulativeCost: 11800, savings: 3200 },
                    },
                ],
                summary: {},
            } as unknown as BuyRentResults;
            const csv = RealEstateCalculationService.exportToCSV(results);
            expect(csv).toContain('Année');
            expect(csv).toContain('15000');
            expect(csv.split('\n')).toHaveLength(2); // header + 1 row
        });
    });
});
