/**
 * Real Estate Tools Type Definitions
 *
 * TypeScript interfaces for Buy/Rent Comparator and Property Rental Investment Simulator
 * Requirements: REQ-1.1.x, REQ-1.2.x, REQ-2.1.x, REQ-2.2.x, REQ-2.3.x
 */

// ============================================
// Buy/Rent Comparator Types
// ============================================

/**
 * Purchase input parameters
 * REQ-1.1.1, REQ-1.1.2, REQ-1.1.3, REQ-1.1.4
 */
export interface PurchaseInputs {
  /** Property price in EUR (default: 300000) */
  propertyPrice: number;
  /** Renovation work amount in EUR (default: 0) */
  renovationAmount: number;
  /** New property flag - affects tax exemption (default: false) */
  isNewProperty: boolean;
  /** Notary fees percentage (default: 7) */
  notaryFeesPercent: number;
  /** Agency fees in EUR (default: 0) */
  agencyFees: number;

  // Financing
  /** Personal down payment in EUR (default: 60000) */
  downPayment: number;
  /** Loan duration in years (default: 25) */
  loanDuration: number;
  /** Annual interest rate TAEG % (default: 4.2) */
  interestRate: number;

  // Insurance & Fees
  /** Total insurance cost over loan duration in EUR (default: 12900) */
  totalInsurance: number;
  /** Application fees in EUR (default: 2000) */
  applicationFees: number;
  /** Guarantee fees in EUR (default: 2750) */
  guaranteeFees: number;
  /** Account maintenance fees in EUR (default: 720) */
  accountFees: number;

  // Recurring charges
  /** Annual property tax in EUR (default: 2000) */
  propertyTax: number;
  /** Annual co-ownership charges in EUR (default: 1200) */
  coOwnershipCharges: number;
  /** Annual maintenance percentage (default: 1) */
  maintenancePercent: number;
  /** Annual home insurance in EUR (default: 600) */
  homeInsurance: number;
  /** Additional annual bank fees in EUR (default: 0) */
  bankFees: number;
  /** Annual garbage tax in EUR (default: 150) */
  garbageTax: number;
}

/**
 * Rental input parameters
 * REQ-1.2.1
 */
export interface RentalInputs {
  /** Monthly rent excluding charges in EUR (default: 1200) */
  monthlyRent: number;
  /** Monthly rental charges in EUR (default: 100) */
  monthlyCharges: number;
  /** Security deposit in EUR (default: 1200) */
  securityDeposit: number;
  /** Annual rental insurance in EUR (default: 200) */
  rentalInsurance: number;
  /** Annual garbage tax in EUR (default: 150) */
  garbageTax: number;
  /** Initial savings amount in EUR (default: 0) */
  initialSavings: number;
  /** Additional monthly savings in EUR (default: 0) */
  monthlySavings: number;
}

/**
 * Market evolution parameters
 * REQ-1.3.1
 */
export interface MarketInputs {
  /** Annual property price evolution % (default: 2) */
  priceEvolution: number;
  /** Annual rent evolution % (default: 2) */
  rentEvolution: number;
  /** Investment return rate % (default: 4) */
  investmentReturn: number;
  /** Annual inflation rate % (default: 2) */
  inflation: number;
}

/**
 * Resale target parameters
 * REQ-1.4.1
 */
export interface ResaleInputs {
  /** Target resale year (default: 10) */
  targetYear: number;
  /** Desired net profit in EUR (default: 50000) */
  desiredProfit: number;
  /** Resale fees percentage (default: 8) */
  resaleFeesPercent: number;
}

/**
 * Complete Buy/Rent input data
 */
export interface BuyRentInputs {
  purchase: PurchaseInputs;
  rental: RentalInputs;
  market: MarketInputs;
  resale: ResaleInputs;
}

// ============================================
// Calculation Result Types
// ============================================

/**
 * Buy scenario annual cost breakdown
 */
export interface BuyCostDetails {
  mortgage: number;
  insurance: number;
  applicationFees: number;
  guaranteeFees: number;
  accountFees: number;
  propertyTax: number;
  coOwnershipCharges: number;
  maintenance: number;
  homeInsurance: number;
  bankFees: number;
  garbageTax: number;
}

/**
 * Single year calculation result
 * REQ-1.5.2
 */
export interface YearlyResult {
  year: number;
  buy: {
    annualCost: number;
    cumulativeCost: number;
    propertyValue: number;
    remainingCapital: number;
    minimumResalePrice: number;
    details: BuyCostDetails;
  };
  rent: {
    annualCost: number;
    cumulativeCost: number;
    savings: number;
  };
}

/**
 * Buy scenario summary
 * REQ-1.6.1
 */
export interface BuyScenarioSummary {
  averageMonthlyCost: number;
  totalCost: number;
  finalPropertyValue: number;
  netExpense: number;
  remainingCapital: number;
  netWorth: number;
  totalCreditCost: number;
}

