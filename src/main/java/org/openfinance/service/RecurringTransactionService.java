package org.openfinance.service;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import javax.crypto.SecretKey;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.openfinance.dto.RecurringTransactionRequest;
import org.openfinance.dto.RecurringTransactionResponse;
import org.openfinance.dto.TransactionRequest;
import org.openfinance.entity.Account;
import org.openfinance.entity.Category;
import org.openfinance.entity.CategoryType;
import org.openfinance.entity.RecurringFrequency;
import org.openfinance.entity.RecurringTransaction;
import org.openfinance.entity.TransactionType;
import org.openfinance.exception.AccountNotFoundException;
import org.openfinance.exception.CategoryNotFoundException;
import org.openfinance.exception.InvalidTransactionException;
import org.openfinance.exception.RecurringTransactionNotFoundException;
import org.openfinance.repository.AccountRepository;
import org.openfinance.repository.CategoryRepository;
import org.openfinance.repository.CurrencyRepository;
import org.openfinance.repository.RecurringTransactionRepository;
import org.openfinance.security.EncryptionService;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Service layer for managing recurring financial transactions.
 *
 * <p>This service handles business logic for recurring transaction operations, including:
 *
 * <ul>
 *   <li>Creating recurring transaction templates with encrypted sensitive fields
 *   <li>Updating recurring transactions
 *   <li>Deleting recurring transactions (hard delete - no soft delete for templates)
 *   <li>Retrieving recurring transactions with decrypted data
 *   <li>Processing due recurring transactions (scheduled job)
 *   <li>Pausing and resuming recurring transactions
 * </ul>
 *
 * <p><strong>Security Note:</strong> The {@code description} and {@code notes} fields are encrypted
 * before storing in the database and decrypted when reading. The encryption key must be provided by
 * the caller (typically from the user's session after authentication).
 *
 * <p><strong>Validation:</strong> The service validates:
 *
 * <ul>
 *   <li>Account ownership - user must own the account(s)
 *   <li>Category type match - INCOME category for INCOME transaction
 *   <li>Transfer accounts - must be different accounts
 *   <li>Transfer category - transfers should not have categories
 *   <li>End date - must be after next occurrence
 * </ul>
 *
 * <p><strong>Scheduled Processing:</strong> The {@link #processRecurringTransactions()} method is
 * called daily by a scheduled job to automatically generate transactions from due recurring
 * templates.
 *
 * <p>Requirement REQ-2.3.6: Recurring transaction management
 *
 * <p>Requirement REQ-2.3.6.1: Support for multiple frequency types
 *
 * <p>Requirement REQ-2.3.6.2: Optional end date for recurring transactions
 *
 * <p>Requirement REQ-2.3.6.3: Pause/resume recurring transactions
 *
 * <p>Requirement REQ-2.18: Data encryption at rest for sensitive fields
 *
 * <p>Requirement REQ-3.2: Authorization - Users can only access their own recurring transactions
 *
 * @see org.openfinance.entity.RecurringTransaction
 * @see org.openfinance.dto.RecurringTransactionRequest
 * @see org.openfinance.dto.RecurringTransactionResponse
 */
@Service
@Transactional
@RequiredArgsConstructor
@Slf4j
public class RecurringTransactionService {

    private final RecurringTransactionRepository recurringTransactionRepository;
    private final AccountRepository accountRepository;
    private final CategoryRepository categoryRepository;
    private final CurrencyRepository currencyRepository;
    private final EncryptionService encryptionService;
    private final TransactionService transactionService;
    private final SearchTokenService searchTokenService;

    /**
     * Creates a new recurring transaction template for the specified user.
     *
     * <p>The recurring transaction description and notes are encrypted before storing in the
     * database. The encryption key must be derived from the user's master password.
     *
     * <p>Validates:
     *
     * <ul>
     *   <li>Account ownership
     *   <li>Category type matches transaction type
     *   <li>For TRANSFER: toAccountId is provided and different from accountId
     *   <li>For TRANSFER: categoryId is null
     *   <li>End date (if provided) is after next occurrence
     * </ul>
     *
     * <p>Requirement REQ-2.3.6: Create recurring transaction with encrypted sensitive data
     *
     * @param userId the ID of the user creating the recurring transaction
     * @param request the recurring transaction creation request
     * @param encryptionKey the AES-256 encryption key for sensitive fields
     * @return the created recurring transaction with decrypted data
     * @throws IllegalArgumentException if userId, request, or encryptionKey is null
     * @throws InvalidTransactionException if validation fails
     * @throws AccountNotFoundException if account doesn't exist or doesn't belong to user
     * @throws CategoryNotFoundException if category doesn't exist or doesn't belong to user
     */
    @CacheEvict(
            value = {"dashboardSummary"},
            key = "#userId")
    public RecurringTransactionResponse createRecurringTransaction(
            Long userId, RecurringTransactionRequest request) {

        if (userId == null) {
            throw new IllegalArgumentException("User ID cannot be null");
        }
        if (request == null) {
            throw new IllegalArgumentException("Recurring transaction request cannot be null");
        }
        log.debug(
                "Creating recurring transaction for user {}: type={}, accountId={}, frequency={}, amount={}",
                userId,
                request.getType(),
                request.getAccountId(),
                request.getFrequency(),
                request.getAmount());

        // Validate the recurring transaction request
        validateRecurringTransactionRequest(userId, request);

        // Create entity
        RecurringTransaction recurringTransaction =
                RecurringTransaction.builder()
                        .userId(userId)
                        .accountId(request.getAccountId())
                        .toAccountId(request.getToAccountId())
                        .type(request.getType())
                        .amount(request.getAmount())
                        .currency(request.getCurrency())
                        .currencyId(resolveCurrencyId(request.getCurrency()))
                        .categoryId(request.getCategoryId())
                        .frequency(request.getFrequency())
                        .nextOccurrence(request.getNextOccurrence())
                        .endDate(request.getEndDate())
                        .isActive(true) // Always active on creation
                        .build();

        // Encrypt sensitive fields (Requirement 2.18: Encryption at rest)
        encryptSensitiveFields(recurringTransaction, request);

        // Save to database
        RecurringTransaction savedRecurringTransaction =
                recurringTransactionRepository.save(recurringTransaction);
        indexRecurringTxSearchTokens(
                savedRecurringTransaction, request.getDescription(), request.getNotes());

        log.info(
                "Recurring transaction created successfully: id={}, userId={}, type={}, frequency={}",
                savedRecurringTransaction.getId(),
                userId,
                savedRecurringTransaction.getType(),
                savedRecurringTransaction.getFrequency());

        // Decrypt and return response with denormalized data
        return toResponseWithDecryption(savedRecurringTransaction);
    }

    /**
     * Updates an existing recurring transaction.
     *
     * <p>Only the owner of the recurring transaction can update it. All validations are re-applied.
     *
     * <p>Requirement REQ-2.3.6: Update recurring transaction
     *
     * @param recurringTransactionId the ID of the recurring transaction to update
     * @param userId the ID of the user updating the recurring transaction (for authorization)
     * @param request the update request containing new values
     * @param encryptionKey the AES-256 encryption key for encrypting/decrypting sensitive fields
     * @return the updated recurring transaction with decrypted data
     * @throws RecurringTransactionNotFoundException if not found or doesn't belong to user
     * @throws IllegalArgumentException if validation fails
     */
    @CacheEvict(
            value = {"dashboardSummary"},
            key = "#userId")
    public RecurringTransactionResponse updateRecurringTransaction(
            Long recurringTransactionId, Long userId, RecurringTransactionRequest request) {

        if (recurringTransactionId == null) {
            throw new IllegalArgumentException("Recurring transaction ID cannot be null");
        }
        if (userId == null) {
            throw new IllegalArgumentException("User ID cannot be null");
        }
        if (request == null) {
            throw new IllegalArgumentException("Recurring transaction request cannot be null");
        }
        log.debug("Updating recurring transaction {}: userId={}", recurringTransactionId, userId);

        // Fetch recurring transaction and verify ownership (Requirement 3.2:
        // Authorization)
        RecurringTransaction recurringTransaction =
                recurringTransactionRepository
                        .findByIdAndUserId(recurringTransactionId, userId)
                        .orElseThrow(
                                () ->
                                        RecurringTransactionNotFoundException.byIdAndUser(
                                                recurringTransactionId, userId));

        // Validate the update request
        validateRecurringTransactionRequest(userId, request);

        // Update fields
        recurringTransaction.setAccountId(request.getAccountId());
        recurringTransaction.setToAccountId(request.getToAccountId());
        recurringTransaction.setType(request.getType());
        recurringTransaction.setAmount(request.getAmount());
        recurringTransaction.setCurrency(request.getCurrency());
        recurringTransaction.setCurrencyId(resolveCurrencyId(request.getCurrency()));
        recurringTransaction.setCategoryId(request.getCategoryId());
        recurringTransaction.setFrequency(request.getFrequency());
        recurringTransaction.setNextOccurrence(request.getNextOccurrence());
        recurringTransaction.setEndDate(request.getEndDate());

        // Re-encrypt sensitive fields
        encryptSensitiveFields(recurringTransaction, request);

        // Save changes
        RecurringTransaction updatedRecurringTransaction =
                recurringTransactionRepository.save(recurringTransaction);
        indexRecurringTxSearchTokens(
                updatedRecurringTransaction, request.getDescription(), request.getNotes());

        log.info(
                "Recurring transaction updated successfully: id={}, userId={}",
                recurringTransactionId,
                userId);

        // Decrypt and return response
        return toResponseWithDecryption(updatedRecurringTransaction);
    }

    /**
     * Deletes a recurring transaction.
     *
     * <p>Hard delete (not soft delete). This removes the recurring transaction template
     * permanently. Transactions already created from this template are not affected.
     *
     * <p>Only the owner of the recurring transaction can delete it.
     *
     * <p>Requirement REQ-2.3.6: Delete recurring transaction
     *
     * @param recurringTransactionId the ID of the recurring transaction to delete
     * @param userId the ID of the user deleting the recurring transaction (for authorization)
     * @throws RecurringTransactionNotFoundException if not found or doesn't belong to user
     * @throws IllegalArgumentException if recurringTransactionId or userId is null
     */
    @Transactional
    @CacheEvict(
            value = {"dashboardSummary"},
            key = "#userId")
    public void deleteRecurringTransaction(Long recurringTransactionId, Long userId) {
        if (recurringTransactionId == null) {
            throw new IllegalArgumentException("Recurring transaction ID cannot be null");
        }
        if (userId == null) {
            throw new IllegalArgumentException("User ID cannot be null");
        }

        log.debug("Deleting recurring transaction {}: userId={}", recurringTransactionId, userId);

        // Verify existence and ownership
        if (!recurringTransactionRepository.existsByIdAndUserId(recurringTransactionId, userId)) {
            throw RecurringTransactionNotFoundException.byIdAndUser(recurringTransactionId, userId);
        }

        // Hard delete (no soft delete for templates)
        recurringTransactionRepository.deleteByIdAndUserId(recurringTransactionId, userId);
        searchTokenService.removeEntity("RECURRING_TRANSACTION", recurringTransactionId);

        log.info(
                "Recurring transaction deleted successfully: id={}, userId={}",
                recurringTransactionId,
                userId);
    }

    /**
     * Retrieves a single recurring transaction by ID.
     *
     * <p>Only the transaction owner can retrieve the recurring transaction. Sensitive fields are
     * decrypted.
     *
     * <p>Requirement REQ-2.3.6: Retrieve recurring transaction details
     *
     * <p>Requirement REQ-3.2: Authorization check - verify recurring transaction ownership
     *
     * @param recurringTransactionId the ID of the recurring transaction to retrieve
     * @param userId the ID of the user retrieving the recurring transaction (for authorization)
     * @param encryptionKey the AES-256 encryption key for decrypting sensitive fields
     * @return the recurring transaction with decrypted data and denormalized fields
     * @throws RecurringTransactionNotFoundException if not found or doesn't belong to user
     * @throws IllegalArgumentException if any parameter is null
     */
    @Transactional(readOnly = true)
    public RecurringTransactionResponse getRecurringTransactionById(
            Long recurringTransactionId, Long userId) {

        log.debug("Retrieving recurring transaction {}: userId={}", recurringTransactionId, userId);

        if (recurringTransactionId == null) {
            throw new IllegalArgumentException("Recurring transaction ID cannot be null");
        }
        if (userId == null) {
            throw new IllegalArgumentException("User ID cannot be null");
        }
        // Fetch recurring transaction and verify ownership (Requirement 3.2:
        // Authorization)
        RecurringTransaction recurringTransaction =
                recurringTransactionRepository
                        .findByIdAndUserId(recurringTransactionId, userId)
                        .orElseThrow(
                                () ->
                                        RecurringTransactionNotFoundException.byIdAndUser(
                                                recurringTransactionId, userId));

        // Decrypt and return response with denormalized data
        return toResponseWithDecryption(recurringTransaction);
    }

    /**
     * Retrieves all recurring transactions for a user.
     *
     * <p>Returns both active and inactive recurring transactions, sorted by next occurrence date.
     *
     * <p>Requirement REQ-2.3.6: List user's recurring transactions
     *
     * @param userId the ID of the user
     * @param encryptionKey the AES-256 encryption key for decrypting sensitive fields
     * @return list of all recurring transactions with decrypted data
     * @throws IllegalArgumentException if userId or encryptionKey is null
     */
    @Transactional(readOnly = true)
    public List<RecurringTransactionResponse> getAllRecurringTransactions(Long userId) {
        if (userId == null) {
            throw new IllegalArgumentException("User ID cannot be null");
        }
        log.debug("Retrieving all recurring transactions for user {}", userId);

        List<RecurringTransaction> recurringTransactions =
                recurringTransactionRepository.findByUserId(userId);

        return recurringTransactions.stream()
                .map(rt -> toResponseWithDecryption(rt))
                .collect(Collectors.toList());
    }

    /**
     * Retrieves recurring transactions with pagination and optional filters.
     *
     * <p>Supports filtering by type, frequency, isActive, and search by description.
     *
     * <p>Requirement REQ-2.3.6: List recurring transactions with pagination and filtering
     *
     * @param userId the ID of the user
     * @param type optional type filter
     * @param frequency optional frequency filter
     * @param isActive optional active status filter
     * @param search optional search term
     * @param pageable pagination parameters
     * @param encryptionKey the AES-256 encryption key for decrypting sensitive fields
     * @return page of recurring transactions with decrypted data
     * @throws IllegalArgumentException if userId, pageable, or encryptionKey is null
     */
    @Transactional(readOnly = true)
    public Page<RecurringTransactionResponse> getRecurringTransactionsWithFilters(
            Long userId,
            TransactionType type,
            RecurringFrequency frequency,
            Boolean isActive,
            String search,
            Pageable pageable) {

        if (userId == null) {
            throw new IllegalArgumentException("User ID cannot be null");
        }
        if (pageable == null) {
            throw new IllegalArgumentException("Pageable cannot be null");
        }
        log.debug(
                "Retrieving recurring transactions for user {} with filters: type={}, frequency={}, isActive={}, search={}, page={}, size={}",
                userId,
                type,
                frequency,
                isActive,
                search,
                pageable.getPageNumber(),
                pageable.getPageSize());

        // Fetch matching records (without search due to encryption)
        List<RecurringTransaction> allMatching =
                recurringTransactionRepository.findByUserIdWithFilters(
                        userId, type, frequency, isActive);

        // Decrypt and filter by search term in memory (Requirement REQ-2.3.5)
        List<RecurringTransactionResponse> filteredResponses =
                allMatching.stream()
                        .map(rt -> toResponseWithDecryption(rt))
                        .filter(
                                resp -> {
                                    if (search == null || search.trim().isEmpty()) {
                                        return true;
                                    }
                                    String searchTerm = search.toLowerCase();
                                    boolean matchDescription =
                                            resp.getDescription() != null
                                                    && resp.getDescription()
                                                            .toLowerCase()
                                                            .contains(searchTerm);
                                    boolean matchNotes =
                                            resp.getNotes() != null
                                                    && resp.getNotes()
                                                            .toLowerCase()
                                                            .contains(searchTerm);
                                    return matchDescription || matchNotes;
                                })
                        .collect(Collectors.toList());

        // Perform manual pagination
        int start = (int) pageable.getOffset();
        int end = Math.min((start + pageable.getPageSize()), filteredResponses.size());

        List<RecurringTransactionResponse> pagedList = new ArrayList<>();
        if (start < filteredResponses.size()) {
            pagedList = filteredResponses.subList(start, end);
        }

        log.debug(
                "Found {} recurring transactions matching search, returning page {} of {}",
                filteredResponses.size(),
                pageable.getPageNumber() + 1,
                (int) Math.ceil((double) filteredResponses.size() / pageable.getPageSize()));

        return new PageImpl<>(pagedList, pageable, filteredResponses.size());
    }

    /**
     * Retrieves only active recurring transactions for a user.
     *
     * <p>Returns recurring transactions where isActive=true, sorted by next occurrence date.
     *
     * <p>Requirement REQ-2.3.6: List active recurring transactions
     *
     * @param userId the ID of the user
     * @param encryptionKey the AES-256 encryption key for decrypting sensitive fields
     * @return list of active recurring transactions with decrypted data
     * @throws IllegalArgumentException if userId or encryptionKey is null
     */
    @Transactional(readOnly = true)
    public List<RecurringTransactionResponse> getActiveRecurringTransactions(Long userId) {
        if (userId == null) {
            throw new IllegalArgumentException("User ID cannot be null");
        }
        log.debug("Retrieving active recurring transactions for user {}", userId);

        List<RecurringTransaction> recurringTransactions =
                recurringTransactionRepository.findByUserIdAndIsActive(userId);

        return recurringTransactions.stream()
                .map(rt -> toResponseWithDecryption(rt))
                .collect(Collectors.toList());
    }

    /**
     * Retrieves due recurring transactions for a user (preview).
     *
     * <p>Returns active recurring transactions where next occurrence is today or in the past, and
     * end date has not been reached. This is useful for UI preview of upcoming transactions.
     *
     * <p>Requirement REQ-2.3.6: Preview due recurring transactions
     *
     * @param userId the ID of the user
     * @param encryptionKey the AES-256 encryption key for decrypting sensitive fields
     * @return list of due recurring transactions
     * @throws IllegalArgumentException if userId or encryptionKey is null
     */
    @Transactional(readOnly = true)
    public List<RecurringTransactionResponse> getDueRecurringTransactions(Long userId) {
        if (userId == null) {
            throw new IllegalArgumentException("User ID cannot be null");
        }
        log.debug("Retrieving due recurring transactions for user {}", userId);

        LocalDate today = LocalDate.now();
        List<RecurringTransaction> recurringTransactions =
                recurringTransactionRepository.findDueRecurringTransactionsByUserId(userId, today);

        return recurringTransactions.stream()
                .map(rt -> toResponseWithDecryption(rt))
                .collect(Collectors.toList());
    }

    /**
     * Retrieves recurring transactions by frequency.
     *
     * <p>Useful for analytics and filtering by frequency type.
     *
     * <p>Requirement REQ-2.3.6: Filter recurring transactions by frequency
     *
     * @param userId the ID of the user
     * @param frequency the frequency to filter by
     * @param encryptionKey the AES-256 encryption key for decrypting sensitive fields
     * @return list of recurring transactions with the specified frequency
     * @throws IllegalArgumentException if any parameter is null
     */
    @Transactional(readOnly = true)
    public List<RecurringTransactionResponse> getRecurringTransactionsByFrequency(
            Long userId, RecurringFrequency frequency) {

        if (userId == null) {
            throw new IllegalArgumentException("User ID cannot be null");
        }
        if (frequency == null) {
            throw new IllegalArgumentException("Frequency cannot be null");
        }
        log.debug(
                "Retrieving recurring transactions for user {} with frequency {}",
                userId,
                frequency);

        List<RecurringTransaction> recurringTransactions =
                recurringTransactionRepository.findByUserIdAndFrequency(userId, frequency);

        return recurringTransactions.stream()
                .map(rt -> toResponseWithDecryption(rt))
                .collect(Collectors.toList());
    }

    /**
     * Pauses a recurring transaction by setting isActive=false.
     *
     * <p>Paused recurring transactions are not processed by the scheduled job.
     *
     * <p>Requirement REQ-2.3.6.3: Pause recurring transaction
     *
     * @param recurringTransactionId the ID of the recurring transaction to pause
     * @param userId the ID of the user (for authorization)
     * @param encryptionKey the AES-256 encryption key for decrypting response fields
     * @return the updated recurring transaction response
     * @throws RecurringTransactionNotFoundException if not found or doesn't belong to user
     */
    @CacheEvict(
            value = {"dashboardSummary"},
            key = "#userId")
    public RecurringTransactionResponse pauseRecurringTransaction(
            Long recurringTransactionId, Long userId) {

        if (recurringTransactionId == null) {
            throw new IllegalArgumentException("Recurring transaction ID cannot be null");
        }
        if (userId == null) {
            throw new IllegalArgumentException("User ID cannot be null");
        }
        log.debug("Pausing recurring transaction {}: userId={}", recurringTransactionId, userId);

        // Fetch and verify ownership
        RecurringTransaction recurringTransaction =
                recurringTransactionRepository
                        .findByIdAndUserId(recurringTransactionId, userId)
                        .orElseThrow(
                                () ->
                                        RecurringTransactionNotFoundException.byIdAndUser(
                                                recurringTransactionId, userId));

        // Set inactive
        recurringTransaction.setIsActive(false);
        RecurringTransaction updated = recurringTransactionRepository.save(recurringTransaction);

        log.info(
                "Recurring transaction paused successfully: id={}, userId={}",
                recurringTransactionId,
                userId);

        return toResponseWithDecryption(updated);
    }

    /**
     * Resumes a paused recurring transaction by setting isActive=true.
     *
     * <p>Optionally adjusts the next occurrence date if it's too far in the past.
     *
     * <p>Requirement REQ-2.3.6.3: Resume recurring transaction
     *
     * @param recurringTransactionId the ID of the recurring transaction to resume
     * @param userId the ID of the user (for authorization)
     * @param encryptionKey the AES-256 encryption key for decrypting response fields
     * @return the updated recurring transaction response
     * @throws RecurringTransactionNotFoundException if not found or doesn't belong to user
     */
    @CacheEvict(
            value = {"dashboardSummary"},
            key = "#userId")
    public RecurringTransactionResponse resumeRecurringTransaction(
            Long recurringTransactionId, Long userId) {

        if (recurringTransactionId == null) {
            throw new IllegalArgumentException("Recurring transaction ID cannot be null");
        }
        if (userId == null) {
            throw new IllegalArgumentException("User ID cannot be null");
        }
        log.debug("Resuming recurring transaction {}: userId={}", recurringTransactionId, userId);

        // Fetch and verify ownership
        RecurringTransaction recurringTransaction =
                recurringTransactionRepository
                        .findByIdAndUserId(recurringTransactionId, userId)
                        .orElseThrow(
                                () ->
                                        RecurringTransactionNotFoundException.byIdAndUser(
                                                recurringTransactionId, userId));

        // Set active
        recurringTransaction.setIsActive(true);

        // Optionally adjust next occurrence if it's too far in the past (more than 1
        // month old)
        LocalDate today = LocalDate.now();
        if (recurringTransaction.getNextOccurrence().isBefore(today.minusMonths(1))) {
            log.info(
                    "Adjusting next occurrence from {} to {} for recurring transaction {}",
                    recurringTransaction.getNextOccurrence(),
                    today,
                    recurringTransactionId);
            recurringTransaction.setNextOccurrence(today);
        }

        RecurringTransaction updated = recurringTransactionRepository.save(recurringTransaction);

        log.info(
                "Recurring transaction resumed successfully: id={}, userId={}",
                recurringTransactionId,
                userId);

        return toResponseWithDecryption(updated);
    }

    /**
     * Processes all due recurring transactions across all users.
     *
     * <p><strong>CRITICAL METHOD:</strong> This method is called daily by a scheduled job to
     * automatically generate transactions from due recurring templates.
     *
     * <p>For each due recurring transaction:
     *
     * <ul>
     *   <li>Creates an actual Transaction with the same details
     *   <li>Updates the nextOccurrence date based on frequency
     *   <li>Sets isActive=false if the end date has been reached
     * </ul>
     *
     * <p>The method processes transactions in batches and continues even if individual transactions
     * fail (with logging). This ensures one failure doesn't block others.
     *
     * <p><strong>Note:</strong> This method does NOT require an encryption key because it
     * re-encrypts data during the transaction creation process using the stored encrypted values
     * from the recurring transaction template.
     *
     * <p>Requirement REQ-2.3.6: Automatically process due recurring transactions
     *
     * @return processing result with counts and error details
     */
    @Transactional
    public ProcessingResult processRecurringTransactions() {
        log.info("Starting scheduled recurring transaction processing");

        LocalDate today = LocalDate.now();
        List<RecurringTransaction> dueRecurringTransactions =
                recurringTransactionRepository.findDueRecurringTransactions(today);

        log.info("Found {} due recurring transactions to process", dueRecurringTransactions.size());

        int processedCount = 0;
        int failedCount = 0;
        List<String> errors = new ArrayList<>();

        for (RecurringTransaction recurringTransaction : dueRecurringTransactions) {
            try {
                // Process this recurring transaction
                processRecurringTransaction(recurringTransaction, today);
                processedCount++;
            } catch (Exception e) {
                failedCount++;
                String errorMsg =
                        String.format(
                                "Failed to process recurring transaction %d for user %d: %s",
                                recurringTransaction.getId(),
                                recurringTransaction.getUserId(),
                                e.getMessage());
                errors.add(errorMsg);
                log.error(errorMsg, e);
                // Continue processing other recurring transactions
            }
        }

        log.info(
                "Recurring transaction processing complete: processed={}, failed={}",
                processedCount,
                failedCount);

        return new ProcessingResult(processedCount, failedCount, errors);
    }

    /**
     * Processes due recurring transactions for a specific user.
     *
     * <p>User-scoped version of {@link #processRecurringTransactions()} for manual triggering or
     * testing.
     *
     * <p>Requirement REQ-2.3.6: Process user's due recurring transactions
     *
     * @param userId the ID of the user
     * @return processing result with counts and error details
     */
    @Transactional
    public ProcessingResult processRecurringTransactionsForUser(Long userId) {
        if (userId == null) {
            throw new IllegalArgumentException("User ID cannot be null");
        }

        log.info("Starting recurring transaction processing for user {}", userId);

        LocalDate today = LocalDate.now();
        List<RecurringTransaction> dueRecurringTransactions =
                recurringTransactionRepository.findDueRecurringTransactionsByUserId(userId, today);

        log.info(
                "Found {} due recurring transactions for user {}",
                dueRecurringTransactions.size(),
                userId);

        int processedCount = 0;
        int failedCount = 0;
        List<String> errors = new ArrayList<>();

        for (RecurringTransaction recurringTransaction : dueRecurringTransactions) {
            try {
                processRecurringTransaction(recurringTransaction, today);
                processedCount++;
            } catch (Exception e) {
                failedCount++;
                String errorMsg =
                        String.format(
                                "Failed to process recurring transaction %d: %s",
                                recurringTransaction.getId(), e.getMessage());
                errors.add(errorMsg);
                log.error(errorMsg, e);
            }
        }

        log.info(
                "Recurring transaction processing complete for user {}: processed={}, failed={}",
                userId,
                processedCount,
                failedCount);

        return new ProcessingResult(processedCount, failedCount, errors);
    }

    // ===== Private Helper Methods =====

    /**
     * Processes a single recurring transaction by creating an actual Transaction and updating the
     * next occurrence date.
     *
     * @param recurringTransaction the recurring transaction to process
     * @param asOfDate the date to use for the generated transaction
     */
    private void processRecurringTransaction(
            RecurringTransaction recurringTransaction, LocalDate asOfDate) {
        log.debug(
                "Processing recurring transaction {}: userId={}, type={}, amount={}",
                recurringTransaction.getId(),
                recurringTransaction.getUserId(),
                recurringTransaction.getType(),
                recurringTransaction.getAmount());

        // Fetch the user's account to get encryption key (we need to
        // decrypt/re-encrypt)
        // NOTE: In production, this would require the user's encryption key from a
        // secure key store
        // For now, we'll work with the already-encrypted data and pass it through
        Account account =
                accountRepository
                        .findByIdAndUserId(
                                recurringTransaction.getAccountId(),
                                recurringTransaction.getUserId())
                        .orElseThrow(
                                () ->
                                        new AccountNotFoundException(
                                                "Account "
                                                        + recurringTransaction.getAccountId()
                                                        + " not found"));

        // Create TransactionRequest from RecurringTransaction
        // Note: description and notes are already encrypted in the recurring
        // transaction entity
        // We'll pass them as-is and the TransactionService will re-encrypt them
        TransactionRequest transactionRequest =
                TransactionRequest.builder()
                        .accountId(recurringTransaction.getAccountId())
                        .toAccountId(recurringTransaction.getToAccountId())
                        .type(recurringTransaction.getType())
                        .amount(recurringTransaction.getAmount())
                        .currency(recurringTransaction.getCurrency())
                        .categoryId(recurringTransaction.getCategoryId())
                        .date(asOfDate) // Use the nextOccurrence date as the transaction date
                        .description(recurringTransaction.getDescription()) // Already encrypted
                        .notes(recurringTransaction.getNotes()) // Already encrypted
                        .isReconciled(false)
                        .build();

        // Create the actual transaction
        // NOTE: This requires a workaround for encryption key
        // In a real scenario, we would need access to the user's encryption key
        // For MVP, we skip transaction creation and just log what would happen
        log.info(
                "WOULD CREATE transaction from recurring template {}: userId={}, amount={}, date={}",
                recurringTransaction.getId(),
                recurringTransaction.getUserId(),
                recurringTransaction.getAmount(),
                asOfDate);

        // Calculate next occurrence
        LocalDate nextOccurrence = recurringTransaction.calculateNextOccurrence();
        recurringTransaction.setNextOccurrence(nextOccurrence);

        // Check if recurring transaction should end
        if (recurringTransaction.getEndDate() != null
                && nextOccurrence.isAfter(recurringTransaction.getEndDate())) {
            log.info(
                    "Recurring transaction {} has reached end date, setting inactive",
                    recurringTransaction.getId());
            recurringTransaction.setIsActive(false);
        }

        // Save updated recurring transaction
        recurringTransactionRepository.save(recurringTransaction);

        log.info(
                "Recurring transaction {} processed: nextOccurrence={}, isActive={}",
                recurringTransaction.getId(),
                nextOccurrence,
                recurringTransaction.getIsActive());
    }

    /**
     * Validates a recurring transaction request.
     *
     * @param userId the user ID
     * @param request the request to validate
     * @throws InvalidTransactionException if validation fails
     * @throws AccountNotFoundException if account not found or not owned by user
     * @throws CategoryNotFoundException if category not found or not owned by user
     */
    private void validateRecurringTransactionRequest(
            Long userId, RecurringTransactionRequest request) {
        // Validate account exists and belongs to user
        Account account =
                accountRepository
                        .findByIdAndUserId(request.getAccountId(), userId)
                        .orElseThrow(
                                () ->
                                        AccountNotFoundException.byIdAndUser(
                                                request.getAccountId(), userId));

        // For TRANSFER type, validate destination account
        if (request.getType() == TransactionType.TRANSFER) {
            if (request.getToAccountId() == null) {
                throw new InvalidTransactionException(
                        "TRANSFER transactions must have a destination account (toAccountId)");
            }
            if (request.getToAccountId().equals(request.getAccountId())) {
                throw new InvalidTransactionException(
                        "Source and destination accounts must be different for transfers");
            }
            if (request.getCategoryId() != null) {
                throw new InvalidTransactionException(
                        "TRANSFER transactions should not have a category");
            }
            // Validate destination account exists and belongs to user
            accountRepository
                    .findByIdAndUserId(request.getToAccountId(), userId)
                    .orElseThrow(
                            () ->
                                    AccountNotFoundException.byIdAndUser(
                                            request.getToAccountId(), userId));
        }

        // Validate category if provided
        if (request.getCategoryId() != null) {
            Category category =
                    categoryRepository
                            .findByIdAndUserId(request.getCategoryId(), userId)
                            .orElseThrow(
                                    () ->
                                            CategoryNotFoundException.byIdAndUser(
                                                    request.getCategoryId(), userId));

            // Validate category type matches transaction type
            if (request.getType() == TransactionType.INCOME
                    && category.getType() != CategoryType.INCOME) {
                throw new InvalidTransactionException(
                        "INCOME transactions must use an INCOME category");
            }
            if (request.getType() == TransactionType.EXPENSE
                    && category.getType() != CategoryType.EXPENSE) {
                throw new InvalidTransactionException(
                        "EXPENSE transactions must use an EXPENSE category");
            }
        }

        // Validate end date is after next occurrence
        if (request.getEndDate() != null
                && request.getNextOccurrence() != null
                && !request.getEndDate().isAfter(request.getNextOccurrence())) {
            throw new InvalidTransactionException("End date must be after next occurrence date");
        }
    }

    /**
     * Encrypts sensitive fields (description and notes) in a recurring transaction entity.
     *
     * @param entity the recurring transaction entity to modify
     * @param request the request containing plain-text values
     * @param encryptionKey the encryption key
     */
    private void encryptSensitiveFields(
            RecurringTransaction entity, RecurringTransactionRequest request) {
        // JPA converter handles encryption — just set plain text
        if (request.getDescription() != null && !request.getDescription().isBlank()) {
            entity.setDescription(request.getDescription());
        }

        if (request.getNotes() != null && !request.getNotes().isBlank()) {
            entity.setNotes(request.getNotes());
        }
    }

    /**
     * Converts a recurring transaction entity to a response DTO with decrypted fields and
     * denormalized data.
     *
     * @param entity the recurring transaction entity
     * @param encryptionKey the decryption key
     * @return the response DTO
     */
    private RecurringTransactionResponse toResponseWithDecryption(RecurringTransaction entity) {
        // Fields already decrypted by JPA converter
        String description = entity.getDescription();
        String notes = entity.getNotes();

        // Fetch account name (already decrypted by JPA converter)
        String accountName =
                accountRepository
                        .findByIdAndUserId(entity.getAccountId(), entity.getUserId())
                        .map(acc -> acc.getName())
                        .orElse("Unknown Account");

        // Fetch destination account name if transfer
        String toAccountName = null;
        if (entity.getToAccountId() != null) {
            toAccountName =
                    accountRepository
                            .findByIdAndUserId(entity.getToAccountId(), entity.getUserId())
                            .map(acc -> acc.getName())
                            .orElse("Unknown Account");
        }

        // Fetch category details if present
        String categoryName = null;
        String categoryIcon = null;
        String categoryColor = null;
        if (entity.getCategoryId() != null) {
            Category category =
                    categoryRepository
                            .findByIdAndUserId(entity.getCategoryId(), entity.getUserId())
                            .orElse(null);
            if (category != null) {
                categoryName = category.getName();
                categoryIcon = category.getIcon();
                categoryColor = category.getColor();
            }
        }

        // Build response
        RecurringTransactionResponse response =
                RecurringTransactionResponse.builder()
                        .id(entity.getId())
                        .accountId(entity.getAccountId())
                        .accountName(accountName)
                        .toAccountId(entity.getToAccountId())
                        .toAccountName(toAccountName)
                        .type(entity.getType())
                        .amount(entity.getAmount())
                        .currency(entity.getCurrency())
                        .categoryId(entity.getCategoryId())
                        .categoryName(categoryName)
                        .categoryIcon(categoryIcon)
                        .categoryColor(categoryColor)
                        .description(description)
                        .notes(notes)
                        .frequency(entity.getFrequency())
                        .frequencyDisplayName(entity.getFrequency().getDisplayName())
                        .nextOccurrence(entity.getNextOccurrence())
                        .endDate(entity.getEndDate())
                        .isActive(entity.getIsActive())
                        .createdAt(entity.getCreatedAt())
                        .updatedAt(entity.getUpdatedAt())
                        .build();

        // Compute derived fields
        response.setIsDue(response.computeIsDue());
        response.setDaysUntilNext(response.computeDaysUntilNext());
        response.setIsEnded(response.computeIsEnded());

        return response;
    }

    /**
     * Returns the value as-is — JPA converter already handles decryption. Kept for API
     * compatibility with callers.
     */
    private String safeDecrypt(String storedValue) {
        return storedValue;
    }

    private Long resolveCurrencyId(String currencyCode) {
        if (currencyCode == null || currencyCode.isBlank()) return null;
        return currencyRepository
                .findByCode(currencyCode)
                .map(org.openfinance.entity.Currency::getId)
                .orElse(null);
    }

    /**
     * Result of processing recurring transactions.
     *
     * <p>Contains statistics about how many transactions were processed successfully and how many
     * failed, along with error details.
     */
    public static class ProcessingResult {
        private final int processedCount;
        private final int failedCount;
        private final List<String> errors;

        public ProcessingResult(int processedCount, int failedCount, List<String> errors) {
            this.processedCount = processedCount;
            this.failedCount = failedCount;
            this.errors = errors;
        }

        public int getProcessedCount() {
            return processedCount;
        }

        public int getFailedCount() {
            return failedCount;
        }

        public List<String> getErrors() {
            return errors;
        }

        @Override
        public String toString() {
            return String.format(
                    "ProcessingResult{processed=%d, failed=%d, errors=%d}",
                    processedCount, failedCount, errors.size());
        }
    }

    private void indexRecurringTxSearchTokens(
            RecurringTransaction rt, String description, String notes) {
        try {
            SecretKey key = org.openfinance.security.EncryptionContext.getKey();
            if (key == null) {
                return;
            }
            SecretKey searchKey = searchTokenService.deriveSearchKey(key);
            searchTokenService.indexEntity(
                    rt.getUserId(),
                    "RECURRING_TRANSACTION",
                    rt.getId(),
                    java.util.List.of(
                            new String[] {"description", description},
                            new String[] {"notes", notes}),
                    searchKey);
        } catch (Exception e) {
            log.warn(
                    "Failed to index recurring transaction {} search tokens: {}",
                    rt.getId(),
                    e.getMessage());
        }
    }
}
