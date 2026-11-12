package org.openfinance.util;

import java.math.BigDecimal;
import java.util.Comparator;
import java.util.function.Function;
import org.openfinance.dto.AmountCurrencyMode;

/** Shared, precision-preserving comparisons of decrypted monetary amounts. */
public final class AmountComparison {
    private AmountComparison() {}

    public static BigDecimal select(
            BigDecimal nativeAmount,
            String nativeCurrency,
            BigDecimal baseAmount,
            String baseCurrency,
            Boolean converted,
            AmountCurrencyMode mode) {
        if (mode == AmountCurrencyMode.NATIVE
                || (baseCurrency != null && baseCurrency.equalsIgnoreCase(nativeCurrency))) {
            return nativeAmount;
        }
        // Conversion helpers may return a native fallback for display. Never compare that
        // fallback against a base-currency bound or treat it as a converted sort value.
        return Boolean.TRUE.equals(converted) ? baseAmount : null;
    }

    public static BigDecimal atRate(
            BigDecimal nativeAmount,
            String nativeCurrency,
            String baseCurrency,
            BigDecimal rate,
            Boolean converted,
            AmountCurrencyMode mode) {
        return select(
                nativeAmount,
                nativeCurrency,
                nativeAmount != null && rate != null ? nativeAmount.multiply(rate) : null,
                baseCurrency,
                converted,
                mode);
    }

    /** Null amounts match only an unrestricted query; range bounds are inclusive. */
    public static boolean matches(
            BigDecimal amount, BigDecimal minimum, BigDecimal maximum, BigDecimal exact) {
        if (minimum == null && maximum == null && exact == null) return true;
        return amount != null
                && (minimum == null || amount.compareTo(minimum) >= 0)
                && (maximum == null || amount.compareTo(maximum) <= 0)
                && (exact == null || amount.compareTo(exact) == 0);
    }

    /** Unavailable conversions remain last in both directions. */
    public static <T> Comparator<T> comparator(Function<T, BigDecimal> amount, boolean ascending) {
        Comparator<BigDecimal> order =
                ascending ? Comparator.naturalOrder() : Comparator.reverseOrder();
        return Comparator.comparing(amount, Comparator.nullsLast(order));
    }
}