/**
 * Rent scenario summary
 * REQ-1.6.2
 */
export interface RentScenarioSummary {
  averageMonthlyCost: number;
  totalCost: number;
  accumulatedSavings: number;
  netExpense: number;
  netWorth: number;
}

/**
 * Comparison metrics
 * REQ-1.6.3
 */
export interface ComparisonMetrics {
  netWorthDifference: number;
  netExpenseDifference: number;
  monthlyGap: number;
  winner: 'buy' | 'rent';
}

/**
 * Complete Buy/Rent calculation results
 * REQ-1.6.x
 */
export interface BuyRentResults {
  years: YearlyResult[];
  summary: {
    buy: BuyScenarioSummary;
    rent: RentScenarioSummary;
    comparison: ComparisonMetrics;
  };
}

/**
 * Analysis for specific year N
 * REQ-1.6.6
 */
export interface YearNAnalysis {
  year: number;
  propertyValue: number;
  remainingCapital: number;
  netWorth: number;
  totalCostsBuy: number;
  totalCostsRent: number;
  netExpenseBuy: number;
  netExpenseRent: number;
  annualProfitability: number;
  minimumResalePrice: number;
  rentSavings: number;
}

// ============================================
// Rental Simulator Types
// ============================================

/**
 * Furnishing type options
 * REQ-2.1.2
 */
export type FurnishingType = 'unfurnished' | 'basic' | 'standard' | 'luxury';

/**
 * Investment property inputs
 * REQ-2.1.x
 */
export interface InvestmentPropertyInputs {
  /** Total property price (inherited from comparator) */
  totalPrice: number;
  /** Furnishing type selection */
  furnishingType: FurnishingType;
  /** Furniture value in EUR (auto-set based on type) */
  furnitureValue: number;
}

/**
 * Rental revenue inputs
 * REQ-2.2.x
 */
export interface RentalRevenueInputs {
  /** Monthly rent excluding charges in EUR (default: 900) */
  monthlyRent: number;
  /** Monthly recoverable charges in EUR (default: 100) */
  recoverableCharges: number;
  /** Occupancy rate % (default: 95) */
  occupancyRate: number;
  /** Bad debt rate % (default: 1) */
  badDebtRate: number;
}

/**
 * Owner expenses inputs
 * REQ-2.3.x
 */
export interface OwnerExpensesInputs {
  /** Annual property tax in EUR (inherited) */
  propertyTax: number;
  /** Annual non-recoverable co-ownership charges in EUR (inherited) */
  nonRecoverableCharges: number;
  /** Annual maintenance work in EUR (default: 2000) */
  annualMaintenance: number;
  /** Annual CFE (business property tax) in EUR (default: 1500) */
  cfe: number;
  /** Annual CVAE (corporate value added tax) in EUR (default: 0) */
  cvae: number;
  /** Annual property management fees in EUR (default: 800) */
  managementFees: number;
  /** Annual PNO insurance in EUR (default: 300) */
  pnoInsurance: number;
  /** Annual accounting fees in EUR (default: 600) */
  accountingFees: number;
  /** Marginal tax rate TMI % (default: 30) */
  marginalTaxRate: number;
}

/**
 * Credit information for investment
 */
export interface InvestmentCreditInfo {
  monthlyPayment: number;
  annualCost: number;
  totalCost: number;
  assurance: number;
  bankFees: number;
}

/**
 * Complete investment inputs
 */
export interface InvestmentInputs {
  credit: InvestmentCreditInfo;
  property: InvestmentPropertyInputs;
  revenue: RentalRevenueInputs;
  expenses: OwnerExpensesInputs;
}

// ============================================
// Tax Regime Types
// ============================================

/**
 * Tax regime types
 * REQ-2.4.x
 */
export type TaxRegime = 'micro_foncier' | 'reel_foncier' | 'lmnp_reel' | 'micro_bic';

/**
 * Regime calculation result
 * REQ-2.6.1
 */
export interface RegimeCalculationResult {
  regime: TaxRegime;
  eligible: boolean;
  investment: {
    totalPrice: number;
    annualCreditCost: number;
    monthlyCreditPayment: number;
    detail: {
      credit: number;
      assurance: number;
      fraisBancaires: number;
    };
  };
  revenue: {
    gross: number;
    net: number;
    deduction: number;
    taxable: number;
    detail: {
      loyers: number;
      chargesRecup: number;
    };
  };
  charges: {
    total: number;
    credit: number;
    other: number;
  };
  taxation: {
    regime: TaxRegime;
    incomeTax: number;
    socialContributions: number;
    totalTaxes: number;
  };
  performance: {
    monthlyCashFlow: number;
    grossYield: number;
    netYield: number;
  };
  details: {
    isEligible: boolean;
    depreciation: number;
    warnings: string[];
  };
}

