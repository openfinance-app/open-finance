/**
 * Private currency formatting utilities
 * 
 * Wraps currency formatting with privacy blur functionality and the user's
 * number format preference from NumberFormatContext.
 */
import { useNumberFormat } from '@/context/NumberFormatContext';
import { PrivateAmount } from '@/components/ui/PrivateAmount';
import { DEFAULT_CURRENCY, formatCurrency, type FormatCurrencyOptions } from './currency';

/**
 * Hook-based private currency formatter that respects the user's number format.
 *
 * Returns a render function — call it inside a React component to get a JSX
 * element with privacy blur + correct number separators.
 *
 * @example
 *   const { formatPrivate } = usePrivateCurrencyFormatter();
 *   return <>{formatPrivate(1234.56, 'USD')}</>;
 */
export function usePrivateCurrencyFormatter() {
  const { numberFormat } = useNumberFormat();

  const formatPrivate = (
    amount: number,
    currencyCode: string = DEFAULT_CURRENCY,
    options: FormatCurrencyOptions = {}
  ) => {
    const formatted = formatCurrency(amount, currencyCode, { numberFormat, ...options });
    return (
      <PrivateAmount inline>
        {formatted}
      </PrivateAmount>
    );
  };

  return { formatPrivate };
}

/**
 * Format currency with privacy blur wrapper.
 *
 * @deprecated Prefer `usePrivateCurrencyFormatter` hook which automatically
 * applies the user's number format preference. This function uses the default
 * en-US format (1,234.56) and cannot access React context.
 *
 * @param amount - The numeric amount to format
 * @param currencyCode - The currency code (e.g., 'USD', 'EUR', 'BTC')
 * @param options - Formatting options
 * @returns JSX element with formatted currency wrapped in PrivateAmount
 */
export function formatPrivateCurrency(
  amount: number,
  currencyCode: string = DEFAULT_CURRENCY,
  options: FormatCurrencyOptions = {}
) {
  const formatted = formatCurrency(amount, currencyCode, options);
  
  return (
    <PrivateAmount inline>
      {formatted}
    </PrivateAmount>
  );
}
