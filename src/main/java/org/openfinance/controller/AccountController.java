package org.openfinance.controller;

import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.openfinance.dto.AccountRequest;
import org.openfinance.dto.AccountResponse;
import org.openfinance.dto.AccountSearchCriteria;
import org.openfinance.dto.AccountSummaryResponse;
import org.openfinance.dto.BalanceHistoryPoint;
import org.openfinance.entity.User;
import org.openfinance.exception.AccountHasTransactionsException;
import org.openfinance.service.AccountService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST controller for account management endpoints.
 *
 * <p>
 * Provides CRUD operations for financial accounts. All endpoints require
 * authentication and use
 * the user's encryption key to secure sensitive data.
 *
 * <p>
 * <strong>Endpoints:</strong>
 *
 * <ul>
 * <li>POST /api/v1/accounts - Create new account
 * <li>GET /api/v1/accounts - List all user accounts
 * <li>GET /api/v1/accounts/{id} - Get account by ID
 * <li>PUT /api/v1/accounts/{id} - Update account
 * <li>DELETE /api/v1/accounts/{id} - Soft-delete account
 * <li>GET /api/v1/accounts/{id}/balance - Get account balance
 * </ul>
 *
 * <p>
 * <strong>Security:</strong>
 *
 * <ul>
 * <li>All endpoints require JWT authentication
 * <li>Encryption key must be provided via X-Encryption-Session header
 * <li>Users can only access their own accounts
 * <li>Account name and description are encrypted at rest
 * </ul>
 *
 * <p>
 * Requirement REQ-2.2: Account Management - CRUD operations
 *
 * <p>
 * Requirement REQ-2.18: Data encryption at rest
 *
 * <p>
 * Requirement REQ-3.2: Authorization checks
 *
 * @see AccountService
 * @see AccountRequest
 * @see AccountResponse
 */
@RestController
@RequestMapping("/api/v1/accounts")
@RequiredArgsConstructor
@Slf4j
public class AccountController {
        private final AccountService accountService;

        /**
         * Creates a new financial account for the authenticated user.
         *
         * <p><strong>Request Headers:</strong>
         *
         * <ul>
         * <li>Authorization: Bearer {jwt_token}
         * <li>X-Encryption-Session: {base64_encoded_key}
         * </ul>
         *
         * <p><strong>Request Body:</strong>
         *
         * <pre>{@code
         * {
         * "name": "Chase Checking",
         * "type": "CHECKING",
         * "currency": "USD",
         * "initialBalance": 5000.00,
         * "description": "Primary checking account"
         * }
         * }</pre>
         *
         * <p><strong>Success Response (HTTP 201 Created):</strong>
         *
         * <pre>{@code
         * {
         * "id": 1,
         * "name": "Chase Checking",
         * "type": "CHECKING",
         * "currency": "USD",
         * "balance": 5000.00,
         * "description": "Primary checking account",
         * "isActive": true,
         * "createdAt": "2026-01-31T10:00:00",
         * "updatedAt": null
         * }
         * }</pre>
         *
         * <p>Requirement REQ-2.2.1: Create account
         *
         * @param request account creation request
         * @param encodedKey Base64-encoded encryption key from header
         * @param authentication Spring Security authentication object
         * @return HTTP 201 Created with AccountResponse
         */
        @PostMapping
        public ResponseEntity<AccountResponse> createAccount(
                        @Valid @RequestBody AccountRequest request,
                        Authentication authentication) {

                log.info("Creating account for user");
                User user = (User) authentication.getPrincipal();
                AccountResponse response = accountService.createAccount(user.getId(), request);

                log.info("Account created successfully: id={}", response.getId());

                return ResponseEntity.status(HttpStatus.CREATED).body(response);
        }