/**
 * Complete investment results for all regimes
 * REQ-2.6.x
 */
export interface InvestmentResults {
  microFoncier: RegimeCalculationResult;
  reelFoncier: RegimeCalculationResult;
  lmnpReel: RegimeCalculationResult;
  microBic: RegimeCalculationResult;
}

// ============================================
// Simulation Storage Types
// ============================================

/**
 * Simulation type discriminator
 */
export type SimulationType = 'buy_rent' | 'rental_investment';

/**
 * Simulation metadata
 * REQ-1.7.x, REQ-3.4.x
 */
export interface SimulationMetadata {
  id: string;
  name: string;
  type: SimulationType;
  createdAt: Date;
  updatedAt: Date;
}

/**
 * Saved simulation storage format
 */
export interface SavedSimulation {
  metadata: SimulationMetadata;
  data: BuyRentInputs | InvestmentInputs;
}

// ============================================
// Shared Data Between Tools
// ============================================

/**
 * Data passed from Buy/Rent to Rental Simulator
 * REQ-4.2.1
 */
export interface SharedPropertyData {
  totalPrice: number;
  credit: InvestmentCreditInfo;
  propertyTax: number;
  coOwnershipCharges: number;
}

// ============================================
// UI/Component Types
// ============================================

/**
 * Input field props for real estate inputs
 */
export interface RealEstateInputProps {
  id: string;
  label: string;
  value: number;
  onChange: (value: number) => void;
  disabled?: boolean;
  readOnly?: boolean;
  error?: string;
  helperText?: string;
  min?: number;
  max?: number;
  step?: number;
  suffix?: string;
  prefix?: string;
}

/**
 * Validation error
 * REQ-5.x
 */
export interface ValidationError {
  field: string;
  message: string;
}

/**
 * Section card props
 */
export interface SectionCardProps {
  title: string;
  children: React.ReactNode;
  variant?: 'purchase' | 'rental' | 'market' | 'resale' | 'investment' | 'revenue' | 'expenses';
  defaultExpanded?: boolean;
}

// ============================================
// Constants
// ============================================

/**
 * Default values for Buy/Rent Comparator
 * Based on immo-simmul/achat-loc.js
 */
export const DEFAULT_BUY_RENT_INPUTS: BuyRentInputs = {
  purchase: {
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
  },
  rental: {
    monthlyRent: 1200,
    monthlyCharges: 100,
    securityDeposit: 1200,
    rentalInsurance: 200,
    garbageTax: 150,
    initialSavings: 0,
    monthlySavings: 0,
  },
  market: {
    priceEvolution: 2,
    rentEvolution: 2,
    investmentReturn: 4,
    inflation: 2,
  },
  resale: {
    targetYear: 10,
    desiredProfit: 50000,
    resaleFeesPercent: 8,
  },
};

/**
 * Default values for Rental Simulator
 */
export const DEFAULT_INVESTMENT_INPUTS: Omit<InvestmentInputs, 'credit'> = {
  property: {
    totalPrice: 0,
    furnishingType: 'unfurnished',
    furnitureValue: 0,
  },
  revenue: {
    monthlyRent: 900,
    recoverableCharges: 100,
    occupancyRate: 95,
    badDebtRate: 1,
  },
  expenses: {
    propertyTax: 2000,
    nonRecoverableCharges: 1200,
    annualMaintenance: 2000,
    cfe: 1500,
    cvae: 0,
    managementFees: 800,
    pnoInsurance: 300,
    accountingFees: 600,
    marginalTaxRate: 30,
  },
};

/**
 * Furniture values by type
 */
export const FURNITURE_VALUES: Record<FurnishingType, number> = {
  unfurnished: 0,
  basic: 5000,
  standard: 10000,
  luxury: 20000,
};

/**
 * Tax regime limits
 * REQ-2.4.x
 */
export const REGIME_LIMITS = {
  MICRO_FONCIER: 15000,
  MICRO_BIC: 77700,
  LMNP_SOCIAL_THRESHOLD: 23000,
} as const;

/**
 * Tax regime rates
 * REQ-2.4.x
 */
export const REGIME_RATES = {
  MICRO_FONCIER_ABATEMENT: 0.3,
  MICRO_BIC_ABATEMENT: 0.5,
  SOCIAL_CONTRIBUTIONS_STANDARD: 0.172,
  SOCIAL_CONTRIBUTIONS_LMP: 0.45,
  BUILDING_DEPRECIATION_YEARS: 25,
  FURNITURE_DEPRECIATION_YEARS: 5,
} as const;

/**
 * New property tax exemption years
 * REQ-1.1.5
 */
export const NEW_PROPERTY_TAX_EXEMPTION_YEARS = 2;
