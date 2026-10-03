/**
 * Real Estate Calculation Service
 *
 * Main calculation orchestration service for Buy/Rent Comparator and Rental Simulator
 * Requirements: REQ-1.5.x, REQ-1.6.x
 */

import type {
  BuyRentInputs,
  BuyRentResults,
  YearlyResult,
  BuyCostDetails,
  BuyScenarioSummary,
  RentScenarioSummary,
  ComparisonMetrics,
  YearNAnalysis,
  InvestmentInputs,
  InvestmentResults,
} from '@/types/realEstateTools';
import { NEW_PROPERTY_TAX_EXEMPTION_YEARS } from '@/types/realEstateTools';
import {
  calculateMonthlyPayment,
  calculateRemainingCapital,
  calculateCompoundInterest,
  calculateMinimumResalePrice,
  calculateTotalPrice,
  calculateBorrowedAmount,
  calculateAppreciatedValue,
} from '@/utils/realEstateCalculations';
import { add, subtract, multiply, divide, sum, pow } from '@/utils/money';
import { calculateAllRegimes } from '@/utils/taxRegimeCalculations';
import { DEFAULT_CURRENCY, formatCurrency } from '@/utils/currency';

/**
 * Real Estate Calculation Service
 * Provides methods for running complete simulations
 */
export class RealEstateCalculationService {
  /**
   * Run complete buy vs rent comparison simulation
   * REQ-1.5.x
   *
   * @param inputs - Buy/Rent input parameters
   * @returns Complete simulation results
   */
  static calculateBuyRentComparison(inputs: BuyRentInputs): BuyRentResults {
    const startTime = performance.now();

    // Calculate initial values
    const totalPrice = calculateTotalPrice(inputs.purchase);
    const borrowedAmount = calculateBorrowedAmount(totalPrice, inputs.purchase.downPayment);
    const monthlyPayment = calculateMonthlyPayment(
      borrowedAmount,
      inputs.purchase.interestRate,
      inputs.purchase.loanDuration
    );

    // Run year-by-year calculation
    const years: YearlyResult[] = [];
    let buyCumulativeCost = inputs.purchase.downPayment;
    let rentCumulativeCost = inputs.rental.securityDeposit;
    // Both households start with the same cash and fund the same monthly budget.
    const initialCash = Math.max(inputs.purchase.downPayment, inputs.rental.securityDeposit);
    let buySavings = subtract(initialCash, inputs.purchase.downPayment);
    let rentSavings = subtract(initialCash, inputs.rental.securityDeposit);
    let buyContributions = buySavings;
    let rentContributions = rentSavings;

    for (let year = 1; year <= inputs.purchase.loanDuration; year++) {
      const yearResult = this.calculateYear(
        year,
        inputs,
        borrowedAmount,
        monthlyPayment,
        inputs.purchase.propertyPrice,
        buyCumulativeCost,
        rentCumulativeCost,
        buySavings,
        rentSavings,
        buyContributions,
        rentContributions
      );

      years.push(yearResult);

      // Update running totals for next iteration
      buyCumulativeCost = yearResult.buy.cumulativeCost;
      rentCumulativeCost = yearResult.rent.cumulativeCost;
      buySavings = yearResult.buy.savings ?? 0;
      rentSavings = yearResult.rent.savings;
      buyContributions = yearResult.buy.savingsContributions ?? 0;
      rentContributions = yearResult.rent.savingsContributions ?? 0;
    }

    // Compile final results
    const results = this.compileResults(years, inputs, borrowedAmount, monthlyPayment, totalPrice);

    const endTime = performance.now();
    if (import.meta.env.DEV && import.meta.env.MODE !== 'test') {
      console.log(`Buy/Rent calculation completed in ${(endTime - startTime).toFixed(2)}ms`);
    }

    return results;
  }

