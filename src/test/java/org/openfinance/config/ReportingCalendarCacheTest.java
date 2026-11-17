package org.openfinance.config;

import static org.assertj.core.api.Assertions.assertThat;

import com.github.benmanes.caffeine.cache.Caffeine;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import org.junit.jupiter.api.Test;
import org.openfinance.util.ReportingClock;
import org.springframework.cache.caffeine.CaffeineCache;

class ReportingCalendarCacheTest {
    @Test
    void neverReusesAnotherCalendarAndEvictsAllVariantsAfterAnEdit() {
        ReportingCalendarCache cache =
                new ReportingCalendarCache(
                        new CaffeineCache("balances", Caffeine.newBuilder().build()));
        Clock paris = Clock.fixed(Instant.parse("2026-11-15T23:51:00Z"), ZoneId.of("Europe/Paris"));
        Clock newYork = paris.withZone(ZoneId.of("America/New_York"));
        try (ReportingClock.Scope ignored = ReportingClock.use(paris)) {
            cache.put(1L, "Paris balance");
        }
        try (ReportingClock.Scope ignored = ReportingClock.use(newYork)) {
            assertThat(cache.get(1L)).isNull();
            assertThat(cache.get(1L, () -> "New York balance")).isEqualTo("New York balance");
        }
        try (ReportingClock.Scope ignored =
                ReportingClock.use(Clock.offset(paris, java.time.Duration.ofDays(1)))) {
            assertThat(cache.get(1L)).isNull();
            cache.evict(1L);
        }
        try (ReportingClock.Scope ignored = ReportingClock.use(paris)) {
            assertThat(cache.get(1L)).isNull();
        }
        try (ReportingClock.Scope ignored = ReportingClock.use(newYork)) {
            assertThat(cache.get(1L)).isNull();
        }
    }
}
