/**
 * Private currency formatting utilities
 *
 * Wraps currency formatting with privacy blur functionality and the user's
 * number format preference from NumberFormatContext.
 */
import { useFormatCurrency } from '@/hooks/useFormatCurrency';
import { PrivateAmount } from '@/components/ui/PrivateAmount';
import { DEFAULT_CURRENCY, type MonetaryValue, type FormatCurrencyOptions } from '@/utils/currency';

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
  const { format } = useFormatCurrency();

  const formatPrivate = (
    amount: MonetaryValue,
    currencyCode: string = DEFAULT_CURRENCY,
    options: FormatCurrencyOptions = {}
  ) => {
    const formatted = format(amount, currencyCode, options);
    return <PrivateAmount inline>{formatted}</PrivateAmount>;
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
  amount: MonetaryValue,
  currencyCode: string = DEFAULT_CURRENCY,
  options: FormatCurrencyOptions = {}
) {
  return <PrivateCurrency amount={amount} currency={currencyCode} options={options} />;
}

function PrivateCurrency({
  amount,
  currency,
  options,
}: {
  amount: MonetaryValue;
  currency: string;
  options: FormatCurrencyOptions;
}) {
  const { format } = useFormatCurrency();
  return <PrivateAmount inline>{format(amount, currency, options)}</PrivateAmount>;
}
