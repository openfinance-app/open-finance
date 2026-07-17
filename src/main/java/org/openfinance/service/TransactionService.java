package org.openfinance.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.Base64;
import java.util.List;
import java.util.stream.Collectors;
import javax.crypto.SecretKey;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.openfinance.dto.TransactionRequest;
import org.openfinance.dto.TransactionResponse;
import org.openfinance.dto.TransactionSplitResponse;
import org.openfinance.dto.TransferUpdateRequest;
import org.openfinance.entity.Account;
import org.openfinance.entity.Category;
import org.openfinance.entity.CategoryType;
import org.openfinance.entity.Transaction;
import org.openfinance.entity.TransactionType;
import org.openfinance.entity.User;
import org.openfinance.exception.AccountNotFoundException;
import org.openfinance.exception.CategoryNotFoundException;
import org.openfinance.exception.InvalidTransactionException;
import org.openfinance.exception.TransactionNotFoundException;
import org.openfinance.mapper.TransactionMapper;
import org.openfinance.repository.AccountRepository;
import org.openfinance.repository.CategoryRepository;
import org.openfinance.repository.CurrencyRepository;
import org.openfinance.repository.NetWorthRepository;
import org.openfinance.repository.PayeeRepository;
import org.openfinance.repository.TransactionRepository;
import org.openfinance.repository.UserRepository;
import org.openfinance.security.EncryptionService;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Caching;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Service layer for managing financial transactions.
 *
 * <p>This service handles business logic for transaction CRUD operations, including:
 *
 * <ul>
 *   <li>Creating new transactions with encrypted sensitive fields
 *   <li>Updating existing transactions
 *   <li>Soft-deleting transactions (setting isDeleted = true)
 *   <li>Retrieving transactions with decrypted data
 *   <li>Managing transfer transactions between accounts
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
 * </ul>
 *
 * <p>Requirement REQ-2.4.1: Transaction Management - CRUD operations for financial transactions
 *
 * <p>Requirement REQ-2.4.1.1: Create transactions with validation
 *
 * <p>Requirement REQ-2.4.1.2: Update transactions
 *
 * <p>Requirement REQ-2.4.1.3: Soft delete transactions
 *
 * <p>Requirement REQ-2.18: Data encryption at rest for sensitive fields
 *
 * <p>Requirement REQ-3.2: Authorization - Users can only access their own transactions
 *
 * @see org.openfinance.entity.Transaction
 * @see org.openfinance.dto.TransactionRequest
 * @see org.openfinance.dto.TransactionResponse
 */
@Service
@Transactional
@RequiredArgsConstructor
@Slf4j
public class TransactionService {

    private final TransactionRepository transactionRepository;
    private final AccountRepository accountRepository;
    private final CategoryRepository categoryRepository;
    private final PayeeRepository payeeRepository;
    private final CurrencyRepository currencyRepository;
    private final TransactionMapper transactionMapper;
    private final EncryptionService encryptionService;
    private final BudgetAlertService budgetAlertService;
    private final TransactionSplitService transactionSplitService;
    private final UserRepository userRepository;
    private final ExchangeRateService exchangeRateService;
    private final JdbcTemplate jdbcTemplate;
    private final MessageSource messageSource;
    private final NetWorthRepository netWorthRepository;
    private final OperationHistoryService operationHistoryService;
    private final SearchTokenService searchTokenService;

    /**
     * Creates a new transaction for the specified user.
     *
     * <p>The transaction description and notes are encrypted before storing in the database. The
     * encryption key must be derived from the user's master password.
     *
     * <p>Validates:
     *
     * <ul>
     *   <li>Account ownership
     *   <li>Category type matches transaction type
     *   <li>For TRANSFER: toAccountId is provided and different from accountId
     *   <li>For TRANSFER: categoryId is null
     * </ul>
     *
     * <p>Requirement REQ-2.4.1.1: Create new transaction with encrypted sensitive data
     *
     * @param userId the ID of the user creating the transaction
     * @param request the transaction creation request containing transaction details
     * @param encryptionKey the AES-256 encryption key for sensitive fields
     * @return the created transaction with decrypted data
     * @throws IllegalArgumentException if userId, request, or encryptionKey is null
     * @throws InvalidTransactionException if validation fails
     * @throws AccountNotFoundException if account doesn't exist or doesn't belong to user
     * @throws CategoryNotFoundException if category doesn't exist or doesn't belong to user
     */
    @Caching(
            evict = {
                @CacheEvict(
                        value = {"dashboardSummary", "accountSummaries", "netWorthSummary"},
                        key = "#userId"),
                @CacheEvict(
                        value = {"cashFlow", "spendingByCategory", "cashflowSankey"},
                        allEntries = true),
                @CacheEvict(value = "borrowingCapacity", allEntries = true)
            })
    public TransactionResponse createTransaction(Long userId, TransactionRequest request) {
        if (userId == null) {
            throw new IllegalArgumentException("User ID cannot be null");
        }
        if (request == null) {
            throw new IllegalArgumentException("Transaction request cannot be null");
        }
        log.debug(
                "Creating transaction for user {}: type={}, accountId={}, amount={}",
                userId,
                request.getType(),
                request.getAccountId(),
                request.getAmount());

        // Validate the transaction request
        validateTransactionRequest(userId, request);

        // REQ-SPL-1.5: Validate splits if provided (only for INCOME/EXPENSE)
        transactionSplitService.validateSplits(
                request.getAmount(), request.getType(), request.getSplits());

        // TRANSFER type should use createTransfer() method instead
        if (request.getType() == TransactionType.TRANSFER) {
            throw new InvalidTransactionException(
                    "TRANSFER transactions must be created using createTransfer() method");
        }

        // Auto-fill category from payee if not specified (REQ-CAT-5.1)
        if (request.getCategoryId() == null
                && request.getPayee() != null
                && !request.getPayee().isBlank()) {
            // Name is encrypted — fetch all payees and match in Java
            var payee =
                    payeeRepository.findAll().stream()
                            .filter(
                                    p ->
                                            p.getName() != null
                                                    && p.getName()
                                                            .equalsIgnoreCase(
                                                                    request.getPayee().trim()))
                            .findFirst()
                            .orElse(null);
            if (payee != null && payee.getDefaultCategory() != null) {
                log.info(
                        "Auto-filling category {} from payee {}",
                        payee.getDefaultCategory().getId(),
                        payee.getName());
                request.setCategoryId(payee.getDefaultCategory().getId());
            }
        }

        // Map request to entity
        Transaction transaction = transactionMapper.toEntity(request);
        transaction.setUserId(userId);

        // Resolve or create Payee entity and link
        resolveAndLinkPayee(transaction, userId);

        // Link Currency entity
        resolveAndLinkCurrency(transaction);

        // Encrypt sensitive fields (Requirement 2.18: Encryption at rest)
        encryptSensitiveFields(transaction, request);

        // Save to database
        Transaction savedTransaction = transactionRepository.save(transaction);

        // Index in FTS for full-text search
        indexTransactionInFts(
                savedTransaction.getId(),
                userId,
                request.getDescription(),
                request.getNotes(),
                savedTransaction.getTags(),
                savedTransaction.getPayee());

        // Save split lines if provided (REQ-SPL-2.1)
        if (request.getSplits() != null && !request.getSplits().isEmpty()) {
            transactionSplitService.saveSplits(savedTransaction.getId(), request.getSplits());
        }

        // Update account balance (Requirement 2.2.5: Account balance calculation)
        Account account =
                accountRepository
                        .findByIdAndUserId(request.getAccountId(), userId)
                        .orElseThrow(
                                () ->
                                        AccountNotFoundException.byIdAndUser(
                                                request.getAccountId(), userId));

        // Ensure opening_date <= transaction date so net worth backfill uses this
        // account
        LocalDate txDate = request.getDate();
        if (txDate != null) {
            LocalDate currentOpening = account.getOpeningDate();
            if (currentOpening == null || txDate.isBefore(currentOpening)) {
                account.setOpeningDate(txDate);
                log.info(
                        "Updated opening_date for account {} from {} to {} (new transaction)",
                        account.getId(),
                        currentOpening,
                        txDate);
            }
        }

        if (request.getType() == TransactionType.INCOME) {
            // INCOME: add to account balance
            account.setBalance(account.getBalance().add(request.getAmount()));
        } else if (request.getType() == TransactionType.EXPENSE) {
            // EXPENSE: subtract from account balance
            account.setBalance(account.getBalance().subtract(request.getAmount()));
        }
        accountRepository.save(account);

        log.info(
                "Transaction created successfully: id={}, userId={}, type={}, accountId={}, balance updated",
                savedTransaction.getId(),
                userId,
                savedTransaction.getType(),
                savedTransaction.getAccountId());

        // Transparently invalidate net worth snapshots whose historical balance
        // calculation is affected by this transaction's date.
        invalidateSnapshotsFor(userId, savedTransaction.getDate());

        // Check budget alerts after transaction creation (Requirement REQ-2.9.4: Budget
        // alerts)
        try {
            budgetAlertService.checkBudgetAlertsAfterTransaction(userId);
        } catch (Exception e) {
            log.warn(
                    "Failed to check budget alerts after transaction {}: {}",
                    savedTransaction.getId(),
                    e.getMessage());
            // Don't fail transaction creation if alert checking fails
        }

        // Decrypt and return response with denormalized data
        TransactionResponse txResponse = toResponseWithDecryption(savedTransaction);

        // Record in operation history
        operationHistoryService.record(
                userId,
                org.openfinance.entity.EntityType.TRANSACTION,
                savedTransaction.getId(),
                request.getDescription(),
                org.openfinance.entity.OperationType.CREATE,
                (Object) null,
                null);

        return txResponse;
    }

