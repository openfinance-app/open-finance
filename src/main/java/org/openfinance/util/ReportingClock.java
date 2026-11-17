package org.openfinance.util;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.function.Supplier;

/**
 * The user's financial calendar, scoped to one request or background operation. Stored timestamps
 * remain instants in the application's storage timezone; financial dates are never shifted.
 */
public final class ReportingClock {
    private static final ThreadLocal<Clock> CURRENT = new ThreadLocal<>();
    private static final Clock FALLBACK = Clock.systemUTC();

    private ReportingClock() {}

    public static Clock current() {
        Clock clock = CURRENT.get();
        return clock == null ? FALLBACK : clock;
    }

    public static LocalDate today() {
        return LocalDate.now(current());
    }

    public static ZoneId zone() {
        return current().getZone();
    }

    /** Convert a server-local stored timestamp to a date in the user's calendar. */
    public static LocalDate dateOf(LocalDateTime timestamp) {
        return timestamp.atZone(ZoneId.systemDefault()).withZoneSameInstant(zone()).toLocalDate();
    }

    /** Preserve an imported calendar date when it has to be represented by a stored timestamp. */
    public static LocalDateTime startOfDay(LocalDate date) {
        return LocalDateTime.ofInstant(
                date.atStartOfDay(zone()).toInstant(), ZoneId.systemDefault());
    }

    public static Scope use(Clock clock) {
        Clock previous = CURRENT.get();
        CURRENT.set(clock == null ? FALLBACK : clock);
        return () -> {
            if (previous == null) CURRENT.remove();
            else CURRENT.set(previous);
        };
    }

    public static Runnable propagate(Runnable task) {
        Clock captured = current();
        return () -> {
            try (Scope ignored = use(captured)) {
                task.run();
            }
        };
    }

    public static <T> Supplier<T> propagate(Supplier<T> task) {
        Clock captured = current();
        return () -> {
            try (Scope ignored = use(captured)) {
                return task.get();
            }
        };
    }

    /** Validators cache their Clock, so it must resolve the current scope on every invocation. */
    public static Clock validationClock() {
        return new Clock() {
            @Override
            public ZoneId getZone() {
                return zone();
            }

            @Override
            public Clock withZone(ZoneId zone) {
                return Clock.fixed(instant(), zone);
            }

            @Override
            public Instant instant() {
                return current().instant();
            }
        };
    }

    public static ZoneId validZone(String value) {
        return value != null && ZoneId.getAvailableZoneIds().contains(value)
                ? ZoneId.of(value)
                : ZoneOffset.UTC;
    }

    @FunctionalInterface
    public interface Scope extends AutoCloseable {
        @Override
        void close();
    }
}
