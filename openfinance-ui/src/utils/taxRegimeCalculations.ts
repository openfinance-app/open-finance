/**
 * Tax Regime Calculation Functions
 *
 * French real estate tax regime calculations
 * Requirements: REQ-2.4.1, REQ-2.4.2, REQ-2.4.3, REQ-2.4.4
 */

import type {
  InvestmentInputs,
  RegimeCalculationResult,
  TaxRegime,
  RentalRevenueInputs,
  OwnerExpensesInputs,
  RentalCalculationStatus,
} from '@/types/realEstateTools';
import {
  DEFAULT_RENTAL_TAX_CONTEXT,
  REGIME_LIMITS,
  REGIME_RATES,
  FURNITURE_VALUES,
} from '@/types/realEstateTools';
import { add, subtract, multiply, divide, sum, percentage } from '@/utils/money';
import i18n from '@/i18n';

/**
 * Calculate gross rental revenue
 * Formula: (Monthly Rent + Recoverable Charges) * 12 * Occupancy Rate * (1 - Bad Debt Rate)
 *
 * @param revenue - Rental revenue inputs
 * @returns Gross annual revenue in EUR
 */
export function calculateGrossRevenue(revenue: RentalRevenueInputs): number {
  const annualRent = multiply(add(revenue.monthlyRent, revenue.recoverableCharges), 12);
  const effectiveOccupancy = divide(revenue.occupancyRate, 100);
  const effectiveCollection = subtract(1, divide(revenue.badDebtRate, 100));

  return multiply(multiply(annualRent, effectiveOccupancy), effectiveCollection);
}

/** Tenant reimbursements are excluded from unfurnished tax receipts and economic profit. */
export function calculateRentExcludingCharges(revenue: RentalRevenueInputs): number {
  return calculateGrossRevenue({ ...revenue, recoverableCharges: 0 });
}

/** Ordinary full-year, long-term furnished rentals by French-resident households. */
function furnishedStatus(inputs: InvestmentInputs): RentalCalculationStatus {
  const tax = { ...DEFAULT_RENTAL_TAX_CONTEXT, ...inputs.tax };
  const householdReceipts = add(calculateGrossRevenue(inputs.revenue), tax.otherFurnishedReceipts);
  if (householdReceipts <= REGIME_LIMITS.LMNP_SOCIAL_THRESHOLD) return 'complete';
  if (tax.otherHouseholdIncome === null) return 'needsHouseholdIncome';
  return householdReceipts > tax.otherHouseholdIncome ? 'professionalOutOfScope' : 'complete';
}

/**
 * Calculate Micro-Foncier regime
 * - Eligible if gross revenue <= €15,000
 * - 30% flat-rate deduction
 * - 17.2% social contributions on taxable income
 *
 * REQ-2.4.1, REQ-2.6.2
 *
 * @param inputs - Investment inputs
 * @returns Regime calculation result
 */
export function calculateMicroFoncier(inputs: InvestmentInputs): RegimeCalculationResult {
  const grossRevenue = calculateRentExcludingCharges(inputs.revenue);
  const eligible =
    add(grossRevenue, inputs.tax?.otherUnfurnishedRent ?? 0) <= REGIME_LIMITS.MICRO_FONCIER;

  const abattement = multiply(grossRevenue, REGIME_RATES.MICRO_FONCIER_ABATEMENT);
  const taxableIncome = Math.max(0, subtract(grossRevenue, abattement));
  const incomeTax = multiply(taxableIncome, divide(inputs.expenses.marginalTaxRate, 100));
  const socialContributions = multiply(taxableIncome, REGIME_RATES.SOCIAL_CONTRIBUTIONS_STANDARD);

  const warnings: string[] = [];
  if (!eligible) {
    warnings.push(i18n.t('taxContext.microFoncierLimit', { ns: 'realEstate' }));
  }

  return buildRegimeResult(
    'micro_foncier',
    eligible,
    inputs,
    grossRevenue,
    abattement,
    0, // No depreciation
    incomeTax,
    socialContributions,
    warnings
  );
}

/**
 * Calculate Réel Foncier regime
 * - No revenue limit
 * - All actual expenses deductible
 * - 17.2% social contributions on taxable income
 *
 * REQ-2.4.2
 *
 * @param inputs - Investment inputs
 * @returns Regime calculation result
 */
