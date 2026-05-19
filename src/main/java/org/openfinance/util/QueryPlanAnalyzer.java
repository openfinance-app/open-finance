package org.openfinance.util;

import jakarta.persistence.EntityManager;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Utility for analyzing SQLite query execution plans at startup or on demand.
 *
 * <p>Uses {@code EXPLAIN QUERY PLAN} to log the execution plan for critical queries. This helps
 * identify missing indexes or full-table scans.
 *
 * <p>Requirement REQ-3.1: Database optimization — query execution plan analysis.
 *
 * @author Open-Finance Team
 * @version 1.0
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class QueryPlanAnalyzer {

    private final EntityManager entityManager;

    /**
     * Analyzes and logs the execution plan for a native SQL query.
     *
     * <p>Prepends {@code EXPLAIN QUERY PLAN} to the given SQL and logs each step at DEBUG level.
     * This is a no-op if DEBUG logging is not enabled for this class.
     *
     * <p>Usage: call this from a {@code @PostConstruct} method or a
     * {@code @EventListener(ApplicationReadyEvent.class)} in any service to analyze critical
     * queries at startup.
     *
     * @param queryLabel a human-readable name for the query (used in log output)
     * @param sql the native SQL to analyze (without EXPLAIN QUERY PLAN prefix)
     */
    @Transactional(readOnly = true)
    @SuppressWarnings("unchecked")
    public void analyzeQuery(String queryLabel, String sql) {
        if (!log.isDebugEnabled()) {
            return;
        }
        try {
            List<Object[]> plan =
                    entityManager.createNativeQuery("EXPLAIN QUERY PLAN " + sql).getResultList();

            log.debug("=== Query Plan: {} ===", queryLabel);
            for (Object[] row : plan) {
                // SQLite EXPLAIN QUERY PLAN returns: id, parent, notused, detail
                String detail =
                        row.length >= 4
                                ? String.valueOf(row[3])
                                : String.valueOf(row[row.length - 1]);
                log.debug("  {}", detail);
            }
            log.debug("=== End Plan: {} ===", queryLabel);
        } catch (Exception e) {
            log.warn("Failed to analyze query plan for '{}': {}", queryLabel, e.getMessage());
        }
    }

    /**
     * Analyzes the most critical application queries and logs their plans.
     *
     * <p>Called automatically at application startup via {@link
     * org.openfinance.config.DatabaseInitializer} or similar.
     */
    @Transactional(readOnly = true)
    public void analyzeCriticalQueries() {
        analyzeQuery(
                "transactions_by_user_date",
                "SELECT * FROM transactions WHERE user_id = 1 AND is_deleted = 0 ORDER BY transaction_date DESC LIMIT 20");
        analyzeQuery(
                "transactions_by_account",
                "SELECT * FROM transactions WHERE account_id = 1 AND is_deleted = 0");
        analyzeQuery(
                "net_worth_trend",
                "SELECT * FROM net_worth WHERE user_id = 1 ORDER BY snapshot_date DESC LIMIT 365");
        analyzeQuery(
                "assets_by_user_type",
                "SELECT * FROM assets WHERE user_id = 1 AND asset_type = 'STOCK'");
        analyzeQuery(
                "exchange_rates_latest",
                "SELECT * FROM exchange_rates WHERE base_currency = 'USD' AND target_currency = 'EUR' ORDER BY rate_date DESC LIMIT 1");
    }
}
