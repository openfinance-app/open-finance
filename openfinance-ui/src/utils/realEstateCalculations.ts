/**
 * Real Estate Math Utility Functions
 * 
 * Core calculation formulas for Buy/Rent Comparator and Rental Simulator
 * Requirements: REQ-1.5.1, REQ-1.5.2, REQ-1.5.3, REQ-1.5.4
 */

import type { PurchaseInputs } from '@/types/realEstateTools';
import {
  add,
  subtract,
  multiply,
  divide,
  sum,
  pow,
  roundToDecimals,
} from '@/utils/money';

/**
 * Calculate monthly mortgage payment using actuarial formula
 * Formula: M = P * (r * (1 + r)^n) / ((1 + r)^n - 1)
 * where:
 *   M = monthly payment
 *   P = principal (borrowed amount)
 *   r = monthly interest rate (annual / 12 / 100)
 *   n = total number of payments (years * 12)
 * 
 * REQ-1.5.1
 * 
 * @param principal - Principal amount borrowed (EUR)
 * @param annualRate - Annual interest rate (percentage, e.g., 4.2 for 4.2%)
 * @param years - Loan duration in years
 * @returns Monthly payment amount in EUR
 * 
 * @example
 * calculateMonthlyPayment(240000, 4.2, 25) // Returns ~1299.32
 */
export function calculateMonthlyPayment(
  principal: number,
  annualRate: number,
  years: number
): number {
  // Handle edge cases
  if (principal <= 0) return 0;
  if (years <= 0) return principal;

  const numPayments = years * 12;

  // Handle 0% interest case
  if (annualRate <= 0) {
    return divide(principal, numPayments);
  }

  const monthlyRate = divide(divide(annualRate, 12), 100); // Convert percentage to decimal

  // Actuarial formula
  const factor = pow(add(1, monthlyRate), numPayments);
  const payment = divide(
    multiply(multiply(principal, monthlyRate), factor),
    subtract(factor, 1)
  );

  return roundToDecimals(payment, 2); // Round to 2 decimal places
}

/**
 * Calculate remaining capital after given number of months
 * Formula: CR = P * (1 + r)^m - M * ((1 + r)^m - 1) / r
 * where:
 *   CR = remaining capital
 *   P = initial principal
 *   r = monthly interest rate
 *   m = months elapsed
 *   M = monthly payment
 * 
 * REQ-1.5.2
 * 
 * @param principal - Initial borrowed amount (EUR)
 * @param annualRate - Annual interest rate (percentage)
 * @param monthlyPayment - Monthly payment amount (EUR)
 * @param monthsElapsed - Number of months elapsed
 * @returns Remaining capital in EUR
 * 
 * @example
 * const monthlyPayment = calculateMonthlyPayment(240000, 4.2, 25);
 * calculateRemainingCapital(240000, 4.2, monthlyPayment, 60) // After 5 years
 */
export function calculateRemainingCapital(
  principal: number,
  annualRate: number,
  monthlyPayment: number,
  monthsElapsed: number
): number {
  // Handle edge cases
  if (principal <= 0) return 0;
  if (monthsElapsed <= 0) return principal;

  // Handle 0% interest case - simple linear reduction
  if (annualRate <= 0) {
    return Math.max(0, subtract(principal, multiply(monthlyPayment, monthsElapsed)));
  }

  const monthlyRate = divide(divide(annualRate, 12), 100);

  // Actuarial formula for remaining capital
  const factor = pow(add(1, monthlyRate), monthsElapsed);
  const remaining = subtract(
    multiply(principal, factor),
    divide(multiply(monthlyPayment, subtract(factor, 1)), monthlyRate)
  );

  return Math.max(0, roundToDecimals(remaining, 2));
}

/**
 * Calculate compound interest growth with optional monthly contributions
 * Formula for principal: A = P * (1 + r)^t
 * Formula for contributions: FV = PMT * (((1 + r)^n - 1) / r)
 * 
 * REQ-1.5.3
 * 
 * @param principal - Initial principal amount (EUR)
 * @param annualRate - Annual return rate (percentage, e.g., 4 for 4%)
 * @param years - Number of years
 * @param monthlyContribution - Optional monthly contribution (EUR)
 * @returns Final amount in EUR
 * 
 * @example
 * calculateCompoundInterest(10000, 4, 10, 500) // Initial 10k + 500/month for 10 years at 4%
 */
