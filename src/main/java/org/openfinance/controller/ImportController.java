package org.openfinance.controller;

import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.openfinance.dto.ImportConfirmRequest;
import org.openfinance.dto.ImportProcessRequest;
import org.openfinance.dto.ImportedTransaction;
import org.openfinance.entity.ImportSession;
import org.openfinance.service.ImportService;
import org.openfinance.util.ControllerUtil;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST controller for transaction import workflow from QIF/OFX/QFX files.
 *
 * <p>Manages the multi-step import process:
 *
 * <ol>
 *   <li>User uploads file via POST /api/v1/import/upload (FileUploadController)
 *   <li>User initiates import via POST /api/v1/import/process → creates ImportSession, parses file
 *   <li>User reviews transactions via GET /api/v1/import/sessions/{id}/review → previews parsed
 *       data
 *   <li>User confirms import via POST /api/v1/import/sessions/{id}/confirm → saves to database
 *   <li>Optional: Cancel via POST /api/v1/import/sessions/{id}/cancel
 * </ol>
 *
 * <p><strong>Key Features:</strong>
 *
 * <ul>
 *   <li>Duplicate detection with 85% similarity threshold
 *   <li>Intelligent category mapping (exact + fuzzy match)
 *   <li>Category auto-creation for unknown categories
 *   <li>Transaction validation with detailed error messages
 *   <li>Multi-session support with status tracking
 * </ul>
 *
 * <p><strong>Security:</strong>
 *
 * <ul>
 *   <li>All endpoints require JWT authentication
 *   <li>Users can only access their own import sessions
 *   <li>Authorization checked at service layer
 * </ul>
 *
 * <p>Requirement REQ-2.5.1: Transaction import from files (QIF, OFX, QFX)
 *
 * <p>Requirement REQ-2.5.1.2: Import process management
 *
 * <p>Requirement REQ-2.5.1.5: Category mapping during import
 *
 * <p>Requirement REQ-2.5.1.6: Duplicate detection and handling
 *
 * @see ImportService
 * @see ImportSession
 * @see ImportedTransaction
 * @see FileUploadController
 * @author Open-Finance Development Team
 * @version 1.0
 * @since 2024-01-15
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/import")
@RequiredArgsConstructor
public class ImportController {

    private final ImportService importService;

    /**
     * Initiates transaction import process from uploaded file.
     *
     * <p>Creates an ImportSession and parses the file to extract transactions. After parsing
     * completes, the session status will be PARSED and ready for review.
     *
     * <p><strong>Request Headers:</strong>
     *
     * <ul>
     *   <li>Authorization: Bearer {jwt_token}
     * </ul>
     *
     * <p><strong>Request Body:</strong>
     *
     * <pre>{@code
     * {
     * "uploadId": "550e8400-e29b-41d4-a716-446655440000",
     * "accountId": 1 // optional, can be selected during review
     * }
     * }</pre>
     *
     * <p><strong>Success Response (HTTP 200 OK):</strong>
     *
     * <pre>{@code
     * {
     * "id": 42,
     * "uploadId": "550e8400-e29b-41d4-a716-446655440000",
     * "userId": 123,
     * "fileName": "transactions.qif",
     * "fileFormat": "QIF",
     * "accountId": 1,
     * "status": "PARSED",
     * "totalTransactions": 50,
     * "importedCount": 0,
     * "skippedCount": 0,
     * "errorCount": 2,
     * "metadata": "{...}", // JSON-serialized ImportedTransaction list
     * "errorMessage": null,
     * "createdAt": "2026-02-02T10:00:00",
     * "completedAt": null
     * }
     * }</pre>
     *
     * <p><strong>Error Responses:</strong>
     *
     * <ul>
     *   <li>HTTP 400 Bad Request - Validation error (missing uploadId)
     *   <li>HTTP 404 Not Found - Upload file not found
     *   <li>HTTP 404 Not Found - Account not found or not owned by user
     *   <li>HTTP 400 Bad Request - Account is inactive
     *   <li>HTTP 500 Internal Server Error - File parsing failed
     * </ul>
     *
     * <p><strong>Usage Example:</strong>
     *
     * <pre>{@code
     * // Step 1: Upload file
     * POST /api/v1/import/upload
     * Form-data: file=transactions.qif
     * Response: { "uploadId": "550e8400-...", "status": "VALIDATED" }
     *
     * // Step 2: Start import
     * POST /api/v1/import/process
     * Body: { "uploadId": "550e8400-...", "accountId": 1 }
     * Response: ImportSession with status PARSED
     *
     * // Step 3: Review transactions
     * GET /api/v1/import/sessions/42/review
     * }</pre>
     *
     * <p>Requirement REQ-2.5.1.2: Import process initiation
     *
     * <p>Requirement REQ-2.5.1.3: File parsing (QIF, OFX, QFX)
     *
     * @param request import process request with uploadId and optional accountId
     * @param authentication Spring Security authentication (auto-injected)
     * @return ImportSession with PARSED status and parsed transaction count
     */
    @PostMapping("/process")
    public ResponseEntity<ImportSession> processImport(
            @Valid @RequestBody ImportProcessRequest request, Authentication authentication) {

        Long userId = ControllerUtil.extractUserId(authentication);
        log.info(
                "User {} initiating import for uploadId={}, accountId={}",
                userId,
                request.getUploadId(),
                request.getAccountId());

        ImportSession session =
                importService.startImport(
                        request.getUploadId(),
                        userId,
                        request.getAccountId(),
                        request.getOriginalFileName());

        log.info(
                "Import session created: id={}, status={}, totalTransactions={}",
                session.getId(),
                session.getStatus(),
                session.getTotalTransactions());

        return ResponseEntity.ok(session);
    }