    /**
     * Creates a transfer transaction between two accounts.
     *
     * <p>A transfer creates two linked transactions:
     *
     * <ul>
     *   <li><strong>Source transaction (EXPENSE):</strong> Money leaving the source account
     *   <li><strong>Destination transaction (INCOME):</strong> Money entering the destination
     *       account
     * </ul>
     *
     * Both transactions share a common {@code transferId} (UUID) to maintain the relationship.
     *
     * <p>The operation is atomic - both transactions are created within a single database
     * transaction. If either fails, both are rolled back.
     *
     * <p>Validates:
     *
     * <ul>
     *   <li>Both accounts exist and belong to the user
     *   <li>Source and destination accounts are different
     *   <li>Amount is positive
     *   <li>No category is specified (transfers are not categorized)
     * </ul>
     *
     * <p>Requirement REQ-2.4.1.4: Create transfer transactions with atomic operations
     *
     * <p>Requirement REQ-2.18: Encrypt description and notes fields
     *
     * @param userId the ID of the user creating the transfer
     * @param request the transfer request (must have type=TRANSFER, accountId, toAccountId, amount)
     * @param encryptionKey the AES-256 encryption key for sensitive fields
     * @return the source transaction response with decrypted data (linked to destination via
     *     transferId)
     * @throws IllegalArgumentException if userId, request, or encryptionKey is null
     * @throws InvalidTransactionException if validation fails (same accounts, category provided,
     *     etc.)
     * @throws AccountNotFoundException if either account doesn't exist or doesn't belong to user
     */
    @Caching(
            evict = {
                @CacheEvict(
                        value = {"dashboardSummary", "accountSummaries", "netWorthSummary"},
                        key = "#userId"),
                @CacheEvict(
                        value = {"cashFlow", "spendingByCategory", "cashflowSankey"},
                        allEntries = true),
                @CacheEvict(value = "borrowingCapacity", allEntries = true)
            })
    @Transactional
    public TransactionResponse createTransfer(Long userId, TransactionRequest request) {
        if (userId == null) {
            throw new IllegalArgumentException("User ID cannot be null");
        }
        if (request == null) {
            throw new IllegalArgumentException("Transaction request cannot be null");
        }
        if (request.getType() != TransactionType.TRANSFER) {
            throw new IllegalArgumentException("Transaction type must be TRANSFER");
        }

        log.debug(
                "Creating transfer for user {}: from account {} to account {}, amount={}",
                userId,
                request.getAccountId(),
                request.getToAccountId(),
                request.getAmount());

        // Validate the transfer request (checks accounts exist, are different, etc.)
        validateTransactionRequest(userId, request);

        // Generate a unique transfer ID to link the two transactions
        String transferId = java.util.UUID.randomUUID().toString();

        // Create source transaction (money leaving source account - EXPENSE)
        Transaction sourceTransaction = transactionMapper.toEntity(request);
        sourceTransaction.setUserId(userId);
        sourceTransaction.setType(TransactionType.EXPENSE);
        sourceTransaction.setTransferId(transferId);
        sourceTransaction.setCategoryId(null); // Transfers are not categorized
        resolveAndLinkPayee(sourceTransaction, userId);
        resolveAndLinkCurrency(sourceTransaction);
        encryptSensitiveFields(sourceTransaction, request);

        // Fetch accounts
        Account sourceAccount =
                accountRepository
                        .findByIdAndUserId(request.getAccountId(), userId)
                        .orElseThrow(
                                () ->
                                        AccountNotFoundException.byIdAndUser(
                                                request.getAccountId(), userId));
        Account destAccount =
                accountRepository
                        .findByIdAndUserId(request.getToAccountId(), userId)
                        .orElseThrow(
                                () ->
                                        AccountNotFoundException.byIdAndUser(
                                                request.getToAccountId(), userId));

        // Handle multi-currency transfer
        String sourceCurrency =
                sourceAccount.getCurrency() != null ? sourceAccount.getCurrency() : "USD";
        String destCurrency = destAccount.getCurrency() != null ? destAccount.getCurrency() : "USD";
        BigDecimal destAmount = request.getAmount();

        if (!sourceCurrency.equalsIgnoreCase(destCurrency)) {
            try {
                destAmount =
                        exchangeRateService.convert(
                                request.getAmount(), sourceCurrency, destCurrency);
                log.info(
                        "Converted transfer amount: {} {} -> {} {}",
                        request.getAmount(),
                        sourceCurrency,
                        destAmount,
                        destCurrency);
            } catch (Exception e) {
                log.warn(
                        "Failed to convert {} {} to {}: {}. Using original amount.",
                        request.getAmount(),
                        sourceCurrency,
                        destCurrency,
                        e.getMessage());
            }
        }

        // Create destination transaction (money entering destination account - INCOME)
        Transaction destinationTransaction = transactionMapper.toEntity(request);
        destinationTransaction.setUserId(userId);
        destinationTransaction.setAccountId(
                request.getToAccountId()); // Swap: destination becomes primary account
        destinationTransaction.setToAccountId(
                request.getAccountId()); // Original source becomes "toAccount"
        destinationTransaction.setType(TransactionType.INCOME);

        // Round to 4 decimal places to avoid ConstraintViolationException on
        // @Digits(integer = 15, fraction = 4)
        BigDecimal roundedDestAmount = destAmount.setScale(4, RoundingMode.HALF_UP);
        destinationTransaction.setAmount(roundedDestAmount);

        destinationTransaction.setCurrency(destCurrency);
        destinationTransaction.setTransferId(transferId);
        destinationTransaction.setCategoryId(null); // Transfers are not categorized
        resolveAndLinkPayee(destinationTransaction, userId);
        resolveAndLinkCurrency(destinationTransaction);

        log.debug("Tracing description in createTransfer: input='{}'", request.getDescription());
        encryptSensitiveFields(destinationTransaction, request);
        log.debug(
                "Tracing description after encryption in createTransfer: encrypted='{}'",
                destinationTransaction.getDescription());

        // Save both transactions atomically
        Transaction savedSourceTransaction = transactionRepository.save(sourceTransaction);
        Transaction savedDestinationTransaction =
                transactionRepository.save(destinationTransaction);

        // Index both transfer transactions in FTS
        indexTransactionInFts(
                savedSourceTransaction.getId(),
                userId,
                request.getDescription(),
                null,
                savedSourceTransaction.getTags(),
                savedSourceTransaction.getPayee());
        indexTransactionInFts(
                savedDestinationTransaction.getId(),
                userId,
                request.getDescription(),
                null,
                savedDestinationTransaction.getTags(),
                savedDestinationTransaction.getPayee());

        // Update account balances (Requirement 2.2.5: Account balance calculation)
        // Ensure opening_date <= transaction date so net worth backfill uses these
        // accounts
        LocalDate transferDate = request.getDate();
        if (transferDate != null) {
            LocalDate srcOpening = sourceAccount.getOpeningDate();
            if (srcOpening == null || transferDate.isBefore(srcOpening)) {
                sourceAccount.setOpeningDate(transferDate);
                log.info(
                        "Updated opening_date for source account {} from {} to {} (transfer)",
                        sourceAccount.getId(),
                        srcOpening,
                        transferDate);
            }
        }
        // Source account: subtract amount (money leaving)
        sourceAccount.setBalance(sourceAccount.getBalance().subtract(request.getAmount()));
        accountRepository.save(sourceAccount);

        if (transferDate != null) {
            LocalDate destOpening = destAccount.getOpeningDate();
            if (destOpening == null || transferDate.isBefore(destOpening)) {
                destAccount.setOpeningDate(transferDate);
                log.info(
                        "Updated opening_date for dest account {} from {} to {} (transfer)",
                        destAccount.getId(),
                        destOpening,
                        transferDate);
            }
        }
        // Destination account: add amount (money entering)
        destAccount.setBalance(destAccount.getBalance().add(destAmount));
        accountRepository.save(destAccount);

        log.info(
                "Transfer created successfully: transferId={}, userId={}, sourceId={}, destId={}, amount={}",
                transferId,
                userId,
                savedSourceTransaction.getId(),
                savedDestinationTransaction.getId(),
                request.getAmount());

        // Invalidate snapshots affected by this transfer's date
        invalidateSnapshotsFor(userId, request.getDate());

        // Return the source transaction response (client can query by transferId to get
        // both)
        return toResponseWithDecryption(savedSourceTransaction);
    }

