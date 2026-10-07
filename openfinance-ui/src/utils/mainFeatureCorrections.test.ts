import { describe, expect, it } from 'vitest';
import { calculateSavingsLongevity } from '@/utils/financialCalculations';
import {
  calculateMicroFoncier,
  calculateMicroBIC,
  calculateLMNPReel,
} from '@/utils/taxRegimeCalculations';
import {
  DEFAULT_BUY_RENT_INPUTS,
  DEFAULT_INVESTMENT_INPUTS,
  DEFAULT_RENTAL_TAX_CONTEXT,
  type InvestmentInputs,
} from '@/types/realEstateTools';
import { RealEstateCalculationService } from '@/services/realEstateCalculationService';

function rental(): InvestmentInputs {
  return {
    ...structuredClone(DEFAULT_INVESTMENT_INPUTS),
    credit: { monthlyPayment: 0, annualCost: 0, totalCost: 0, assurance: 0, bankFees: 0 },
    property: { totalPrice: 100000, furnishingType: 'unfurnished', furnitureValue: 0 },
    revenue: { monthlyRent: 1000, recoverableCharges: 100, occupancyRate: 100, badDebtRate: 0 },
    expenses: {
      propertyTax: 0,
      nonRecoverableCharges: 0,
      annualMaintenance: 0,
      cfe: 0,
      cvae: 0,
      managementFees: 0,
      pnoInsurance: 0,
      accountingFees: 0,
      marginalTaxRate: 30,
    },
    tax: { ...DEFAULT_RENTAL_TAX_CONTEXT },
  };
}

describe('independent audit counterexamples', () => {
  it('counts twelve funded payments and handles zero outflow', () => {
    expect(calculateSavingsLongevity(12000, 1000, 0).monthsUntilDepletion).toBe(12);
    expect(calculateSavingsLongevity(12001, 1000, 0).monthsUntilDepletion).toBe(13);
    expect(calculateSavingsLongevity(12000, 0, 0).isInfinite).toBe(true);
    expect(calculateSavingsLongevity(0, 0, 0).isInfinite).toBe(true);
  });

  it('separates acquisition costs from market value', () => {
    const inputs = structuredClone(DEFAULT_BUY_RENT_INPUTS);
    Object.assign(inputs.purchase, {
      propertyPrice: 100000,
      renovationAmount: 10000,
      notaryFeesPercent: 7,
      agencyFees: 2000,
    });
    inputs.market.priceEvolution = 0;
    const result = RealEstateCalculationService.calculateBuyRentComparison(inputs);
    expect(result.years[0].buy.propertyValue).toBe(110000);
  });

  it('excludes reimbursed charges from micro-foncier tax and profit', () => {
    const result = calculateMicroFoncier(rental());
    expect(result.revenue.gross).toBe(12000);
    expect(result.revenue.taxable).toBe(8400);
    expect(result.taxation.totalTaxes).toBeCloseTo(3964.8, 2);
    expect(result.performance.monthlyCashFlow).toBeCloseTo(669.6, 2);
    const atLimit = rental();
    atLimit.revenue.monthlyRent = 1250;
    expect(calculateMicroFoncier(atLimit).eligible).toBe(true);
    atLimit.tax!.otherUnfurnishedRent = 1;
    expect(calculateMicroFoncier(atLimit).eligible).toBe(false);
  });

  it('uses both household conditions and refuses to invent professional contributions', () => {
    const inputs = rental();
    inputs.revenue = {
      monthlyRent: 2100,
      recoverableCharges: 0,
      occupancyRate: 100,
      badDebtRate: 0,
    };
    expect(calculateLMNPReel(inputs).details.calculationStatus).toBe('needsHouseholdIncome');
    expect(calculateLMNPReel(inputs).taxation.totalTaxes).toBeNull();
    inputs.tax!.otherHouseholdIncome = 60000;
    expect(calculateLMNPReel(inputs).taxation.socialContributions).toBeCloseTo(3943.2, 2);
    inputs.tax!.otherHouseholdIncome = 25200;
    expect(calculateLMNPReel(inputs).details.calculationStatus).toBe('complete');
    inputs.tax!.otherHouseholdIncome = 25199;
    const professional = calculateLMNPReel(inputs);
    expect(professional.details.calculationStatus).toBe('professionalOutOfScope');
    expect(professional.eligible).toBe(false);
    expect(professional.performance.monthlyCashFlow).toBeNull();
    expect(calculateMicroBIC(inputs).taxation.socialContributions).toBeNull();
  });

  it('counts other furnished receipts, but requires receipts strictly above 23000', () => {
    const inputs = rental();
    inputs.revenue.recoverableCharges = 0;
    inputs.tax!.otherHouseholdIncome = 0;
    inputs.tax!.otherFurnishedReceipts = 11000;
    expect(calculateLMNPReel(inputs).details.calculationStatus).toBe('complete');
    inputs.tax!.otherFurnishedReceipts = 11001;
    expect(calculateLMNPReel(inputs).details.calculationStatus).toBe('professionalOutOfScope');
  });

  it('distinguishes the 2025 and 2026 long-term micro-BIC thresholds', () => {
    const inputs = rental();
    inputs.property.furnishingType = 'basic';
    inputs.revenue = {
      monthlyRent: 6500,
      recoverableCharges: 0,
      occupancyRate: 100,
      badDebtRate: 0,
    };
    inputs.tax!.otherHouseholdIncome = 100000;
    inputs.tax!.incomeYear = 2025;
    expect(calculateMicroBIC(inputs).eligible).toBe(false);
    inputs.tax!.incomeYear = 2026;
    expect(calculateMicroBIC(inputs).eligible).toBe(true);
    expect(calculateMicroBIC(inputs).taxation.socialContributions).toBe(7254);
  });
  it('shares the micro-BIC minimum allowance across household receipts', () => {
    const inputs = rental();
    inputs.revenue = { monthlyRent: 10, recoverableCharges: 0, occupancyRate: 100, badDebtRate: 0 };
    expect(calculateMicroBIC(inputs).revenue.taxable).toBe(0);
    inputs.tax!.otherFurnishedReceipts = 1080;
    expect(calculateMicroBIC(inputs).revenue.taxable).toBe(60);
    inputs.tax!.otherFurnishedReceipts = 480;
    expect(calculateMicroBIC(inputs).revenue.deduction).toBe(61);
  });
});
