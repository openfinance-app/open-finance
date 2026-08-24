/**
 * Account-related types
 */

export type AccountType =
  | 'CHECKING'
  | 'SAVINGS'
  | 'CREDIT_CARD'
  | 'INVESTMENT'
  | 'CASH'
  | 'OTHER';

export type InterestPeriod = 'ANNUAL' | 'HALF_YEARLY' | 'QUARTERLY' | 'MONTHLY' | 'DAILY';

/**
 * Account filters for search
 */
export interface AccountFilters {
  keyword?: string;
  /** When true, `keyword` is treated as a regular expression instead of a plain substring. */
  keywordRegex?: boolean;
  type?: AccountType;
  currency?: string;
  isActive?: boolean;
  balanceMin?: number;
  balanceMax?: number;
  institution?: string;
  /** When true, filter to show only accounts with a low (≤ 0) balance */
  lowBalance?: boolean;
  // Pagination
  page?: number;
  size?: number;
  sort?: string;
}

export interface InterestRateVariation {
  id: number;
  accountId: number;
  rate: number;
  taxRate?: number;
  validFrom: string;
  createdAt: string;
  updatedAt?: string;
}

export interface InterestRateVariationRequest {
  rate: number;
  taxRate?: number;
  validFrom: string;
}

/**
 * Institution information for accounts
 */
export interface InstitutionInfo {
  id: number;
  name: string;
  bic?: string;
  country?: string;
  logo?: string;
}

export interface Account {
  id: number;
  userId: number;
  name: string;
  accountNumber?: string;
  type: AccountType;
  currency: string;
  balance: number;
  description?: string;
  /** ISO date (yyyy-MM-dd) when the account was opened. */
  openingDate?: string;
  isActive: boolean;
  createdAt: string;
  updatedAt?: string;
  institution?: InstitutionInfo;
  isInterestEnabled?: boolean;
  interestPeriod?: InterestPeriod;
  // Requirement REQ-2.1: Currency conversion fields
  balanceInBaseCurrency?: number;
  baseCurrency?: string;
  exchangeRate?: number;
  isConverted?: boolean;
  // Requirement REQ-3.5: Secondary currency conversion fields
  balanceInSecondaryCurrency?: number;
  secondaryCurrency?: string;
  secondaryExchangeRate?: number;
}

export interface AccountRequest {
  name: string;
  accountNumber?: string;
  type: AccountType;
  currency: string;
  /** Exact decimal string as entered by the user (e.g. "6322.1899") — never a JS number. */
  initialBalance: string;
  description?: string;
  /** ISO date (yyyy-MM-dd) when the account was opened. */
  openingDate?: string;
  institutionId?: number;
  isInterestEnabled?: boolean;
  interestPeriod?: InterestPeriod;
  interestRate?: number;
  taxRate?: number;
}
