package org.openfinance.scheduler;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.Set;
import javax.crypto.SecretKey;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.openfinance.config.SchedulerProperties;
import org.openfinance.security.EncryptionContext;
import org.openfinance.security.EncryptionKeyCache;
import org.openfinance.service.RecurringTransactionService;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Scheduled job for automatically processing due recurring transactions.
 *
 * <p>This scheduler runs daily at midnight to generate actual transactions from recurring
 * transaction templates. It queries all due recurring transactions across all users and creates
 * corresponding Transaction entries.
 *
 * <p><strong>Default Schedule:</strong>
 *
 * <ul>
 *   <li>Frequency: Daily at midnight (00:00:00)
 *   <li>Cron Expression: {@code 0 0 0 * * ?}
 *   <li>Time Zone: System default (UTC recommended for production)
 * </ul>
 *
 * <p><strong>Configurable Frequency</strong> ({@code
 * application.scheduled.recurring-transactions.mode}):
 *
 * <ul>
 *   <li>{@code DEFAULT} — daily at midnight (built-in default above)
 *   <li>{@code STARTUP_ONLY} — once on application startup, no periodic schedule
 *   <li>{@code STARTUP_AND_EVERY_X_HOURS} — on startup, then every {@code interval-hours} hours
 *   <li>{@code EVERY_HOUR} — once per hour
 *   <li>{@code DAILY} — once per day at midnight
 * </ul>
 *
 * <p><strong>Behavior:</strong>
 *
 * <ul>
 *   <li>Fetches all active recurring transactions where nextOccurrence <= today
 *   <li>Creates actual Transaction for each due recurring transaction
 *   <li>Updates nextOccurrence date based on frequency (DAILY, WEEKLY, MONTHLY, etc.)
 *   <li>Sets isActive=false if endDate has been reached
 *   <li>Logs summary statistics (processed count, failed count, errors)
 *   <li>Continues processing even if individual transactions fail
 * </ul>
 *
 * <p><strong>Performance:</strong> With 100 users and 500 total recurring transactions, expect
 * ~5-10 seconds execution time. Uses batch processing to handle errors gracefully.
 *
 * <p><strong>Error Handling:</strong> Failures for individual recurring transactions are caught,
 * logged, and reported in the ProcessingResult. The scheduler continues processing remaining
 * transactions to ensure maximum reliability.
 *
 * <p><strong>Requirements:</strong>
 *
 * <ul>
 *   <li>REQ-2.3.6: Recurring transaction management
 *   <li>REQ-2.3.6.1: Automatic processing based on frequency
 *   <li>REQ-2.3.6.2: End date handling
 * </ul>
 *
 * @author Open-Finance Development Team
 * @version 1.0
 * @since 2026-02-03
 * @see SchedulerProperties
 * @see RecurringTransactionService#processRecurringTransactions()
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RecurringTransactionScheduler implements ApplicationRunner {

    /** Default cron: daily at midnight. */
    static final String DEFAULT_CRON = "0 0 0 * * ?";

    private final RecurringTransactionService recurringTransactionService;
    private final SchedulerProperties schedulerProperties;
    private final EncryptionKeyCache encryptionKeyCache;

    // -----------------------------------------------------------------
    // Startup execution
    // -----------------------------------------------------------------

    /**
     * Runs recurring transaction processing once on application startup when the configured mode
     * requests it ({@code STARTUP_ONLY} or {@code STARTUP_AND_EVERY_X_HOURS}).
     */
    @Override
    public void run(ApplicationArguments args) {
        if (schedulerProperties.getRecurringTransactions().isRunOnStartup()) {
            log.info(
                    "Executing startup recurring transaction processing (mode={})",
                    schedulerProperties.getRecurringTransactions().getMode());
            processRecurringTransactions();
        }
    }

    // -----------------------------------------------------------------
    // Periodic execution
    // -----------------------------------------------------------------

    /**
     * Scheduled job to process due recurring transactions daily at midnight.
     *
     * <p>Cron expression: {@code 0 0 0 * * ?} translates to:
     *
     * <ul>
     *   <li>0 seconds
     *   <li>0 minutes
     *   <li>0 hours (midnight)
     *   <li>Every day of month
     *   <li>Every month
     *   <li>Every day of week (? means no specific value)
     * </ul>
     *
     * <p><strong>Processing Steps:</strong>
     *
     * <ol>
     *   <li>Query all active recurring transactions with nextOccurrence <= today
     *   <li>For each recurring transaction:
     *       <ul>
     *         <li>Create a new Transaction with the same details
     *         <li>Calculate next occurrence date based on frequency
     *         <li>Update nextOccurrence in recurring transaction
     *         <li>Set isActive=false if endDate has been reached
     *       </ul>
     *   <li>Log summary with processed count, failed count, and error details
     * </ol>
     *
     * <p>The effective cron is resolved once at startup via Spring SpEL from {@link
     * SchedulerProperties.SchedulerConfig#effectiveCron(String)}. When mode is {@code STARTUP_ONLY}
     * the cron resolves to {@code "-"}, which instructs Spring not to schedule any periodic
     * execution.
     *
     * <p><strong>Example Log Output:</strong>
     *
     * <pre>{@code
     * INFO  Starting scheduled recurring transaction processing
     * INFO  Found 25 due recurring transactions to process
     * INFO  Recurring transaction processing complete: processed=24, failed=1, duration=3.2s
     * }</pre>
     *
     * <p>Requirement REQ-2.3.6: Automatically process recurring transactions daily
     */
    @Scheduled(
            cron =
                    "#{schedulerProperties.recurringTransactions.effectiveCron('"
                            + DEFAULT_CRON
                            + "')}")
    public void processRecurringTransactions() {
        LocalDateTime startTime = LocalDateTime.now();
        log.info(
                "Starting scheduled recurring transaction processing at {} (mode={})",
                startTime,
                schedulerProperties.getRecurringTransactions().getMode());

        try {
            // Process per-user to set the correct encryption context
            Set<Long> cachedUserIds = encryptionKeyCache.getCachedUserIds();
            int totalProcessed = 0;
            int totalFailed = 0;

            for (Long userId : cachedUserIds) {
                Optional<SecretKey> keyOpt = encryptionKeyCache.getKey(userId);
                if (keyOpt.isEmpty()) {
                    continue;
                }
                try {
                    EncryptionContext.setKey(keyOpt.get());
                    RecurringTransactionService.ProcessingResult result =
                            recurringTransactionService.processRecurringTransactionsForUser(userId);
                    totalProcessed += result.getProcessedCount();
                    totalFailed += result.getFailedCount();

                    if (!result.getErrors().isEmpty()) {
                        result.getErrors().forEach(error -> log.warn("  - {}", error));
                    }
                } catch (Exception e) {
                    log.error(
                            "Error processing recurring transactions for user {}: {}",
                            userId,
                            e.getMessage(),
                            e);
                } finally {
                    EncryptionContext.clear();
                }
            }

            LocalDateTime endTime = LocalDateTime.now();
            long durationSeconds = java.time.Duration.between(startTime, endTime).getSeconds();

            log.info(
                    "Recurring transaction processing complete: processed={}, failed={}, users={}, duration={}s",
                    totalProcessed,
                    totalFailed,
                    cachedUserIds.size(),
                    durationSeconds);

        } catch (Exception e) {
            log.error("Fatal error during scheduled recurring transaction processing", e);
        }
    }

    // -----------------------------------------------------------------
    // Manual trigger
    // -----------------------------------------------------------------

    /**
     * Manual trigger for processing recurring transactions outside of the scheduled time.
     *
     * <p>This method can be called via an admin endpoint for manual processing. Useful for testing
     * or immediate processing when needed.
     *
     * <p><strong>Use Cases:</strong>
     *
     * <ul>
     *   <li>Testing recurring transaction processing in development
     *   <li>Recovering from a failed scheduled job
     *   <li>Processing on-demand after system maintenance
     * </ul>
     *
     * <p><strong>Warning:</strong> This method should only be called by administrators to avoid
     * processing recurring transactions multiple times in a single day.
     *
     * @return summary message with processing statistics
     */
    public String triggerManualProcessing() {
        log.info("Manual recurring transaction processing triggered");

        try {
            RecurringTransactionService.ProcessingResult result =
                    recurringTransactionService.processRecurringTransactions();

            String message =
                    String.format(
                            "Manual processing completed. Processed: %d, Failed: %d, Errors: %d",
                            result.getProcessedCount(),
                            result.getFailedCount(),
                            result.getErrors().size());

            log.info(message);

            if (!result.getErrors().isEmpty()) {
                log.warn("Errors during manual processing:");
                result.getErrors().forEach(error -> log.warn("  - {}", error));
            }

            return message;

        } catch (Exception e) {
            String message = "Manual processing failed: " + e.getMessage();
            log.error(message, e);
            return message;
        }
    }
}
