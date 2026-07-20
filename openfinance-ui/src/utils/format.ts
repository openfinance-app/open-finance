/**
 * Number formatting utilities for financial data display
 * Follows Finary-style formatting (e.g., "1 048 396 €")
 *
 * Note: Most components should prefer the richer `formatCurrency` from
 * `@/utils/currency` which supports NumberFormatContext preferences.
 * These helpers remain for backward-compat and simple use-cases.
 */

import { DEFAULT_CURRENCY } from './currency';

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
    maximumFractionDigits: 2,
  });

  return formatter.format(amount);
}

/**
 * Format percentage with specified decimal places
 * Example: formatPercentage(72.45, 2) => "72.45%"
 */
export function formatPercentage(value: number, decimals = 2): string {
  return `${value.toFixed(decimals)}%`;
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
    maximumFractionDigits: 2,
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
export function formatGainLoss(
  amount: number,
  percentage: number,
  currency: string
): string {
  const sign = amount >= 0 ? '+' : '';
  const formattedAmount = formatCurrency(Math.abs(amount), currency);
  const formattedPercentage = formatPercentage(Math.abs(percentage), 2);

  return `${sign}${formattedAmount} ${sign}${formattedPercentage}`;
}