export function calculateCompoundInterest(
  principal: number,
  annualRate: number,
  years: number,
  monthlyContribution: number = 0
): number {
  if (years <= 0) return principal;

  const rate = divide(annualRate, 100);

  // Compound interest for initial principal
  const principalGrowth = multiply(principal, pow(add(1, rate), years));

  // Future value of monthly contributions
  if (monthlyContribution > 0 && rate > 0) {
    const monthlyRate = divide(rate, 12);
    const months = years * 12;
    const contributionGrowth = multiply(
      monthlyContribution,
      divide(subtract(pow(add(1, monthlyRate), months), 1), monthlyRate)
    );
    return roundToDecimals(add(principalGrowth, contributionGrowth), 2);
  } else if (monthlyContribution > 0) {
    // 0% interest - simple accumulation
    return roundToDecimals(
      add(principalGrowth, multiply(multiply(monthlyContribution, 12), years)),
      2
    );
  }

  return roundToDecimals(principalGrowth, 2);
}

/**
 * Calculate minimum resale price to cover costs and achieve target profit
 * Formula: Prix_min = (Cout_total + Capital_restant + Benefice_souhaite) / (1 - Frais_revente)
 * 
 * REQ-1.5.4
 * 
 * @param totalCosts - Total costs incurred (EUR)
 * @param remainingCapital - Remaining capital to repay (EUR)
 * @param targetProfit - Desired net profit (EUR)
 * @param resaleFeesPercent - Resale fees percentage (e.g., 8 for 8%)
 * @returns Minimum resale price in EUR
 * 
 * @example
 * calculateMinimumResalePrice(150000, 200000, 50000, 8) // ~434,783
 */
export function calculateMinimumResalePrice(
  totalCosts: number,
  remainingCapital: number,
  targetProfit: number,
  resaleFeesPercent: number
): number {
  const resaleFees = divide(resaleFeesPercent, 100);

  // When fees >= 100%, the price needed is mathematically infinite
  if (resaleFees >= 1) return Infinity;

  const minimumPrice = divide(
    add(add(totalCosts, remainingCapital), targetProfit),
    subtract(1, resaleFees)
  );
  return roundToDecimals(minimumPrice, 2);
}

/**
 * Calculate total property price including fees
 * 
 * @param inputs - Purchase input parameters
 * @returns Total price in EUR
 * 
 * @example
 * calculateTotalPrice({ propertyPrice: 300000, renovationAmount: 0, notaryFeesPercent: 7, agencyFees: 0 })
 * // Returns 321000
 */
export function calculateTotalPrice(inputs: PurchaseInputs): number {
  const notaryFees = multiply(inputs.propertyPrice, divide(inputs.notaryFeesPercent, 100));
  return sum([inputs.propertyPrice, inputs.renovationAmount, notaryFees, inputs.agencyFees]);
}

/**
 * Calculate borrowed amount (total price - down payment)
 * 
 * @param totalPrice - Total property price including fees (EUR)
 * @param downPayment - Personal down payment (EUR)
 * @returns Borrowed amount in EUR
 */
export function calculateBorrowedAmount(
  totalPrice: number,
  downPayment: number
): number {
  return Math.max(0, subtract(totalPrice, downPayment));
}

/**
 * Calculate minimum required down payment (fees only)
 * This represents the minimum cash needed at purchase (notary, agency, application fees, etc.)
 * 
 * @param inputs - Purchase input parameters
 * @returns Minimum down payment in EUR
 */
export function calculateMinimumDownPayment(inputs: PurchaseInputs): number {
  // Minimum down payment = fees that cannot be financed
  return sum([inputs.applicationFees, inputs.guaranteeFees, inputs.accountFees]);
}

/**
 * Calculate annual interest portion of a payment
 * Formula: Interest = Remaining_Capital * Annual_Rate
 * 
 * @param remainingCapital - Current remaining capital (EUR)
 * @param annualRate - Annual interest rate (percentage)
 * @returns Annual interest amount in EUR
 */
export function calculateAnnualInterest(
  remainingCapital: number,
  annualRate: number
): number {
  if (remainingCapital <= 0 || annualRate <= 0) return 0;
  return multiply(remainingCapital, divide(annualRate, 100));
}

/**
 * Calculate monthly buy scenario costs
 * Includes mortgage, taxes, insurance, and other recurring charges
 * 
 * @param inputs - Purchase input parameters
 * @param monthlyPayment - Monthly mortgage payment (EUR)
 * @returns Total monthly cost in EUR
 */
