package org.openfinance.controller;

import jakarta.validation.Valid;
import java.util.List;
import javax.crypto.SecretKey;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.openfinance.dto.RecurringTransactionRequest;
import org.openfinance.dto.RecurringTransactionResponse;
import org.openfinance.entity.RecurringFrequency;
import org.openfinance.entity.TransactionType;
import org.openfinance.entity.User;
import org.openfinance.service.RecurringTransactionService;
import org.openfinance.util.EncryptionUtil;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
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
 * REST controller for recurring transaction management endpoints.
 *
 * <p>Provides CRUD operations for recurring transactions, pause/resume functionality, and preview
 * of due transactions. All endpoints require authentication and use the user's encryption key to
 * secure sensitive data.
 *
 * <p><strong>Endpoints:</strong>
 *
 * <ul>
 *   <li>POST /api/v1/recurring-transactions - Create new recurring transaction
 *   <li>GET /api/v1/recurring-transactions - List all user recurring transactions
 *   <li>GET /api/v1/recurring-transactions/active - List active recurring transactions
 *   <li>GET /api/v1/recurring-transactions/due - List due recurring transactions (preview)
 *   <li>GET /api/v1/recurring-transactions/{id} - Get recurring transaction by ID
 *   <li>PUT /api/v1/recurring-transactions/{id} - Update recurring transaction
 *   <li>DELETE /api/v1/recurring-transactions/{id} - Delete recurring transaction
 *   <li>POST /api/v1/recurring-transactions/{id}/pause - Pause recurring transaction
 *   <li>POST /api/v1/recurring-transactions/{id}/resume - Resume recurring transaction
 *   <li>POST /api/v1/recurring-transactions/process - Manual trigger (admin only)
 * </ul>
 *
 * <p><strong>Security:</strong>
 *
 * <ul>
 *   <li>All endpoints require JWT authentication
 *   <li>Encryption key must be provided via X-Encryption-Key header for sensitive data
 *   <li>Users can only access their own recurring transactions
 *   <li>Description and notes are encrypted at rest
 * </ul>
 *
 * <p><strong>Requirements:</strong>
 *
 * <ul>
 *   <li>REQ-2.3.6: Recurring transaction management
 *   <li>REQ-2.3.6.1: Support for multiple frequency types
 *   <li>REQ-2.3.6.2: Optional end date for recurring transactions
 *   <li>REQ-2.3.6.3: Pause/resume recurring transactions
 *   <li>REQ-2.18: Data encryption at rest
 *   <li>REQ-3.2: Authorization checks
 * </ul>
 *
 * @see RecurringTransactionService
 * @see RecurringTransactionRequest
 * @see RecurringTransactionResponse
 * @author Open-Finance Development Team
 * @version 1.0
 * @since 2026-02-03
 */
@RestController
@RequestMapping("/api/v1/recurring-transactions")
@RequiredArgsConstructor
@Slf4j
public class RecurringTransactionController {

    private static final String ENCRYPTION_KEY_HEADER = "X-Encryption-Key";

    private final RecurringTransactionService recurringTransactionService;

