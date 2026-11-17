package org.openfinance.scheduler;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.scheduling.support.CronExpression;

class FinancialSchedulerCalendarTest {
    @ParameterizedTest
    @ValueSource(
            strings = {
                "Europe/Paris",
                "America/New_York",
                "Asia/Kolkata",
                "Asia/Kathmandu",
                "Australia/Eucla",
                "Pacific/Kiritimati"
            })
    void defaultsReachEachLocalMidnightWithoutWaitingForServerMidnight(String zone) {
        ZonedDateTime midnight = LocalDate.of(2026, 11, 16).atStartOfDay(ZoneId.of(zone));
        ZonedDateTime serverTime = midnight.minusSeconds(1).withZoneSameInstant(ZoneOffset.UTC);
        ZonedDateTime recurring =
                CronExpression.parse(RecurringTransactionScheduler.DEFAULT_CRON).next(serverTime);
        ZonedDateTime snapshot =
                CronExpression.parse(NetWorthSnapshotScheduler.DEFAULT_CRON).next(serverTime);
        assertThat(recurring.toInstant()).isEqualTo(midnight.toInstant());
        assertThat(snapshot.toInstant()).isEqualTo(midnight.plusMinutes(5).toInstant());
    }
}
