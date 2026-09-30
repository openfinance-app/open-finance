package org.openfinance.util;

import java.time.LocalDate;
import java.time.LocalDateTime;

/** An independent valuation supersedes movements already known at its effective date. */
public final class ValuationContributions {
    private ValuationContributions() {}

    public static boolean applies(
            LocalDate movementDate,
            LocalDateTime movementRecordedAt,
            LocalDate anchorDate,
            LocalDateTime anchorRecordedAt) {
        if (anchorDate == null) return true;
        return movementDate.isAfter(anchorDate)
                || (movementRecordedAt != null
                        && anchorRecordedAt != null
                        && movementRecordedAt.isAfter(anchorRecordedAt));
    }
}
