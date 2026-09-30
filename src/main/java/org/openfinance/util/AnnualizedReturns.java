package org.openfinance.util;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

/** Compound-equivalent growth with actual holding years and fractional anniversary years. */
public final class AnnualizedReturns {
    private static final MathContext PRECISION = MathContext.DECIMAL128;
    private static final BigDecimal TWO = BigDecimal.valueOf(2);

    private AnnualizedReturns() {}

    public static BigDecimal compound(
            BigDecimal initial, BigDecimal terminal, LocalDate start, LocalDate end) {
        if (start == null
                || !end.isAfter(start)
                || initial.signum() <= 0
                || terminal.signum() < 0) {
            return null;
        }
        if (terminal.signum() == 0) return new BigDecimal("-100.00");
        long years = ChronoUnit.YEARS.between(start, end);
        LocalDate anniversary = start.plusYears(years);
        int yearDays =
                Math.toIntExact(ChronoUnit.DAYS.between(anniversary, anniversary.plusYears(1)));
        int holdingUnits =
                Math.toIntExact(years * yearDays + ChronoUnit.DAYS.between(anniversary, end));
        // growth ^ holdingUnits = (terminal / initial) ^ yearDays.
        // Integer powers keep the entire calculation in decimal arithmetic.
        BigDecimal target = terminal.divide(initial, PRECISION).pow(yearDays, PRECISION);
        int digits = target.precision() - target.scale();
        BigDecimal high =
                BigDecimal.ONE.scaleByPowerOfTen(Math.max(0, Math.ceilDiv(digits, holdingUnits)));
        BigDecimal low = BigDecimal.ZERO;
        for (int iteration = 0; iteration < 160; iteration++) {
            BigDecimal middle = low.add(high).divide(TWO, PRECISION);
            if (middle.equals(low) || middle.equals(high)) break;
            if (middle.pow(holdingUnits, PRECISION).compareTo(target) < 0) {
                low = middle;
            } else {
                high = middle;
            }
        }
        return low.add(high)
                .divide(TWO, PRECISION)
                .subtract(BigDecimal.ONE)
                .multiply(BigDecimal.valueOf(100))
                .setScale(2, RoundingMode.HALF_UP);
    }
}
