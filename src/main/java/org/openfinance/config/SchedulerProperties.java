package org.openfinance.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Configuration properties for all scheduled tasks.
 *
 * <p>Each scheduler has its own nested block in {@code application.yml} under {@code
 * application.scheduled.<scheduler-name>}. The available modes are:
 *
 * <ul>
 *   <li>{@code DEFAULT} — uses the built-in default cron for that scheduler
 *   <li>{@code STARTUP_ONLY} — runs once on application startup, never again
 *   <li>{@code STARTUP_AND_EVERY_X_HOURS} — runs on startup then every {@code interval-hours} hours
 *   <li>{@code EVERY_HOUR} — runs once per hour
 *   <li>{@code DAILY} — runs once per day at midnight
 * </ul>
 *
 * <p>Example {@code application.yml} configuration:
 *
 * <pre>{@code
 * application:
 *   scheduled:
 *     market-data:
 *       mode: STARTUP_AND_EVERY_X_HOURS
 *       interval-hours: 2
 *     exchange-rates:
 *       mode: DAILY
 *     recurring-transactions:
 *       mode: DEFAULT
 *     net-worth-snapshot:
 *       mode: STARTUP_ONLY
 * }</pre>
 */
@Component
@ConfigurationProperties(prefix = "application.scheduled")
public class SchedulerProperties {

    private SchedulerConfig marketData = new SchedulerConfig();
    private SchedulerConfig exchangeRates = new SchedulerConfig();
    private SchedulerConfig recurringTransactions = new SchedulerConfig();
    private SchedulerConfig netWorthSnapshot = new SchedulerConfig();
    private SchedulerConfig unusualTransactionDetection = new SchedulerConfig();
    private MarketHoursConfig marketHours = new MarketHoursConfig();

    // --- Getters & Setters ---

    public SchedulerConfig getMarketData() {
        return marketData;
    }

    public void setMarketData(SchedulerConfig marketData) {
        this.marketData = marketData;
    }

    public SchedulerConfig getExchangeRates() {
        return exchangeRates;
    }

    public void setExchangeRates(SchedulerConfig exchangeRates) {
        this.exchangeRates = exchangeRates;
    }

    public SchedulerConfig getRecurringTransactions() {
        return recurringTransactions;
    }

    public void setRecurringTransactions(SchedulerConfig recurringTransactions) {
        this.recurringTransactions = recurringTransactions;
    }

    public SchedulerConfig getNetWorthSnapshot() {
        return netWorthSnapshot;
    }

    public void setNetWorthSnapshot(SchedulerConfig netWorthSnapshot) {
        this.netWorthSnapshot = netWorthSnapshot;
    }

    public SchedulerConfig getUnusualTransactionDetection() {
        return unusualTransactionDetection;
    }

    public void setUnusualTransactionDetection(SchedulerConfig unusualTransactionDetection) {
        this.unusualTransactionDetection = unusualTransactionDetection;
    }

    public MarketHoursConfig getMarketHours() {
        return marketHours;
    }

    public void setMarketHours(MarketHoursConfig marketHours) {
        this.marketHours = marketHours;
    }

    // -------------------------------------------------------------------------

    /** Per-scheduler configuration block. */
    public static class SchedulerConfig {

        /** Scheduling mode for this scheduler. Defaults to {@link SchedulingMode#DEFAULT}. */
        private SchedulingMode mode = SchedulingMode.DEFAULT;

        /**
         * Number of hours between executions when mode is {@link
         * SchedulingMode#STARTUP_AND_EVERY_X_HOURS}. Must be between 1 and 168 (one week). Defaults
         * to 6.
         */
        private int intervalHours = 6;

        public SchedulingMode getMode() {
            return mode;
        }

        public void setMode(SchedulingMode mode) {
            this.mode = mode;
        }

        public int getIntervalHours() {
            return intervalHours;
        }

        public void setIntervalHours(int intervalHours) {
            if (intervalHours < 1 || intervalHours > 168) {
                throw new IllegalArgumentException(
                        "intervalHours must be between 1 and 168, got: " + intervalHours);
            }
            this.intervalHours = intervalHours;
        }

        /**
         * Derives the cron expression to pass to {@code @Scheduled} based on the configured mode.
         *
         * <p>Returns Spring's special {@code "-"} token for {@link SchedulingMode#STARTUP_ONLY},
         * which disables periodic firing entirely (Spring 5.3+).
         *
         * @param defaultCron the cron string used when mode is {@link SchedulingMode#DEFAULT}
         * @return the effective cron expression string, or {@code "-"} to disable
         */
        public String effectiveCron(String defaultCron) {
            return switch (mode) {
                case DEFAULT -> defaultCron;
                case STARTUP_ONLY -> "-";
                case STARTUP_AND_EVERY_X_HOURS -> "0 0 */" + intervalHours + " * * *";
                case EVERY_HOUR -> "0 0 * * * *";
                case DAILY -> "0 0 0 * * *";
            };
        }

        /** Whether this scheduler should execute once immediately on application startup. */
        public boolean isRunOnStartup() {
            return mode == SchedulingMode.STARTUP_ONLY
                    || mode == SchedulingMode.STARTUP_AND_EVERY_X_HOURS;
        }
    }

    // -------------------------------------------------------------------------

    /**
     * Market-hours window applied by the market-data scheduler's {@code DEFAULT}-mode guard.
     *
     * <p>Configurable via {@code application.scheduled.market-hours.*} so half-day sessions, non-US
     * exchanges, or custom trading windows can be supported without code changes. This is a simple
     * daily {@code [openHour, closeHour]} window (both inclusive) evaluated in a single time zone;
     * it does not model exchange holidays.
     */
    public static class MarketHoursConfig {

        /** IANA time-zone id used to evaluate the market-hours window. Defaults to US Eastern. */
        private String zone = "America/New_York";

        /** First hour of the trading window (0-23, inclusive). Defaults to 9 (9 AM). */
        private int openHour = 9;

        /** Last hour of the trading window (0-23, inclusive). Defaults to 16 (4 PM). */
        private int closeHour = 16;

        public String getZone() {
            return zone;
        }

        public void setZone(String zone) {
            this.zone = zone;
        }

        public int getOpenHour() {
            return openHour;
        }

        public void setOpenHour(int openHour) {
            this.openHour = validateHour(openHour, "openHour");
        }

        public int getCloseHour() {
            return closeHour;
        }

        public void setCloseHour(int closeHour) {
            this.closeHour = validateHour(closeHour, "closeHour");
        }

        private static int validateHour(int hour, String field) {
            if (hour < 0 || hour > 23) {
                throw new IllegalArgumentException(
                        field + " must be between 0 and 23, got: " + hour);
            }
            return hour;
        }
    }

    // -------------------------------------------------------------------------

    /** Scheduling frequency modes available for each scheduler. */
    public enum SchedulingMode {

        /** Use the scheduler's built-in default cron expression. */
        DEFAULT,

        /** Execute once at application startup; never run on a schedule. */
        STARTUP_ONLY,

        /**
         * Execute once at application startup, then repeat every {@link
         * SchedulerConfig#intervalHours} hours.
         */
        STARTUP_AND_EVERY_X_HOURS,

        /** Execute once per hour (top of the hour). */
        EVERY_HOUR,

        /** Execute once per day at midnight. */
        DAILY
    }
}
