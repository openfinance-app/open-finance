/**
 * Tests for Financial Calculation Utilities
 */

import {
  calculateFutureValue,
  calculateMonthsToTarget,
  calculateTargetAmount,
  calculateSavingsLongevity,
  calculateRealReturn,
  calculateProgressPercentage,
  calculatePassiveIncome,
  formatCurrency,
  formatPercentage,
  formatTimeToFreedom,
  validateCalculatorInput,
} from './financialCalculations';

describe('calculateFutureValue', () => {
  it('should calculate future value with no contributions', () => {
    // €10,000 at 7% annual for 12 months
    const result = calculateFutureValue(10000, 0, 7, 12);
    expect(result).toBeCloseTo(10723, 0);
  });

  it('should calculate future value with monthly contributions', () => {
    // €10,000 starting + €500/month at 7% annual for 12 months
    const result = calculateFutureValue(10000, 500, 7, 12);
    expect(result).toBeCloseTo(16919, 0);
  });

  it('should handle zero months', () => {
    const result = calculateFutureValue(10000, 500, 7, 0);
    expect(result).toBe(10000);
  });

  it('should handle zero interest rate', () => {
    const result = calculateFutureValue(10000, 500, 0, 12);
    expect(result).toBe(16000); // 10000 + (500 * 12)
  });

  it('should handle negative interest rate', () => {
    const result = calculateFutureValue(10000, 0, -5, 12);
    expect(result).toBeLessThan(10000);
  });

  it('should handle large time periods', () => {
    // 30 years = 360 months
    const result = calculateFutureValue(10000, 500, 7, 360);
    expect(result).toBeGreaterThan(500000);
  });
});

describe('calculateMonthsToTarget', () => {
  it('should calculate months to reach target with contributions', () => {
    // €50,000 starting, €500/month, 7% annual, target €750,000
    const result = calculateMonthsToTarget(50000, 500, 7, 750000);
    expect(result).toBeGreaterThan(0);
    expect(result).toBeLessThan(1200);
  });

  it('should return maxMonths when target is unreachable', () => {
    // No contributions, negative return, can't reach higher target
    const result = calculateMonthsToTarget(10000, 0, -5, 50000, 100);
    expect(result).toBe(100);
  });

  it('should return 0 when already at target', () => {
    const result = calculateMonthsToTarget(100000, 500, 7, 50000);
    expect(result).toBe(0);
  });

  it('should handle zero interest rate with contributions', () => {
    // €10,000 starting, €1,000/month, 0% return, target €20,000
    const result = calculateMonthsToTarget(10000, 1000, 0, 20000);
    expect(result).toBe(10); // Need 10 months of €1,000 contributions
  });

  it('should handle high interest rates', () => {
    const result = calculateMonthsToTarget(10000, 500, 15, 50000);
    expect(result).toBeGreaterThan(0);
    expect(result).toBeLessThan(100);
  });
});

describe('calculateTargetAmount', () => {
  it('should calculate target using 4% rule', () => {
    // €30,000 annual expenses / 4% = €750,000
    const result = calculateTargetAmount(30000, 4);
    expect(result).toBe(750000);
  });

  it('should calculate target using 3% rule', () => {
    // €30,000 annual expenses / 3% = €1,000,000
    const result = calculateTargetAmount(30000, 3);
    expect(result).toBe(1000000);
  });

  it('should handle zero withdrawal rate', () => {
    const result = calculateTargetAmount(30000, 0);
    expect(result).toBe(Infinity);
  });

  it('should handle high withdrawal rates', () => {
    // €30,000 annual expenses / 10% = €300,000
    const result = calculateTargetAmount(30000, 10);
    expect(result).toBe(300000);
  });
});

