/**
 * Financial Freedom Calculator Type Definitions
 *
 * TypeScript interfaces for Financial Freedom and Savings Longevity calculations
 */

/**
 * Input parameters for financial freedom calculation
 */
export interface FreedomCalculatorInput {
  /** Current total savings and investments in EUR */
  currentSavings: number;

  /** Monthly expenses in EUR */
  monthlyExpenses: number;

  /** Expected annual return rate as percentage (e.g., 7 for 7%) */
  expectedAnnualReturn: number;

  /** Monthly contribution to savings in EUR */
  monthlyContribution?: number;

  /** Withdrawal rate for target calculation (default 4%) */
  withdrawalRate?: number;

  /** Expected annual inflation rate as percentage */
  inflationRate?: number;

  /** Whether to include inflation adjustment */
  adjustForInflation?: boolean;
}

/**
 * Result of financial freedom calculation
 */
export interface FreedomCalculatorResult {
  /** Years until financial freedom is achieved */
  yearsToFreedom: number;

  /** Months until financial freedom (decimal) */
  monthsToFreedom: number;

  /** Target savings amount needed */
  targetSavingsAmount: number;

  /** Current progress toward goal as percentage */
  progressPercentage: number;

  /** Current progress amount */
  currentProgress: number;

  /** Estimated annual passive income from investments */
  annualPassiveIncome: number;

  /** Whether savings will last indefinitely */
  isSustainableIndefinitely: boolean;

  /** Whether the goal is achievable within projection period */
  isAchievable: boolean;

  /** Year-by-year projections */
  yearlyProjections: YearlyProjection[];

  /** Sensitivity analysis scenarios */
  sensitivityScenarios: SensitivityScenario[];

  /** Human-readable message about the result */
  message: string;

  /** Withdrawal rate used for the calculation */
  withdrawalRate?: number;
}

/**
 * Individual year projection for display
 */
export interface YearlyProjection {
  /** Year number (0 = current year) */
  year: number;

  /** Starting balance for this year */
  startingBalance: number;

  /** Ending balance after contributions and returns */
  endingBalance: number;

  /** Total contributions made this year */
  contributions: number;

  /** Investment returns this year */
  investmentReturns: number;

  /** Expenses withdrawn this year */
  withdrawals: number;

  /** Cumulative progress toward target */
  progressTowardTarget: number;

  /** Flag indicating if target was reached this year */
  targetReached: boolean;
}

/**
 * Sensitivity analysis scenario
 */
export interface SensitivityScenario {
  /** Description of scenario */
  label: string;

  /** Return rate for this scenario as percentage */
  returnRate: number;

  /** Years to freedom with this scenario */
  yearsToFreedom: number;

  /** Months to freedom with this scenario (0-11) */
  monthsToFreedom: number;

  /** Is this scenario optimistic, pessimistic, or baseline */
  scenarioType: 'optimistic' | 'pessimistic' | 'baseline';
}

/**
 * Savings longevity result
 */
export interface SavingsLongevityResult {
  /** Years savings will last */
  yearsUntilDepletion: number;

  /** Total months until depletion */
  totalMonthsUntilDepletion: number;

  /** Whether savings will last forever */
  isInfinite: boolean;

  /** Year when savings deplete (null if infinite) */
  depletionYear: number | null;

  /** Final balance before depletion (null if infinite) */
  finalBalance: number | null;

  /** Whether savings will deplete within projection period */
  willDeplete: boolean;

  /** Year-by-year depletion analysis */
  depletionProjections: YearlyProjection[];

  /** Current monthly expenses */
  monthlyExpenses: number;

  /** Current savings balance */
  currentSavings: number;

  /** Expected annual return rate used for calculation */
  annualReturnRate: number;

  /** Human-readable message about the result */
  message: string;
}

/**
 * Calculation defaults and limits
 */
export interface CalculationDefaults {
  /** Default withdrawal rate percentage (4% rule) */
  defaultWithdrawalRate: number;

