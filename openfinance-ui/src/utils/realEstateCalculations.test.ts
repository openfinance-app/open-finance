/**
 * Unit Tests for Real Estate Calculations
 *
 * Tests for math utility functions
 * Requirements: REQ-3.1.1, REQ-5.x
 */

import { describe, it, expect } from 'vitest';
import {
  calculateMonthlyPayment,
  calculateRemainingCapital,
  calculateCompoundInterest,
  calculateMinimumResalePrice,
  calculateTotalPrice,
  calculateBorrowedAmount,
  calculateMinimumDownPayment,
  calculateAnnualInterest,
  calculateMonthlyBuyCost,
  calculateMonthlyRentCost,
  calculateInflationAdjustedAmount,
  calculateAppreciatedValue,
  calculateAnnualInsurance,
  calculateAnnualizedFee,
  round,
  sumObjectValues,
  clamp,
  isValidPositiveNumber,
} from './realEstateCalculations';
import type { PurchaseInputs } from '@/types/realEstateTools';

describe('Real Estate Calculations', () => {
  describe('calculateMonthlyPayment', () => {
    it('should calculate correct monthly payment for standard loan', () => {
      // €240,000 over 25 years at 4.2% TAEG
      const result = calculateMonthlyPayment(240000, 4.2, 25);
      // Expected: ~€1,293.46 (validated with actuarial formula)
      expect(result).toBeCloseTo(1293.46, 1);
    });

    it('should return 0 for zero principal', () => {
      expect(calculateMonthlyPayment(0, 4.2, 25)).toBe(0);
    });

    it('should handle edge case of 0% interest', () => {
      const result = calculateMonthlyPayment(240000, 0, 25);
      // Simple division: 240000 / (25 * 12) = 800
      expect(result).toBe(800);
    });

    it('should handle very low interest rates', () => {
      const result = calculateMonthlyPayment(300000, 0.5, 20);
      expect(result).toBeGreaterThan(0);
      expect(result).toBeLessThan(1320); // Should be ~1,313.80
    });

    it('should handle short loan durations', () => {
      const result = calculateMonthlyPayment(100000, 4.2, 5);
      expect(result).toBeGreaterThan(1800); // Higher payments for shorter term
    });

    it('should return principal for zero years', () => {
      expect(calculateMonthlyPayment(100000, 4.2, 0)).toBe(100000);
    });

    it('should handle negative principal gracefully', () => {
      expect(calculateMonthlyPayment(-100000, 4.2, 25)).toBe(0);
    });

    it('should handle negative interest rate', () => {
      const result = calculateMonthlyPayment(100000, -1, 25);
      // Negative rate treated as 0%
      expect(result).toBeCloseTo(333.33, 1);
    });

    it('should handle negative years', () => {
      expect(calculateMonthlyPayment(100000, 4.2, -5)).toBe(100000);
    });

    it('should handle high interest rates', () => {
      const result = calculateMonthlyPayment(100000, 15, 20);
      expect(result).toBeGreaterThan(1300); // Should be significantly higher
    });
  });

  describe('calculateRemainingCapital', () => {
    it('should calculate correct remaining capital after N months', () => {
      const monthlyPayment = calculateMonthlyPayment(240000, 4.2, 25);
      const result = calculateRemainingCapital(240000, 4.2, monthlyPayment, 60);
      // After 5 years, expect ~€204,000 remaining
      expect(result).toBeGreaterThan(200000);
      expect(result).toBeLessThan(210000);
    });

    it('should return 0 when loan is fully paid', () => {
      const monthlyPayment = calculateMonthlyPayment(240000, 4.2, 25);
      const result = calculateRemainingCapital(240000, 4.2, monthlyPayment, 300);
      expect(result).toBeLessThan(1); // Should be ~0 with minor rounding
    });

    it('should return full principal at start', () => {
      const monthlyPayment = calculateMonthlyPayment(240000, 4.2, 25);
      const result = calculateRemainingCapital(240000, 4.2, monthlyPayment, 0);
      expect(result).toBe(240000);
    });

    it('should handle 0% interest correctly', () => {
      const result = calculateRemainingCapital(100000, 0, 1000, 12);
      expect(result).toBe(88000); // 100000 - (1000 * 12)
    });

    it('should never return negative values', () => {
      const monthlyPayment = calculateMonthlyPayment(100000, 4.2, 10);
      const result = calculateRemainingCapital(100000, 4.2, monthlyPayment, 200);
      expect(result).toBe(0);
      expect(result).not.toBeLessThan(0);
    });

    it('should handle negative principal', () => {
      expect(calculateRemainingCapital(-100000, 4.2, 1000, 12)).toBe(0);
    });

    it('should handle very high monthly payment', () => {
      const result = calculateRemainingCapital(100000, 4.2, 50000, 5);
      expect(result).toBe(0); // Paid off completely
    });

    it('should decrease capital over time', () => {
      const monthlyPayment = calculateMonthlyPayment(200000, 4.2, 25);
      const after1Year = calculateRemainingCapital(200000, 4.2, monthlyPayment, 12);
      const after2Years = calculateRemainingCapital(200000, 4.2, monthlyPayment, 24);

      expect(after1Year).toBeLessThan(200000);
      expect(after2Years).toBeLessThan(after1Year);
    });
  });

  describe('calculateCompoundInterest', () => {
    it('should calculate compound interest without contributions', () => {
      const result = calculateCompoundInterest(10000, 4, 10);
      // 10000 * (1.04)^10 = ~14,802
      expect(result).toBeCloseTo(14802.44, 1);
    });

    it('should calculate compound interest with monthly contributions', () => {
      const result = calculateCompoundInterest(10000, 4, 10, 500);
      // Principal growth + contribution growth
      expect(result).toBeGreaterThan(70000); // ~73,500
    });

    it('should return principal for zero years', () => {
      expect(calculateCompoundInterest(10000, 4, 0)).toBe(10000);
    });

    it('should handle 0% interest rate', () => {
      const result = calculateCompoundInterest(10000, 0, 5, 100);
      // Simple accumulation: 10000 + (100 * 12 * 5) = 16000
      expect(result).toBe(16000);
    });

    it('should handle zero principal with contributions', () => {
      const result = calculateCompoundInterest(0, 4, 5, 1000);
      expect(result).toBeGreaterThan(65000); // ~65,500
    });

    it('should handle negative years gracefully', () => {
      expect(calculateCompoundInterest(10000, 4, -5)).toBe(10000);
    });

    it('should handle negative interest rate', () => {
      const result = calculateCompoundInterest(10000, -2, 5);
      // Negative growth
      expect(result).toBeLessThan(10000);
    });

    it('should handle high interest rate', () => {
      const result = calculateCompoundInterest(10000, 20, 5);
      // Significant growth
      expect(result).toBeGreaterThan(24000);
    });
  });

  describe('calculateMinimumResalePrice', () => {
    it('should calculate correct minimum resale price', () => {
      const result = calculateMinimumResalePrice(150000, 200000, 50000, 8);
      // (150000 + 200000 + 50000) / (1 - 0.08) = 434,782.61
      expect(result).toBeCloseTo(434782.61, 1);
    });

    it('should handle 0% resale fees', () => {
      const result = calculateMinimumResalePrice(100000, 150000, 50000, 0);
      expect(result).toBe(300000);
    });

    it('should return Infinity for 100% resale fees', () => {
      const result = calculateMinimumResalePrice(100000, 100000, 0, 100);
      expect(result).toBe(Infinity);
    });

    it('should handle zero target profit', () => {
      const result = calculateMinimumResalePrice(100000, 100000, 0, 10);
      // (100000 + 100000) / 0.9 = 222,222.22
      expect(result).toBeCloseTo(222222.22, 1);
    });

    it('should handle fees over 100%', () => {
      const result = calculateMinimumResalePrice(100000, 100000, 50000, 150);
      expect(result).toBe(Infinity);
    });

    it('should handle all zero values', () => {
      const result = calculateMinimumResalePrice(0, 0, 0, 0);
      expect(result).toBe(0);
    });

    it('should handle high resale fees', () => {
      const result = calculateMinimumResalePrice(100000, 100000, 50000, 20);
      // (250000) / 0.8 = 312,500
      expect(result).toBeCloseTo(312500, 1);
    });
  });

  describe('calculateTotalPrice', () => {
    it('should calculate total price with all fees', () => {
      const inputs: PurchaseInputs = {
        propertyPrice: 300000,
        renovationAmount: 10000,
        isNewProperty: false,
        notaryFeesPercent: 7,
        agencyFees: 5000,
        downPayment: 60000,
        loanDuration: 25,
        interestRate: 4.2,
        totalInsurance: 12900,
        applicationFees: 2000,
        guaranteeFees: 2750,
        accountFees: 720,
        propertyTax: 2000,
        coOwnershipCharges: 1200,
        maintenancePercent: 1,
        homeInsurance: 600,
        bankFees: 0,
        garbageTax: 150,
      };
      const result = calculateTotalPrice(inputs);
      // 300000 + 10000 + (300000 * 0.07) + 5000 = 336000
      expect(result).toBe(336000);
    });

    it('should handle zero fees', () => {
      const inputs: PurchaseInputs = {
        propertyPrice: 300000,
        renovationAmount: 0,
        isNewProperty: false,
        notaryFeesPercent: 0,
        agencyFees: 0,
        downPayment: 60000,
        loanDuration: 25,
        interestRate: 4.2,
        totalInsurance: 12900,
        applicationFees: 2000,
        guaranteeFees: 2750,
        accountFees: 720,
        propertyTax: 2000,
        coOwnershipCharges: 1200,
        maintenancePercent: 1,
        homeInsurance: 600,
        bankFees: 0,
        garbageTax: 150,
      };
      expect(calculateTotalPrice(inputs)).toBe(300000);
    });
  });

  describe('calculateBorrowedAmount', () => {
    it('should calculate borrowed amount correctly', () => {
      expect(calculateBorrowedAmount(336000, 60000)).toBe(276000);
    });

    it('should return 0 when down payment equals or exceeds price', () => {
      expect(calculateBorrowedAmount(300000, 300000)).toBe(0);
      expect(calculateBorrowedAmount(300000, 350000)).toBe(0);
    });
  });

  describe('calculateMinimumDownPayment', () => {
    it('should sum all upfront fees', () => {
      const inputs: PurchaseInputs = {
        propertyPrice: 300000,
        renovationAmount: 0,
        isNewProperty: false,
        notaryFeesPercent: 7,
        agencyFees: 0,
        downPayment: 60000,
        loanDuration: 25,
        interestRate: 4.2,
        totalInsurance: 12900,
        applicationFees: 2000,
        guaranteeFees: 2750,
        accountFees: 720,
        propertyTax: 2000,
        coOwnershipCharges: 1200,
        maintenancePercent: 1,
        homeInsurance: 600,
        bankFees: 0,
        garbageTax: 150,
      };
      // 2000 + 2750 + 720 = 5470
      expect(calculateMinimumDownPayment(inputs)).toBe(5470);
    });
  });

  describe('calculateAnnualInterest', () => {
    it('should calculate annual interest correctly', () => {
      expect(calculateAnnualInterest(200000, 4.2)).toBe(8400);
    });

    it('should return 0 for zero remaining capital', () => {
      expect(calculateAnnualInterest(0, 4.2)).toBe(0);
    });

    it('should return 0 for 0% interest rate', () => {
      expect(calculateAnnualInterest(200000, 0)).toBe(0);
    });
  });

  describe('calculateInflationAdjustedAmount', () => {
    it('should adjust amount for inflation', () => {
      const result = calculateInflationAdjustedAmount(1000, 2, 5);
      // 1000 * (1.02)^5 = ~1,104
      expect(result).toBeCloseTo(1104.08, 1);
    });

    it('should return base amount for zero years', () => {
      expect(calculateInflationAdjustedAmount(1000, 2, 0)).toBe(1000);
    });

    it('should return base amount for 0% inflation', () => {
      expect(calculateInflationAdjustedAmount(1000, 0, 5)).toBe(1000);
    });
  });

  describe('calculateAppreciatedValue', () => {
    it('should calculate appreciated property value', () => {
      const result = calculateAppreciatedValue(300000, 2, 10);
      // 300000 * (1.02)^10 = ~365,698
      expect(result).toBeCloseTo(365698.33, 0);
    });

    it('should return initial value for zero years', () => {
      expect(calculateAppreciatedValue(300000, 2, 0)).toBe(300000);
    });

    it('should handle negative appreciation (depreciation)', () => {
      const result = calculateAppreciatedValue(300000, -2, 5);
      // Value should decrease
      expect(result).toBeLessThan(300000);
    });

    it('should handle zero appreciation', () => {
      expect(calculateAppreciatedValue(300000, 0, 10)).toBe(300000);
    });
  });

  describe('calculateMonthlyBuyCost', () => {
    it('should calculate total monthly buy costs', () => {
      const inputs: PurchaseInputs = {
        propertyPrice: 300000,
        renovationAmount: 0,
        isNewProperty: false,
        notaryFeesPercent: 7,
        agencyFees: 0,
        downPayment: 60000,
        loanDuration: 25,
        interestRate: 4.2,
        totalInsurance: 12900,
        applicationFees: 2000,
        guaranteeFees: 2750,
        accountFees: 720,
        propertyTax: 2000,
        coOwnershipCharges: 1200,
        maintenancePercent: 1,
        homeInsurance: 600,
        bankFees: 0,
        garbageTax: 150,
      };
      const monthlyPayment = calculateMonthlyPayment(276000, 4.2, 25);
      const result = calculateMonthlyBuyCost(inputs, monthlyPayment);

      expect(result).toBeGreaterThan(monthlyPayment);
    });
  });

  describe('calculateMonthlyRentCost', () => {
    it('should calculate total monthly rent costs', () => {
      const result = calculateMonthlyRentCost(1000, 150, 300, 200);
      // 1000 + 150 + (300 + 200) / 12 = 1191.67
      expect(result).toBeCloseTo(1191.67, 1);
    });

    it('should handle zero charges', () => {
      expect(calculateMonthlyRentCost(1000, 0, 0, 0)).toBe(1000);
    });
  });

  describe('calculateAnnualInsurance', () => {
    it('should distribute insurance over loan duration', () => {
      expect(calculateAnnualInsurance(10000, 25)).toBe(400);
    });

    it('should handle zero duration', () => {
      expect(calculateAnnualInsurance(10000, 0)).toBe(10000);
    });
  });

  describe('calculateAnnualizedFee', () => {
    it('should distribute fee over loan duration', () => {
      expect(calculateAnnualizedFee(5000, 25)).toBe(200);
    });

    it('should handle zero duration', () => {
      expect(calculateAnnualizedFee(5000, 0)).toBe(5000);
    });
  });

  describe('Utility Functions', () => {
    describe('round', () => {
      it('should round to 2 decimal places by default', () => {
        expect(round(3.14159)).toBe(3.14);
        expect(round(3.145)).toBe(3.15);
      });

      it('should round to specified decimal places', () => {
        expect(round(3.14159, 3)).toBe(3.142);
        expect(round(3.1, 0)).toBe(3);
      });
    });

    describe('sumObjectValues', () => {
      it('should sum all numeric values in object', () => {
        const obj = { a: 10, b: 20, c: 30 };
        expect(sumObjectValues(obj)).toBe(60);
      });

      it('should return 0 for empty object', () => {
        expect(sumObjectValues({})).toBe(0);
      });
    });

    describe('clamp', () => {
      it('should clamp value between min and max', () => {
        expect(clamp(50, 0, 100)).toBe(50);
        expect(clamp(-10, 0, 100)).toBe(0);
        expect(clamp(150, 0, 100)).toBe(100);
      });
    });

    describe('isValidPositiveNumber', () => {
      it('should return true for valid positive numbers', () => {
        expect(isValidPositiveNumber(100)).toBe(true);
        expect(isValidPositiveNumber(0)).toBe(true);
        expect(isValidPositiveNumber(0.5)).toBe(true);
      });

      it('should return false for invalid values', () => {
        expect(isValidPositiveNumber(-1)).toBe(false);
        expect(isValidPositiveNumber(NaN)).toBe(false);
        expect(isValidPositiveNumber(Infinity)).toBe(false);
        expect(isValidPositiveNumber('100')).toBe(false);
        expect(isValidPositiveNumber(null)).toBe(false);
        expect(isValidPositiveNumber(undefined)).toBe(false);
      });
    });
  });
});
