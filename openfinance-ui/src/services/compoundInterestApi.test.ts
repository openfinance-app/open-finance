import { describe, it, expect, vi, beforeEach } from 'vitest';
import { calculateCompoundInterest } from './compoundInterestApi';
import apiClient from './apiClient';

vi.mock('./apiClient', () => ({
    default: {
        post: vi.fn(),
        get: vi.fn(),
    },
}));

describe('compoundInterestApi', () => {
    beforeEach(() => {
        vi.clearAllMocks();
    });

    it('calls the correct endpoint with mapped parameters', async () => {
        const mockResult = {
            finalBalance: 19671.51,
            principal: 10000,
            totalContributions: 0,
            totalInterest: 9671.51,
            totalInvested: 10000,
            effectiveAnnualRate: 7.23,
            yearlyBreakdown: [],
        };

        vi.mocked(apiClient.post).mockResolvedValue({ data: mockResult });

        const input = {
            principal: 10000,
            annualRate: 7,
            compoundingFrequency: 12 as const,
            years: 10,
            regularContribution: 100,
            contributionAtBeginning: true,
        };

        const result = await calculateCompoundInterest(input);

        expect(apiClient.post).toHaveBeenCalledWith(
            '/calculator/compound-interest/calculate',
            {
                principal: 10000,
                annualRate: 7,
                compoundingFrequency: 12,
                years: 10,
                regularContribution: 100,
                contributionAtBeginning: true,
            }
        );
        expect(result).toEqual(mockResult);
    });

    it('uses default values for optional parameters', async () => {
        vi.mocked(apiClient.post).mockResolvedValue({
            data: { finalBalance: 10000 },
        });

        const input = {
            principal: 5000,
            annualRate: 5,
            compoundingFrequency: 4 as const,
            years: 5,
            regularContribution: 0,
            contributionAtBeginning: false,
        };

        await calculateCompoundInterest(input);

        expect(apiClient.post).toHaveBeenCalledWith(
            '/calculator/compound-interest/calculate',
            expect.objectContaining({
                regularContribution: 0,
                contributionAtBeginning: false,
            })
        );
    });

    it('propagates API errors', async () => {
        vi.mocked(apiClient.post).mockRejectedValue(new Error('Network failure'));

        await expect(
            calculateCompoundInterest({
                principal: 1000,
                annualRate: 5,
                compoundingFrequency: 12,
                years: 1,
                regularContribution: 0,
                contributionAtBeginning: false,
            })
        ).rejects.toThrow('Network failure');
    });
});
