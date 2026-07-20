/**
 * Currency formatting and handling utilities
 * Task 2.2.14: Add currency formatting utility
 * Task 6.2.12: Enhanced currency formatting with multi-currency support
 * Task 6.3.15: Number format preferences (1,234.56 / 1.234,56 / 1 234,56)
 * Task 1.4.4 (i18n): Currency names resolved via i18n; useCurrencyName hook added
 */

import { useTranslation } from 'react-i18next';
import type { NumberFormat } from '@/context/NumberFormatContext';

/**
 * Common currencies with their symbols and English name fallbacks.
 *
 * The `name` field here is an **English fallback** used in non-React contexts.
 * In React components, resolve the localised name with:
 *   const { t } = useTranslation('common');
 *   t(`currency.${code}`, { defaultValue: name })
 * or use the `useCurrencyName()` hook below.
 *
 * NOTE: This is a fallback list. The app fetches currencies from backend API.
 */
export const CURRENCIES = [
  { code: 'USD', symbol: '$', name: 'US Dollar' },
  { code: 'EUR', symbol: '€', name: 'Euro' },
  { code: 'GBP', symbol: '£', name: 'British Pound' },
  { code: 'JPY', symbol: '¥', name: 'Japanese Yen' },
  { code: 'CHF', symbol: 'CHF', name: 'Swiss Franc' },
  { code: 'CAD', symbol: 'C$', name: 'Canadian Dollar' },
  { code: 'AUD', symbol: 'A$', name: 'Australian Dollar' },
  { code: 'CNY', symbol: '¥', name: 'Chinese Yuan' },
  { code: 'BTC', symbol: '₿', name: 'Bitcoin' },
  { code: 'ETH', symbol: 'Ξ', name: 'Ethereum' },
] as const;

export type CurrencyCode = typeof CURRENCIES[number]['code'];

/**
 * Application-wide default/fallback currency (ISO 4217).
 *
 * Single source of truth for the currency used when neither an explicit currency nor the user's
 * `baseCurrency` is available. Set to EUR to match the backend's default base currency
 * (`application.exchange-rates.base-currency`). Wherever a React context is available, prefer the
 * authenticated user's `baseCurrency` (via `useAuthContext()`) over this constant.
 */
export const DEFAULT_CURRENCY = 'EUR';

/**
 * Currencies that use zero decimal places
 */
const ZERO_DECIMAL_CURRENCIES = ['JPY', 'KRW', 'VND', 'CLP', 'ISK'];

/**
 * Cryptocurrencies that use 8 decimal places
 */
const CRYPTO_CURRENCIES = ['BTC', 'ETH', 'BNB', 'ADA', 'SOL', 'DOT', 'AVAX', 'MATIC', 'LINK', 'UNI'];

/**
 * Currencies where symbol comes after the amount
 */
const SYMBOL_AFTER_CURRENCIES = ['CHF', 'SEK', 'NOK', 'DKK'];

/**
 * Get currency symbol from code
 */
export function getCurrencySymbol(code: string | null | undefined): string {
  code = code || DEFAULT_CURRENCY;
  const currency = CURRENCIES.find(c => c.code === code);
  return currency?.symbol ?? code;
}

/**
 * Get currency name from code (English fallback — non-React contexts).
 * In React components, prefer `useCurrencyName()` for a localised name.
 */
export function getCurrencyName(code: string | null | undefined): string {
  code = code || DEFAULT_CURRENCY;
  const currency = CURRENCIES.find(c => c.code === code);
  return currency?.name ?? code;
}

/**
 * React hook that returns a locale-aware currency name resolver.
 * Falls back to the English `name` field in `CURRENCIES` when the key is missing.
 *
 * Usage:
 *   const getCurrencyNameI18n = useCurrencyName();
 *   return <span>{getCurrencyNameI18n('USD')}</span>;
 */
export function useCurrencyName(): (code: string | null | undefined) => string {
  const { t } = useTranslation('common');
  return (code: string | null | undefined) => {
    const resolvedCode = code || DEFAULT_CURRENCY;
    const fallback = getCurrencyName(resolvedCode);
    return t(`currency.${resolvedCode}`, { defaultValue: fallback });
  };
}