export function calculateMonthlyBuyCost(
  inputs: PurchaseInputs,
  monthlyPayment: number
): number {
  const monthlyCharges = add(
    divide(
      sum([
        inputs.propertyTax,
        inputs.coOwnershipCharges,
        inputs.homeInsurance,
        inputs.bankFees,
        inputs.garbageTax,
      ]),
      12
    ),
    divide(
      divide(multiply(inputs.propertyPrice, inputs.maintenancePercent), 100),
      12
    )
  );

  return add(monthlyPayment, monthlyCharges);
}

/**
 * Calculate monthly rental scenario costs
 * 
 * @param monthlyRent - Monthly rent excluding charges (EUR)
 * @param monthlyCharges - Monthly rental charges (EUR)
 * @param annualInsurance - Annual rental insurance (EUR)
 * @param annualGarbageTax - Annual garbage tax (EUR)
 * @returns Total monthly cost in EUR
 */
export function calculateMonthlyRentCost(
  monthlyRent: number,
  monthlyCharges: number,
  annualInsurance: number,
  annualGarbageTax: number
): number {
  return add(
    add(monthlyRent, monthlyCharges),
    divide(add(annualInsurance, annualGarbageTax), 12)
  );
}

/**
 * Calculate inflation-adjusted amount
 * 
 * @param baseAmount - Base amount (EUR)
 * @param inflationRate - Annual inflation rate (percentage)
 * @param years - Number of years
 * @returns Inflation-adjusted amount in EUR
 */
export function calculateInflationAdjustedAmount(
  baseAmount: number,
  inflationRate: number,
  years: number
): number {
  if (years <= 0) return baseAmount;
  return multiply(baseAmount, pow(add(1, divide(inflationRate, 100)), years));
}

/**
 * Calculate property value with appreciation
 * 
 * @param initialValue - Initial property value (EUR)
 * @param appreciationRate - Annual appreciation rate (percentage)
 * @param years - Number of years
 * @returns Appreciated value in EUR
 */
export function calculateAppreciatedValue(
  initialValue: number,
  appreciationRate: number,
  years: number
): number {
  if (years <= 0) return initialValue;
  return multiply(initialValue, pow(add(1, divide(appreciationRate, 100)), years));
}

/**
 * Calculate annual insurance cost distributed over loan duration
 * 
 * @param totalInsurance - Total insurance cost over loan duration (EUR)
 * @param loanDurationYears - Loan duration in years
 * @returns Annual insurance cost in EUR
 */
export function calculateAnnualInsurance(
  totalInsurance: number,
  loanDurationYears: number
): number {
  if (loanDurationYears <= 0) return totalInsurance;
  return divide(totalInsurance, loanDurationYears);
}

/**
 * Calculate annual fee distributed over loan duration
 * 
 * @param totalFee - Total fee amount (EUR)
 * @param loanDurationYears - Loan duration in years
 * @returns Annual fee amount in EUR
 */
export function calculateAnnualizedFee(
  totalFee: number,
  loanDurationYears: number
): number {
  if (loanDurationYears <= 0) return totalFee;
  return divide(totalFee, loanDurationYears);
}

/**
 * Note: Use formatCurrency, formatPercentage from '@/utils/format' for UI display
 * This file focuses on calculation utilities only
 */

/**
 * Round a number to specified decimal places
 * 
 * @param value - Number to round
 * @param decimals - Number of decimal places (default: 2)
 * @returns Rounded number
 */
export function round(value: number, decimals: number = 2): number {
  return roundToDecimals(value, decimals);
}

/**
 * Calculate sum of values in an object
 * 
 * @param obj - Object with numeric values
 * @returns Sum of all values
 */
export function sumObjectValues(obj: Record<string, number>): number {
  return sum(Object.values(obj));
}

/**
 * Validate that a number is within a range
 * 
 * @param value - Number to validate
 * @param min - Minimum allowed value
 * @param max - Maximum allowed value
 * @returns Clamped value
 */
export function clamp(value: number, min: number, max: number): number {
  return Math.min(Math.max(value, min), max);
}

/**
 * Check if a value is a valid positive number
 * 
 * @param value - Value to check
 * @returns True if valid positive number
 */
export function isValidPositiveNumber(value: unknown): value is number {
  return typeof value === 'number' && !isNaN(value) && isFinite(value) && value >= 0;
}