export function calculateReelFoncier(inputs: InvestmentInputs): RegimeCalculationResult {
  const grossRevenue = calculateRentExcludingCharges(inputs.revenue);

  const deductibleExpenses = calculateDeductibleExpenses(inputs.expenses);
  const taxableIncome = Math.max(0, subtract(grossRevenue, deductibleExpenses));
  const incomeTax = multiply(taxableIncome, divide(inputs.expenses.marginalTaxRate, 100));
  const socialContributions = multiply(taxableIncome, REGIME_RATES.SOCIAL_CONTRIBUTIONS_STANDARD);

  return buildRegimeResult(
    'reel_foncier',
    true,
    inputs,
    grossRevenue,
    deductibleExpenses,
    0, // No depreciation in réel foncier
    incomeTax,
    socialContributions,
    []
  );
}

/**
 * Calculate Micro-BIC regime (furnished rental)
 * - Long-term micro-BIC threshold depends on the income year
 * - 50% flat-rate deduction
 * - 18.6% non-professional social levies (income years 2025 and 2026)
 *
 * REQ-2.4.3, REQ-2.6.2
 *
 * @param inputs - Investment inputs
 * @returns Regime calculation result
 */
export function calculateMicroBIC(inputs: InvestmentInputs): RegimeCalculationResult {
  const grossRevenue = calculateGrossRevenue(inputs.revenue);
  // For Micro-BIC, eligibility is based on gross revenue (chiffre d'affaires)
  // before any deductions
  const tax = { ...DEFAULT_RENTAL_TAX_CONTEXT, ...inputs.tax };
  const threshold = REGIME_LIMITS.MICRO_BIC[tax.incomeYear];
  const status = furnishedStatus(inputs);
  const householdReceipts = add(grossRevenue, tax.otherFurnishedReceipts);
  const eligible = householdReceipts <= threshold && status === 'complete';

  const householdAbatement = Math.min(
    householdReceipts,
    Math.max(305, multiply(householdReceipts, REGIME_RATES.MICRO_BIC_ABATEMENT))
  );
  // Allocate the household allowance to this property's share of receipts. The minimum
  // must not be awarded again for every property in the same household.
  const abattement =
    householdReceipts === 0
      ? 0
      : multiply(householdAbatement, divide(grossRevenue, householdReceipts));
  const taxableIncome = Math.max(0, subtract(grossRevenue, abattement));
  const incomeTax = multiply(taxableIncome, divide(inputs.expenses.marginalTaxRate, 100));
  const socialContributions = multiply(taxableIncome, REGIME_RATES.SOCIAL_CONTRIBUTIONS_FURNISHED);

  const warnings: string[] = [];
  if (add(grossRevenue, tax.otherFurnishedReceipts) > threshold) {
    warnings.push(i18n.t('taxContext.microBicLimit', { ns: 'realEstate', threshold }));
  }

  return buildRegimeResult(
    'micro_bic',
    eligible,
    inputs,
    grossRevenue,
    abattement,
    0, // No depreciation in micro-BIC
    incomeTax,
    socialContributions,
    warnings,
    status
  );
}

/**
 * Calculate LMNP Réel regime (Non-Professional Furnished Landlord)
 * - No revenue limit for eligibility
 * - All actual expenses deductible
 * - Building depreciation: 25 years straight-line
 * - Furniture depreciation: 5 years straight-line
 * - 18.6% social levies for LMNP; professional contributions are not estimated
 *
 * REQ-2.4.4, REQ-2.6.3
 *
 * @param inputs - Investment inputs
 * @returns Regime calculation result
 */
export function calculateLMNPReel(inputs: InvestmentInputs): RegimeCalculationResult {
  const grossRevenue = calculateGrossRevenue(inputs.revenue);

  const recoveredCharges = subtract(grossRevenue, calculateRentExcludingCharges(inputs.revenue));
  const deductibleExpenses = add(calculateDeductibleExpenses(inputs.expenses), recoveredCharges);

  // Calculate depreciation
  const buildingDepreciation = divide(
    inputs.property.totalPrice,
    REGIME_RATES.BUILDING_DEPRECIATION_YEARS
  );
  const furnitureDepreciation = divide(
    inputs.property.furnitureValue,
    REGIME_RATES.FURNITURE_DEPRECIATION_YEARS
  );
  const totalDepreciation = add(buildingDepreciation, furnitureDepreciation);

  const totalDeductions = add(deductibleExpenses, totalDepreciation);
  const taxableIncome = Math.max(0, subtract(grossRevenue, totalDeductions));
  const incomeTax = multiply(taxableIncome, divide(inputs.expenses.marginalTaxRate, 100));

  const status = furnishedStatus(inputs);
  const socialContributions = multiply(taxableIncome, REGIME_RATES.SOCIAL_CONTRIBUTIONS_FURNISHED);
  const warnings: string[] = [];

  const result = buildRegimeResult(
    'lmnp_reel',
    status === 'complete',
    inputs,
    grossRevenue,
    totalDeductions,
    totalDepreciation,
    incomeTax,
    socialContributions,
    warnings,
    status
  );

  return result;
}

