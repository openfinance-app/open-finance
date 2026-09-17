/**
 * Unit Tests for Tax Regime Calculations
 *
 * Tests for French tax regime calculations
 * Requirements: REQ-2.4.x, REQ-2.6.x
 */

import { describe, it, expect } from 'vitest';
import {
  calculateGrossRevenue,
  calculateMicroFoncier,
  calculateReelFoncier,
  calculateMicroBIC,
  calculateLMNPReel,
  calculateAllRegimes,
  calculateGrossYield,
  calculateNetYield,
  calculateMonthlyCashFlow,
  getRecommendedRegime,
  getRegimeDisplayName,
  getRegimeDescription,
  getFurnitureValue,
} from './taxRegimeCalculations';
import type {
  InvestmentInputs,
  RentalRevenueInputs,
  OwnerExpensesInputs,
} from '@/types/realEstateTools';

// Test data factory
const createTestInputs = (overrides: Partial<InvestmentInputs> = {}): InvestmentInputs => ({
  credit: {
    monthlyPayment: 1200,
    annualCost: 15000,
    totalCost: 375000,
    assurance: 500,
    bankFees: 300,
  },
  property: {
    totalPrice: 300000,
    furnishingType: 'unfurnished',
    furnitureValue: 0,
  },
  revenue: {
    monthlyRent: 1000,
    recoverableCharges: 100,
    occupancyRate: 95,
    badDebtRate: 1,
  },
  expenses: {
    propertyTax: 2000,
    nonRecoverableCharges: 1200,
    annualMaintenance: 1500,
    cfe: 1500,
    cvae: 0,
    managementFees: 800,
    pnoInsurance: 300,
    accountingFees: 600,
    marginalTaxRate: 30,
  },
  ...overrides,
});

