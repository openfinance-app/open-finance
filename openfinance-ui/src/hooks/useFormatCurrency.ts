/**
 * useFormatCurrency — convenience hook that reads the user's NumberFormat
 * preference from context and returns a `formatCurrency` wrapper that applies
 * it automatically to every call.
 *
 * Usage:
 *   const { format, formatCompact, formatWithColor } = useFormatCurrency();
 *   format(1234.56, 'USD')          // → "$1,234.56" / "€1.234,56" / "€1 234,56"
 *   formatCompact(1234567, 'EUR')   // → "€1.2M" or "€1,2M" depending on setting
 */
import { useCallback } from 'react';
import { useNumberFormat } from '@/context/NumberFormatContext';
import { useDecimalPlaces } from '@/context/DecimalPlacesContext';
import {
  formatCurrency,
  formatCurrencyCompact,
  formatCurrencyWithColor,
  type FormatCurrencyOptions,
} from '@/utils/currency';

export function useFormatCurrency() {
  const { numberFormat } = useNumberFormat();
  const { effectiveDecimals } = useDecimalPlaces();

  const format = useCallback(
    (amount: number, currencyCode?: string | null, options?: FormatCurrencyOptions): string =>
      formatCurrency(amount, currencyCode, {
        numberFormat,
        ...(effectiveDecimals !== null ? { decimals: effectiveDecimals } : {}),
        ...options,
      }),
    [numberFormat, effectiveDecimals]
  );

  const formatCompact = useCallback(
    (
      amount: number,
      currencyCode?: string | null,
      options?: Omit<FormatCurrencyOptions, 'compact'>
    ): string =>
      formatCurrencyCompact(amount, currencyCode, {
        numberFormat,
        ...(effectiveDecimals !== null ? { decimals: effectiveDecimals } : {}),
        ...options,
      }),
    [numberFormat, effectiveDecimals]
  );

  const formatWithColor = useCallback(
    (
      amount: number,
      currencyCode?: string | null,
      options?: FormatCurrencyOptions
    ): { formatted: string; className: string } =>
      formatCurrencyWithColor(amount, currencyCode, {
        numberFormat,
        ...(effectiveDecimals !== null ? { decimals: effectiveDecimals } : {}),
        ...options,
      }),
    [numberFormat, effectiveDecimals]
  );

  return { format, formatCompact, formatWithColor, numberFormat };
}