  /** Default inflation rate percentage */
  defaultInflationRate: number;

  /** Minimum allowed withdrawal rate percentage */
  minimumWithdrawalRate: number;

  /** Maximum allowed withdrawal rate percentage */
  maximumWithdrawalRate: number;

  /** Minimum allowed return rate percentage */
  minimumReturnRate: number;

  /** Maximum allowed return rate percentage */
  maximumReturnRate: number;

  /** Default expected annual return rate */
  defaultReturnRate: number;

  /** Maximum number of projection years */
  maxProjectionYears: number;

  /** Default monthly contribution amount */
  defaultMonthlyContribution: number;
}

// ============================================
// Default Values
// ============================================

/**
 * Default values for financial freedom calculator
 */
export const DEFAULT_FREEDOM_CALCULATOR_INPUT: FreedomCalculatorInput = {
  currentSavings: 50000,
  monthlyExpenses: 2500,
  expectedAnnualReturn: 7,
  monthlyContribution: 500,
  withdrawalRate: 4,
  inflationRate: 2.5,
  adjustForInflation: false,
};

/**
 * Default calculation limits
 */
export const CALCULATION_LIMITS = {
  MIN_WITHDRAWAL_RATE: 0.5,
  MAX_WITHDRAWAL_RATE: 10,
  MIN_RETURN_RATE: -10,
  MAX_RETURN_RATE: 30,
  MAX_PROJECTION_YEARS: 50,
  MAX_LONGEVITY_YEARS: 100,
} as const;

// ============================================
// API Request/Response Types
// ============================================

/**
 * API request for timeline calculation
 */
export interface TimelineApiRequest {
  currentSavings: number;
  monthlyExpenses: number;
  expectedAnnualReturn: number;
  monthlyContribution?: number;
  withdrawalRate?: number;
  inflationRate?: number;
  adjustForInflation?: boolean;
}

/**
 * API response for timeline calculation
 */
export interface TimelineApiResponse {
  yearsToFreedom: number;
  monthsToFreedom: number;
  targetSavingsAmount: number;
  progressPercentage: number;
  currentProgress: number;
  annualPassiveIncome: number;
  isSustainableIndefinitely: boolean;
  isAchievable: boolean;
  yearlyProjections: YearlyProjection[];
  sensitivityScenarios: SensitivityScenario[];
  message: string;
}

/**
 * API request for longevity calculation
 */
export interface LongevityApiRequest {
  currentSavings: number;
  monthlyExpenses: number;
  annualReturnRate: number;
}

/**
 * API response for longevity calculation
 */
export interface LongevityApiResponse {
  yearsUntilDepletion: number;
  totalMonthsUntilDepletion: number;
  isInfinite: boolean;
  depletionYear: number | null;
  finalBalance: number | null;
  willDeplete: boolean;
  depletionProjections: YearlyProjection[];
  monthlyExpenses: number;
  currentSavings: number;
  annualReturnRate: number;
  message: string;
}

// ============================================
// Compound Interest Calculator Types
// ============================================

/** Supported compounding frequencies, with their periods-per-year value. */
export type CompoundingFrequency = 1 | 2 | 4 | 12 | 52 | 365;

/** Input parameters for the compound interest calculator. */
export interface CompoundInterestInput {
  /** Initial investment (principal). */
  principal: number;
  /** Annual interest rate as a percentage (e.g. 5 for 5%). */
  annualRate: number;
  /** Number of compounding periods per year. */
  compoundingFrequency: CompoundingFrequency;
  /** Investment duration in years. */
  years: number;
  /** Regular contribution per period. Defaults to 0. */
  regularContribution: number;
  /** Whether contributions are made at the beginning of each period. */
  contributionAtBeginning: boolean;
}

/** Year-by-year breakdown row. */
export interface CompoundInterestYearlyBreakdown {
  year: number;
  startingBalance: number;
  contributions: number;
  interestEarned: number;
  endingBalance: number;
  cumulativeInterest: number;
  cumulativePrincipal: number;
}