describe('calculateSavingsLongevity', () => {
  it('should detect infinite sustainability', () => {
    // €1,000,000 at 5% annual = €4,167/month return > €2,500 expenses
    const result = calculateSavingsLongevity(1000000, 2500, 5);
    expect(result.isInfinite).toBe(true);
    expect(result.finalBalance).toBeNull();
  });

  it('should calculate depletion timeline', () => {
    // €100,000 at 5% annual, €2,000/month expenses
    const result = calculateSavingsLongevity(100000, 2000, 5);
    expect(result.isInfinite).toBe(false);
    expect(result.monthsUntilDepletion).toBeGreaterThan(0);
    expect(result.monthsUntilDepletion).toBeLessThan(1200);
  });

  it('should handle zero return rate', () => {
    // €50,000 with €1,000/month expenses and 0% return = 49 months (balance goes to 0 after 49 months)
    const result = calculateSavingsLongevity(50000, 1000, 0);
    expect(result.monthsUntilDepletion).toBe(49);
    expect(result.isInfinite).toBe(false);
  });

  it('should handle negative return rate', () => {
    const result = calculateSavingsLongevity(50000, 1000, -5);
    expect(result.isInfinite).toBe(false);
    expect(result.monthsUntilDepletion).toBeLessThan(50);
  });

  it('should handle immediate depletion', () => {
    // Very high expenses relative to savings
    const result = calculateSavingsLongevity(1000, 5000, 5);
    expect(result.monthsUntilDepletion).toBe(0);
    expect(result.finalBalance).toBe(0);
  });
});

describe('calculateRealReturn', () => {
  it('should calculate real return with inflation', () => {
    // 7% nominal - 2.5% inflation ≈ 4.39% real
    const result = calculateRealReturn(7, 2.5);
    expect(result).toBeCloseTo(4.39, 1);
  });

  it('should handle zero inflation', () => {
    const result = calculateRealReturn(7, 0);
    expect(result).toBeCloseTo(7, 5);
  });

  it('should handle high inflation', () => {
    // 7% nominal - 10% inflation = negative real return
    const result = calculateRealReturn(7, 10);
    expect(result).toBeLessThan(0);
  });

  it('should handle negative nominal return', () => {
    const result = calculateRealReturn(-5, 2);
    expect(result).toBeLessThan(-5);
  });

  it('should handle equal nominal and inflation rates', () => {
    const result = calculateRealReturn(5, 5);
    expect(result).toBeCloseTo(0, 1);
  });
});

describe('calculateProgressPercentage', () => {
  it('should calculate progress percentage', () => {
    const result = calculateProgressPercentage(50000, 100000);
    expect(result).toBe(50);
  });

  it('should cap at 100%', () => {
    const result = calculateProgressPercentage(150000, 100000);
    expect(result).toBe(100);
  });

  it('should handle zero target', () => {
    const result = calculateProgressPercentage(50000, 0);
    expect(result).toBe(100);
  });

  it('should handle zero current savings', () => {
    const result = calculateProgressPercentage(0, 100000);
    expect(result).toBe(0);
  });
});

describe('calculatePassiveIncome', () => {
  it('should calculate passive income at 4% withdrawal', () => {
    const result = calculatePassiveIncome(750000, 4);
    expect(result).toBe(30000);
  });

  it('should calculate passive income at 3% withdrawal', () => {
    const result = calculatePassiveIncome(1000000, 3);
    expect(result).toBe(30000);
  });

  it('should handle zero withdrawal rate', () => {
    const result = calculatePassiveIncome(750000, 0);
    expect(result).toBe(0);
  });
});

describe('formatCurrency', () => {
  it('should format currency in EUR', () => {
    const result = formatCurrency(50000);
    expect(result).toContain('50');
    expect(result).toContain('000');
  });

  it('should format currency in USD', () => {
    const result = formatCurrency(50000, 'en-US', 'USD');
    expect(result).toContain('50');
    expect(result).toContain('000');
  });

  it('should handle zero', () => {
    const result = formatCurrency(0);
    expect(result).toBeDefined();
  });

  it('should handle negative values', () => {
    const result = formatCurrency(-5000);
    expect(result).toContain('5');
    expect(result).toContain('000');
  });

  it('should handle large values', () => {
    const result = formatCurrency(1000000);
    expect(result).toContain('1');
    expect(result).toContain('000');
  });
});

describe('formatPercentage', () => {
  it('should format percentage with default decimals', () => {
    const result = formatPercentage(7.5);
    expect(result).toBe('7.5%');
  });

  it('should format percentage with custom decimals', () => {
    const result = formatPercentage(7.567, 2);
    expect(result).toBe('7.57%');
  });

  it('should handle zero', () => {
    const result = formatPercentage(0);
    expect(result).toBe('0.0%');
  });

  it('should handle negative percentages', () => {
    const result = formatPercentage(-5.5);
    expect(result).toBe('-5.5%');
  });
});