  /**
   * Calculate a single year in the simulation
   * REQ-1.5.2
   *
   * @param year - Year number (1-based)
   * @param inputs - Input parameters
   * @param borrowedAmount - Initial borrowed amount
   * @param monthlyPayment - Monthly mortgage payment
   * @param initialPropertyPrice - Initial property price
   * @param previousBuyCumulativeCost - Previous year's cumulative buy cost
   * @param previousRentCumulativeCost - Previous year's cumulative rent cost
   * @param previousSavings - Previous year's savings
   * @param previousPropertyValue - Previous year's property value
   * @returns Yearly calculation result
   */
  private static calculateYear(
    year: number,
    inputs: BuyRentInputs,
    borrowedAmount: number,
    monthlyPayment: number,
    initialPropertyPrice: number,
    previousBuyCumulativeCost: number,
    previousRentCumulativeCost: number,
    previousBuySavings: number,
    previousRentSavings: number,
    previousBuyContributions: number,
    previousRentContributions: number
  ): YearlyResult {
    // Calculate inflation coefficient for this year
    const inflationCoeff = pow(add(1, divide(inputs.market.inflation, 100)), year);

    // Calculate remaining capital at end of this year
    const monthsElapsed = year * 12;
    const remainingCapital = calculateRemainingCapital(
      borrowedAmount,
      inputs.purchase.interestRate,
      monthlyPayment,
      monthsElapsed
    );

    // Calculate annual buy costs
    const buyCostDetails = this.calculateAnnualBuyCosts(
      inputs.purchase,
      monthlyPayment,
      year,
      inflationCoeff
    );
    const annualBuyCost = sum(Object.values(buyCostDetails));
    const buyCumulativeCost = add(previousBuyCumulativeCost, annualBuyCost);

    // Update property value with appreciation
    const propertyValue = calculateAppreciatedValue(
      add(initialPropertyPrice, inputs.purchase.renovationAmount),
      inputs.market.priceEvolution,
      year
    );

    // Calculate minimum resale price
    const minimumResalePrice = calculateMinimumResalePrice(
      buyCumulativeCost,
      remainingCapital,
      inputs.resale.desiredProfit,
      inputs.resale.resaleFeesPercent
    );

    // Calculate rent with evolution
    const loyerAnnuel = multiply(
      multiply(inputs.rental.monthlyRent, 12),
      pow(add(1, divide(inputs.market.rentEvolution, 100)), subtract(year, 1))
    );
    const chargesAnnuelles = multiply(multiply(inputs.rental.monthlyCharges, 12), inflationCoeff);
    const taxeOrduresAnnuelle = multiply(inputs.rental.garbageTax, inflationCoeff);
    const assuranceLocativeAnnuelle = multiply(inputs.rental.rentalInsurance, inflationCoeff);

    const annualRentCost = sum([
      loyerAnnuel,
      chargesAnnuelles,
      assuranceLocativeAnnuelle,
      taxeOrduresAnnuelle,
    ]);
    const rentCumulativeCost = add(previousRentCumulativeCost, annualRentCost);

    // Rebalance each year's budget as rents and ownership costs evolve.
    const buyDeposit = Math.max(0, subtract(annualRentCost, annualBuyCost));
    const rentDeposit = Math.max(0, subtract(annualBuyCost, annualRentCost));
    const buySavings = calculateCompoundInterest(
      previousBuySavings,
      inputs.market.investmentReturn,
      1,
      divide(buyDeposit, 12)
    );
    const savings = calculateCompoundInterest(
      previousRentSavings,
      inputs.market.investmentReturn,
      1,
      divide(rentDeposit, 12)
    );
    const buyContributions = add(previousBuyContributions, buyDeposit);
    const rentContributions = add(previousRentContributions, rentDeposit);
    const saleFees = divide(multiply(propertyValue, inputs.resale.resaleFeesPercent), 100);
    const buyNetWorth = sum([propertyValue, -remainingCapital, -saleFees, buySavings]);
    // A refundable deposit remains the renter's asset, without earning investment returns.
    const rentNetWorth = add(savings, inputs.rental.securityDeposit);

    return {
      year,
      buy: {
        annualCost: annualBuyCost,
        cumulativeCost: buyCumulativeCost,
        propertyValue,
        remainingCapital,
        minimumResalePrice,
        details: buyCostDetails,
        savings: buySavings,
        savingsContributions: buyContributions,
        netWorth: buyNetWorth,
        netExpense: subtract(add(buyCumulativeCost, buyContributions), buyNetWorth),
      },
      rent: {
        annualCost: annualRentCost,
        cumulativeCost: rentCumulativeCost,
        savings,
        savingsContributions: rentContributions,
        netWorth: rentNetWorth,
        netExpense: subtract(add(rentCumulativeCost, rentContributions), rentNetWorth),
      },
    };
  }