    /**
     * Retrieves import session by ID with current status.
     *
     * <p>Use this endpoint to check import progress and session details. The status indicates the
     * current step in the import workflow:
     *
     * <ul>
     *   <li>PENDING: Session created, parsing not started
     *   <li>PARSING: File is being parsed
     *   <li>PARSED: Parsing complete, ready for review
     *   <li>REVIEWING: User is reviewing transactions
     *   <li>IMPORTING: Transactions are being saved
     *   <li>COMPLETED: Import finished successfully
     *   <li>FAILED: Import failed with error
     *   <li>CANCELLED: Import was cancelled by user
     * </ul>
     *
     * <p><strong>Request Headers:</strong>
     *
     * <ul>
     *   <li>Authorization: Bearer {jwt_token}
     * </ul>
     *
     * <p><strong>Success Response (HTTP 200 OK):</strong>
     *
     * <pre>{@code
     * {
     * "id": 42,
     * "uploadId": "550e8400-e29b-41d4-a716-446655440000",
     * "userId": 123,
     * "fileName": "transactions.qif",
     * "fileFormat": "QIF",
     * "accountId": 1,
     * "status": "PARSED",
     * "totalTransactions": 50,
     * "importedCount": 0,
     * "skippedCount": 0,
     * "errorCount": 2,
     * "metadata": "{...}",
     * "errorMessage": null,
     * "createdAt": "2026-02-02T10:00:00",
     * "completedAt": null
     * }
     * }</pre>
     *
     * <p><strong>Error Responses:</strong>
     *
     * <ul>
     *   <li>HTTP 404 Not Found - Session not found
     *   <li>HTTP 403 Forbidden - Session belongs to different user
     * </ul>
     *
     * <p>Requirement REQ-2.5.1.2: Import session status tracking
     *
     * @param id import session ID
     * @param authentication Spring Security authentication (auto-injected)
     * @return ImportSession with current status
     */
    @GetMapping("/sessions/{id}")
    public ResponseEntity<ImportSession> getSession(
            @PathVariable Long id, Authentication authentication) {

        Long userId = ControllerUtil.extractUserId(authentication);
        log.info("User {} retrieving import session: {}", userId, id);

        ImportSession session = importService.getSession(id, userId);

        return ResponseEntity.ok(session);
    }

