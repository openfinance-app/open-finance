import apiClient from './apiClient';
import type { CompoundInterestInput, CompoundInterestResult } from '../types/calculator';

/**
 * Call the backend compound interest calculation endpoint.
 */
export async function calculateCompoundInterest(
  input: CompoundInterestInput
): Promise<CompoundInterestResult> {
  const response = await apiClient.post('/calculator/compound-interest/calculate', {
    principal: input.principal,
    annualRate: input.annualRate,
    compoundingFrequency: input.compoundingFrequency,
    years: input.years,
    regularContribution: input.regularContribution ?? 0,
    contributionAtBeginning: input.contributionAtBeginning ?? false,
  });
  return response.data;
}