/** Full result returned by the compound interest API. */
export interface CompoundInterestResult {
  finalBalance: number;
  principal: number;
  totalContributions: number;
  totalInterest: number;
  totalInvested: number;
  effectiveAnnualRate: number;
  yearlyBreakdown: CompoundInterestYearlyBreakdown[];
}

/** Default input values for the compound interest calculator. */
export const DEFAULT_COMPOUND_INTEREST_INPUT: CompoundInterestInput = {
  principal: 10000,
  annualRate: 7,
  compoundingFrequency: 12,
  years: 10,
  regularContribution: 0,
  contributionAtBeginning: false,
};

// Loan Calculator Types
export interface LoanCalculatorInput {
  principal: number;
  annualRate: number;
  years: number;
}

export interface LoanAmortizationEntry {
  paymentNumber: number;
  paymentAmount: number;
  principalPortion: number;
  interestPortion: number;
  remainingBalance: number;
  cumulativeInterest: number;
}

export interface LoanCalculatorResult {
  monthlyPayment: number;
  totalInterest: number;
  totalPayment: number;
  amortizationSchedule: LoanAmortizationEntry[];
}

export const DEFAULT_LOAN_CALCULATOR_INPUT: LoanCalculatorInput = {
  principal: 200000,
  annualRate: 5.0,
  years: 20,
};

// ============================================
// Early Mortgage Payoff Calculator Types
// ============================================

/** A single one-off lump-sum prepayment applied at a specific month. */
export interface LumpSumPayment {
  id: string;
  /** Month number relative to now (1 = after next regular payment). */
  month: number;
  amount: number;
}

export interface EarlyPayoffInput {
  /** Current outstanding loan balance. */
  loanBalance: number;
  /** Annual nominal interest rate as a percentage (e.g. 4.2 for 4.2 %). */
  annualRate: number;
  /** Remaining full years in the loan term. */
  remainingYears: number;
  /** Extra months beyond the full years (0–11). */
  remainingMonthsExtra: number;
  /** List of planned lump-sum prepayments. */
  lumpSumPayments: LumpSumPayment[];
  /** Fixed extra amount paid every month on top of the scheduled payment. */
  monthlyExtraPayment: number;
}

/** One row of the yearly amortization summary table. */
export interface EarlyPayoffYearRow {
  year: number;
  totalPayment: number;
  principalPaid: number;
  interestPaid: number;
  lumpSum: number;
  ira: number;
  endBalance: number;
}

/** Full result for one payoff scenario (base / reduce-duration / reduce-payment). */
export interface EarlyPayoffScenario {
  /** Base monthly payment at start. */
  monthlyPayment: number;
  /** Final monthly payment (only differs from monthlyPayment in reduce-payment mode). */
  finalMonthlyPayment: number;
  /** Actual number of months until full repayment. */
  totalMonths: number;
  /** Total interest paid over the life of the loan. */
  totalInterest: number;
  /** Total IRA penalties paid (France only). */
  totalIRA: number;
  /** Sum of all lump-sum payments applied. */
  totalLumpSum: number;
  /** Total cost = regular payments + lump sums + IRA. */
  totalCost: number;
  /** Interest saved vs the base (no-prepayment) scenario. */
  interestSaved: number;
  /** Months eliminated vs the base scenario. */
  timeSavedMonths: number;
  /** Net savings = interestSaved − totalIRA. */
  netSavings: number;
  yearlySchedule: EarlyPayoffYearRow[];
}

export interface EarlyPayoffResult {
  base: EarlyPayoffScenario;
  reduceDuration: EarlyPayoffScenario;
  reducePayment: EarlyPayoffScenario;
}

export const DEFAULT_EARLY_PAYOFF_INPUT: EarlyPayoffInput = {
  loanBalance: 200000,
  annualRate: 4.2,
  remainingYears: 20,
  remainingMonthsExtra: 0,
  lumpSumPayments: [{ id: '1', month: 12, amount: 20000 }],
  monthlyExtraPayment: 0,
};
