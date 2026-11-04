import {
  DEFAULT_INVESTMENT_INPUTS,
  DEFAULT_RENTAL_TAX_CONTEXT,
  type InvestmentInputs,
} from '@/types/realEstateTools';
export const createInvestmentInputs = (): InvestmentInputs => ({
  ...structuredClone(DEFAULT_INVESTMENT_INPUTS),
  currency: 'EUR',
  property: { totalPrice: 250000, furnishingType: 'standard', furnitureValue: 10000 },
  credit: {
    monthlyPayment: 1000,
    annualCost: 12000,
    totalCost: 240000,
    assurance: 500,
    bankFees: 100,
  },
  tax: { ...DEFAULT_RENTAL_TAX_CONTEXT, otherHouseholdIncome: 50000 },
});
