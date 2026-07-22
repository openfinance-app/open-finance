/**
 * useSecondaryConversion — helper hook for secondary-currency tooltip amounts.
 *
 * Reads the user's secondary currency from {@link CurrencyDisplayContext} and
 * fetches the latest exchange rate so callers can pass pre-computed
 * `secondaryAmount` / `secondaryExchangeRate` props to {@link ConvertedAmount}.
 *
 * This hook is intended for components that display amounts already in the
 * user's base currency (e.g. net-worth aggregates, budget totals). Those
 * components set `isConverted={false}` on ConvertedAmount — the matrix row
 * "mode=base, native=base, secondary set" then shows only the secondary
 * tooltip line.
 *
 * Exchange rates are cached by React Query (stale after 15 min). All calls
 * with the same `(from, to)` pair share the same cache entry, so rendering
 * many sibling components (e.g. budget rows) triggers only one network request.
 */
import { useMemo } from 'react';
import { useCurrencyDisplay } from '@/context/CurrencyDisplayContext';
import { useLatestExchangeRate } from '@/hooks/useCurrency';
import { multiply } from '@/utils/money';

export interface SecondaryConversionResult {
    /** ISO 4217 code of the secondary currency, or null when not applicable. */
    secondaryCurrency: string | null;
    /**
     * Exchange rate: 1 unit of the source currency = `secondaryExchangeRate`
     * units of `secondaryCurrency`. Null when rate is unavailable or secondary
     * currency equals the source currency.
     */
    secondaryExchangeRate: number | null;
    /**
     * Converts `amount` (in the source currency) to the secondary currency.
     * Returns null when the secondary currency is not configured, equals the
     * source currency, or the rate has not yet loaded.
     */
    convert: (amount: number | null | undefined) => number | null;
}

/**
 * @param fromCurrency  ISO 4217 code of the amounts' currency (usually the
 *                      user's base currency).  Pass null/undefined when the
 *                      currency is not yet known — the hook will simply return
 *                      nulls in that case.
 */
export function useSecondaryConversion(
    fromCurrency: string | null | undefined,
): SecondaryConversionResult {
    const { secondaryCurrency } = useCurrencyDisplay();

    // Only fetch when both currencies are known and different
    const enabled =
        !!fromCurrency && !!secondaryCurrency && fromCurrency !== secondaryCurrency;

    const { data: rateData } = useLatestExchangeRate(
        fromCurrency ?? '',
        secondaryCurrency ?? '',
    );

    const rate = enabled && rateData ? rateData.rate : null;

    const convert = useMemo(
        () =>
            (amount: number | null | undefined): number | null => {
                if (amount == null || rate == null) return null;
                return multiply(amount, rate);
            },
        [rate],
    );

    return {
        secondaryCurrency: enabled ? secondaryCurrency : null,
        secondaryExchangeRate: rate,
        convert,
    };
}
