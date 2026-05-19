package org.openfinance.controller;

import jakarta.validation.Valid;
import java.time.LocalDate;
import java.util.List;
import java.util.stream.Collectors;
import javax.crypto.SecretKey;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.openfinance.dto.TransactionRequest;
import org.openfinance.dto.TransactionResponse;
import org.openfinance.dto.TransactionSplitResponse;
import org.openfinance.dto.TransferUpdateRequest;
import org.openfinance.entity.TransactionType;
import org.openfinance.entity.User;
import org.openfinance.service.TransactionService;
import org.openfinance.util.EncryptionUtil;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.format.annotation.DateTimeFormat;
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
 * REST controller for transaction management endpoints.
 *
 * <p>Provides CRUD operations for financial transactions including income, expenses, and transfers
 * between accounts. All endpoints require authentication and use the user's encryption key to
 * secure sensitive data (description, notes).
 *
 * <p><strong>Endpoints:</strong>
 *
 * <ul>
 *   <li>POST /api/v1/transactions - Create new INCOME or EXPENSE transaction
 *   <li>POST /api/v1/transactions/transfer - Create transfer between accounts
 *   <li>GET /api/v1/transactions/{id} - Get transaction by ID
 *   <li>GET /api/v1/transactions - List transactions with optional filters
 *   <li>PUT /api/v1/transactions/{id} - Update transaction
 *   <li>DELETE /api/v1/transactions/{id} - Soft-delete transaction
 * </ul>
 *
 * <p><strong>Security:</strong>
 *
 * <ul>
 *   <li>All endpoints require JWT authentication
 *   <li>Encryption key must be provided via X-Encryption-Key header
 *   <li>Users can only access their own transactions
 *   <li>Transaction description and notes are encrypted at rest
 * </ul>
 *
 * <p>Requirement REQ-2.4.1: Transaction Management - CRUD operations
 *
 * <p>Requirement REQ-2.4.1.1: Create transactions
 *
 * <p>Requirement REQ-2.4.1.2: Update transactions
 *
 * <p>Requirement REQ-2.4.1.3: Soft-delete transactions
 *
 * <p>Requirement REQ-2.4.1.4: Create transfer transactions
 *
 * <p>Requirement REQ-2.18: Data encryption at rest
 *
 * <p>Requirement REQ-3.2: Authorization checks
 *
 * @see TransactionService
 * @see TransactionRequest
 * @see TransactionResponse
 */
@RestController
@RequestMapping("/api/v1/transactions")
@RequiredArgsConstructor
@Slf4j
public class TransactionController {

    private static final String ENCRYPTION_KEY_HEADER = "X-Encryption-Key";

    private final TransactionService transactionService;

