package org.openfinance.util;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import jakarta.validation.Validation;
import jakarta.validation.constraints.PastOrPresent;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.concurrent.Executors;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class ReportingClockTest {
    private static Clock at(String zone) {
        return Clock.fixed(Instant.parse("2026-11-15T23:51:00Z"), ZoneId.of(zone));
    }

    @ParameterizedTest
    @CsvSource({
        "Europe/Paris,2026-11-16",
        "America/New_York,2026-11-15",
        "Pacific/Kiritimati,2026-11-16",
        "Etc/GMT+12,2026-11-15"
    })
    void usesTheUsersCalendar(String zone, String expected) {
        try (ReportingClock.Scope ignored = ReportingClock.use(at(zone))) {
            assertThat(ReportingClock.today()).isEqualTo(expected);
        }
    }

    @ParameterizedTest
    @CsvSource({"Europe/Paris,2026-11-16", "America/New_York,2026-11-15"})
    void storedTimestampDatesFollowTheReportingZoneAndImportedDatesRoundTrip(
            String zone, String expected) {
        try (ReportingClock.Scope ignored = ReportingClock.use(at(zone))) {
            java.time.LocalDateTime stored =
                    java.time.LocalDateTime.ofInstant(at(zone).instant(), ZoneId.systemDefault());
            assertThat(ReportingClock.dateOf(stored)).isEqualTo(expected);
            LocalDate importedDate = LocalDate.of(2026, 3, 29);
            assertThat(ReportingClock.dateOf(ReportingClock.startOfDay(importedDate)))
                    .isEqualTo(importedDate);
        }
    }

    @Test
    void restoresNestedScopesAfterFailure() {
        Clock original = ReportingClock.current();
        try (ReportingClock.Scope ignored = ReportingClock.use(at("Europe/Paris"))) {
            assertThatThrownBy(
                            () -> {
                                try (ReportingClock.Scope nested =
                                        ReportingClock.use(at("America/New_York"))) {
                                    throw new IllegalStateException();
                                }
                            })
                    .isInstanceOf(IllegalStateException.class);
            assertThat(ReportingClock.today()).isEqualTo("2026-11-16");
        }
        assertThat(ReportingClock.current()).isSameAs(original);
    }

    @Test
    void asyncWorkRetainsItsCalendarWithoutLeakingToTheNextTask() throws Exception {
        try (java.util.concurrent.ExecutorService executor = Executors.newSingleThreadExecutor()) {
            java.util.function.Supplier<LocalDate> task;
            try (ReportingClock.Scope ignored = ReportingClock.use(at("Pacific/Kiritimati"))) {
                task = ReportingClock.propagate(ReportingClock::today);
            }
            assertThat(executor.submit(task::get).get()).isEqualTo("2026-11-16");
            assertThat(executor.submit(ReportingClock::zone).get())
                    .isEqualTo(Clock.systemUTC().getZone());
        }
    }

    record Payment(@PastOrPresent LocalDate date) {}

    @Test
    void configuredImportExecutorCarriesTheCalendarAndClearsItsWorkerThread() throws Exception {
        org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor executor =
                (org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor)
                        new org.openfinance.config.AsyncConfig().taskExecutor();
        executor.setCorePoolSize(1);
        executor.setMaxPoolSize(1);
        try {
            java.util.concurrent.CompletableFuture<LocalDate> parsedDate =
                    new java.util.concurrent.CompletableFuture<>();
            try (ReportingClock.Scope ignored = ReportingClock.use(at("Europe/Paris"))) {
                executor.execute(() -> parsedDate.complete(ReportingClock.today()));
            }
            assertThat(parsedDate.get(5, java.util.concurrent.TimeUnit.SECONDS))
                    .isEqualTo("2026-11-16");
            java.util.concurrent.CompletableFuture<ZoneId> nextZone =
                    new java.util.concurrent.CompletableFuture<>();
            executor.execute(() -> nextZone.complete(ReportingClock.zone()));
            assertThat(nextZone.get(5, java.util.concurrent.TimeUnit.SECONDS))
                    .isEqualTo(Clock.systemUTC().getZone());
        } finally {
            executor.shutdown();
        }
    }

    @Test
    void cachedValidationClockDoesNotRetainTheFirstUsersTimezone() {
        try (jakarta.validation.ValidatorFactory factory =
                Validation.byDefaultProvider()
                        .configure()
                        .clockProvider(ReportingClock::validationClock)
                        .buildValidatorFactory()) {
            jakarta.validation.Validator validator = factory.getValidator();
            Payment payment = new Payment(LocalDate.of(2026, 11, 16));
            try (ReportingClock.Scope ignored = ReportingClock.use(at("Europe/Paris"))) {
                assertThat(validator.validate(payment)).isEmpty();
            }
            try (ReportingClock.Scope ignored = ReportingClock.use(at("America/New_York"))) {
                assertThat(validator.validate(payment)).hasSize(1);
            }
            try (ReportingClock.Scope ignored = ReportingClock.use(at("Europe/Paris"))) {
                assertThat(validator.validate(payment)).isEmpty();
            }
        }
    }
}
