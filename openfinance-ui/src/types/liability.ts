/**
 * Liability Type Definitions
 * Task 6.2: Frontend implementation for Liability Management
 */

export type LiabilityType =
  | 'MORTGAGE'
  | 'LOAN'
  | 'CREDIT_CARD'
  | 'STUDENT_LOAN'
  | 'AUTO_LOAN'
  | 'PERSONAL_LOAN'
  | 'OTHER';

export interface Liability {
  id: number;
  name: string;
  type: LiabilityType;
  principal: number;
  currentBalance: number;
  interestRate?: number;
  startDate: string; // ISO date string
  endDate?: string; // ISO date string
  minimumPayment?: number;
  currency: string;
  notes?: string;
  institution?: {
    id: number;
    name: string;
    bic?: string;
    country?: string;
    logo?: string;
  };
  linkedPropertyId?: number;
  linkedPropertyName?: string;
  // Requirement 1.1: Insurance percentage and additional fees fields
  insurancePercentage?: number;
  additionalFees?: number;
  // Requirement 1.2: Computed cost fields
  monthlyInsuranceCost?: number;
  totalInsuranceCost?: number;
  totalCost?: number;
  principalPaid?: number;
  interestPaid?: number;
  createdAt: string;
  updatedAt: string;

  // Requirement REQ-2.3: Currency conversion fields
  balanceInBaseCurrency?: number;
  baseCurrency?: string;
  exchangeRate?: number;
  isConverted?: boolean;
  // Requirement REQ-3.5: Secondary currency conversion fields
  balanceInSecondaryCurrency?: number;
  secondaryCurrency?: string;
  secondaryExchangeRate?: number;
}

export interface LiabilityRequest {
  name: string;
  type: LiabilityType;
  // Exact decimal strings as entered by the user — never JS numbers.
  principal: string;
  currentBalance: string;
  interestRate?: number;
  startDate: string; // ISO date string (YYYY-MM-DD)
  endDate?: string; // ISO date string (YYYY-MM-DD)
  minimumPayment?: string;
  currency: string;
  notes?: string;
  institutionId?: number;
  // Requirement 1.1: Insurance percentage and additional fees fields
  insurancePercentage?: number;
  additionalFees?: string;
  /** Optional real estate property ID to link this mortgage to on creation */
  realEstateId?: number;
}

export interface AmortizationPayment {
  paymentNumber: number;
  paymentDate: string; // ISO date string
  paymentAmount: number;
  principalPayment: number;
  interestPayment: number;
  remainingBalance: number;
}

export interface AmortizationSchedule {
  liabilityId: number;
  liabilityName: string;
  principal: number;
  interestRate: number;
  termMonths: number;
  monthlyPayment: number;
  totalPayments: number;
  totalInterest: number;
  totalAmount: number;
  currency: string;
  payments: AmortizationPayment[];
}

export interface LiabilityTotals {
  [currency: string]: {
    totalLiabilities: number;
    totalPrincipal: number;
    totalInterest: number;
    averageInterestRate: number;
    count: number;
  };
}

// UI Filter state
export interface LiabilityFilters {
  type?: LiabilityType;
  search?: string;
  page?: number;
  size?: number;
  sort?: string;
}

/**
 * Requirement 2.1: Detailed cost breakdown response for a liability.
 * Returned by GET /api/v1/liabilities/{id}/breakdown
 */
export interface LiabilityBreakdown {
  liabilityId: number;
  name: string;
  currency: string;
  institution?: {
    id: number;
    name: string;
    bic?: string;
    country?: string;
    logo?: string;
  };
  // Balances
  currentBalance: number;
  principal: number;
  principalPaid: number;
  // Amounts paid to date (from linked transactions)
  interestPaid: number;
  insurancePaid: number;
  feesPaid: number;
  totalPaid: number;
  // Projections
  projectedInterest: number;
  projectedInsurance: number;
  projectedFees: number;
  totalProjectedCost: number;
  // Linked transactions summary
  linkedTransactionCount: number;
  linkedTransactionsTotalAmount: number;
}
