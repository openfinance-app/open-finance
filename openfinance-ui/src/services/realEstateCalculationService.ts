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
    let currentSavings = inputs.rental.initialSavings || inputs.purchase.downPayment;
    let currentPropertyValue = inputs.purchase.propertyPrice + inputs.purchase.renovationAmount;

    for (let year = 1; year <= inputs.purchase.loanDuration; year++) {
      const yearResult = this.calculateYear(
        year,
        inputs,
        borrowedAmount,
        monthlyPayment,
        totalPrice,
        buyCumulativeCost,
        rentCumulativeCost,
        currentSavings,
        currentPropertyValue
      );

      years.push(yearResult);

      // Update running totals for next iteration
      buyCumulativeCost = yearResult.buy.cumulativeCost;
      rentCumulativeCost = yearResult.rent.cumulativeCost;
      currentSavings = yearResult.rent.savings;
      currentPropertyValue = yearResult.buy.propertyValue;
    }

    // Compile final results
    const results = this.compileResults(years, inputs, borrowedAmount, monthlyPayment, totalPrice);
    
    const endTime = performance.now();
    console.log(`Buy/Rent calculation completed in ${(endTime - startTime).toFixed(2)}ms`);
    
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
    previousSavings: number,
    _previousPropertyValue: number
  ): YearlyResult {
    // Calculate inflation coefficient for this year
    const inflationCoeff = Math.pow(1 + inputs.market.inflation / 100, year);

    // Calculate remaining capital at end of this year
    const monthsElapsed = year * 12;
    const remainingCapital = calculateRemainingCapital(
      borrowedAmount,
      inputs.purchase.interestRate,
      monthlyPayment,
      monthsElapsed
    );

    // Calculate annual buy costs
    const buyCostDetails = this.calculateAnnualBuyCosts(inputs.purchase, monthlyPayment, year, inflationCoeff);
    const annualBuyCost = Object.values(buyCostDetails).reduce((sum, cost) => sum + cost, 0);
    const buyCumulativeCost = previousBuyCumulativeCost + annualBuyCost;

    // Update property value with appreciation
    const propertyValue = calculateAppreciatedValue(
      initialPropertyPrice + inputs.purchase.renovationAmount,
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
    const loyerAnnuel = inputs.rental.monthlyRent * 12 * 
      Math.pow(1 + inputs.market.rentEvolution / 100, year - 1);
    const chargesAnnuelles = inputs.rental.monthlyCharges * 12 * inflationCoeff;
    const taxeOrduresAnnuelle = inputs.rental.garbageTax * inflationCoeff;
    const assuranceLocativeAnnuelle = inputs.rental.rentalInsurance * inflationCoeff;

    const annualRentCost = loyerAnnuel + chargesAnnuelles + assuranceLocativeAnnuelle + taxeOrduresAnnuelle;
    const rentCumulativeCost = previousRentCumulativeCost + annualRentCost;

    // Calculate savings growth
    const savings = calculateCompoundInterest(
      previousSavings,
      inputs.market.investmentReturn,
      1,
      inputs.rental.monthlySavings
    );

    return {
      year,
      buy: {
        annualCost: annualBuyCost,
        cumulativeCost: buyCumulativeCost,
        propertyValue,
        remainingCapital,
        minimumResalePrice,
        details: buyCostDetails,
      },
      rent: {
        annualCost: annualRentCost,
        cumulativeCost: rentCumulativeCost,
        savings,
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
      mortgage: monthlyPayment * 12,
      insurance: purchase.totalInsurance / purchase.loanDuration,
      applicationFees: purchase.applicationFees / purchase.loanDuration,
      guaranteeFees: purchase.guaranteeFees / purchase.loanDuration,
      accountFees: purchase.accountFees / purchase.loanDuration,
      propertyTax: isTaxExempt ? 0 : purchase.propertyTax * inflationCoeff,
      coOwnershipCharges: purchase.coOwnershipCharges * inflationCoeff,
      maintenance: (purchase.propertyPrice * purchase.maintenancePercent) / 100,
      homeInsurance: purchase.homeInsurance * inflationCoeff,
      bankFees: purchase.bankFees,
      garbageTax: purchase.garbageTax * inflationCoeff,
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
    const totalCreditCost = years.reduce((total, year) => {
      return total + year.buy.details.mortgage + year.buy.details.insurance;
    }, 0) - borrowedAmount;

    // Build buy scenario summary
    const buySummary: BuyScenarioSummary = {
      averageMonthlyCost: lastYear.buy.cumulativeCost / totalMonths,
      totalCost: lastYear.buy.cumulativeCost,
      finalPropertyValue: lastYear.buy.propertyValue,
      netExpense: lastYear.buy.cumulativeCost - lastYear.buy.propertyValue,
      remainingCapital: lastYear.buy.remainingCapital,
      netWorth: lastYear.buy.propertyValue - lastYear.buy.remainingCapital,
      totalCreditCost,
    };

    // Build rent scenario summary
    const rentSummary: RentScenarioSummary = {
      averageMonthlyCost: lastYear.rent.cumulativeCost / totalMonths,
      totalCost: lastYear.rent.cumulativeCost,
      accumulatedSavings: lastYear.rent.savings,
      netExpense: lastYear.rent.cumulativeCost - lastYear.rent.savings,
      netWorth: lastYear.rent.savings,
    };

    // Build comparison metrics
    const netWorthDifference = buySummary.netWorth - rentSummary.netWorth;
    const netExpenseDifference = rentSummary.netExpense - buySummary.netExpense;
    const monthlyGap = rentSummary.averageMonthlyCost - buySummary.averageMonthlyCost;

    const comparison: ComparisonMetrics = {
      netWorthDifference,
      netExpenseDifference,
      monthlyGap,
      winner: netWorthDifference > 0 ? 'buy' : 'rent',
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
    targetYear: number
  ): YearNAnalysis | null {
    if (targetYear < 1 || targetYear > results.years.length) {
      return null;
    }

    const yearData = results.years[targetYear - 1];
    const patrimoineNetAchat = yearData.buy.propertyValue - yearData.buy.remainingCapital;

    return {
      year: targetYear,
      propertyValue: yearData.buy.propertyValue,
      remainingCapital: yearData.buy.remainingCapital,
      netWorth: patrimoineNetAchat,
      totalCostsBuy: yearData.buy.cumulativeCost,
      totalCostsRent: yearData.rent.cumulativeCost,
      netExpenseBuy: yearData.buy.cumulativeCost - yearData.buy.propertyValue,
      netExpenseRent: yearData.rent.cumulativeCost - yearData.rent.savings,
      annualProfitability: (Math.pow(
        yearData.buy.propertyValue / (yearData.buy.propertyValue - yearData.buy.remainingCapital + yearData.buy.cumulativeCost),
        1 / targetYear
      ) - 1) * 100,
      minimumResalePrice: yearData.buy.minimumResalePrice,
      rentSavings: yearData.rent.savings,
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
    console.log(`Investment calculation completed in ${(endTime - startTime).toFixed(2)}ms`);
    
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
    const minimumDownPayment = 
      inputs.purchase.applicationFees + 
      inputs.purchase.guaranteeFees + 
      inputs.purchase.accountFees;

    // Calculate suggested monthly savings
    const monthlyBuyCost = monthlyPayment + 
      (inputs.purchase.propertyTax + 
       inputs.purchase.coOwnershipCharges + 
       inputs.purchase.homeInsurance + 
       inputs.purchase.bankFees + 
       inputs.purchase.garbageTax) / 12;
    
    const monthlyRentCost = inputs.rental.monthlyRent + 
      inputs.rental.monthlyCharges + 
      (inputs.rental.rentalInsurance + inputs.rental.garbageTax) / 12;
    
    const suggestedMonthlySavings = Math.max(0, monthlyBuyCost - monthlyRentCost);

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
    return inputs.resale.targetYear > 0 && 
           inputs.resale.targetYear <= inputs.purchase.loanDuration;
  }

  /**
   * Get recommendation based on comparison results
   * 
   * @param results - Simulation results
   * @returns Recommendation message
   */
  static getRecommendation(results: BuyRentResults, baseCurrency: string = DEFAULT_CURRENCY): string {
    const { comparison } = results.summary;
    
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

    return [
      headers.join(';'),
      ...rows.map(row => row.join(';')),
    ].join('\n');
  }
}

export default RealEstateCalculationService;