    /**
     * Updates an existing transfer transaction atomically.
     *
     * <p>This method updates both sides of a transfer (source EXPENSE and destination INCOME) in a
     * single atomic transaction. Both transactions share the same transferId and will be updated
     * with the new values provided in the request.
     *
     * <p>The operation handles:
     *
     * <ul>
     *   <li>Account changes - reverses old account balances and applies to new accounts
     *   <li>Amount changes - updates both sides with the new amount
     *   <li>Date/description updates - applies to both transactions
     *   <li>Reconciliation status - applies to both transactions
     * </ul>
     *
     * <p>Validates:
     *
     * <ul>
     *   <li>Both accounts exist and belong to the user
     *   <li>Source and destination accounts are different
     *   <li>Amount is positive
     *   <li>User owns both transactions in the transfer
     * </ul>
     *
     * <p>Requirement REQ-2.4.1.4: Atomic transfer updates
     *
     * <p>Requirement REQ-2.18: Re-encrypt sensitive fields if changed
     *
     * @param transferId the transfer ID (UUID) linking the two transactions
     * @param userId the ID of the user updating the transfer
     * @param request the transfer update request with new values
     * @param encryptionKey the AES-256 encryption key for sensitive fields
     * @return the updated source transaction response with decrypted data
     * @throws IllegalArgumentException if any parameter is null
     * @throws InvalidTransactionException if validation fails (same accounts, etc.)
     * @throws TransactionNotFoundException if transfer doesn't exist or doesn't belong to user
     */
    @Caching(
            evict = {
                @CacheEvict(
                        value = {"dashboardSummary", "accountSummaries", "netWorthSummary"},
                        key = "#userId"),
                @CacheEvict(
                        value = {"cashFlow", "spendingByCategory", "cashflowSankey"},
                        allEntries = true),
                @CacheEvict(value = "borrowingCapacity", allEntries = true)
            })
    @Transactional
    public TransactionResponse updateTransfer(
            String transferId, Long userId, TransferUpdateRequest request) {
        if (transferId == null || transferId.isBlank()) {
            throw new IllegalArgumentException("Transfer ID cannot be null or blank");
        }
        if (userId == null) {
            throw new IllegalArgumentException("User ID cannot be null");
        }
        if (request == null) {
            throw new IllegalArgumentException("Transfer update request cannot be null");
        }
        log.debug(
                "Updating transfer {} for user {}: from account {} to account {}, amount={}",
                transferId,
                userId,
                request.getFromAccountId(),
                request.getToAccountId(),
                request.getAmount());

        // Find both transactions in the transfer
        List<Transaction> linkedTransactions = transactionRepository.findByTransferId(transferId);

        if (linkedTransactions.size() != 2) {
            throw new TransactionNotFoundException(
                    "Transfer not found or corrupted: expected 2 transactions but found "
                            + linkedTransactions.size());
        }

        // Verify user owns both transactions
        for (Transaction t : linkedTransactions) {
            if (!t.getUserId().equals(userId)) {
                throw new TransactionNotFoundException(
                        "Transfer " + transferId + " not found or access denied");
            }
        }

        // Identify source (EXPENSE) and destination (INCOME) transactions
        Transaction sourceTransaction =
                linkedTransactions.stream()
                        .filter(t -> t.getType() == TransactionType.EXPENSE)
                        .findFirst()
                        .orElseThrow(
                                () ->
                                        new InvalidTransactionException(
                                                "Source transaction (EXPENSE) not found in transfer"));

        Transaction destTransaction =
                linkedTransactions.stream()
                        .filter(t -> t.getType() == TransactionType.INCOME)
                        .findFirst()
                        .orElseThrow(
                                () ->
                                        new InvalidTransactionException(
                                                "Destination transaction (INCOME) not found in transfer"));

        // Store old values for balance reversal
        Long oldSourceAccountId = sourceTransaction.getAccountId();
        Long oldDestAccountId = destTransaction.getAccountId();
        BigDecimal oldAmount = sourceTransaction.getAmount();
        LocalDate oldTransferDate = sourceTransaction.getDate();

        // Validate the update request
        validateTransferUpdateRequest(userId, request);

        // Reverse old balance changes
        // Source account: add back the old amount (reverse the subtraction)
        Account oldSourceAccount =
                accountRepository
                        .findByIdAndUserId(oldSourceAccountId, userId)
                        .orElseThrow(
                                () ->
                                        AccountNotFoundException.byIdAndUser(
                                                oldSourceAccountId, userId));
        oldSourceAccount.setBalance(oldSourceAccount.getBalance().add(oldAmount));
        accountRepository.save(oldSourceAccount);

        // Destination account: subtract the old amount (reverse the addition)
        Account oldDestAccount =
                accountRepository
                        .findByIdAndUserId(oldDestAccountId, userId)
                        .orElseThrow(
                                () ->
                                        AccountNotFoundException.byIdAndUser(
                                                oldDestAccountId, userId));
        oldDestAccount.setBalance(
                oldDestAccount.getBalance().subtract(destTransaction.getAmount()));
        accountRepository.save(oldDestAccount);

        // Update source transaction (EXPENSE)
        sourceTransaction.setAccountId(request.getFromAccountId());
        sourceTransaction.setToAccountId(request.getToAccountId());
        sourceTransaction.setAmount(request.getAmount());
        sourceTransaction.setCurrency(request.getCurrency());
        sourceTransaction.setDate(request.getDate());
        sourceTransaction.setPayee(request.getPayee());
        sourceTransaction.setTags(request.getTags());
        sourceTransaction.setIsReconciled(request.getIsReconciled());
        resolveAndLinkPayee(sourceTransaction, userId);
        resolveAndLinkCurrency(sourceTransaction);
        // Re-encrypt sensitive fields
        encryptTransferSensitiveFields(sourceTransaction, request);

        // Handle multi-currency transfer
        Account newSourceAccount =
                accountRepository
                        .findByIdAndUserId(request.getFromAccountId(), userId)
                        .orElseThrow(
                                () ->
                                        AccountNotFoundException.byIdAndUser(
                                                request.getFromAccountId(), userId));
        Account newDestAccount =
                accountRepository
                        .findByIdAndUserId(request.getToAccountId(), userId)
                        .orElseThrow(
                                () ->
                                        AccountNotFoundException.byIdAndUser(
                                                request.getToAccountId(), userId));

        String sourceCurrency =
                newSourceAccount.getCurrency() != null ? newSourceAccount.getCurrency() : "USD";
        String destCurrency =
                newDestAccount.getCurrency() != null ? newDestAccount.getCurrency() : "USD";
        BigDecimal destAmount = request.getAmount();

        if (!sourceCurrency.equalsIgnoreCase(destCurrency)) {
            try {
                destAmount =
                        exchangeRateService.convert(
                                request.getAmount(), sourceCurrency, destCurrency);
            } catch (Exception e) {
                log.warn(
                        "Failed to convert {} {} to {}: {}. Using original amount.",
                        request.getAmount(),
                        sourceCurrency,
                        destCurrency,
                        e.getMessage());
            }
        }

        // Update destination transaction (INCOME)
        destTransaction.setAccountId(
                request.getToAccountId()); // Destination account becomes primary
        destTransaction.setToAccountId(request.getFromAccountId()); // Source becomes "toAccount"
        destTransaction.setAmount(destAmount);
        destTransaction.setCurrency(destCurrency);
        destTransaction.setDate(request.getDate());
        destTransaction.setPayee(request.getPayee());
        destTransaction.setTags(request.getTags());
        destTransaction.setIsReconciled(request.getIsReconciled());
        resolveAndLinkPayee(destTransaction, userId);
        resolveAndLinkCurrency(destTransaction);
        // Re-encrypt sensitive fields
        encryptTransferSensitiveFields(destTransaction, request);

        // Save both updated transactions
        Transaction savedSourceTransaction = transactionRepository.save(sourceTransaction);
        Transaction savedDestTransaction = transactionRepository.save(destTransaction);

        // Apply new balance changes
        // New source account: subtract the new amount
        newSourceAccount.setBalance(newSourceAccount.getBalance().subtract(request.getAmount()));
        accountRepository.save(newSourceAccount);

        // New destination account: add the new amount
        newDestAccount.setBalance(newDestAccount.getBalance().add(destAmount));
        accountRepository.save(newDestAccount);

        log.info(
                "Transfer updated successfully: transferId={}, userId={}, sourceId={}, destId={}, amount={}",
                transferId,
                userId,
                savedSourceTransaction.getId(),
                savedDestTransaction.getId(),
                request.getAmount());

        // Invalidate snapshots for the wider of old/new transfer dates
        LocalDate newTransferDate = request.getDate();
        invalidateSnapshotsFor(
                userId,
                oldTransferDate != null && oldTransferDate.isAfter(newTransferDate)
                        ? oldTransferDate
                        : newTransferDate);

        // Return the source transaction response
        return toResponseWithDecryption(savedSourceTransaction);
    }