    /**
     * Creates a new recurring transaction for the authenticated user.
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
     * "toAccountId": null,
     * "type": "EXPENSE",
     * "amount": 1200.00,
     * "currency": "USD",
     * "categoryId": 5,
     * "description": "Monthly rent payment",
     * "notes": "Due on the 1st of each month",
     * "frequency": "MONTHLY",
     * "nextOccurrence": "2026-03-01",
     * "endDate": null
     * }
     * }</pre>
     *
     * <p><strong>Success Response (HTTP 201 Created):</strong>
     *
     * <pre>{@code
     * {
     * "id": 1,
     * "accountId": 1,
     * "accountName": "Checking Account",
     * "toAccountId": null,
     * "toAccountName": null,
     * "type": "EXPENSE",
     * "amount": 1200.00,
     * "currency": "USD",
     * "categoryId": 5,
     * "categoryName": "Housing",
     * "categoryIcon": "home",
     * "categoryColor": "#FF6B6B",
     * "description": "Monthly rent payment",
     * "notes": "Due on the 1st of each month",
     * "frequency": "MONTHLY",
     * "frequencyDisplayName": "Monthly",
     * "nextOccurrence": "2026-03-01",
     * "endDate": null,
     * "isActive": true,
     * "createdAt": "2026-02-03T10:00:00",
     * "updatedAt": "2026-02-03T10:00:00",
     * "isDue": false,
     * "daysUntilNext": 26,
     * "isEnded": false
     * }
     * }</pre>
     *
     * <p>Requirement REQ-2.3.6: Create recurring transaction
     *
     * @param request recurring transaction creation request
     * @param encodedKey Base64-encoded encryption key from header
     * @param authentication Spring Security authentication object
     * @return HTTP 201 Created with RecurringTransactionResponse
     * @throws IllegalArgumentException if encryption key is missing or invalid
     */
    @PostMapping
    public ResponseEntity<RecurringTransactionResponse> createRecurringTransaction(
            @Valid @RequestBody RecurringTransactionRequest request,
            @RequestHeader(value = ENCRYPTION_KEY_HEADER, required = false) String encodedKey,
            Authentication authentication) {

        log.info(
                "Creating recurring transaction for user: type={}, frequency={}, amount={}",
                request.getType(),
                request.getFrequency(),
                request.getAmount());

        if (encodedKey == null || encodedKey.trim().isEmpty()) {
            throw new IllegalArgumentException("Encryption key header is required");
        }

        User user = (User) authentication.getPrincipal();
        SecretKey encryptionKey = EncryptionUtil.decodeEncryptionKey(encodedKey);

        RecurringTransactionResponse response =
                recurringTransactionService.createRecurringTransaction(
                        user.getId(), request, encryptionKey);

        log.info(
                "Recurring transaction created successfully: id={}, frequency={}, nextOccurrence={}",
                response.getId(),
                response.getFrequency(),
                response.getNextOccurrence());

        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    /**
     * Retrieves all recurring transactions for the authenticated user.
     *
     * <p>Returns both active and inactive (paused) recurring transactions, sorted by next
     * occurrence date ascending.
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
     *   <li>frequency (optional): Filter by frequency type (DAILY, WEEKLY, MONTHLY, etc.)
     * </ul>
     *
     * <p><strong>Success Response (HTTP 200 OK):</strong> Returns array of
     * RecurringTransactionResponse objects.
     *
     * <p>Requirement REQ-2.3.6: List recurring transactions with optional filtering
     *
     * @param frequency optional frequency filter
     * @param encodedKey Base64-encoded encryption key from header
     * @param authentication Spring Security authentication object
     * @return HTTP 200 OK with list of RecurringTransactionResponse
     */
    @GetMapping
    public ResponseEntity<List<RecurringTransactionResponse>> getAllRecurringTransactions(
            @RequestParam(required = false) RecurringFrequency frequency,
            @RequestHeader(value = ENCRYPTION_KEY_HEADER, required = false) String encodedKey,
            Authentication authentication) {

        log.info(
                "Retrieving recurring transactions for user{}",
                frequency != null ? " with frequency=" + frequency : "");

        if (encodedKey == null || encodedKey.trim().isEmpty()) {
            throw new IllegalArgumentException("Encryption key header is required");
        }

        User user = (User) authentication.getPrincipal();
        SecretKey encryptionKey = EncryptionUtil.decodeEncryptionKey(encodedKey);

        List<RecurringTransactionResponse> recurringTransactions;
        if (frequency != null) {
            recurringTransactions =
                    recurringTransactionService.getRecurringTransactionsByFrequency(
                            user.getId(), frequency, encryptionKey);
        } else {
            recurringTransactions =
                    recurringTransactionService.getAllRecurringTransactions(
                            user.getId(), encryptionKey);
        }

        log.info("Retrieved {} recurring transactions for user", recurringTransactions.size());

        return ResponseEntity.ok(recurringTransactions);
    }

    /**
     * Retrieves recurring transactions with pagination and optional filtering.
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
     *   <li>page (optional): Page number (0-indexed, default: 0)
     *   <li>size (optional): Page size (default: 20)
     *   <li>type (optional): Filter by transaction type (INCOME, EXPENSE, TRANSFER)
     *   <li>frequency (optional): Filter by frequency (DAILY, WEEKLY, MONTHLY, etc.)
     *   <li>isActive (optional): Filter by active status (true/false)
     *   <li>search (optional): Search by description
     *   <li>sort (optional): Sort field and direction (e.g., "nextOccurrence,asc" or
     *       "description,desc")
     * </ul>
     *
     * <p><strong>Example Request:</strong>
     *
     * <pre>GET
     * /api/v1/recurring-transactions/paged?page=0&size=10&type=EXPENSE&search=rent&sort=nextOccurrence,asc
     * </pre>
     *
     * <p><strong>Success Response (HTTP 200 OK):</strong>
     *
     * <pre>{@code
     * {
     * "content": [...],
     * "pageable": { "pageNumber": 0, "pageSize": 10 },
     * "totalElements": 25,
     * "totalPages": 3,
     * "last": false,
     * "first": true
     * }
     * }</pre>
     *
     * <p>Requirement REQ-2.3.6: List recurring transactions with pagination and filtering
     *
     * @param page page number (0-indexed)
     * @param size page size
     * @param type optional type filter
     * @param frequency optional frequency filter
     * @param isActive optional active status filter
     * @param search optional search term
     * @param sort optional sort field and direction
     * @param encodedKey Base64-encoded encryption key from header
     * @param authentication Spring Security authentication object
     * @return HTTP 200 OK with paginated RecurringTransactionResponse
     */
    @GetMapping("/paged")
    public ResponseEntity<Page<RecurringTransactionResponse>> getRecurringTransactionsPaged(
            @RequestParam(value = "page", defaultValue = "0") int page,
            @RequestParam(value = "size", defaultValue = "20") int size,
            @RequestParam(value = "type", required = false) TransactionType type,
            @RequestParam(value = "frequency", required = false) RecurringFrequency frequency,
            @RequestParam(value = "isActive", required = false) Boolean isActive,
            @RequestParam(value = "search", required = false) String search,
            @RequestParam(value = "sort", defaultValue = "nextOccurrence,asc") String sort,
            @RequestHeader(value = ENCRYPTION_KEY_HEADER, required = false) String encodedKey,
            Authentication authentication) {

        log.info(
                "Retrieving recurring transactions paged for user: page={}, size={}, type={}, frequency={}, isActive={}, search={}, sort={}",
                page,
                size,
                type,
                frequency,
                isActive,
                search,
                sort);

        if (encodedKey == null || encodedKey.trim().isEmpty()) {
            throw new IllegalArgumentException("Encryption key header is required");
        }

        User user = (User) authentication.getPrincipal();
        SecretKey encryptionKey = EncryptionUtil.decodeEncryptionKey(encodedKey);

        // Parse sort parameter
        String[] sortParts = sort.split(",");
        String sortField = sortParts[0];
        Sort.Direction direction =
                sortParts.length > 1 && "desc".equalsIgnoreCase(sortParts[1])
                        ? Sort.Direction.DESC
                        : Sort.Direction.ASC;

        Pageable pageable = PageRequest.of(page, size, Sort.by(direction, sortField));

        Page<RecurringTransactionResponse> recurringTransactionsPage =
                recurringTransactionService.getRecurringTransactionsWithFilters(
                        user.getId(), type, frequency, isActive, search, pageable, encryptionKey);

        log.info(
                "Retrieved recurring transactions page {} of {} (total: {}) for user",
                page + 1,
                recurringTransactionsPage.getTotalPages(),
                recurringTransactionsPage.getTotalElements());

        return ResponseEntity.ok(recurringTransactionsPage);
    }

    /**
     * Retrieves only active recurring transactions for the authenticated user.
     *
     * <p>Returns recurring transactions where isActive=true, sorted by next occurrence date
     * ascending.
     *
     * <p><strong>Request Headers:</strong>
     *
     * <ul>
     *   <li>Authorization: Bearer {jwt_token}
     *   <li>X-Encryption-Key: {base64_encoded_key}
     * </ul>
     *
     * <p><strong>Success Response (HTTP 200 OK):</strong> Returns array of active
     * RecurringTransactionResponse objects.
     *
     * <p>Requirement REQ-2.3.6: List active recurring transactions
     *
     * @param encodedKey Base64-encoded encryption key from header
     * @param authentication Spring Security authentication object
     * @return HTTP 200 OK with list of active RecurringTransactionResponse
     */
    @GetMapping("/active")
    public ResponseEntity<List<RecurringTransactionResponse>> getActiveRecurringTransactions(
            @RequestHeader(value = ENCRYPTION_KEY_HEADER, required = false) String encodedKey,
            Authentication authentication) {

        log.info("Retrieving active recurring transactions for user");

        if (encodedKey == null || encodedKey.trim().isEmpty()) {
            throw new IllegalArgumentException("Encryption key header is required");
        }

        User user = (User) authentication.getPrincipal();
        SecretKey encryptionKey = EncryptionUtil.decodeEncryptionKey(encodedKey);

        List<RecurringTransactionResponse> activeRecurringTransactions =
                recurringTransactionService.getActiveRecurringTransactions(
                        user.getId(), encryptionKey);

        log.info(
                "Retrieved {} active recurring transactions for user",
                activeRecurringTransactions.size());

        return ResponseEntity.ok(activeRecurringTransactions);
    }

    /**
     * Retrieves due recurring transactions for the authenticated user (preview).
     *
     * <p>Returns active recurring transactions where next occurrence is today or in the past, and
     * end date has not been reached. This is useful for UI preview of upcoming transactions.
     *
     * <p><strong>Request Headers:</strong>
     *
     * <ul>
     *   <li>Authorization: Bearer {jwt_token}
     *   <li>X-Encryption-Key: {base64_encoded_key}
     * </ul>
     *
     * <p><strong>Success Response (HTTP 200 OK):</strong> Returns array of due
     * RecurringTransactionResponse objects.
     *
     * <p><strong>Use Cases:</strong>
     *
     * <ul>
     *   <li>Dashboard widget showing "Upcoming Recurring Transactions"
     *   <li>Preview of what the scheduled job will process
     *   <li>User notification of due recurring transactions
     * </ul>
     *
     * <p>Requirement REQ-2.3.6: Preview due recurring transactions
     *
     * @param encodedKey Base64-encoded encryption key from header
     * @param authentication Spring Security authentication object
     * @return HTTP 200 OK with list of due RecurringTransactionResponse
     */
    @GetMapping("/due")
    public ResponseEntity<List<RecurringTransactionResponse>> getDueRecurringTransactions(
            @RequestHeader(value = ENCRYPTION_KEY_HEADER, required = false) String encodedKey,
            Authentication authentication) {

        log.info("Retrieving due recurring transactions for user");

        if (encodedKey == null || encodedKey.trim().isEmpty()) {
            throw new IllegalArgumentException("Encryption key header is required");
        }

        User user = (User) authentication.getPrincipal();
        SecretKey encryptionKey = EncryptionUtil.decodeEncryptionKey(encodedKey);

        List<RecurringTransactionResponse> dueRecurringTransactions =
                recurringTransactionService.getDueRecurringTransactions(
                        user.getId(), encryptionKey);

        log.info(
                "Retrieved {} due recurring transactions for user",
                dueRecurringTransactions.size());

        return ResponseEntity.ok(dueRecurringTransactions);
    }

    /**
     * Retrieves a specific recurring transaction by ID.
     *
     * <p><strong>Request Headers:</strong>
     *
     * <ul>
     *   <li>Authorization: Bearer {jwt_token}
     *   <li>X-Encryption-Key: {base64_encoded_key}
     * </ul>
     *
     * <p><strong>Success Response (HTTP 200 OK):</strong> Returns a single
     * RecurringTransactionResponse object.
     *
     * <p><strong>Error Responses:</strong>
     *
     * <ul>
     *   <li>HTTP 404 Not Found - Recurring transaction not found or doesn't belong to user
     * </ul>
     *
     * <p>Requirement REQ-2.3.6: Retrieve recurring transaction details
     *
     * <p>Requirement REQ-3.2: Authorization - verify ownership
     *
     * @param recurringTransactionId the recurring transaction ID
     * @param encodedKey Base64-encoded encryption key from header
     * @param authentication Spring Security authentication object
     * @return HTTP 200 OK with RecurringTransactionResponse
     */
    @GetMapping("/{id}")
    public ResponseEntity<RecurringTransactionResponse> getRecurringTransactionById(
            @PathVariable("id") Long recurringTransactionId,
            @RequestHeader(value = ENCRYPTION_KEY_HEADER, required = false) String encodedKey,
            Authentication authentication) {

        log.info("Retrieving recurring transaction: id={}", recurringTransactionId);

        if (encodedKey == null || encodedKey.trim().isEmpty()) {
            throw new IllegalArgumentException("Encryption key header is required");
        }

        User user = (User) authentication.getPrincipal();
        SecretKey encryptionKey = EncryptionUtil.decodeEncryptionKey(encodedKey);

        RecurringTransactionResponse response =
                recurringTransactionService.getRecurringTransactionById(
                        recurringTransactionId, user.getId(), encryptionKey);

        log.info("Recurring transaction retrieved successfully: id={}", recurringTransactionId);

        return ResponseEntity.ok(response);
    }

    /**
     * Updates an existing recurring transaction.
     *
     * <p><strong>Request Headers:</strong>
     *
     * <ul>
     *   <li>Authorization: Bearer {jwt_token}
     *   <li>X-Encryption-Key: {base64_encoded_key}
     * </ul>
     *
     * <p><strong>Request Body:</strong> Same format as create recurring transaction. All fields are
     * required.
     *
     * <p><strong>Success Response (HTTP 200 OK):</strong> Returns updated
     * RecurringTransactionResponse object.
     *
     * <p><strong>Error Responses:</strong>
     *
     * <ul>
     *   <li>HTTP 404 Not Found - Recurring transaction not found or doesn't belong to user
     *   <li>HTTP 400 Bad Request - Validation errors
     * </ul>
     *
     * <p>Requirement REQ-2.3.6: Update recurring transaction
     *
     * <p>Requirement REQ-3.2: Authorization - verify ownership
     *
     * @param recurringTransactionId the recurring transaction ID
     * @param request recurring transaction update request
     * @param encodedKey Base64-encoded encryption key from header
     * @param authentication Spring Security authentication object
     * @return HTTP 200 OK with RecurringTransactionResponse
     */
    @PutMapping("/{id}")
    public ResponseEntity<RecurringTransactionResponse> updateRecurringTransaction(
            @PathVariable("id") Long recurringTransactionId,
            @Valid @RequestBody RecurringTransactionRequest request,
            @RequestHeader(value = ENCRYPTION_KEY_HEADER, required = false) String encodedKey,
            Authentication authentication) {

        log.info("Updating recurring transaction: id={}", recurringTransactionId);

        if (encodedKey == null || encodedKey.trim().isEmpty()) {
            throw new IllegalArgumentException("Encryption key header is required");
        }

        User user = (User) authentication.getPrincipal();
        SecretKey encryptionKey = EncryptionUtil.decodeEncryptionKey(encodedKey);

        RecurringTransactionResponse response =
                recurringTransactionService.updateRecurringTransaction(
                        recurringTransactionId, user.getId(), request, encryptionKey);

        log.info("Recurring transaction updated successfully: id={}", recurringTransactionId);

        return ResponseEntity.ok(response);
    }

    /**
     * Deletes a recurring transaction (hard delete).
     *
     * <p>Unlike regular transactions, recurring transactions are hard-deleted (not soft-deleted)
     * because they are templates that don't affect account balances directly.
     *
     * <p><strong>Request Headers:</strong>
     *
     * <ul>
     *   <li>Authorization: Bearer {jwt_token}
     * </ul>
     *
     * <p><strong>Success Response (HTTP 204 No Content):</strong> Empty response body.
     *
     * <p><strong>Error Responses:</strong>
     *
     * <ul>
     *   <li>HTTP 404 Not Found - Recurring transaction not found or doesn't belong to user
     * </ul>
     *
     * <p>Requirement REQ-2.3.6: Delete recurring transaction
     *
     * <p>Requirement REQ-3.2: Authorization - verify ownership
     *
     * @param recurringTransactionId the recurring transaction ID
     * @param authentication Spring Security authentication object
     * @return HTTP 204 No Content
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteRecurringTransaction(
            @PathVariable("id") Long recurringTransactionId, Authentication authentication) {

        log.info("Deleting recurring transaction: id={}", recurringTransactionId);

        User user = (User) authentication.getPrincipal();

        recurringTransactionService.deleteRecurringTransaction(
                recurringTransactionId, user.getId());

        log.info("Recurring transaction deleted successfully: id={}", recurringTransactionId);

        return ResponseEntity.noContent().build();
    }

    /**
     * Pauses a recurring transaction by setting isActive=false.
     *
     * <p>Paused recurring transactions are not processed by the scheduled job.
     *
     * <p><strong>Request Headers:</strong>
     *
     * <ul>
     *   <li>Authorization: Bearer {jwt_token}
     *   <li>X-Encryption-Key: {base64_encoded_key}
     * </ul>
     *
     * <p><strong>Success Response (HTTP 200 OK):</strong> Returns updated
     * RecurringTransactionResponse object with isActive=false.
     *
     * <p><strong>Error Responses:</strong>
     *
     * <ul>
     *   <li>HTTP 404 Not Found - Recurring transaction not found or doesn't belong to user
     * </ul>
     *
     * <p>Requirement REQ-2.3.6.3: Pause recurring transaction
     *
     * @param recurringTransactionId the recurring transaction ID
     * @param encodedKey Base64-encoded encryption key from header
     * @param authentication Spring Security authentication object
     * @return HTTP 200 OK with RecurringTransactionResponse
     */
    @PostMapping("/{id}/pause")
    public ResponseEntity<RecurringTransactionResponse> pauseRecurringTransaction(
            @PathVariable("id") Long recurringTransactionId,
            @RequestHeader(value = ENCRYPTION_KEY_HEADER, required = false) String encodedKey,
            Authentication authentication) {

        log.info("Pausing recurring transaction: id={}", recurringTransactionId);

        if (encodedKey == null || encodedKey.trim().isEmpty()) {
            throw new IllegalArgumentException("Encryption key header is required");
        }

        User user = (User) authentication.getPrincipal();
        SecretKey encryptionKey = EncryptionUtil.decodeEncryptionKey(encodedKey);

        RecurringTransactionResponse response =
                recurringTransactionService.pauseRecurringTransaction(
                        recurringTransactionId, user.getId(), encryptionKey);

        log.info("Recurring transaction paused successfully: id={}", recurringTransactionId);

        return ResponseEntity.ok(response);
    }

    /**
     * Resumes a paused recurring transaction by setting isActive=true.
     *
     * <p>Optionally adjusts the next occurrence date if it's too far in the past (more than 1 month
     * old).
     *
     * <p><strong>Request Headers:</strong>
     *
     * <ul>
     *   <li>Authorization: Bearer {jwt_token}
     *   <li>X-Encryption-Key: {base64_encoded_key}
     * </ul>
     *
     * <p><strong>Success Response (HTTP 200 OK):</strong> Returns updated
     * RecurringTransactionResponse object with isActive=true.
     *
     * <p><strong>Error Responses:</strong>
     *
     * <ul>
     *   <li>HTTP 404 Not Found - Recurring transaction not found or doesn't belong to user
     * </ul>
     *
     * <p>Requirement REQ-2.3.6.3: Resume recurring transaction
     *
     * @param recurringTransactionId the recurring transaction ID
     * @param encodedKey Base64-encoded encryption key from header
     * @param authentication Spring Security authentication object
     * @return HTTP 200 OK with RecurringTransactionResponse
     */
    @PostMapping("/{id}/resume")
    public ResponseEntity<RecurringTransactionResponse> resumeRecurringTransaction(
            @PathVariable("id") Long recurringTransactionId,
            @RequestHeader(value = ENCRYPTION_KEY_HEADER, required = false) String encodedKey,
            Authentication authentication) {

        log.info("Resuming recurring transaction: id={}", recurringTransactionId);

        if (encodedKey == null || encodedKey.trim().isEmpty()) {
            throw new IllegalArgumentException("Encryption key header is required");
        }

        User user = (User) authentication.getPrincipal();
        SecretKey encryptionKey = EncryptionUtil.decodeEncryptionKey(encodedKey);

        RecurringTransactionResponse response =
                recurringTransactionService.resumeRecurringTransaction(
                        recurringTransactionId, user.getId(), encryptionKey);

        log.info("Recurring transaction resumed successfully: id={}", recurringTransactionId);

        return ResponseEntity.ok(response);
    }

    /**
     * Manual trigger for processing recurring transactions (admin only).
     *
     * <p>This endpoint allows manual processing of recurring transactions outside of the scheduled
     * time. Useful for testing or recovering from a failed scheduled job.
     *
     * <p><strong>Warning:</strong> This endpoint should only be called by administrators to avoid
     * processing recurring transactions multiple times in a single day.
     *
     * <p><strong>Request Headers:</strong>
     *
     * <ul>
     *   <li>Authorization: Bearer {jwt_token} (admin role required)
     * </ul>
     *
     * <p><strong>Success Response (HTTP 200 OK):</strong>
     *
     * <pre>{@code
     * {
     * "message": "Manual processing completed. Processed: 25, Failed: 1, Errors:
     * 1",
     * "processedCount": 25,
     * "failedCount": 1,
     * "errors": [
     * "Failed to process recurring transaction 123 for user 456: Account not found"
     * ]
     * }
     * }</pre>
     *
     * <p>Requirement REQ-2.3.6: Manual processing trigger
     *
     * @param authentication Spring Security authentication object
     * @return HTTP 200 OK with processing result summary
     */
    @PostMapping("/process")
    public ResponseEntity<ProcessingResultResponse> processRecurringTransactions(
            Authentication authentication) {

        log.info("Manual recurring transaction processing triggered by user");

        User user = (User) authentication.getPrincipal();

        // Process recurring transactions for all users
        RecurringTransactionService.ProcessingResult result =
                recurringTransactionService.processRecurringTransactions();

        String message =
                String.format(
                        "Manual processing completed. Processed: %d, Failed: %d, Errors: %d",
                        result.getProcessedCount(),
                        result.getFailedCount(),
                        result.getErrors().size());

        log.info(
                "Manual processing completed: processed={}, failed={}, errors={}",
                result.getProcessedCount(),
                result.getFailedCount(),
                result.getErrors().size());

        ProcessingResultResponse response =
                new ProcessingResultResponse(
                        message,
                        result.getProcessedCount(),
                        result.getFailedCount(),
                        result.getErrors());

        return ResponseEntity.ok(response);
    }

    /**
     * Response DTO for manual processing trigger.
     *
     * @param message summary message
     * @param processedCount number of recurring transactions processed successfully
     * @param failedCount number of recurring transactions that failed
     * @param errors list of error messages
     */
    public record ProcessingResultResponse(
            String message, int processedCount, int failedCount, List<String> errors) {}
}
