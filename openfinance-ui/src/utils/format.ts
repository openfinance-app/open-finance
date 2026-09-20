/**
 * Number formatting utilities for financial data display
 * Follows Finary-style formatting (e.g., "1 048 396 €")
 *
 * Note: Most components should prefer the richer `formatCurrency` from
 * `@/utils/currency` which supports NumberFormatContext preferences.
 * These helpers remain for backward-compat and simple use-cases.
 */

import { DEFAULT_CURRENCY, getCurrencyDecimals } from './currency';
import i18n from '@/i18n';

/** Use the same persisted number preference as monetary amounts. */
function numberLocale(): string | undefined {
  const locales: Record<string, string> = {
    '1,234.56': 'en-US',
    '1.234,56': 'de-DE',
    '1 234,56': 'fr-FR',
  };
  try {
    const preference = localStorage.getItem('open_finance_number_format');
    if (preference && locales[preference]) return locales[preference];
  } catch {
    // Fall back to the active language when storage is unavailable.
  }
  return i18n.language || undefined;
}

/**
 * Format currency with proper thousand separators.
 * Uses fr-FR style as the display default (space thousands, comma decimal).
 * Example: formatCurrency(1048396, 'EUR') => "1 048 396 €"
 */
export interface FormatOptions {
  compact?: boolean;
}

export function formatCurrency(amount: number, currency?: string, options?: FormatOptions): string {
  // Default to the app default currency if none provided
  const actualCurrency = currency ?? DEFAULT_CURRENCY;
  const formatter = new Intl.NumberFormat('fr-FR', {
    style: 'currency',
    currency: actualCurrency,
    notation: options?.compact ? 'compact' : 'standard',
    minimumFractionDigits: 0,
    maximumFractionDigits: getCurrencyDecimals(actualCurrency),
  });

  return formatter.format(amount);
}

/**
 * Format percentage with specified decimal places, in the active UI locale.
 * Example: formatPercentage(72.45, 2) => "72,45%" (fr), "72.45%" (en)
 */
export function formatPercentage(value: number, decimals = 2): string {
  return `${formatDecimal(value, decimals)}%`;
}

/**
 * Locale-aware decimal formatting without any unit suffix — for templates that
 * supply their own (e.g. dashboard's "{{percent}}% of total").
 * Example: formatDecimal(72.45, 1) => "72,45" (fr), "72.45" (en)
 */
export function formatDecimal(value: number, decimals = 2): string {
  return new Intl.NumberFormat(numberLocale(), {
    minimumFractionDigits: decimals,
    maximumFractionDigits: decimals,
  }).format(value);
}

/**
 * Format number with thousand separators.
 * Uses fr-FR style as the display default (space thousands, comma decimal).
 * Example: formatNumber(1048396) => "1 048 396"
 */
export function formatNumber(value: number): string {
  return new Intl.NumberFormat('fr-FR', {
    minimumFractionDigits: 0,
    maximumFractionDigits: 2,
  }).format(value);
}

/**
 * Format compact currency (K, M, B suffixes)
 * Example: formatCompactCurrency(1048396, 'EUR') => "1.05M €"
 */
export function formatCompactCurrency(amount: number, currency: string): string {
  const formatter = new Intl.NumberFormat('fr-FR', {
    style: 'currency',
    currency: currency,
    notation: 'compact',
    minimumFractionDigits: 0,
    maximumFractionDigits: getCurrencyDecimals(currency),
  });

  return formatter.format(amount);
}

/**
 * Get CSS class for gain/loss color coding
 * Returns 'gain-positive' or 'gain-negative' based on value
 */
export function getGainLossClass(value: number): string {
  return value >= 0 ? 'gain-positive' : 'gain-negative';
}

/**
 * Format gain/loss with sign and percentage
 * Example: formatGainLoss(25385, 72.45, 'EUR') => "+25 385 € +72.45%"
 */
export function formatGainLoss(amount: number, percentage: number, currency: string): string {
  const sign = amount >= 0 ? '+' : '';
  const formattedAmount = formatCurrency(Math.abs(amount), currency);
  const formattedPercentage = formatPercentage(Math.abs(percentage), 2);

  return `${sign}${formattedAmount} ${sign}${formattedPercentage}`;
}