  /**
   * Calculate annual buy costs breakdown
   * REQ-1.1.4, REQ-1.1.5
   *
   * @param purchase - Purchase input parameters
   * @param monthlyPayment - Monthly mortgage payment
   * @param year - Current year number
   * @param inflationCoeff - Inflation coefficient for this year
   * @returns Detailed annual costs
   */
  private static calculateAnnualBuyCosts(
    purchase: BuyRentInputs['purchase'],
    monthlyPayment: number,
    year: number,
    inflationCoeff: number
  ): BuyCostDetails {
    // Check for new property tax exemption (first 2 years)
    const isTaxExempt = purchase.isNewProperty && year <= NEW_PROPERTY_TAX_EXEMPTION_YEARS;

    return {
      mortgage: multiply(monthlyPayment, 12),
      insurance: divide(purchase.totalInsurance, purchase.loanDuration),
      applicationFees: divide(purchase.applicationFees, purchase.loanDuration),
      guaranteeFees: divide(purchase.guaranteeFees, purchase.loanDuration),
      accountFees: divide(purchase.accountFees, purchase.loanDuration),
      propertyTax: isTaxExempt ? 0 : multiply(purchase.propertyTax, inflationCoeff),
      coOwnershipCharges: multiply(purchase.coOwnershipCharges, inflationCoeff),
      maintenance: divide(multiply(purchase.propertyPrice, purchase.maintenancePercent), 100),
      homeInsurance: multiply(purchase.homeInsurance, inflationCoeff),
      bankFees: purchase.bankFees,
      garbageTax: multiply(purchase.garbageTax, inflationCoeff),
    };
  }

  /**
   * Compile final results from yearly calculations
   * REQ-1.6.x
   *
   * @param years - Array of yearly results
   * @param inputs - Original inputs
   * @param borrowedAmount - Amount borrowed
   * @param monthlyPayment - Monthly payment
   * @param totalPrice - Total property price
   * @returns Complete BuyRentResults
   */
  private static compileResults(
    years: YearlyResult[],
    _inputs: BuyRentInputs,
    borrowedAmount: number,
    _monthlyPayment: number,
    _totalPrice: number
  ): BuyRentResults {
    const lastYear = years[years.length - 1];
    const totalMonths = years.length * 12;

    // Calculate total credit cost
    const totalCreditCost = subtract(
      years.reduce(
        (total, year) => add(add(total, year.buy.details.mortgage), year.buy.details.insurance),
        0
      ),
      borrowedAmount
    );

    // Build buy scenario summary
    const buySummary: BuyScenarioSummary = {
      averageMonthlyCost: divide(lastYear.buy.cumulativeCost, totalMonths),
      totalCost: lastYear.buy.cumulativeCost,
      finalPropertyValue: lastYear.buy.propertyValue,
      netExpense: lastYear.buy.netExpense ?? 0,
      remainingCapital: lastYear.buy.remainingCapital,
      netWorth: lastYear.buy.netWorth ?? 0,
      accumulatedSavings: lastYear.buy.savings ?? 0,
      totalCreditCost,
    };

    // Build rent scenario summary
    const rentSummary: RentScenarioSummary = {
      averageMonthlyCost: divide(lastYear.rent.cumulativeCost, totalMonths),
      totalCost: lastYear.rent.cumulativeCost,
      accumulatedSavings: lastYear.rent.savings,
      netExpense: lastYear.rent.netExpense ?? 0,
      netWorth: lastYear.rent.netWorth ?? lastYear.rent.savings,
    };

    // Build comparison metrics
    const netWorthDifference = subtract(buySummary.netWorth, rentSummary.netWorth);
    const netExpenseDifference = subtract(rentSummary.netExpense, buySummary.netExpense);
    const monthlyGap = subtract(rentSummary.averageMonthlyCost, buySummary.averageMonthlyCost);

    const comparison: ComparisonMetrics = {
      netWorthDifference,
      netExpenseDifference,
      monthlyGap,
      winner:
        Math.abs(netWorthDifference) < 0.005 ? 'tie' : netWorthDifference > 0 ? 'buy' : 'rent',
    };

    return {
      years,
      summary: {
        buy: buySummary,
        rent: rentSummary,
        comparison,
      },
    };
  }