    /**
     * Retrieves parsed transactions for user review.
     *
     * <p>Returns list of transactions extracted from the file with:
     *
     * <ul>
     *   <li>Duplicate detection flags (DUPLICATE: prefix in validationErrors)
     *   <li>Category suggestions (CATEGORY_SUGGESTION: or CATEGORY_UNKNOWN: in validationErrors)
     *   <li>Validation errors (missing required fields, invalid data)
     * </ul>
     *
     * <p>This endpoint can only be called when session status is PARSED or REVIEWING.
     *
     * <p><strong>Request Headers:</strong>
     *
     * <ul>
     *   <li>Authorization: Bearer {jwt_token}
     * </ul>
     *
     * <p><strong>Success Response (HTTP 200 OK):</strong>
     *
     * <pre>{@code
     * [
     * {
     * "date": "2026-01-15",
     * "amount": -45.67,
     * "payee": "Walmart",
     * "category": "Groceries",
     * "memo": "Weekly shopping",
     * "transactionType": "EXPENSE",
     * "checkNumber": null,
     * "validationErrors": [
     * "CATEGORY_SUGGESTION: Matched 'Groceries' with 95% confidence to category ID
     * 15",
     * "DUPLICATE: Similar transaction found on 2026-01-15 with 90% payee match"
     * ]
     * },
     * {
     * "date": "2026-01-20",
     * "amount": 2500.00,
     * "payee": "Employer Inc",
     * "category": "Salary",
     * "memo": "Monthly salary",
     * "transactionType": "INCOME",
     * "checkNumber": null,
     * "validationErrors": [
     * "CATEGORY_SUGGESTION: Matched 'Salary' with 100% confidence to category ID 3"
     * ]
     * }
     * ]
     * }</pre>
     *
     * <p><strong>Error Responses:</strong>
     *
     * <ul>
     *   <li>HTTP 404 Not Found - Session not found
     *   <li>HTTP 403 Forbidden - Session belongs to different user
     *   <li>HTTP 400 Bad Request - Session not ready for review (wrong status)
     * </ul>
     *
     * <p><strong>Validation Error Prefixes:</strong>
     *
     * <ul>
     *   <li><code>DUPLICATE:</code> - Potential duplicate transaction detected
     *   <li><code>CATEGORY_SUGGESTION:</code> - Category match found with confidence score
     *   <li><code>CATEGORY_UNKNOWN:</code> - No matching category found
     *   <li>Other errors indicate missing/invalid data
     * </ul>
     *
     * <p>Requirement REQ-2.5.1.4: Transaction preview and validation
     *
     * <p>Requirement REQ-2.5.1.5: Category suggestions during review
     *
     * <p>Requirement REQ-2.5.1.6: Duplicate detection with similarity matching
     *
     * @param id import session ID
     * @param authentication Spring Security authentication (auto-injected)
     * @return list of ImportedTransaction DTOs with validation results
     */
    @GetMapping("/sessions/{id}/review")
    public ResponseEntity<List<ImportedTransaction>> reviewTransactions(
            @PathVariable Long id, Authentication authentication) {

        Long userId = ControllerUtil.extractUserId(authentication);
        log.info("User {} reviewing transactions for session: {}", userId, id);

        List<ImportedTransaction> transactions = importService.reviewTransactions(id, userId);

        log.info(
                "Returning {} transactions for review ({} with errors, {} potential duplicates)",
                transactions.size(),
                transactions.stream().filter(ImportedTransaction::hasErrors).count(),
                transactions.stream().filter(ImportedTransaction::isPotentialDuplicate).count());

        return ResponseEntity.ok(transactions);
    }