        /**
         * Retrieves all active accounts for the authenticated user.
         *
         * <p>
         * <strong>Request Headers:</strong>
         *
         * <ul>
         * <li>Authorization: Bearer {jwt_token}
         * <li>X-Encryption-Session: {base64_encoded_key}
         * </ul>
         *
         * <p>
         * <strong>Query Parameters:</strong>
         *
         * <ul>
         * <li>filter (optional): "all", "active", or "closed" (default: "active")
         * <li>summary (optional): when {@code true}, returns a lightweight {@link
         * AccountSummaryResponse} list instead of the full {@link AccountResponse}
         * (default:
         * false)
         * </ul>
         *
         * <p>
         * Requirement REQ-2.2.1: List user accounts
         *
         * <p>
         * Requirement TASK-14.1.3: Sparse fieldsets via {@code ?summary=true}
         *
         * @param filter         optional filter: "all", "active" (default), or "closed"
         * @param summary        when {@code true} returns lightweight
         *                       AccountSummaryResponse list
         * @param encodedKey     Base64-encoded encryption key from header
         * @param authentication Spring Security authentication object
         * @return HTTP 200 OK with list of AccountResponse or AccountSummaryResponse
         *         (may be empty)
         */
        @GetMapping
        public ResponseEntity<?> getAllAccounts(
                        @org.springframework.web.bind.annotation.RequestParam(value = "filter", required = false, defaultValue = "active") String filter,
                        @RequestParam(value = "summary", required = false, defaultValue = "false") boolean summary,
                        Authentication authentication) {

                log.info("Retrieving accounts for user with filter: {}, summary: {}", filter, summary);
                User user = (User) authentication.getPrincipal();
                if (summary) {
                        // Return lightweight summary projection (TASK-14.1.3)
                        List<AccountSummaryResponse> summaries = accountService.getAccountsSummary(user.getId());
                        log.info("Retrieved {} account summaries for user", summaries.size());
                        return ResponseEntity.ok(summaries);
                }

                // Determine filter value
                Boolean isActiveFilter;
                switch (filter.toLowerCase()) {
                        case "all":
                                isActiveFilter = null; // null means fetch all accounts
                                break;
                        case "closed":
                                isActiveFilter = false; // fetch only closed accounts
                                break;
                        case "active":
                        default:
                                isActiveFilter = true; // fetch only active accounts (default)
                                break;
                }

                List<AccountResponse> accounts = accountService.getAccountsByUserIdWithFilter(
                                user.getId(), isActiveFilter);

                log.info("Retrieved {} accounts for user with filter: {}", accounts.size(), filter);

                return ResponseEntity.ok(accounts);
        }

        /**
         * Searches accounts with filters and pagination.
         *
         * <p><strong>Request Headers:</strong>
         *
         * <ul>
         * <li>Authorization: Bearer {jwt_token}
         * <li>X-Encryption-Session: {base64_encoded_key}
         * </ul>
         *
         * <p><strong>Query Parameters:</strong>
         *
         * <ul>
         * <li>keyword (optional): Search in account name
         * <li>type (optional): Filter by account type (CHECKING, SAVINGS, CREDIT_CARD,
         * INVESTMENT,
         * CASH, OTHER)
         * <li>currency (optional): Filter by currency code (USD, EUR, etc.)
         * <li>isActive (optional): Filter by active status (true/false)
         * <li>balanceMin (optional): Minimum balance
         * <li>balanceMax (optional): Maximum balance
         * <li>institution (optional): Filter by institution name
         * <li>lowBalance (optional): When true, only accounts with balance below 1000
         * <li>page (optional): Page number (0-indexed, default: 0)
         * <li>size (optional): Page size (default: 20)
         * <li>sort (optional): Sort field and direction (e.g., "name,asc" or
         * "balance,desc")
         * </ul>
         *
         * <p><strong>Success Response (HTTP 200 OK):</strong>
         *
         * <pre>{@code
         * {
         * "content": [
         * {
         * "id": 1,
         * "name": "Chase Checking",
         * "type": "CHECKING",
         * "currency": "USD",
         * "balance": 5000.00,
         * "isActive": true,
         * ...
         * }
         * ],
         * "totalElements": 10,
         * "totalPages": 1,
         * "number": 0,
         * "size": 20
         * }
         * }</pre>
         *
         * @param keyword optional keyword to search in account name
         * @param type optional account type filter
         * @param currency optional currency filter
         * @param isActive optional active status filter
         * @param balanceMin optional minimum balance filter
         * @param balanceMax optional maximum balance filter
         * @param institution optional institution name filter
         * @param lowBalance optional flag to return only accounts with balance below
         * 1000
         * @param pageable pagination and sorting parameters
         * @param encodedKey Base64-encoded encryption key from header
         * @param authentication Spring Security authentication object
         * @return HTTP 200 OK with Page<AccountResponse>
         */
        @GetMapping("/search")
        public ResponseEntity<Page<AccountResponse>> searchAccounts(
                        @RequestParam(required = false) String keyword,
                        @RequestParam(required = false) org.openfinance.entity.AccountType type,
                        @RequestParam(required = false) String currency,
                        @RequestParam(required = false) Boolean isActive,
                        @RequestParam(required = false) java.math.BigDecimal balanceMin,
                        @RequestParam(required = false) java.math.BigDecimal balanceMax,
                        @RequestParam(required = false) String institution,
                        @RequestParam(required = false) Boolean lowBalance,
                        @PageableDefault(size = 20, sort = "name", direction = Sort.Direction.ASC) Pageable pageable,
                        Authentication authentication) {

                log.info(
                                "Searching accounts for user: keyword={}, type={}, currency={}, isActive={}, page={}, size={}",
                                keyword,
                                type,
                                currency,
                                isActive,
                                pageable.getPageNumber(),
                                pageable.getPageSize());
                User user = (User) authentication.getPrincipal();
                // Build search criteria
                AccountSearchCriteria criteria = AccountSearchCriteria.builder()
                                .keyword(keyword)
                                .type(type)
                                .currency(currency)
                                .isActive(isActive)
                                .balanceMin(balanceMin)
                                .balanceMax(balanceMax)
                                .institution(institution)
                                .lowBalance(lowBalance)
                                .build();

                // Execute search with pagination
                Page<AccountResponse> results = accountService.searchAccounts(user.getId(), criteria, pageable);

                log.info(
                                "Search returned {} accounts (page {}/{}, total: {})",
                                results.getNumberOfElements(),
                                results.getNumber() + 1,
                                results.getTotalPages(),
                                results.getTotalElements());

                return ResponseEntity.ok(results);
        }

