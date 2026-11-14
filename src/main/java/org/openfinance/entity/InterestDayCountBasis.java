package org.openfinance.entity;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;

public enum InterestDayCountBasis {
    ACTUAL_365,
    THIRTY_360,
    FORTNIGHT_24;

    /** Working precision for estimates, independent of currency settlement and display rounding. */
    public static final int CALCULATION_SCALE =
            org.openfinance.util.MoneyPrecision.CALCULATION_SCALE;

    /** Daily weights total one year; fortnight accrual is recognized at each half-month end. */
    public BigDecimal dailyInterest(BigDecimal annualInterest, LocalDate date) {
        int units = 1;
        int denominator = 365;
        if (this == THIRTY_360) {
            denominator = 360;
            units = date.getDayOfMonth() == date.lengthOfMonth() ? 31 - date.getDayOfMonth() : 1;
        } else if (this == FORTNIGHT_24) {
            denominator = 24;
            units =
                    date.getDayOfMonth() == 15 || date.getDayOfMonth() == date.lengthOfMonth()
                            ? 1
                            : 0;
        }
        return annualInterest
                .multiply(BigDecimal.valueOf(units))
                .divide(BigDecimal.valueOf(denominator), CALCULATION_SCALE, RoundingMode.HALF_UP);
    }

    public static InterestDayCountBasis fromSkrooge(String code) {
        return switch (code) {
            case "365", "" -> ACTUAL_365;
            case "360" -> THIRTY_360;
            case "24" -> FORTNIGHT_24;
            default -> throw new IllegalArgumentException("Unsupported interest calculation basis");
        };
    }
}
