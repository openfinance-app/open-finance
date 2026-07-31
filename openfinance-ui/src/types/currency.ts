/**
 * Currency types for multi-currency support
 * Sprint 6 - Task 6.2: Multi-Currency Support
 */

/**
 * Currency information from backend
 */
export type CurrencyType = 'FIAT' | 'CRYPTO';

export interface Currency {
  code: string;
  name: string;
  symbol: string;
  isActive: boolean;
  nameKey: string; // i18n key for translated currency name (e.g., "currency.usd")
  type: CurrencyType;
}

/**
 * Exchange rate information
 */
export interface ExchangeRate {
  baseCurrency: string;
  targetCurrency: string;
  rate: number;
  inverseRate: number;
  rateDate: string;
  source: string;
}

/**
 * Currency conversion request
 */
export interface ConvertRequest {
  amount: number;
  fromCurrency: string;
  toCurrency: string;
  date?: string; // Optional - defaults to current date
}

/**
 * Currency conversion response
 */
export interface ConvertResponse {
  fromCurrency: string;
  toCurrency: string;
  originalAmount: number;
  convertedAmount: number;
  exchangeRate: number;
}

/**
 * Update exchange rates response
 */
export interface UpdateRatesResponse {
  message: string;
  updatedCount: number;
}