        /**
         * Retrieves a specific account by ID.
         *
         * <p>
         * <strong>Request Headers:</strong>
         *
         * <ul>
         * <li>Authorization: Bearer {jwt_token}
         * <li>X-Encryption-Session: {base64_encoded_key}
         * </ul>
         *
         * <p>
         * <strong>Error Responses:</strong>
         *
         * <ul>
         * <li>HTTP 404 Not Found - Account not found or doesn't belong to user
         * </ul>
         *
         * <p>
         * Requirement REQ-2.2.1: Get account by ID
         *
         * <p>
         * Requirement REQ-3.2: Authorization - verify ownership
         *
         * @param accountId      the account ID
         * @param encodedKey     Base64-encoded encryption key from header
         * @param authentication Spring Security authentication object
         * @return HTTP 200 OK with AccountResponse
         */
        @GetMapping("/{id}")
        public ResponseEntity<AccountResponse> getAccountById(
                        @PathVariable("id") Long accountId,
                        Authentication authentication) {

                log.info("Retrieving account: id={}", accountId);
                User user = (User) authentication.getPrincipal();
                AccountResponse response = accountService.getAccountById(accountId, user.getId());

                log.info("Account retrieved successfully: id={}", accountId);

                return ResponseEntity.ok(response);
        }

        /**
         * Updates an existing account.
         *
         * <p>
         * <strong>Request Headers:</strong>
         *
         * <ul>
         * <li>Authorization: Bearer {jwt_token}
         * <li>X-Encryption-Session: {base64_encoded_key}
         * </ul>
         *
         * <p>
         * <strong>Request Body:</strong> Same as create account request
         *
         * <p>
         * <strong>Error Responses:</strong>
         *
         * <ul>
         * <li>HTTP 400 Bad Request - Validation errors
         * <li>HTTP 404 Not Found - Account not found or doesn't belong to user
         * </ul>
         *
         * <p>
         * Requirement REQ-2.2.3: Update account
         *
         * <p>
         * Requirement REQ-3.2: Authorization - verify ownership
         *
         * @param accountId      the account ID
         * @param request        account update request
         * @param encodedKey     Base64-encoded encryption key from header
         * @param authentication Spring Security authentication object
         * @return HTTP 200 OK with updated AccountResponse
         */
        @PutMapping("/{id}")
        public ResponseEntity<AccountResponse> updateAccount(
                        @PathVariable("id") Long accountId,
                        @Valid @RequestBody AccountRequest request,
                        Authentication authentication) {

                log.info("Updating account: id={}", accountId);
                User user = (User) authentication.getPrincipal();
                AccountResponse response = accountService.updateAccount(accountId, user.getId(), request);

                log.info("Account updated successfully: id={}", accountId);

                return ResponseEntity.ok(response);
        }