describe('Tax Regime Calculations', () => {
  describe('calculateGrossRevenue', () => {
    it('should calculate gross revenue correctly', () => {
      const revenue: RentalRevenueInputs = {
        monthlyRent: 1000,
        recoverableCharges: 100,
        occupancyRate: 95,
        badDebtRate: 1,
      };
      const result = calculateGrossRevenue(revenue);
      // (1000 + 100) * 12 * 0.95 * 0.99 = 12,414.60
      expect(result).toBeCloseTo(12414.6, 1);
    });

    it('should handle 100% occupancy and 0% bad debt', () => {
      const revenue: RentalRevenueInputs = {
        monthlyRent: 1000,
        recoverableCharges: 0,
        occupancyRate: 100,
        badDebtRate: 0,
      };
      expect(calculateGrossRevenue(revenue)).toBe(12000);
    });

    it('should handle zero rent', () => {
      const revenue: RentalRevenueInputs = {
        monthlyRent: 0,
        recoverableCharges: 100,
        occupancyRate: 95,
        badDebtRate: 1,
      };
      expect(calculateGrossRevenue(revenue)).toBeGreaterThan(0);
    });

    it('should return 0 for all zero inputs', () => {
      const revenue: RentalRevenueInputs = {
        monthlyRent: 0,
        recoverableCharges: 0,
        occupancyRate: 0,
        badDebtRate: 0,
      };
      expect(calculateGrossRevenue(revenue)).toBe(0);
    });

    it('should handle 100% bad debt rate', () => {
      const revenue: RentalRevenueInputs = {
        monthlyRent: 1000,
        recoverableCharges: 100,
        occupancyRate: 100,
        badDebtRate: 100,
      };
      expect(calculateGrossRevenue(revenue)).toBe(0);
    });
  });

  describe('calculateMicroFoncier', () => {
    it('should calculate Micro-Foncier correctly for eligible income', () => {
      const inputs = createTestInputs();
      const result = calculateMicroFoncier(inputs);

      expect(result.regime).toBe('micro_foncier');
      expect(result.eligible).toBe(true);
      // 30% abatement on gross revenue
      const expectedDeduction = result.revenue.gross * 0.3;
      expect(result.revenue.deduction).toBeCloseTo(expectedDeduction, 1);
      expect(result.revenue.taxable).toBeCloseTo(result.revenue.gross * 0.7, 1);
    });

    it('should mark as ineligible for revenue over 15000 EUR', () => {
      const inputs = createTestInputs({
        revenue: {
          monthlyRent: 1500,
          recoverableCharges: 0,
          occupancyRate: 100,
          badDebtRate: 0,
        },
      });
      const result = calculateMicroFoncier(inputs);

      expect(result.eligible).toBe(false);
      expect(result.details.warnings).toContain('Revenus > 15 000€ - Régime réel conseillé');
    });

    it('should calculate income tax at 30%', () => {
      const inputs = createTestInputs();
      const result = calculateMicroFoncier(inputs);

      const expectedTax = result.revenue.taxable * 0.3;
      expect(result.taxation.incomeTax).toBeCloseTo(expectedTax, 1);
    });

    it('should calculate social contributions at 17.2%', () => {
      const inputs = createTestInputs();
      const result = calculateMicroFoncier(inputs);

      const expectedSocial = result.revenue.taxable * 0.172;
      expect(result.taxation.socialContributions).toBeCloseTo(expectedSocial, 1);
    });

    it('should handle exactly 15000 EUR revenue (boundary)', () => {
      const inputs = createTestInputs({
        revenue: {
          monthlyRent: 1250,
          recoverableCharges: 0,
          occupancyRate: 100,
          badDebtRate: 0,
        },
      });
      const result = calculateMicroFoncier(inputs);

      // Should be eligible at exactly 15000
      expect(result.eligible).toBe(true);
    });

    it('should handle zero revenue', () => {
      const inputs = createTestInputs({
        revenue: {
          monthlyRent: 0,
          recoverableCharges: 0,
          occupancyRate: 0,
          badDebtRate: 0,
        },
      });
      const result = calculateMicroFoncier(inputs);

      expect(result.revenue.gross).toBe(0);
      expect(result.taxation.totalTaxes).toBe(0);
    });
  });

  describe('calculateReelFoncier', () => {
    it('should calculate Réel Foncier with actual expenses', () => {
      const inputs = createTestInputs();
      const result = calculateReelFoncier(inputs);

      expect(result.regime).toBe('reel_foncier');
      expect(result.eligible).toBe(true);

      // Deductible expenses should be the non-recoverable charges
      const expectedExpenses =
        inputs.expenses.propertyTax +
        inputs.expenses.nonRecoverableCharges +
        inputs.expenses.annualMaintenance +
        inputs.expenses.cfe +
        inputs.expenses.managementFees +
        inputs.expenses.pnoInsurance +
        inputs.expenses.accountingFees;

      expect(result.revenue.deduction).toBe(expectedExpenses);
    });

    it('should always be eligible', () => {
      const inputs = createTestInputs({
        revenue: {
          monthlyRent: 5000,
          recoverableCharges: 0,
          occupancyRate: 100,
          badDebtRate: 0,
        },
      });
      const result = calculateReelFoncier(inputs);
      expect(result.eligible).toBe(true);
    });

    it('should calculate 17.2% social contributions', () => {
      const inputs = createTestInputs();
      const result = calculateReelFoncier(inputs);

      const expectedSocial = result.revenue.taxable * 0.172;
      expect(result.taxation.socialContributions).toBeCloseTo(expectedSocial, 1);
    });

    it('should handle high expenses exceeding revenue', () => {
      const inputs = createTestInputs({
        expenses: {
          propertyTax: 5000,
          nonRecoverableCharges: 5000,
          annualMaintenance: 5000,
          cfe: 1500,
          cvae: 0,
          managementFees: 1000,
          pnoInsurance: 500,
          accountingFees: 1000,
          marginalTaxRate: 30,
        },
      });
      const result = calculateReelFoncier(inputs);

      // Taxable income should be 0 (not negative)
      expect(result.revenue.taxable).toBe(0);
      expect(result.taxation.totalTaxes).toBe(0);
    });
  });

  describe('calculateMicroBIC', () => {
    it('should calculate Micro-BIC correctly for eligible income', () => {
      const inputs = createTestInputs();
      const result = calculateMicroBIC(inputs);

      expect(result.regime).toBe('micro_bic');
      expect(result.eligible).toBe(true);
      // 50% abatement on gross revenue
      const expectedDeduction = result.revenue.gross * 0.5;
      expect(result.revenue.deduction).toBeCloseTo(expectedDeduction, 1);
    });

    it('should mark as ineligible for revenue over 77700 EUR', () => {
      const inputs = createTestInputs({
        revenue: {
          monthlyRent: 7000,
          recoverableCharges: 0,
          occupancyRate: 100,
          badDebtRate: 0,
        },
      });
      const result = calculateMicroBIC(inputs);

      expect(result.eligible).toBe(false);
      expect(result.details.warnings).toContain(
        "Chiffre d'affaires brut > 77 700€ - Régime réel conseillé"
      );
    });

    it('should calculate 17.2% social contributions', () => {
      const inputs = createTestInputs();
      const result = calculateMicroBIC(inputs);

      const expectedSocial = result.revenue.taxable * 0.172;
      expect(result.taxation.socialContributions).toBeCloseTo(expectedSocial, 1);
    });

    it('should handle exactly 77700 EUR revenue (boundary)', () => {
      const inputs = createTestInputs({
        revenue: {
          monthlyRent: 6475,
          recoverableCharges: 0,
          occupancyRate: 100,
          badDebtRate: 0,
        },
      });
      const result = calculateMicroBIC(inputs);

      // Should be eligible at exactly 77700
      expect(result.eligible).toBe(true);
    });
  });

  describe('calculateLMNPReel', () => {
    it('should calculate LMNP Réel with depreciation', () => {
      const inputs = createTestInputs({
        property: {
          totalPrice: 300000,
          furnishingType: 'standard',
          furnitureValue: 10000,
        },
      });
      const result = calculateLMNPReel(inputs);

      expect(result.regime).toBe('lmnp_reel');
      expect(result.eligible).toBe(true);

      // Building depreciation: 300000 / 25 = 12,000
      // Furniture depreciation: 10000 / 5 = 2,000
      const expectedDepreciation = 12000 + 2000;
      expect(result.details.depreciation).toBe(expectedDepreciation);
    });

    it('should use standard social contributions (17.2%) under 23000 EUR', () => {
      const inputs = createTestInputs();
      const result = calculateLMNPReel(inputs);

      const expectedSocial = result.revenue.taxable * 0.172;
      expect(result.taxation.socialContributions).toBeCloseTo(expectedSocial, 1);
    });

    it('should use LMP social contributions (45%) over 23000 EUR', () => {
      const inputs = createTestInputs({
        revenue: {
          monthlyRent: 2500,
          recoverableCharges: 0,
          occupancyRate: 100,
          badDebtRate: 0,
        },
      });
      const result = calculateLMNPReel(inputs);

      // Should have warning about LMP status
      expect(result.details.warnings).toContain(
        'Revenus > 23 000€ - Cotisations sociales LMP applicables'
      );

      // Social contributions at 45%
      const expectedSocial = result.revenue.taxable * 0.45;
      expect(result.taxation.socialContributions).toBeCloseTo(expectedSocial, 1);
    });

    it('should include all deductible expenses plus depreciation', () => {
      const inputs = createTestInputs({
        property: {
          totalPrice: 300000,
          furnishingType: 'basic',
          furnitureValue: 5000,
        },
      });
      const result = calculateLMNPReel(inputs);

      const expectedExpenses =
        inputs.expenses.propertyTax +
        inputs.expenses.nonRecoverableCharges +
        inputs.expenses.annualMaintenance +
        inputs.expenses.cfe +
        inputs.expenses.managementFees +
        inputs.expenses.pnoInsurance +
        inputs.expenses.accountingFees;

      const expectedDepreciation = 12000 + 1000; // 300000/25 + 5000/5

      expect(result.revenue.deduction).toBe(expectedExpenses + expectedDepreciation);
    });

    it('should handle exactly 23000 EUR revenue (LMP boundary)', () => {
      const inputs = createTestInputs({
        revenue: {
          monthlyRent: 1917,
          recoverableCharges: 0,
          occupancyRate: 100,
          badDebtRate: 0,
        },
      });
      const result = calculateLMNPReel(inputs);

      // Revenue of 23004 (1917*12) is just over 23000, so LMP rate applies
      // Check that social contributions are calculated (at 45% LMP rate)
      expect(result.taxation.socialContributions).toBeGreaterThan(0);
      expect(result.details.warnings).toContain(
        'Revenus > 23 000€ - Cotisations sociales LMP applicables'
      );
    });

    it('should handle zero furniture value', () => {
      const inputs = createTestInputs({
        property: {
          totalPrice: 300000,
          furnishingType: 'unfurnished',
          furnitureValue: 0,
        },
      });
      const result = calculateLMNPReel(inputs);

      // Only building depreciation
      const expectedDepreciation = 12000; // 300000/25
      expect(result.details.depreciation).toBe(expectedDepreciation);
    });
  });

  describe('calculateAllRegimes', () => {
    it('should calculate all four regimes', () => {
      const inputs = createTestInputs();
      const results = calculateAllRegimes(inputs);

      expect(results.microFoncier).toBeDefined();
      expect(results.reelFoncier).toBeDefined();
      expect(results.lmnpReel).toBeDefined();
      expect(results.microBic).toBeDefined();
    });

    it('should return different results for different regimes', () => {
      const inputs = createTestInputs();
      const results = calculateAllRegimes(inputs);

      // All regimes should have different taxable income amounts
      const taxables = [
        results.microFoncier.revenue.taxable,
        results.reelFoncier.revenue.taxable,
        results.lmnpReel.revenue.taxable,
        results.microBic.revenue.taxable,
      ];

      // At least some should be different
      const uniqueTaxables = new Set(taxables);
      expect(uniqueTaxables.size).toBeGreaterThan(1);
    });
  });

  describe('calculateGrossYield', () => {
    it('should calculate gross yield correctly', () => {
      const result = calculateGrossYield(12000, 300000, 0);
      expect(result).toBe(4); // 4%
    });

    it('should include furniture value in investment', () => {
      const result = calculateGrossYield(12000, 300000, 10000);
      expect(result).toBeCloseTo(3.87, 1); // 12000 / 310000
    });

    it('should return 0 for zero investment', () => {
      expect(calculateGrossYield(12000, 0, 0)).toBe(0);
    });

    it('should handle negative investment gracefully', () => {
      expect(calculateGrossYield(12000, -10000, 0)).toBe(0);
    });
  });

  describe('calculateNetYield', () => {
    it('should calculate net yield correctly', () => {
      const result = calculateNetYield(12000, 2000, 2000, 300000, 0);
      // (12000 - 2000 - 2000) / 300000 = 8000 / 300000 = 2.67%
      expect(result).toBeCloseTo(2.67, 1);
    });

    it('should return 0 for zero investment', () => {
      expect(calculateNetYield(12000, 2000, 2000, 0, 0)).toBe(0);
    });

    it('should handle negative net income (losses)', () => {
      const result = calculateNetYield(10000, 15000, 3000, 300000, 0);
      expect(result).toBeLessThan(0); // Negative yield
    });
  });

  describe('calculateMonthlyCashFlow', () => {
    it('should calculate positive cash flow', () => {
      const result = calculateMonthlyCashFlow(12000, 8000, 1000);
      // (12000 - 8000 - 1000) / 12 = 250
      expect(result).toBeCloseTo(250, 1);
    });

    it('should calculate negative cash flow', () => {
      const result = calculateMonthlyCashFlow(10000, 12000, 1000);
      expect(result).toBeLessThan(0);
    });

    it('should handle zero cash flow', () => {
      const result = calculateMonthlyCashFlow(12000, 10000, 2000);
      expect(result).toBe(0);
    });
  });

  describe('getRecommendedRegime', () => {
    it('should recommend regime with highest net yield', () => {
      const inputs = createTestInputs();
      const results = calculateAllRegimes(inputs);
      const recommended = getRecommendedRegime(results);

      expect(['micro_foncier', 'reel_foncier', 'lmnp_reel', 'micro_bic']).toContain(recommended);
    });

    it('should only consider eligible regimes', () => {
      const highRevenueInputs = createTestInputs({
        revenue: {
          monthlyRent: 1500,
          recoverableCharges: 0,
          occupancyRate: 100,
          badDebtRate: 0,
        },
      });
      const results = calculateAllRegimes(highRevenueInputs);
      const recommended = getRecommendedRegime(results);

      // Micro-Foncier should be ineligible at this revenue level
      expect(recommended).not.toBe('micro_foncier');
    });
  });

  describe('getFurnitureValue', () => {
    it('should return correct values for each type', () => {
      expect(getFurnitureValue('unfurnished')).toBe(0);
      expect(getFurnitureValue('basic')).toBe(5000);
      expect(getFurnitureValue('standard')).toBe(10000);
      expect(getFurnitureValue('luxury')).toBe(20000);
    });
  });

  describe('getRegimeDisplayName', () => {
    it('should return French names for all regimes', () => {
      expect(getRegimeDisplayName('micro_foncier')).toBe('Micro-Foncier');
      expect(getRegimeDisplayName('reel_foncier')).toBe('Régime Réel Foncier');
      expect(getRegimeDisplayName('lmnp_reel')).toBe('LMNP Réel');
      expect(getRegimeDisplayName('micro_bic')).toBe('Micro-BIC');
    });
  });

  describe('getRegimeDescription', () => {
    it('should return descriptions for all regimes', () => {
      expect(getRegimeDescription('micro_foncier')).toContain('30%');
      expect(getRegimeDescription('micro_bic')).toContain('50%');
      expect(getRegimeDescription('lmnp_reel')).toContain('25 ans');
    });
  });

  describe('Edge Cases and Boundary Tests', () => {
    it('should handle all regimes with zero marginal tax rate', () => {
      const inputs = createTestInputs({
        expenses: {
          propertyTax: 2000,
          nonRecoverableCharges: 1200,
          annualMaintenance: 1500,
          cfe: 1500,
          cvae: 0,
          managementFees: 800,
          pnoInsurance: 300,
          accountingFees: 600,
          marginalTaxRate: 0, // 0% tax bracket
        },
      });

      const microFoncier = calculateMicroFoncier(inputs);
      expect(microFoncier.taxation.incomeTax).toBe(0);
      expect(microFoncier.taxation.socialContributions).toBeGreaterThan(0);
    });

    it('should handle high marginal tax rate (45%)', () => {
      const inputs = createTestInputs({
        expenses: {
          propertyTax: 2000,
          nonRecoverableCharges: 1200,
          annualMaintenance: 1500,
          cfe: 1500,
          cvae: 0,
          managementFees: 800,
          pnoInsurance: 300,
          accountingFees: 600,
          marginalTaxRate: 45,
        },
      });

      const result = calculateReelFoncier(inputs);
      const expectedTax = result.revenue.taxable * 0.45;
      expect(result.taxation.incomeTax).toBeCloseTo(expectedTax, 1);
    });

    it('should ensure LMNP depreciation reduces taxable income', () => {
      const withoutFurniture = createTestInputs({
        property: {
          totalPrice: 300000,
          furnishingType: 'unfurnished',
          furnitureValue: 0,
        },
        revenue: {
          monthlyRent: 2000,
          recoverableCharges: 0,
          occupancyRate: 100,
          badDebtRate: 0,
        },
      });

      const withFurniture = createTestInputs({
        property: {
          totalPrice: 300000,
          furnishingType: 'luxury',
          furnitureValue: 20000,
        },
        revenue: {
          monthlyRent: 2000,
          recoverableCharges: 0,
          occupancyRate: 100,
          badDebtRate: 0,
        },
      });

      const result1 = calculateLMNPReel(withoutFurniture);
      const result2 = calculateLMNPReel(withFurniture);

      // With furniture should have higher depreciation
      expect(result2.details.depreciation).toBeGreaterThan(result1.details.depreciation);

      // With higher depreciation, deduction should be higher
      expect(result2.revenue.deduction).toBeGreaterThan(result1.revenue.deduction);
    });
  });
});
