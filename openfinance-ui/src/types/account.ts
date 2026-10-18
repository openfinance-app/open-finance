/**
 * Account-related types
 */

export type AccountType = 'CHECKING' | 'SAVINGS' | 'CREDIT_CARD' | 'INVESTMENT' | 'CASH' | 'OTHER';

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

export const INTEREST_VALUE_DATE_MODES = [
  'TRANSACTION_DATE',
  'FORTNIGHT',
  'DAYS_1',
  'DAYS_2',
  'DAYS_3',
  'DAYS_4',
  'DAYS_5',
] as const;
export type InterestValueDateMode = (typeof INTEREST_VALUE_DATE_MODES)[number];
export const INTEREST_DAY_COUNT_BASES = ['ACTUAL_365', 'THIRTY_360', 'FORTNIGHT_24'] as const;
export type InterestDayCountBasis = (typeof INTEREST_DAY_COUNT_BASES)[number];
export interface InterestEstimate {
  /** Net interest for January–December of the current year. */
  estimate: number;
  /** Net interest from January 1 through today. */
  yearToDate: number;
  /** Net interest from account opening through today. */
  historicalAccumulated: number;
}

export interface InterestRateVariation {
  creditValueDateMode?: InterestValueDateMode;
  debitValueDateMode?: InterestValueDateMode;
  dayCountBasis?: InterestDayCountBasis;
  exactValues?: Record<string, string>;
  interestProduced?: number;
  activeDays?: number;
  id: number;
  accountId: number;
  rate: number;
  taxRate?: number;
  validFrom: string;
  createdAt: string;
  updatedAt?: string;
}

export interface InterestRateVariationRequest {
  creditValueDateMode?: InterestValueDateMode;
  debitValueDateMode?: InterestValueDateMode;
  dayCountBasis?: InterestDayCountBasis;
  rate: number | string;
  taxRate?: number | string;
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
  exactValues?: Record<string, string>;
  id: number;
  userId: number;
  name: string;
  accountNumber?: string;
  type: AccountType;
  currency: string;
  balance: number;
  /** Account's own balance excluding linked assets — use for edit forms. */
  ownBalance: number;
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
  adjustBalance?: boolean;
  balanceCurrency?: string;
  description?: string;
  /** ISO date (yyyy-MM-dd) when the account was opened. */
  openingDate?: string;
  institutionId?: number;
  isInterestEnabled?: boolean;
  interestPeriod?: InterestPeriod;
  interestRate?: number | string;
  taxRate?: number | string;
}
