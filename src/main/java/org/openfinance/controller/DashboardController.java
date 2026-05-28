package org.openfinance.controller;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import javax.crypto.SecretKey;
import lombok.RequiredArgsConstructor;
import org.openfinance.dto.AccountSummary;
import org.openfinance.dto.AssetAllocation;
import org.openfinance.dto.BorrowingCapacity;
import org.openfinance.dto.CashflowSankeyDto;
import org.openfinance.dto.DashboardSummary;
import org.openfinance.dto.EstimatedInterestSummary;
import org.openfinance.dto.NetWorthAllocation;
import org.openfinance.dto.NetWorthSummary;
import org.openfinance.dto.PortfolioPerformance;
import org.openfinance.entity.NetWorth;
import org.openfinance.entity.User;
import org.openfinance.service.DashboardService;
import org.openfinance.service.NetWorthService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST controller for dashboard and aggregated financial data endpoints.
 *
 * <p>
 * Provides comprehensive dashboard functionality including net worth summaries,
 * account
 * summaries, recent transactions, cash flow analysis, and spending breakdowns.
 *
 * <p>
 * <b>Endpoints:</b>
 *
 * <ul>
 * <li>GET /api/v1/dashboard - Complete dashboard summary
 * <li>GET /api/v1/dashboard/accounts - Account summaries
 * <li>GET /api/v1/dashboard/cashflow - Cash flow analysis (income vs expenses)
 * <li>GET /api/v1/dashboard/spending - Spending breakdown by category
 * <li>GET /api/v1/dashboard/networth-history - Historical net worth snapshots
 * </ul>
 *
 * <p>
 * <b>Security:</b>
 *
 * <ul>
 * <li>All endpoints require JWT authentication
 * <li>Encryption key must be provided via X-Encryption-Key header
 * <li>Users can only access their own dashboard data
 * <li>Sensitive fields (account names, transaction details) are decrypted
 * </ul>
 *
 * <p>
 * <b>Requirements:</b>
 *
 * <ul>
 * <li>REQ-2.8.1.1: Dashboard Metrics - Display gross wealth, liabilities, net
 * worth
 * <li>REQ-2.8.1.2: Trend Visualization - Provide monthly change data
 * <li>REQ-3.2: Authorization checks - User-specific data isolation
 * </ul>
 *
 * @see DashboardService
 * @see DashboardSummary
 * @see AccountSummary
 * @since 1.0.0
 */
@RestController
@RequestMapping("/api/v1/dashboard")
@RequiredArgsConstructor
public class DashboardController {

        private static final Logger log = LoggerFactory.getLogger(DashboardController.class);
        private static final String ENCRYPTION_KEY_HEADER = "X-Encryption-Key";

        private final DashboardService dashboardService;
        private final NetWorthService netWorthService;

        /**
         * Retrieves the complete dashboard summary for the authenticated user.
         *
         * <p>This endpoint aggregates data from multiple sources to provide a
         * comprehensive financial
         * overview including net worth, accounts, and recent transactions.
         *
         * <p><b>Request Headers:</b>
         *
         * <ul>
         * <li>Authorization: Bearer {jwt_token}
         * <li>X-Encryption-Key: {base64_encoded_key}
         * </ul>
         *
         * <p><b>Success Response (HTTP 200 OK):</b>
         *
         * <pre>{@code
         * {
         * "netWorth": {
         * "date": "2026-01-31",
         * "totalAssets": 10000.00,
         * "totalLiabilities": 2000.00,
         * "netWorth": 8000.00,
         * "monthlyChangeAmount": 500.00,
         * "monthlyChangePercentage": 6.67,
         * "currency": "EUR"
         * },
         * "accounts": [
         * {
         * "id": 1,
         * "name": "Chase Checking",
         * "type": "CHECKING",
         * "balance": 5000.00,
         * "currency": "EUR",
         * "isActive": true,
         * "description": "Primary checking account"
         * }
         * ],
         * "recentTransactions": [
         * {
         * "id": 1,
         * "type": "EXPENSE",
         * "amount": 50.00,
         * "date": "2026-01-31",
         * "description": "Groceries",
         * "categoryId": 1,
         * "accountId": 1
         * }
         * ],
         * "snapshotDate": "2026-01-31",
         * "totalAccounts": 3,
         * "totalTransactions": 150,
         * "baseCurrency": "EUR"
         * }
         * }</pre>
         *
         * <p><b>Error Responses:</b>
         *
         * <ul>
         * <li>400 Bad Request - Missing or invalid encryption key header
         * <li>401 Unauthorized - Missing or invalid JWT token
         * <li>500 Internal Server Error - Unexpected error during processing
         * </ul>
         *
         * <p><b>Requirement REQ-2.8.1.1:</b> Dashboard displays gross wealth,
         * liabilities, net worth
         *
         * @param authentication the authentication object containing the authenticated
         * user
         * @param encryptionKeyHeader the base64-encoded encryption key from request
         * header
         * @return ResponseEntity containing the complete dashboard summary
         */
        @GetMapping({ "", "/summary" })
        public ResponseEntity<DashboardSummary> getDashboardSummary(
                        Authentication authentication,
                        @RequestHeader(ENCRYPTION_KEY_HEADER) String encryptionKeyHeader) {

                log.debug("Received dashboard summary request");

                // Validate encryption key header
                if (encryptionKeyHeader == null || encryptionKeyHeader.isBlank()) {
                        log.warn("Missing encryption key header in dashboard request");
                        return ResponseEntity.badRequest().build();
                }

                // Extract user ID from authentication
                User user = (User) authentication.getPrincipal();
                Long userId = user.getId();

                // Decode encryption key using Utility for consistency and security
                SecretKey encryptionKey = org.openfinance.util.EncryptionUtil.decodeEncryptionKey(encryptionKeyHeader);

                log.info("Fetching dashboard summary for user {}", userId);

                // Get dashboard summary
                DashboardSummary summary = dashboardService.getDashboardSummary(userId, encryptionKey);

                log.debug(
                                "Dashboard summary retrieved for user {}: {} accounts, {} transactions",
                                userId,
                                summary.getTotalAccounts(),
                                summary.getTotalTransactions());

                return ResponseEntity.ok(summary);
        }