        /**
         * Soft-deletes an account (sets isActive = false).
         *
         * <p>
         * Soft deletion preserves historical data while hiding the account from active
         * views.
         *
         * <p>
         * <strong>Request Headers:</strong>
         *
         * <ul>
         * <li>Authorization: Bearer {jwt_token}
         * </ul>
         *
         * <p>
         * <strong>Error Responses:</strong>
         *
         * <ul>
         * <li>HTTP 404 Not Found - Account not found or doesn't belong to user
         * </ul>
         *
         * <p>
         * Requirement REQ-2.2.4: Soft-delete account
         *
         * <p>
         * Requirement REQ-3.2: Authorization - verify ownership
         *
         * <p>
         * Requirement REQ-2.5: Data integrity - prevent deletion of accounts with
         * active
         * transactions
         *
         * @param accountId      the account ID
         * @param encodedKey     the base64-encoded encryption key (for decrypting
         *                       account name in error
         *                       messages)
         * @param authentication Spring Security authentication object
         * @return HTTP 204 No Content on success
         * @throws AccountHasTransactionsException if account has active transactions
         *                                         (HTTP 400)
         */
        @DeleteMapping("/{id}")
        public ResponseEntity<Void> deleteAccount(
                        @PathVariable("id") Long accountId,
                        Authentication authentication) {

                log.info("Deleting account: id={}", accountId);
                User user = (User) authentication.getPrincipal();
                accountService.deleteAccount(accountId, user.getId());

                log.info("Account deleted successfully: id={}", accountId);

                return ResponseEntity.noContent().build();
        }

        /**
         * Closes an account (sets isActive = false).
         *
         * <p>
         * Closing an account is a soft operation that preserves all historical data
         * including
         * transactions. The account will no longer appear in active views but can be
         * reopened later.
         *
         * <p>
         * <strong>Request Headers:</strong>
         *
         * <ul>
         * <li>Authorization: Bearer {jwt_token}
         * </ul>
         *
         * <p>
         * <strong>Error Responses:</strong>
         *
         * <ul>
         * <li>HTTP 404 Not Found - Account not found or doesn't belong to user
         * </ul>
         *
         * <p>
         * Requirement: Close account functionality - Toggle Active/Close state
         *
         * @param accountId      the account ID
         * @param authentication Spring Security authentication object
         * @return HTTP 204 No Content on success
         */
        @PostMapping("/{id}/close")
        public ResponseEntity<Void> closeAccount(
                        @PathVariable("id") Long accountId, Authentication authentication) {

                log.info("Closing account: id={}", accountId);

                User user = (User) authentication.getPrincipal();

                accountService.closeAccount(accountId, user.getId());

                log.info("Account closed successfully: id={}", accountId);

                return ResponseEntity.noContent().build();
        }

        /**
         * Reopens a closed account (sets isActive = true).
         *
         * <p>
         * Reopening an account makes it active again. All historical data including
         * transactions is
         * preserved and the account will appear in active views.
         *
         * <p>
         * <strong>Request Headers:</strong>
         *
         * <ul>
         * <li>Authorization: Bearer {jwt_token}
         * </ul>
         *
         * <p>
         * <strong>Error Responses:</strong>
         *
         * <ul>
         * <li>HTTP 404 Not Found - Account not found or doesn't belong to user
         * </ul>
         *
         * <p>
         * Requirement: Close account functionality - Toggle Active/Close state
         *
         * @param accountId      the account ID
         * @param authentication Spring Security authentication object
         * @return HTTP 204 No Content on success
         */
        @PostMapping("/{id}/reopen")
        public ResponseEntity<Void> reopenAccount(
                        @PathVariable("id") Long accountId, Authentication authentication) {

                log.info("Reopening account: id={}", accountId);

                User user = (User) authentication.getPrincipal();

                accountService.reopenAccount(accountId, user.getId());

                log.info("Account reopened successfully: id={}", accountId);

                return ResponseEntity.noContent().build();
        }