  /**
   * Calculate analysis for a specific year N
   * REQ-1.6.6
   *
   * @param results - Complete simulation results
   * @param targetYear - Year to analyze
   * @returns Analysis for that year or null if invalid
   */
  static calculateYearNAnalysis(
    results: BuyRentResults,
    targetYear: number,
    resaleFeesPercent = 0
  ): YearNAnalysis | null {
    if (!Number.isInteger(targetYear) || targetYear < 1 || targetYear > results.years.length) {
      return null;
    }

    const yearData = results.years[targetYear - 1];
    const patrimoineNetAchat =
      yearData.buy.netWorth ?? subtract(yearData.buy.propertyValue, yearData.buy.remainingCapital);

    return {
      year: targetYear,
      propertyValue: yearData.buy.propertyValue,
      remainingCapital: yearData.buy.remainingCapital,
      netWorth: patrimoineNetAchat,
      totalCostsBuy: yearData.buy.cumulativeCost,
      totalCostsRent: yearData.rent.cumulativeCost,
      netExpenseBuy: yearData.buy.netExpense ?? 0,
      netExpenseRent: yearData.rent.netExpense ?? 0,
      annualProfitability: annualCashFlowReturn(results, targetYear, resaleFeesPercent),
      minimumResalePrice: yearData.buy.minimumResalePrice,
      rentSavings: yearData.rent.netWorth ?? yearData.rent.savings,
      buySavings: yearData.buy.savings ?? 0,
    };
  }

  /**
   * Run rental investment simulation
   * REQ-2.5.x
   *
   * @param inputs - Investment input parameters
   * @returns Results for all tax regimes
   */
  static calculateInvestment(inputs: InvestmentInputs): InvestmentResults {
    const startTime = performance.now();

    const results = calculateAllRegimes(inputs);

    const endTime = performance.now();
    if (import.meta.env.DEV && import.meta.env.MODE !== 'test') {
      console.log(`Investment calculation completed in ${(endTime - startTime).toFixed(2)}ms`);
    }

    return results;
  }

  /**
   * Calculate derived values for display (real-time updates)
   * REQ-3.1.2
   *
   * @param inputs - Buy/Rent inputs
   * @returns Derived values for display
   */
  static calculateDerivedValues(inputs: BuyRentInputs): {
    totalPrice: number;
    borrowedAmount: number;
    monthlyPayment: number;
    minimumDownPayment: number;
    suggestedMonthlySavings: number;
  } {
    const totalPrice = calculateTotalPrice(inputs.purchase);
    const borrowedAmount = calculateBorrowedAmount(totalPrice, inputs.purchase.downPayment);
    const monthlyPayment = calculateMonthlyPayment(
      borrowedAmount,
      inputs.purchase.interestRate,
      inputs.purchase.loanDuration
    );

    // Minimum down payment = upfront fees that can't be financed
    const minimumDownPayment = sum([
      inputs.purchase.applicationFees,
      inputs.purchase.guaranteeFees,
      inputs.purchase.accountFees,
    ]);

    // Calculate suggested monthly savings
    const inflation = add(1, divide(inputs.market.inflation, 100));
    const monthlyBuyCost = divide(
      sum(
        Object.values(this.calculateAnnualBuyCosts(inputs.purchase, monthlyPayment, 1, inflation))
      ),
      12
    );

    const monthlyRentCost = add(
      add(inputs.rental.monthlyRent, multiply(inputs.rental.monthlyCharges, inflation)),
      divide(multiply(add(inputs.rental.rentalInsurance, inputs.rental.garbageTax), inflation), 12)
    );

    const suggestedMonthlySavings = Math.max(0, subtract(monthlyBuyCost, monthlyRentCost));

    return {
      totalPrice,
      borrowedAmount,
      monthlyPayment,
      minimumDownPayment,
      suggestedMonthlySavings,
    };
  }