        /**
         * Retrieves account summaries for the authenticated user.
         *
         * <p>Returns a list of all active accounts with balances, sorted by balance
         * descending. Account
         * names and descriptions are decrypted.
         *
         * <p><b>Request Headers:</b>
         *
         * <ul>
         * <li>Authorization: Bearer {jwt_token}
         * <li>X-Encryption-Key: {base64_encoded_key}
         * </ul>
         *
         * <p><b>Success Response (HTTP 200 OK):</b>
         *
         * <pre>{@code
         * [
         * {
         * "id": 1,
         * "name": "Chase Checking",
         * "type": "CHECKING",
         * "balance": 5000.00,
         * "currency": "EUR",
         * "isActive": true,
         * "description": "Primary checking account"
         * },
         * {
         * "id": 2,
         * "name": "Savings Account",
         * "type": "SAVINGS",
         * "balance": 3000.00,
         * "currency": "EUR",
         * "isActive": true,
         * "description": "Emergency fund"
         * }
         * ]
         * }</pre>
         *
         * <p><b>Error Responses:</b>
         *
         * <ul>
         * <li>400 Bad Request - Missing or invalid encryption key header
         * <li>401 Unauthorized - Missing or invalid JWT token
         * <li>500 Internal Server Error - Unexpected error during processing
         * </ul>
         *
         * @param authentication the authentication object containing the authenticated
         * user
         * @param encryptionKeyHeader the base64-encoded encryption key from request
         * header
         * @return ResponseEntity containing list of account summaries
         */
        @GetMapping("/accounts")
        public ResponseEntity<List<AccountSummary>> getAccountSummaries(
                        Authentication authentication,
                        @RequestHeader(ENCRYPTION_KEY_HEADER) String encryptionKeyHeader) {

                log.debug("Received account summaries request");

                // Validate encryption key header
                if (encryptionKeyHeader == null || encryptionKeyHeader.isBlank()) {
                        log.warn("Missing encryption key header in account summaries request");
                        return ResponseEntity.badRequest().build();
                }

                // Extract user ID from authentication
                User user = (User) authentication.getPrincipal();
                Long userId = user.getId();

                // Decode encryption key using Utility for consistency and security
                SecretKey encryptionKey = org.openfinance.util.EncryptionUtil.decodeEncryptionKey(encryptionKeyHeader);

                log.info("Fetching account summaries for user {}", userId);

                // Get account summaries
                List<AccountSummary> summaries = dashboardService.getAccountSummaries(userId, encryptionKey);

                log.debug("Retrieved {} account summaries for user {}", summaries.size(), userId);

                return ResponseEntity.ok(summaries);
        }