/**
 * Validate if a currency code is supported
 */
export function isValidCurrency(code: string | null | undefined): boolean {
  if (!code) return false;
  return CURRENCIES.some(c => c.code === code);
}

/**
 * Get the number of decimal places for a currency
 * 
 * @param currencyCode - Currency code (e.g., 'USD', 'BTC')
 * @returns Number of decimal places (0 for JPY, 8 for crypto, 2 for most)
 */
export function getCurrencyDecimals(currencyCode: string | null | undefined): number {
  currencyCode = currencyCode || DEFAULT_CURRENCY;
  if (ZERO_DECIMAL_CURRENCIES.includes(currencyCode.toUpperCase())) {
    return 0;
  }
  if (CRYPTO_CURRENCIES.includes(currencyCode.toUpperCase())) {
    return 8;
  }
  return 2; // Default for most fiat currencies
}

/**
 * Check if a currency is a cryptocurrency
 */
export function isCryptoCurrency(currencyCode: string | null | undefined): boolean {
  currencyCode = currencyCode || DEFAULT_CURRENCY;
  return CRYPTO_CURRENCIES.includes(currencyCode.toUpperCase());
}

/**
 * Format options for currency formatting
 */
export interface FormatCurrencyOptions {
  /** Show in compact format (1.5K, 2.3M, etc.) */
  compact?: boolean;
  /** Show currency symbol (default: true) */
  showSymbol?: boolean;
  /** Override default decimal places */
  decimals?: number;
  /** Show positive sign for positive numbers */
  showPositiveSign?: boolean;
  /** Use accounting format (parentheses for negative) */
  accounting?: boolean;
  /**
   * Number format preference controlling thousand/decimal separators.
   * When omitted the default '1,234.56' (US/UK) style is used.
   *   '1,234.56'  — comma thousands, dot decimal   (en-US)
   *   '1.234,56'  — dot thousands, comma decimal   (de-DE / European)
   *   '1 234,56'  — space thousands, comma decimal (fr-FR / French)
   */
  numberFormat?: NumberFormat;
}

// ---------------------------------------------------------------------------
// Number format helpers
// ---------------------------------------------------------------------------

/**
 * Map from our NumberFormat preference strings to the matching BCP-47 locale
 * used by Intl.NumberFormat to produce the correct separators.
 * (Kept for reference; actual conversion uses applyNumberFormat token replacement.)
 */
const _NUMBER_FORMAT_TO_LOCALE: Record<NumberFormat, string> = {
  '1,234.56': 'en-US',
  '1.234,56': 'de-DE',
  '1 234,56': 'fr-FR',
};
void _NUMBER_FORMAT_TO_LOCALE; // suppress unused-variable warning

/**
 * Apply the user's number format preference to a raw numeric string produced
 * by Intl.NumberFormat('en-US', …).
 *
 * Instead of re-formatting the entire number (which would re-run the locale
 * logic), this helper converts only the separator characters so we don't
 * have to change every call-site.
 *
 * Conversion table:
 *   en-US (default)  →  no change
 *   de-DE            →  swap ',' ↔ '.'   (1,234.56 → 1.234,56)
 *   fr-FR            →  ',' → ' ', '.' → ','  (1,234.56 → 1 234,56)
 *
 * We use a placeholder token approach to avoid double-substitution:
 *   1. replace ',' with TOKEN_THOUSANDS
 *   2. replace '.' with TOKEN_DECIMAL
 *   3. replace tokens with target separators
 */
const TOKEN_THOUSANDS = '\x00T\x00';
const TOKEN_DECIMAL = '\x00D\x00';