  /**
   * Check if target resale year is valid
   *
   * @param inputs - Buy/Rent inputs
   * @returns True if valid
   */
  static isValidResaleYear(inputs: BuyRentInputs): boolean {
    return inputs.resale.targetYear > 0 && inputs.resale.targetYear <= inputs.purchase.loanDuration;
  }

  /**
   * Get recommendation based on comparison results
   *
   * @param results - Simulation results
   * @returns Recommendation message
   */
  static getRecommendation(
    results: BuyRentResults,
    baseCurrency: string = DEFAULT_CURRENCY
  ): string {
    const { comparison } = results.summary;

    if (comparison.winner === 'tie')
      return 'Les deux scénarios aboutissent au même patrimoine net.';
    if (comparison.winner === 'buy') {
      return `L'achat est plus avantageux avec un patrimoine net supérieur de ${formatCurrency(Math.abs(comparison.netWorthDifference), baseCurrency)} après ${results.years.length} ans.`;
    } else {
      return `La location est plus avantageuse avec une économie nette de ${formatCurrency(Math.abs(comparison.netExpenseDifference), baseCurrency)} après ${results.years.length} ans.`;
    }
  }

  /**
   * Export results to CSV format
   *
   * @param results - Simulation results
   * @returns CSV string
   */
  static exportToCSV(results: BuyRentResults): string {
    const headers = [
      'Année',
      'Coût annuel achat',
      'Coût cumulé achat',
      'Valeur du bien',
      'Capital restant',
      'Prix revente min',
      'Coût annuel location',
      'Coût cumulé location',
      'Épargne cumulée',
    ];

    const rows = results.years.map(year => [
      year.year,
      year.buy.annualCost,
      year.buy.cumulativeCost,
      year.buy.propertyValue,
      year.buy.remainingCapital,
      year.buy.minimumResalePrice,
      year.rent.annualCost,
      year.rent.cumulativeCost,
      year.rent.savings,
    ]);

    return [headers.join(';'), ...rows.map(row => row.join(';'))].join('\n');
  }
}

export default RealEstateCalculationService;

/** Annual IRR of down payment, year-end ownership costs and net sale proceeds. */
function annualCashFlowReturn(
  results: BuyRentResults,
  year: number,
  resaleFeesPercent: number
): number | null {
  const years = results.years.slice(0, year);
  const initial = subtract(years[0].buy.cumulativeCost, years[0].buy.annualCost);
  const last = years[years.length - 1];
  const proceeds = subtract(
    multiply(last.buy.propertyValue, 1 - resaleFeesPercent / 100),
    last.buy.remainingCapital
  );
  const cashFlows = [-initial, ...years.map(row => -row.buy.annualCost)];
  cashFlows[cashFlows.length - 1] += proceeds;
  if (!cashFlows.some(value => value < 0) || !cashFlows.some(value => value > 0)) return null;
  const npv = (rate: number): number =>
    cashFlows.reduce((total, value, index) => total + value / (1 + rate) ** index, 0);
  let low = -0.9999;
  let high = 1;
  while (npv(high) > 0 && high < 1e6) high *= 2;
  if (npv(low) < 0 || npv(high) > 0) return null;
  for (let iteration = 0; iteration < 150; iteration++) {
    const middle = (low + high) / 2;
    if (npv(middle) > 0) low = middle;
    else high = middle;
  }
  const result = ((low + high) / 2) * 100;
  return Math.abs(result) < 1e-8 ? 0 : result;
}
