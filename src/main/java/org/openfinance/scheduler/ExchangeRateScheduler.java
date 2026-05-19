package org.openfinance.scheduler;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.openfinance.config.SchedulerProperties;
import org.openfinance.service.ExchangeRateService;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Scheduler for automatically updating currency exchange rates.
 *
 * <p>This scheduler runs a daily job to fetch and store the latest exchange rates from Yahoo
 * Finance for all active currencies in the system.
 *
 * <p><strong>Default Schedule:</strong> Daily at 1:00 AM (server timezone)
 *
 * <p><strong>Default Cron Expression:</strong> {@code 0 0 1 * * *}
 *
 * <ul>
 *   <li>Second: 0
 *   <li>Minute: 0
 *   <li>Hour: 1 (1:00 AM)
 *   <li>Day of month: * (every day)
 *   <li>Month: * (every month)
 *   <li>Day of week: * (every day of week)
 * </ul>
 *
 * <p><strong>Configurable Frequency</strong> ({@code application.scheduled.exchange-rates.mode}):
 *
 * <ul>
 *   <li>{@code DEFAULT} — daily at 1:00 AM (built-in default above)
 *   <li>{@code STARTUP_ONLY} — once on application startup, no periodic schedule
 *   <li>{@code STARTUP_AND_EVERY_X_HOURS} — on startup, then every {@code interval-hours} hours
 *   <li>{@code EVERY_HOUR} — once per hour
 *   <li>{@code DAILY} — once per day at midnight
 * </ul>
 *
 * <p><strong>Error Handling:</strong> If the rate update fails (API unavailable, network error),
 * the error is logged but does not crash the application. The system continues to use existing
 * rates until the next successful update.
 *
 * <p><strong>Manual Triggering:</strong> Rates can also be updated manually via the REST API
 * endpoint {@code POST /api/v1/exchange-rates/update} for immediate refresh without waiting for the
 * scheduled job.
 *
 * <p><strong>Production Considerations:</strong>
 *
 * <ul>
 *   <li>Schedule time (1:00 AM) minimizes impact during business hours
 *   <li>Single-threaded execution prevents concurrent API hammering
 *   <li>Existing rates remain valid if update fails
 *   <li>Consider configuring timezone explicitly in production
 * </ul>
 *
 * @author Open Finance Team
 * @version 1.0
 * @since 2026-02-01
 * @see SchedulerProperties
 * @see ExchangeRateService#updateExchangeRates()
 * @see org.springframework.scheduling.annotation.EnableScheduling
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ExchangeRateScheduler implements ApplicationRunner {

    /** Default cron: daily at 1:00 AM (server timezone). */
    static final String DEFAULT_CRON = "0 0 1 * * *";

    private final ExchangeRateService exchangeRateService;
    private final SchedulerProperties schedulerProperties;

    // -----------------------------------------------------------------
    // Startup execution
    // -----------------------------------------------------------------

    /**
     * Runs the exchange rate update once on application startup when the configured mode requests
     * it ({@code STARTUP_ONLY} or {@code STARTUP_AND_EVERY_X_HOURS}).
     */
    @Override
    public void run(ApplicationArguments args) {
        if (schedulerProperties.getExchangeRates().isRunOnStartup()) {
            log.info(
                    "Executing startup exchange rate update (mode={})",
                    schedulerProperties.getExchangeRates().getMode());
            updateDailyExchangeRates();
        }
    }

    // -----------------------------------------------------------------
    // Periodic execution
    // -----------------------------------------------------------------

    /**
     * Scheduled job to update exchange rates daily at 1:00 AM.
     *
     * <p>This method fetches the latest exchange rates from Yahoo Finance for all active currencies
     * and stores them in the database with today's date.
     *
     * <p><strong>Execution Flow:</strong>
     *
     * <ol>
     *   <li>Log start of update
     *   <li>Call {@link ExchangeRateService#updateExchangeRates()}
     *   <li>Log success with count of updated rates
     *   <li>If exception occurs, log error and continue
     * </ol>
     *
     * <p><strong>Default Cron Schedule:</strong> {@code 0 0 1 * * *} (1:00 AM daily)
     *
     * <p>The effective cron is resolved once at startup via Spring SpEL from {@link
     * SchedulerProperties.SchedulerConfig#effectiveCron(String)}. When mode is {@code STARTUP_ONLY}
     * the cron resolves to {@code "-"}, which instructs Spring not to schedule any periodic
     * execution.
     *
     * <p><strong>Example Log Output:</strong>
     *
     * <pre>
     * [INFO] Starting scheduled exchange rate update
     * [INFO] Exchange rate update completed successfully: 35 rates updated
     * </pre>
     *
     * <p><strong>Error Example:</strong>
     *
     * <pre>
     * [INFO] Starting scheduled exchange rate update
     * [ERROR] Failed to update exchange rates: Market data service unavailable
     * </pre>
     *
     * @see org.springframework.scheduling.annotation.Scheduled
     */
    @Scheduled(cron = "#{schedulerProperties.exchangeRates.effectiveCron('" + DEFAULT_CRON + "')}")
    public void updateDailyExchangeRates() {
        log.info(
                "Starting scheduled exchange rate update (mode={})",
                schedulerProperties.getExchangeRates().getMode());

        try {
            int updatedCount = exchangeRateService.updateExchangeRates();
            log.info("Exchange rate update completed successfully: {} rates updated", updatedCount);

            if (updatedCount == 0) {
                log.warn(
                        "No exchange rates were updated. Check currency configuration and API availability.");
            }

        } catch (Exception e) {
            log.error("Failed to update exchange rates: {}", e.getMessage(), e);
            // Don't throw - allow application to continue with existing rates
        }
    }

    // -----------------------------------------------------------------
    // Manual trigger
    // -----------------------------------------------------------------

    /**
     * Test method for manual execution (not scheduled).
     *
     * <p>This method can be used for testing the scheduler logic without waiting for the scheduled
     * time. Call it directly from tests or integration endpoints.
     *
     * <p><strong>Note:</strong> This method is NOT annotated with @Scheduled and will not run
     * automatically. It's provided for manual testing only.
     *
     * @return the number of exchange rates updated
     */
    public int updateExchangeRatesNow() {
        log.info("Manual exchange rate update triggered");
        return exchangeRateService.updateExchangeRates();
    }
}