export function applyNumberFormat(formattedEnUS: string, fmt: NumberFormat | undefined): string {
  if (!fmt || fmt === '1,234.56') return formattedEnUS; // already en-US

  // Tokenise the existing en-US separators
  const tokenised = formattedEnUS
    .replace(/,/g, TOKEN_THOUSANDS)
    .replace(/\./g, TOKEN_DECIMAL);

  if (fmt === '1.234,56') {
    return tokenised
      .replace(new RegExp(TOKEN_THOUSANDS, 'g'), '.')
      .replace(new RegExp(TOKEN_DECIMAL, 'g'), ',');
  }

  // '1 234,56'  (fr-FR style — narrow non-breaking space \u202F or plain space)
  return tokenised
    .replace(new RegExp(TOKEN_THOUSANDS, 'g'), '\u202F')
    .replace(new RegExp(TOKEN_DECIMAL, 'g'), ',');
}

/**
 * Format a number as currency with proper symbol and formatting
 * 
 * Handles all currency-specific rules:
 * - JPY, KRW: 0 decimals
 * - BTC, ETH: 8 decimals
 * - CHF, SEK: symbol after amount
 * - Negative amounts: proper sign handling
 * - Compact mode: 1.5K, 2.3M, 1.2B
 * 
 * @param amount - The numeric amount to format
 * @param currencyCode - The currency code (e.g., 'USD', 'EUR', 'BTC')
 * @param options - Formatting options
 * @returns Formatted currency string (e.g., "$1,234.56", "€1.234,56", "₿0.00123456")
 */
export function formatCurrency(
  amount: number,
  currencyCode: string | null | undefined = DEFAULT_CURRENCY,
  options: FormatCurrencyOptions = {}
): string {
  currencyCode = currencyCode || DEFAULT_CURRENCY;
  const {
    compact = false,
    showSymbol = true,
    decimals,
    showPositiveSign = false,
    accounting = false,
    numberFormat,
  } = options;

  // Handle invalid numbers
  if (!isFinite(amount)) {
    return showSymbol ? `${getCurrencySymbol(currencyCode)}0.00` : '0.00';
  }

  // Determine decimal places
  const decimalPlaces = decimals ?? getCurrencyDecimals(currencyCode);

  // Format in compact mode
  if (compact) {
    return formatCurrencyCompact(amount, currencyCode, { showSymbol, decimals: decimalPlaces, numberFormat });
  }

  // Format number with locale-appropriate separators (always en-US base, then convert)
  const absAmount = Math.abs(amount);
  const formattedNumber = applyNumberFormat(
    new Intl.NumberFormat('en-US', {
      minimumFractionDigits: decimalPlaces,
      maximumFractionDigits: decimalPlaces,
    }).format(absAmount),
    numberFormat
  );

  // Handle sign
  let sign = '';
  if (amount < 0) {
    sign = accounting ? '(' : '-';
  } else if (amount > 0 && showPositiveSign) {
    sign = '+';
  }

  // Get symbol
  const symbol = showSymbol ? getCurrencySymbol(currencyCode) : '';

  // Position symbol based on currency
  const symbolAfter = SYMBOL_AFTER_CURRENCIES.includes(currencyCode.toUpperCase());

  // Build formatted string
  let formatted: string;
  if (accounting && amount < 0) {
    // Accounting format: ($1,234.56)
    formatted = symbolAfter
      ? `(${formattedNumber} ${symbol})`
      : `(${symbol}${formattedNumber})`;
  } else {
    formatted = symbolAfter
      ? `${sign}${formattedNumber} ${symbol}`
      : `${sign}${symbol}${formattedNumber}`;
  }

  return formatted.trim();
}

/**
 * Format currency in compact notation (1.5K, 2.3M, 1.2B)
 * 
 * @param amount - The numeric amount to format
 * @param currencyCode - The currency code
 * @param options - Formatting options
 * @returns Compact formatted string (e.g., "$1.5K", "€2.3M", "₿1.2B")
 */