        /**
         * Permanently deletes an account along with all associated transactions.
         *
         * <p>
         * <strong>WARNING:</strong> This is a destructive operation that cannot be
         * undone. All
         * transactions associated with this account will be permanently deleted.
         *
         * <p>
         * <strong>Request Headers:</strong>
         *
         * <ul>
         * <li>Authorization: Bearer {jwt_token}
         * </ul>
         *
         * <p>
         * <strong>Error Responses:</strong>
         *
         * <ul>
         * <li>HTTP 404 Not Found - Account not found or doesn't belong to user
         * </ul>
         *
         * <p>
         * Requirement: Delete account functionality - Permanently delete accounts
         *
         * @param accountId      the account ID
         * @param encodedKey     the base64-encoded encryption key (required for
         *                       authorization)
         * @param authentication Spring Security authentication object
         * @return HTTP 204 No Content on success
         */
        @DeleteMapping("/{id}/permanent")
        public ResponseEntity<Void> permanentDeleteAccount(
                        @PathVariable("id") Long accountId,
                        Authentication authentication) {

                log.warn("Permanently deleting account: id={}", accountId);
                User user = (User) authentication.getPrincipal();
                accountService.permanentDeleteAccount(accountId, user.getId());

                log.warn("Account permanently deleted: id={}", accountId);

                return ResponseEntity.noContent().build();
        }

        /**
         * Retrieves the current balance of an account.
         *
         * <p><strong>Request Headers:</strong>
         *
         * <ul>
         * <li>Authorization: Bearer {jwt_token}
         * </ul>
         *
         * <p><strong>Success Response (HTTP 200 OK):</strong>
         *
         * <pre>{@code
         * {
         * "balance": 5000.00
         * }
         * }</pre>
         *
         * <p>Requirement REQ-2.2.5: Get account balance
         *
         * @param accountId the account ID
         * @param authentication Spring Security authentication object
         * @return HTTP 200 OK with balance
         */
        @GetMapping("/{id}/balance")
        public ResponseEntity<BalanceResponse> getAccountBalance(
                        @PathVariable("id") Long accountId, Authentication authentication) {

                log.info("Retrieving balance for account: id={}", accountId);

                User user = (User) authentication.getPrincipal();

                java.math.BigDecimal balance = accountService.getAccountBalance(accountId, user.getId());

                log.info("Balance retrieved successfully: id={}, balance={}", accountId, balance);

                return ResponseEntity.ok(new BalanceResponse(balance));
        }

        /** DTO for balance response. */
        public static record BalanceResponse(java.math.BigDecimal balance) {
        }

        /**
         * Retrieves the balance history for an account over time.
         *
         * <p>
         * <strong>Request Headers:</strong>
         *
         * <ul>
         * <li>Authorization: Bearer {jwt_token}
         * <li>X-Encryption-Session: {base64_encoded_key}
         * </ul>
         *
         * <p>
         * <strong>Query Parameters:</strong>
         *
         * <ul>
         * <li>period (optional): Time period - "1M" (1 month), "3M" (3 months), "6M" (6
         * months), "1Y"
         * (1 year), "ALL" (all time). Default: "3M"
         * </ul>
         *
         * <p>
         * <strong>Success Response (HTTP 200 OK):</strong>
         *
         * <pre>{@code
         * [
         *   { "date": "2026-01-15", "balance": 5000.00 },
         *   { "date": "2026-01-16", "balance": 4800.00 },
         *   { "date": "2026-01-17", "balance": 5100.00 }
         * ]
         * }</pre>
         *
         * <p>
         * Requirement REQ-2.6.1.2: Account Balance Tracking - Historical snapshots
         *
         * @param accountId      the account ID
         * @param period         the time period for history
         * @param encodedKey     Base64-encoded encryption key from header
         * @param authentication Spring Security authentication object
         * @return HTTP 200 OK with list of balance history points
         */
        @GetMapping("/{id}/balance-history")
        public ResponseEntity<List<BalanceHistoryPoint>> getAccountBalanceHistory(
                        @PathVariable("id") Long accountId,
                        @org.springframework.web.bind.annotation.RequestParam(value = "period", required = false, defaultValue = "3M") String period,
                        Authentication authentication) {

                log.info("Retrieving balance history for account: id={}, period={}", accountId, period);
                User user = (User) authentication.getPrincipal();
                List<BalanceHistoryPoint> history = accountService.getAccountBalanceHistory(
                                accountId, user.getId(), period);

                log.info(
                                "Balance history retrieved successfully: id={}, points={}",
                                accountId,
                                history.size());

                return ResponseEntity.ok(history);
        }
}
