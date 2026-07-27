package org.openfinance.entity;

import static org.assertj.core.api.Assertions.*;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link BudgetPeriod}.
 *
 * <p>Pins the i18n {@code nameKey} for each period so display labels can be localized (EN/FR)
 * instead of relying on the hardcoded English {@code displayName}.
 */
@DisplayName("BudgetPeriod Enum Tests")
class BudgetPeriodTest {

    @Test
    @DisplayName("each period exposes a stable i18n name key")
    void eachPeriodExposesItsNameKey() {
        assertThat(BudgetPeriod.WEEKLY.getNameKey()).isEqualTo("budget.period.weekly");
        assertThat(BudgetPeriod.MONTHLY.getNameKey()).isEqualTo("budget.period.monthly");
        assertThat(BudgetPeriod.QUARTERLY.getNameKey()).isEqualTo("budget.period.quarterly");
        assertThat(BudgetPeriod.YEARLY.getNameKey()).isEqualTo("budget.period.yearly");
    }

    @Test
    @DisplayName("approximate day counts are unchanged")
    void approximateDaysPreserved() {
        assertThat(BudgetPeriod.WEEKLY.getApproximateDays()).isEqualTo(7);
        assertThat(BudgetPeriod.MONTHLY.getApproximateDays()).isEqualTo(30);
        assertThat(BudgetPeriod.QUARTERLY.getApproximateDays()).isEqualTo(90);
        assertThat(BudgetPeriod.YEARLY.getApproximateDays()).isEqualTo(365);
    }
}