/**
 * Calculate all deductible expenses (excluding credit costs and depreciation)
 *
 * @param expenses - Owner expenses inputs
 * @returns Total deductible expenses in EUR
 */
function calculateDeductibleExpenses(expenses: OwnerExpensesInputs): number {
  return sum([
    expenses.propertyTax,
    expenses.nonRecoverableCharges,
    expenses.annualMaintenance,
    expenses.cfe,
    expenses.cvae,
    expenses.managementFees,
    expenses.pnoInsurance,
    expenses.accountingFees,
  ]);
}

/**
 * Build a standardized regime calculation result object
 *
 * @param regime - Tax regime type
 * @param eligible - Whether the regime is eligible
 * @param inputs - Investment inputs
 * @param grossRevenue - Gross annual revenue
 * @param deduction - Total deductions (expenses + abatement + depreciation)
 * @param depreciation - Depreciation amount (if applicable)
 * @param incomeTax - Calculated income tax
 * @param socialContributions - Calculated social contributions
 * @param warnings - Array of warning messages
 * @returns Complete regime calculation result
 */
function buildRegimeResult(
  regime: TaxRegime,
  eligible: boolean,
  inputs: InvestmentInputs,
  grossRevenue: number,
  deduction: number,
  depreciation: number,
  incomeTax: number,
  socialContributions: number,
  warnings: string[],
  status: RentalCalculationStatus = 'complete'
): RegimeCalculationResult {
  const taxableIncome = Math.max(0, subtract(grossRevenue, deduction));
  const totalTaxes = add(incomeTax, socialContributions);

  // Calculate charges breakdown
  const creditCharges = sum([
    inputs.credit.annualCost,
    inputs.credit.assurance ?? 0,
    inputs.credit.bankFees ?? 0,
  ]);
  const otherCharges = calculateDeductibleExpenses(inputs.expenses);
  const totalCharges = add(creditCharges, otherCharges);

  // Calculate performance metrics
  const netRevenue = calculateRentExcludingCharges(inputs.revenue);
  const monthlyCashFlow = divide(subtract(subtract(netRevenue, totalCharges), totalTaxes), 12);

  const totalInvestment = add(inputs.property.totalPrice, inputs.property.furnitureValue);
  const grossYield = totalInvestment > 0 ? percentage(netRevenue, totalInvestment) : 0;
  const netYield =
    totalInvestment > 0
      ? percentage(
          subtract(subtract(subtract(netRevenue, otherCharges), incomeTax), socialContributions),
          totalInvestment
        )
      : 0;

  return {
    regime,
    eligible,
    investment: {
      totalPrice: totalInvestment,
      annualCreditCost: creditCharges,
      monthlyCreditPayment: inputs.credit.monthlyPayment,
      detail: {
        credit: inputs.credit.annualCost,
        assurance: inputs.credit.assurance,
        fraisBancaires: inputs.credit.bankFees,
      },
    },
    revenue: {
      gross: grossRevenue,
      net: netRevenue,
      deduction,
      taxable: taxableIncome,
      detail: {
        loyers: multiply(inputs.revenue.monthlyRent, 12),
        chargesRecup: multiply(inputs.revenue.recoverableCharges, 12),
      },
    },
    charges: {
      total: totalCharges,
      credit: creditCharges,
      other: otherCharges,
    },
    taxation: {
      regime,
      incomeTax: status === 'complete' ? incomeTax : null,
      socialContributions: status === 'complete' ? socialContributions : null,
      totalTaxes: status === 'complete' ? totalTaxes : null,
    },
    performance: {
      monthlyCashFlow: status === 'complete' ? monthlyCashFlow : null,
      grossYield: grossYield,
      netYield: status === 'complete' ? netYield : null,
    },
    details: {
      isEligible: eligible,
      calculationStatus: status,
      depreciation: depreciation,
      warnings: warnings,
    },
  };
}

/**
 * Calculate all tax regimes at once
 *
 * @param inputs - Investment inputs
 * @returns Results for all 4 tax regimes
 */
