import { describe, it, expect, vi, beforeEach } from 'vitest';
import { calculateTimeline, calculateLongevity, getCalculationDefaults } from './calculatorApi';
import apiClient from './apiClient';

vi.mock('./apiClient', () => ({
    default: {
        post: vi.fn(),
        get: vi.fn(),
    },
}));

describe('calculatorApi', () => {
    beforeEach(() => {
        vi.clearAllMocks();
    });

    describe('calculateTimeline', () => {
        it('calls the correct endpoint with mapped parameters', async () => {
            const mockResult = {
                yearsToFreedom: 15,
                monthsToFreedom: 180,
                targetSavingsAmount: 750000,
                progressPercentage: 6.67,
                currentProgress: 50000,
                annualPassiveIncome: 30000,
                isSustainableIndefinitely: false,
                isAchievable: true,
                yearlyProjections: [],
                sensitivityScenarios: [],
                message: 'On track',
            };

            vi.mocked(apiClient.post).mockResolvedValue({ data: mockResult });

            const input = {
                currentSavings: 50000,
                monthlyExpenses: 2500,
                expectedAnnualReturn: 7,
                monthlyContribution: 500,
                withdrawalRate: 4,
                inflationRate: 2.5,
                adjustForInflation: true,
            };

            const result = await calculateTimeline(input);

            expect(apiClient.post).toHaveBeenCalledWith(
                '/calculator/financial-freedom/timeline',
                {
                    currentSavings: 50000,
                    monthlyExpenses: 2500,
                    expectedAnnualReturn: 7,
                    monthlyContribution: 500,
                    withdrawalRate: 4,
                    inflationRate: 2.5,
                    adjustForInflation: true,
                }
            );
            expect(result).toEqual(mockResult);
        });

        it('uses defaults for optional parameters', async () => {
            vi.mocked(apiClient.post).mockResolvedValue({ data: {} });

            const input = {
                currentSavings: 10000,
                monthlyExpenses: 2000,
                expectedAnnualReturn: 5,
            };

            await calculateTimeline(input);

            expect(apiClient.post).toHaveBeenCalledWith(
                '/calculator/financial-freedom/timeline',
                expect.objectContaining({
                    monthlyContribution: 0,
                    withdrawalRate: 4,
                    inflationRate: 2.5,
                    adjustForInflation: false,
                })
            );
        });
    });

    describe('calculateLongevity', () => {
        it('calls correct endpoint with query params', async () => {
            const mockResult = {
                yearsUntilDepletion: 25,
                totalMonthsUntilDepletion: 300,
                isInfinite: false,
                depletionYear: 2051,
                willDeplete: true,
                message: 'Savings will last 25 years',
            };

            vi.mocked(apiClient.post).mockResolvedValue({ data: mockResult });

            const result = await calculateLongevity(100000, 3000, 5);

            expect(apiClient.post).toHaveBeenCalledWith(
                '/calculator/financial-freedom/longevity',
                null,
                {
                    params: {
                        currentSavings: 100000,
                        monthlyExpenses: 3000,
                        annualReturnRate: 5,
                    },
                }
            );
            expect(result).toEqual(mockResult);
        });
    });

    describe('getCalculationDefaults', () => {
        it('calls GET endpoint and returns defaults', async () => {
            const mockDefaults = {
                defaultWithdrawalRate: 4,
                defaultInflationRate: 2.5,
                minimumWithdrawalRate: 0.5,
                maximumWithdrawalRate: 10,
                minimumReturnRate: -10,
                maximumReturnRate: 30,
                defaultReturnRate: 7,
                maxProjectionYears: 50,
                defaultMonthlyContribution: 500,
            };

            vi.mocked(apiClient.get).mockResolvedValue({ data: mockDefaults });

            const result = await getCalculationDefaults();

            expect(apiClient.get).toHaveBeenCalledWith('/calculator/financial-freedom/defaults');
            expect(result).toEqual(mockDefaults);
        });
    });
});
