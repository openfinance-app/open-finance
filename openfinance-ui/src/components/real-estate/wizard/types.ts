/**
 * Shared types of the buy-property wizard steps (Task 9).
 */

/** How the purchase is financed. */
export type FundingSource = 'new' | 'existing' | 'none';

/** Where the bank sends the loan funds. */
export type DisbursementRoute = 'direct' | 'account';

/** Step 1 state: property identity and valuation. */
export interface PropertyStepState {
  name: string;
  address: string;
  propertyType: string;
  purchasePrice: string;
  purchaseDate: string;
  currentValue: string;
  currency: string;
}

/** Step 2 state: funding, disbursement route and optional down payment. */
export interface FundingStepState {
  source: FundingSource;
  mortgageName: string;
  loanAmount: string;
  interestRate: string;
  existingMortgageId?: number;
  route: DisbursementRoute;
  downPaymentAmount: string;
  downPaymentAccountId?: number;
}

export const PROPERTY_TYPE_OPTIONS = [
  'RESIDENTIAL',
  'COMMERCIAL',
  'LAND',
  'MIXED_USE',
  'INDUSTRIAL',
  'OTHER',
];
