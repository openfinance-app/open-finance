package org.openfinance.scheduler;

import java.util.Optional;
import javax.crypto.SecretKey;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.openfinance.config.SchedulerProperties;
import org.openfinance.entity.User;
import org.openfinance.repository.UserRepository;
import org.openfinance.security.EncryptionContext;
import org.openfinance.security.EncryptionKeyCache;
import org.openfinance.service.NetWorthService;
import org.openfinance.util.ReportingClock;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Scheduled task to create daily net worth snapshots for all users.
 *
 * <p>This job checks each user's reporting calendar (with a configurable cadence) to:
 *
 * <ul>
 *   <li>Calculate current net worth for each user
 *   <li>Save a snapshot to the database for historical tracking
 *   <li>Enable trend analysis on the dashboard
 * </ul>
 *
 * <p><strong>Default Schedule:</strong> Checks every 15 minutes, five minutes after recurring
 * processing, to ensure all transactions for the previous day have been processed.
 *
 * <p><strong>Configurable Frequency</strong> ({@code
 * application.scheduled.net-worth-snapshot.mode}):
 *
 * <ul>
 *   <li>{@code DEFAULT} — every 15 minutes, creating at most one snapshot per reporting day
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
     * Check every 15 minutes, five minutes after recurring processing. Existing daily snapshots are
     * skipped in DEFAULT mode; quarter-hour offsets cover all current civil timezones.
     */
    static final String DEFAULT_CRON = "0 5,20,35,50 * * * *";

    private final org.openfinance.service.UserReportingClock userReportingClock;
    private final NetWorthService netWorthService;
    private final org.openfinance.repository.NetWorthRepository netWorthRepository;
    private final UserRepository userRepository;
    private final SchedulerProperties schedulerProperties;
    private final EncryptionKeyCache encryptionKeyCache;
    private final org.openfinance.config.EncryptionProperties encryptionProperties;
    private final org.openfinance.security.UserEncryptionLock userEncryptionLock;
    private final org.openfinance.service.DefaultCurrencyProvider defaultCurrencyProvider;

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
     * <p>Checks every 15 minutes and uses each owner's reporting date.
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
        createSnapshots(false);
    }

    private enum SnapshotResult {
        CREATED,
        CURRENT,
        NO_KEY,
        FAILED
    }

    private void createSnapshots(boolean refreshExisting) {
        log.info(
                "Starting net worth snapshot job (mode={})",
                schedulerProperties.getNetWorthSnapshot().getMode());
        try {
            java.util.Map<SnapshotResult, Integer> counts =
                    new java.util.EnumMap<>(SnapshotResult.class);
            for (User user : userRepository.findAll()) {
                counts.merge(snapshotUser(user, refreshExisting), 1, Integer::sum);
            }
            log.info("Net worth snapshot job completed: {}", counts);
        } catch (Exception e) {
            log.error("Fatal error in net worth snapshot job", e);
        }
    }

    private SnapshotResult snapshotUser(User user, boolean refreshExisting) {
        try (org.openfinance.security.UserEncryptionLock.Scope ignored =
                        userEncryptionLock.acquire(user.getId(), false);
                ReportingClock.Scope calendar =
                        ReportingClock.use(userReportingClock.forUser(user.getId()))) {
            Optional<SecretKey> key = encryptionKeyCache.getKey(user.getId());
            if (encryptionProperties.isEnabled() && key.isEmpty()) return SnapshotResult.NO_KEY;
            boolean defaultSchedule =
                    schedulerProperties.getNetWorthSnapshot().getMode()
                            == SchedulerProperties.SchedulingMode.DEFAULT;
            if (!refreshExisting
                    && defaultSchedule
                    && netWorthRepository.existsByUserIdAndSnapshotDate(
                            user.getId(), ReportingClock.today())) {
                return SnapshotResult.CURRENT;
            }
            SecretKey previousKey = EncryptionContext.getKey();
            try {
                EncryptionContext.setKey(key.orElse(null));
                netWorthService.saveNetWorthSnapshot(
                        user.getId(),
                        ReportingClock.today(),
                        defaultCurrencyProvider.resolve(user.getBaseCurrency()));
                return SnapshotResult.CREATED;
            } finally {
                if (previousKey == null) EncryptionContext.clear();
                else EncryptionContext.setKey(previousKey);
            }
        } catch (Exception e) {
            log.error("Failed to create net worth snapshot for user {}", user.getId(), e);
            return SnapshotResult.FAILED;
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
            try (org.openfinance.util.ReportingClock.Scope calendar =
                    org.openfinance.util.ReportingClock.use(userReportingClock.forUser(userId))) {
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
            createSnapshots(true);
        }
    }
}