export function calculateAllRegimes(inputs: InvestmentInputs): {
  microFoncier: RegimeCalculationResult;
  reelFoncier: RegimeCalculationResult;
  lmnpReel: RegimeCalculationResult;
  microBic: RegimeCalculationResult;
} {
  return {
    microFoncier: calculateMicroFoncier(inputs),
    reelFoncier: calculateReelFoncier(inputs),
    lmnpReel: calculateLMNPReel(inputs),
    microBic: calculateMicroBIC(inputs),
  };
}

/**
 * Get furniture value by type
 *
 * @param type - Furnishing type
 * @returns Furniture value in EUR
 */
export function getFurnitureValue(type: keyof typeof FURNITURE_VALUES): number {
  return FURNITURE_VALUES[type] || 0;
}

/**
 * Calculate effective gross yield (rental revenue / total investment)
 *
 * @param annualRent - Annual rental revenue
 * @param propertyPrice - Property purchase price
 * @param furnitureValue - Furniture value (optional)
 * @returns Gross yield percentage
 */
export function calculateGrossYield(
  annualRent: number,
  propertyPrice: number,
  furnitureValue: number = 0
): number {
  const totalInvestment = add(propertyPrice, furnitureValue);
  if (totalInvestment <= 0) return 0;
  return percentage(annualRent, totalInvestment);
}

/**
 * Calculate net yield accounting for expenses and taxes
 *
 * @param annualRent - Annual rental revenue
 * @param expenses - Annual expenses (excluding credit)
 * @param taxes - Annual taxes
 * @param propertyPrice - Property purchase price
 * @param furnitureValue - Furniture value (optional)
 * @returns Net yield percentage
 */
export function calculateNetYield(
  annualRent: number,
  expenses: number,
  taxes: number,
  propertyPrice: number,
  furnitureValue: number = 0
): number {
  const totalInvestment = add(propertyPrice, furnitureValue);
  if (totalInvestment <= 0) return 0;
  const netIncome = subtract(subtract(annualRent, expenses), taxes);
  return percentage(netIncome, totalInvestment);
}

/**
 * Calculate monthly cash flow
 *
 * @param annualRevenue - Annual rental revenue
 * @param annualExpenses - Annual expenses (including credit)
 * @param annualTaxes - Annual taxes
 * @returns Monthly cash flow in EUR
 */
export function calculateMonthlyCashFlow(
  annualRevenue: number,
  annualExpenses: number,
  annualTaxes: number
): number {
  return divide(subtract(subtract(annualRevenue, annualExpenses), annualTaxes), 12);
}

/**
 * Determine the recommended tax regime based on results
 * Returns the regime with the highest net yield
 *
 * @param results - All regime calculation results
 * @returns The recommended regime type
 */
export function getRecommendedRegime(results: {
  microFoncier: RegimeCalculationResult;
  reelFoncier: RegimeCalculationResult;
  lmnpReel: RegimeCalculationResult;
  microBic: RegimeCalculationResult;
}): TaxRegime {
  const regimes: TaxRegime[] = ['micro_foncier', 'reel_foncier', 'lmnp_reel', 'micro_bic'];

  let bestRegime: TaxRegime = 'reel_foncier';
  let bestYield = -Infinity;

  for (const regime of regimes) {
    const result =
      results[
        regime === 'micro_foncier'
          ? 'microFoncier'
          : regime === 'reel_foncier'
            ? 'reelFoncier'
            : regime === 'lmnp_reel'
              ? 'lmnpReel'
              : 'microBic'
      ];

    if (
      result.eligible &&
      result.performance.netYield !== null &&
      result.performance.netYield > bestYield
    ) {
      bestYield = result.performance.netYield;
      bestRegime = regime;
    }
  }

  return bestRegime;
}

/**
 * Get regime display name in French
 *
 * @param regime - Tax regime type
 * @returns French display name
 */
export function getRegimeDisplayName(regime: TaxRegime): string {
  const names: Record<TaxRegime, string> = {
    micro_foncier: 'Micro-Foncier',
    reel_foncier: 'Régime Réel Foncier',
    lmnp_reel: 'LMNP Réel',
    micro_bic: 'Micro-BIC',
  };
  return names[regime] || regime;
}

/**
 * Get regime description in French
 *
 * @param regime - Tax regime type
 * @returns French description
 */
export function getRegimeDescription(regime: TaxRegime): string {
  return i18n.t(`regimeDescriptions.${regime}`, { ns: 'realEstate' });
}