    /**
     * Creates a new transaction (INCOME or EXPENSE) for the authenticated user.
     *
     * <p>For TRANSFER transactions, use the POST /api/v1/transactions/transfer endpoint instead.
     *
     * <p><strong>Request Headers:</strong>
     *
     * <ul>
     *   <li>Authorization: Bearer {jwt_token}
     *   <li>X-Encryption-Key: {base64_encoded_key}
     * </ul>
     *
     * <p><strong>Request Body:</strong>
     *
     * <pre>{@code
     * {
     * "accountId": 1,
     * "type": "INCOME",
     * "amount": 1500.00,
     * "currency": "USD",
     * "categoryId": 5,
     * "date": "2026-01-31",
     * "description": "Monthly salary",
     * "notes": "Salary payment from employer",
     * "payee": "Employer Inc",
     * "tags": "salary,income",
     * "isReconciled": false
     * }
     * }</pre>
     *
     * <p><strong>Success Response (HTTP 201 Created):</strong>
     *
     * <pre>{@code
     * {
     * "id": 100,
     * "accountId": 1,
     * "accountName": "Chase Checking",
     * "toAccountId": null,
     * "toAccountName": null,
     * "type": "INCOME",
     * "amount": 1500.00,
     * "currency": "USD",
     * "categoryId": 5,
     * "categoryName": "Salary",
     * "categoryIcon": "💰",
     * "categoryColor": "#4CAF50",
     * "date": "2026-01-31",
     * "description": "Monthly salary",
     * "notes": "Salary payment from employer",
     * "payee": "Employer Inc",
     * "tags": "salary,income",
     * "transferId": null,
     * "isReconciled": false,
     * "isDeleted": false,
     * "createdAt": "2026-01-31T13:00:00",
     * "updatedAt": null
     * }
     * }</pre>
     *
     * <p><strong>Error Responses:</strong>
     *
     * <ul>
     *   <li>HTTP 400 Bad Request - Validation errors or invalid data
     *   <li>HTTP 404 Not Found - Account or category not found
     *   <li>HTTP 400 Bad Request - If type is TRANSFER (use transfer endpoint)
     * </ul>
     *
     * <p>Requirement REQ-2.4.1.1: Create transaction
     *
     * @param request transaction creation request
     * @param encodedKey Base64-encoded encryption key from header
     * @param authentication Spring Security authentication object
     * @return HTTP 201 Created with TransactionResponse
     */
    @PostMapping
    public ResponseEntity<TransactionResponse> createTransaction(
            @Valid @RequestBody TransactionRequest request,
            @RequestHeader(value = ENCRYPTION_KEY_HEADER, required = false) String encodedKey,
            Authentication authentication) {

        log.info("Creating transaction for user: type={}", request.getType());

        if (encodedKey == null || encodedKey.trim().isEmpty()) {
            throw new IllegalArgumentException("Encryption key header is required");
        }

        User user = (User) authentication.getPrincipal();
        SecretKey encryptionKey = EncryptionUtil.decodeEncryptionKey(encodedKey);

        TransactionResponse response =
                transactionService.createTransaction(user.getId(), request, encryptionKey);

        log.info(
                "Transaction created successfully: id={}, type={}, accountId={}",
                response.getId(),
                response.getType(),
                response.getAccountId());

        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    /**
     * Creates a transfer transaction between two accounts.
     *
     * <p>A transfer creates two linked transactions:
     *
     * <ul>
     *   <li>Source transaction (EXPENSE) - money leaving source account
     *   <li>Destination transaction (INCOME) - money entering destination account
     * </ul>
     *
     * Both transactions share a common {@code transferId} for tracking.
     *
     * <p><strong>Request Headers:</strong>
     *
     * <ul>
     *   <li>Authorization: Bearer {jwt_token}
     *   <li>X-Encryption-Key: {base64_encoded_key}
     * </ul>
     *
     * <p><strong>Request Body:</strong>
     *
     * <pre>{@code
     * {
     * "accountId": 1,
     * "toAccountId": 2,
     * "type": "TRANSFER",
     * "amount": 500.00,
     * "currency": "USD",
     * "date": "2026-01-31",
     * "description": "Transfer to savings",
     * "notes": "Monthly savings transfer"
     * }
     * }</pre>
     *
     * <p><strong>Success Response (HTTP 201 Created):</strong> Returns the source transaction. The
     * destination transaction shares the same {@code transferId}.
     *
     * <p><strong>Error Responses:</strong>
     *
     * <ul>
     *   <li>HTTP 400 Bad Request - Same account for source and destination
     *   <li>HTTP 400 Bad Request - Category provided (transfers have no category)
     *   <li>HTTP 404 Not Found - Either account not found
     * </ul>
     *
     * <p>Requirement REQ-2.4.1.4: Create transfer transactions
     *
     * @param request transfer creation request (type must be TRANSFER)
     * @param encodedKey Base64-encoded encryption key from header
     * @param authentication Spring Security authentication object
     * @return HTTP 201 Created with source TransactionResponse
     */
    @PostMapping("/transfer")
    public ResponseEntity<TransactionResponse> createTransfer(
            @Valid @RequestBody TransactionRequest request,
            @RequestHeader(value = ENCRYPTION_KEY_HEADER, required = false) String encodedKey,
            Authentication authentication) {

        log.info(
                "Creating transfer for user: accountId={}, toAccountId={}",
                request.getAccountId(),
                request.getToAccountId());

        if (encodedKey == null || encodedKey.trim().isEmpty()) {
            throw new IllegalArgumentException("Encryption key header is required");
        }

        User user = (User) authentication.getPrincipal();
        SecretKey encryptionKey = EncryptionUtil.decodeEncryptionKey(encodedKey);

        TransactionResponse response =
                transactionService.createTransfer(user.getId(), request, encryptionKey);

        log.info(
                "Transfer created successfully: transferId={}, accountId={}, toAccountId={}",
                response.getTransferId(),
                response.getAccountId(),
                response.getToAccountId());

        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    /**
     * Updates an existing transfer transaction atomically.
     *
     * <p>This endpoint updates both sides of a transfer (source EXPENSE and destination INCOME) in
     * a single atomic operation. The transferId in the path identifies the transfer to update.
     *
     * <p><strong>Request Headers:</strong>
     *
     * <ul>
     *   <li>Authorization: Bearer {jwt_token}
     *   <li>X-Encryption-Key: {base64_encoded_key}
     * </ul>
     *
     * <p><strong>Request Body:</strong>
     *
     * <pre>{@code
     * {
     * "fromAccountId": 1,
     * "toAccountId": 2,
     * "amount": 750.00,
     * "currency": "USD",
     * "date": "2026-02-01",
     * "description": "Updated transfer description",
     * "notes": "Updated notes",
     * "payee": "Updated payee",
     * "tags": "savings,monthly",
     * "isReconciled": false
     * }
     * }</pre>
     *
     * <p><strong>Success Response (HTTP 200 OK):</strong> Returns the updated source transaction.
     * Both transactions share the same transferId.
     *
     * <p><strong>Error Responses:</strong>
     *
     * <ul>
     *   <li>HTTP 400 Bad Request - Same account for source and destination
     *   <li>HTTP 400 Bad Request - Validation errors
     *   <li>HTTP 404 Not Found - Transfer not found or doesn't belong to user
     * </ul>
     *
     * <p>Requirement REQ-2.4.1.4: Update transfer transactions
     *
     * @param transferId the transfer ID (UUID) linking the two transactions
     * @param request transfer update request with new values
     * @param encodedKey Base64-encoded encryption key from header
     * @param authentication Spring Security authentication object
     * @return HTTP 200 OK with updated TransactionResponse
     */
    @PutMapping("/transfers/{transferId}")
    public ResponseEntity<TransactionResponse> updateTransfer(
            @PathVariable("transferId") String transferId,
            @Valid @RequestBody TransferUpdateRequest request,
            @RequestHeader(value = ENCRYPTION_KEY_HEADER, required = false) String encodedKey,
            Authentication authentication) {

        log.info(
                "Updating transfer: transferId={}, fromAccountId={}, toAccountId={}",
                transferId,
                request.getFromAccountId(),
                request.getToAccountId());

        if (encodedKey == null || encodedKey.trim().isEmpty()) {
            throw new IllegalArgumentException("Encryption key header is required");
        }

        User user = (User) authentication.getPrincipal();
        SecretKey encryptionKey = EncryptionUtil.decodeEncryptionKey(encodedKey);

        TransactionResponse response =
                transactionService.updateTransfer(transferId, user.getId(), request, encryptionKey);

        log.info(
                "Transfer updated successfully: transferId={}, accountId={}, toAccountId={}",
                transferId,
                response.getAccountId(),
                response.getToAccountId());

        return ResponseEntity.ok(response);
    }

    /**
     * Retrieves a specific transaction by ID.
     *
     * <p><strong>Request Headers:</strong>
     *
     * <ul>
     *   <li>Authorization: Bearer {jwt_token}
     *   <li>X-Encryption-Key: {base64_encoded_key}
     * </ul>
     *
     * <p><strong>Error Responses:</strong>
     *
     * <ul>
     *   <li>HTTP 404 Not Found - Transaction not found or doesn't belong to user
     * </ul>
     *
     * <p>Requirement REQ-2.4.1.1: Get transaction by ID
     *
     * <p>Requirement REQ-3.2: Authorization - verify ownership
     *
     * @param transactionId the transaction ID
     * @param encodedKey Base64-encoded encryption key from header
     * @param authentication Spring Security authentication object
     * @return HTTP 200 OK with TransactionResponse
     */
    @GetMapping("/{id}")
    public ResponseEntity<TransactionResponse> getTransactionById(
            @PathVariable("id") Long transactionId,
            @RequestHeader(value = ENCRYPTION_KEY_HEADER, required = false) String encodedKey,
            Authentication authentication) {

        log.info("Retrieving transaction: id={}", transactionId);

        if (encodedKey == null || encodedKey.trim().isEmpty()) {
            throw new IllegalArgumentException("Encryption key header is required");
        }

        User user = (User) authentication.getPrincipal();
        SecretKey encryptionKey = EncryptionUtil.decodeEncryptionKey(encodedKey);

        TransactionResponse response =
                transactionService.getTransactionById(transactionId, user.getId(), encryptionKey);

        log.info(
                "Transaction retrieved successfully: id={}, type={}",
                transactionId,
                response.getType());

        return ResponseEntity.ok(response);
    }

    /**
     * Retrieves transactions with optional filters.
     *
     * <p>Supports filtering by:
     *
     * <ul>
     *   <li><strong>accountId</strong> - transactions for specific account
     *   <li><strong>dateFrom, dateTo</strong> - transactions within date range
     *   <li><strong>type</strong> - filter by INCOME, EXPENSE, or TRANSFER
     * </ul>
     *
     * <p>If no filters are provided, returns all user transactions. Filters can be combined (e.g.,
     * accountId + dateFrom + dateTo).
     *
     * <p><strong>Request Headers:</strong>
     *
     * <ul>
     *   <li>Authorization: Bearer {jwt_token}
     *   <li>X-Encryption-Key: {base64_encoded_key}
     * </ul>
     *
     * <p><strong>Query Parameters:</strong>
     *
     * <ul>
     *   <li>accountId (optional) - Long - Filter by account ID
     *   <li>dateFrom (optional) - LocalDate (ISO-8601: YYYY-MM-DD) - Start date (inclusive)
     *   <li>dateTo (optional) - LocalDate (ISO-8601: YYYY-MM-DD) - End date (inclusive)
     *   <li>type (optional) - TransactionType - Filter by type (INCOME, EXPENSE, TRANSFER)
     * </ul>
     *
     * <p><strong>Example Requests:</strong>
     *
     * <pre>
     * GET /api/v1/transactions?accountId=1
     * GET /api/v1/transactions?dateFrom=2026-01-01&dateTo=2026-01-31
     * GET /api/v1/transactions?type=INCOME
     * GET /api/v1/transactions?accountId=1&type=EXPENSE&dateFrom=2026-01-01
     * </pre>
     *
     * <p><strong>Success Response (HTTP 200 OK):</strong> Returns array of TransactionResponse
     * objects (may be empty).
     *
     * <p>Requirement REQ-2.4.1.1: List user transactions
     *
     * <p>Requirement REQ-2.3.5: Filter transactions by criteria
     *
     * @param accountId optional account ID filter
     * @param dateFrom optional start date filter (ISO-8601 format)
     * @param dateTo optional end date filter (ISO-8601 format)
     * @param type optional transaction type filter
     * @param encodedKey Base64-encoded encryption key from header
     * @param authentication Spring Security authentication object
     * @return HTTP 200 OK with list of TransactionResponse (may be empty)
     */
    @GetMapping
    public ResponseEntity<List<TransactionResponse>> getTransactions(
            @RequestParam(required = false) Long accountId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
                    LocalDate dateFrom,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
                    LocalDate dateTo,
            @RequestParam(required = false) TransactionType type,
            @RequestHeader(value = ENCRYPTION_KEY_HEADER, required = false) String encodedKey,
            Authentication authentication) {

        log.info(
                "Retrieving transactions for user: accountId={}, dateFrom={}, dateTo={}, type={}",
                accountId,
                dateFrom,
                dateTo,
                type);

        if (encodedKey == null || encodedKey.trim().isEmpty()) {
            throw new IllegalArgumentException("Encryption key header is required");
        }

        User user = (User) authentication.getPrincipal();
        SecretKey encryptionKey = EncryptionUtil.decodeEncryptionKey(encodedKey);

        List<TransactionResponse> transactions;

        // Apply filters based on provided parameters
        if (accountId != null) {
            // Filter by account (with optional date range)
            transactions =
                    transactionService.getTransactionsByAccount(
                            user.getId(), accountId, encryptionKey);

            // Apply date range filter if provided
            if (dateFrom != null && dateTo != null) {
                transactions =
                        transactions.stream()
                                .filter(
                                        t ->
                                                !t.getDate().isBefore(dateFrom)
                                                        && !t.getDate().isAfter(dateTo))
                                .collect(Collectors.toList());
            }
        } else if (dateFrom != null && dateTo != null) {
            // Filter by date range only
            transactions =
                    transactionService.getTransactionsByDateRange(
                            user.getId(), dateFrom, dateTo, encryptionKey);
        } else {
            // REQ-2.4.1.1: No filters - return all user transactions
            transactions = transactionService.getAllTransactions(user.getId(), encryptionKey);
        }

        // Apply type filter if provided
        if (type != null) {
            transactions =
                    transactions.stream()
                            .filter(t -> t.getType() == type)
                            .collect(Collectors.toList());
        }

        log.info("Retrieved {} transactions for user", transactions.size());

        return ResponseEntity.ok(transactions);
    }

    /**
     * Updates an existing transaction.
     *
     * <p><strong>Important:</strong> Transfer transactions cannot be updated using this endpoint.
     * To modify a transfer, delete it and create a new one.
     *
     * <p><strong>Request Headers:</strong>
     *
     * <ul>
     *   <li>Authorization: Bearer {jwt_token}
     *   <li>X-Encryption-Key: {base64_encoded_key}
     * </ul>
     *
     * <p><strong>Request Body:</strong> Same as create transaction request
     *
     * <p><strong>Error Responses:</strong>
     *
     * <ul>
     *   <li>HTTP 400 Bad Request - Validation errors
     *   <li>HTTP 400 Bad Request - Attempting to update a transfer transaction
     *   <li>HTTP 404 Not Found - Transaction not found or doesn't belong to user
     * </ul>
     *
     * <p>Requirement REQ-2.4.1.2: Update transaction
     *
     * <p>Requirement REQ-3.2: Authorization - verify ownership
     *
     * @param transactionId the transaction ID
     * @param request transaction update request
     * @param encodedKey Base64-encoded encryption key from header
     * @param authentication Spring Security authentication object
     * @return HTTP 200 OK with updated TransactionResponse
     */
    @PutMapping("/{id}")
    public ResponseEntity<TransactionResponse> updateTransaction(
            @PathVariable("id") Long transactionId,
            @Valid @RequestBody TransactionRequest request,
            @RequestHeader(value = ENCRYPTION_KEY_HEADER, required = false) String encodedKey,
            Authentication authentication) {

        log.info("Updating transaction: id={}", transactionId);

        if (encodedKey == null || encodedKey.trim().isEmpty()) {
            throw new IllegalArgumentException("Encryption key header is required");
        }

        User user = (User) authentication.getPrincipal();
        SecretKey encryptionKey = EncryptionUtil.decodeEncryptionKey(encodedKey);

        TransactionResponse response =
                transactionService.updateTransaction(
                        transactionId, user.getId(), request, encryptionKey);

        log.info(
                "Transaction updated successfully: id={}, type={}, accountId={}",
                transactionId,
                response.getType(),
                response.getAccountId());

        return ResponseEntity.ok(response);
    }

    /**
     * Soft-deletes a transaction (sets isDeleted = true).
     *
     * <p>Soft deletion preserves historical data while hiding the transaction from active views. If
     * the transaction is part of a transfer, both linked transactions are deleted.
     *
     * <p><strong>Request Headers:</strong>
     *
     * <ul>
     *   <li>Authorization: Bearer {jwt_token}
     * </ul>
     *
     * <p><strong>Error Responses:</strong>
     *
     * <ul>
     *   <li>HTTP 404 Not Found - Transaction not found or doesn't belong to user
     * </ul>
     *
     * <p>Requirement REQ-2.4.1.3: Soft-delete transaction
     *
     * <p>Requirement REQ-3.2: Authorization - verify ownership
     *
     * @param transactionId the transaction ID
     * @param authentication Spring Security authentication object
     * @return HTTP 204 No Content on success
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteTransaction(
            @PathVariable("id") Long transactionId,
            @RequestHeader(value = ENCRYPTION_KEY_HEADER, required = false) String encodedKey,
            Authentication authentication) {

        log.info("Deleting transaction: id={}", transactionId);

        User user = (User) authentication.getPrincipal();
        SecretKey encryptionKey =
                encodedKey != null && !encodedKey.trim().isEmpty()
                        ? EncryptionUtil.decodeEncryptionKey(encodedKey)
                        : null;

        transactionService.deleteTransaction(transactionId, user.getId(), encryptionKey);

        log.info("Transaction deleted successfully: id={}", transactionId);

        return ResponseEntity.noContent().build();
    }

    /**
     * Searches transactions using advanced criteria with pagination.
     *
     * <p>Provides flexible search capabilities with multiple optional filters. All filters can be
     * combined (AND logic). Supports pagination and sorting.
     *
     * <p><strong>Request Headers:</strong>
     *
     * <ul>
     *   <li>Authorization: Bearer {jwt_token}
     *   <li>X-Encryption-Key: {base64_encoded_key}
     * </ul>
     *
     * <p><strong>Query Parameters (all optional):</strong>
     *
     * <ul>
     *   <li>keyword - String - Search in description, notes, payee (case-insensitive)
     *   <li>accountId - Long - Filter by account ID
     *   <li>categoryId - Long - Filter by category ID
     *   <li>type - TransactionType - Filter by INCOME, EXPENSE, or TRANSFER
     *   <li>dateFrom - LocalDate (ISO-8601) - Start date (inclusive)
     *   <li>dateTo - LocalDate (ISO-8601) - End date (inclusive)
     *   <li>amountMin - BigDecimal - Minimum amount (inclusive)
     *   <li>amountMax - BigDecimal - Maximum amount (inclusive)
     *   <li>tags - String - Search for transactions containing these tags
     *   <li>isReconciled - Boolean - Filter by reconciliation status (true/false)
     *   <li>noCategory - Boolean - When true, return only uncategorized transactions
     *   <li>noPayee - Boolean - When true, return only transactions without a payee
     *   <li>page - int - Page number (0-based, default: 0)
     *   <li>size - int - Page size (default: 20, max: 100)
     *   <li>sort - String - Sort criteria (e.g., "date,desc" or "amount,asc")
     * </ul>
     *
     * <p><strong>Example Requests:</strong>
     *
     * <pre>
     * GET /api/v1/transactions/search?keyword=grocery&page=0&size=20
     * GET
     * /api/v1/transactions/search?accountId=1&type=EXPENSE&dateFrom=2026-01-01&dateTo=2026-01-31
     * GET /api/v1/transactions/search?amountMin=100&amountMax=500&sort=date,desc
     * GET /api/v1/transactions/search?isReconciled=false&page=0&size=50
     * GET /api/v1/transactions/search?tags=business,reimbursable
     * </pre>
     *
     * <p><strong>Success Response (HTTP 200 OK):</strong>
     *
     * <pre>{@code
     * {
     * "content": [ array of TransactionResponse objects ],
     * "pageable": {
     * "pageNumber": 0,
     * "pageSize": 20,
     * "sort": { "sorted": true, "unsorted": false },
     * "offset": 0,
     * "paged": true,
     * "unpaged": false
     * },
     * "totalElements": 150,
     * "totalPages": 8,
     * "last": false,
     * "first": true,
     * "size": 20,
     * "number": 0,
     * "numberOfElements": 20,
     * "empty": false
     * }
     * }</pre>
     *
     * <p>Requirement REQ-2.3.5: Advanced transaction search and filtering
     *
     * @param keyword optional keyword to search in description/notes/payee
     * @param accountId optional account ID filter
     * @param categoryId optional category ID filter
     * @param type optional transaction type filter
     * @param dateFrom optional start date filter (ISO-8601 format)
     * @param dateTo optional end date filter (ISO-8601 format)
     * @param amountMin optional minimum amount filter
     * @param amountMax optional maximum amount filter
     * @param tags optional tags filter
     * @param isReconciled optional reconciliation status filter
     * @param noCategory when true, return only uncategorized transactions
     * @param noPayee when true, return only transactions without a payee
     * @param pageable pagination and sorting parameters
     * @param encodedKey Base64-encoded encryption key from header
     * @param authentication Spring Security authentication object
     * @return HTTP 200 OK with Page&lt;TransactionResponse&gt;
     */
    @GetMapping("/search")
    public ResponseEntity<Page<TransactionResponse>> searchTransactions(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Long accountId,
            @RequestParam(required = false) Long categoryId,
            @RequestParam(required = false) TransactionType type,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
                    LocalDate dateFrom,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
                    LocalDate dateTo,
            @RequestParam(required = false) java.math.BigDecimal amountMin,
            @RequestParam(required = false) java.math.BigDecimal amountMax,
            @RequestParam(required = false) String tags,
            @RequestParam(required = false) Boolean isReconciled,
            @RequestParam(required = false) Boolean noCategory,
            @RequestParam(required = false) Boolean noPayee,
            @RequestParam(required = false) String payee,
            Pageable pageable,
            @RequestHeader(value = ENCRYPTION_KEY_HEADER, required = false) String encodedKey,
            Authentication authentication) {

        log.info(
                "Searching transactions for user: keyword={}, accountId={}, type={}, page={}, size={}",
                keyword,
                accountId,
                type,
                pageable.getPageNumber(),
                pageable.getPageSize());

        if (encodedKey == null || encodedKey.trim().isEmpty()) {
            throw new IllegalArgumentException("Encryption key header is required");
        }

        User user = (User) authentication.getPrincipal();
        SecretKey encryptionKey = EncryptionUtil.decodeEncryptionKey(encodedKey);

        // Build search criteria
        org.openfinance.dto.TransactionSearchCriteria criteria =
                org.openfinance.dto.TransactionSearchCriteria.builder()
                        .keyword(keyword)
                        .accountId(accountId)
                        .categoryId(categoryId)
                        .type(type)
                        .dateFrom(dateFrom)
                        .dateTo(dateTo)
                        .amountMin(amountMin)
                        .amountMax(amountMax)
                        .tags(tags)
                        .isReconciled(isReconciled)
                        .noCategory(noCategory)
                        .noPayee(noPayee)
                        .payee(payee)
                        .build();

        // Execute search with pagination
        Page<TransactionResponse> results =
                transactionService.searchTransactions(
                        user.getId(), criteria, pageable, encryptionKey);

        log.info(
                "Search returned {} transactions (page {}/{}, total: {})",
                results.getNumberOfElements(),
                results.getNumber() + 1,
                results.getTotalPages(),
                results.getTotalElements());

        return ResponseEntity.ok(results);
    }

    /**
     * Gets all unique tags used by the authenticated user's transactions.
     *
     * <p>Returns a list of all tags with their usage counts, sorted by frequency (most used first).
     * This endpoint is useful for tag autocomplete features and tag cloud displays.
     *
     * <p><strong>Request Headers:</strong>
     *
     * <ul>
     *   <li>Authorization: Bearer {jwt_token}
     * </ul>
     *
     * <p><strong>Response:</strong> List of tag objects with name and count:
     *
     * <pre>
     * [
     *   { "tag": "business", "count": 45 },
     *   { "tag": "groceries", "count": 32 },
     *   { "tag": "vacation", "count": 12 }
     * ]
     * </pre>
     *
     * <p>Requirement REQ-2.3.7: Tag support for flexible categorization
     *
     * @param authentication Spring Security authentication object (auto-injected)
     * @return ResponseEntity with list of TagInfo objects (tag name and count)
     */
    @GetMapping("/tags")
    public ResponseEntity<List<TransactionService.TagInfo>> getAllTags(
            Authentication authentication) {
        User user = (User) authentication.getPrincipal();

        log.info("Fetching all tags for user id={}", user.getId());

        List<TransactionService.TagInfo> tags = transactionService.getAllTagsForUser(user.getId());

        log.info("Returning {} unique tags for user id={}", tags.size(), user.getId());
        return ResponseEntity.ok(tags);
    }

    /**
     * Gets the most popular tags for the authenticated user.
     *
     * <p>Returns up to the specified limit of tags, sorted by frequency of use. Useful for tag
     * autocomplete suggestions.
     *
     * <p><strong>Request Headers:</strong>
     *
     * <ul>
     *   <li>Authorization: Bearer {jwt_token}
     * </ul>
     *
     * <p><strong>Query Parameters:</strong>
     *
     * <ul>
     *   <li>limit (optional, default=20): Maximum number of tags to return
     * </ul>
     *
     * <p><strong>Response:</strong> List of tag strings:
     *
     * <pre>
     * ["business", "groceries", "vacation", "utilities", "healthcare"]
     * </pre>
     *
     * <p>Requirement REQ-2.3.7: Tag autocomplete support
     *
     * @param limit Maximum number of tags to return (default: 20)
     * @param authentication Spring Security authentication object (auto-injected)
     * @return ResponseEntity with list of popular tag strings
     */
    @GetMapping("/tags/popular")
    public ResponseEntity<List<String>> getPopularTags(
            @RequestParam(defaultValue = "20") int limit, Authentication authentication) {

        User user = (User) authentication.getPrincipal();

        log.info("Fetching top {} popular tags for user id={}", limit, user.getId());

        List<String> popularTags = transactionService.getPopularTags(user.getId(), limit);

        log.info("Returning {} popular tags for user id={}", popularTags.size(), user.getId());
        return ResponseEntity.ok(popularTags);
    }

    /**
     * Gets all transactions with a specific tag for the authenticated user.
     *
     * <p>Returns all transactions that contain the specified tag (case-insensitive match). The tag
     * search is exact - "vacation" will not match "vacations".
     *
     * <p><strong>Request Headers:</strong>
     *
     * <ul>
     *   <li>Authorization: Bearer {jwt_token}
     *   <li>X-Encryption-Key: {base64_encoded_key}
     * </ul>
     *
     * <p><strong>Path Parameters:</strong>
     *
     * <ul>
     *   <li>tag: The tag to search for (case-insensitive)
     * </ul>
     *
     * <p><strong>Response:</strong> List of TransactionResponse objects containing the tag
     *
     * <p>Requirement REQ-2.3.7: Tag-based filtering
     *
     * @param tag The tag to search for
     * @param encodedKey Base64-encoded encryption key from request header
     * @param authentication Spring Security authentication object (auto-injected)
     * @return ResponseEntity with list of TransactionResponse objects
     * @throws IllegalArgumentException if tag is null/empty or encryption key is missing
     */
    @GetMapping("/by-tag/{tag}")
    public ResponseEntity<List<TransactionResponse>> getTransactionsByTag(
            @PathVariable String tag,
            @RequestHeader(value = ENCRYPTION_KEY_HEADER, required = false) String encodedKey,
            Authentication authentication) {

        if (encodedKey == null || encodedKey.trim().isEmpty()) {
            throw new IllegalArgumentException("Encryption key header is required");
        }

        User user = (User) authentication.getPrincipal();
        SecretKey encryptionKey = EncryptionUtil.decodeEncryptionKey(encodedKey);

        log.info("Fetching transactions with tag '{}' for user id={}", tag, user.getId());

        List<TransactionResponse> transactions =
                transactionService.getTransactionsByTag(user.getId(), tag, encryptionKey);

        log.info(
                "Returning {} transactions with tag '{}' for user id={}",
                transactions.size(),
                tag,
                user.getId());
        return ResponseEntity.ok(transactions);
    }

    /**
     * Gets all split lines for a specific transaction.
     *
     * <p>Returns an empty list if the transaction has no splits. Only the transaction owner can
     * access splits.
     *
     * <p><strong>Request Headers:</strong>
     *
     * <ul>
     *   <li>Authorization: Bearer {jwt_token}
     *   <li>X-Encryption-Key: {base64_encoded_key}
     * </ul>
     *
     * <p><strong>Error Responses:</strong>
     *
     * <ul>
     *   <li>HTTP 404 Not Found - Transaction not found or doesn't belong to user
     * </ul>
     *
     * <p>Requirement REQ-SPL-2.5: Dedicated endpoint for retrieving transaction splits
     *
     * <p>Requirement REQ-3.2: Authorization - verify transaction ownership
     *
     * @param transactionId the transaction ID
     * @param encodedKey Base64-encoded encryption key from header
     * @param authentication Spring Security authentication object
     * @return HTTP 200 OK with list of TransactionSplitResponse (may be empty)
     */
    @GetMapping("/{id}/splits")
    public ResponseEntity<List<TransactionSplitResponse>> getSplitsForTransaction(
            @PathVariable("id") Long transactionId,
            @RequestHeader(value = ENCRYPTION_KEY_HEADER, required = false) String encodedKey,
            Authentication authentication) {

        log.info("Retrieving splits for transaction: id={}", transactionId);

        if (encodedKey == null || encodedKey.trim().isEmpty()) {
            throw new IllegalArgumentException("Encryption key header is required");
        }

        User user = (User) authentication.getPrincipal();
        SecretKey encryptionKey = EncryptionUtil.decodeEncryptionKey(encodedKey);

        List<TransactionSplitResponse> splits =
                transactionService.getSplitsForTransaction(
                        transactionId, user.getId(), encryptionKey);

        log.info("Retrieved {} splits for transaction id={}", splits.size(), transactionId);

        return ResponseEntity.ok(splits);
    }
}
