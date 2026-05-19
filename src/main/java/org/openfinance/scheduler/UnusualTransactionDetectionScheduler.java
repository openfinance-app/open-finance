package org.openfinance.scheduler;

import java.time.LocalDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.openfinance.config.SchedulerProperties;
import org.openfinance.entity.User;
import org.openfinance.repository.UserRepository;
import org.openfinance.service.UnusualTransactionDetectionService;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Scheduled job that runs daily to detect unusual or potentially fraudulent transactions for every
 * active user.
 *
 * <p>The job analyses transactions that were <em>created</em> (imported or entered manually) since
 * the previous run. For each newly created transaction it applies the rule-based statistical
 * algorithms implemented in {@link UnusualTransactionDetectionService} and persists any anomalies
 * as {@code UNUSUAL_TRANSACTION} {@link org.openfinance.entity.Insight} records, which are surfaced
 * to the user through the existing insight/notification pipeline.
 *
 * <p><strong>Default Schedule:</strong> Daily at 01:00 AM — intentionally one hour after midnight
 * so that the recurring-transaction scheduler (00:00) and the net-worth snapshot scheduler (00:05)
 * have already completed their work.
 *
 * <p><strong>Configurable Frequency</strong> ({@code
 * application.scheduled.unusual-transaction-detection.mode}):
 *
 * <ul>
 *   <li>{@code DEFAULT} — daily at 01:00 AM
 *   <li>{@code STARTUP_ONLY} — once on application startup, no periodic schedule
 *   <li>{@code STARTUP_AND_EVERY_X_HOURS} — on startup then every {@code interval-hours} hours
 *   <li>{@code EVERY_HOUR} — once per hour
 *   <li>{@code DAILY} — once per day at midnight
 * </ul>
 *
 * <p><strong>Lookback window:</strong> the scheduler looks back {@value #LOOKBACK_HOURS} hours from
 * the current run time so that a brief outage or restart does not cause transactions to be silently
 * skipped.
 *
 * @see UnusualTransactionDetectionService
 * @see SchedulerProperties
 * @since Sprint 12 – Unusual Transaction Detection
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class UnusualTransactionDetectionScheduler implements ApplicationRunner {

    /** Default cron: 01:00 AM daily. */
    static final String DEFAULT_CRON = "0 0 1 * * ?";

    /**
     * How many hours back from "now" to look for newly created transactions. Set to 25 h so a brief
     * outage or delayed run does not miss transactions.
     */
    static final int LOOKBACK_HOURS = 25;

    private final UnusualTransactionDetectionService detectionService;
    private final UserRepository userRepository;
    private final SchedulerProperties schedulerProperties;

    // -----------------------------------------------------------------
    // Startup execution
    // -----------------------------------------------------------------

    @Override
    public void run(ApplicationArguments args) {
        if (schedulerProperties.getUnusualTransactionDetection().isRunOnStartup()) {
            log.info(
                    "Executing startup unusual transaction detection (mode={})",
                    schedulerProperties.getUnusualTransactionDetection().getMode());
            runDetection();
        }
    }

    // -----------------------------------------------------------------
    // Periodic execution
    // -----------------------------------------------------------------

    /**
     * Daily job to detect unusual transactions across all users.
     *
     * <p>The effective cron is resolved once at startup via Spring SpEL from {@link
     * SchedulerProperties.SchedulerConfig#effectiveCron(String)}. When mode is {@code STARTUP_ONLY}
     * the cron resolves to {@code "-"}, which instructs Spring not to schedule any periodic
     * execution.
     */
    @Scheduled(
            cron =
                    "#{schedulerProperties.unusualTransactionDetection.effectiveCron('"
                            + DEFAULT_CRON
                            + "')}")
    public void runDetection() {
        LocalDateTime startTime = LocalDateTime.now();
        LocalDateTime since = startTime.minusHours(LOOKBACK_HOURS);

        log.info(
                "Starting unusual transaction detection at {} (lookback since {})",
                startTime,
                since);

        List<User> users = userRepository.findAll();
        int totalInsights = 0;
        int failedUsers = 0;

        for (User user : users) {
            try {
                int count = detectionService.detectAndPersist(user.getId(), since);
                if (count > 0) {
                    log.info("Detected {} unusual transaction(s) for user {}", count, user.getId());
                }
                totalInsights += count;
            } catch (Exception e) {
                failedUsers++;
                log.error(
                        "Error detecting unusual transactions for user {}: {}",
                        user.getId(),
                        e.getMessage(),
                        e);
            }
        }

        long durationMs = java.time.Duration.between(startTime, LocalDateTime.now()).toMillis();
        log.info(
                "Unusual transaction detection complete: users={}, insights={}, failed={}, duration={}ms",
                users.size(),
                totalInsights,
                failedUsers,
                durationMs);
    }
}
