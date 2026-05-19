package org.openfinance.scheduler;

import java.sql.Connection;
import java.sql.Statement;
import javax.sql.DataSource;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Scheduler for periodic SQLite database maintenance operations.
 *
 * <p>Performs the following maintenance tasks:
 *
 * <ul>
 *   <li>VACUUM — reclaims unused pages from soft-deleted rows and compacts the DB file
 *   <li>ANALYZE — updates SQLite query planner statistics so indexes are used optimally
 *   <li>PRAGMA integrity_check — verifies database integrity (logged, not thrown)
 * </ul>
 *
 * <p>All operations are scheduled during low-usage periods (early morning). VACUUM is run weekly;
 * ANALYZE is run daily.
 *
 * <p>Requirement REQ-3.1: Database performance — scheduled maintenance.
 *
 * @author Open-Finance Team
 * @version 1.0
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class DatabaseMaintenanceScheduler {

    private final DataSource dataSource;

    /**
     * Runs SQLite VACUUM weekly on Sundays at 02:00 AM.
     *
     * <p>VACUUM rewrites the entire database file, reclaiming free pages left by deleted rows and
     * reducing file fragmentation.
     *
     * <p>VACUUM cannot run inside a transaction — this method is intentionally NOT annotated with
     * {@code @Transactional}.
     *
     * <p>Requirement REQ-3.1: Periodic VACUUM for SQLite database compaction.
     */
    @Scheduled(cron = "${scheduler.db-maintenance.vacuum-cron:0 0 2 * * SUN}")
    public void runVacuum() {
        log.info("Starting scheduled SQLite VACUUM operation");
        long startTime = System.currentTimeMillis();
        try (Connection conn = dataSource.getConnection();
                Statement stmt = conn.createStatement()) {
            stmt.execute("VACUUM");
            long duration = System.currentTimeMillis() - startTime;
            log.info("SQLite VACUUM completed successfully in {}ms", duration);
        } catch (Exception e) {
            log.error("SQLite VACUUM failed: {}", e.getMessage(), e);
        }
    }

    /**
     * Runs SQLite ANALYZE daily at 03:00 AM.
     *
     * <p>ANALYZE updates the query planner statistics tables so that SQLite can make optimal
     * index-usage decisions for complex queries.
     *
     * <p>ANALYZE cannot run inside a transaction — this method is intentionally NOT annotated with
     * {@code @Transactional}.
     *
     * <p>Requirement REQ-3.1: Daily ANALYZE to keep query planner statistics current.
     */
    @Scheduled(cron = "${scheduler.db-maintenance.analyze-cron:0 0 3 * * *}")
    public void runAnalyze() {
        log.info("Starting scheduled SQLite ANALYZE operation");
        long startTime = System.currentTimeMillis();
        try (Connection conn = dataSource.getConnection();
                Statement stmt = conn.createStatement()) {
            stmt.execute("ANALYZE");
            long duration = System.currentTimeMillis() - startTime;
            log.info("SQLite ANALYZE completed successfully in {}ms", duration);
        } catch (Exception e) {
            log.error("SQLite ANALYZE failed: {}", e.getMessage(), e);
        }
    }

    /**
     * Runs SQLite integrity_check weekly on Sundays at 03:00 AM.
     *
     * <p>Checks the database for structural corruption. Results are logged. This does not throw
     * even if corruption is detected — it logs an ERROR for operator attention.
     *
     * <p>Requirement REQ-3.1: Periodic integrity check for database health monitoring.
     */
    @Scheduled(cron = "${scheduler.db-maintenance.integrity-check-cron:0 0 3 * * SUN}")
    public void runIntegrityCheck() {
        log.info("Starting scheduled SQLite integrity_check");
        try (Connection conn = dataSource.getConnection();
                Statement stmt = conn.createStatement();
                java.sql.ResultSet rs = stmt.executeQuery("PRAGMA integrity_check")) {
            String resultStr = rs.next() ? rs.getString(1) : "null";
            if ("ok".equalsIgnoreCase(resultStr)) {
                log.info("SQLite integrity_check passed: {}", resultStr);
            } else {
                log.error(
                        "SQLite integrity_check FAILED: {} — manual intervention may be required",
                        resultStr);
            }
        } catch (Exception e) {
            log.error("SQLite integrity_check failed with exception: {}", e.getMessage(), e);
        }
    }
}