    /**
     * Confirms import and saves transactions to database.
     *
     * <p>After user reviews transactions, maps categories, and decides how to handle duplicates,
     * this endpoint finalizes the import by:
     *
     * <ol>
     *   <li>Validating the target account
     *   <li>Applying category mappings
     *   <li>Filtering out transactions with validation errors
     *   <li>Optionally skipping duplicates
     *   <li>Saving valid transactions to the database
     *   <li>Updating session status to COMPLETED
     * </ol>
     *
     * <p>This endpoint can only be called when session status is PARSED or REVIEWING.
     *
     * <p><strong>Request Headers:</strong>
     *
     * <ul>
     *   <li>Authorization: Bearer {jwt_token}
     * </ul>
     *
     * <p><strong>Request Body:</strong>
     *
     * <pre>{@code
     * {
     * "accountId": 1,
     * "categoryMappings": {
     * "Groceries": 15,
     * "Salary": 3,
     * "Utilities": 22
     * },
     * "skipDuplicates": true
     * }
     * }</pre>
     *
     * <p><strong>Success Response (HTTP 200 OK):</strong>
     *
     * <pre>{@code
     * {
     * "id": 42,
     * "uploadId": "550e8400-e29b-41d4-a716-446655440000",
     * "userId": 123,
     * "fileName": "transactions.qif",
     * "fileFormat": "QIF",
     * "accountId": 1,
     * "status": "COMPLETED",
     * "totalTransactions": 50,
     * "importedCount": 45,
     * "skippedCount": 5,
     * "errorCount": 2,
     * "metadata": "{...}",
     * "errorMessage": null,
     * "createdAt": "2026-02-02T10:00:00",
     * "completedAt": "2026-02-02T10:05:30"
     * }
     * }</pre>
     *
     * <p><strong>Error Responses:</strong>
     *
     * <ul>
     *   <li>HTTP 400 Bad Request - Validation error (missing accountId)
     *   <li>HTTP 404 Not Found - Session not found
     *   <li>HTTP 403 Forbidden - Session belongs to different user
     *   <li>HTTP 404 Not Found - Account not found or not owned by user
     *   <li>HTTP 400 Bad Request - Account is inactive
     *   <li>HTTP 400 Bad Request - Session cannot be confirmed (wrong status)
     *   <li>HTTP 500 Internal Server Error - Error saving transactions
     * </ul>
     *
     * <p><strong>Category Mapping Behavior:</strong>
     *
     * <ul>
     *   <li>If category name is in categoryMappings, use the provided category ID
     *   <li>If category name not in map, system will auto-create category
     *   <li>Categories are created with default icon and color
     *   <li>CategoryType (INCOME/EXPENSE) is determined from transaction type
     * </ul>
     *
     * <p>Requirement REQ-2.5.1.7: Import confirmation and transaction creation
     *
     * <p>Requirement REQ-2.5.1.5: Category mapping during import
     *
     * <p>Requirement REQ-2.5.1.6: Duplicate handling (skip or import)
     *
     * @param id import session ID
     * @param request import confirmation request with account, mappings, and duplicate handling
     * @param authentication Spring Security authentication (auto-injected)
     * @return ImportSession with COMPLETED status and import statistics
     */
    @PostMapping("/sessions/{id}/confirm")
    public ResponseEntity<ImportSession> confirmImport(
            @PathVariable Long id,
            @Valid @RequestBody ImportConfirmRequest request,
            Authentication authentication) {

        Long userId = ControllerUtil.extractUserId(authentication);
        log.info(
                "User {} confirming import for session: {}, accountId={}, skipDuplicates={}",
                userId,
                id,
                request.getAccountId(),
                request.isSkipDuplicates());

        // Confirmation runs asynchronously: large imports can take well over a minute,
        // which
        // would otherwise exhaust the request timeout and abort the connection. The
        // session is
        // returned immediately in IMPORTING status; the client polls GET /sessions/{id}
        // until
        // the status becomes COMPLETED or FAILED.
        ImportSession session =
                importService.startConfirmImport(
                        id,
                        userId,
                        request.getAccountId(),
                        request.getCategoryMappings(),
                        request.isSkipDuplicates());

        log.info(
                "Import confirmation accepted for session {} (status={})",
                session.getId(),
                session.getStatus());

        return ResponseEntity.accepted().body(session);
    }

    /**
     * Cancels an import session.
     *
     * <p>Use this endpoint to cancel an import that is no longer needed. Only sessions in PENDING,
     * PARSED, or REVIEWING status can be cancelled. Sessions that are IMPORTING, COMPLETED, FAILED,
     * or already CANCELLED cannot be cancelled.
     *
     * <p><strong>Request Headers:</strong>
     *
     * <ul>
     *   <li>Authorization: Bearer {jwt_token}
     * </ul>
     *
     * <p><strong>Success Response (HTTP 200 OK):</strong>
     *
     * <pre>{@code
     * {
     * "id": 42,
     * "uploadId": "550e8400-e29b-41d4-a716-446655440000",
     * "userId": 123,
     * "fileName": "transactions.qif",
     * "fileFormat": "QIF",
     * "accountId": 1,
     * "status": "CANCELLED",
     * "totalTransactions": 50,
     * "importedCount": 0,
     * "skippedCount": 0,
     * "errorCount": 2,
     * "metadata": "{...}",
     * "errorMessage": null,
     * "createdAt": "2026-02-02T10:00:00",
     * "completedAt": "2026-02-02T10:03:15"
     * }
     * }</pre>
     *
     * <p><strong>Error Responses:</strong>
     *
     * <ul>
     *   <li>HTTP 404 Not Found - Session not found
     *   <li>HTTP 403 Forbidden - Session belongs to different user
     *   <li>HTTP 400 Bad Request - Session cannot be cancelled (wrong status)
     * </ul>
     *
     * <p>Requirement REQ-2.5.1.2: Import session lifecycle management
     *
     * @param id import session ID
     * @param authentication Spring Security authentication (auto-injected)
     * @return ImportSession with CANCELLED status
     */
    @PostMapping("/sessions/{id}/cancel")
    public ResponseEntity<ImportSession> cancelImport(
            @PathVariable Long id, Authentication authentication) {

        Long userId = ControllerUtil.extractUserId(authentication);
        log.info("User {} cancelling import session: {}", userId, id);

        ImportSession session = importService.cancelImport(id, userId);

        log.info("Import session cancelled: {}", id);

        return ResponseEntity.ok(session);
    }