export function formatCurrencyCompact(
  amount: number,
  currencyCode: string | null | undefined = DEFAULT_CURRENCY,
  options: { showSymbol?: boolean; decimals?: number; numberFormat?: NumberFormat } = {}
): string {
  currencyCode = currencyCode || DEFAULT_CURRENCY;
  const { showSymbol = true, decimals = 1, numberFormat } = options;

  // Handle invalid numbers
  if (!isFinite(amount)) {
    return showSymbol ? `${getCurrencySymbol(currencyCode)}0` : '0';
  }

  const absAmount = Math.abs(amount);
  const sign = amount < 0 ? '-' : '';
  const symbol = showSymbol ? getCurrencySymbol(currencyCode) : '';
  const symbolAfter = SYMBOL_AFTER_CURRENCIES.includes(currencyCode.toUpperCase());

  let value: number;
  let suffix: string;

  if (absAmount >= 1e9) {
    value = absAmount / 1e9;
    suffix = 'B';
  } else if (absAmount >= 1e6) {
    value = absAmount / 1e6;
    suffix = 'M';
  } else if (absAmount >= 1e3) {
    value = absAmount / 1e3;
    suffix = 'K';
  } else {
    // For small amounts, show full value with proper decimals
    const decimalPlaces = getCurrencyDecimals(currencyCode);
    value = absAmount;
    suffix = '';
    const formatted = applyNumberFormat(value.toFixed(decimalPlaces), numberFormat);
    return symbolAfter
      ? `${sign}${formatted} ${symbol}`.trim()
      : `${sign}${symbol}${formatted}`;
  }

  // Format with specified decimals; apply number format to decimal separator only
  // (compact suffixes like K/M/B don't have thousands separators)
  const rawFormatted = value.toFixed(decimals);
  // For compact, only the decimal separator matters — apply format conversion
  const formatted = applyNumberFormat(rawFormatted, numberFormat);

  return symbolAfter
    ? `${sign}${formatted}${suffix} ${symbol}`.trim()
    : `${sign}${symbol}${formatted}${suffix}`;
}

/**
 * Parse a currency string to a number
 * 
 * Removes currency symbols, commas, and other formatting
 * 
 * @param value - Currency string (e.g., "$1,234.56", "€1.234,56")
 * @returns Parsed number or null if invalid
 */
export function parseCurrency(value: string): number | null {
  if (!value || typeof value !== 'string') {
    return null;
  }

  // Remove all non-numeric characters except decimal point, comma, minus, and parentheses
  let cleaned = value
    .replace(/[^\d.,\-()]/g, '') // Keep digits, dot, comma, minus, parentheses
    .replace(/,/g, ''); // Remove commas (thousand separators)

  // Handle accounting format with parentheses
  if (cleaned.startsWith('(') && cleaned.endsWith(')')) {
    cleaned = '-' + cleaned.slice(1, -1);
  }

  const parsed = parseFloat(cleaned);
  return isFinite(parsed) ? parsed : null;
}

/**
 * Format a currency amount with color coding based on positive/negative
 * 
 * @param amount - Amount to format
 * @param currencyCode - Currency code
 * @param options - Formatting options
 * @returns Object with formatted string and CSS class
 */
export function formatCurrencyWithColor(
  amount: number,
  currencyCode: string | null | undefined = DEFAULT_CURRENCY,
  options: FormatCurrencyOptions = {}
): { formatted: string; className: string } {
  currencyCode = currencyCode || DEFAULT_CURRENCY;
  const formatted = formatCurrency(amount, currencyCode, {
    ...options,
    showPositiveSign: true,
  });

  let className: string;
  if (amount > 0) {
    className = 'text-green-500';
  } else if (amount < 0) {
    className = 'text-red-500';
  } else {
    className = 'text-text-muted';
  }

  return { formatted, className };
}

/**
 * Formats an exchange rate number for display with enough precision to be
 * accurate regardless of magnitude.
 *
 * Uses 6 significant figures so that small rates like 0.00176385510712862
 * render as "0.00176386" rather than being truncated to "0.0018" (toFixed(4))
 * or "0.00176" (toPrecision(6) without adequate sig-fig handling).
 *
 * Examples:
 *   0.00176385510712862  → "0.00176386"
 *   1.08321              → "1.08321"
 *   95000.12             → "95000.1"
 *   1.0                  → "1"
 */
export function formatExchangeRate(rate: number): string {
  if (!isFinite(rate) || rate === 0) return '0';
  // toPrecision gives 6 significant figures; parseFloat strips trailing zeros.
  return parseFloat(rate.toPrecision(6)).toString();
}