        /**
         * Retrieves cash flow analysis for the authenticated user.
         *
         * <p>Calculates income vs expenses for the specified time period, returning
         * total income, total
         * expenses, and net cash flow (income - expenses).
         *
         * <p><b>Request Headers:</b>
         *
         * <ul>
         * <li>Authorization: Bearer {jwt_token}
         * </ul>
         *
         * <p><b>Request Parameters:</b>
         *
         * <ul>
         * <li>period (optional, default=30) - Number of days to analyze (e.g., 7, 30,
         * 90, 365)
         * </ul>
         *
         * <p><b>Success Response (HTTP 200 OK):</b>
         *
         * <pre>{@code
         * {
         * "income": 3000.00,
         * "expenses": 1500.00,
         * "netCashFlow": 1500.00
         * }
         * }</pre>
         *
         * <p><b>Error Responses:</b>
         *
         * <ul>
         * <li>400 Bad Request - Invalid period parameter (must be positive)
         * <li>401 Unauthorized - Missing or invalid JWT token
         * <li>500 Internal Server Error - Unexpected error during processing
         * </ul>
         *
         * <p><b>Requirement REQ-2.8.1.2:</b> Display monthly income vs. expenses
         *
         * @param authentication the authentication object containing the authenticated
         * user
         * @param period the time period in days (default: 30)
         * @return ResponseEntity containing cash flow data (income, expenses,
         * netCashFlow)
         */
        @GetMapping("/cashflow")
        public ResponseEntity<Map<String, BigDecimal>> getCashFlow(
                        Authentication authentication,
                        @RequestParam(defaultValue = "30") int period,
                        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) java.time.LocalDate startDate,
                        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) java.time.LocalDate endDate) {

                log.debug(
                                "Received cash flow request for period: {} days, startDate: {}, endDate: {}",
                                period,
                                startDate,
                                endDate);

                User user = (User) authentication.getPrincipal();
                Long userId = user.getId();

                Map<String, BigDecimal> cashFlow;
                if (startDate != null && endDate != null) {
                        log.info(
                                        "Fetching cash flow for user {} with custom range: {} to {}",
                                        userId,
                                        startDate,
                                        endDate);
                        cashFlow = dashboardService.getCashFlow(userId, startDate, endDate);
                } else {
                        if (period <= 0) {
                                log.warn("Invalid period parameter: {}", period);
                                return ResponseEntity.badRequest().build();
                        }
                        log.info("Fetching cash flow for user {} over {} days", userId, period);
                        cashFlow = dashboardService.getCashFlow(userId, period);
                }

                log.debug(
                                "Cash flow for user {}: income={}, expenses={}, net={}",
                                userId,
                                cashFlow.get("income"),
                                cashFlow.get("expenses"),
                                cashFlow.get("netCashFlow"));

                return ResponseEntity.ok(cashFlow);
        }

        /**
         * Retrieves daily cash flow for the authenticated user for a given month and
         * year.
         *
         * @param authentication the authentication object
         * @param year           the year (defaults to current year)
         * @param month          the month (1-12, defaults to current month)
         * @return ResponseEntity containing list of daily cash flows
         */
        @GetMapping("/daily-cashflow")
        public ResponseEntity<List<org.openfinance.dto.DailyCashFlow>> getDailyCashFlow(
                        Authentication authentication,
                        @RequestParam(required = false) Integer year,
                        @RequestParam(required = false) Integer month) {

                if (year == null) {
                        year = java.time.LocalDate.now().getYear();
                }
                if (month == null) {
                        month = java.time.LocalDate.now().getMonthValue();
                }

                log.debug("Received daily cash flow request for year: {}, month: {}", year, month);

                User user = (User) authentication.getPrincipal();
                Long userId = user.getId();

                List<org.openfinance.dto.DailyCashFlow> dailyCashFlow = dashboardService.getDailyCashFlow(userId, year,
                                month);

                return ResponseEntity.ok(dailyCashFlow);
        }

        /**
         * Retrieves spending breakdown by category for the authenticated user.
         *
         * <p>Groups all expense transactions by category and sums the amounts,
         * providing a breakdown of
         * where money is being spent. Results are sorted by amount descending.
         *
         * <p><b>Request Headers:</b>
         *
         * <ul>
         * <li>Authorization: Bearer {jwt_token}
         * </ul>
         *
         * <p><b>Request Parameters:</b>
         *
         * <ul>
         * <li>period (optional, default=30) - Number of days to analyze (e.g., 7, 30,
         * 90, 365)
         * </ul>
         *
         * <p><b>Success Response (HTTP 200 OK):</b>
         *
         * <pre>{@code
         * {
         * "Category_1": 500.00,
         * "Category_3": 300.00,
         * "Category_5": 200.00,
         * "Uncategorized": 50.00
         * }
         * }</pre>
         *
         * <p><b>Note:</b> Category names are returned as "Category_{id}" (e.g.,
         * "Category_1"). Frontend
         * should resolve these IDs to actual category names via the Category API.
         * Uncategorized
         * transactions are grouped under "Uncategorized".
         *
         * <p><b>Error Responses:</b>
         *
         * <ul>
         * <li>400 Bad Request - Invalid period parameter (must be positive)
         * <li>401 Unauthorized - Missing or invalid JWT token
         * <li>500 Internal Server Error - Unexpected error during processing
         * </ul>
         *
         * <p><b>Requirement REQ-2.8.1.1:</b> Dashboard displays spending by category
         *
         * @param authentication the authentication object containing the authenticated
         * user
         * @param period the time period in days (default: 30)
         * @return ResponseEntity containing map of category to spending amount, sorted
         * descending
         */
        @GetMapping("/spending")
        public ResponseEntity<Map<String, BigDecimal>> getSpendingByCategory(
                        Authentication authentication,
                        @RequestParam(defaultValue = "30") int period,
                        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) java.time.LocalDate startDate,
                        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) java.time.LocalDate endDate) {

                log.debug(
                                "Received spending by category request for period: {} days, startDate: {}, endDate: {}",
                                period,
                                startDate,
                                endDate);

                User user = (User) authentication.getPrincipal();
                Long userId = user.getId();

                Map<String, BigDecimal> spending;
                if (startDate != null && endDate != null) {
                        log.info(
                                        "Fetching spending by category for user {} with custom range: {} to {}",
                                        userId,
                                        startDate,
                                        endDate);
                        spending = dashboardService.getSpendingByCategory(userId, startDate, endDate);
                } else {
                        if (period <= 0) {
                                log.warn("Invalid period parameter: {}", period);
                                return ResponseEntity.badRequest().build();
                        }
                        log.info("Fetching spending by category for user {} over {} days", userId, period);
                        spending = dashboardService.getSpendingByCategory(userId, period);
                }

                log.debug(
                                "Spending by category for user {}: {} categories, total={}",
                                userId,
                                spending.size(),
                                spending.values().stream().reduce(BigDecimal.ZERO, BigDecimal::add));

                return ResponseEntity.ok(spending);
        }

        /**
         * Get net worth history over a specified period.
         *
         * <p>
         * Returns historical net worth snapshots for trend analysis and charting. Data
         * points are
         * ordered by date ascending (oldest to newest).
         *
         * <p>
         * <b>Example Request:</b>
         *
         * <pre>
         * GET /api/v1/dashboard/networth-history?period=365
         * </pre>
         *
         * <p>
         * <b>Example Response:</b>
         *
         * <pre>
         * [
         *   {
         *     "date": "2025-02-01",
         *     "totalAssets": 150000.00,
         *     "totalLiabilities": 50000.00,
         *     "netWorth": 100000.00,
         *     "currency": "EUR"
         *   },
         *   {
         *     "date": "2025-03-01",
         *     "totalAssets": 155000.00,
         *     "totalLiabilities": 48000.00,
         *     "netWorth": 107000.00,
         *     "currency": "EUR"
         *   }
         * ]
         * </pre>
         *
         * <p>
         * <b>Requirement REQ-2.8.1:</b> Dashboard displays net worth history over time
         *
         * @param authentication the authentication object containing the authenticated
         *                       user
         * @param period         the time period in days to look back (default: 365)
         * @return ResponseEntity containing list of net worth snapshots
         */
        @GetMapping("/networth-history")
        public ResponseEntity<List<org.openfinance.dto.NetWorthSummary>> getNetWorthHistory(
                        Authentication authentication,
                        @RequestParam(defaultValue = "365") int period,
                        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) java.time.LocalDate startDate,
                        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) java.time.LocalDate endDate,
                        @RequestParam(defaultValue = "false") boolean recalculate,
                        @RequestHeader(value = ENCRYPTION_KEY_HEADER, required = false) String encryptionKeyHeader) {

                log.debug(
                                "Received net worth history request for period: {} days, startDate: {}, endDate: {}, recalculate: {}",
                                period,
                                startDate,
                                endDate,
                                recalculate);

                User user = (User) authentication.getPrincipal();
                Long userId = user.getId();

                // Calculate effective date range
                java.time.LocalDate effectiveEnd = (endDate != null) ? endDate : java.time.LocalDate.now();
                java.time.LocalDate effectiveStart;
                if (startDate != null) {
                        effectiveStart = startDate;
                } else {
                        if (period <= 0) {
                                log.warn("Invalid period parameter: {}", period);
                                return ResponseEntity.badRequest().build();
                        }
                        effectiveStart = effectiveEnd.minusDays(period);
                }

                log.info(
                                "Fetching net worth history for user {} from {} to {} (recalculate={})",
                                userId,
                                effectiveStart,
                                effectiveEnd,
                                recalculate);

                String userCurrency = (user.getBaseCurrency() != null && !user.getBaseCurrency().isBlank())
                                ? user.getBaseCurrency()
                                : "USD";
                SecretKey backfillKey = (encryptionKeyHeader != null && !encryptionKeyHeader.isBlank())
                                ? org.openfinance.util.EncryptionUtil.decodeEncryptionKey(
                                                encryptionKeyHeader)
                                : null;

                // Force-recalculate: delete existing snapshots in range and recompute from
                // scratch
                if (recalculate) {
                        int rebuilt = netWorthService.backfillNetWorthHistory(
                                        userId, effectiveStart, effectiveEnd, userCurrency, backfillKey, true);
                        log.info(
                                        "Recalculated {} net worth snapshots for user {} from {} to {}",
                                        rebuilt,
                                        userId,
                                        effectiveStart,
                                        effectiveEnd);
                }

                // Get net worth history from service
                List<NetWorth> history = netWorthService.getNetWorthHistory(userId, effectiveStart, effectiveEnd);

                // Auto-backfill if sparse (fewer than 3 meaningful data points)
                if (!recalculate
                                && history.stream()
                                                .filter(nw -> nw.getNetWorth().compareTo(BigDecimal.ZERO) != 0)
                                                .count() < 3) {
                        int backfilled = netWorthService.backfillNetWorthHistory(
                                        userId, effectiveStart, effectiveEnd, userCurrency, backfillKey);
                        if (backfilled > 0) {
                                log.info(
                                                "Backfilled {} net worth snapshots for user {} from {} to {}",
                                                backfilled,
                                                userId,
                                                effectiveStart,
                                                effectiveEnd);
                                history = netWorthService.getNetWorthHistory(userId, effectiveStart, effectiveEnd);
                        }
                }

                // Filter out zero-value snapshots if non-zero snapshots also exist (prevents
                // stale $0 initialization snapshots from polluting the chart)
                boolean hasNonZero = history.stream().anyMatch(nw -> nw.getNetWorth().compareTo(BigDecimal.ZERO) != 0);
                if (hasNonZero) {
                        history = history.stream()
                                        .filter(
                                                        nw -> nw.getTotalAssets().compareTo(BigDecimal.ZERO) != 0
                                                                        || nw.getTotalLiabilities()
                                                                                        .compareTo(BigDecimal.ZERO) != 0)
                                        .collect(Collectors.toList());
                }

                // Convert to DTOs
                List<NetWorthSummary> summaries = history.stream()
                                .map(
                                                nw -> new NetWorthSummary(
                                                                nw.getSnapshotDate(),
                                                                nw.getTotalAssets(),
                                                                nw.getTotalLiabilities(),
                                                                nw.getNetWorth(),
                                                                BigDecimal.ZERO, // monthlyChangeAmount - not needed
                                                                // for historical data
                                                                BigDecimal.ZERO, // monthlyChangePercentage - not
                                                                // needed for historical data
                                                                nw.getCurrency()))
                                .collect(Collectors.toList());

                log.debug(
                                "Net worth history for user {}: {} data points from {} to {}",
                                userId,
                                summaries.size(),
                                effectiveStart,
                                effectiveEnd);

                return ResponseEntity.ok(summaries);
        }

        /**
         * Get asset allocation breakdown by type for portfolio visualization.
         *
         * <p>Returns a list of asset allocations showing the distribution of the user's
         * portfolio
         * across different asset types (STOCK, CRYPTO, BOND, etc.) for treemap
         * visualization on the
         * dashboard.
         *
         * <p><b>Request Headers:</b>
         *
         * <ul>
         * <li>Authorization: Bearer {jwt_token}
         * </ul>
         *
         * <p><b>Example Request:</b>
         *
         * <pre>GET /api/v1/dashboard/asset-allocation</pre>
         *
         * <p><b>Example Response (HTTP 200 OK):</b>
         *
         * <pre>{@code
         * [
         * {
         * "type": "STOCK",
         * "typeName": "Stocks",
         * "totalValue": 5000.00,
         * "percentage": 50.00,
         * "assetCount": 3,
         * "currency": "EUR"
         * },
         * {
         * "type": "CRYPTO",
         * "typeName": "Cryptocurrency",
         * "totalValue": 3000.00,
         * "percentage": 30.00,
         * "assetCount": 2,
         * "currency": "EUR"
         * }
         * ]
         * }</pre>
         *
         * <p><b>Error Responses:</b>
         *
         * <ul>
         * <li>401 Unauthorized - Missing or invalid JWT token
         * <li>500 Internal Server Error - Unexpected error during processing
         * </ul>
         *
         * <p><b>Task 4.3.6:</b> Asset allocation chart component data
         *
         * <p><b>Requirement REQ-2.6.3:</b> Portfolio analytics and visualization
         *
         * @param authentication the authentication object containing the authenticated
         * user
         * @return ResponseEntity containing list of asset allocations sorted by value
         * descending
         */
        @GetMapping("/asset-allocation")
        public ResponseEntity<List<AssetAllocation>> getAssetAllocation(Authentication authentication) {
                log.debug("Received asset allocation request");

                // Extract user ID from authentication
                User user = (User) authentication.getPrincipal();
                Long userId = user.getId();

                log.info("Fetching asset allocation for user {}", userId);

                // Get asset allocation
                List<AssetAllocation> allocations = dashboardService.getAssetAllocation(userId);

                log.debug("Asset allocation for user {}: {} asset types", userId, allocations.size());

                return ResponseEntity.ok(allocations);
        }

        /**
         * Get portfolio performance metrics for dashboard cards with sparkline data.
         *
         * <p>Returns a list of performance metrics including total portfolio value,
         * unrealized
         * gains/losses, and historical sparkline data for visualization.
         *
         * <p><b>Request Headers:</b>
         *
         * <ul>
         * <li>Authorization: Bearer {jwt_token}
         * </ul>
         *
         * <p><b>Example Request:</b>
         *
         * <pre>GET /api/v1/dashboard/portfolio-performance</pre>
         *
         * <p><b>Example Response (HTTP 200 OK):</b>
         *
         * <pre>{@code
         * [
         * {
         * "label": "Total Value",
         * "currentValue": 10000.00,
         * "changeAmount": 500.00,
         * "changePercentage": 5.26,
         * "currency": "EUR",
         * "sparklineData": [
         * { "date": "2026-01-01", "value": 9500.00 },
         * { "date": "2026-01-15", "value": 9800.00 },
         * { "date": "2026-01-31", "value": 10000.00 }
         * ]
         * },
         * {
         * "label": "Unrealized Gain",
         * "currentValue": 1500.00,
         * "changeAmount": 1500.00,
         * "changePercentage": 17.65,
         * "currency": "EUR",
         * "sparklineData": []
         * }
         * ]
         * }</pre>
         *
         * <p><b>Error Responses:</b>
         *
         * <ul>
         * <li>401 Unauthorized - Missing or invalid JWT token
         * <li>500 Internal Server Error - Unexpected error during processing
         * </ul>
         *
         * <p><b>Task 4.3.8:</b> Portfolio performance cards component data
         *
         * <p><b>Requirement REQ-2.6.3:</b> Portfolio performance analytics
         *
         * @param authentication the authentication object containing the authenticated
         * user
         * @return ResponseEntity containing list of portfolio performance metrics with
         * sparkline data
         */
        @GetMapping("/portfolio-performance")
        public ResponseEntity<List<PortfolioPerformance>> getPortfolioPerformance(
                        Authentication authentication,
                        @RequestParam(defaultValue = "30") int period,
                        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) java.time.LocalDate startDate,
                        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) java.time.LocalDate endDate) {
                log.debug(
                                "Received portfolio performance request for period: {} days, startDate: {}, endDate: {}",
                                period,
                                startDate,
                                endDate);

                User user = (User) authentication.getPrincipal();
                Long userId = user.getId();

                List<PortfolioPerformance> performances;
                if (startDate != null && endDate != null) {
                        log.info(
                                        "Fetching portfolio performance for user {} with custom range: {} to {}",
                                        userId,
                                        startDate,
                                        endDate);
                        performances = dashboardService.getPortfolioPerformance(userId, startDate, endDate);
                } else {
                        log.info("Fetching portfolio performance for user {} over {} days", userId, period);
                        performances = dashboardService.getPortfolioPerformance(userId, period);
                }

                log.debug("Portfolio performance for user {}: {} metrics", userId, performances.size());

                return ResponseEntity.ok(performances);
        }

        /**
         * Get borrowing capacity analysis for the authenticated user.
         *
         * <p>Calculates borrowing capacity based on debt-to-income ratio, showing:
         *
         * <ul>
         * <li>Monthly income and expenses averages
         * <li>Monthly debt payment obligations
         * <li>Debt-to-income ratio percentage
         * <li>Available borrowing capacity
         * <li>Financial health status
         * </ul>
         *
         * <p><b>Request Headers:</b>
         *
         * <ul>
         * <li>Authorization: Bearer {jwt_token}
         * <li>X-Encryption-Key: {base64_encoded_key}
         * </ul>
         *
         * <p><b>Request Parameters:</b>
         *
         * <ul>
         * <li>period (optional, default=90) - Analysis period in days for
         * income/expense averaging
         * </ul>
         *
         * <p><b>Example Request:</b>
         *
         * <pre>GET /api/v1/dashboard/borrowing-capacity?period=90</pre>
         *
         * <p><b>Example Response (HTTP 200 OK):</b>
         *
         * <pre>{@code
         * {
         * "monthlyIncome": 5000.00,
         * "monthlyExpenses": 3000.00,
         * "monthlyDebtPayments": 1500.00,
         * "debtToIncomeRatio": 30.00,
         * "recommendedMaxBorrowing": 500.00,
         * "availableBorrowingCapacity": 60000.00,
         * "financialHealthStatus": "GOOD",
         * "currency": "EUR",
         * "analysisPeriod": 90
         * }
         * }</pre>
         *
         * @param authentication the authentication object containing the authenticated
         * user
         * @param encryptionKeyHeader the base64-encoded encryption key from request
         * header
         * @param period the analysis period in days (default: 90)
         * @return ResponseEntity containing borrowing capacity analysis
         */
        @GetMapping("/borrowing-capacity")
        public ResponseEntity<BorrowingCapacity> getBorrowingCapacity(
                        Authentication authentication,
                        @RequestHeader(ENCRYPTION_KEY_HEADER) String encryptionKeyHeader,
                        @RequestParam(defaultValue = "90") int period,
                        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) java.time.LocalDate startDate,
                        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) java.time.LocalDate endDate) {

                log.debug(
                                "Received borrowing capacity request for period: {} days, startDate: {}, endDate: {}",
                                period,
                                startDate,
                                endDate);

                // Validate encryption key header
                if (encryptionKeyHeader == null || encryptionKeyHeader.isBlank()) {
                        log.warn("Missing encryption key header in borrowing capacity request");
                        return ResponseEntity.badRequest().build();
                }

                // Extract user ID from authentication
                User user = (User) authentication.getPrincipal();
                Long userId = user.getId();

                // Decode encryption key
                SecretKey encryptionKey = org.openfinance.util.EncryptionUtil.decodeEncryptionKey(encryptionKeyHeader);

                BorrowingCapacity capacity;
                if (startDate != null && endDate != null) {
                        log.info(
                                        "Fetching borrowing capacity for user {} with custom range: {} to {}",
                                        userId,
                                        startDate,
                                        endDate);
                        capacity = dashboardService.getBorrowingCapacity(
                                        userId, startDate, endDate, encryptionKey);
                } else {
                        if (period <= 0) {
                                log.warn("Invalid period parameter: {}", period);
                                return ResponseEntity.badRequest().build();
                        }
                        log.info("Fetching borrowing capacity for user {} over {} days", userId, period);
                        capacity = dashboardService.getBorrowingCapacity(userId, period, encryptionKey);
                }

                log.debug(
                                "Borrowing capacity retrieved for user {}: DTI={}%, status={}",
                                userId, capacity.getDebtToIncomeRatio(), capacity.getFinancialHealthStatus());

                return ResponseEntity.ok(capacity);
        }

        /**
         * Get net worth allocation breakdown for portfolio visualization.
         *
         * <p>Returns the distribution of the user's net worth across different
         * categories: assets
         * (cash, investments, real estate) and liabilities (mortgages, loans, credit
         * cards).
         *
         * <p><b>Request Headers:</b>
         *
         * <ul>
         * <li>Authorization: Bearer {jwt_token}
         * <li>X-Encryption-Key: {base64_encoded_key}
         * </ul>
         *
         * <p><b>Example Request:</b>
         *
         * <pre>GET /api/v1/dashboard/networth-allocation</pre>
         *
         * <p><b>Example Response (HTTP 200 OK):</b>
         *
         * <pre>{@code
         * [
         * {
         * "category": "Cash & Savings",
         * "value": 10000.00,
         * "percentage": 25.00,
         * "itemCount": 2,
         * "isLiability": false,
         * "currency": "EUR"
         * },
         * {
         * "category": "Investments",
         * "value": 20000.00,
         * "percentage": 50.00,
         * "itemCount": 5,
         * "isLiability": false,
         * "currency": "EUR"
         * },
         * {
         * "category": "Mortgages",
         * "value": -10000.00,
         * "percentage": 25.00,
         * "itemCount": 1,
         * "isLiability": true,
         * "currency": "EUR"
         * }
         * ]
         * }</pre>
         *
         * @param authentication the authentication object containing the authenticated
         * user
         * @param encryptionKeyHeader the base64-encoded encryption key from request
         * header
         * @return ResponseEntity containing list of net worth allocations
         */
        @GetMapping("/networth-allocation")
        public ResponseEntity<List<NetWorthAllocation>> getNetWorthAllocation(
                        Authentication authentication,
                        @RequestHeader(ENCRYPTION_KEY_HEADER) String encryptionKeyHeader) {

                log.debug("Received net worth allocation request");

                // Validate encryption key header
                if (encryptionKeyHeader == null || encryptionKeyHeader.isBlank()) {
                        log.warn("Missing encryption key header in net worth allocation request");
                        return ResponseEntity.badRequest().build();
                }

                // Extract user ID from authentication
                User user = (User) authentication.getPrincipal();
                Long userId = user.getId();

                // Decode encryption key
                SecretKey encryptionKey = org.openfinance.util.EncryptionUtil.decodeEncryptionKey(encryptionKeyHeader);

                log.info("Fetching net worth allocation for user {}", userId);

                // Get net worth allocation
                List<NetWorthAllocation> allocations = dashboardService.getNetWorthAllocation(userId, encryptionKey);

                log.debug(
                                "Net worth allocation retrieved for user {}: {} categories",
                                userId,
                                allocations.size());

                return ResponseEntity.ok(allocations);
        }

        /**
         * Get cashflow Sankey diagram data for the authenticated user.
         *
         * <p>Returns income sources, expense categories and surplus/deficit values
         * structured for a
         * Sankey flow diagram visualization on the dashboard.
         *
         * <p><b>Request Headers:</b>
         *
         * <ul>
         * <li>Authorization: Bearer {jwt_token}
         * <li>X-Encryption-Key: {base64_encoded_key}
         * </ul>
         *
         * <p><b>Request Parameters:</b>
         *
         * <ul>
         * <li>period (optional, default=30) - Number of days to analyse
         * </ul>
         *
         * <p><b>Example Request:</b>
         *
         * <pre>GET /api/v1/dashboard/cashflow-sankey?period=30</pre>
         *
         * <p><b>Example Response (HTTP 200 OK):</b>
         *
         * <pre>{@code
         * {
         * "totalIncome": 5000.00,
         * "totalExpenses": 3500.00,
         * "surplus": 1500.00,
         * "incomeSources": [
         * { "name": "Salary", "amount": 4000.00, "color": "#10b981", "icon": null },
         * { "name": "Uncategorized", "amount": 1000.00, "color": null, "icon": null }
         * ],
         * "expenseCategories": [
         * { "name": "Housing", "amount": 1500.00, "color": "#ef4444", "icon": "home" },
         * { "name": "Food & Dining", "amount": 800.00, "color": "#f59e0b", "icon":
         * "utensils" }
         * ],
         * "period": 30
         * }
         * }</pre>
         *
         * @param authentication the authentication object
         * @param encryptionKeyHeader the base64-encoded encryption key from request
         * header
         * @param period the time period in days (default: 30)
         * @return ResponseEntity containing CashflowSankeyDto
         */
        @GetMapping("/cashflow-sankey")
        public ResponseEntity<CashflowSankeyDto> getCashflowSankey(
                        Authentication authentication,
                        @RequestHeader(value = ENCRYPTION_KEY_HEADER, required = false) String encryptionKeyHeader,
                        @RequestParam(defaultValue = "30") int period,
                        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) java.time.LocalDate startDate,
                        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) java.time.LocalDate endDate) {

                log.debug(
                                "Received cashflow sankey request for period: {} days, startDate: {}, endDate: {}",
                                period,
                                startDate,
                                endDate);

                User user = (User) authentication.getPrincipal();
                Long userId = user.getId();

                SecretKey encryptionKey = null;
                if (encryptionKeyHeader != null && !encryptionKeyHeader.isBlank()) {
                        encryptionKey = org.openfinance.util.EncryptionUtil.decodeEncryptionKey(encryptionKeyHeader);
                }

                CashflowSankeyDto sankey;
                if (startDate != null && endDate != null) {
                        log.info(
                                        "Fetching cashflow sankey for user {} with custom range: {} to {}",
                                        userId,
                                        startDate,
                                        endDate);
                        sankey = dashboardService.getCashflowSankey(userId, startDate, endDate, encryptionKey);
                } else {
                        if (period <= 0) {
                                log.warn("Invalid period parameter: {}", period);
                                return ResponseEntity.badRequest().build();
                        }
                        log.info("Fetching cashflow sankey for user {} over {} days", userId, period);
                        sankey = dashboardService.getCashflowSankey(userId, period, encryptionKey);
                }

                log.debug(
                                "Cashflow sankey for user {}: {} income sources, {} expense categories",
                                userId,
                                sankey.getIncomeSources().size(),
                                sankey.getExpenseCategories().size());

                return ResponseEntity.ok(sankey);
        }

        /**
         * Retrieves the estimated interest summary across all interest-bearing
         * accounts.
         *
         * @param authentication      the authentication object containing the
         *                            authenticated user
         * @param period              the time period string (e.g., "1M", "1Y", "30")
         *                            for historical calculation
         *                            (default: "1Y")
         * @param encryptionKeyHeader the base64-encoded encryption key from request
         *                            header
         * @return ResponseEntity containing estimated interest summary
         */
        @GetMapping("/estimated-interest")
        public ResponseEntity<EstimatedInterestSummary> getEstimatedInterest(
                        Authentication authentication,
                        @RequestParam(defaultValue = "1Y") String period,
                        @RequestHeader(ENCRYPTION_KEY_HEADER) String encryptionKeyHeader) {

                log.debug("Received estimated interest request for period: {}", period);

                if (encryptionKeyHeader == null || encryptionKeyHeader.isBlank()) {
                        log.warn("Missing encryption key header in estimated interest request");
                        return ResponseEntity.badRequest().build();
                }

                User user = (User) authentication.getPrincipal();
                Long userId = user.getId();

                SecretKey encryptionKey = org.openfinance.util.EncryptionUtil.decodeEncryptionKey(encryptionKeyHeader);

                EstimatedInterestSummary summary = dashboardService.getEstimatedInterestSummary(userId, period,
                                encryptionKey);

                return ResponseEntity.ok(summary);
        }

        /**
         * Get yearly balance variations for accounts, institutions, and total net
         * worth.
         *
         * <p>
         * Returns year-end balances from the year of the first transaction to the year
         * of the
         * last transaction, along with year-over-year variation percentages.
         *
         * @param authentication      the authentication object
         * @param encryptionKeyHeader the base64-encoded encryption key
         * @return yearly balance variation data
         */
        @GetMapping("/yearly-balance")
        public ResponseEntity<org.openfinance.dto.YearlyBalanceResponse> getYearlyBalance(
                        Authentication authentication,
                        @RequestHeader(ENCRYPTION_KEY_HEADER) String encryptionKeyHeader) {

                log.debug("Received yearly balance request");

                if (encryptionKeyHeader == null || encryptionKeyHeader.isBlank()) {
                        log.warn("Missing encryption key header in yearly balance request");
                        return ResponseEntity.badRequest().build();
                }

                User user = (User) authentication.getPrincipal();
                Long userId = user.getId();

                SecretKey encryptionKey = org.openfinance.util.EncryptionUtil.decodeEncryptionKey(encryptionKeyHeader);

                log.info("Fetching yearly balance variations for user {}", userId);

                org.openfinance.dto.YearlyBalanceResponse response = dashboardService.getYearlyBalanceVariations(userId,
                                encryptionKey);

                log.debug("Yearly balance retrieved for user {}: {} years", userId,
                                response.getYears().size());

                return ResponseEntity.ok(response);
        }
}