    /**
     * Validates a transfer update request.
     *
     * @param userId the user ID
     * @param request the transfer update request
     * @throws InvalidTransactionException if validation fails
     * @throws AccountNotFoundException if account doesn't exist or doesn't belong to user
     */
    private void validateTransferUpdateRequest(Long userId, TransferUpdateRequest request) {
        // Source and destination must be different
        if (request.getFromAccountId().equals(request.getToAccountId())) {
            throw InvalidTransactionException.sameTransferAccounts(request.getFromAccountId());
        }

        // Validate source account ownership
        Account sourceAccount =
                accountRepository
                        .findByIdAndUserId(request.getFromAccountId(), userId)
                        .orElseThrow(
                                () ->
                                        AccountNotFoundException.byIdAndUser(
                                                request.getFromAccountId(), userId));

        // Validate destination account ownership
        accountRepository
                .findByIdAndUserId(request.getToAccountId(), userId)
                .orElseThrow(
                        () ->
                                AccountNotFoundException.byIdAndUser(
                                        request.getToAccountId(), userId));

        // Validate currency matches source account currency
        if (sourceAccount.getCurrency() != null && !sourceAccount.getCurrency().isBlank()) {
            if (!sourceAccount.getCurrency().equalsIgnoreCase(request.getCurrency())) {
                throw InvalidTransactionException.currencyMismatch(
                        sourceAccount.getCurrency(), request.getCurrency());
            }
        }

        // Validate amount
        if (request.getAmount() == null || request.getAmount().compareTo(BigDecimal.ZERO) <= 0) {
            throw InvalidTransactionException.invalidAmount(String.valueOf(request.getAmount()));
        }
    }

    /**
     * Helper method to encrypt sensitive fields for transfer updates.
     *
     * @param transaction the transaction entity to update
     * @param request the transfer update request with plaintext values
     * @param encryptionKey the encryption key
     */
    private void encryptTransferSensitiveFields(
            Transaction transaction, TransferUpdateRequest request) {
        // JPA converter handles encryption — just set plain text
        if (request.getDescription() != null) {
            if (!request.getDescription().isBlank()) {
                transaction.setDescription(request.getDescription());
            } else {
                // Explicit empty -> clear the field
                transaction.setDescription(null);
            }
        }

        if (request.getNotes() != null) {
            if (!request.getNotes().isBlank()) {
                transaction.setNotes(request.getNotes());
            } else {
                // Explicit empty -> clear the field
                transaction.setNotes(null);
            }
        }
    }

    /**
     * Updates an existing transaction.
     *
     * <p>Only the transaction owner can update the transaction. Sensitive fields are re-encrypted
     * if they have changed.
     *
     * <p>Requirement REQ-2.4.1.2: Update existing transaction
     *
     * <p>Requirement REQ-3.2: Authorization check - verify transaction ownership
     *
     * @param transactionId the ID of the transaction to update
     * @param userId the ID of the user updating the transaction (for authorization)
     * @param request the transaction update request
     * @param encryptionKey the AES-256 encryption key for sensitive fields
     * @return the updated transaction with decrypted data
     * @throws TransactionNotFoundException if transaction not found or doesn't belong to user
     * @throws IllegalArgumentException if any parameter is null
     * @throws InvalidTransactionException if validation fails
     */
    @Caching(
            evict = {
                @CacheEvict(
                        value = {"dashboardSummary", "accountSummaries", "netWorthSummary"},
                        key = "#userId"),
                @CacheEvict(
                        value = {"cashFlow", "spendingByCategory", "cashflowSankey"},
                        allEntries = true),
                @CacheEvict(value = "borrowingCapacity", allEntries = true)
            })
    public TransactionResponse updateTransaction(
            Long transactionId, Long userId, TransactionRequest request) {
        log.debug("Updating transaction {}: userId={}", transactionId, userId);

        if (transactionId == null) {
            throw new IllegalArgumentException("Transaction ID cannot be null");
        }
        if (userId == null) {
            throw new IllegalArgumentException("User ID cannot be null");
        }
        if (request == null) {
            throw new IllegalArgumentException("Transaction request cannot be null");
        }
        // Fetch transaction and verify ownership (Requirement 3.2: Authorization)
        Transaction transaction =
                transactionRepository
                        .findByIdAndUserId(transactionId, userId)
                        .orElseThrow(
                                () ->
                                        TransactionNotFoundException.byIdAndUser(
                                                transactionId, userId));

        // Capture snapshot before update for history
        TransactionResponse beforeSnapshot = toResponseWithDecryption(transaction);

        // Prevent updating transfer transactions (they must be deleted and recreated)
        if (transaction.getTransferId() != null) {
            throw new InvalidTransactionException(
                    "Cannot update transfer transactions individually. "
                            + "Please delete and recreate the transfer.");
        }

        // Store old values for balance adjustment
        BigDecimal oldAmount = transaction.getAmount();
        TransactionType oldType = transaction.getType();
        Long oldAccountId = transaction.getAccountId();
        LocalDate oldDate = transaction.getDate();

        // Validate the transaction request
        validateTransactionRequest(userId, request);

        // REQ-SPL-1.5 / REQ-SPL-2.2: Validate and prepare splits if provided
        transactionSplitService.validateSplits(
                request.getAmount(), request.getType(), request.getSplits());

        // Update fields from request (only non-null fields will be copied)
        transactionMapper.updateEntityFromRequest(request, transaction);

        // Resolve or create Payee entity and link
        resolveAndLinkPayee(transaction, userId);

        // Link Currency entity
        resolveAndLinkCurrency(transaction);

        // Re-encrypt sensitive fields (always re-encrypt the provided plaintext values)
        encryptSensitiveFields(transaction, request);

        // Save changes
        Transaction updatedTransaction = transactionRepository.save(transaction);

        // Update FTS index with new plaintext values
        indexTransactionInFts(
                updatedTransaction.getId(),
                userId,
                request.getDescription(),
                request.getNotes(),
                updatedTransaction.getTags(),
                updatedTransaction.getPayee());

        // Replace splits: always call saveSplits (it handles null/empty as clear)
        // REQ-SPL-2.2: PUT replaces all existing splits with the new list
        transactionSplitService.saveSplits(updatedTransaction.getId(), request.getSplits());

        // Adjust account balance based on changes (Requirement 2.2.5: Account balance
        // calculation)
        // If account changed, reverse old balance and apply new balance
        if (!oldAccountId.equals(request.getAccountId())) {
            // Reverse old account balance
            Account oldAccount =
                    accountRepository
                            .findByIdAndUserId(oldAccountId, userId)
                            .orElseThrow(
                                    () ->
                                            AccountNotFoundException.byIdAndUser(
                                                    oldAccountId, userId));
            if (oldType == TransactionType.INCOME) {
                oldAccount.setBalance(oldAccount.getBalance().subtract(oldAmount));
            } else if (oldType == TransactionType.EXPENSE) {
                oldAccount.setBalance(oldAccount.getBalance().add(oldAmount));
            }
            accountRepository.save(oldAccount);

            // Apply new account balance
            Account newAccount =
                    accountRepository
                            .findByIdAndUserId(request.getAccountId(), userId)
                            .orElseThrow(
                                    () ->
                                            AccountNotFoundException.byIdAndUser(
                                                    request.getAccountId(), userId));
            if (request.getType() == TransactionType.INCOME) {
                newAccount.setBalance(newAccount.getBalance().add(request.getAmount()));
            } else if (request.getType() == TransactionType.EXPENSE) {
                newAccount.setBalance(newAccount.getBalance().subtract(request.getAmount()));
            }
            accountRepository.save(newAccount);
        } else {
            // Same account, but amount or type may have changed
            Account account =
                    accountRepository
                            .findByIdAndUserId(request.getAccountId(), userId)
                            .orElseThrow(
                                    () ->
                                            AccountNotFoundException.byIdAndUser(
                                                    request.getAccountId(), userId));

            // Reverse old transaction effect
            if (oldType == TransactionType.INCOME) {
                account.setBalance(account.getBalance().subtract(oldAmount));
            } else if (oldType == TransactionType.EXPENSE) {
                account.setBalance(account.getBalance().add(oldAmount));
            }

            // Apply new transaction effect
            if (request.getType() == TransactionType.INCOME) {
                account.setBalance(account.getBalance().add(request.getAmount()));
            } else if (request.getType() == TransactionType.EXPENSE) {
                account.setBalance(account.getBalance().subtract(request.getAmount()));
            }

            accountRepository.save(account);
        }

        log.info(
                "Transaction updated successfully: id={}, userId={}, balance adjusted",
                transactionId,
                userId);

        // Invalidate snapshots affected by either the old or new transaction date;
        // the later of the two covers the wider set of affected monthly snapshots.
        LocalDate newDate =
                updatedTransaction.getDate() != null ? updatedTransaction.getDate() : oldDate;
        invalidateSnapshotsFor(
                userId, oldDate != null && oldDate.isAfter(newDate) ? oldDate : newDate);

        // Decrypt and return response with denormalized data
        TransactionResponse updateTxResponse = toResponseWithDecryption(updatedTransaction);

        // Record in operation history
        operationHistoryService.record(
                userId,
                org.openfinance.entity.EntityType.TRANSACTION,
                transactionId,
                updateTxResponse.getDescription(), // available from TransactionResponse
                org.openfinance.entity.OperationType.UPDATE,
                beforeSnapshot,
                null);

        return updateTxResponse;
    }