    /** Update the target account for an import session. */
    @PutMapping("/sessions/{id}/account")
    public ResponseEntity<ImportSession> updateAccount(
            @PathVariable Long id, @RequestParam Long accountId, Authentication authentication) {

        Long userId = ControllerUtil.extractUserId(authentication);
        log.info("User {} updating account for import session: {} to {}", userId, id, accountId);

        ImportSession session = importService.updateAccount(id, accountId, userId);

        return ResponseEntity.ok(session);
    }

    /** Update the parsed transactions in the session metadata. */
    @PutMapping("/sessions/{id}/transactions")
    public ResponseEntity<ImportSession> updateParsedTransactions(
            @PathVariable Long id,
            @RequestBody List<ImportedTransaction> transactions,
            Authentication authentication) {

        Long userId = ControllerUtil.extractUserId(authentication);
        log.info("User {} updating transactions for import session: {}", userId, id);

        ImportSession session = importService.updateParsedTransactions(id, transactions, userId);

        return ResponseEntity.ok(session);
    }

    /**
     * Retrieves all import sessions for the authenticated user.
     *
     * <p>Returns sessions ordered by creation date (most recent first). Includes all sessions
     * regardless of status (PENDING, COMPLETED, FAILED, CANCELLED, etc.).
     *
     * <p><strong>Request Headers:</strong>
     *
     * <ul>
     *   <li>Authorization: Bearer {jwt_token}
     * </ul>
     *
     * <p><strong>Success Response (HTTP 200 OK):</strong>
     *
     * <pre>{@code
     * [
     * {
     * "id": 42,
     * "fileName": "transactions.qif",
     * "status": "COMPLETED",
     * "totalTransactions": 50,
     * "importedCount": 45,
     * "skippedCount": 5,
     * "createdAt": "2026-02-02T10:00:00",
     * "completedAt": "2026-02-02T10:05:30"
     * },
     * {
     * "id": 38,
     * "fileName": "bank_export.ofx",
     * "status": "PARSED",
     * "totalTransactions": 120,
     * "importedCount": 0,
     * "skippedCount": 0,
     * "createdAt": "2026-02-01T14:30:00",
     * "completedAt": null
     * }
     * ]
     * }</pre>
     *
     * <p><strong>Use Cases:</strong>
     *
     * <ul>
     *   <li>Display import history to user
     *   <li>Resume incomplete imports
     *   <li>Review past import results
     * </ul>
     *
     * <p>Requirement REQ-2.5.1.2: Import session history and tracking
     *
     * @param authentication Spring Security authentication (auto-injected)
     * @return list of ImportSession ordered by creation date descending
     */
    @GetMapping("/sessions")
    public ResponseEntity<List<ImportSession>> getUserSessions(Authentication authentication) {

        Long userId = ControllerUtil.extractUserId(authentication);
        log.info("User {} retrieving import session history", userId);

        List<ImportSession> sessions = importService.getUserSessions(userId);

        log.info("Returning {} import sessions for user {}", sessions.size(), userId);

        return ResponseEntity.ok(sessions);
    }

    // ========================================
    // Helper Methods
    // ========================================

    // extractUserId() method removed - now using ControllerUtil.extractUserId()
}