describe('formatTimeToFreedom', () => {
  it('should format years and months', () => {
    const result = formatTimeToFreedom(65); // 5 years, 5 months
    expect(result).toBe('5 years, 5 months');
  });

  it('should format only years', () => {
    const result = formatTimeToFreedom(60); // 5 years exactly
    expect(result).toBe('5 years');
  });

  it('should format only months', () => {
    const result = formatTimeToFreedom(8);
    expect(result).toBe('8 months');
  });

  it('should handle singular year', () => {
    const result = formatTimeToFreedom(12);
    expect(result).toBe('1 year');
  });

  it('should handle singular month', () => {
    const result = formatTimeToFreedom(1);
    expect(result).toBe('1 month');
  });

  it('should handle singular year and month', () => {
    const result = formatTimeToFreedom(13);
    expect(result).toBe('1 year, 1 month');
  });

  it('should handle zero months', () => {
    const result = formatTimeToFreedom(0);
    expect(result).toBe('0 months');
  });
});

describe('validateCalculatorInput', () => {
  it('should validate correct input', () => {
    const result = validateCalculatorInput({
      currentSavings: 50000,
      monthlyExpenses: 2500,
      expectedAnnualReturn: 7,
      monthlyContribution: 500,
      withdrawalRate: 4,
    });
    expect(result.isValid).toBe(true);
    expect(result.errors).toHaveLength(0);
  });

  it('should reject negative current savings', () => {
    const result = validateCalculatorInput({
      currentSavings: -1000,
      monthlyExpenses: 2500,
      expectedAnnualReturn: 7,
    });
    expect(result.isValid).toBe(false);
    expect(result.errors).toContain('Current savings cannot be negative');
  });

  it('should reject negative monthly expenses', () => {
    const result = validateCalculatorInput({
      currentSavings: 50000,
      monthlyExpenses: -100,
      expectedAnnualReturn: 7,
    });
    expect(result.isValid).toBe(false);
    expect(result.errors).toContain('Monthly expenses cannot be negative');
  });

  it('should reject return rate below -10%', () => {
    const result = validateCalculatorInput({
      currentSavings: 50000,
      monthlyExpenses: 2500,
      expectedAnnualReturn: -15,
    });
    expect(result.isValid).toBe(false);
    expect(result.errors).toContain('Return rate must be between -10% and 30%');
  });

  it('should reject return rate above 30%', () => {
    const result = validateCalculatorInput({
      currentSavings: 50000,
      monthlyExpenses: 2500,
      expectedAnnualReturn: 35,
    });
    expect(result.isValid).toBe(false);
    expect(result.errors).toContain('Return rate must be between -10% and 30%');
  });

  it('should reject negative monthly contribution', () => {
    const result = validateCalculatorInput({
      currentSavings: 50000,
      monthlyExpenses: 2500,
      expectedAnnualReturn: 7,
      monthlyContribution: -100,
    });
    expect(result.isValid).toBe(false);
    expect(result.errors).toContain('Monthly contribution cannot be negative');
  });

  it('should reject withdrawal rate below 0.5%', () => {
    const result = validateCalculatorInput({
      currentSavings: 50000,
      monthlyExpenses: 2500,
      expectedAnnualReturn: 7,
      withdrawalRate: 0.3,
    });
    expect(result.isValid).toBe(false);
    expect(result.errors).toContain('Withdrawal rate must be between 0.5% and 10%');
  });

  it('should reject withdrawal rate above 10%', () => {
    const result = validateCalculatorInput({
      currentSavings: 50000,
      monthlyExpenses: 2500,
      expectedAnnualReturn: 7,
      withdrawalRate: 12,
    });
    expect(result.isValid).toBe(false);
    expect(result.errors).toContain('Withdrawal rate must be between 0.5% and 10%');
  });

  it('should collect multiple errors', () => {
    const result = validateCalculatorInput({
      currentSavings: -1000,
      monthlyExpenses: -500,
      expectedAnnualReturn: 50,
    });
    expect(result.isValid).toBe(false);
    expect(result.errors.length).toBeGreaterThan(1);
  });
});