    /**
     * Soft-deletes a transaction by setting isDeleted = true.
     *
     * <p>Soft deletion preserves historical data while removing the transaction from active views.
     * Only the transaction owner can delete the transaction.
     *
     * <p>Requirement REQ-2.4.1.3: Soft delete transactions
     *
     * <p>Requirement REQ-3.2: Authorization check - verify transaction ownership
     *
     * @param transactionId the ID of the transaction to delete
     * @param userId the ID of the user deleting the transaction (for authorization)
     * @throws TransactionNotFoundException if transaction not found or doesn't belong to user
     * @throws IllegalArgumentException if transactionId or userId is null
     */
    @Caching(
            evict = {
                @CacheEvict(
                        value = {"dashboardSummary", "accountSummaries", "netWorthSummary"},
                        key = "#userId"),
                @CacheEvict(
                        value = {"cashFlow", "spendingByCategory", "cashflowSankey"},
                        allEntries = true),
                @CacheEvict(value = "borrowingCapacity", allEntries = true)
            })
    @Transactional
    public void deleteTransaction(Long transactionId, Long userId) {
        log.debug("Soft-deleting transaction {}: userId={}", transactionId, userId);

        if (transactionId == null) {
            throw new IllegalArgumentException("Transaction ID cannot be null");
        }
        if (userId == null) {
            throw new IllegalArgumentException("User ID cannot be null");
        }

        // Fetch transaction and verify ownership (Requirement 3.2: Authorization)
        Transaction transaction =
                transactionRepository
                        .findByIdAndUserId(transactionId, userId)
                        .orElseThrow(
                                () ->
                                        TransactionNotFoundException.byIdAndUser(
                                                transactionId, userId));

        // Handle transfer transactions (delete both sides and reverse both balances)
        if (transaction.getTransferId() != null) {
            // Find both transactions in the transfer
            List<Transaction> linkedTransactions =
                    transactionRepository.findByTransferId(transaction.getTransferId());

            for (Transaction t : linkedTransactions) {
                // Soft delete the transaction
                t.setIsDeleted(true);
                transactionRepository.save(t);

                // Remove from FTS index
                removeTransactionFromFts(t.getId());

                // Reverse balance changes for each transaction
                Account account =
                        accountRepository
                                .findByIdAndUserId(t.getAccountId(), userId)
                                .orElseThrow(
                                        () ->
                                                AccountNotFoundException.byIdAndUser(
                                                        t.getAccountId(), userId));

                if (t.getType() == TransactionType.INCOME) {
                    // INCOME deletion: subtract from balance (reverse the addition)
                    account.setBalance(account.getBalance().subtract(t.getAmount()));
                } else if (t.getType() == TransactionType.EXPENSE) {
                    // EXPENSE deletion: add to balance (reverse the subtraction)
                    account.setBalance(account.getBalance().add(t.getAmount()));
                }
                accountRepository.save(account);
            }

            log.info(
                    "Transfer soft-deleted: transferId={}, deleted {} transactions, balances reversed",
                    transaction.getTransferId(),
                    linkedTransactions.size());
        } else {
            // Single transaction delete (non-transfer)
            // Soft delete
            transaction.setIsDeleted(true);
            transactionRepository.save(transaction);

            // Remove from FTS index
            removeTransactionFromFts(transaction.getId());

            // Reverse balance change
            Account account =
                    accountRepository
                            .findByIdAndUserId(transaction.getAccountId(), userId)
                            .orElseThrow(
                                    () ->
                                            AccountNotFoundException.byIdAndUser(
                                                    transaction.getAccountId(), userId));

            if (transaction.getType() == TransactionType.INCOME) {
                // INCOME deletion: subtract from balance
                account.setBalance(account.getBalance().subtract(transaction.getAmount()));
            } else if (transaction.getType() == TransactionType.EXPENSE) {
                // EXPENSE deletion: add to balance
                account.setBalance(account.getBalance().add(transaction.getAmount()));
            }
            accountRepository.save(account);

            log.info(
                    "Transaction soft-deleted successfully: id={}, userId={}, balance reversed",
                    transactionId,
                    userId);
        }

        // Invalidate net worth snapshots whose historical balance used this transaction
        invalidateSnapshotsFor(userId, transaction.getDate());

        String label = null;
        TransactionResponse snapshot = null;
        if (org.openfinance.security.EncryptionContext.getKey() != null) {
            snapshot = toResponseWithDecryption(transaction);
            label = snapshot.getDescription();
        }

        // Record in operation history
        operationHistoryService.record(
                userId,
                org.openfinance.entity.EntityType.TRANSACTION,
                transactionId,
                label,
                org.openfinance.entity.OperationType.DELETE,
                snapshot,
                null);
    }

    /**
     * Retrieves a single transaction by ID.
     *
     * <p>Only the transaction owner can retrieve the transaction. Sensitive fields are decrypted.
     *
     * <p>Requirement REQ-2.4.1.1: Retrieve transaction details
     *
     * <p>Requirement REQ-3.2: Authorization check - verify transaction ownership
     *
     * @param transactionId the ID of the transaction to retrieve
     * @param userId the ID of the user retrieving the transaction (for authorization)
     * @param encryptionKey the AES-256 encryption key for decrypting sensitive fields
     * @return the transaction with decrypted data and denormalized fields
     * @throws TransactionNotFoundException if transaction not found or doesn't belong to user
     * @throws IllegalArgumentException if any parameter is null
     */
    @Transactional(readOnly = true)
    public TransactionResponse getTransactionById(Long transactionId, Long userId) {
        log.debug("Retrieving transaction {}: userId={}", transactionId, userId);

        if (transactionId == null) {
            throw new IllegalArgumentException("Transaction ID cannot be null");
        }
        if (userId == null) {
            throw new IllegalArgumentException("User ID cannot be null");
        }
        // Fetch transaction and verify ownership (Requirement 3.2: Authorization)
        Transaction transaction =
                transactionRepository
                        .findByIdAndUserId(transactionId, userId)
                        .orElseThrow(
                                () ->
                                        TransactionNotFoundException.byIdAndUser(
                                                transactionId, userId));

        // Decrypt and return response with denormalized data
        return toResponseWithDecryption(transaction);
    }

    /**
     * Retrieves all active transactions for a specific account.
     *
     * <p>Returns only non-deleted transactions where the account is either the source (accountId)
     * or destination (toAccountId) for transfers.
     *
     * <p>Requirement REQ-2.4.1.1: List transactions by account
     *
     * @param userId the ID of the user (for authorization)
     * @param accountId the ID of the account
     * @param encryptionKey the AES-256 encryption key for decrypting sensitive fields
     * @return list of transactions for the account with decrypted data (may be empty)
     * @throws IllegalArgumentException if userId, accountId, or encryptionKey is null
     * @throws AccountNotFoundException if account doesn't exist or doesn't belong to user
     */
    @Transactional(readOnly = true)
    public List<TransactionResponse> getTransactionsByAccount(Long userId, Long accountId) {
        log.debug("Retrieving transactions for account {}: userId={}", accountId, userId);

        if (userId == null) {
            throw new IllegalArgumentException("User ID cannot be null");
        }
        if (accountId == null) {
            throw new IllegalArgumentException("Account ID cannot be null");
        }
        // Verify account ownership
        accountRepository
                .findByIdAndUserId(accountId, userId)
                .orElseThrow(() -> AccountNotFoundException.byIdAndUser(accountId, userId));

        // Fetch transactions for the account
        List<Transaction> transactions =
                transactionRepository.findByUserIdAndAccountId(userId, accountId);

        log.debug("Found {} transactions for account {}", transactions.size(), accountId);

        // Decrypt and map to responses
        return transactions.stream()
                .map(transaction -> toResponseWithDecryption(transaction))
                .collect(Collectors.toList());
    }

    /**
     * Retrieves all active transactions within a date range for a user.
     *
     * <p>Useful for generating reports and analyzing spending over specific periods. Returns only
     * non-deleted transactions.
     *
     * <p>Requirement REQ-2.4.1.1: List transactions by date range
     *
     * @param userId the ID of the user
     * @param startDate the start date (inclusive)
     * @param endDate the end date (inclusive)
     * @param encryptionKey the AES-256 encryption key for decrypting sensitive fields
     * @return list of transactions in date range with decrypted data (may be empty)
     * @throws IllegalArgumentException if any parameter is null
     */
    @Transactional(readOnly = true)
    public List<TransactionResponse> getTransactionsByDateRange(
            Long userId, LocalDate startDate, LocalDate endDate) {
        log.debug(
                "Retrieving transactions for user {} between {} and {}",
                userId,
                startDate,
                endDate);

        if (userId == null) {
            throw new IllegalArgumentException("User ID cannot be null");
        }
        if (startDate == null) {
            throw new IllegalArgumentException("Start date cannot be null");
        }
        if (endDate == null) {
            throw new IllegalArgumentException("End date cannot be null");
        }
        // Fetch transactions in date range
        List<Transaction> transactions =
                transactionRepository.findByUserIdAndDateBetween(userId, startDate, endDate);

        log.debug("Found {} transactions for user {} in date range", transactions.size(), userId);

        // Decrypt and map to responses
        return transactions.stream()
                .map(transaction -> toResponseWithDecryption(transaction))
                .collect(Collectors.toList());
    }

