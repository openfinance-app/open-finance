package org.openfinance.service;

import java.math.BigDecimal;
import java.util.List;
import java.util.stream.Collectors;
import javax.crypto.SecretKey;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.openfinance.config.EncryptionProperties;
import org.openfinance.dto.AccountRequest;
import org.openfinance.dto.AccountResponse;
import org.openfinance.dto.AccountSearchCriteria;
import org.openfinance.dto.AccountSummaryResponse;
import org.openfinance.entity.Account;
import org.openfinance.entity.Institution;
import org.openfinance.entity.Transaction;
import org.openfinance.entity.TransactionType;
import org.openfinance.entity.User;
import org.openfinance.exception.AccountHasTransactionsException;
import org.openfinance.exception.AccountNotFoundException;
import org.openfinance.exception.InstitutionNotFoundException;
import org.openfinance.mapper.AccountMapper;
import org.openfinance.repository.AccountRepository;
import org.openfinance.repository.InstitutionRepository;
import org.openfinance.repository.InterestRateVariationRepository;
import org.openfinance.repository.TransactionRepository;
import org.openfinance.repository.UserRepository;
import org.openfinance.security.EncryptionService;
import org.openfinance.specification.AccountSpecification;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Caching;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Service layer for managing financial accounts.
 *
 * <p>
 * This service handles business logic for account CRUD operations, including:
 *
 * <ul>
 * <li>Creating new accounts with encrypted sensitive fields
 * <li>Updating existing accounts
 * <li>Soft-deleting accounts (setting isActive = false)
 * <li>Retrieving accounts with decrypted data
 * </ul>
 *
 * <p>
 * <strong>Security Note:</strong> The {@code name} and {@code description}
 * fields are encrypted
 * before storing in the database and decrypted when reading. The encryption key
 * must be provided by
 * the caller (typically from the user's session after authentication).
 *
 * <p>
 * Requirement REQ-2.2: Account Management - CRUD operations for financial
 * accounts
 *
 * <p>
 * Requirement REQ-2.18: Data encryption at rest for sensitive fields
 *
 * <p>
 * Requirement REQ-3.2: Authorization - Users can only access their own accounts
 *
 * @see org.openfinance.entity.Account
 * @see org.openfinance.dto.AccountRequest
 * @see org.openfinance.dto.AccountResponse
 */
@Service
@Transactional
@RequiredArgsConstructor
@Slf4j
public class AccountService {

    private final AccountRepository accountRepository;
    private final AccountMapper accountMapper;
    private final EncryptionService encryptionService;
    private final TransactionRepository transactionRepository;
    private final InstitutionService institutionService;
    private final InstitutionRepository institutionRepository;
    private final InterestRateVariationRepository interestRateVariationRepository;
    private final UserRepository userRepository;
    private final ExchangeRateService exchangeRateService;
    private final org.openfinance.repository.AssetRepository assetRepository;
    private final OperationHistoryService operationHistoryService;
    private final SearchTokenService searchTokenService;
    private final EncryptionProperties encryptionProperties;

    /**
     * Creates a new account for the specified user.
     *
     * <p>
     * The account name and description are encrypted before storing in the
     * database. The
     * encryption key must be derived from the user's master password.
     *
     * <p>
     * Requirement REQ-2.2.1: Create new account with encrypted sensitive data
     *
     * @param userId        the ID of the user creating the account
     * @param request       the account creation request containing account details
     * @param encryptionKey the AES-256 encryption key for sensitive fields
     * @return the created account with decrypted data
     * @throws IllegalArgumentException if userId, request, or encryptionKey is null
     */
    @Caching(evict = {
            @CacheEvict(value = {
                    "dashboardSummary",
                    "accountSummaries",
                    "netWorthSummary",
                    "assetAllocation",
                    "portfolioPerformance"
            }, key = "#userId"),
            @CacheEvict(value = { "cashFlow", "spendingByCategory", "cashflowSankey" }, allEntries = true)
    })
    public AccountResponse createAccount(
            Long userId, AccountRequest request) {
        if (userId == null) {
            throw new IllegalArgumentException("User ID cannot be null");
        }
        if (request == null) {
            throw new IllegalArgumentException("Account request cannot be null");
        }
        if (encryptionProperties.isEnabled()
                && org.openfinance.security.EncryptionContext.getKey() == null) {
            throw new IllegalArgumentException("Encryption key required for account operations");
        }
        log.debug(
                "Creating account for user {}: type={}, currency={}",
                userId,
                request.getType(),
                request.getCurrency());

        // Map request to entity
        Account account = accountMapper.toEntity(request);
        account.setUserId(userId);

        // Default opening date if not provided (Requirement: Opening date is mandatory
        // in DB)
        if (account.getOpeningDate() == null) {
            account.setOpeningDate(java.time.LocalDate.now());
        }

        // Encrypt sensitive fields handled by JPA converter automatically

        // Set institution if provided (REQ-2.6.1.3)
        if (request.getInstitutionId() != null) {
            Institution institution = institutionRepository
                    .findById(request.getInstitutionId())
                    .orElseThrow(
                            () -> new InstitutionNotFoundException(
                                    request.getInstitutionId()));
            account.setInstitution(institution);
        }

        // Save to database
        Account savedAccount = accountRepository.save(account);
        indexAccountSearchTokens(savedAccount, request.getName(), request.getDescription());
        log.info(
                "Account created successfully: id={}, userId={}, type={}",
                savedAccount.getId(),
                userId,
                savedAccount.getType());

        // Handle initial interest rate variation if enabled
        if (Boolean.TRUE.equals(request.getIsInterestEnabled())
                && request.getInterestRate() != null) {
            org.openfinance.entity.InterestRateVariation variation = org.openfinance.entity.InterestRateVariation
                    .builder()
                    .accountId(savedAccount.getId())
                    .rate(request.getInterestRate())
                    .taxRate(
                            request.getTaxRate() != null
                                    ? request.getTaxRate()
                                    : java.math.BigDecimal.ZERO)
                    .validFrom(savedAccount.getOpeningDate())
                    .build();
            interestRateVariationRepository.save(variation);
        }

        // Decrypt and return response
        AccountResponse response = toResponseWithDecryption(savedAccount);

        // Record in operation history
        operationHistoryService.record(
                userId,
                org.openfinance.entity.EntityType.ACCOUNT,
                savedAccount.getId(),
                request.getName(),
                org.openfinance.entity.OperationType.CREATE,
                (Object) null,
                null);

        return response;
    }

    /**
     * Updates an existing account.
     *
     * <p>
     * Only the account owner can update the account. Sensitive fields are
     * re-encrypted if they
     * have changed.
     *
     * <p>
     * Requirement REQ-2.2.3: Update existing account
     *
     * <p>
     * Requirement REQ-3.2: Authorization check - verify account ownership
     *
     * @param accountId     the ID of the account to update
     * @param userId        the ID of the user updating the account (for
     *                      authorization)
     * @param request       the account update request
     * @param encryptionKey the AES-256 encryption key for sensitive fields
     * @return the updated account with decrypted data
     * @throws AccountNotFoundException if account not found or doesn't belong to
     *                                  user
     * @throws IllegalArgumentException if any parameter is null
     */
    @Caching(evict = {
            @CacheEvict(value = {
                    "dashboardSummary",
                    "accountSummaries",
                    "netWorthSummary",
                    "assetAllocation",
                    "portfolioPerformance"
            }, key = "#userId"),
            @CacheEvict(value = { "cashFlow", "spendingByCategory", "cashflowSankey" }, allEntries = true)
    })
    public AccountResponse updateAccount(
            Long accountId, Long userId, AccountRequest request) {
        log.debug("Updating account {}: userId={}", accountId, userId);

        if (accountId == null) {
            throw new IllegalArgumentException("Account ID cannot be null");
        }
        if (userId == null) {
            throw new IllegalArgumentException("User ID cannot be null");
        }
        if (request == null) {
            throw new IllegalArgumentException("Account request cannot be null");
        }
        // Fetch account and verify ownership (Requirement 3.2: Authorization)
        Account account = accountRepository
                .findByIdAndUserId(accountId, userId)
                .orElseThrow(() -> AccountNotFoundException.byIdAndUser(accountId, userId));

        // Capture snapshot before update for history
        AccountResponse beforeSnapshot = toResponseWithDecryption(account);

        // Update fields from request (only non-null fields will be copied)
        accountMapper.updateEntityFromRequest(request, account);

        // Handle institution update (REQ-2.6.1.3)
        if (request.getInstitutionId() != null) {
            Institution institution = institutionRepository
                    .findById(request.getInstitutionId())
                    .orElseThrow(
                            () -> new InstitutionNotFoundException(
                                    request.getInstitutionId()));
            account.setInstitution(institution);
        } else if (request.getInstitutionId() == null) {
            // Allow clearing institution
            account.setInstitution(null);
        }

        // Sensitive fields handled by JPA converter — just set plain text from request
        account.setName(request.getName());

        if (request.getDescription() != null && !request.getDescription().isBlank()) {
            account.setDescription(request.getDescription());
        } else if (request.getDescription() != null) {
            // explicit empty description -> clear stored value
            account.setDescription(null);
        }

        if (request.getAccountNumber() != null && !request.getAccountNumber().isBlank()) {
            account.setAccountNumber(request.getAccountNumber());
        } else if (request.getAccountNumber() != null) {
            // explicit empty account number -> clear stored value
            account.setAccountNumber(null);
        }

        // Save changes
        Account updatedAccount = accountRepository.save(account);
        indexAccountSearchTokens(updatedAccount, request.getName(), request.getDescription());
        log.info("Account updated successfully: id={}, userId={}", accountId, userId);

        // Decrypt and return response
        AccountResponse updateResponse = toResponseWithDecryption(updatedAccount);

        // Record in operation history
        operationHistoryService.record(
                userId,
                org.openfinance.entity.EntityType.ACCOUNT,
                accountId,
                request.getName(),
                org.openfinance.entity.OperationType.UPDATE,
                beforeSnapshot,
                null);

        return updateResponse;
    }

    /**
     * Soft-deletes an account by setting isActive = false.
     *
     * <p>
     * Soft deletion preserves historical data while removing the account from
     * active views. Only
     * the account owner can delete the account.
     *
     * <p>
     * <strong>Business Rule:</strong> Accounts with active transactions cannot be
     * deleted. This
     * prevents orphaned transactions and maintains data integrity. Users must
     * delete or reassign
     * all transactions before deleting the account.
     *
     * <p>
     * Requirement REQ-2.2.4: Soft delete accounts
     *
     * <p>
     * Requirement REQ-3.2: Authorization check - verify account ownership
     *
     * <p>
     * Requirement REQ-2.5: Data integrity - prevent deletion of accounts with
     * active
     * transactions
     *
     * @param accountId     the ID of the account to delete
     * @param userId        the ID of the user deleting the account (for
     *                      authorization)
     * @param encryptionKey the AES-256 encryption key for decrypting the account
     *                      name (for error
     *                      messages)
     * @throws AccountNotFoundException        if account not found or doesn't
     *                                         belong to user
     * @throws AccountHasTransactionsException if account has active transactions
     * @throws IllegalArgumentException        if accountId, userId, or
     *                                         encryptionKey is null
     */
    @Caching(evict = {
            @CacheEvict(value = {
                    "dashboardSummary",
                    "accountSummaries",
                    "netWorthSummary",
                    "assetAllocation",
                    "portfolioPerformance"
            }, key = "#userId"),
            @CacheEvict(value = { "cashFlow", "spendingByCategory", "cashflowSankey" }, allEntries = true)
    })
    public void deleteAccount(Long accountId, Long userId) {
        log.debug("Deleting account id: {} for user: {}", accountId, userId);

        if (accountId == null) {
            throw new IllegalArgumentException("Account ID cannot be null");
        }
        if (userId == null) {
            throw new IllegalArgumentException("User ID cannot be null");
        }

        // Fetch account and verify ownership (Requirement 3.2: Authorization)
        Account account = accountRepository
                .findByIdAndUserId(accountId, userId)
                .orElseThrow(() -> AccountNotFoundException.byIdAndUser(accountId, userId));

        // Check for active transactions (Requirement 2.5: Data integrity)
        Long transactionCount = transactionRepository.countByAccountId(accountId);
        if (transactionCount > 0) {
            // Account name already decrypted by JPA converter
            String nameToLog = account.getName();
            log.warn(
                    "Cannot delete account {} ('{}') - has {} active transactions",
                    accountId,
                    nameToLog,
                    transactionCount);
            throw AccountHasTransactionsException.forAccount(nameToLog, transactionCount);
        }

        // Capture snapshot before delete for history (only if key provided)
        AccountResponse beforeDeleteSnapshot = null;
        String decryptedName = null;
        if (org.openfinance.security.EncryptionContext.getKey() != null) {
            try {
                beforeDeleteSnapshot = toResponseWithDecryption(account);
                decryptedName = beforeDeleteSnapshot.getName();
            } catch (Exception e) {
                log.warn("Failed to capture snapshot for history: {}", e.getMessage());
            }
        }

        // Soft delete
        account.setIsActive(false);
        accountRepository.save(account);
        try {
            searchTokenService.removeEntity("ACCOUNT", accountId);
        } catch (Exception e) {
            log.warn("Failed to remove account {} search tokens: {}", accountId, e.getMessage());
        }

        log.info("Account soft-deleted successfully: id={}, userId={}", accountId, userId);

        // Record in operation history
        operationHistoryService.record(
                userId,
                org.openfinance.entity.EntityType.ACCOUNT,
                accountId,
                decryptedName != null ? decryptedName : "Account " + accountId,
                org.openfinance.entity.OperationType.DELETE,
                beforeDeleteSnapshot,
                null);
    }

    /**
     * Closes an account by setting isActive = false.
     *
     * <p>
     * Closing an account is a soft delete that preserves all historical data
     * including
     * transactions. The account will no longer appear in active views but can be
     * reopened later if
     * needed.
     *
     * <p>
     * Unlike {@link #deleteAccount(Long, Long)}, this method allows
     * closing accounts
     * that have transactions associated with them.
     *
     * <p>
     * Requirement: Close account functionality - Toggle Active/Close state
     *
     * <p>
     * Requirement REQ-3.2: Authorization check - verify account ownership
     *
     * @param accountId the ID of the account to close
     * @param userId    the ID of the user closing the account (for authorization)
     * @throws AccountNotFoundException if account not found or doesn't belong to
     *                                  user
     * @throws IllegalArgumentException if accountId or userId is null
     */
    @Caching(evict = {
            @CacheEvict(value = {
                    "dashboardSummary",
                    "accountSummaries",
                    "netWorthSummary",
                    "assetAllocation",
                    "portfolioPerformance"
            }, key = "#userId"),
            @CacheEvict(value = { "cashFlow", "spendingByCategory", "cashflowSankey" }, allEntries = true)
    })
    public void closeAccount(Long accountId, Long userId) {
        log.debug("Closing account {}: userId={}", accountId, userId);

        if (accountId == null) {
            throw new IllegalArgumentException("Account ID cannot be null");
        }
        if (userId == null) {
            throw new IllegalArgumentException("User ID cannot be null");
        }

        // Fetch account and verify ownership
        Account account = accountRepository
                .findByIdAndUserId(accountId, userId)
                .orElseThrow(() -> AccountNotFoundException.byIdAndUser(accountId, userId));

        // Check if already closed
        if (!account.getIsActive()) {
            log.warn("Account {} is already closed", accountId);
            return; // Already closed, no action needed
        }

        // Close account (soft delete)
        account.setIsActive(false);
        accountRepository.save(account);

        log.info("Account closed successfully: id={}, userId={}", accountId, userId);
    }

    /**
     * Reopens a closed account by setting isActive = true.
     *
     * <p>
     * Reopening an account makes it active again. All historical data including
     * transactions is
     * preserved and the account will appear in active views.
     *
     * <p>
     * Requirement: Close account functionality - Toggle Active/Close state
     *
     * <p>
     * Requirement REQ-3.2: Authorization check - verify account ownership
     *
     * @param accountId the ID of the account to reopen
     * @param userId    the ID of the user reopening the account (for authorization)
     * @throws AccountNotFoundException if account not found or doesn't belong to
     *                                  user
     * @throws IllegalArgumentException if accountId or userId is null
     */
    @Caching(evict = {
            @CacheEvict(value = {
                    "dashboardSummary",
                    "accountSummaries",
                    "netWorthSummary",
                    "assetAllocation",
                    "portfolioPerformance"
            }, key = "#userId"),
            @CacheEvict(value = { "cashFlow", "spendingByCategory", "cashflowSankey" }, allEntries = true)
    })
    public void reopenAccount(Long accountId, Long userId) {
        log.debug("Reopening account {}: userId={}", accountId, userId);

        if (accountId == null) {
            throw new IllegalArgumentException("Account ID cannot be null");
        }
        if (userId == null) {
            throw new IllegalArgumentException("User ID cannot be null");
        }

        // Fetch account and verify ownership
        Account account = accountRepository
                .findByIdAndUserId(accountId, userId)
                .orElseThrow(() -> AccountNotFoundException.byIdAndUser(accountId, userId));

        // Check if already active
        if (account.getIsActive()) {
            log.warn("Account {} is already active", accountId);
            return; // Already active, no action needed
        }

        // Reopen account
        account.setIsActive(true);
        accountRepository.save(account);

        log.info("Account reopened successfully: id={}, userId={}", accountId, userId);
    }

    /**
     * Permanently deletes an account along with all associated transactions.
     *
     * <p>
     * <strong>WARNING:</strong> This is a destructive operation that cannot be
     * undone. All
     * transactions associated with this account will be permanently deleted.
     * Historical data will
     * be lost.
     *
     * <p>
     * This method first deletes all transactions associated with the account, then
     * deletes the
     * account itself from the database.
     *
     * <p>
     * Requirement: Delete account functionality - Permanently delete accounts
     *
     * <p>
     * Requirement REQ-3.2: Authorization check - verify account ownership
     *
     * @param accountId     the ID of the account to permanently delete
     * @param userId        the ID of the user deleting the account (for
     *                      authorization)
     * @param encryptionKey the AES-256 encryption key (required for authorization
     *                      check)
     * @throws AccountNotFoundException if account not found or doesn't belong to
     *                                  user
     * @throws IllegalArgumentException if accountId, userId, or encryptionKey is
     *                                  null
     */
    @Caching(evict = {
            @CacheEvict(value = {
                    "dashboardSummary",
                    "accountSummaries",
                    "netWorthSummary",
                    "assetAllocation",
                    "portfolioPerformance"
            }, key = "#userId"),
            @CacheEvict(value = { "cashFlow", "spendingByCategory", "cashflowSankey" }, allEntries = true)
    })
    public void permanentDeleteAccount(Long accountId, Long userId) {
        log.warn(
                "Permanently deleting account {} and all its transactions: userId={}",
                accountId,
                userId);

        if (accountId == null) {
            throw new IllegalArgumentException("Account ID cannot be null");
        }
        if (userId == null) {
            throw new IllegalArgumentException("User ID cannot be null");
        }
        // Fetch account and verify ownership
        Account account = accountRepository
                .findByIdAndUserId(accountId, userId)
                .orElseThrow(() -> AccountNotFoundException.byIdAndUser(accountId, userId));

        // Delete all transactions associated with this account
        // First, get all transactions for this account
        List<org.openfinance.entity.Transaction> transactions = transactionRepository.findByAccountId(accountId);
        if (!transactions.isEmpty()) {
            log.info(
                    "Deleting {} transactions associated with account {}",
                    transactions.size(),
                    accountId);
            transactionRepository.deleteAll(transactions);
        }

        // Also check for transactions where this account is the destination
        // (toAccountId)
        List<org.openfinance.entity.Transaction> toAccountTransactions = transactionRepository
                .findByToAccountId(accountId);
        if (!toAccountTransactions.isEmpty()) {
            // Filter to only those that are not already in the first list
            List<org.openfinance.entity.Transaction> additionalTransactions = toAccountTransactions.stream()
                    .filter(
                            t -> transactions.stream()
                                    .noneMatch(tx -> tx.getId().equals(t.getId())))
                    .toList();
            if (!additionalTransactions.isEmpty()) {
                log.info(
                        "Deleting {} additional transactions where account is destination",
                        additionalTransactions.size());
                transactionRepository.deleteAll(additionalTransactions);
            }
        }

        // Delete the account itself (hard delete)
        accountRepository.delete(account);

        log.warn(
                "Account permanently deleted successfully: id={}, userId={}, transactionsDeleted={}",
                accountId,
                userId,
                transactions.size() + toAccountTransactions.size());
    }

    /**
     * Retrieves a single account by ID.
     *
     * <p>
     * Only the account owner can retrieve the account. Sensitive fields are
     * decrypted.
     *
     * <p>
     * Requirement REQ-2.2.1: Retrieve account details
     *
     * <p>
     * Requirement REQ-3.2: Authorization check - verify account ownership
     *
     * @param accountId     the ID of the account to retrieve
     * @param userId        the ID of the user retrieving the account (for
     *                      authorization)
     * @param encryptionKey the AES-256 encryption key for decrypting sensitive
     *                      fields
     * @return the account with decrypted data
     * @throws AccountNotFoundException if account not found or doesn't belong to
     *                                  user
     * @throws IllegalArgumentException if any parameter is null
     */
    @Transactional(readOnly = true)
    public AccountResponse getAccountById(Long accountId, Long userId) {
        log.debug("Retrieving account {}: userId={}", accountId, userId);

        if (accountId == null) {
            throw new IllegalArgumentException("Account ID cannot be null");
        }
        if (userId == null) {
            throw new IllegalArgumentException("User ID cannot be null");
        }
        // Fetch account and verify ownership (Requirement 3.2: Authorization)
        Account account = accountRepository
                .findByIdAndUserId(accountId, userId)
                .orElseThrow(() -> AccountNotFoundException.byIdAndUser(accountId, userId));

        // Decrypt and return response
        return toResponseWithDecryption(account);
    }

    /**
     * Retrieves all active accounts for a user.
     *
     * <p>
     * Returns only active accounts (isActive = true). Sensitive fields are
     * decrypted.
     *
     * <p>
     * Requirement REQ-2.2.1: List all user accounts
     *
     * @param userId        the ID of the user
     * @param encryptionKey the AES-256 encryption key for decrypting sensitive
     *                      fields
     * @return list of active accounts with decrypted data (may be empty)
     * @throws IllegalArgumentException if userId or encryptionKey is null
     */
    @Transactional(readOnly = true)
    public List<AccountResponse> getAccountsByUserId(Long userId) {
        log.debug("Retrieving all active accounts for user {}", userId);

        if (userId == null) {
            throw new IllegalArgumentException("User ID cannot be null");
        }
        // Fetch active accounts only
        List<Account> accounts = accountRepository.findByUserIdAndIsActive(userId, true);

        log.debug("Found {} active accounts for user {}", accounts.size(), userId);

        // Decrypt and map to responses
        return accounts.stream()
                .map(account -> toResponseWithDecryption(account))
                .collect(Collectors.toList());
    }

    /**
     * Retrieves accounts for a user with optional filtering by active status.
     *
     * <p>
     * Supports filtering by:
     *
     * <ul>
     * <li>All accounts (isActive = null)
     * <li>Active accounts only (isActive = true)
     * <li>Closed/inactive accounts only (isActive = false)
     * </ul>
     *
     * <p>
     * Requirement REQ-2.2.1: List user accounts with filtering
     *
     * @param userId        the ID of the user
     * @param isActive      filter by active status (null for all accounts)
     * @param encryptionKey the AES-256 encryption key for decrypting sensitive
     *                      fields
     * @return list of accounts matching the filter with decrypted data (may be
     *         empty)
     * @throws IllegalArgumentException if userId or encryptionKey is null
     */
    @Transactional(readOnly = true)
    public List<AccountResponse> getAccountsByUserIdWithFilter(
            Long userId, Boolean isActive) {
        log.debug("Retrieving accounts for user {} with filter: isActive={}", userId, isActive);

        if (userId == null) {
            throw new IllegalArgumentException("User ID cannot be null");
        }
        // Fetch accounts based on filter
        List<Account> accounts;
        if (isActive == null) {
            // Fetch all accounts (both active and inactive)
            accounts = accountRepository.findByUserId(userId);
        } else {
            // Fetch accounts filtered by active status
            accounts = accountRepository.findByUserIdAndIsActive(userId, isActive);
        }

        log.debug(
                "Found {} accounts for user {} with filter isActive={}",
                accounts.size(),
                userId,
                isActive);

        // Decrypt and map to responses
        return accounts.stream()
                .map(account -> toResponseWithDecryption(account))
                .collect(Collectors.toList());
    }

    /**
     * Searches accounts with filters and pagination.
     *
     * <p>
     * This method supports dynamic filtering and sorting through the search
     * criteria. All
     * filtering is done at the database level for efficiency.
     *
     * <p>
     * <strong>Supported Filters:</strong>
     *
     * <ul>
     * <li>keyword - Search in account name (case-insensitive)
     * <li>type - Filter by account type
     * <li>currency - Filter by currency code
     * <li>isActive - Filter by active status
     * <li>balanceMin - Filter by minimum balance
     * <li>balanceMax - Filter by maximum balance
     * <li>institution - Filter by institution name
     * </ul>
     *
     * @param userId        the ID of the user
     * @param criteria      the search criteria (all fields optional)
     * @param pageable      pagination and sorting parameters (page number, size,
     *                      sort)
     * @param encryptionKey the AES-256 encryption key for decrypting sensitive
     *                      fields
     * @return page of accounts matching criteria with decrypted data
     * @throws IllegalArgumentException if userId, criteria, pageable, or
     *                                  encryptionKey is null
     */
    @Transactional(readOnly = true)
    public Page<AccountResponse> searchAccounts(
            Long userId,
            AccountSearchCriteria criteria,
            Pageable pageable) {

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
                "Searching accounts for user {}: keyword={}, type={}, currency={}, isActive={}",
                userId,
                criteria.getKeyword(),
                criteria.getType(),
                criteria.getCurrency(),
                criteria.getIsActive());

        // Build dynamic specification
        Specification<Account> spec = AccountSpecification.buildSpecification(userId, criteria);

        // Execute query
        if (criteria.getKeyword() != null && !criteria.getKeyword().trim().isEmpty()) {
            // Keyword search must be done in-memory because the name is encrypted in DB
            List<Account> allAccounts = accountRepository.findAll(spec, pageable.getSort());
            String searchStr = criteria.getKeyword().toLowerCase();

            List<AccountResponse> filteredList = allAccounts.stream()
                    .map(account -> toResponseWithDecryption(account))
                    .filter(
                            res -> (res.getName() != null
                                    && res.getName()
                                            .toLowerCase()
                                            .contains(searchStr))
                                    || (res.getInstitution() != null
                                            && res.getInstitution().getName() != null
                                            && res.getInstitution()
                                                    .getName()
                                                    .toLowerCase()
                                                    .contains(searchStr))
                                    || (res.getAccountNumber() != null
                                            && res.getAccountNumber()
                                                    .toLowerCase()
                                                    .contains(searchStr)))
                    .collect(Collectors.toList());

            int start = (int) pageable.getOffset();
            int end = Math.min((start + pageable.getPageSize()), filteredList.size());
            List<AccountResponse> pageContent = start <= end ? filteredList.subList(start, end) : List.of();

            return new org.springframework.data.domain.PageImpl<>(
                    pageContent, pageable, filteredList.size());
        }

        // If no keyword search, use normal pagination
        Page<Account> accountPage = accountRepository.findAll(spec, pageable);

        log.debug(
                "Found {} accounts (page {}/{})",
                accountPage.getNumberOfElements(),
                accountPage.getNumber() + 1,
                accountPage.getTotalPages());

        // Decrypt and map to responses (preserving pagination metadata)
        return accountPage.map(account -> toResponseWithDecryption(account));
    }

    /**
     * Retrieves the current balance of an account.
     *
     * <p>
     * Requirement REQ-2.2.5: Get account balance
     *
     * @param accountId the ID of the account
     * @param userId    the ID of the user (for authorization)
     * @return the current balance
     * @throws AccountNotFoundException if account not found or doesn't belong to
     *                                  user
     * @throws IllegalArgumentException if accountId or userId is null
     */
    @Transactional(readOnly = true)
    public java.math.BigDecimal getAccountBalance(Long accountId, Long userId) {
        log.debug("Retrieving balance for account {}: userId={}", accountId, userId);

        if (accountId == null) {
            throw new IllegalArgumentException("Account ID cannot be null");
        }
        if (userId == null) {
            throw new IllegalArgumentException("User ID cannot be null");
        }

        // Fetch account and verify ownership
        Account account = accountRepository
                .findByIdAndUserId(accountId, userId)
                .orElseThrow(() -> AccountNotFoundException.byIdAndUser(accountId, userId));

        return account.getBalance();
    }

    /**
     * Recalculates the account balance based on its opening balance and all
     * non-deleted
     * transactions.
     *
     * <p>
     * Sums all transaction amounts (income - expense) and adds to the
     * openingBalance. This is
     * used to ensure the stored balance is in sync with transactions, particularly
     * after bulk
     * operations like imports.
     *
     * <p>
     * Requirement REQ-2.2.5: Account balance calculation
     *
     * @param accountId the account ID
     * @param userId    the user ID (for authorization)
     * @return the updated account response
     * @throws AccountNotFoundException if account not found or doesn't belong to
     *                                  user
     * @throws IllegalArgumentException if any parameter is null
     */
    @Caching(evict = {
            @CacheEvict(value = {
                    "dashboardSummary",
                    "accountSummaries",
                    "netWorthSummary",
                    "assetAllocation",
                    "portfolioPerformance"
            }, key = "#userId"),
            @CacheEvict(value = { "cashFlow", "spendingByCategory", "cashflowSankey" }, allEntries = true)
    })
    public AccountResponse recalculateBalance(Long accountId, Long userId) {
        log.info("Recalculating balance for account {}: userId={}", accountId, userId);

        if (accountId == null) {
            throw new IllegalArgumentException("Account ID cannot be null");
        }
        if (userId == null) {
            throw new IllegalArgumentException("User ID cannot be null");
        }

        // Fetch account and verify ownership
        Account account = accountRepository
                .findByIdAndUserId(accountId, userId)
                .orElseThrow(() -> AccountNotFoundException.byIdAndUser(accountId, userId));

        // Calculate balance from transactions in Java (SQL SUM cannot operate on
        // encrypted amounts)
        List<Transaction> transactions = transactionRepository.findActiveByAccountId(accountId);
        java.math.BigDecimal transactionsBalance = transactions.stream()
                .map(t -> {
                    if (t.getAmount() == null)
                        return java.math.BigDecimal.ZERO;
                    return t.getType() == TransactionType.INCOME
                            ? t.getAmount()
                            : t.getAmount().negate();
                })
                .reduce(java.math.BigDecimal.ZERO, java.math.BigDecimal::add);

        // Final balance = openingBalance + transactionsBalance
        java.math.BigDecimal newBalance = account.getOpeningBalance().add(transactionsBalance);

        log.info(
                "Account {}: openingBalance={}, transactionsBalance={}, totalBalance={}",
                accountId,
                account.getOpeningBalance(),
                transactionsBalance,
                newBalance);

        // Update and save
        account.setBalance(newBalance);
        Account savedAccount = accountRepository.save(account);

        return toResponseWithDecryption(savedAccount);
    }

    /**
     * Helper method to decrypt sensitive fields and map to response DTO.
     *
     * @param account       the account entity with encrypted fields
     * @param encryptionKey the encryption key for decryption
     * @return the account response with decrypted fields
     */
    private AccountResponse toResponseWithDecryption(Account account) {
        // Fields already decrypted by JPA converter
        AccountResponse response = accountMapper.toResponse(account);
        response.setName(account.getName());
        response.setDescription(account.getDescription());
        response.setAccountNumber(account.getAccountNumber());

        // Set institution info if present
        if (account.getInstitution() != null) {
            response.setInstitution(
                    AccountResponse.InstitutionInfo.builder()
                            .id(account.getInstitution().getId())
                            .name(account.getInstitution().getName())
                            .bic(account.getInstitution().getBic())
                            .country(account.getInstitution().getCountry())
                            .logo(account.getInstitution().getLogo())
                            .build());
        }

        // Add the total value of linked assets to the account's balance
        List<org.openfinance.entity.Asset> linkedAssets = assetRepository.findByAccountId(account.getId());
        java.math.BigDecimal assetsTotalValue = linkedAssets.stream()
                .map(org.openfinance.entity.Asset::getTotalValue)
                .reduce(java.math.BigDecimal.ZERO, java.math.BigDecimal::add);

        java.math.BigDecimal totalBalance = account.getBalance().add(assetsTotalValue);
        response.setBalance(totalBalance);

        // Populate currency conversion metadata (Requirement REQ-3.1, REQ-3.5)
        populateConversionFields(
                response, account.getUserId(), account.getCurrency(), totalBalance);

        return response;
    }

    /**
     * Populates currency conversion metadata fields on an AccountResponse.
     *
     * <p>
     * Fetches the user's base currency from the database, then attempts to convert
     * the given
     * native amount to the base currency using {@link ExchangeRateService}. On
     * failure, falls back
     * to the native amount with {@code isConverted=false}.
     *
     * <p>
     * Also performs secondary currency conversion when the user has a secondary
     * currency
     * configured and it differs from the native currency. Secondary conversion
     * failure sets the
     * secondary fields to null without affecting base conversion.
     *
     * <p>
     * Requirement REQ-3.1: AccountService populates conversion fields
     *
     * <p>
     * Requirement REQ-3.5: Graceful fallback when conversion unavailable
     *
     * <p>
     * Requirement REQ-3.6: isConverted semantics
     *
     * <p>
     * Requirement REQ-4.1, REQ-4.5, REQ-4.6: Secondary conversion logic
     *
     * @param response       the response DTO to populate
     * @param userId         the ID of the account owner (used to look up base
     *                       currency)
     * @param nativeCurrency the account's native currency code (ISO 4217)
     * @param nativeAmount   the native balance amount
     */
    private void populateConversionFields(
            AccountResponse response, Long userId, String nativeCurrency, BigDecimal nativeAmount) {
        User user = userId != null ? userRepository.findById(userId).orElse(null) : null;
        String baseCurrency = user != null && user.getBaseCurrency() != null && !user.getBaseCurrency().isBlank()
                ? user.getBaseCurrency()
                : "USD";
        String secCurrency = user != null ? user.getSecondaryCurrency() : null;
        response.setBaseCurrency(baseCurrency);

        // Step 1: Base conversion
        boolean needsConversion = nativeCurrency != null && !nativeCurrency.equals(baseCurrency);
        if (!needsConversion || nativeAmount == null) {
            response.setBalanceInBaseCurrency(nativeAmount);
            response.setIsConverted(false);
        } else {
            try {
                BigDecimal rate = exchangeRateService.getExchangeRate(nativeCurrency, baseCurrency, null);
                BigDecimal converted = exchangeRateService.convert(nativeAmount, nativeCurrency, baseCurrency);
                response.setBalanceInBaseCurrency(converted);
                response.setExchangeRate(rate);
                response.setIsConverted(true);
            } catch (Exception e) {
                log.warn(
                        "Currency conversion failed for account (user={}, {}->{}) – falling back to native: {}",
                        userId,
                        nativeCurrency,
                        baseCurrency,
                        e.getMessage());
                response.setBalanceInBaseCurrency(nativeAmount);
                response.setIsConverted(false);
            }
        }

        // Step 2: Secondary conversion (Requirement REQ-4.1, REQ-4.6)
        if (secCurrency != null
                && !secCurrency.isBlank()
                && nativeCurrency != null
                && !nativeCurrency.equals(secCurrency)
                && nativeAmount != null) {
            try {
                BigDecimal secRate = exchangeRateService.getExchangeRate(nativeCurrency, secCurrency, null);
                BigDecimal secAmount = exchangeRateService.convert(nativeAmount, nativeCurrency, secCurrency);
                response.setBalanceInSecondaryCurrency(secAmount);
                response.setSecondaryCurrency(secCurrency);
                response.setSecondaryExchangeRate(secRate);
            } catch (Exception e) {
                log.warn(
                        "Secondary currency conversion failed for account (user={}, {}->{}) – omitting: {}",
                        userId,
                        nativeCurrency,
                        secCurrency,
                        e.getMessage());
                // Secondary fields remain null — frontend omits secondary line from tooltip
                response.setSecondaryCurrency(secCurrency);
            }
        } else if (secCurrency != null && !secCurrency.isBlank()) {
            // Secondary currency is configured but matches native — no conversion needed
            response.setSecondaryCurrency(secCurrency);
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
     * Retrieves the balance history for an account over time.
     *
     * <p>
     * Calculates daily balance by aggregating transactions for each day starting
     * from the
     * account's opening date. The balance is calculated cumulatively from the
     * opening balance.
     *
     * <p>
     * Requirement REQ-2.6.1.2: Account Balance Tracking - Historical snapshots
     *
     * @param accountId     the ID of the account
     * @param userId        the ID of the user (for authorization)
     * @param period        the time period: "1M", "3M", "6M", "1Y", "ALL"
     * @param encryptionKey the AES-256 encryption key (for decrypting account name)
     * @return list of balance history points (date, balance)
     * @throws AccountNotFoundException if account not found or doesn't belong to
     *                                  user
     * @throws IllegalArgumentException if any parameter is null or invalid
     */
    @Transactional(readOnly = true)
    public List<org.openfinance.dto.BalanceHistoryPoint> getAccountBalanceHistory(
            Long accountId, Long userId, String period) {
        log.debug(
                "Retrieving balance history for account {}: userId={}, period={}",
                accountId,
                userId,
                period);

        if (accountId == null) {
            throw new IllegalArgumentException("Account ID cannot be null");
        }
        if (userId == null) {
            throw new IllegalArgumentException("User ID cannot be null");
        }
        if (period == null || period.isBlank()) {
            throw new IllegalArgumentException("Period cannot be null or empty");
        }
        // Fetch account and verify ownership
        Account account = accountRepository
                .findByIdAndUserId(accountId, userId)
                .orElseThrow(() -> AccountNotFoundException.byIdAndUser(accountId, userId));

        // Calculate start date based on period
        java.time.LocalDate startDate = calculateStartDate(period, account.getOpeningDate());

        // Get transactions for the account within the date range
        java.time.LocalDate endDate = java.time.LocalDate.now();
        List<org.openfinance.entity.Transaction> transactions = transactionRepository
                .findByAccountIdAndTransactionDateBetweenOrderByTransactionDateAsc(
                        accountId, startDate, endDate);

        // Calculate daily balances
        java.math.BigDecimal openingBalance = account.getOpeningBalance();
        java.math.BigDecimal runningBalance = openingBalance;

        // Determine if we need to negate transaction amounts (for credit cards)
        boolean isCreditCard = account.getType() == org.openfinance.entity.AccountType.CREDIT_CARD;

        // Map to store total transaction amount per day
        java.util.Map<java.time.LocalDate, java.math.BigDecimal> dailyTxAmounts = new java.util.LinkedHashMap<>();

        // First, sum up all transaction amounts by date
        for (org.openfinance.entity.Transaction transaction : transactions) {
            java.time.LocalDate txDate = transaction.getDate();
            java.math.BigDecimal txAmount = transaction.getAmount();

            // For credit cards, charges increase balance (negative amount means expense,
            // but increases debt)
            // So we negate to get the correct sign for the balance calculation
            if (isCreditCard) {
                txAmount = txAmount.negate();
            }

            dailyTxAmounts.merge(
                    txDate, txAmount, (existing, newAmount) -> existing.add(newAmount));
        }

        // Now calculate running balance for each day
        java.util.Map<java.time.LocalDate, java.math.BigDecimal> dailyBalances = new java.util.LinkedHashMap<>();

        // Add opening date balance
        dailyBalances.put(account.getOpeningDate(), openingBalance);

        // Process each day in order
        for (java.util.Map.Entry<java.time.LocalDate, java.math.BigDecimal> entry : dailyTxAmounts.entrySet()) {
            java.time.LocalDate txDate = entry.getKey();
            java.math.BigDecimal dayTotal = entry.getValue();

            // Add the day's total to running balance
            runningBalance = runningBalance.add(dayTotal);
            dailyBalances.put(txDate, runningBalance);
        }

        // Convert to list sorted by date
        List<org.openfinance.dto.BalanceHistoryPoint> history = dailyBalances.entrySet().stream()
                .sorted(java.util.Map.Entry.comparingByKey())
                .map(
                        entry -> new org.openfinance.dto.BalanceHistoryPoint(
                                entry.getKey(), entry.getValue()))
                .collect(java.util.stream.Collectors.toList());

        log.debug("Found {} balance history points for account {}", history.size(), accountId);

        return history;
    }

    /**
     * Returns a lightweight summary list of accounts for the given user.
     *
     * <p>
     * This method is optimised for high-volume list use-cases where only the most
     * essential
     * account fields are required. It reuses the existing
     * {@link #getAccountsByUserIdWithFilter}
     * fetch but maps results to the smaller {@link AccountSummaryResponse}
     * projection, avoiding the
     * full currency-conversion and institution-info resolution that {@link
     * #toResponseWithDecryption} performs.
     *
     * <p>
     * Requirement TASK-14.1.3: Sparse fieldsets / summary projection.
     *
     * @param userId        the ID of the user
     * @param encryptionKey the AES-256 encryption key for decrypting account names
     * @return list of lightweight account summaries (may be empty)
     * @throws IllegalArgumentException if userId or encryptionKey is null
     */
    @Transactional(readOnly = true)
    public List<AccountSummaryResponse> getAccountsSummary(Long userId) {
        log.debug("Retrieving account summaries for user {}", userId);

        if (userId == null) {
            throw new IllegalArgumentException("User ID cannot be null");
        }
        // Fetch active accounts only for the summary view
        List<Account> accounts = accountRepository.findByUserIdAndIsActive(userId, true);

        log.debug("Found {} active accounts for summary (userId={})", accounts.size(), userId);

        return accounts.stream()
                .map(
                        account -> {
                            String decryptedName = account.getName();
                            String institutionName = (account.getInstitution() != null)
                                    ? account.getInstitution().getName()
                                    : null;
                            return AccountSummaryResponse.builder()
                                    .id(account.getId())
                                    .name(decryptedName)
                                    .type(
                                            account.getType() != null
                                                    ? account.getType().name()
                                                    : null)
                                    .currency(account.getCurrency())
                                    .balance(account.getBalance())
                                    .isActive(Boolean.TRUE.equals(account.getIsActive()))
                                    .institutionName(institutionName)
                                    .build();
                        })
                .collect(Collectors.toList());
    }

    /** Calculate start date based on period string. */
    private java.time.LocalDate calculateStartDate(String period, java.time.LocalDate openingDate) {
        java.time.LocalDate now = java.time.LocalDate.now();

        return switch (period.toUpperCase()) {
            case "1M" -> now.minusMonths(1);
            case "3M" -> now.minusMonths(3);
            case "6M" -> now.minusMonths(6);
            case "1Y" -> now.minusYears(1);
            case "ALL" -> openingDate; // Use account opening date
            default -> now.minusMonths(3); // Default to 3 months
        };
    }

    private void indexAccountSearchTokens(Account account, String name, String description) {
        try {
            SecretKey key = org.openfinance.security.EncryptionContext.getKey();
            if (key == null) {
                return;
            }
            SecretKey searchKey = searchTokenService.deriveSearchKey(key);
            searchTokenService.indexEntity(account.getUserId(), "ACCOUNT", account.getId(),
                    java.util.List.of(
                            new String[] { "name", name },
                            new String[] { "description", description }),
                    searchKey);
        } catch (Exception e) {
            log.warn("Failed to index account {} search tokens: {}", account.getId(), e.getMessage());
        }
    }

}
