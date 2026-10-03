package org.openfinance.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.openfinance.config.ImportProperties;
import org.openfinance.dto.AccountRequest;
import org.openfinance.dto.AccountResponse;
import org.openfinance.dto.ImportParseResult;
import org.openfinance.dto.ImportReviewRequest;
import org.openfinance.dto.ImportedTransaction;
import org.openfinance.dto.SkroogeImportMetadata;
import org.openfinance.dto.SkroogeImportParseResult;
import org.openfinance.dto.TransactionRequest;
import org.openfinance.dto.TransactionSplitRequest;
import org.openfinance.entity.Account;
import org.openfinance.entity.AccountType;
import org.openfinance.entity.Category;
import org.openfinance.entity.CategoryType;
import org.openfinance.entity.EntityType;
import org.openfinance.entity.ImportSession;
import org.openfinance.entity.ImportSession.ImportStatus;
import org.openfinance.entity.Institution;
import org.openfinance.entity.OperationType;
import org.openfinance.entity.PaymentMethod;
import org.openfinance.entity.Transaction;
// Import TransactionType enum
import org.openfinance.entity.TransactionType;
import org.openfinance.entity.User;
import org.openfinance.entity.UserSettings;
import org.openfinance.exception.ResourceNotFoundException;
import org.openfinance.repository.AccountRepository;
import org.openfinance.repository.CategoryRepository;
import org.openfinance.repository.CurrencyRepository;
import org.openfinance.repository.ImportSessionRepository;
import org.openfinance.repository.InstitutionRepository;
import org.openfinance.repository.NetWorthRepository;
import org.openfinance.repository.PayeeRepository;
import org.openfinance.repository.TransactionRepository;
import org.openfinance.repository.UserRepository;
import org.openfinance.repository.UserSettingsRepository;
import org.openfinance.service.history.ReversibleOperation;
import org.openfinance.service.parser.CsvParser;
import org.openfinance.service.parser.ImportParseContext;
import org.openfinance.service.parser.OfxParser;
import org.openfinance.service.parser.QifParser;
import org.openfinance.service.parser.SkroogeJsonParser;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Service for managing transaction import from files (QIF, OFX, QFX).
 *
 * <p>Import Process Flow:
 *
 * <ol>
 *   <li>User uploads file via FileUploadController ΓåÆ FileStorageService
 *   <li>User initiates import via startImport(uploadId, accountId)
 *   <li>System creates ImportSession with PENDING status
 *   <li>System parses file asynchronously ΓåÆ PARSING ΓåÆ PARSED
 *   <li>User reviews transactions, maps categories, handles duplicates ΓåÆ REVIEWING
 *   <li>User confirms import ΓåÆ IMPORTING ΓåÆ COMPLETED
 * </ol>
 *
 * @see ImportSession
 * @see ImportedTransaction
 * @see QifParser
 * @see OfxParser
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ImportService {

    private static final int TRANSACTION_DESCRIPTION_MAX_LENGTH = 255;
    private static final int TRANSACTION_NOTES_MAX_LENGTH = 1000;
    private static final int TRANSACTION_PAYEE_MAX_LENGTH = 100;
    private static final int TRANSACTION_TAGS_MAX_LENGTH = 500;

    /** Prefixes the frontend can render directly in an {@code <img>} element. */
    private static final Set<String> DISPLAYABLE_LOGO_PREFIXES =
            Set.of("data:", "http://", "https://", "/logos/");

    /** Minimum existing-institution slug length for a containment match. */
    private static final int MIN_CONTAINMENT_SLUG_LENGTH = 4;

    private final ImportSessionRepository importSessionRepository;
    private final BudgetAlertService budgetAlertService;
    private final TransactionRepository transactionRepository;
    private final AccountRepository accountRepository;
    private final CategoryRepository categoryRepository;
    private final UserRepository userRepository;
    private final FileStorageService fileStorageService;
    private final QifParser qifParser;
    private final OfxParser ofxParser;
    private final CsvParser csvParser;
    private final SkroogeJsonParser skroogeJsonParser;
    private final ObjectMapper objectMapper;
    private final AutoCategorizationService autoCategorizationService;
    private final AccountService accountService;
    private final ImportedOpeningBalanceService openingBalanceService;
    private final TransactionRuleService transactionRuleService;
    private final TransactionService transactionService;
    private final ExchangeRateService exchangeRateService;
    private final TransactionSplitService transactionSplitService;
    private final NetWorthRepository netWorthRepository;
    private final AICategorizationService aiCategorizationService;
    private final MessageSource messageSource;
    private final InstitutionRepository institutionRepository;
    private final CurrencyRepository currencyRepository;
    private final PayeeRepository payeeRepository;
    private final DefaultCurrencyProvider defaultCurrencyProvider;
    private final ImportProperties importProperties;
    private final AccountCurrencyService accountCurrencyService;

    /**
     * Lazily-resolved executor used to run import confirmation on a background thread. Injected as
     * an {@link ObjectProvider} to break the construction-time circular dependency between this
     * service and {@link ImportConfirmationExecutor} (which depends on this service).
     */
    private final ObjectProvider<ImportConfirmationExecutor> importConfirmationExecutor;

    private final UserSettingsRepository userSettingsRepository;
    private final OperationHistoryService operationHistoryService;

    /**
     * Start a new import session and parse the uploaded file.
     *
     * @param uploadId the UUID of the uploaded file
     * @param userId the ID of the user initiating the import
     * @param accountId the target account ID (can be null if selected during review)
     * @return the created import session
     * @throws ResourceNotFoundException if upload not found
     * @throws IllegalArgumentException if file validation fails
     */
    @Transactional
    public ImportSession startImport(String uploadId, Long userId, Long accountId) {
        return startImport(uploadId, userId, accountId, null);
    }

    @Transactional
    public ImportSession startImport(
            String uploadId, Long userId, Long accountId, String originalFileName) {
        log.info(
                "Starting import for uploadId={}, userId={}, accountId={}",
                uploadId,
                userId,
                accountId);

        // Validate upload exists
        if (!fileStorageService.fileExists(uploadId, userId)) {
            throw new ResourceNotFoundException("Uploaded file not found: " + uploadId);
        }

        // Get storage filename (UUID.ext) ΓÇö used for format detection
        String storageFileName = fileStorageService.getOriginalFileName(uploadId, userId);

        // Prefer the original user-visible filename (e.g. "bank_statement.csv") for
        // display purposes and account name derivation; fall back to storage filename
        String displayFileName =
                (originalFileName != null && !originalFileName.isBlank())
                        ? originalFileName
                        : storageFileName;

        // Detect file format from the storage filename (preserves the extension)
        String fileFormat = detectFileFormat(storageFileName, uploadId);

        // Validate account if provided
        if (accountId != null) {
            Account account =
                    accountRepository
                            .findByIdAndUserId(accountId, userId)
                            .orElseThrow(
                                    () ->
                                            new ResourceNotFoundException(
                                                    "Account not found: " + accountId));

            if (!account.getIsActive()) {
                throw new IllegalArgumentException(
                        "Cannot import to inactive account: " + accountId);
            }
        }

        // Create import session
        ImportSession session =
                ImportSession.builder()
                        .uploadId(uploadId)
                        .userId(userId)
                        .fileName(displayFileName)
                        .fileFormat(fileFormat)
                        .accountId(accountId)
                        .status(ImportStatus.PENDING)
                        .build();

        session = importSessionRepository.save(session);
        log.info("Created import session: {}", session.getId());

        // Start parsing asynchronously (non-blocking)
        // Client should poll GET /api/v1/import/sessions/{id} for status updates
        parseFileAsync(session.getId());

        return session;
    }

    /**
     * Parse the uploaded file asynchronously and extract transactions. Updates the session status
     * to PARSING ΓåÆ PARSED (or FAILED on error).
     *
     * <p>This method runs in a background thread managed by the taskExecutor. The HTTP request
     * returns immediately with the session ID, and the client should poll GET
     * /api/v1/import/sessions/{id} to check progress.
     *
     * <p><strong>Status Transitions:</strong>
     *
     * <ul>
     *   <li>PENDING ΓåÆ PARSING (when parsing starts)
     *   <li>PARSING ΓåÆ PARSED (on success)
     *   <li>PARSING ΓåÆ FAILED (on error)
     * </ul>
     *
     * <p><strong>Transaction Management:</strong>
     *
     * <p>Note: No @Transactional annotation on this async method. Each repository operation runs in
     * its own transaction, which is actually desirable because:
     *
     * <ul>
     *   <li>Status updates (PARSING ΓåÆ PARSED/FAILED) are persisted immediately
     *   <li>No risk of long-running transaction holding database locks
     *   <li>Partial commits are acceptable (we want to persist FAILED status)
     * </ul>
     *
     * <p>Requirement REQ-2.5.1.8: Asynchronous file parsing
     *
     * @param sessionId the import session ID
     */
    @Async("taskExecutor")
    public void parseFileAsync(Long sessionId) {
        log.info("Starting async parsing for session: {}", sessionId);

        // Fetch session in async context
        ImportSession session =
                importSessionRepository
                        .findById(sessionId)
                        .orElseThrow(
                                () ->
                                        new ResourceNotFoundException(
                                                "Import session not found: " + sessionId));

        try {
            // Update status to PARSING
            session.setStatus(ImportStatus.PARSING);
            importSessionRepository.save(session);

            // Get file content
            InputStream fileStream =
                    fileStorageService.getFileContent(session.getUploadId(), session.getUserId());

            // Parse based on format
            List<ImportedTransaction> transactions;
            BigDecimal ledgerBalance = null;
            Map<String, BigDecimal> ledgerBalances = Map.of();
            String fileCurrency = defaultCurrencyProvider.getDefaultCurrency();
            try {
                User user = userRepository.findById(session.getUserId()).orElse(null);
                if (user != null && user.getBaseCurrency() != null) {
                    fileCurrency = user.getBaseCurrency();
                }
            } catch (Exception e) {
                log.warn("Could not fetch user base currency, defaulting to USD", e);
            }

            // Capture the user's parsing preferences (date ordering, message locale) up
            // front —
            // this method runs on a background thread where the request locale is
            // unavailable.
            ImportParseContext parseContext = buildParseContext(session.getUserId());

            switch (session.getFileFormat().toUpperCase()) {
                case "QIF":
                    transactions =
                            qifParser.parseFile(fileStream, session.getFileName(), parseContext);
                    break;
                case "OFX":
                case "QFX":
                    ImportParseResult ofxResult =
                            ofxParser.parseFileToResult(
                                    fileStream, session.getFileName(), parseContext);
                    transactions = ofxResult.getTransactions();
                    if (ofxResult.getLedgerBalance() != null) {
                        ledgerBalance = ofxResult.getLedgerBalance();
                        ledgerBalances = ofxResult.getLedgerBalances();
                    }
                    if (ofxResult.getCurrency() != null) {
                        fileCurrency = ofxResult.getCurrency();
                    }
                    break;
                case "CSV":
                    transactions =
                            csvParser.parseFile(fileStream, session.getFileName(), parseContext);
                    break;
                case "JSON":
                    if (!importProperties.isSkroogeJsonEnabled()) {
                        throw new IllegalArgumentException(
                                "Skrooge JSON import is currently disabled");
                    }
                    SkroogeImportParseResult skroogeResult =
                            skroogeJsonParser.parseFile(fileStream, session.getFileName());
                    transactions = skroogeResult.getTransactions();
                    if (skroogeResult.getCurrency() != null) {
                        fileCurrency = skroogeResult.getCurrency();
                    }
                    session.setMetadata(
                            serializeTransactions(
                                    transactions,
                                    ledgerBalance,
                                    fileCurrency,
                                    Map.of("skroogeMetadata", skroogeResult.getSkroogeMetadata())));
                    break;
                default:
                    throw new IllegalArgumentException(
                            "Unsupported file format: " + session.getFileFormat());
            }

            // Attempt to detect account automatically if not provided
            if (session.getAccountId() == null && !transactions.isEmpty()) {
                String detectedAccountName = null;
                for (ImportedTransaction tx : transactions) {
                    if (tx.getAccountName() != null && !tx.getAccountName().trim().isEmpty()) {
                        detectedAccountName = tx.getAccountName().trim();
                        break;
                    }
                }

                if (detectedAccountName != null) {
                    session.setSuggestedAccountName(formatAccountSuggestion(detectedAccountName));
                    // Try to find matching account
                    List<Account> userAccounts =
                            accountRepository.findByUserId(session.getUserId());
                    for (Account acc : userAccounts) {
                        String normalizedDetected =
                                detectedAccountName.replaceAll("[^a-zA-Z0-9]", "").toLowerCase();
                        String normalizedAccName =
                                acc.getName().replaceAll("[^a-zA-Z0-9]", "").toLowerCase();
                        String normalizedAccNum =
                                acc.getAccountNumber() != null
                                        ? acc.getAccountNumber()
                                                .replaceAll("[^a-zA-Z0-9]", "")
                                                .toLowerCase()
                                        : "";

                        if (normalizedAccName.equals(normalizedDetected)
                                || normalizedAccNum.equals(normalizedDetected)) {
                            session.setAccountId(acc.getId());
                            log.info(
                                    "Automatically matched account '{}' (id={}) for session {}",
                                    acc.getName(),
                                    acc.getId(),
                                    sessionId);
                            break;
                        }
                    }
                } else {
                    // Fallback to filename (without extension)
                    String nameWithoutExt = session.getFileName();
                    int dotIndex = nameWithoutExt.lastIndexOf('.');
                    if (dotIndex > 0) {
                        nameWithoutExt = nameWithoutExt.substring(0, dotIndex);
                    }
                    session.setSuggestedAccountName(nameWithoutExt);
                }
            }

            // Update session with parsing results
            session.setTotalTransactions(transactions.size());
            session.setErrorCount(
                    (int) transactions.stream().filter(ImportedTransaction::hasErrors).count());
            session.setStatus(ImportStatus.PARSED);

            log.info(
                    "Async parsing complete for session {}: {} transactions extracted, {} with errors",
                    sessionId,
                    transactions.size(),
                    session.getErrorCount());

            // Store parsed transactions in metadata (JSON format)
            // Note: In production, consider storing in separate table for large imports
            if (!"JSON".equalsIgnoreCase(session.getFileFormat())) {
                session.setMetadata(
                        serializeTransactions(
                                transactions,
                                ledgerBalance,
                                fileCurrency,
                                Map.of(
                                        "ledgerBalances",
                                        ledgerBalances == null ? Map.of() : ledgerBalances,
                                        "statementNetAmounts",
                                        statementNetAmounts(transactions))));
            }

            importSessionRepository.save(session);

        } catch (IOException e) {
            log.error("Error parsing file for session {}: {}", sessionId, e.getMessage(), e);
            session.setStatus(ImportStatus.FAILED);
            session.setErrorMessage("Failed to parse file: " + e.getMessage());
            importSessionRepository.save(session);
            // Don't rethrow - async method should handle errors gracefully
        } catch (Exception e) {
            log.error(
                    "Unexpected error parsing file for session {}: {}",
                    sessionId,
                    e.getMessage(),
                    e);
            session.setStatus(ImportStatus.FAILED);
            session.setErrorMessage("Unexpected error: " + e.getMessage());
            importSessionRepository.save(session);
        }
    }

    /**
     * Build the parsing context (date ordering, message locale) from the user's settings. Falls
     * back to locale-derived defaults when the user has no settings or they cannot be loaded.
     */
    private ImportParseContext buildParseContext(Long userId) {
        try {
            UserSettings settings = userSettingsRepository.findByUserId(userId).orElse(null);
            if (settings != null) {
                return ImportParseContext.from(settings);
            }
        } catch (Exception e) {
            log.warn("Could not load settings for user {}; using parse defaults", userId, e);
        }
        return ImportParseContext.defaults();
    }

    /**
     * Get parsed transactions for review. Includes duplicate detection and category suggestions.
     *
     * @param sessionId the import session ID
     * @param userId the user ID (for authorization)
     * @return list of imported transactions with duplicate flags and category suggestions
     */
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public List<ImportedTransaction> reviewTransactions(Long sessionId, Long userId) {
        log.info("Reviewing transactions for session: {}", sessionId);

        ImportSession session = getSessionForUser(sessionId, userId);

        if (!session.isReadyForReview()) {
            throw new IllegalStateException(
                    "Session is not ready for review. Current status: " + session.getStatus());
        }

        // Review is a projection of parsed input until the user explicitly saves it.
        // Never write a detached session here: confirmation may have completed while
        // categorization was running. Saved review edits must not run through rules again.
        List<ImportedTransaction> transactions = deserializeTransactions(session.getMetadata());
        prepareUnreviewedTransactions(session, transactions, userId);
        validateImportedSplitAmounts(transactions, session.getAccountId(), userId);
        detectDuplicates(transactions, session.getAccountId(), session.getFileFormat(), userId);

        resolveReviewCurrencies(session, transactions, userId);

        return transactions;
    }

    private void resolveReviewCurrencies(
            ImportSession session, List<ImportedTransaction> transactions, Long userId) {
        Map<String, Long> scopes =
                duplicateAccountScopes(transactions, session.getAccountId(), userId);
        Map<Long, String> currencies =
                accountRepository.findByUserId(userId).stream()
                        .filter(
                                account ->
                                        account.getCurrency() != null
                                                && !account.getCurrency().isBlank())
                        .collect(Collectors.toMap(Account::getId, Account::getCurrency));
        String fileCurrency = extractFileCurrency(session.getMetadata(), userId);
        for (ImportedTransaction transaction : transactions) {
            String currency = transaction.getCurrency();
            if (currency == null || currency.isBlank()) {
                String key =
                        buildImportedAccountKey(
                                transaction.getAccountName(), transaction.getAccountNumber());
                Long accountId = key == null ? session.getAccountId() : scopes.get(key);
                currency = currencies.getOrDefault(accountId, fileCurrency);
            }
            transaction.setReviewCurrency(currency);
        }
    }

    /**
     * Update the target account for an import session.
     *
     * @param sessionId the import session ID
     * @param accountId the new account ID
     * @param userId the user ID (for authorization)
     * @return the updated import session
     */
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public ImportSession updateAccount(Long sessionId, Long accountId, Long userId) {
        ImportSession session = getSessionForUser(sessionId, userId);
        if (!session.isReadyForReview()) {
            throw new IllegalStateException(
                    "Session is not ready for review: " + session.getStatus());
        }
        if (accountId != null) {
            Account account =
                    accountRepository
                            .findByIdAndUserId(accountId, userId)
                            .orElseThrow(
                                    () ->
                                            new ResourceNotFoundException(
                                                    "Account not found: " + accountId));
            if (!account.getIsActive()) {
                throw new IllegalArgumentException(
                        "Cannot import to inactive account: " + accountId);
            }
        }
        if (importSessionRepository.selectReviewAccount(
                        sessionId, userId, accountId, LocalDateTime.now())
                != 1) {
            throw new IllegalStateException("Import is no longer available for review");
        }
        return getSessionForUser(sessionId, userId);
    }

    /**
     * Update the parsed transactions in the session metadata, usually after manual review/edits.
     *
     * @param sessionId the import session ID
     * @param transactions the updated list of imported transactions
     * @param userId the user ID (for authorization)
     * @return the updated import session
     */
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public ImportSession updateParsedTransactions(
            Long sessionId, List<ImportedTransaction> transactions, Long userId) {
        return saveReview(sessionId, transactions, userId, null);
    }

    /** Persist review choices with the rows so reloading can restore the same confirmation. */
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public ImportSession updateReview(Long sessionId, ImportReviewRequest request, Long userId) {
        return saveReview(sessionId, request.getTransactions(), userId, request);
    }

    private ImportSession saveReview(
            Long sessionId,
            List<ImportedTransaction> transactions,
            Long userId,
            ImportReviewRequest review) {
        log.info("Updating parsed transactions for session: {}", sessionId);

        ImportSession session = getSessionForUser(sessionId, userId);

        if (!session.isReadyForReview()) {
            throw new IllegalStateException(
                    "Session is not ready for review. Current status: " + session.getStatus());
        }

        // Detect duplicates with the currently selected account
        validateImportedSplitAmounts(transactions, session.getAccountId(), userId);
        detectDuplicates(transactions, session.getAccountId(), session.getFileFormat(), userId);

        // Preserve existing ledgerBalance and fileCurrency in metadata
        BigDecimal ledgerBalance = null;
        String fileCurrency = defaultCurrencyProvider.getDefaultCurrency();
        try {
            User user = userRepository.findById(session.getUserId()).orElse(null);
            if (user != null && user.getBaseCurrency() != null) {
                fileCurrency = user.getBaseCurrency();
            }
        } catch (Exception e) {
            log.warn("Could not fetch user base currency, defaulting to USD", e);
        }
        if (session.getMetadata() != null && !session.getMetadata().trim().isEmpty()) {
            try {
                Map<String, Object> metadataMap = deserializeMetadata(session.getMetadata());
                if (metadataMap.get("ledgerBalance") != null) {
                    ledgerBalance = new BigDecimal(metadataMap.get("ledgerBalance").toString());
                }
                if (metadataMap.containsKey("fileCurrency")
                        && metadataMap.get("fileCurrency") != null) {
                    fileCurrency = metadataMap.get("fileCurrency").toString();
                }
            } catch (Exception e) {
                log.warn("Error extracting ledger metadata during update: {}", e.getMessage());
            }
        }

        Map<String, Object> extraMetadata = preserveMetadata(session.getMetadata());
        extraMetadata.put("reviewSaved", true);
        if (review != null) {
            validateImportCategoryMappings(transactions, userId, review.getCategoryMappings());
            extraMetadata.put(
                    "reviewOptions",
                    Map.of(
                            "categoryMappings", review.getCategoryMappings(),
                            "skipDuplicates", review.isSkipDuplicates()));
        }
        String metadata =
                serializeTransactions(transactions, ledgerBalance, fileCurrency, extraMetadata);
        if (importSessionRepository.saveReview(
                        sessionId, userId, metadata, transactions.size(), LocalDateTime.now())
                != 1) {
            throw new IllegalStateException("Import is no longer available for review");
        }
        return getSessionForUser(sessionId, userId);
    }

    /**
     * Begin an import confirmation asynchronously.
     *
     * <p>Validates the session, transitions it to {@code IMPORTING} in its own transaction, and
     * then hands the heavy work off to a background thread. The HTTP request returns immediately;
     * the client polls {@code GET /api/v1/import/sessions/{id}} until the status becomes {@code
     * COMPLETED} or {@code FAILED}.
     *
     * @param sessionId the import session ID
     * @param userId the user ID (for authorization)
     * @param accountId the target account ID (if null, an account will be auto-created)
     * @param categoryMappings map of imported category names to category IDs
     * @param skipDuplicates if true, skip transactions flagged as duplicates
     * @return the session in {@code IMPORTING} status
     */
    public ImportSession startConfirmImport(
            Long sessionId,
            Long userId,
            Long accountId,
            Map<String, Long> categoryMappings,
            boolean skipDuplicates) {
        ImportSession session = markImporting(sessionId, userId);
        importConfirmationExecutor
                .getObject()
                .runAsync(
                        sessionId,
                        userId,
                        accountId,
                        categoryMappings,
                        skipDuplicates,
                        org.openfinance.security.EncryptionContext.getKey());
        return session;
    }

    /**
     * Validate that a session can be confirmed and transition it to {@code IMPORTING}.
     *
     * @param sessionId the import session ID
     * @param userId the owning user ID
     * @return the updated session
     * @throws IllegalStateException if the session is not in a confirmable state
     */
    @Transactional
    public ImportSession markImporting(Long sessionId, Long userId) {
        int claimed = importSessionRepository.claimConfirmation(sessionId, userId);
        ImportSession session = getSessionForUser(sessionId, userId);
        if (claimed != 1) {
            throw new IllegalStateException(
                    "Session cannot be confirmed. Current status: " + session.getStatus());
        }
        return session;
    }

    /**
     * Mark an import session as {@code FAILED} with the given error message. Used by the async
     * executor when confirmation throws.
     *
     * @param sessionId the import session ID
     * @param errorMessage the failure detail (truncated to fit the column)
     */
    @Transactional
    public void markImportFailed(Long sessionId, String errorMessage) {
        importSessionRepository
                .findById(sessionId)
                .ifPresent(
                        session -> {
                            if (session.isTerminal()) return;
                            session.setStatus(ImportStatus.FAILED);
                            session.setErrorMessage(truncate(errorMessage, 1000));
                            session.setCompletedAt(LocalDateTime.now());
                            importSessionRepository.save(session);
                        });
    }

    /**
     * Confirm import and save transactions to database.
     *
     * @param sessionId the import session ID
     * @param userId the user ID (for authorization)
     * @param accountId the target account ID (if null, an account will be auto-created using the
     *     session's suggestedAccountName)
     * @param categoryMappings map of imported category names to category IDs
     * @param skipDuplicates if true, skip transactions flagged as duplicates
     * @param encryptionKey the user's encryption key (used when auto-creating an account)
     * @return the updated import session
     */
    @Transactional
    @ReversibleOperation(
            entity = EntityType.IMPORT,
            operation = OperationType.CREATE,
            userArgument = 1,
            idArgument = 0)
    public ImportSession confirmImport(
            Long sessionId,
            Long userId,
            Long accountId,
            Map<String, Long> categoryMappings,
            boolean skipDuplicates) {
        log.info(
                "Confirming import for session: {}, accountId={}, skipDuplicates={}",
                sessionId,
                accountId,
                skipDuplicates);

        if (importSessionRepository.beginConfirmationExecution(sessionId, userId) != 1) {
            throw new IllegalStateException(
                    "Import has already been started or is no longer confirmable");
        }
        ImportSession session = getSessionForUser(sessionId, userId);

        // Accept IMPORTING as well: startConfirmImport() flips the status to IMPORTING
        // synchronously
        // before this method runs on the async worker thread.
        if (!session.isConfirmable() && session.getStatus() != ImportStatus.IMPORTING) {
            throw new IllegalStateException(
                    "Session cannot be confirmed. Current status: " + session.getStatus());
        }

        List<ImportedTransaction> transactions = deserializeTransactions(session.getMetadata());
        prepareUnreviewedTransactions(session, transactions, userId);
        validateImportedSplitAmounts(
                transactions, accountId != null ? accountId : session.getAccountId(), userId);
        validateImportCategoryMappings(transactions, userId, categoryMappings);
        detectDuplicates(
                transactions,
                accountId != null ? accountId : session.getAccountId(),
                session.getFileFormat(),
                userId);

        if ("JSON".equalsIgnoreCase(session.getFileFormat())
                && hasSkroogeMetadata(session.getMetadata())) {
            return confirmSkroogeImport(
                    session, userId, categoryMappings, skipDuplicates, transactions);
        }

        if (shouldUseImportedAccountRouting(transactions)) {
            return confirmImportedAccountImport(
                    session, userId, accountId, categoryMappings, skipDuplicates, transactions);
        }

        // Resolve target account: use provided ID, fall back to session's accountId,
        // or auto-create a new account from the session's suggestedAccountName.
        Long resolvedAccountId = accountId != null ? accountId : session.getAccountId();
        if (resolvedAccountId == null) {
            BigDecimal ledgerBalance = null;
            String fileCurrency = defaultCurrencyProvider.getDefaultCurrency();
            try {
                User user = userRepository.findById(session.getUserId()).orElse(null);
                if (user != null && user.getBaseCurrency() != null) {
                    fileCurrency = user.getBaseCurrency();
                }
            } catch (Exception e) {
                log.warn("Could not fetch user base currency, defaulting to USD", e);
            }
            if (session.getMetadata() != null && !session.getMetadata().trim().isEmpty()) {
                try {
                    Map<String, Object> metadataMap = deserializeMetadata(session.getMetadata());
                    if (metadataMap.get("ledgerBalance") != null) {
                        ledgerBalance = new BigDecimal(metadataMap.get("ledgerBalance").toString());
                    }
                    if (metadataMap.containsKey("fileCurrency")
                            && metadataMap.get("fileCurrency") != null) {
                        fileCurrency = metadataMap.get("fileCurrency").toString();
                    }
                } catch (Exception e) {
                    log.warn(
                            "Error extracting ledger metadata during auto-create: {}",
                            e.getMessage());
                }
            }
            // The OFX ledgerBalance is the ENDING balance (after all transactions).
            // The account's openingBalance must be: ledgerBalance - net of imported
            // transactions
            // so that recalculateBalance (openingBalance + income - expenses) =
            // ledgerBalance.
            if (ledgerBalance != null) {
                List<ImportedTransaction> parsedTxs =
                        deserializeTransactions(session.getMetadata());
                BigDecimal transactionNet =
                        parsedTxs.stream()
                                .filter(tx -> !tx.hasErrors())
                                .map(
                                        tx -> {
                                            if (tx.getAmount().compareTo(BigDecimal.ZERO) >= 0) {
                                                return tx.getAmount().abs(); // INCOME adds
                                            } else {
                                                return tx.getAmount()
                                                        .abs()
                                                        .negate(); // EXPENSE subtracts
                                            }
                                        })
                                .reduce(BigDecimal.ZERO, BigDecimal::add);
                Object originalNet =
                        readMetadataMap(session.getMetadata()).get("statementNetAmounts");
                if (originalNet instanceof Map<?, ?> amounts && amounts.get("") != null) {
                    transactionNet = new BigDecimal(amounts.get("").toString());
                }
                ledgerBalance = ledgerBalance.subtract(transactionNet);
            }
            resolvedAccountId =
                    createAccountForImport(
                            userId,
                            session,
                            ledgerBalance == null ? BigDecimal.ZERO : ledgerBalance,
                            fileCurrency);
        }
        final Long targetAccountId = resolvedAccountId;

        Account account =
                accountRepository
                        .findByIdAndUserId(targetAccountId, userId)
                        .orElseThrow(
                                () ->
                                        new ResourceNotFoundException(
                                                "Account not found: " + targetAccountId));

        if (!account.getIsActive()) {
            throw new IllegalArgumentException(
                    "Cannot import to inactive account: " + targetAccountId);
        }

        // Update session status
        session.setStatus(ImportStatus.IMPORTING);
        session.setAccountId(targetAccountId);
        importSessionRepository.save(session);

        try {
            // Separate blocking-error transactions from importable ones
            List<ImportedTransaction> errorTxs =
                    transactions.stream()
                            .filter(tx -> tx.hasErrors() && !tx.isSkippedByRule())
                            .collect(Collectors.toList());

            List<ImportedTransaction> validTxs =
                    transactions.stream()
                            .filter(tx -> !tx.hasErrors())
                            .collect(Collectors.toList());

            // Among valid transactions, split on duplicate flag
            List<ImportedTransaction> duplicateTxs =
                    validTxs.stream().filter(this::isDuplicate).collect(Collectors.toList());

            List<ImportedTransaction> toImport =
                    validTxs.stream()
                            .filter(tx -> !skipDuplicates || !isDuplicate(tx))
                            .collect(Collectors.toList());

            // Separate opening-balance rows (Skrooge "0000-00-00") — apply to the account's
            // opening_balance instead of persisting as transactions.
            List<ImportedTransaction> openingBalanceTxs =
                    toImport.stream()
                            .filter(ImportedTransaction::isOpeningBalance)
                            .collect(Collectors.toList());
            toImport =
                    toImport.stream()
                            .filter(tx -> !tx.isOpeningBalance())
                            .collect(Collectors.toList());
            if (!openingBalanceTxs.isEmpty()) {
                BigDecimal openingDelta =
                        openingBalanceTxs.stream()
                                .map(ImportedTransaction::getAmount)
                                .filter(java.util.Objects::nonNull)
                                .reduce(BigDecimal.ZERO, BigDecimal::add);
                LocalDate openingDate =
                        openingBalanceTxs.stream()
                                .map(ImportedTransaction::getTransactionDate)
                                .filter(java.util.Objects::nonNull)
                                .min(LocalDate::compareTo)
                                .orElse(null);
                openingBalanceService.reconcile(
                        account, openingDelta, account.getCurrency(), openingDate, userId);
            }

            // Save transactions
            int imported = 0;
            int saveFailed = 0;

            for (ImportedTransaction importedTx : toImport) {
                try {
                    Transaction transaction =
                            convertToTransaction(
                                    importedTx, targetAccountId, userId, categoryMappings);
                    List<TransactionSplitRequest> splits =
                            prepareImportedSplits(
                                    transaction, importedTx, userId, categoryMappings, Map.of());
                    accountCurrencyService.book(transaction, userId);
                    Transaction saved = transactionRepository.save(transaction);
                    if (importedTx.isSplitTransaction()) {
                        transactionSplitService.saveSplits(saved.getId(), splits);
                    }
                    // Index in FTS ΓÇö importedTx has plain-text description (payee) and memo
                    // (notes)
                    transactionService.syncTransactionFts(
                            saved, importedTx.getPayee(), importedTx.getMemo());
                    imported++;
                } catch (Exception e) {
                    log.error("Error saving transaction: {}", e.getMessage(), e);
                    saveFailed++;
                }
            }

            // REQ-2.2.5: Recalculate account balance after successful import
            try {
                accountService.recalculateBalance(targetAccountId, userId);
            } catch (Exception e) {
                log.error(
                        "Failed to recalculate balance for account {} after import: {}",
                        targetAccountId,
                        e.getMessage());
            }

            // Ensure opening_date <= earliest imported transaction date so backfill works
            try {
                toImport.stream()
                        .map(ImportedTransaction::getTransactionDate)
                        .filter(java.util.Objects::nonNull)
                        .min(java.util.Comparator.naturalOrder())
                        .ifPresent(
                                earliestTxDate -> {
                                    java.time.LocalDate currentOpening = account.getOpeningDate();
                                    if (currentOpening == null
                                            || earliestTxDate.isBefore(currentOpening)) {
                                        account.setOpeningDate(earliestTxDate);
                                        accountRepository.save(account);
                                        log.info(
                                                "Updated opening_date for account {} from {} to {}",
                                                targetAccountId,
                                                currentOpening,
                                                earliestTxDate);
                                    }
                                });
            } catch (Exception e) {
                log.warn(
                        "Failed to update opening_date for account {} after import: {}",
                        targetAccountId,
                        e.getMessage());
            }

            // Update session with results
            int duplicatesSkipped = skipDuplicates ? duplicateTxs.size() : 0;
            int ruleSkipped =
                    (int)
                            transactions.stream()
                                    .filter(ImportedTransaction::isSkippedByRule)
                                    .count();
            session.setImportedCount(imported);
            session.setDuplicateCount(duplicateTxs.size());
            session.setErrorCount(errorTxs.size() + saveFailed);
            session.setSkippedCount(
                    duplicatesSkipped
                            + ruleSkipped
                            + errorTxs.size()
                            + saveFailed
                            + openingBalanceTxs.size());
            session.setStatus(ImportStatus.COMPLETED);
            operationHistoryService.record(
                    session.getUserId(),
                    EntityType.IMPORT,
                    session.getId(),
                    session.getFileName(),
                    OperationType.CREATE,
                    (Object) null,
                    null);
            session.setCompletedAt(LocalDateTime.now());

            log.info(
                    "Import complete: {} imported, {} duplicates, {} errors, {} skipped",
                    imported,
                    duplicateTxs.size(),
                    session.getErrorCount(),
                    session.getSkippedCount());

            // Transparently invalidate net worth snapshots affected by imported transaction
            // dates.
            try {
                toImport.stream()
                        .map(ImportedTransaction::getTransactionDate)
                        .filter(d -> d != null)
                        .reduce((a, b) -> a.isAfter(b) ? a : b)
                        .ifPresent(
                                maxDate -> {
                                    netWorthRepository.deleteByUserIdAndSnapshotDateBefore(
                                            userId, maxDate);
                                    log.debug(
                                            "Invalidated net worth snapshots for user {} after import (cutoff: {})",
                                            userId,
                                            maxDate);
                                });
            } catch (Exception e) {
                log.warn(
                        "Failed to invalidate net worth snapshots after import for user {}: {}",
                        userId,
                        e.getMessage());
            }

            importSessionRepository.save(session);
            budgetAlertService.checkBudgetAlertsAfterTransaction(userId);

            return session;

        } catch (Exception e) {
            log.error("Error confirming import for session {}: {}", sessionId, e.getMessage(), e);
            session.setStatus(ImportStatus.FAILED);
            session.setErrorMessage("Failed to import transactions: " + e.getMessage());
            importSessionRepository.save(session);
            throw new RuntimeException("Failed to confirm import", e);
        }
    }

    /**
     * Cancel an import session.
     *
     * @param sessionId the import session ID
     * @param userId the user ID (for authorization)
     * @return the cancelled session
     */
    @Transactional
    public ImportSession cancelImport(Long sessionId, Long userId) {
        log.info("Cancelling import for session: {}", sessionId);

        ImportSession session = getSessionForUser(sessionId, userId);

        if (!session.isCancellable()) {
            throw new IllegalStateException(
                    "Session cannot be cancelled. Current status: " + session.getStatus());
        }

        session.setStatus(ImportStatus.CANCELLED);
        session.setCompletedAt(LocalDateTime.now());
        importSessionRepository.save(session);

        log.info("Import session cancelled: {}", sessionId);

        return session;
    }

    /**
     * Get import session by ID with user authorization check.
     *
     * @param sessionId the session ID
     * @param userId the user ID
     * @return the import session
     */
    @Transactional(readOnly = true)
    public ImportSession getSession(Long sessionId, Long userId) {
        return getSessionForUser(sessionId, userId);
    }

    /**
     * Get all import sessions for a user.
     *
     * @param userId the user ID
     * @return list of import sessions ordered by creation date descending
     */
    @Transactional(readOnly = true)
    public List<ImportSession> getUserSessions(Long userId) {
        return importSessionRepository.findByUserIdOrderByCreatedAtDesc(userId);
    }

    /**
     * Get incomplete import sessions for a user.
     *
     * @param userId the user ID
     * @return list of incomplete sessions
     */
    @Transactional(readOnly = true)
    public List<ImportSession> getIncompleteSessions(Long userId) {
        return importSessionRepository.findIncompleteByUserId(userId);
    }

    // ========================================
    // Private Helper Methods
    // ========================================

    /** Get session and verify user ownership. */
    private ImportSession getSessionForUser(Long sessionId, Long userId) {
        ImportSession session =
                importSessionRepository
                        .findById(sessionId)
                        .orElseThrow(
                                () ->
                                        new ResourceNotFoundException(
                                                "Import session not found: " + sessionId));

        if (!session.getUserId().equals(userId)) {
            throw new IllegalArgumentException("User does not have access to this import session");
        }

        return session;
    }

    /** Detect file format from filename and content. */
    private String detectFileFormat(String fileName, String uploadId) {
        String extension = fileName.substring(fileName.lastIndexOf('.') + 1).toLowerCase();

        // Validate extension
        if (!List.of("qif", "ofx", "qfx", "csv", "json").contains(extension)) {
            throw new IllegalArgumentException("Unsupported file extension: " + extension);
        }

        // QFX is essentially OFX format
        if ("qfx".equals(extension)) {
            return "OFX";
        }

        return extension.toUpperCase();
    }

    /**
     * Detect duplicate transactions using a tiered strategy:
     *
     * <ol>
     *   <li><strong>Tier 0 ΓÇö Intra-session reference match</strong>: If the incoming transaction
     *       carries a non-blank {@code referenceNumber}, check whether any <em>earlier</em>
     *       transaction in the same import batch shares the same reference for the same account.
     *       This catches the common case of importing the same OFX/QFX file a second time before
     *       the first import is confirmed.
     *   <li><strong>Tier 1 ΓÇö DB exact reference match</strong>: If the incoming transaction has a
     *       non-blank {@code referenceNumber}, look for an existing persisted transaction on the
     *       same account with the same {@code externalReference}. A match is authoritative ΓÇö no
     *       fuzzy fallback is needed.
     *   <li><strong>Tier 2 ΓÇö Fuzzy date/amount/payee match</strong>: Fall back to the classic
     *       heuristic: date ┬▒1 day, same absolute amount (using {@link BigDecimal#compareTo} to
     *       ignore scale), and ΓëÑ85 % payee similarity via Levenshtein distance. Used for QIF
     *       files and any file where no reference is available.
     * </ol>
     *
     * <p>When a duplicate is detected the transaction is annotated with a {@code DUPLICATE:}
     * validation message <em>and</em> the {@code potentialDuplicate} flag is set to {@code true} so
     * that callers can inspect either surface.
     *
     * @param transactions the imported transactions to check
     * @param accountId the target account (detection is skipped when {@code null})
     * @param userId the user ID (currently reserved for future per-user scoping)
     */
    /**
     * Detect duplicate transactions using a tiered strategy.
     *
     * <p><strong>Tier 0 and Tier 1 are only active for formats that produce globally-unique
     * transaction IDs</strong> (currently OFX/QFX via FITID). QIF {@code N} fields are cheque
     * numbers ΓÇö not unique IDs ΓÇö and CSV reference columns are bank-dependent and unreliable.
     * Using them as authoritative keys would produce false positives (e.g. two different
     * transactions sharing check number "1042") and false negatives (skipping Tier 2 for a QIF
     * transaction that is in fact a fuzzy duplicate). See {@link
     * #isReferenceAuthoritative(String)}.
     *
     * <ol>
     *   <li><strong>Tier 0 ΓÇö Intra-session reference match</strong> (OFX/QFX only): If two
     *       transactions in the same import batch share the same {@code referenceNumber} (FITID),
     *       the second is a definite duplicate. Catches the "import same file twice before
     *       confirming" case.
     *   <li><strong>Tier 1 ΓÇö DB exact reference match</strong> (OFX/QFX only): If the incoming
     *       transaction's FITID matches an {@code externalReference} already stored in the DB for
     *       this account, it is a definite duplicate. No fuzzy fallback is needed.
     *   <li><strong>Tier 2 ΓÇö Fuzzy date/amount/payee match</strong> (all formats): Date ┬▒1 day,
     *       same absolute amount ({@link BigDecimal#compareTo} to handle scale), and ΓëÑ85 % payee
     *       similarity via Levenshtein. Always runs for QIF and CSV; also runs for OFX/QFX
     *       transactions that have no reference number or that survived Tier 0/1.
     * </ol>
     *
     * <p>When a duplicate is detected the {@code DUPLICATE:} message is added to {@code
     * validationErrors} <em>and</em> {@code potentialDuplicate} is set to {@code true}.
     *
     * @param transactions the imported transactions to check
     * @param accountId the target account (detection is skipped when {@code null})
     * @param fileFormat the import file format (e.g. {@code "OFX"}, {@code "QIF"}, {@code "CSV"})
     *     ΓÇö controls whether Tier 0/1 are active
     * @param userId the user ID (reserved for future per-user scoping)
     */
    private void detectDuplicates(
            List<ImportedTransaction> transactions,
            Long accountId,
            String fileFormat,
            Long userId) {
        boolean useReferences = isReferenceAuthoritative(fileFormat);
        Map<String, Long> importedAccounts =
                duplicateAccountScopes(transactions, accountId, userId);
        List<Transaction> existing =
                importedAccounts.isEmpty()
                        ? accountId == null
                                ? List.of()
                                : transactionRepository.findByAccountId(accountId)
                        : transactionRepository.findByUserId(userId);
        Map<String, Integer> seenReferences = new HashMap<>();
        for (int index = 0; index < transactions.size(); index++) {
            ImportedTransaction tx = transactions.get(index);
            tx.setPotentialDuplicate(false);
            tx.setValidationErrors(
                    tx.getValidationErrors().stream()
                            .filter(error -> !error.startsWith("DUPLICATE:"))
                            .collect(Collectors.toList()));
            if (tx.getTransactionDate() == null || tx.getAmount() == null) continue;
            String importedKey =
                    buildImportedAccountKey(tx.getAccountName(), tx.getAccountNumber());
            Long targetId = importedKey == null ? accountId : importedAccounts.get(importedKey);
            String scope = targetId == null ? "import:" + importedKey : "account:" + targetId;
            String reference = tx.getReferenceNumber();
            boolean authoritative =
                    useReferences && !Boolean.FALSE.equals(tx.getAuthoritativeReference());
            if (authoritative && reference != null && !reference.isBlank()) {
                Integer first = seenReferences.putIfAbsent(scope + "|" + reference, index);
                if (first != null) {
                    markDuplicate(
                            tx,
                            "DUPLICATE: Same reference number '"
                                    + reference
                                    + "' already appears in this account's import (transaction #"
                                    + (first + 1)
                                    + ")");
                    continue;
                }
            }
            if (targetId == null)
                continue; // A new/unselected account has no existing transactions.
            for (Transaction candidate : existing) {
                if (targetId.equals(candidate.getAccountId())
                        && ImportDuplicateMatcher.matches(
                                tx, candidate, authoritative, exchangeRateService)) {
                    String message =
                            authoritative
                                            && reference != null
                                            && reference.equals(candidate.getExternalReference())
                                    ? "DUPLICATE: Transaction with reference '"
                                            + reference
                                            + "' has already been imported into this account"
                                    : "DUPLICATE: Possible duplicate of transaction on "
                                            + candidate.getDate()
                                            + " with description: "
                                            + candidate.getDescription();
                    markDuplicate(tx, message);
                    break;
                }
            }
        }
    }

    private Map<String, Long> duplicateAccountScopes(
            List<ImportedTransaction> transactions, Long fallbackAccountId, Long userId) {
        Map<String, ImportedAccountDescriptor> descriptors =
                collectImportedAccounts(transactions, null);
        if (descriptors.isEmpty()) return Map.of();
        List<Account> accounts = accountRepository.findByUserId(userId);
        Map<String, Long> scopes = new HashMap<>();
        for (ImportedAccountDescriptor descriptor : descriptors.values()) {
            Account existing = findMatchingAccount(accounts, descriptor);
            scopes.put(
                    descriptor.key(),
                    existing != null
                            ? existing.getId()
                            : descriptors.size() == 1 ? fallbackAccountId : null);
        }
        return scopes;
    }

    /**
     * Returns {@code true} when the given file format produces account-scoped transaction IDs that
     * are safe to use as authoritative duplicate-detection keys (Tier 0 intra-session and Tier 1 DB
     * exact-match).
     *
     * <p>Only OFX and QFX qualify: their {@code FITID} field is defined by the OFX specification to
     * be unique per financial institution and account.
     *
     * <p>QIF {@code N} fields are paper cheque numbers ΓÇö not unique transaction IDs. CSV
     * reference columns vary wildly by bank export format and may be absent, a cheque number, or an
     * actual unique ID. Neither is safe to use as an authoritative key without format-specific
     * knowledge.
     *
     * @param fileFormat the import file format string (case-insensitive)
     * @return {@code true} for OFX/QFX; {@code false} for QIF, CSV and unknown formats
     */
    private boolean isReferenceAuthoritative(String fileFormat) {
        return "OFX".equalsIgnoreCase(fileFormat) || "QFX".equalsIgnoreCase(fileFormat);
    }

    /**
     * Format a raw account identifier (e.g. a long bank account number) into a friendlier display
     * name. Long numeric IDs are shortened to show only the last 4 digits (e.g. "00011234567890189"
     * ΓåÆ "Account ΓÇóΓÇóΓÇó0189").
     *
     * @param rawId the raw account identifier from the imported file
     * @return a human-readable account name suggestion
     */
    private String formatAccountSuggestion(String rawId) {
        if (rawId == null || rawId.isBlank()) return rawId;
        // If it's a long numeric string (looks like a bank account number), abbreviate
        if (rawId.replaceAll("[^0-9]", "").length() >= 9
                && rawId.replaceAll("[^0-9]", "").equals(rawId.replaceAll("\\s", ""))) {
            String last4 = rawId.substring(Math.max(0, rawId.length() - 4));
            return "Account \u2022\u2022\u2022" + last4;
        }
        return rawId;
    }

    /**
     * Mark an imported transaction as a potential duplicate by adding the supplied {@code
     * DUPLICATE:} message to its validation errors and setting the {@code potentialDuplicate} flag.
     *
     * @param tx the transaction to mark
     * @param message the {@code DUPLICATE:} message to record
     */
    private void markDuplicate(ImportedTransaction tx, String message) {
        tx.addValidationError(message);
        tx.setPotentialDuplicate(true);
    }

    /**
     * Suggest categories for imported transactions using intelligent matching. - Exact match on
     * category name (case-insensitive) - Fuzzy match with 80%+ similarity - Marks unknown
     * categories for user review/creation
     *
     * @param transactions list of imported transactions
     * @param userId the user ID
     *     <p>Requirement: REQ-2.10.3 (Category mapping during import)
     */
    private void prepareUnreviewedTransactions(
            ImportSession session, List<ImportedTransaction> transactions, Long userId) {
        if (Boolean.TRUE.equals(readMetadataMap(session.getMetadata()).get("reviewSaved"))) {
            return;
        }
        // Old sessions may already contain persisted rule output. Do not append its
        // split actions again; all newly parsed sessions remain immutable until save.
        List<ImportedTransaction> unprocessed =
                transactions.stream()
                        .filter(
                                tx ->
                                        tx.getValidationErrors().stream()
                                                .noneMatch(
                                                        error ->
                                                                error.startsWith("RULE_MATCH:")
                                                                        || error.startsWith(
                                                                                "RULE_SKIP:")))
                        .collect(Collectors.toList());
        suggestCategories(unprocessed, userId);
    }

    private void suggestCategories(List<ImportedTransaction> transactions, Long userId) {
        // Preserve the raw imported payee BEFORE rules run — SET_PAYEE actions
        // overwrite
        // tx.payee, so capturing it here (rather than after applyRules) is required to
        // avoid losing the original description.
        for (ImportedTransaction tx : transactions) {
            if (tx.getOriginalPayee() == null) {
                tx.setOriginalPayee(tx.getPayee());
            }
        }

        // Requirement REQ-TR-4.1: Apply transaction rules BEFORE auto-categorization.
        // Rules are evaluated in priority order; first match wins
        // (stop-on-first-match).
        Set<Integer> ruleMatchedIndices = transactionRuleService.applyRules(transactions, userId);

        // Load all user categories
        List<Category> userCategories = categoryRepository.findByUserId(userId);
        Locale locale = LocaleContextHolder.getLocale();

        // Build exact match map using translated display names (matching what the
        // frontend sees)
        // Key: lowercase display name, Value: display name (properly cased)
        Map<String, String> categoryDisplayMap = new HashMap<>();
        Map<String, Category> categoryMapExact = new HashMap<>();
        for (Category cat : userCategories) {
            String displayName = resolveDisplayName(cat, locale);
            categoryMapExact.put(displayName.toLowerCase().trim(), cat);
            categoryDisplayMap.put(cat.getName().toLowerCase().trim(), displayName);
        }

        // Process each transaction
        for (int txIndex = 0; txIndex < transactions.size(); txIndex++) {
            ImportedTransaction tx = transactions.get(txIndex);

            // Requirement REQ-TR-4.2: Skip auto-categorization for rule-matched
            // transactions
            if (ruleMatchedIndices.contains(txIndex)) {
                continue;
            }

            // 1. If the imported file already provides a category, skip
            // auto-categorization and go straight to category matching.
            // File-supplied categories (e.g. QIF L-field, OFX category) are explicit
            // user assignments from the source system and should take precedence over
            // history-based guessing.
            boolean hasFileCategory =
                    tx.getCategory() != null && !tx.getCategory().trim().isEmpty();

            if (!hasFileCategory) {
                // 2. Try Auto-Categorization based on user history (only when no
                // file-supplied category)
                Optional<AutoCategorizationService.Prediction> predictionOpt =
                        autoCategorizationService.predictCategoryAndPayee(tx, userId);

                if (predictionOpt.isPresent()) {
                    AutoCategorizationService.Prediction prediction = predictionOpt.get();
                    tx.setCategory(prediction.suggestedCategoryName());
                    tx.setCategorizationConfidence(prediction.confidenceScore());
                    if ((tx.getPayee() == null || tx.getPayee().isBlank())
                            && prediction.suggestedPayee() != null) {
                        tx.setPayee(prediction.suggestedPayee());
                    }

                    tx.addValidationError(
                            String.format(
                                    "AUTO-MATCH: Category suggested based on past"
                                            + " transaction history (Confidence: %.0f%%)",
                                    prediction.confidenceScore() * 100));
                    log.debug(
                            "Auto-matched transaction: category='{}', payee='{}', confidence={}",
                            prediction.suggestedCategoryName(),
                            prediction.suggestedPayee(),
                            prediction.confidenceScore());
                    continue; // Move to next transaction
                }

                // No auto-match and no file category — nothing more to do
                continue;
            }

            String importedCategory = tx.getCategory().trim();
            String normalizedCategory = importedCategory.toLowerCase().trim();

            // Try exact match first
            if (categoryMapExact.containsKey(normalizedCategory)) {
                Category matched = categoryMapExact.get(normalizedCategory);
                String displayName = resolveDisplayName(matched, locale);
                tx.setCategory(displayName); // Store translated category name
                log.debug(
                        "Exact match: '{}' ΓåÆ category ID {}", importedCategory, matched.getId());
                continue;
            }

            // Try fuzzy match with 80%+ similarity
            Category bestMatch = null;
            double bestSimilarity = 0.0;

            for (Category cat : userCategories) {
                String displayName = resolveDisplayName(cat, locale);
                double similarity =
                        ImportDuplicateMatcher.calculateStringSimilarity(
                                normalizedCategory, displayName.toLowerCase());
                if (similarity > bestSimilarity && similarity >= 0.80) {
                    bestSimilarity = similarity;
                    bestMatch = cat;
                }
            }

            if (bestMatch != null) {
                String displayName = resolveDisplayName(bestMatch, locale);
                tx.setCategory(displayName); // Store translated category name
                tx.addValidationError(
                        String.format(
                                "CATEGORY_SUGGESTION: Imported category '%s' matched to '%s' (%.0f%% similarity)",
                                importedCategory, displayName, bestSimilarity * 100));
                log.debug(
                        "Fuzzy match: '{}' ΓåÆ '{}' ({:.0f}%)",
                        importedCategory, displayName, bestSimilarity * 100);
            } else {
                // No match found - mark for user review/creation
                tx.addValidationError(
                        String.format(
                                "CATEGORY_UNKNOWN: Category '%s' not found. Will be created during import.",
                                importedCategory));
                log.debug("Unknown category: '{}' - will be created", importedCategory);
            }
        }

        // Final tier: AI-based categorization for any remaining uncategorized
        // transactions
        try {
            aiCategorizationService.categorizeWithAI(transactions, userCategories);
        } catch (Exception e) {
            log.warn("AI categorization failed (non-blocking): {}", e.getMessage());
        }
    }

    /**
     * Resolve the display name for a category in the given locale. System categories use their
     * nameKey for i18n; user categories use their stored name.
     */
    private String resolveDisplayName(Category category, Locale locale) {
        if (Boolean.TRUE.equals(category.getIsSystem())
                && category.getNameKey() != null
                && !category.getNameKey().isBlank()) {
            return messageSource.getMessage(
                    category.getNameKey(), null, category.getName(), locale);
        }
        return category.getName();
    }

    /**
     * Auto-create an account for import when none was selected by the user. Uses the session's
     * suggestedAccountName as the account name, falling back to the session's fileName (without
     * extension) if none is available.
     *
     * @param userId the user ID
     * @param session the import session
     * @param encryptionKey the user's encryption key
     * @param initialBalance the starting balance derived from the imported file
     * @param currency the currency derived from the imported file
     * @return the ID of the newly created account
     */
    private Long createAccountForImport(
            Long userId, ImportSession session, BigDecimal initialBalance, String currency) {
        String name = session.getSuggestedAccountName();
        if (name == null || name.trim().isEmpty()) {
            // Fallback: derive name from filename without extension
            name = session.getFileName();
            int dotIndex = name.lastIndexOf('.');
            if (dotIndex > 0) {
                name = name.substring(0, dotIndex);
            }
        }
        name = name.trim();

        log.info(
                "Auto-creating account '{}' for import session {} (user {})",
                name,
                session.getId(),
                userId);

        AccountRequest accountRequest =
                AccountRequest.builder()
                        .name(name.trim())
                        .type(AccountType.CHECKING)
                        .currency(defaultCurrencyProvider.resolve(currency))
                        .initialBalance(initialBalance != null ? initialBalance : BigDecimal.ZERO)
                        .openingDate(LocalDate.now())
                        .build();

        AccountResponse created = accountService.createAccount(userId, accountRequest);
        log.info(
                "Auto-created account id={} name='{}' for import session {}",
                created.getId(),
                name,
                session.getId());
        return created.getId();
    }

    /**
     * Create a new category for the user during import. Determines category type (INCOME/EXPENSE)
     * based on transaction amount.
     *
     * @param categoryName the name of the category to create
     * @param userId the user ID
     * @param transactionType the transaction type (INCOME or EXPENSE)
     * @param encryptionKey the user's encryption key
     * @return the created Category entity
     */
    @SuppressWarnings("unused")
    private Category createCategoryForImport(
            String categoryName,
            Long userId,
            TransactionType transactionType,
            String encryptionKey) {
        log.info(
                "Creating new category '{}' for user {} (type: {})",
                categoryName,
                userId,
                transactionType);

        // Determine category type
        CategoryType categoryType =
                (transactionType == TransactionType.INCOME)
                        ? CategoryType.INCOME
                        : CategoryType.EXPENSE;

        // Create category using builder pattern
        Category category =
                Category.builder()
                        .userId(userId)
                        .name(categoryName)
                        .type(categoryType)
                        .isSystem(false)
                        .icon("tag") // Default icon
                        .color("#6B7280") // Default gray color
                        .build();

        return categoryRepository.save(category);
    }

    /** Check if transaction is marked as duplicate. */
    private boolean isDuplicate(ImportedTransaction tx) {
        return tx.getValidationErrors().stream().anyMatch(error -> error.startsWith("DUPLICATE:"));
    }

    private ImportSession confirmSkroogeImport(
            ImportSession session,
            Long userId,
            Map<String, Long> categoryMappings,
            boolean skipDuplicates,
            List<ImportedTransaction> transactions) {
        session.setStatus(ImportStatus.IMPORTING);
        importSessionRepository.save(session);

        List<ImportedTransaction> errorTxs =
                transactions.stream()
                        .filter(tx -> tx.hasErrors() && !tx.isSkippedByRule())
                        .collect(Collectors.toList());
        List<ImportedTransaction> validTxs =
                transactions.stream().filter(tx -> !tx.hasErrors()).collect(Collectors.toList());
        List<ImportedTransaction> duplicateTxs =
                validTxs.stream().filter(this::isDuplicate).collect(Collectors.toList());
        List<ImportedTransaction> toImport =
                validTxs.stream()
                        .filter(tx -> !skipDuplicates || !isDuplicate(tx))
                        .collect(Collectors.toList());

        SkroogeImportMetadata skroogeMetadata = extractSkroogeMetadata(session.getMetadata());
        Map<Long, Long> institutionIdsBySource = ensureInstitutions(skroogeMetadata, userId);
        Map<Long, Long> accountIdsBySource =
                ensureAccounts(skroogeMetadata, institutionIdsBySource, userId);
        Map<Long, Long> categoryIdsBySource =
                ensureCategories(skroogeMetadata, userId, categoryMappings);

        int imported = 0;
        int saveFailed = 0;
        Set<String> processedTransferGroups = new java.util.HashSet<>();
        Set<Long> affectedAccountIds = new java.util.HashSet<>(accountIdsBySource.values());
        Map<String, List<ImportedTransaction>> transferTransactionsByGroup =
                toImport.stream()
                        .filter(tx -> tx.isTransfer() && tx.getTransferGroupKey() != null)
                        .collect(Collectors.groupingBy(ImportedTransaction::getTransferGroupKey));

        for (ImportedTransaction importedTx : toImport) {
            try {
                if (importedTx.isTransfer()) {
                    if (importedTx.getTransferGroupKey() == null
                            || !processedTransferGroups.add(importedTx.getTransferGroupKey())) {
                        continue;
                    }
                    Long fromAccountId = accountIdsBySource.get(importedTx.getSourceAccountId());
                    Long toAccountId = accountIdsBySource.get(importedTx.getToAccountSourceId());
                    if (fromAccountId == null || toAccountId == null) {
                        throw new IllegalStateException(
                                "Unable to resolve transfer accounts for "
                                        + importedTx.getTransferGroupKey());
                    }
                    saveSkroogeTransferTransactions(
                            transferTransactionsByGroup.get(importedTx.getTransferGroupKey()),
                            importedTx.getTransferGroupKey(),
                            accountIdsBySource,
                            userId,
                            categoryMappings,
                            categoryIdsBySource);
                    imported++;
                    continue;
                }

                Long accountId = accountIdsBySource.get(importedTx.getSourceAccountId());
                if (accountId == null) {
                    throw new IllegalStateException(
                            "Unable to resolve account for source account "
                                    + importedTx.getSourceAccountId());
                }
                Transaction transaction =
                        convertToTransaction(
                                importedTx,
                                accountId,
                                userId,
                                categoryMappings,
                                categoryIdsBySource);
                List<TransactionSplitRequest> splits =
                        prepareImportedSplits(
                                transaction,
                                importedTx,
                                userId,
                                categoryMappings,
                                categoryIdsBySource);
                accountCurrencyService.book(transaction, userId);
                Transaction saved = transactionRepository.save(transaction);
                if (importedTx.isSplitTransaction()) {
                    transactionSplitService.saveSplits(saved.getId(), splits);
                }
                transactionService.syncTransactionFts(
                        saved, importedTx.getPayee(), importedTx.getMemo());
                imported++;
            } catch (Exception ex) {
                log.error(
                        "Error saving Skrooge transaction {}: {}",
                        importedTx.getReferenceNumber(),
                        ex.getMessage(),
                        ex);
                saveFailed++;
            }
        }

        for (Long affectedAccountId : affectedAccountIds) {
            try {
                accountService.recalculateBalance(affectedAccountId, userId);
            } catch (Exception ex) {
                log.warn(
                        "Failed to recalculate balance for account {} after Skrooge import: {}",
                        affectedAccountId,
                        ex.getMessage());
            }
        }

        int duplicatesSkipped = skipDuplicates ? duplicateTxs.size() : 0;
        int ruleSkipped =
                (int) transactions.stream().filter(ImportedTransaction::isSkippedByRule).count();
        session.setImportedCount(imported);
        session.setDuplicateCount(duplicateTxs.size());
        session.setErrorCount(errorTxs.size() + saveFailed);
        session.setSkippedCount(duplicatesSkipped + ruleSkipped + errorTxs.size() + saveFailed);
        session.setStatus(ImportStatus.COMPLETED);
        operationHistoryService.record(
                session.getUserId(),
                EntityType.IMPORT,
                session.getId(),
                session.getFileName(),
                OperationType.CREATE,
                (Object) null,
                null);
        session.setCompletedAt(LocalDateTime.now());
        if (accountIdsBySource.size() == 1) {
            session.setAccountId(accountIdsBySource.values().iterator().next());
        }

        try {
            toImport.stream()
                    .map(ImportedTransaction::getTransactionDate)
                    .filter(date -> date != null)
                    .reduce((left, right) -> left.isAfter(right) ? left : right)
                    .ifPresent(
                            maxDate ->
                                    netWorthRepository.deleteByUserIdAndSnapshotDateBefore(
                                            userId, maxDate));
        } catch (Exception ex) {
            log.warn(
                    "Failed to invalidate net worth snapshots after Skrooge import for user {}: {}",
                    userId,
                    ex.getMessage());
        }

        importSessionRepository.save(session);
        budgetAlertService.checkBudgetAlertsAfterTransaction(userId);
        return session;
    }

    private void saveSkroogeTransferTransactions(
            List<ImportedTransaction> transferTransactions,
            String transferGroupKey,
            Map<Long, Long> accountIdsBySource,
            Long userId,
            Map<String, Long> categoryMappings,
            Map<Long, Long> categoryIdsBySource) {
        if (transferTransactions == null || transferTransactions.isEmpty()) {
            throw new IllegalStateException("No transfer transactions for " + transferGroupKey);
        }

        if (transferTransactions.size() != 2) {
            throw new IllegalStateException(
                    "Expected two transfer transactions for " + transferGroupKey);
        }

        String transferId = java.util.UUID.randomUUID().toString();
        List<Transaction> transactionsToSave = new ArrayList<>();
        for (ImportedTransaction transferTransaction : transferTransactions) {
            Long accountId = accountIdsBySource.get(transferTransaction.getSourceAccountId());
            Long toAccountId = accountIdsBySource.get(transferTransaction.getToAccountSourceId());
            if (accountId == null || toAccountId == null) {
                throw new IllegalStateException(
                        "Unable to resolve transfer accounts for " + transferGroupKey);
            }

            Transaction transaction =
                    convertToTransaction(
                            transferTransaction,
                            accountId,
                            userId,
                            categoryMappings,
                            categoryIdsBySource,
                            false);
            transaction.setTransferId(transferId);
            transaction.setToAccountId(toAccountId);
            transaction.setCategoryId(null);
            transactionsToSave.add(transaction);
        }

        List<Transaction> savedTransactions = new ArrayList<>();
        try {
            for (Transaction transaction : transactionsToSave) {
                accountCurrencyService.book(transaction, userId);
                savedTransactions.add(transactionRepository.save(transaction));
            }
        } catch (RuntimeException ex) {
            if (!savedTransactions.isEmpty()) {
                transactionRepository.deleteAll(savedTransactions);
            }
            throw ex;
        }

        for (int i = 0; i < savedTransactions.size(); i++) {
            ImportedTransaction transferTransaction = transferTransactions.get(i);
            transactionService.syncTransactionFts(
                    savedTransactions.get(i),
                    transferTransaction.getPayee(),
                    transferTransaction.getMemo());
        }
    }

    private ImportSession confirmImportedAccountImport(
            ImportSession session,
            Long userId,
            Long requestedAccountId,
            Map<String, Long> categoryMappings,
            boolean skipDuplicates,
            List<ImportedTransaction> transactions) {
        session.setStatus(ImportStatus.IMPORTING);
        importSessionRepository.save(session);

        List<ImportedTransaction> errorTxs =
                transactions.stream()
                        .filter(tx -> tx.hasErrors() && !tx.isSkippedByRule())
                        .collect(Collectors.toList());
        List<ImportedTransaction> validTxs =
                transactions.stream().filter(tx -> !tx.hasErrors()).collect(Collectors.toList());
        List<ImportedTransaction> duplicateTxs =
                validTxs.stream().filter(this::isDuplicate).collect(Collectors.toList());
        List<ImportedTransaction> toImport =
                validTxs.stream()
                        .filter(tx -> !skipDuplicates || !isDuplicate(tx))
                        .collect(Collectors.toList());

        // Separate opening-balance rows (Skrooge "0000-00-00") — they set the account's
        // opening_balance via the descriptor and must not be persisted as transactions.
        List<ImportedTransaction> openingBalanceTxs =
                toImport.stream()
                        .filter(ImportedTransaction::isOpeningBalance)
                        .collect(Collectors.toList());
        toImport =
                toImport.stream().filter(tx -> !tx.isOpeningBalance()).collect(Collectors.toList());

        String fileCurrency = extractFileCurrency(session.getMetadata(), userId);
        final Long initialFallbackAccountId =
                requestedAccountId != null ? requestedAccountId : session.getAccountId();
        Account fallbackAccount =
                initialFallbackAccountId != null
                        ? accountRepository
                                .findByIdAndUserId(initialFallbackAccountId, userId)
                                .orElseThrow(
                                        () ->
                                                new ResourceNotFoundException(
                                                        "Account not found: "
                                                                + initialFallbackAccountId))
                        : null;

        // Collect account descriptors from both regular and opening-balance
        // transactions so
        // that opening_balance and institution metadata are captured for account
        // creation.
        List<ImportedTransaction> descriptorSource = new ArrayList<>(toImport);
        descriptorSource.addAll(openingBalanceTxs);
        Map<String, ImportedAccountDescriptor> descriptorsByKey =
                collectImportedAccounts(descriptorSource, fileCurrency);
        applyStatementOpeningBalances(descriptorsByKey, transactions, session.getMetadata());
        Map<String, Long> accountIdsByKey =
                ensureImportedAccounts(descriptorsByKey, fallbackAccount, userId);

        Long resolvedFallbackAccountId = initialFallbackAccountId;
        if (resolvedFallbackAccountId == null
                && fallbackAccount == null
                && accountIdsByKey.size() == 1) {
            resolvedFallbackAccountId = accountIdsByKey.values().iterator().next();
        }
        if (resolvedFallbackAccountId == null && requiresFallbackAccount(toImport)) {
            resolvedFallbackAccountId =
                    createAccountForImport(userId, session, BigDecimal.ZERO, fileCurrency);
        }

        int imported = 0;
        int saveFailed = 0;
        Set<Long> affectedAccountIds = new java.util.HashSet<>(accountIdsByKey.values());
        if (resolvedFallbackAccountId != null) {
            affectedAccountIds.add(resolvedFallbackAccountId);
        }
        Set<String> processedTransferKeys = new java.util.HashSet<>();
        ImportedTransferMatcher transferMatcher = new ImportedTransferMatcher();

        for (ImportedTransaction importedTx : toImport) {
            try {
                Long sourceAccountId =
                        resolveImportedAccountId(
                                importedTx, accountIdsByKey, resolvedFallbackAccountId);

                if (importedTx.isTransfer()
                        && importedTx.getToAccountName() != null
                        && !importedTx.getToAccountName().isBlank()) {
                    Long destinationAccountId =
                            resolveImportedDestinationAccountId(
                                    importedTx, accountIdsByKey, resolvedFallbackAccountId);
                    if (sourceAccountId == null || destinationAccountId == null) {
                        throw new IllegalStateException(
                                "Unable to resolve transfer accounts for imported transaction");
                    }

                    String transferKey =
                            transferMatcher.keyFor(
                                    importedTx, sourceAccountId, destinationAccountId);
                    if (!processedTransferKeys.add(transferKey)) {
                        continue;
                    }

                    Long transferSourceAccountId = sourceAccountId;
                    Long transferDestinationAccountId = destinationAccountId;
                    if (importedTx.getAmount().signum() > 0) {
                        transferSourceAccountId = destinationAccountId;
                        transferDestinationAccountId = sourceAccountId;
                    }

                    TransactionRequest transferRequest =
                            TransactionRequest.builder()
                                    .accountId(transferSourceAccountId)
                                    .toAccountId(transferDestinationAccountId)
                                    .type(TransactionType.TRANSFER)
                                    .amount(normalizeAmount(importedTx.getAmount()))
                                    .currency(
                                            resolveTransactionCurrency(
                                                    importedTx, transferSourceAccountId))
                                    .date(importedTx.getTransactionDate())
                                    .description(
                                            truncate(
                                                    importedTx.getPayee(),
                                                    TRANSACTION_DESCRIPTION_MAX_LENGTH))
                                    .notes(
                                            truncate(
                                                    importedTx.getMemo(),
                                                    TRANSACTION_NOTES_MAX_LENGTH))
                                    .payee(
                                            truncate(
                                                    importedTx.getPayee(),
                                                    TRANSACTION_PAYEE_MAX_LENGTH))
                                    .paymentMethod(mapPaymentMethod(importedTx.getPaymentMethod()))
                                    .tags(
                                            importedTx.getTags() != null
                                                            && !importedTx.getTags().isEmpty()
                                                    ? truncate(
                                                            String.join(",", importedTx.getTags()),
                                                            TRANSACTION_TAGS_MAX_LENGTH)
                                                    : null)
                                    .build();
                    transactionService.createTransfer(userId, transferRequest);
                    affectedAccountIds.add(transferSourceAccountId);
                    affectedAccountIds.add(transferDestinationAccountId);
                    imported++;
                    continue;
                }

                if (sourceAccountId == null) {
                    throw new IllegalStateException(
                            "Unable to resolve account for imported transaction");
                }

                Transaction transaction =
                        convertToTransaction(importedTx, sourceAccountId, userId, categoryMappings);
                List<TransactionSplitRequest> splits =
                        prepareImportedSplits(
                                transaction, importedTx, userId, categoryMappings, Map.of());
                accountCurrencyService.book(transaction, userId);
                Transaction saved = transactionRepository.save(transaction);
                if (importedTx.isSplitTransaction()) {
                    transactionSplitService.saveSplits(saved.getId(), splits);
                }
                transactionService.syncTransactionFts(
                        saved, importedTx.getPayee(), importedTx.getMemo());
                affectedAccountIds.add(sourceAccountId);
                imported++;
            } catch (Exception ex) {
                log.error(
                        "Error saving imported transaction {}: {}",
                        importedTx.getReferenceNumber(),
                        ex.getMessage(),
                        ex);
                saveFailed++;
            }
        }

        for (Long affectedAccountId : affectedAccountIds) {
            try {
                accountService.recalculateBalance(affectedAccountId, userId);
            } catch (Exception ex) {
                log.warn(
                        "Failed to recalculate balance for account {} after multi-account import: {}",
                        affectedAccountId,
                        ex.getMessage());
            }
        }

        int duplicatesSkipped = skipDuplicates ? duplicateTxs.size() : 0;
        int ruleSkipped =
                (int) transactions.stream().filter(ImportedTransaction::isSkippedByRule).count();
        session.setImportedCount(imported);
        session.setDuplicateCount(duplicateTxs.size());
        session.setErrorCount(errorTxs.size() + saveFailed);
        session.setSkippedCount(duplicatesSkipped + ruleSkipped + errorTxs.size() + saveFailed);
        session.setStatus(ImportStatus.COMPLETED);
        operationHistoryService.record(
                session.getUserId(),
                EntityType.IMPORT,
                session.getId(),
                session.getFileName(),
                OperationType.CREATE,
                (Object) null,
                null);
        session.setCompletedAt(LocalDateTime.now());

        Set<Long> resolvedAccountIds = new java.util.HashSet<>(affectedAccountIds);
        if (resolvedAccountIds.size() == 1) {
            session.setAccountId(resolvedAccountIds.iterator().next());
        }

        try {
            toImport.stream()
                    .map(ImportedTransaction::getTransactionDate)
                    .filter(date -> date != null)
                    .reduce((left, right) -> left.isAfter(right) ? left : right)
                    .ifPresent(
                            maxDate ->
                                    netWorthRepository.deleteByUserIdAndSnapshotDateBefore(
                                            userId, maxDate));
        } catch (Exception ex) {
            log.warn(
                    "Failed to invalidate net worth snapshots after multi-account import for user {}: {}",
                    userId,
                    ex.getMessage());
        }

        importSessionRepository.save(session);
        budgetAlertService.checkBudgetAlertsAfterTransaction(userId);
        return session;
    }

    private Map<Long, Long> ensureInstitutions(SkroogeImportMetadata metadata, Long userId) {
        Map<Long, Long> institutionIdsBySource = new HashMap<>();
        List<Institution> existingInstitutions =
                new ArrayList<>(institutionRepository.findAllByUser(userId));
        for (SkroogeImportMetadata.SkroogeInstitution institution : metadata.getInstitutions()) {
            String rawName = institution.getName();
            final String institutionName =
                    (rawName == null || rawName.isBlank())
                            ? "Institution " + institution.getSourceId()
                            : rawName;
            if (rawName == null || rawName.isBlank()) {
                log.info(
                        "Using fallback name for Skrooge institution {} (no usable name in export)",
                        institution.getSourceId());
            }
            Institution existing =
                    findMatchingInstitution(existingInstitutions, institutionName)
                            .orElseGet(
                                    () -> {
                                        Institution created =
                                                institutionRepository.save(
                                                        Institution.builder()
                                                                .name(institutionName)
                                                                .country(institution.getCountry())
                                                                .logo(
                                                                        sanitizeLogo(
                                                                                institution
                                                                                        .getLogo()))
                                                                .isSystem(false)
                                                                .userId(userId)
                                                                .build());
                                        existingInstitutions.add(created);
                                        return created;
                                    });
            institutionIdsBySource.put(institution.getSourceId(), existing.getId());
        }
        return institutionIdsBySource;
    }

    /**
     * Find an existing institution (system or the user's own) that best matches the given name.
     *
     * <p>Names are compared as normalized slugs (accent-stripped, lower-case, alphanumeric only) so
     * Skrooge export names such as "hellobank" or "boursorama banque" resolve to the seeded "Hello
     * bank!"/ "Boursorama" institutions instead of creating duplicates with broken logos. Falls
     * back to a containment match (the longest existing slug fully contained in the imported slug)
     * for names that append a qualifier, e.g. "boursorama banque" &rarr; "boursorama".
     */
    private Optional<Institution> findMatchingInstitution(
            List<Institution> existingInstitutions, String name) {
        String slug = normalizeInstitutionName(name);
        if (slug.isEmpty()) {
            return Optional.empty();
        }
        Optional<Institution> exact =
                existingInstitutions.stream()
                        .filter(
                                candidate ->
                                        candidate.getName() != null
                                                && normalizeInstitutionName(candidate.getName())
                                                        .equals(slug))
                        .findFirst();
        if (exact.isPresent()) {
            return exact;
        }
        return existingInstitutions.stream()
                .filter(
                        candidate -> {
                            String candidateSlug = normalizeInstitutionName(candidate.getName());
                            return candidateSlug.length() >= MIN_CONTAINMENT_SLUG_LENGTH
                                    && slug.length() > candidateSlug.length()
                                    && slug.contains(candidateSlug);
                        })
                .max(Comparator.comparingInt(this::normalizedNameLength));
    }

    /**
     * Normalize an institution name into a comparison slug: accent-stripped, lower-cased
     * (locale-independent), and reduced to alphanumerics. "Hello bank!" and "hellobank" both map to
     * "hellobank".
     */
    static String normalizeInstitutionName(String name) {
        if (name == null) {
            return "";
        }
        return java.text.Normalizer.normalize(name, java.text.Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")
                .toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9]", "");
    }

    private int normalizedNameLength(Institution institution) {
        return normalizeInstitutionName(institution.getName()).length();
    }

    /**
     * Keep only logos the frontend can actually render: data URIs, absolute http(s) URLs, or the
     * bundled {@code /logos/...} paths. Skrooge exports carry bare icon filenames ("hellobank.png")
     * or host-specific absolute paths ("/usr/share/skrooge/images/logo/...") which are unusable, so
     * they are dropped in favor of the frontend placeholder.
     */
    static String sanitizeLogo(String rawLogo) {
        if (rawLogo == null) {
            return null;
        }
        String trimmed = rawLogo.trim();
        if (trimmed.isEmpty()) {
            return null;
        }
        for (String prefix : DISPLAYABLE_LOGO_PREFIXES) {
            if (trimmed.startsWith(prefix)) {
                return trimmed;
            }
        }
        return null;
    }

    /**
     * Resolve an institution by name (case-insensitive), creating it if necessary.
     *
     * <p>Used by the CSV import path to link accounts to institutions from the "bank" column.
     * Returns null when the name is blank or null.
     *
     * @param name institution name from the import file
     * @param userId the authenticated user's ID
     * @return institution ID, or null when no name is provided
     */
    private Long resolveInstitutionIdByName(String name, Long userId) {
        if (name == null || name.isBlank()) {
            return null;
        }
        String trimmed = name.trim();
        List<Institution> existing = new ArrayList<>(institutionRepository.findAllByUser(userId));
        Institution match =
                findMatchingInstitution(existing, trimmed)
                        .orElseGet(
                                () -> {
                                    Institution created =
                                            institutionRepository.save(
                                                    Institution.builder()
                                                            .name(trimmed)
                                                            .isSystem(false)
                                                            .userId(userId)
                                                            .build());
                                    existing.add(created);
                                    return created;
                                });
        return match.getId();
    }

    private Map<Long, Long> ensureAccounts(
            SkroogeImportMetadata metadata, Map<Long, Long> institutionIdsBySource, Long userId) {
        Map<Long, Long> accountIdsBySource = new HashMap<>();
        List<Account> existingAccounts = accountRepository.findByUserId(userId);
        for (SkroogeImportMetadata.SkroogeAccount account : metadata.getAccounts()) {
            Account matchingAccount = findMatchingAccount(existingAccounts, account);
            if (matchingAccount == null) {
                AccountRequest accountRequest =
                        AccountRequest.builder()
                                .name(account.getName())
                                .type(
                                        account.getAccountType() != null
                                                ? account.getAccountType()
                                                : AccountType.OTHER)
                                .currency(defaultCurrencyProvider.resolve(account.getCurrency()))
                                .initialBalance(
                                        account.getOpeningBalance() != null
                                                ? account.getOpeningBalance()
                                                : BigDecimal.ZERO)
                                .openingDate(account.getOpeningDate())
                                .description(account.getDescription())
                                .accountNumber(account.getAccountNumber())
                                .institutionId(
                                        account.getSourceInstitutionId() != null
                                                ? institutionIdsBySource.get(
                                                        account.getSourceInstitutionId())
                                                : null)
                                .build();
                AccountResponse created = accountService.createAccount(userId, accountRequest);
                matchingAccount =
                        accountRepository
                                .findById(created.getId())
                                .orElseThrow(
                                        () ->
                                                new ResourceNotFoundException(
                                                        "Account not found: " + created.getId()));
                existingAccounts.add(matchingAccount);
            }
            applyImportedAccountActiveState(matchingAccount, account);
            accountIdsBySource.put(account.getSourceId(), matchingAccount.getId());
        }
        return accountIdsBySource;
    }

    private void applyImportedAccountActiveState(
            Account matchingAccount, SkroogeImportMetadata.SkroogeAccount importedAccount) {
        if (matchingAccount == null || importedAccount.getActive() == null) {
            return;
        }
        if (!Objects.equals(matchingAccount.getIsActive(), importedAccount.getActive())) {
            matchingAccount.setIsActive(importedAccount.getActive());
            accountRepository.save(matchingAccount);
        }
    }

    private Account findMatchingAccount(
            List<Account> existingAccounts, SkroogeImportMetadata.SkroogeAccount importedAccount) {
        for (Account existingAccount : existingAccounts) {
            if (matchesImportedAccount(
                    existingAccount,
                    importedAccount.getName(),
                    importedAccount.getAccountNumber())) {
                return existingAccount;
            }
        }
        return null;
    }

    private void applyStatementOpeningBalances(
            Map<String, ImportedAccountDescriptor> descriptors,
            List<ImportedTransaction> transactions,
            String metadata) {
        if (metadata == null || !metadata.contains("\"ledgerBalances\"")) return;
        try {
            com.fasterxml.jackson.databind.JsonNode root =
                    objectMapper
                            .reader()
                            .with(DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS)
                            .readTree(metadata);
            com.fasterxml.jackson.databind.JsonNode balances = root.path("ledgerBalances");
            com.fasterxml.jackson.databind.JsonNode originalNets = root.path("statementNetAmounts");
            for (Map.Entry<String, ImportedAccountDescriptor> entry : descriptors.entrySet()) {
                ImportedAccountDescriptor descriptor = entry.getValue();
                String statementId =
                        descriptor.accountNumber() == null
                                ? descriptor.name()
                                : descriptor.accountNumber();
                if (statementId == null || !balances.hasNonNull(statementId)) continue;
                BigDecimal closing = balances.get(statementId).decimalValue();
                BigDecimal net =
                        transactions.stream()
                                .filter(
                                        tx ->
                                                entry.getKey()
                                                        .equals(
                                                                buildImportedAccountKey(
                                                                        tx.getAccountName(),
                                                                        tx.getAccountNumber())))
                                .map(ImportedTransaction::getAmount)
                                .filter(Objects::nonNull)
                                .reduce(BigDecimal.ZERO, BigDecimal::add);
                if (originalNets.has(entry.getKey())) {
                    if (originalNets.get(entry.getKey()).isNull()) continue;
                    net = originalNets.get(entry.getKey()).decimalValue();
                }
                entry.setValue(
                        new ImportedAccountDescriptor(
                                descriptor.key(),
                                descriptor.name(),
                                descriptor.accountNumber(),
                                descriptor.currency(),
                                descriptor.openingDate(),
                                descriptor.qifAccountType(),
                                descriptor.institutionName(),
                                closing.subtract(net)));
            }
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Invalid import metadata", e);
        }
    }

    /** Preserve statement arithmetic before rules or review edits remove/change movements. */
    private Map<String, BigDecimal> statementNetAmounts(List<ImportedTransaction> transactions) {
        Map<String, BigDecimal> amounts = new HashMap<>();
        for (ImportedTransaction tx : transactions) {
            String key =
                    Objects.toString(
                            buildImportedAccountKey(tx.getAccountName(), tx.getAccountNumber()),
                            "");
            if (tx.getAmount() == null) {
                amounts.put(
                        key, null); // An incomplete statement cannot establish an opening balance.
            } else if (!amounts.containsKey(key) || amounts.get(key) != null) {
                amounts.merge(key, tx.getAmount(), BigDecimal::add);
            }
        }
        return amounts;
    }

    private boolean shouldUseImportedAccountRouting(List<ImportedTransaction> transactions) {
        return transactions.stream().anyMatch(this::hasImportedAccountReference);
    }

    private boolean hasImportedAccountReference(ImportedTransaction transaction) {
        return (transaction.getAccountName() != null && !transaction.getAccountName().isBlank())
                || (transaction.getAccountNumber() != null
                        && !transaction.getAccountNumber().isBlank())
                || (transaction.getToAccountName() != null
                        && !transaction.getToAccountName().isBlank());
    }

    private Map<String, ImportedAccountDescriptor> collectImportedAccounts(
            List<ImportedTransaction> transactions, String defaultCurrency) {
        Map<String, ImportedAccountDescriptor> descriptors = new LinkedHashMap<>();
        for (ImportedTransaction transaction : transactions) {
            registerImportedAccountDescriptor(
                    descriptors,
                    transaction.getAccountName(),
                    transaction.getAccountNumber(),
                    transaction.getCurrency(),
                    transaction.getTransactionDate(),
                    defaultCurrency,
                    transaction.getQifAccountType(),
                    transaction.getInstitutionName(),
                    transaction.isOpeningBalance() ? transaction.getAmount() : null);
            registerImportedAccountDescriptor(
                    descriptors,
                    transaction.getToAccountName(),
                    null,
                    null,
                    transaction.getTransactionDate(),
                    defaultCurrency,
                    null,
                    null,
                    null);
        }
        return descriptors;
    }

    private void registerImportedAccountDescriptor(
            Map<String, ImportedAccountDescriptor> descriptors,
            String accountName,
            String accountNumber,
            String currency,
            LocalDate transactionDate,
            String defaultCurrency,
            String qifAccountType,
            String institutionName,
            BigDecimal openingBalance) {
        String descriptorKey = buildImportedAccountKey(accountName, accountNumber);
        if (descriptorKey == null) {
            return;
        }

        ImportedAccountDescriptor existing = descriptors.get(descriptorKey);
        String resolvedName =
                accountName != null && !accountName.isBlank()
                        ? accountName.trim()
                        : existing != null && existing.name() != null
                                ? existing.name()
                                : formatAccountSuggestion(accountNumber);
        String resolvedNumber =
                accountNumber != null && !accountNumber.isBlank()
                        ? accountNumber.trim()
                        : existing != null ? existing.accountNumber() : null;
        String resolvedCurrency =
                currency != null && !currency.isBlank()
                        ? currency.trim()
                        : existing != null && existing.currency() != null
                                ? existing.currency()
                                : defaultCurrency;
        LocalDate openingDate =
                existing != null && existing.openingDate() != null
                        ? existing.openingDate()
                        : transactionDate;
        if (openingDate != null
                && transactionDate != null
                && transactionDate.isBefore(openingDate)) {
            openingDate = transactionDate;
        }
        String resolvedQifAccountType =
                qifAccountType != null
                        ? qifAccountType
                        : existing != null ? existing.qifAccountType() : null;
        String resolvedInstitution =
                institutionName != null && !institutionName.isBlank()
                        ? institutionName.trim()
                        : existing != null ? existing.institutionName() : null;
        BigDecimal resolvedOpeningBalance =
                openingBalance != null
                        ? openingBalance
                        : existing != null ? existing.openingBalance() : null;

        descriptors.put(
                descriptorKey,
                new ImportedAccountDescriptor(
                        descriptorKey,
                        resolvedName,
                        resolvedNumber,
                        resolvedCurrency,
                        openingDate,
                        resolvedQifAccountType,
                        resolvedInstitution,
                        resolvedOpeningBalance));
    }

    private Map<String, Long> ensureImportedAccounts(
            Map<String, ImportedAccountDescriptor> descriptorsByKey,
            Account fallbackAccount,
            Long userId) {
        Map<String, Long> accountIdsByKey = new HashMap<>();
        List<Account> existingAccounts = accountRepository.findByUserId(userId);
        for (ImportedAccountDescriptor descriptor : descriptorsByKey.values()) {
            Account matchingAccount =
                    fallbackAccount != null
                                    && matchesImportedAccount(
                                            fallbackAccount,
                                            descriptor.name(),
                                            descriptor.accountNumber())
                            ? fallbackAccount
                            : findMatchingAccount(existingAccounts, descriptor);
            if (matchingAccount == null
                    && fallbackAccount != null
                    && descriptorsByKey.size() == 1) {
                matchingAccount = fallbackAccount;
            }
            if (matchingAccount == null) {
                Long institutionId =
                        resolveInstitutionIdByName(descriptor.institutionName(), userId);
                AccountRequest accountRequest =
                        AccountRequest.builder()
                                .name(descriptor.name())
                                .type(mapQifAccountType(descriptor.qifAccountType()))
                                .currency(defaultCurrencyProvider.resolve(descriptor.currency()))
                                .initialBalance(
                                        descriptor.openingBalance() != null
                                                ? descriptor.openingBalance()
                                                : BigDecimal.ZERO)
                                .openingDate(
                                        descriptor.openingDate() != null
                                                ? descriptor.openingDate()
                                                : LocalDate.now())
                                .accountNumber(descriptor.accountNumber())
                                .institutionId(institutionId)
                                .build();
                AccountResponse created = accountService.createAccount(userId, accountRequest);
                matchingAccount =
                        accountRepository
                                .findById(created.getId())
                                .orElseThrow(
                                        () ->
                                                new ResourceNotFoundException(
                                                        "Account not found: " + created.getId()));
                existingAccounts.add(matchingAccount);
            } else {
                openingBalanceService.reconcile(
                        matchingAccount,
                        descriptor.openingBalance(),
                        descriptor.currency(),
                        descriptor.openingDate(),
                        userId);
            }
            accountIdsByKey.put(descriptor.key(), matchingAccount.getId());
        }
        return accountIdsByKey;
    }

    private Account findMatchingAccount(
            List<Account> existingAccounts, ImportedAccountDescriptor descriptor) {
        for (Account existingAccount : existingAccounts) {
            if (matchesImportedAccount(
                    existingAccount, descriptor.name(), descriptor.accountNumber())) {
                return existingAccount;
            }
        }
        return null;
    }

    private boolean matchesImportedAccount(
            Account existingAccount, String importedName, String importedAccountNumber) {
        String existingName = existingAccount.getName();
        String existingAccountNumber = existingAccount.getAccountNumber();
        boolean sameName =
                existingName != null
                        && importedName != null
                        && existingName.equalsIgnoreCase(importedName);
        boolean sameNumber =
                importedAccountNumber != null
                        && !importedAccountNumber.isBlank()
                        && existingAccountNumber != null
                        && existingAccountNumber.equalsIgnoreCase(importedAccountNumber);
        return sameName || sameNumber;
    }

    private String buildImportedAccountKey(String accountName, String accountNumber) {
        if (accountNumber != null && !accountNumber.isBlank()) {
            return "number:" + accountNumber.trim().replaceAll("[^a-zA-Z0-9]", "").toLowerCase();
        }
        if (accountName != null && !accountName.isBlank()) {
            return "name:" + accountName.trim().replaceAll("[^a-zA-Z0-9]", "").toLowerCase();
        }
        return null;
    }

    private Long resolveImportedAccountId(
            ImportedTransaction importedTx,
            Map<String, Long> accountIdsByKey,
            Long fallbackAccountId) {
        String descriptorKey =
                buildImportedAccountKey(importedTx.getAccountName(), importedTx.getAccountNumber());
        if (descriptorKey != null && accountIdsByKey.containsKey(descriptorKey)) {
            return accountIdsByKey.get(descriptorKey);
        }
        return fallbackAccountId;
    }

    private Long resolveImportedDestinationAccountId(
            ImportedTransaction importedTx,
            Map<String, Long> accountIdsByKey,
            Long fallbackAccountId) {
        String descriptorKey = buildImportedAccountKey(importedTx.getToAccountName(), null);
        if (descriptorKey != null && accountIdsByKey.containsKey(descriptorKey)) {
            return accountIdsByKey.get(descriptorKey);
        }
        return fallbackAccountId;
    }

    private boolean requiresFallbackAccount(List<ImportedTransaction> transactions) {
        return transactions.stream()
                .anyMatch(
                        transaction ->
                                !transaction.isTransfer()
                                        && (transaction.getAccountName() == null
                                                || transaction.getAccountName().isBlank())
                                        && (transaction.getAccountNumber() == null
                                                || transaction.getAccountNumber().isBlank()));
    }

    private String decryptQuietly(String value) {
        return value;
    }

    private Map<Long, Long> ensureCategories(
            SkroogeImportMetadata metadata, Long userId, Map<String, Long> categoryMappings) {
        Map<Long, Long> categoryIdsBySource = new HashMap<>();
        List<Category> existingCategories = categoryRepository.findByUserId(userId);
        Map<Long, String> existingPathsById = buildCategoryPaths(existingCategories);

        for (SkroogeImportMetadata.SkroogeCategory category : metadata.getCategories()) {
            Long mappedId =
                    categoryMappings != null ? categoryMappings.get(category.getFullName()) : null;
            if (mappedId == null && categoryMappings != null) {
                mappedId = categoryMappings.get(category.getName());
            }
            if (mappedId != null) {
                categoryIdsBySource.put(category.getSourceId(), mappedId);
                continue;
            }

            Long parentId =
                    category.getParentSourceId() != null
                            ? categoryIdsBySource.get(category.getParentSourceId())
                            : null;
            Category matchingCategory =
                    existingCategories.stream()
                            .filter(
                                    existingCategory ->
                                            existingCategory.getType() == category.getType())
                            .filter(
                                    existingCategory ->
                                            Objects.equals(
                                                    existingCategory.getParentId(), parentId))
                            .filter(
                                    existingCategory ->
                                            category.getName()
                                                    .equalsIgnoreCase(existingCategory.getName()))
                            .findFirst()
                            .orElse(null);

            if (matchingCategory == null) {
                matchingCategory =
                        categoryRepository.save(
                                Category.builder()
                                        .userId(userId)
                                        .name(category.getName())
                                        .type(category.getType())
                                        .parentId(parentId)
                                        .isSystem(false)
                                        .icon("tag")
                                        .color("#6B7280")
                                        .build());
                existingCategories.add(matchingCategory);
                existingPathsById.put(matchingCategory.getId(), category.getFullName());
            }
            categoryIdsBySource.put(category.getSourceId(), matchingCategory.getId());
        }

        return categoryIdsBySource;
    }

    private Map<Long, String> buildCategoryPaths(List<Category> categories) {
        Map<Long, Category> categoriesById =
                categories.stream()
                        .collect(Collectors.toMap(Category::getId, category -> category));
        Map<Long, String> pathsById = new HashMap<>();
        for (Category category : categories) {
            pathsById.put(category.getId(), buildCategoryPath(category, categoriesById));
        }
        return pathsById;
    }

    private String buildCategoryPath(Category category, Map<Long, Category> categoriesById) {
        List<String> segments = new ArrayList<>();
        Category current = category;
        while (current != null) {
            segments.add(0, current.getName());
            current =
                    current.getParentId() != null
                            ? categoriesById.get(current.getParentId())
                            : null;
        }
        return String.join(":", segments);
    }

    /** Validate enriched rows before any posting, including saved review edits. */
    private void validateImportedSplitAmounts(
            List<ImportedTransaction> transactions, Long fallbackAccountId, Long userId) {
        Map<String, Long> scopes = duplicateAccountScopes(transactions, fallbackAccountId, userId);
        for (ImportedTransaction tx : transactions) {
            tx.getValidationErrors().removeIf(error -> error.startsWith("SPLIT_INVALID:"));
            if (!tx.isSplitTransaction() || tx.hasErrors() || tx.getAmount() == null) continue;
            String accountKey = buildImportedAccountKey(tx.getAccountName(), tx.getAccountNumber());
            Long accountId = accountKey == null ? fallbackAccountId : scopes.get(accountKey);
            String currency = resolveTransactionCurrency(tx, accountId);
            try {
                BigDecimal amount = resolveSignedAmount(tx, accountId, currency).abs();
                List<TransactionSplitRequest> splits =
                        tx.getSplits().stream()
                                .map(
                                        split ->
                                                TransactionSplitRequest.builder()
                                                        .amount(
                                                                split.getAmount() == null
                                                                        ? null
                                                                        : split.getAmount().abs())
                                                        .build())
                                .toList();
                splits = transactionSplitService.reconcileForImport(amount, currency, splits);
                transactionSplitService.validateSplits(
                        amount,
                        tx.isTransfer()
                                ? TransactionType.TRANSFER
                                : tx.getAmount().signum() < 0
                                        ? TransactionType.EXPENSE
                                        : TransactionType.INCOME,
                        splits);
            } catch (org.openfinance.exception.InvalidTransactionException ex) {
                tx.addValidationError("SPLIT_INVALID: " + ex.getMessage());
            }
        }
    }

    private List<TransactionSplitRequest> prepareImportedSplits(
            Transaction transaction,
            ImportedTransaction importedTx,
            Long userId,
            Map<String, Long> categoryMappings,
            Map<Long, Long> categoryIdsBySource) {
        if (!importedTx.isSplitTransaction()) {
            return List.of();
        }
        List<TransactionSplitRequest> requests =
                buildSplitRequests(importedTx, userId, categoryMappings, categoryIdsBySource);
        List<TransactionSplitRequest> splits =
                transactionSplitService.reconcileForImport(
                        transaction.getAmount(), transaction.getCurrency(), requests);
        transactionSplitService.validateSplits(
                transaction.getAmount(), transaction.getType(), splits);
        return splits;
    }

    private List<TransactionSplitRequest> buildSplitRequests(
            ImportedTransaction importedTx,
            Long userId,
            Map<String, Long> categoryMappings,
            Map<Long, Long> categoryIdsBySource) {
        CategoryType categoryType = importedCategoryType(importedTx);
        return importedTx.getSplits().stream()
                .map(
                        splitEntry -> {
                            Long categoryId =
                                    splitEntry.getSourceCategoryId() != null
                                            ? categoryIdsBySource.get(
                                                    splitEntry.getSourceCategoryId())
                                            : null;
                            if (categoryId == null
                                    && splitEntry.getCategory() != null
                                    && categoryMappings != null) {
                                categoryId = categoryMappings.get(splitEntry.getCategory().trim());
                            }
                            if (categoryId != null) {
                                validateImportedCategory(categoryId, userId, categoryType);
                            }
                            if (categoryId == null && splitEntry.getCategory() != null) {
                                String catName = splitEntry.getCategory().trim();
                                if (!catName.isEmpty() && !catName.startsWith("[")) {
                                    categoryId =
                                            resolveOrCreateHierarchicalCategory(
                                                    catName, userId, categoryType);
                                }
                            }
                            return TransactionSplitRequest.builder()
                                    .categoryId(categoryId)
                                    .amount(normalizeAmount(splitEntry.getAmount()))
                                    .description(
                                            truncate(
                                                    splitEntry.getMemo(),
                                                    TRANSACTION_DESCRIPTION_MAX_LENGTH))
                                    .build();
                        })
                .collect(Collectors.toList());
    }

    private CategoryType importedCategoryType(ImportedTransaction transaction) {
        return transaction.getAmount().signum() >= 0 ? CategoryType.INCOME : CategoryType.EXPENSE;
    }

    private void validateImportedCategory(Long categoryId, Long userId, CategoryType type) {
        Category category =
                categoryRepository
                        .findByIdAndUserId(categoryId, userId)
                        .orElseThrow(() -> new ResourceNotFoundException("Category not found"));
        if (category.getType() != type) {
            throw new IllegalArgumentException(
                    "Mapped category type does not match transaction type");
        }
    }

    /** Validate untrusted mappings before accounts or transactions can be created. */
    private void validateImportCategoryMappings(
            List<ImportedTransaction> transactions, Long userId, Map<String, Long> mappings) {
        if (mappings == null || mappings.isEmpty()) return;
        for (Long categoryId : new java.util.HashSet<>(mappings.values())) {
            if (categoryId != null
                    && categoryRepository.findByIdAndUserId(categoryId, userId).isEmpty()) {
                throw new ResourceNotFoundException("Category not found");
            }
        }
        for (ImportedTransaction transaction : transactions) {
            if (transaction.hasErrors()
                    || transaction.isTransfer()
                    || transaction.isOpeningBalance()) continue;
            CategoryType type = importedCategoryType(transaction);
            List<String> names = new ArrayList<>();
            names.add(transaction.getCategory());
            transaction.getSplits().forEach(split -> names.add(split.getCategory()));
            for (String name : names) {
                Long categoryId = name == null ? null : mappings.get(name.trim());
                if (categoryId != null) validateImportedCategory(categoryId, userId, type);
            }
        }
    }

    private boolean hasSkroogeMetadata(String metadata) {
        return readMetadataMap(metadata).containsKey("skroogeMetadata");
    }

    private SkroogeImportMetadata extractSkroogeMetadata(String metadata) {
        Object value = readMetadataMap(metadata).get("skroogeMetadata");
        if (value == null) {
            throw new IllegalStateException("Skrooge metadata missing from import session");
        }
        return objectMapper.convertValue(value, SkroogeImportMetadata.class);
    }

    private Map<String, Object> preserveMetadata(String metadata) {
        Map<String, Object> preserved = readMetadataMap(metadata);
        preserved.remove("transactions");
        preserved.remove("count");
        preserved.remove("ledgerBalance");
        preserved.remove("fileCurrency");
        preserved.remove("timestamp");
        return preserved;
    }

    private Map<String, Object> deserializeMetadata(String metadata)
            throws JsonProcessingException {
        // Untyped JSON otherwise becomes Double before convertValue can bind BigDecimal fields.
        // Use a local reader so transactions, splits and opening balances retain source precision.
        return objectMapper
                .readerFor(new TypeReference<Map<String, Object>>() {})
                .with(DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS)
                .readValue(metadata);
    }

    private Map<String, Object> readMetadataMap(String metadata) {
        if (metadata == null || metadata.trim().isEmpty()) {
            return new HashMap<>();
        }
        try {
            return deserializeMetadata(metadata);
        } catch (JsonProcessingException ex) {
            log.warn("Failed to read import metadata: {}", ex.getMessage());
            return new HashMap<>();
        }
    }

    private String resolveTransactionCurrency(ImportedTransaction importedTx, Long accountId) {
        Optional<Account> account =
                accountId == null ? Optional.empty() : accountRepository.findById(accountId);
        String accountCurrency = account.map(Account::getCurrency).orElse(null);
        if (accountCurrency != null && !accountCurrency.isBlank()) {
            return accountCurrency;
        }
        if (importedTx.getCurrency() != null && !importedTx.getCurrency().isBlank()) {
            return importedTx.getCurrency();
        }
        return defaultCurrencyProvider.getDefaultCurrency();
    }

    private BigDecimal resolveSignedAmount(
            ImportedTransaction importedTx, Long accountId, String transactionCurrency) {
        BigDecimal signedAmount = importedTx.getAmount();
        if (signedAmount == null) {
            return null;
        }
        String importedCurrency = importedTx.getCurrency();
        if (importedCurrency == null
                || importedCurrency.isBlank()
                || transactionCurrency == null
                || transactionCurrency.isBlank()
                || importedCurrency.equalsIgnoreCase(transactionCurrency)) {
            return signedAmount;
        }

        BigDecimal convertedAmount;
        try {
            convertedAmount =
                    exchangeRateService.convert(
                            signedAmount.abs(),
                            importedCurrency,
                            transactionCurrency,
                            importedTx.getTransactionDate());
        } catch (RuntimeException ex) {
            if (importedTx.getSourceAccountBalanceDelta() != null) {
                log.warn(
                        "Using source account balance delta for imported transaction {} because historical FX conversion failed: {}",
                        importedTx.getReferenceNumber(),
                        ex.getMessage());
                return importedTx.getSourceAccountBalanceDelta();
            }
            throw ex;
        }
        return signedAmount.signum() < 0 ? convertedAmount.negate() : convertedAmount;
    }

    private String extractFileCurrency(String metadata, Long userId) {
        Map<String, Object> metadataMap = readMetadataMap(metadata);
        Object fileCurrency = metadataMap.get("fileCurrency");
        if (fileCurrency != null && !fileCurrency.toString().isBlank()) {
            return fileCurrency.toString();
        }
        return defaultCurrencyProvider.resolveForUser(userId);
    }

    /**
     * Resolve a hierarchical category path (e.g., "Income:Salary") to a category ID.
     *
     * <p>Resolution order:
     *
     * <ol>
     *   <li>Exact match on full path name (e.g., "Income:Salary")
     *   <li>Match child name under matching parent (parent "Income", child "Salary")
     *   <li>Match leaf name only (e.g., "Salary")
     *   <li>Create parent and child categories if no match found
     * </ol>
     *
     * @param categoryPath full category path from import file (e.g., "Income:Salary")
     * @param userId the user who owns the categories
     * @param type INCOME or EXPENSE for newly created categories
     * @return the resolved or created category ID, or null if path is blank
     */
    private Long resolveOrCreateHierarchicalCategory(
            String categoryPath, Long userId, CategoryType type) {
        if (categoryPath == null || categoryPath.isBlank()) {
            return null;
        }

        List<Category> userCategories =
                categoryRepository.findByUserId(userId).stream()
                        .filter(category -> category.getType() == type)
                        .toList();

        // 1. Try exact match on full path name
        Optional<Category> exactMatch =
                userCategories.stream()
                        .filter(c -> c.getName().equalsIgnoreCase(categoryPath))
                        .findFirst();
        if (exactMatch.isPresent()) {
            log.debug(
                    "Category '{}' matched exactly to ID={}",
                    categoryPath,
                    exactMatch.get().getId());
            return exactMatch.get().getId();
        }

        // 2. If hierarchical (contains ":"), try parent/child match then leaf match
        if (categoryPath.contains(":")) {
            String[] segments = categoryPath.split(":");
            String parentName = segments[0].trim();
            String childName = segments[segments.length - 1].trim();

            // Try to find child with a matching parent
            Optional<Category> hierarchicalMatch =
                    userCategories.stream()
                            .filter(
                                    c ->
                                            c.getName().equalsIgnoreCase(childName)
                                                    && c.getParentId() != null)
                            .filter(
                                    c ->
                                            userCategories.stream()
                                                    .anyMatch(
                                                            p ->
                                                                    p.getId()
                                                                                    .equals(
                                                                                            c
                                                                                                    .getParentId())
                                                                            && p.getName()
                                                                                    .equalsIgnoreCase(
                                                                                            parentName)))
                            .findFirst();
            if (hierarchicalMatch.isPresent()) {
                log.debug(
                        "Category '{}' matched hierarchically (parent '{}', child '{}') to ID={}",
                        categoryPath,
                        parentName,
                        childName,
                        hierarchicalMatch.get().getId());
                return hierarchicalMatch.get().getId();
            }

            // Try just the leaf name
            Optional<Category> leafMatch =
                    userCategories.stream()
                            .filter(c -> c.getName().equalsIgnoreCase(childName))
                            .findFirst();
            if (leafMatch.isPresent()) {
                log.debug(
                        "Category '{}' matched by leaf name '{}' to ID={}",
                        categoryPath,
                        childName,
                        leafMatch.get().getId());
                return leafMatch.get().getId();
            }

            // No match — create parent (if not existing) and child
            Category parent =
                    userCategories.stream()
                            .filter(
                                    c ->
                                            c.getName().equalsIgnoreCase(parentName)
                                                    && c.getParentId() == null)
                            .findFirst()
                            .orElseGet(
                                    () -> {
                                        Category newParent =
                                                categoryRepository.save(
                                                        Category.builder()
                                                                .userId(userId)
                                                                .name(parentName)
                                                                .type(type)
                                                                .isSystem(false)
                                                                .build());
                                        log.info(
                                                "Created parent category '{}' (ID={}) for user {}",
                                                parentName,
                                                newParent.getId(),
                                                userId);
                                        return newParent;
                                    });

            Category child =
                    categoryRepository.save(
                            Category.builder()
                                    .userId(userId)
                                    .name(childName)
                                    .parentId(parent.getId())
                                    .type(type)
                                    .isSystem(false)
                                    .build());
            log.info(
                    "Created child category '{}' under parent '{}' (ID={}) for user {}",
                    childName,
                    parentName,
                    child.getId(),
                    userId);
            return child.getId();
        }

        // Non-hierarchical: no exact match was found, create as root category
        Category newCategory =
                categoryRepository.save(
                        Category.builder()
                                .userId(userId)
                                .name(categoryPath)
                                .type(type)
                                .isSystem(false)
                                .build());
        log.info(
                "Created root category '{}' (ID={}) for user {}",
                categoryPath,
                newCategory.getId(),
                userId);
        return newCategory.getId();
    }

    /**
     * Convert ImportedTransaction to Transaction entity. The {@code referenceNumber} from the
     * imported file is persisted as {@code externalReference} so that future imports can perform
     * fast, authoritative duplicate detection (Tier 1 in {@link #detectDuplicates}).
     */
    private Transaction convertToTransaction(
            ImportedTransaction importedTx,
            Long accountId,
            Long userId,
            Map<String, Long> categoryMappings) {
        return convertToTransaction(importedTx, accountId, userId, categoryMappings, Map.of());
    }

    private Transaction convertToTransaction(
            ImportedTransaction importedTx,
            Long accountId,
            Long userId,
            Map<String, Long> categoryMappings,
            Map<Long, Long> categoryIdsBySource) {
        return convertToTransaction(
                importedTx, accountId, userId, categoryMappings, categoryIdsBySource, true);
    }

    private Transaction convertToTransaction(
            ImportedTransaction importedTx,
            Long accountId,
            Long userId,
            Map<String, Long> categoryMappings,
            Map<Long, Long> categoryIdsBySource,
            boolean mapCategory) {
        // Determine transaction type based on amount
        TransactionType transactionType;

        if (importedTx.getAmount().compareTo(BigDecimal.ZERO) >= 0) {
            transactionType = TransactionType.INCOME;
        } else {
            transactionType = TransactionType.EXPENSE;
        }

        // Resolve currency code and link to Currency entity
        String currencyCode = resolveTransactionCurrency(importedTx, accountId);
        BigDecimal signedAmount = resolveSignedAmount(importedTx, accountId, currencyCode);
        BigDecimal amount = normalizeAmount(signedAmount);
        Long currencyId =
                currencyRepository.findByCode(currencyCode).map(c -> c.getId()).orElse(null);

        // Resolve or create Payee entity
        String payeeName = truncate(importedTx.getPayee(), TRANSACTION_PAYEE_MAX_LENGTH);
        Long payeeId = resolveOrCreatePayeeId(payeeName, userId);

        // Description preserves the raw imported payee text (before any
        // rule/auto-categorization
        // SET_PAYEE override) so the original merchant text isn't lost from the record.
        String descriptionSource =
                importedTx.getOriginalPayee() != null
                        ? importedTx.getOriginalPayee()
                        : importedTx.getPayee();

        // Build transaction using builder pattern
        Transaction.TransactionBuilder builder =
                Transaction.builder()
                        .userId(userId)
                        .accountId(accountId)
                        .date(importedTx.getTransactionDate())
                        .amount(amount)
                        .currency(currencyCode)
                        .currencyId(currencyId)
                        .description(
                                truncate(descriptionSource, TRANSACTION_DESCRIPTION_MAX_LENGTH))
                        .notes(truncate(importedTx.getMemo(), TRANSACTION_NOTES_MAX_LENGTH))
                        .payee(payeeName)
                        .payeeId(payeeId)
                        .type(transactionType)
                        .externalReference(
                                Boolean.FALSE.equals(importedTx.getAuthoritativeReference())
                                        ? null
                                        : importedTx.getReferenceNumber())
                        .paymentMethod(mapPaymentMethod(importedTx.getPaymentMethod()))
                        .isReconciled("reconciled".equalsIgnoreCase(importedTx.getClearedStatus()))
                        .isDeleted(false);

        if (importedTx.getCurrency() != null
                && !importedTx.getCurrency().isBlank()
                && !importedTx.getCurrency().equalsIgnoreCase(currencyCode)
                && importedTx.getAmount().signum() != 0) {
            BigDecimal originalAmount = importedTx.getAmount().abs();
            builder.originalAmount(originalAmount)
                    .originalCurrency(importedTx.getCurrency())
                    .conversionRate(
                            amount.divide(originalAmount, 18, java.math.RoundingMode.HALF_EVEN));
        }

        // Map category
        if (mapCategory
                && importedTx.getCategory() != null
                && !importedTx.getCategory().trim().isEmpty()) {
            String categoryName = importedTx.getCategory().trim();
            Long categoryId =
                    importedTx.getSourceCategoryId() != null
                            ? categoryIdsBySource.get(importedTx.getSourceCategoryId())
                            : null;
            if (categoryId == null) {
                categoryId = categoryMappings != null ? categoryMappings.get(categoryName) : null;
            }

            if (categoryId != null) {
                log.debug(
                        "Mapping category '{}' using provided mapping to ID {}",
                        categoryName,
                        categoryId);
                Category selectedCategory =
                        categoryRepository
                                .findByIdAndUserId(categoryId, userId)
                                .orElseThrow(
                                        () ->
                                                new IllegalArgumentException(
                                                        "Invalid import category"));
                CategoryType expectedType =
                        transactionType == TransactionType.INCOME
                                ? CategoryType.INCOME
                                : CategoryType.EXPENSE;
                if (selectedCategory.getType() != expectedType) {
                    throw new IllegalArgumentException(
                            "Import category type does not match transaction type");
                }
                builder.categoryId(categoryId);
            } else if (!categoryName.startsWith("[")) {
                // Resolve hierarchical category (e.g., "Income:Salary" → parent "Income", child
                // "Salary")
                // Skip transfer categories (bracket syntax like "[Savings Account]")
                CategoryType catType =
                        transactionType == TransactionType.INCOME
                                ? CategoryType.INCOME
                                : CategoryType.EXPENSE;
                Long resolvedId =
                        resolveOrCreateHierarchicalCategory(categoryName, userId, catType);
                if (resolvedId != null) {
                    builder.categoryId(resolvedId);
                }
            }
        }

        // Map tags from rule engine results (REQ-TR-4.1 ΓÇö ADD_TAG action)
        if (importedTx.getTags() != null && !importedTx.getTags().isEmpty()) {
            String tagsStr = String.join(",", importedTx.getTags());
            builder.tags(tagsStr);
            log.debug(
                    "Mapped {} tag(s) from import rules: {}", importedTx.getTags().size(), tagsStr);
        }

        return builder.build();
    }

    private String truncate(String value, int maxLength) {
        if (value == null || value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, maxLength);
    }

    /**
     * Resolve an existing Payee by name (case-insensitive) or create a new one. Returns the payee
     * ID, or null if the payee name is blank.
     */
    private Long resolveOrCreatePayeeId(String payeeName, Long userId) {
        if (payeeName == null || payeeName.isBlank()) {
            return null;
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
            return existing.getId();
        }
        // Create a new user-scoped payee
        org.openfinance.entity.Payee newPayee =
                org.openfinance.entity.Payee.builder()
                        .name(trimmed)
                        .userId(userId)
                        .isSystem(false)
                        .isActive(true)
                        .build();
        return payeeRepository.save(newPayee).getId();
    }

    private BigDecimal normalizeAmount(BigDecimal amount) {
        if (amount == null || amount.signum() == 0) {
            throw new IllegalArgumentException(
                    "Imported transaction amount must be non-zero: " + amount);
        }
        // Preserve source precision, including crypto dust. Display/minor-unit rounding is not
        // a storage rule: encrypted Transaction amounts support 26 integer and 18 fractional
        // digits.
        BigDecimal normalized = amount.abs().stripTrailingZeros();
        int integerDigits = normalized.precision() - normalized.scale();
        if (integerDigits > 26 || normalized.scale() > 18) {
            throw new IllegalArgumentException(
                    "Imported amount exceeds supported precision: " + amount);
        }
        return normalized;
    }

    /**
     * Serialize transactions to JSON string for metadata storage. Uses Jackson ObjectMapper for
     * proper JSON serialization.
     *
     * @param transactions list of imported transactions
     * @param ledgerBalance starting balance from the file
     * @param fileCurrency currency from the file
     * @return JSON string representation
     */
    private String serializeTransactions(
            List<ImportedTransaction> transactions, BigDecimal ledgerBalance, String fileCurrency) {
        return serializeTransactions(transactions, ledgerBalance, fileCurrency, Map.of());
    }

    private String serializeTransactions(
            List<ImportedTransaction> transactions,
            BigDecimal ledgerBalance,
            String fileCurrency,
            Map<String, Object> extraMetadata) {
        try {
            Map<String, Object> metadata = new HashMap<>();
            if (extraMetadata != null && !extraMetadata.isEmpty()) {
                metadata.putAll(extraMetadata);
            }
            metadata.put("transactions", transactions);
            metadata.put("count", transactions.size());
            metadata.put("ledgerBalance", ledgerBalance);
            metadata.put("fileCurrency", fileCurrency);
            metadata.put("timestamp", LocalDateTime.now().toString());
            return objectMapper.writeValueAsString(metadata);
        } catch (JsonProcessingException e) {
            log.error("Error serializing transactions: {}", e.getMessage(), e);
            // Fallback to simple JSON
            return String.format(
                    "{\"count\": %d, \"timestamp\": \"%s\", \"error\": \"Serialization failed\"}",
                    transactions.size(), LocalDateTime.now());
        }
    }

    /**
     * Deserialize transactions from JSON metadata. Uses Jackson ObjectMapper for proper JSON
     * deserialization.
     *
     * @param metadata JSON string containing transactions
     * @return list of imported transactions
     */
    private List<ImportedTransaction> deserializeTransactions(String metadata) {
        if (metadata == null || metadata.trim().isEmpty()) {
            log.warn("Metadata is empty, returning empty transaction list");
            return new ArrayList<>();
        }

        try {
            Map<String, Object> metadataMap = deserializeMetadata(metadata);

            if (!metadataMap.containsKey("transactions")) {
                log.warn("Metadata does not contain transactions field");
                return new ArrayList<>();
            }

            // Deserialize transactions list
            Object transactionsObj = metadataMap.get("transactions");
            return objectMapper.convertValue(
                    transactionsObj, new TypeReference<List<ImportedTransaction>>() {});

        } catch (JsonProcessingException e) {
            log.error("Error deserializing transactions: {}", e.getMessage(), e);
            return new ArrayList<>();
        }
    }

    private record ImportedAccountDescriptor(
            String key,
            String name,
            String accountNumber,
            String currency,
            LocalDate openingDate,
            String qifAccountType,
            String institutionName,
            BigDecimal openingBalance) {}

    /**
     * Map a QIF account type string (from !Account T field or !Type directive) to the corresponding
     * {@link AccountType}.
     *
     * <p>QIF types: Bank → CHECKING, CCard → CREDIT_CARD, Cash → CASH, Oth A / Invst → INVESTMENT.
     * Unknown or null types default to CHECKING.
     */
    private AccountType mapQifAccountType(String qifAccountType) {
        if (qifAccountType == null || qifAccountType.isBlank()) {
            return AccountType.CHECKING;
        }
        String lower = qifAccountType.trim().toLowerCase();
        switch (lower) {
            case "bank":
                return AccountType.CHECKING;
            case "ccard":
                return AccountType.CREDIT_CARD;
            case "cash":
                return AccountType.CASH;
            case "oth a":
            case "invst":
                return AccountType.INVESTMENT;
            default:
                return AccountType.OTHER;
        }
    }

    /**
     * Map a raw payment-method string from the import file (e.g., Skrooge "mode" column) to a
     * {@link PaymentMethod} enum value.
     *
     * <p>Recognised values (case-insensitive, accent-tolerant):
     *
     * <ul>
     *   <li>"Débit" / "debit" → {@link PaymentMethod#DEBIT_CARD}
     *   <li>"Crédit" / "credit" → {@link PaymentMethod#CREDIT_CARD}
     *   <li>"Virement" / "transfer" → {@link PaymentMethod#BANK_TRANSFER}
     *   <li>"Prélèvement" / "direct debit" → {@link PaymentMethod#DIRECT_DEBIT}
     *   <li>"Chèque" / "cheque" / "check" → {@link PaymentMethod#CHEQUE}
     *   <li>"Espèces" / "cash" → {@link PaymentMethod#CASH}
     *   <li>"Dépôt" / "deposit" → {@link PaymentMethod#DEPOSIT}
     *   <li>"En ligne" / "online" → {@link PaymentMethod#ONLINE}
     * </ul>
     *
     * @param rawMode raw payment method string, or null
     * @return mapped PaymentMethod, or null when the input is blank or unrecognised
     */
    private PaymentMethod mapPaymentMethod(String rawMode) {
        if (rawMode == null || rawMode.isBlank()) {
            return null;
        }
        // Normalise: lowercase, strip accents
        String normalized =
                java.text.Normalizer.normalize(rawMode, java.text.Normalizer.Form.NFD)
                        .replaceAll("\\p{InCombiningDiacriticalMarks}+", "")
                        .trim()
                        .toLowerCase();
        switch (normalized) {
            case "debit":
                return PaymentMethod.DEBIT_CARD;
            case "credit":
                return PaymentMethod.CREDIT_CARD;
            case "virement":
            case "transfer":
                return PaymentMethod.BANK_TRANSFER;
            case "prelevement":
            case "direct debit":
                return PaymentMethod.DIRECT_DEBIT;
            case "cheque":
            case "check":
                return PaymentMethod.CHEQUE;
            case "especes":
            case "cash":
                return PaymentMethod.CASH;
            case "depot":
            case "deposit":
                return PaymentMethod.DEPOSIT;
            case "en ligne":
            case "online":
                return PaymentMethod.ONLINE;
            default:
                return null;
        }
    }
}