    /**
     * Searches transactions using advanced criteria with pagination support.
     *
     * <p>This method provides flexible search capabilities using dynamic JPA Specifications.
     * Multiple search criteria can be combined (AND logic). If no criteria is provided, returns all
     * user transactions.
     *
     * <p><strong>Supported Search Filters:</strong>
     *
     * <ul>
     *   <li><strong>keyword</strong> - Search in description, notes, payee (case-insensitive,
     *       partial match)
     *   <li><strong>accountId</strong> - Filter by specific account
     *   <li><strong>categoryId</strong> - Filter by specific category
     *   <li><strong>type</strong> - Filter by transaction type (INCOME, EXPENSE, TRANSFER)
     *   <li><strong>dateFrom, dateTo</strong> - Filter by date range (inclusive)
     *   <li><strong>amountMin, amountMax</strong> - Filter by amount range
     *   <li><strong>tags</strong> - Search transactions containing specific tags
     *   <li><strong>isReconciled</strong> - Filter by reconciliation status
     * </ul>
     *
     * <p><strong>Note on Keyword Search:</strong> The keyword search operates on encrypted
     * description/notes/payee fields. For exact matches, it works fine. For partial matches on
     * encrypted data, results may be limited. Consider decrypting all transactions client-side for
     * better full-text search in production applications.
     *
     * <p>Requirement REQ-2.3.5: Advanced transaction search with multiple filters
     *
     * @param userId the ID of the user searching transactions
     * @param criteria the search criteria (all fields optional)
     * @param pageable pagination and sorting parameters (page number, size, sort)
     * @param encryptionKey the AES-256 encryption key for decrypting sensitive fields
     * @return page of transactions matching criteria with decrypted data
     * @throws IllegalArgumentException if userId, criteria, pageable, or encryptionKey is null
     */
    @Transactional(readOnly = true)
    public org.springframework.data.domain.Page<TransactionResponse> searchTransactions(
            Long userId,
            org.openfinance.dto.TransactionSearchCriteria criteria,
            org.springframework.data.domain.Pageable pageable) {

        if (userId == null) {
            throw new IllegalArgumentException("User ID cannot be null");
        }
        if (criteria == null) {
            throw new IllegalArgumentException("Search criteria cannot be null");
        }
        if (pageable == null) {
            throw new IllegalArgumentException("Pageable cannot be null");
        }
        log.debug(
                "Searching transactions for user {}: keyword={}, accountId={}, type={}, dateFrom={}, dateTo={}",
                userId,
                criteria.getKeyword(),
                criteria.getAccountId(),
                criteria.getType(),
                criteria.getDateFrom(),
                criteria.getDateTo());

        List<Long> matchedTransactionIds = null;
        boolean needsInMemoryPreFilter =
                (criteria.getKeyword() != null && !criteria.getKeyword().trim().isEmpty())
                        || criteria.getAmountMin() != null
                        || criteria.getAmountMax() != null;

        if (needsInMemoryPreFilter) {
            // Encrypted fields (description, notes, payee, tags, amount) cannot be
            // filtered at the SQL level. Fetch all non-deleted user transactions via
            // JPA (which decrypts transparently) and filter in Java.
            List<Transaction> allUserTransactions =
                    transactionRepository.findByUserId(userId).stream()
                            .filter(t -> !Boolean.TRUE.equals(t.getIsDeleted()))
                            .toList();

            java.util.stream.Stream<Transaction> stream = allUserTransactions.stream();

            // Keyword filter (description, notes, payee, tags)
            if (criteria.getKeyword() != null && !criteria.getKeyword().trim().isEmpty()) {
                String lowerKeyword = criteria.getKeyword().trim().toLowerCase();
                stream =
                        stream.filter(
                                t -> {
                                    if (t.getDescription() != null
                                            && t.getDescription()
                                                    .toLowerCase()
                                                    .contains(lowerKeyword)) return true;
                                    if (t.getNotes() != null
                                            && t.getNotes().toLowerCase().contains(lowerKeyword))
                                        return true;
                                    if (t.getPayee() != null
                                            && t.getPayee().toLowerCase().contains(lowerKeyword))
                                        return true;
                                    if (t.getTags() != null
                                            && t.getTags().toLowerCase().contains(lowerKeyword))
                                        return true;
                                    return false;
                                });
            }

            // Amount range filter (amount is encrypted, SQL comparison is meaningless)
            if (criteria.getAmountMin() != null) {
                stream =
                        stream.filter(
                                t ->
                                        t.getAmount() != null
                                                && t.getAmount().compareTo(criteria.getAmountMin())
                                                        >= 0);
            }
            if (criteria.getAmountMax() != null) {
                stream =
                        stream.filter(
                                t ->
                                        t.getAmount() != null
                                                && t.getAmount().compareTo(criteria.getAmountMax())
                                                        <= 0);
            }

            matchedTransactionIds =
                    stream.map(org.openfinance.entity.Transaction::getId).limit(1000).toList();

            // If pre-filter criteria matched nothing, return empty page immediately
            if (matchedTransactionIds.isEmpty()) {
                return org.springframework.data.domain.Page.empty(pageable);
            }
        }

        // Ensure deterministic ordering: add id DESC as a secondary sort key when not
        // already specified. This prevents non-deterministic pagination when multiple
        // transactions share the same primary sort value (e.g. same date).
        if (pageable.getSort().getOrderFor("id") == null) {
            org.springframework.data.domain.Sort baseSort =
                    pageable.getSort().isSorted()
                            ? pageable.getSort()
                            : org.springframework.data.domain.Sort.by(
                                    org.springframework.data.domain.Sort.Direction.DESC, "date");
            // Mirror the date sort direction for the id tiebreaker so the ordering
            // is intuitive: DESC date → DESC id (newest id = most recently created first).
            org.springframework.data.domain.Sort.Order dateOrder = baseSort.getOrderFor("date");
            org.springframework.data.domain.Sort.Direction idDir =
                    (dateOrder != null && dateOrder.isAscending())
                            ? org.springframework.data.domain.Sort.Direction.ASC
                            : org.springframework.data.domain.Sort.Direction.DESC;
            pageable =
                    org.springframework.data.domain.PageRequest.of(
                            pageable.getPageNumber(),
                            pageable.getPageSize(),
                            baseSort.and(org.springframework.data.domain.Sort.by(idDir, "id")));
        }

        // Build dynamic specification
        org.springframework.data.jpa.domain.Specification<Transaction> spec =
                org.openfinance.specification.TransactionSpecification.buildSpecification(
                        userId, criteria, matchedTransactionIds);

        // Execute paginated query
        org.springframework.data.domain.Page<Transaction> transactionPage =
                transactionRepository.findAll(spec, pageable);

        log.debug(
                "Found {} transactions (page {}/{})",
                transactionPage.getNumberOfElements(),
                transactionPage.getNumber() + 1,
                transactionPage.getTotalPages());

        // Decrypt and map to responses (preserving pagination metadata)
        return transactionPage.map(transaction -> toResponseWithDecryption(transaction));
    }

    /**
     * Helper method to validate transaction request before saving.
     *
     * <p>Validates:
     *
     * <ul>
     *   <li>Account ownership - user must own the account
     *   <li>Category type matches transaction type (if category provided)
     *   <li>For TRANSFER: toAccountId is provided and different from accountId
     *   <li>For TRANSFER: categoryId should be null
     * </ul>
     *
     * @param userId the user ID
     * @param request the transaction request
     * @throws InvalidTransactionException if validation fails
     * @throws AccountNotFoundException if account doesn't exist or doesn't belong to user
     * @throws CategoryNotFoundException if category doesn't exist or doesn't belong to user
     */
    private void validateTransactionRequest(Long userId, TransactionRequest request) {
        // Validate account ownership
        Account account =
                accountRepository
                        .findByIdAndUserId(request.getAccountId(), userId)
                        .orElseThrow(
                                () ->
                                        AccountNotFoundException.byIdAndUser(
                                                request.getAccountId(), userId));

        // Validate transfer-specific rules
        if (request.getType() == TransactionType.TRANSFER) {
            // TRANSFER must have destination account
            if (request.getToAccountId() == null) {
                throw InvalidTransactionException.transferMissingDestination();
            }

            // TRANSFER must have different source and destination
            if (request.getAccountId().equals(request.getToAccountId())) {
                throw InvalidTransactionException.sameTransferAccounts(request.getAccountId());
            }

            // TRANSFER should not have category
            if (request.getCategoryId() != null) {
                throw InvalidTransactionException.transferWithCategory();
            }

            // Verify destination account ownership
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

            // Category type must match transaction type (INCOME category for INCOME
            // transaction)
            if (request.getType() == TransactionType.INCOME
                    && category.getType() != CategoryType.INCOME) {
                throw InvalidTransactionException.categoryTypeMismatch(
                        category.getType().toString(), request.getType().toString());
            }
            if (request.getType() == TransactionType.EXPENSE
                    && category.getType() != CategoryType.EXPENSE) {
                throw InvalidTransactionException.categoryTypeMismatch(
                        category.getType().toString(), request.getType().toString());
            }
        }

        // Validate amount at service level (defensive - DTO validation should have
        // already enforced this)
        if (request.getAmount() == null || request.getAmount().compareTo(BigDecimal.ZERO) <= 0) {
            throw InvalidTransactionException.invalidAmount(String.valueOf(request.getAmount()));
        }

        // Validate currency matches source account currency. For all transaction types
        // the
        // amount is expressed in the source account's currency (for TRANSFER it's the
        // source).
        String accountCurrency = account.getCurrency();
        if (accountCurrency != null && !accountCurrency.isBlank()) {
            if (!accountCurrency.equalsIgnoreCase(request.getCurrency())) {
                throw InvalidTransactionException.currencyMismatch(
                        accountCurrency, request.getCurrency());
            }
        }
    }

