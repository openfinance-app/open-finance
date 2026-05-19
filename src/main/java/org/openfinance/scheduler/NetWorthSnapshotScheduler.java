package org.openfinance.scheduler;

import java.time.LocalDate;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.openfinance.config.SchedulerProperties;
import org.openfinance.entity.User;
import org.openfinance.repository.UserRepository;
import org.openfinance.service.NetWorthService;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Scheduled task to create daily net worth snapshots for all users.
 *
 * <p>This job runs daily at midnight (configurable via cron expression) to:
 *
 * <ul>
 *   <li>Calculate current net worth for each user
 *   <li>Save a snapshot to the database for historical tracking
 *   <li>Enable trend analysis on the dashboard
 * </ul>
 *
 * <p><strong>Default Schedule:</strong> Runs daily at 00:05 AM (5 minutes after midnight) to ensure
 * all transactions for the previous day have been processed.
 *
 * <p><strong>Configurable Frequency</strong> ({@code
 * application.scheduled.net-worth-snapshot.mode}):
 *
 * <ul>
 *   <li>{@code DEFAULT} — daily at 00:05 AM (built-in default above)
 *   <li>{@code STARTUP_ONLY} — once on application startup, no periodic schedule
 *   <li>{@code STARTUP_AND_EVERY_X_HOURS} — on startup, then every {@code interval-hours} hours
 *   <li>{@code EVERY_HOUR} — once per hour
 *   <li>{@code DAILY} — once per day at midnight
 * </ul>
 *
 * <p><strong>Error Handling:</strong> Failures for individual users are logged but do not stop the
 * job. The job will retry the next day.
 *
 * <p>Requirements: REQ-2.5.1 (Net Worth Snapshot), REQ-2.5.2 (Historical Data)
 *
 * @author Open-Finance Development Team
 * @since 1.0
 * @see SchedulerProperties
 * @see NetWorthService#saveNetWorthSnapshot(Long)
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class NetWorthSnapshotScheduler implements ApplicationRunner {

    /**
     * Default cron: 00:05 AM daily — intentionally 5 minutes after midnight so recurring
     * transactions (processed at 00:00) have already been persisted.
     */
    static final String DEFAULT_CRON = "0 5 0 * * ?";

    private final NetWorthService netWorthService;
    private final UserRepository userRepository;
    private final SchedulerProperties schedulerProperties;

    // -----------------------------------------------------------------
    // Startup execution
    // -----------------------------------------------------------------

    /**
     * Runs the snapshot job once on application startup when the configured mode requests it
     * ({@code STARTUP_ONLY} or {@code STARTUP_AND_EVERY_X_HOURS}).
     */
    @Override
    public void run(ApplicationArguments args) {
        if (schedulerProperties.getNetWorthSnapshot().isRunOnStartup()) {
            log.info(
                    "Executing startup net worth snapshot (mode={})",
                    schedulerProperties.getNetWorthSnapshot().getMode());
            createDailyNetWorthSnapshots();
        }
    }

    // -----------------------------------------------------------------
    // Periodic execution
    // -----------------------------------------------------------------

    /**
     * Creates daily net worth snapshots for all users.
     *
     * <p>Runs every day at 00:05 AM (5 minutes after midnight). Default cron expression: {@code "0
     * 5 0 * * ?"} (seconds minutes hours day-of-month month day-of-week)
     *
     * <p>The effective cron is resolved once at startup via Spring SpEL from {@link
     * SchedulerProperties.SchedulerConfig#effectiveCron(String)}. When mode is {@code STARTUP_ONLY}
     * the cron resolves to {@code "-"}, which instructs Spring not to schedule any periodic
     * execution.
     *
     * <p>To disable: Set spring.task.scheduling.enabled=false in application.properties
     *
     * <p>Requirement REQ-2.5.1: Daily net worth snapshot
     */
    @Scheduled(
            cron = "#{schedulerProperties.netWorthSnapshot.effectiveCron('" + DEFAULT_CRON + "')}")
    public void createDailyNetWorthSnapshots() {
        log.info(
                "Starting daily net worth snapshot job for date: {} (mode={})",
                LocalDate.now(),
                schedulerProperties.getNetWorthSnapshot().getMode());

        try {
            List<User> users = userRepository.findAll();

            int successCount = 0;
            int failureCount = 0;

            for (User user : users) {
                try {
                    String userCurrency =
                            (user.getBaseCurrency() != null && !user.getBaseCurrency().isBlank())
                                    ? user.getBaseCurrency()
                                    : "USD";
                    netWorthService.saveNetWorthSnapshot(
                            user.getId(), LocalDate.now(), userCurrency);
                    successCount++;
                    log.debug("Net worth snapshot created for user: {}", user.getId());
                } catch (Exception e) {
                    failureCount++;
                    log.error(
                            "Failed to create net worth snapshot for user {}: {}",
                            user.getId(),
                            e.getMessage(),
                            e);
                }
            }

            log.info(
                    "Daily net worth snapshot job completed. Total users: {}, Success: {}, Failures: {}",
                    users.size(),
                    successCount,
                    failureCount);

        } catch (Exception e) {
            log.error("Fatal error in daily net worth snapshot job: {}", e.getMessage(), e);
        }
    }

    // -----------------------------------------------------------------
    // Manual trigger
    // -----------------------------------------------------------------

    /**
     * Manual trigger for creating net worth snapshots. Useful for testing or manual execution via
     * admin API.
     *
     * @param userId the user ID to create snapshot for (optional, if null creates for all users)
     */
    public void triggerManualSnapshot(Long userId) {
        if (userId != null) {
            log.info("Manually triggering net worth snapshot for user: {}", userId);
            try {
                netWorthService.saveNetWorthSnapshot(userId);
                log.info("Manual snapshot created successfully for user: {}", userId);
            } catch (Exception e) {
                log.error(
                        "Failed to create manual snapshot for user {}: {}",
                        userId,
                        e.getMessage(),
                        e);
                throw new RuntimeException("Failed to create snapshot for user " + userId, e);
            }
        } else {
            log.info("Manually triggering net worth snapshots for all users");
            createDailyNetWorthSnapshots();
        }
    }
}
