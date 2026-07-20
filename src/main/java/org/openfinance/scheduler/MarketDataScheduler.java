package org.openfinance.scheduler;

import java.time.DayOfWeek;
import java.time.LocalDateTime;
import java.time.ZoneId;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.openfinance.config.SchedulerProperties;
import org.openfinance.repository.UserRepository;
import org.openfinance.service.MarketDataService;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Scheduled job for automatically updating asset prices from market data.
 *
 * <p>This scheduler runs hourly during market hours to keep asset prices current. Updates are only
 * performed during regular trading hours (9 AM - 4 PM ET, Monday-Friday) to avoid unnecessary API
 * calls when markets are closed.
 *
 * <p><strong>Default Schedule:</strong>
 *
 * <ul>
 *   <li>Frequency: Every hour (0 minutes past the hour)
 *   <li>Days: Monday - Friday
 *   <li>Hours: 9:00 AM - 4:00 PM Eastern Time (default; configurable)
 *   <li>Time Zone: America/New_York (ET/EDT) (default; configurable)
 * </ul>
 *
 * <p><strong>Configurable Market Hours</strong> ({@code application.scheduled.market-hours.*}): the
 * {@code DEFAULT}-mode guard window ({@code zone}, {@code open-hour}, {@code close-hour}) can be
 * overridden to support half-day sessions or non-US exchanges. Exchange holidays are not modeled.
 *
 * <p><strong>Configurable Frequency</strong> ({@code application.scheduled.market-data.mode}):
 *
 * <ul>
 *   <li>{@code DEFAULT} — every hour, Mon–Fri 9 AM–4 PM ET (built-in default above)
 *   <li>{@code STARTUP_ONLY} — once on application startup, no periodic schedule
 *   <li>{@code STARTUP_AND_EVERY_X_HOURS} — on startup, then every {@code interval-hours} hours
 *   <li>{@code EVERY_HOUR} — once per hour regardless of day/time
 *   <li>{@code DAILY} — once per day at midnight
 * </ul>
 *
 * <p><strong>Behavior:</strong>
 *
 * <ul>
 *   <li>Fetches all users with assets
 *   <li>Updates prices for each user's assets in batch
 *   <li>Logs summary statistics (users processed, assets updated)
 *   <li>Continues processing even if individual users fail
 * </ul>
 *
 * <p><strong>Performance:</strong> Uses batch quote fetching to minimize API calls. With 100 users
 * and 1000 total assets, expect ~5-10 seconds execution time.
 *
 * @author Open Finance Team
 * @version 1.0
 * @since 2024-01-20
 * @see SchedulerProperties
 * @see MarketDataService#updateAssetPrices(Long)
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class MarketDataScheduler implements ApplicationRunner {

    /** Default cron: every hour on the hour, Mon–Fri 9 AM–4 PM Eastern Time. */
    static final String DEFAULT_CRON = "0 0 9-16 * * MON-FRI";

    static final String DEFAULT_ZONE = "America/New_York";

    private final MarketDataService marketDataService;
    private final UserRepository userRepository;
    private final SchedulerProperties schedulerProperties;

    // -----------------------------------------------------------------
    // Startup execution
    // -----------------------------------------------------------------

    /**
     * Runs the price update once on application startup when the configured mode requests it
     * ({@code STARTUP_ONLY} or {@code STARTUP_AND_EVERY_X_HOURS}).
     */
    @Override
    public void run(ApplicationArguments args) {
        if (schedulerProperties.getMarketData().isRunOnStartup()) {
            log.info(
                    "Executing startup asset price update (mode={})",
                    schedulerProperties.getMarketData().getMode());
            updateAssetPrices();
        }
    }

    // -----------------------------------------------------------------
    // Periodic execution
    // -----------------------------------------------------------------

    /**
     * Scheduled job to update asset prices hourly during market hours.
     *
     * <p>Cron expression: {@code 0 0 9-16 * * MON-FRI} translates to:
     *
     * <ul>
     *   <li>0 seconds
     *   <li>0 minutes (top of the hour)
     *   <li>9-16 hours (9 AM to 4 PM, inclusive)
     *   <li>Every day of month
     *   <li>Every month
     *   <li>Monday through Friday
     * </ul>
     *
     * <p>Time zone is set to America/New_York to match US stock market hours.
     *
     * <p>The effective cron is resolved once at startup via Spring SpEL from {@link
     * SchedulerProperties.SchedulerConfig#effectiveCron(String)}. When mode is {@code STARTUP_ONLY}
     * the cron resolves to {@code "-"}, which instructs Spring not to schedule any periodic
     * execution.
     */
    @Scheduled(
            cron = "#{schedulerProperties.marketData.effectiveCron('" + DEFAULT_CRON + "')}",
            zone = "${application.scheduled.market-hours.zone:" + DEFAULT_ZONE + "}")
    public void updateAssetPrices() {
        LocalDateTime startTime =
                LocalDateTime.now(ZoneId.of(schedulerProperties.getMarketHours().getZone()));
        log.info(
                "Starting scheduled asset price update at {} (mode={})",
                startTime,
                schedulerProperties.getMarketData().getMode());

        // For DEFAULT mode keep the market-hours safety guard; other modes skip it
        // so users who explicitly choose EVERY_HOUR or DAILY get unconditional updates.
        if (schedulerProperties.getMarketData().getMode()
                        == SchedulerProperties.SchedulingMode.DEFAULT
                && !isMarketHours(startTime)) {
            log.info("Skipping update - outside market hours: {}", startTime);
            return;
        }

        try {
            var userIds = userRepository.findAll().stream().map(user -> user.getId()).toList();

            if (userIds.isEmpty()) {
                log.info("No users found, skipping price update");
                return;
            }

            int totalUsers = userIds.size();
            int successfulUsers = 0;
            int totalAssetsUpdated = 0;

            for (Long userId : userIds) {
                try {
                    int assetsUpdated = marketDataService.updateAssetPrices(userId);
                    if (assetsUpdated > 0) {
                        successfulUsers++;
                        totalAssetsUpdated += assetsUpdated;
                    }
                } catch (Exception e) {
                    log.error("Failed to update assets for user {}: {}", userId, e.getMessage());
                }
            }

            LocalDateTime endTime =
                    LocalDateTime.now(ZoneId.of(schedulerProperties.getMarketHours().getZone()));
            long durationSeconds = java.time.Duration.between(startTime, endTime).getSeconds();

            log.info(
                    "Scheduled asset price update completed. "
                            + "Users processed: {}/{}, Assets updated: {}, Duration: {}s",
                    successfulUsers,
                    totalUsers,
                    totalAssetsUpdated,
                    durationSeconds);

        } catch (Exception e) {
            log.error("Fatal error during scheduled asset price update", e);
        }
    }

    // -----------------------------------------------------------------
    // Manual trigger
    // -----------------------------------------------------------------

    /**
     * Manual trigger for updating asset prices outside of the scheduled time.
     *
     * <p>This method can be called via an admin endpoint for manual price updates. Useful for
     * testing or immediate updates when needed.
     *
     * @return summary message with update statistics
     */
    public String triggerManualUpdate() {
        log.info("Manual asset price update triggered");

        try {
            var userIds = userRepository.findAll().stream().map(user -> user.getId()).toList();

            int totalAssetsUpdated = 0;
            for (Long userId : userIds) {
                try {
                    totalAssetsUpdated += marketDataService.updateAssetPrices(userId);
                } catch (Exception e) {
                    log.error("Failed to update assets for user {}: {}", userId, e.getMessage());
                }
            }

            String message =
                    String.format(
                            "Manual update completed. Assets updated: %d", totalAssetsUpdated);
            log.info(message);
            return message;

        } catch (Exception e) {
            String message = "Manual update failed: " + e.getMessage();
            log.error(message, e);
            return message;
        }
    }

    // -----------------------------------------------------------------
    // Helpers
    // -----------------------------------------------------------------

    /**
     * Returns {@code true} if {@code time} falls on a weekday within the configured market-hours
     * window (Monday–Friday, {@code openHour}–{@code closeHour} inclusive). Used only in {@code
     * DEFAULT} mode.
     *
     * <p>The window is configurable via {@code application.scheduled.market-hours.*}; the defaults
     * ({@code 9}–{@code 16} in {@code America/New_York}) preserve the original US-market behavior.
     * Package-private for unit testing.
     *
     * @param time the time to check, already expressed in the configured zone
     * @return true if within the configured market-hours window, false otherwise
     */
    boolean isMarketHours(LocalDateTime time) {
        DayOfWeek dayOfWeek = time.getDayOfWeek();
        int hour = time.getHour();

        boolean isWeekday =
                dayOfWeek.getValue() >= DayOfWeek.MONDAY.getValue()
                        && dayOfWeek.getValue() <= DayOfWeek.FRIDAY.getValue();

        SchedulerProperties.MarketHoursConfig marketHours = schedulerProperties.getMarketHours();
        boolean isDuringHours =
                hour >= marketHours.getOpenHour() && hour <= marketHours.getCloseHour();

        return isWeekday && isDuringHours;
    }
}