    /**
     * Resolve or create a Payee entity and set payeeId on the transaction. If the transaction's
     * payee string is blank, payeeId is set to null.
     */
    private void resolveAndLinkPayee(Transaction transaction, Long userId) {
        String payeeName = transaction.getPayee();
        if (payeeName == null || payeeName.isBlank()) {
            transaction.setPayeeId(null);
            return;
        }
        String trimmed = payeeName.trim();
        // Name is encrypted — SQL equality on ciphertext won't match.
        // Fetch all payees visible to user and match in Java.
        org.openfinance.entity.Payee existing =
                payeeRepository.findAllByUser(userId).stream()
                        .filter(p -> p.getName() != null && p.getName().equalsIgnoreCase(trimmed))
                        .findFirst()
                        .orElse(null);
        if (existing != null) {
            transaction.setPayeeId(existing.getId());
            return;
        }
        org.openfinance.entity.Payee newPayee =
                org.openfinance.entity.Payee.builder()
                        .name(trimmed)
                        .userId(userId)
                        .isSystem(false)
                        .isActive(true)
                        .build();
        transaction.setPayeeId(payeeRepository.save(newPayee).getId());
    }

    /**
     * Look up the Currency entity by the transaction's currency code and set currencyId. If the
     * currency code is not found in the currencies table, currencyId is set to null.
     */
    private void resolveAndLinkCurrency(Transaction transaction) {
        String code = transaction.getCurrency();
        if (code == null || code.isBlank()) {
            transaction.setCurrencyId(null);
            return;
        }
        transaction.setCurrencyId(
                currencyRepository.findByCode(code).map(c -> c.getId()).orElse(null));
    }

    /**
     * Helper method to encrypt sensitive fields before saving.
     *
     * <p>Encrypts description and notes fields if they are provided.
     *
     * <p>Requirement REQ-2.18: Encryption at rest for sensitive fields
     *
     * @param transaction the transaction entity to update
     * @param request the transaction request with plaintext values
     * @param encryptionKey the encryption key
     */
    private void encryptSensitiveFields(Transaction transaction, TransactionRequest request) {
        log.debug(
                "encryptSensitiveFields: Entering for transaction {} (request.description='{}')",
                transaction.getId(),
                request.getDescription());
        // JPA converter handles encryption — just set plain text
        if (request.getDescription() != null) {
            if (!request.getDescription().isBlank()) {
                transaction.setDescription(request.getDescription());
            } else {
                // Explicit empty -> clear the field
                transaction.setDescription(null);
            }
        }

        if (request.getNotes() != null) {
            if (!request.getNotes().isBlank()) {
                transaction.setNotes(request.getNotes());
            } else {
                // Explicit empty -> clear the field
                transaction.setNotes(null);
            }
        }
    }

    /**
     * Helper method to decrypt sensitive fields and map to response DTO.
     *
     * <p>Converts a Transaction entity to a TransactionResponse DTO with decrypted fields and
     * denormalized data.
     *
     * @param transaction the transaction entity
     * @param encryptionKey the AES-256 encryption key
     * @return the transaction response with decrypted data
     */
    /**
     * Returns the value as-is — JPA converter already handles decryption. Kept for API
     * compatibility.
     */
    private String decryptFieldOrReturnRaw(
            String storedValue, Long transactionId, String fieldName) {
        return storedValue;
    }

    private boolean looksLikePlaintext(String value) {
        if (value == null) {
            return false;
        }

        String trimmed = value.trim();
        if (trimmed.length() < 40) {
            return true;
        }

        try {
            byte[] decoded = Base64.getDecoder().decode(trimmed);
            return decoded.length < 29;
        } catch (IllegalArgumentException e) {
            return true;
        }
    }

    public TransactionResponse toResponseWithDecryption(Transaction transaction) {
        if (transaction == null) {
            return null;
        }

        log.debug("Converting transaction {} to response with decryption", transaction.getId());

        // Fields already decrypted by JPA converter
        String decryptedDescription = null;
        log.debug(
                "toResponseWithDecryption: description in entity is '{}'",
                transaction.getDescription());
        if (transaction.getDescription() != null && !transaction.getDescription().isBlank()) {
            decryptedDescription = transaction.getDescription();
        }
        log.debug("toResponseWithDecryption: description is '{}'", decryptedDescription);

        String decryptedNotes = null;
        if (transaction.getNotes() != null && !transaction.getNotes().isBlank()) {
            decryptedNotes = transaction.getNotes();
        }

        // Create response with basic fields
        TransactionResponse response = transactionMapper.toResponse(transaction);
        response.setDescription(decryptedDescription);
        response.setNotes(decryptedNotes);

        // Populate denormalized account name (already decrypted by JPA converter)
        accountRepository
                .findById(transaction.getAccountId())
                .ifPresent(
                        account -> {
                            response.setAccountName(account.getName());
                        });

        // Populate denormalized destination account name for transfers
        if (transaction.getToAccountId() != null) {
            accountRepository
                    .findById(transaction.getToAccountId())
                    .ifPresent(
                            toAccount -> {
                                response.setToAccountName(toAccount.getName());
                            });
        }

        // Populate denormalized category fields
        if (transaction.getCategoryId() != null) {
            categoryRepository
                    .findById(transaction.getCategoryId())
                    .ifPresent(
                            category -> {
                                // Category name already decrypted by JPA converter
                                String categoryName;
                                if (category.getIsSystem()) {
                                    categoryName =
                                            (category.getNameKey() != null)
                                                    ? messageSource.getMessage(
                                                            category.getNameKey(),
                                                            null,
                                                            category.getName(),
                                                            LocaleContextHolder.getLocale())
                                                    : category.getName();
                                } else {
                                    categoryName = category.getName();
                                }
                                response.setCategoryName(categoryName);
                                response.setCategoryIcon(category.getIcon());
                                response.setCategoryColor(category.getColor());
                            });
        }

        // Populate split details (REQ-SPL-2.3, REQ-SPL-2.4)
        if (transaction.getId() != null) {
            List<TransactionSplitResponse> splits =
                    transactionSplitService.getSplitsForTransaction(transaction.getId());
            response.setHasSplits(!splits.isEmpty());
            response.setSplits(splits.isEmpty() ? null : splits);
        }

        // Populate currency conversion fields (Requirement REQ-9.1)
        populateConversionFields(
                response,
                transaction.getUserId(),
                transaction.getCurrency(),
                transaction.getAmount());

        return response;
    }

    /**
     * Populates currency conversion metadata fields on a TransactionResponse.
     *
     * <p>Fetches the user's base currency from the database, then attempts to convert the
     * transaction {@code amount} to the base currency using {@link ExchangeRateService}. On
     * failure, falls back to the native amount with {@code isConverted=false}.
     *
     * <p>Requirement REQ-9.1: Transaction amounts displayed in user's base currency
     *
     * @param response the response DTO to populate
     * @param userId the transaction owner's user ID
     * @param nativeCurrency the transaction's native currency code (ISO 4217)
     * @param nativeAmount the native transaction amount
     */
    private void populateConversionFields(
            TransactionResponse response,
            Long userId,
            String nativeCurrency,
            BigDecimal nativeAmount) {
        String baseCurrency = resolveBaseCurrency(userId);
        response.setBaseCurrency(baseCurrency);

        boolean needsConversion = nativeCurrency != null && !nativeCurrency.equals(baseCurrency);
        if (!needsConversion || nativeAmount == null) {
            response.setAmountInBaseCurrency(nativeAmount);
            response.setIsConverted(false);
            return;
        }

        try {
            BigDecimal rate =
                    exchangeRateService.getExchangeRate(nativeCurrency, baseCurrency, null);
            BigDecimal converted =
                    exchangeRateService.convert(nativeAmount, nativeCurrency, baseCurrency);

            // Round conversion metadata to 4 decimal places for consistency with entity
            // constraints
            response.setAmountInBaseCurrency(converted.setScale(4, RoundingMode.HALF_UP));
            response.setExchangeRate(rate.setScale(4, RoundingMode.HALF_UP));
            response.setIsConverted(true);
        } catch (Exception e) {
            log.warn(
                    "Currency conversion failed for transaction (user={}, {}->{}) – falling back to native: {}",
                    userId,
                    nativeCurrency,
                    baseCurrency,
                    e.getMessage());
            response.setAmountInBaseCurrency(nativeAmount);
            response.setIsConverted(false);
        }
    }

    /**
     * Resolves the user's base currency, defaulting to "USD" if not configured.
     *
     * @param userId the user ID
     * @return the user's base currency or "USD" as fallback
     */
    private String resolveBaseCurrency(Long userId) {
        if (userId == null) {
            return "USD";
        }
        return userRepository
                .findById(userId)
                .map(User::getBaseCurrency)
                .filter(bc -> bc != null && !bc.isBlank())
                .orElse("USD");
    }

    /**
     * Gets all split lines for a specific transaction, verifying ownership.
     *
     * <p>Requirement REQ-SPL-2.5: Retrieve splits for a transaction via dedicated endpoint
     *
     * @param transactionId the ID of the transaction whose splits to retrieve
     * @param userId the ID of the requesting user (ownership check)
     * @param encryptionKey the AES-256 encryption key for decrypting split descriptions
     * @return list of split responses (may be empty if no splits exist)
     * @throws TransactionNotFoundException if transaction not found or doesn't belong to user
     * @throws IllegalArgumentException if any parameter is null
     */
    @Transactional(readOnly = true)
    public List<TransactionSplitResponse> getSplitsForTransaction(Long transactionId, Long userId) {
        if (transactionId == null) {
            throw new IllegalArgumentException("Transaction ID cannot be null");
        }
        if (userId == null) {
            throw new IllegalArgumentException("User ID cannot be null");
        }
        // Verify ownership (Requirement REQ-3.2: Authorization)
        transactionRepository
                .findByIdAndUserId(transactionId, userId)
                .orElseThrow(() -> TransactionNotFoundException.byIdAndUser(transactionId, userId));

        return transactionSplitService.getSplitsForTransaction(transactionId);
    }

    /**
     * Gets all unique tags used by the user's transactions.
     *
     * <p>Requirement REQ-2.3.7: Tag support for flexible categorization
     *
     * @param userId The ID of the user
     * @return List of tag strings with usage counts, sorted by frequency (most used first)
     * @throws IllegalArgumentException if userId is null
     */
    public List<TagInfo> getAllTagsForUser(Long userId) {
        if (userId == null) {
            throw new IllegalArgumentException("User ID cannot be null");
        }

        log.debug("Fetching all tags for user with id={}", userId);

        // Get all transactions for the user and filter out deleted ones
        List<Transaction> transactions =
                transactionRepository.findByUserId(userId).stream()
                        .filter(t -> !Boolean.TRUE.equals(t.getIsDeleted()))
                        .collect(Collectors.toList());

        // Extract and count tags
        java.util.Map<String, Long> tagCounts =
                transactions.stream()
                        .filter(t -> t.getTags() != null && !t.getTags().isBlank())
                        .flatMap(t -> java.util.Arrays.stream(t.getTags().split(",")))
                        .map(String::trim)
                        .filter(tag -> !tag.isEmpty())
                        .collect(Collectors.groupingBy(tag -> tag, Collectors.counting()));

        // Convert to TagInfo objects and sort by count (descending)
        List<TagInfo> tagInfos =
                tagCounts.entrySet().stream()
                        .map(entry -> new TagInfo(entry.getKey(), entry.getValue()))
                        .sorted((a, b) -> Long.compare(b.count(), a.count()))
                        .collect(Collectors.toList());

        log.debug("Found {} unique tags for user id={}", tagInfos.size(), userId);
        return tagInfos;
    }

    /**
     * Gets the most popular tags for the user.
     *
     * <p>Returns up to the specified limit of tags, sorted by frequency of use.
     *
     * <p>Requirement REQ-2.3.7: Tag autocomplete support
     *
     * @param userId The ID of the user
     * @param limit Maximum number of tags to return
     * @return List of most popular tag strings
     * @throws IllegalArgumentException if userId is null or limit is less than 1
     */
    public List<String> getPopularTags(Long userId, int limit) {
        if (userId == null) {
            throw new IllegalArgumentException("User ID cannot be null");
        }
        if (limit < 1) {
            throw new IllegalArgumentException("Limit must be at least 1");
        }

        log.debug("Fetching top {} popular tags for user id={}", limit, userId);

        return getAllTagsForUser(userId).stream()
                .limit(limit)
                .map(TagInfo::tag)
                .collect(Collectors.toList());
    }

    /**
     * Finds transactions by tag for a specific user.
     *
     * <p>Returns all transactions that contain the specified tag (case-insensitive match).
     *
     * <p>Requirement REQ-2.3.7: Tag-based filtering
     *
     * @param userId The ID of the user
     * @param tag The tag to search for (case-insensitive)
     * @param encryptionKey The encryption key for decrypting transaction fields
     * @return List of transactions containing the tag
     * @throws IllegalArgumentException if userId, tag, or encryptionKey is null
     */
    public List<TransactionResponse> getTransactionsByTag(Long userId, String tag) {
        if (userId == null) {
            throw new IllegalArgumentException("User ID cannot be null");
        }
        if (tag == null || tag.isBlank()) {
            throw new IllegalArgumentException("Tag cannot be null or empty");
        }
        log.debug("Fetching transactions with tag '{}' for user id={}", tag, userId);

        String normalizedTag = tag.trim().toLowerCase();

        // Get all transactions for the user and filter out deleted ones
        List<Transaction> transactions =
                transactionRepository.findByUserId(userId).stream()
                        .filter(t -> !Boolean.TRUE.equals(t.getIsDeleted()))
                        .collect(Collectors.toList());

        // Filter transactions that contain the tag (case-insensitive)
        List<Transaction> matchingTransactions =
                transactions.stream()
                        .filter(t -> t.getTags() != null && !t.getTags().isBlank())
                        .filter(
                                t ->
                                        java.util.Arrays.stream(t.getTags().split(","))
                                                .map(String::trim)
                                                .map(String::toLowerCase)
                                                .anyMatch(normalizedTag::equals))
                        .collect(Collectors.toList());

        log.debug(
                "Found {} transactions with tag '{}' for user id={}",
                matchingTransactions.size(),
                tag,
                userId);

        // Convert to responses with decryption
        return matchingTransactions.stream()
                .map(transaction -> toResponseWithDecryption(transaction))
                .collect(Collectors.toList());
    }

    /**
     * Retrieves all non-deleted transactions for a specific user.
     *
     * <p>Results are ordered by transaction date descending (newest first). Sensitive fields are
     * decrypted using the provided encryption key.
     *
     * @param userId the user ID
     * @param encryptionKey the encryption key for decrypting transaction fields
     * @return list of decrypted transaction responses
     */
    @Transactional(readOnly = true)
    public List<TransactionResponse> getAllTransactions(Long userId) {
        if (userId == null) {
            throw new IllegalArgumentException("User ID cannot be null");
        }
        log.debug("Fetching all transactions for user id={}", userId);

        return transactionRepository.findByUserId(userId).stream()
                .map(transaction -> toResponseWithDecryption(transaction))
                .collect(Collectors.toList());
    }

    /**
     * Inner record class to represent a tag with its usage count.
     *
     * <p>Used for returning tag statistics and popularity information.
     *
     * @param tag The tag string
     * @param count The number of times this tag is used
     */
    public record TagInfo(String tag, Long count) {}

    /**
     * Indexes a transaction's searchable fields as blind-index tokens. Uses HMAC-based tokens via
     * SearchTokenService for privacy-preserving search.
     */
    private void indexTransactionInFts(
            Long transactionId,
            Long userId,
            String description,
            String notes,
            String tags,
            String payee) {
        try {
            SecretKey key = org.openfinance.security.EncryptionContext.getKey();
            if (key == null) {
                log.debug(
                        "No encryption key in context — skipping search token indexing for tx {}",
                        transactionId);
                return;
            }
            SecretKey searchKey = searchTokenService.deriveSearchKey(key);
            searchTokenService.indexEntity(
                    userId,
                    "TRANSACTION",
                    transactionId,
                    java.util.List.of(
                            new String[] {"description", description},
                            new String[] {"notes", notes},
                            new String[] {"tags", tags},
                            new String[] {"payee", payee}),
                    searchKey);
        } catch (Exception e) {
            log.warn(
                    "Failed to index transaction {} search tokens: {}",
                    transactionId,
                    e.getMessage());
        }
    }

    /**
     * Public entry point for indexing a transaction entity into the search token table. Used by
     * services that save transactions directly (e.g. ImportService).
     */
    public void syncTransactionFts(
            Transaction transaction, String plainDescription, String plainNotes) {
        indexTransactionInFts(
                transaction.getId(),
                transaction.getUserId(),
                plainDescription,
                plainNotes,
                transaction.getTags(),
                transaction.getPayee());
    }

    /** Removes a transaction's search tokens. */
    private void removeTransactionFromFts(Long transactionId) {
        try {
            searchTokenService.removeEntity("TRANSACTION", transactionId);
        } catch (Exception e) {
            log.warn(
                    "Failed to remove search tokens for transaction {}: {}",
                    transactionId,
                    e.getMessage());
        }
    }

    /**
     * Deletes all monthly net worth snapshots whose {@code snapshotDate < transactionDate}.
     *
     * <p>Why those snapshots? The backfill algorithm computes a historical balance at targetDate T
     * by taking the current account balance and reversing every transaction whose {@code date > T}.
     * So any transaction at date D is part of the reversal set for every snapshot where {@code
     * snapshotDate < D}. When D changes (create, update, delete), those snapshots are stale and
     * must be recomputed.
     *
     * <p>After deletion the existing auto-backfill logic in {@code
     * DashboardController.getNetWorthHistory()} rebuilds the missing snapshots on the next chart
     * request — completely transparent to the user.
     */
    private void invalidateSnapshotsFor(Long userId, LocalDate transactionDate) {
        if (transactionDate == null) return;
        try {
            int deleted =
                    netWorthRepository.deleteByUserIdAndSnapshotDateBefore(userId, transactionDate);
            if (deleted > 0) {
                log.debug(
                        "Invalidated {} net worth snapshots for user {} (transaction at {})",
                        deleted,
                        userId,
                        transactionDate);
            }
        } catch (Exception e) {
            log.warn(
                    "Could not invalidate net worth snapshots for user {} after transaction at {}: {}",
                    userId,
                    transactionDate,
                    e.getMessage());
        }
    }
}
