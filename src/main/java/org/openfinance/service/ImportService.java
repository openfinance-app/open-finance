package org.openfinance.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import javax.crypto.SecretKey;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.openfinance.dto.AccountRequest;
import org.openfinance.dto.AccountResponse;
import org.openfinance.dto.ImportParseResult;
import org.openfinance.dto.ImportedTransaction;
import org.openfinance.dto.SkroogeImportMetadata;
import org.openfinance.dto.SkroogeImportParseResult;
import org.openfinance.dto.TransactionRequest;
import org.openfinance.dto.TransactionSplitRequest;
import org.openfinance.entity.Account;
import org.openfinance.entity.AccountType;
import org.openfinance.entity.Category;
import org.openfinance.entity.CategoryType;
import org.openfinance.entity.ImportSession;
import org.openfinance.entity.ImportSession.ImportStatus;
import org.openfinance.entity.Institution;
import org.openfinance.entity.Transaction;
// Import TransactionType enum
import org.openfinance.entity.TransactionType;
import org.openfinance.entity.User;
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
import org.openfinance.security.EncryptionService;
import org.openfinance.service.parser.CsvParser;
import org.openfinance.service.parser.OfxParser;
import org.openfinance.service.parser.QifParser;
import org.openfinance.service.parser.SkroogeJsonParser;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Service for managing transaction import from files (QIF, OFX, QFX).
 *
 * <p>
 * Import Process Flow:
 *
 * <ol>
 * <li>User uploads file via FileUploadController ΓåÆ FileStorageService
 * <li>User initiates import via startImport(uploadId, accountId)
 * <li>System creates ImportSession with PENDING status
 * <li>System parses file asynchronously ΓåÆ PARSING ΓåÆ PARSED
 * <li>User reviews transactions, maps categories, handles duplicates ΓåÆ
 * REVIEWING
 * <li>User confirms import ΓåÆ IMPORTING ΓåÆ COMPLETED
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

    private final ImportSessionRepository importSessionRepository;
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
    private final TransactionRuleService transactionRuleService;
    private final TransactionService transactionService;
    private final TransactionSplitService transactionSplitService;
    private final NetWorthRepository netWorthRepository;
    private final AICategorizationService aiCategorizationService;
    private final MessageSource messageSource;
    private final EncryptionService encryptionService;
    private final InstitutionRepository institutionRepository;
    private final CurrencyRepository currencyRepository;
    private final PayeeRepository payeeRepository;

    /**
     * Start a new import session and parse the uploaded file.
     *
     * @param uploadId  the UUID of the uploaded file
     * @param userId    the ID of the user initiating the import
     * @param accountId the target account ID (can be null if selected during
     *                  review)
     * @return the created import session
     * @throws ResourceNotFoundException if upload not found
     * @throws IllegalArgumentException  if file validation fails
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
        if (!fileStorageService.fileExists(uploadId)) {
            throw new ResourceNotFoundException("Uploaded file not found: " + uploadId);
        }

        // Get storage filename (UUID.ext) ΓÇö used for format detection
        String storageFileName = fileStorageService.getOriginalFileName(uploadId);

        // Prefer the original user-visible filename (e.g. "bank_statement.csv") for
        // display purposes and account name derivation; fall back to storage filename
        String displayFileName = (originalFileName != null && !originalFileName.isBlank())
                ? originalFileName
                : storageFileName;

        // Detect file format from the storage filename (preserves the extension)
        String fileFormat = detectFileFormat(storageFileName, uploadId);

        // Validate account if provided
        if (accountId != null) {
            Account account = accountRepository
                    .findByIdAndUserId(accountId, userId)
                    .orElseThrow(
                            () -> new ResourceNotFoundException(
                                    "Account not found: " + accountId));

            if (!account.getIsActive()) {
                throw new IllegalArgumentException(
                        "Cannot import to inactive account: " + accountId);
            }
        }

        // Create import session
        ImportSession session = ImportSession.builder()
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
     * Parse the uploaded file asynchronously and extract transactions. Updates the
     * session status
     * to PARSING ΓåÆ PARSED (or FAILED on error).
     *
     * <p>
     * This method runs in a background thread managed by the taskExecutor. The HTTP
     * request
     * returns immediately with the session ID, and the client should poll GET
     * /api/v1/import/sessions/{id} to check progress.
     *
     * <p>
     * <strong>Status Transitions:</strong>
     *
     * <ul>
     * <li>PENDING ΓåÆ PARSING (when parsing starts)
     * <li>PARSING ΓåÆ PARSED (on success)
     * <li>PARSING ΓåÆ FAILED (on error)
     * </ul>
     *
     * <p>
     * <strong>Transaction Management:</strong>
     *
     * <p>
     * Note: No @Transactional annotation on this async method. Each repository
     * operation runs in
     * its own transaction, which is actually desirable because:
     *
     * <ul>
     * <li>Status updates (PARSING ΓåÆ PARSED/FAILED) are persisted immediately
     * <li>No risk of long-running transaction holding database locks
     * <li>Partial commits are acceptable (we want to persist FAILED status)
     * </ul>
     *
     * <p>
     * Requirement REQ-2.5.1.8: Asynchronous file parsing
     *
     * @param sessionId the import session ID
     */
    @Async("taskExecutor")
    public void parseFileAsync(Long sessionId) {
        log.info("Starting async parsing for session: {}", sessionId);

        // Fetch session in async context
        ImportSession session = importSessionRepository
                .findById(sessionId)
                .orElseThrow(
                        () -> new ResourceNotFoundException(
                                "Import session not found: " + sessionId));

        try {
            // Update status to PARSING
            session.setStatus(ImportStatus.PARSING);
            importSessionRepository.save(session);

            // Get file content
            InputStream fileStream = fileStorageService.getFileContent(session.getUploadId());

            // Parse based on format
            List<ImportedTransaction> transactions;
            BigDecimal ledgerBalance = BigDecimal.ZERO;
            String fileCurrency = "USD";
            try {
                User user = userRepository.findById(session.getUserId()).orElse(null);
                if (user != null && user.getBaseCurrency() != null) {
                    fileCurrency = user.getBaseCurrency();
                }
            } catch (Exception e) {
                log.warn("Could not fetch user base currency, defaulting to USD", e);
            }

            switch (session.getFileFormat().toUpperCase()) {
                case "QIF":
                    transactions = qifParser.parseFile(fileStream, session.getFileName());
                    break;
                case "OFX":
                case "QFX":
                    ImportParseResult ofxResult = ofxParser.parseFileToResult(fileStream, session.getFileName());
                    transactions = ofxResult.getTransactions();
                    if (ofxResult.getLedgerBalance() != null) {
                        ledgerBalance = ofxResult.getLedgerBalance();
                    }
                    if (ofxResult.getCurrency() != null) {
                        fileCurrency = ofxResult.getCurrency();
                    }
                    break;
                case "CSV":
                    transactions = csvParser.parseFile(fileStream, session.getFileName());
                    break;
                case "JSON":
                    SkroogeImportParseResult skroogeResult = skroogeJsonParser.parseFile(fileStream,
                            session.getFileName());
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
                    List<Account> userAccounts = accountRepository.findByUserId(session.getUserId());
                    for (Account acc : userAccounts) {
                        String normalizedDetected = detectedAccountName.replaceAll("[^a-zA-Z0-9]", "").toLowerCase();
                        String normalizedAccName = acc.getName().replaceAll("[^a-zA-Z0-9]", "").toLowerCase();
                        String normalizedAccNum = acc.getAccountNumber() != null
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
                        serializeTransactions(transactions, ledgerBalance, fileCurrency));
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
     * Get parsed transactions for review. Includes duplicate detection and category
     * suggestions.
     *
     * @param sessionId the import session ID
     * @param userId    the user ID (for authorization)
     * @return list of imported transactions with duplicate flags and category
     *         suggestions
     */
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public List<ImportedTransaction> reviewTransactions(Long sessionId, Long userId) {
        log.info("Reviewing transactions for session: {}", sessionId);

        ImportSession session = getSessionForUser(sessionId, userId);

        if (!session.isReadyForReview()) {
            throw new IllegalStateException(
                    "Session is not ready for review. Current status: " + session.getStatus());
        }

        // Deserialize transactions from metadata
        List<ImportedTransaction> transactions = deserializeTransactions(session.getMetadata());

        // Detect duplicates
        detectDuplicates(transactions, session.getAccountId(), session.getFileFormat(), userId);

        // Suggest categories and apply transaction rules (sets tags, category, etc.)
        suggestCategories(transactions, userId);

        // Persist the rule-enriched transactions back to the session metadata so that
        // confirmImport() sees the tags/category values set by ADD_TAG / SET_CATEGORY
        // actions. Without this, the enriched state is discarded after the review call.
        BigDecimal ledgerBalance = BigDecimal.ZERO;
        String fileCurrency = "USD";
        if (session.getMetadata() != null && !session.getMetadata().trim().isEmpty()) {
            try {
                Map<String, Object> metadataMap = objectMapper.readValue(
                        session.getMetadata(), new TypeReference<Map<String, Object>>() {
                        });
                if (metadataMap.containsKey("ledgerBalance")) {
                    ledgerBalance = new BigDecimal(metadataMap.get("ledgerBalance").toString());
                }
                if (metadataMap.containsKey("fileCurrency")
                        && metadataMap.get("fileCurrency") != null) {
                    fileCurrency = metadataMap.get("fileCurrency").toString();
                }
            } catch (Exception e) {
                log.warn("Error extracting ledger metadata during review: {}", e.getMessage());
            }
        }
        session.setMetadata(
                serializeTransactions(
                        transactions,
                        ledgerBalance,
                        fileCurrency,
                        preserveMetadata(session.getMetadata())));

        // Update status to REVIEWING if not already
        if (session.getStatus() == ImportStatus.PARSED) {
            session.setStatus(ImportStatus.REVIEWING);
        }
        importSessionRepository.save(session);

        return transactions;
    }

    /**
     * Update the target account for an import session.
     *
     * @param sessionId the import session ID
     * @param accountId the new account ID
     * @param userId    the user ID (for authorization)
     * @return the updated import session
     */
    @Transactional
    public ImportSession updateAccount(Long sessionId, Long accountId, Long userId) {
        log.info("Updating account for session: {}, accountId={}", sessionId, accountId);

        ImportSession session = getSessionForUser(sessionId, userId);

        // Can only change account if not already importing
        if (!session.isReadyForReview()
                && session.getStatus() != ImportStatus.PARSING
                && session.getStatus() != ImportStatus.PENDING) {
            throw new IllegalStateException(
                    "Cannot update account. Current status: " + session.getStatus());
        }

        Account account = accountRepository
                .findByIdAndUserId(accountId, userId)
                .orElseThrow(
                        () -> new ResourceNotFoundException(
                                "Account not found: " + accountId));

        if (!account.getIsActive()) {
            throw new IllegalArgumentException("Cannot import to inactive account: " + accountId);
        }

        session.setAccountId(accountId);
        return importSessionRepository.save(session);
    }

    /**
     * Update the parsed transactions in the session metadata, usually after manual
     * review/edits.
     *
     * @param sessionId    the import session ID
     * @param transactions the updated list of imported transactions
     * @param userId       the user ID (for authorization)
     * @return the updated import session
     */
    @Transactional
    public ImportSession updateParsedTransactions(
            Long sessionId, List<ImportedTransaction> transactions, Long userId) {
        log.info("Updating parsed transactions for session: {}", sessionId);

        ImportSession session = getSessionForUser(sessionId, userId);

        if (!session.isReadyForReview()) {
            throw new IllegalStateException(
                    "Session is not ready for review. Current status: " + session.getStatus());
        }

        // Detect duplicates with the currently selected account
        detectDuplicates(transactions, session.getAccountId(), session.getFileFormat(), userId);

        // Preserve existing ledgerBalance and fileCurrency in metadata
        BigDecimal ledgerBalance = BigDecimal.ZERO;
        String fileCurrency = "USD";
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
                Map<String, Object> metadataMap = objectMapper.readValue(
                        session.getMetadata(), new TypeReference<Map<String, Object>>() {
                        });
                if (metadataMap.containsKey("ledgerBalance")) {
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

        session.setMetadata(
                serializeTransactions(
                        transactions,
                        ledgerBalance,
                        fileCurrency,
                        preserveMetadata(session.getMetadata())));
        return importSessionRepository.save(session);
    }

    /**
     * Confirm import and save transactions to database.
     *
     * @param sessionId        the import session ID
     * @param userId           the user ID (for authorization)
     * @param accountId        the target account ID (if null, an account will be
     *                         auto-created using the
     *                         session's suggestedAccountName)
     * @param categoryMappings map of imported category names to category IDs
     * @param skipDuplicates   if true, skip transactions flagged as duplicates
     * @param encryptionKey    the user's encryption key (used when auto-creating an
     *                         account)
     * @return the updated import session
     */
    @Transactional
    public ImportSession confirmImport(
            Long sessionId,
            Long userId,
            Long accountId,
            Map<String, Long> categoryMappings,
            boolean skipDuplicates,
            SecretKey encryptionKey) {
        log.info(
                "Confirming import for session: {}, accountId={}, skipDuplicates={}",
                sessionId,
                accountId,
                skipDuplicates);

        ImportSession session = getSessionForUser(sessionId, userId);

        if (!session.isConfirmable()) {
            throw new IllegalStateException(
                    "Session cannot be confirmed. Current status: " + session.getStatus());
        }

        if ("JSON".equalsIgnoreCase(session.getFileFormat())
                && hasSkroogeMetadata(session.getMetadata())) {
            return confirmSkroogeImport(
                    session, userId, categoryMappings, skipDuplicates, encryptionKey);
        }

        List<ImportedTransaction> transactions = deserializeTransactions(session.getMetadata());
        if (shouldUseImportedAccountRouting(transactions)) {
            return confirmImportedAccountImport(
                    session,
                    userId,
                    accountId,
                    categoryMappings,
                    skipDuplicates,
                    encryptionKey,
                    transactions);
        }

        // Resolve target account: use provided ID, fall back to session's accountId,
        // or auto-create a new account from the session's suggestedAccountName.
        Long resolvedAccountId = accountId != null ? accountId : session.getAccountId();
        if (resolvedAccountId == null) {
            BigDecimal ledgerBalance = BigDecimal.ZERO;
            String fileCurrency = "USD";
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
                    Map<String, Object> metadataMap = objectMapper.readValue(
                            session.getMetadata(),
                            new TypeReference<Map<String, Object>>() {
                            });
                    if (metadataMap.containsKey("ledgerBalance")) {
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
            if (ledgerBalance.compareTo(BigDecimal.ZERO) != 0) {
                List<ImportedTransaction> parsedTxs = deserializeTransactions(session.getMetadata());
                BigDecimal transactionNet = parsedTxs.stream()
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
                ledgerBalance = ledgerBalance.subtract(transactionNet);
            }
            resolvedAccountId = createAccountForImport(
                    userId, session, encryptionKey, ledgerBalance, fileCurrency);
        }
        final Long targetAccountId = resolvedAccountId;

        Account account = accountRepository
                .findByIdAndUserId(targetAccountId, userId)
                .orElseThrow(
                        () -> new ResourceNotFoundException(
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
            List<ImportedTransaction> errorTxs = transactions.stream()
                    .filter(ImportedTransaction::hasErrors)
                    .collect(Collectors.toList());

            List<ImportedTransaction> validTxs = transactions.stream()
                    .filter(tx -> !tx.hasErrors())
                    .collect(Collectors.toList());

            // Among valid transactions, split on duplicate flag
            List<ImportedTransaction> duplicateTxs = validTxs.stream().filter(this::isDuplicate)
                    .collect(Collectors.toList());

            List<ImportedTransaction> toImport = validTxs.stream()
                    .filter(tx -> !skipDuplicates || !isDuplicate(tx))
                    .collect(Collectors.toList());

            // Save transactions
            int imported = 0;
            int saveFailed = 0;

            for (ImportedTransaction importedTx : toImport) {
                try {
                    Transaction transaction = convertToTransaction(
                            importedTx, targetAccountId, userId, categoryMappings);
                    Transaction saved = transactionRepository.save(transaction);
                    // Save splits if present (REQ-SPL)
                    if (importedTx.isSplitTransaction()) {
                        List<Category> userCategories = categoryRepository.findByUserId(userId);
                        List<TransactionSplitRequest> splitRequests = importedTx.getSplits().stream()
                                .map(
                                        se -> {
                                            Long catId = null;
                                            if (se.getCategory() != null
                                                    && !se.getCategory().trim().isEmpty()) {
                                                String catName = se.getCategory().trim();
                                                Long mapped = categoryMappings != null
                                                        ? categoryMappings.get(
                                                                catName)
                                                        : null;
                                                if (mapped != null) {
                                                    catId = mapped;
                                                } else {
                                                    // Try full name first, then last
                                                    // segment of colon-separated path
                                                    // e.g. "Food:Groceries" ΓåÆ try
                                                    // "Food:Groceries" then "Groceries"
                                                    String lastSegment = catName.contains(":")
                                                            ? catName.substring(
                                                                    catName
                                                                            .lastIndexOf(
                                                                                    ':')
                                                                            + 1)
                                                                    .trim()
                                                            : catName;
                                                    catId = userCategories.stream()
                                                            .filter(
                                                                    c -> c.getName()
                                                                            .equalsIgnoreCase(
                                                                                    catName)
                                                                            || c.getName()
                                                                                    .equalsIgnoreCase(
                                                                                            lastSegment))
                                                            .findFirst()
                                                            .map(c -> c.getId())
                                                            .orElse(null);
                                                }
                                            }
                                            return TransactionSplitRequest.builder()
                                                    .categoryId(catId)
                                                    .amount(se.getAmount().abs())
                                                    .description(se.getMemo())
                                                    .build();
                                        })
                                .collect(Collectors.toList());
                        transactionSplitService.validateSplits(
                                saved.getAmount(), saved.getType(), splitRequests);
                        transactionSplitService.saveSplits(
                                saved.getId(), splitRequests, encryptionKey);
                        log.debug(
                                "Saved {} split(s) for transaction {}",
                                splitRequests.size(),
                                saved.getId());
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
            session.setImportedCount(imported);
            session.setDuplicateCount(duplicateTxs.size()); // total detected (not just skipped)
            session.setErrorCount(errorTxs.size());
            session.setSkippedCount(duplicatesSkipped + errorTxs.size() + saveFailed);
            session.setStatus(ImportStatus.COMPLETED);
            session.setCompletedAt(LocalDateTime.now());

            log.info(
                    "Import complete: {} imported, {} duplicates, {} errors, {} skipped",
                    imported,
                    duplicateTxs.size(),
                    errorTxs.size(),
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
     * @param userId    the user ID (for authorization)
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
     * @param userId    the user ID
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
        ImportSession session = importSessionRepository
                .findById(sessionId)
                .orElseThrow(
                        () -> new ResourceNotFoundException(
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
     * <li><strong>Tier 0 ΓÇö Intra-session reference match</strong>: If the
     * incoming transaction
     * carries a non-blank {@code referenceNumber}, check whether any
     * <em>earlier</em>
     * transaction in the same import batch shares the same reference for the same
     * account.
     * This catches the common case of importing the same OFX/QFX file a second time
     * before
     * the first import is confirmed.
     * <li><strong>Tier 1 ΓÇö DB exact reference match</strong>: If the incoming
     * transaction has a
     * non-blank {@code referenceNumber}, look for an existing persisted transaction
     * on the
     * same account with the same {@code externalReference}. A match is
     * authoritative ΓÇö no
     * fuzzy fallback is needed.
     * <li><strong>Tier 2 ΓÇö Fuzzy date/amount/payee match</strong>: Fall back to
     * the classic
     * heuristic: date ┬▒1 day, same absolute amount (using
     * {@link BigDecimal#compareTo} to
     * ignore scale), and ΓëÑ85 % payee similarity via Levenshtein distance. Used
     * for QIF files
     * and any file where no reference is available.
     * </ol>
     *
     * <p>
     * When a duplicate is detected the transaction is annotated with a
     * {@code DUPLICATE:}
     * validation message <em>and</em> the {@code potentialDuplicate} flag is set to
     * {@code true} so
     * that callers can inspect either surface.
     *
     * @param transactions the imported transactions to check
     * @param accountId    the target account (detection is skipped when
     *                     {@code null})
     * @param userId       the user ID (currently reserved for future per-user
     *                     scoping)
     */
    /**
     * Detect duplicate transactions using a tiered strategy.
     *
     * <p>
     * <strong>Tier 0 and Tier 1 are only active for formats that produce
     * globally-unique
     * transaction IDs</strong> (currently OFX/QFX via FITID). QIF {@code N} fields
     * are cheque
     * numbers ΓÇö not unique IDs ΓÇö and CSV reference columns are bank-dependent
     * and unreliable. Using
     * them as authoritative keys would produce false positives (e.g. two different
     * transactions
     * sharing check number "1042") and false negatives (skipping Tier 2 for a QIF
     * transaction that
     * is in fact a fuzzy duplicate). See {@link #isReferenceAuthoritative(String)}.
     *
     * <ol>
     * <li><strong>Tier 0 ΓÇö Intra-session reference match</strong> (OFX/QFX only):
     * If two
     * transactions in the same import batch share the same {@code referenceNumber}
     * (FITID),
     * the second is a definite duplicate. Catches the "import same file twice
     * before
     * confirming" case.
     * <li><strong>Tier 1 ΓÇö DB exact reference match</strong> (OFX/QFX only): If
     * the incoming
     * transaction's FITID matches an {@code externalReference} already stored in
     * the DB for
     * this account, it is a definite duplicate. No fuzzy fallback is needed.
     * <li><strong>Tier 2 ΓÇö Fuzzy date/amount/payee match</strong> (all formats):
     * Date ┬▒1 day,
     * same absolute amount ({@link BigDecimal#compareTo} to handle scale), and
     * ΓëÑ85 % payee
     * similarity via Levenshtein. Always runs for QIF and CSV; also runs for
     * OFX/QFX
     * transactions that have no reference number or that survived Tier 0/1.
     * </ol>
     *
     * <p>
     * When a duplicate is detected the {@code DUPLICATE:} message is added to
     * {@code
     * validationErrors} <em>and</em> {@code potentialDuplicate} is set to
     * {@code true}.
     *
     * @param transactions the imported transactions to check
     * @param accountId    the target account (detection is skipped when
     *                     {@code null})
     * @param fileFormat   the import file format (e.g. {@code "OFX"},
     *                     {@code "QIF"}, {@code "CSV"}) ΓÇö
     *                     controls whether Tier 0/1 are active
     * @param userId       the user ID (reserved for future per-user scoping)
     */
    private void detectDuplicates(
            List<ImportedTransaction> transactions,
            Long accountId,
            String fileFormat,
            Long userId) {
        if (accountId == null) {
            // No specific account selected yet ΓÇö fall back to checking against all of the
            // user's existing transactions so duplicates are still surfaced at review time.
            log.debug(
                    "Account not specified; checking duplicates across all transactions for user {}",
                    userId);
        }

        // findByAccountId JPQL already filters isDeleted = false ΓÇö no extra stream
        // filter needed
        List<Transaction> existingTransactions = accountId != null
                ? transactionRepository.findByAccountId(accountId)
                : transactionRepository.findByUserId(userId);

        // Only OFX/QFX produces globally-unique FITIDs that are safe for exact-match
        // dedup.
        // QIF check numbers and CSV reference columns are not reliable unique
        // identifiers.
        boolean useReferenceTiers = isReferenceAuthoritative(fileFormat);

        // Tier 0: reference numbers seen within THIS import batch (OFX/QFX only)
        // Maps referenceNumber ΓåÆ index of the first transaction that owns it
        Map<String, Integer> seenReferenceNumbers = new HashMap<>();

        for (int i = 0; i < transactions.size(); i++) {
            ImportedTransaction tx = transactions.get(i);

            if (tx.getTransactionDate() == null || tx.getAmount() == null) {
                continue; // Skip structurally invalid transactions
            }

            if (useReferenceTiers) {
                String ref = tx.getReferenceNumber();
                if (ref != null && !ref.isBlank()) {

                    // ΓöÇΓöÇ Tier 0: intra-session reference duplicate
                    // ΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇ
                    if (seenReferenceNumbers.containsKey(ref)) {
                        int firstIdx = seenReferenceNumbers.get(ref);
                        markDuplicate(
                                tx,
                                "DUPLICATE: Same reference number '"
                                        + ref
                                        + "' already appears in this import batch (transaction #"
                                        + (firstIdx + 1)
                                        + ")");
                        continue; // No need for further tiers
                    }
                    seenReferenceNumbers.put(ref, i);

                    // ΓöÇΓöÇ Tier 1: exact reference match against persisted transactions ΓöÇ
                    boolean tier1Match = existingTransactions.stream()
                            .anyMatch(
                                    existing -> ref.equals(existing.getExternalReference()));
                    if (tier1Match) {
                        markDuplicate(
                                tx,
                                "DUPLICATE: Transaction with reference '"
                                        + ref
                                        + "' has already been imported into this account");
                        continue; // Authoritative match ΓÇö skip fuzzy check
                    }
                }
            }

            // ΓöÇΓöÇ Tier 2: fuzzy date / amount / payee match (all formats)
            // ΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇ
            LocalDate importDate = tx.getTransactionDate();
            BigDecimal importAmount = tx.getAmount().abs();

            for (Transaction existing : existingTransactions) {
                // Date within ┬▒1 day
                long daysDiff = Math.abs(existing.getDate().toEpochDay() - importDate.toEpochDay());
                if (daysDiff > 1) {
                    continue;
                }

                // Amount match ΓÇö use compareTo to handle scale differences (50.00 vs 50.0000)
                if (existing.getAmount().abs().compareTo(importAmount) != 0) {
                    continue;
                }

                // Currency mismatch ΓÇö skip (e.g. USD $6.75 is not a duplicate of EUR Γé¼6.75)
                String importCurrency = tx.getCurrency();
                String existingCurrency = existing.getCurrency();
                if (importCurrency != null
                        && existingCurrency != null
                        && !importCurrency.equalsIgnoreCase(existingCurrency)) {
                    continue;
                }

                // Payee / description similarity
                if (isPayeeSimilar(tx.getPayee(), existing.getDescription())) {
                    markDuplicate(
                            tx,
                            "DUPLICATE: Possible duplicate of transaction on "
                                    + existing.getDate()
                                    + " with description: "
                                    + existing.getDescription());
                    break; // First fuzzy match is sufficient
                }
            }
        }

        long duplicateCount = transactions.stream().filter(this::isDuplicate).count();
        log.debug(
                "Detected {} possible duplicates out of {} transactions (format={}, referenceTiers={})",
                duplicateCount,
                transactions.size(),
                fileFormat,
                useReferenceTiers);
    }

    /**
     * Returns {@code true} when the given file format produces globally-unique
     * transaction IDs that
     * are safe to use as authoritative duplicate-detection keys (Tier 0
     * intra-session and Tier 1 DB
     * exact-match).
     *
     * <p>
     * Only OFX and QFX qualify: their {@code FITID} field is defined by the OFX
     * specification to
     * be unique per financial institution and account.
     *
     * <p>
     * QIF {@code N} fields are paper cheque numbers ΓÇö not unique transaction IDs.
     * CSV reference
     * columns vary wildly by bank export format and may be absent, a cheque number,
     * or an actual
     * unique ID. Neither is safe to use as an authoritative key without
     * format-specific knowledge.
     *
     * @param fileFormat the import file format string (case-insensitive)
     * @return {@code true} for OFX/QFX; {@code false} for QIF, CSV and unknown
     *         formats
     */
    private boolean isReferenceAuthoritative(String fileFormat) {
        return "OFX".equalsIgnoreCase(fileFormat) || "QFX".equalsIgnoreCase(fileFormat);
    }

    /**
     * Format a raw account identifier (e.g. a long bank account number) into a
     * friendlier display
     * name. Long numeric IDs are shortened to show only the last 4 digits (e.g.
     * "00011234567890189"
     * ΓåÆ "Account ΓÇóΓÇóΓÇó0189").
     *
     * @param rawId the raw account identifier from the imported file
     * @return a human-readable account name suggestion
     */
    private String formatAccountSuggestion(String rawId) {
        if (rawId == null || rawId.isBlank())
            return rawId;
        // If it's a long numeric string (looks like a bank account number), abbreviate
        if (rawId.replaceAll("[^0-9]", "").length() >= 9
                && rawId.replaceAll("[^0-9]", "").equals(rawId.replaceAll("\\s", ""))) {
            String last4 = rawId.substring(Math.max(0, rawId.length() - 4));
            return "Account \u2022\u2022\u2022" + last4;
        }
        return rawId;
    }

    /**
     * Mark an imported transaction as a potential duplicate by adding the supplied
     * {@code
     * DUPLICATE:} message to its validation errors and setting the
     * {@code potentialDuplicate} flag.
     *
     * @param tx      the transaction to mark
     * @param message the {@code DUPLICATE:} message to record
     */
    private void markDuplicate(ImportedTransaction tx, String message) {
        tx.addValidationError(message);
        tx.setPotentialDuplicate(true);
    }

    /**
     * Check if two payee strings are similar using Levenshtein distance algorithm.
     * Considers payees
     * similar if they have 85%+ similarity ratio.
     *
     * @param payee1 first payee string
     * @param payee2 second payee string
     * @return true if payees are similar (85%+ match), false otherwise
     *         <p>
     *         Requirement: REQ-2.10.4 (Duplicate transaction detection)
     */
    private boolean isPayeeSimilar(String payee1, String payee2) {
        boolean empty1 = payee1 == null || payee1.isBlank();
        boolean empty2 = payee2 == null || payee2.isBlank();
        if (empty1 && empty2) {
            return true; // Both missing ΓÇö treat as same unknown payee
        }
        if (empty1 || empty2) {
            return false;
        }

        // Normalize: lowercase, trim, remove extra spaces
        String normalized1 = payee1.toLowerCase().trim().replaceAll("\\s+", " ");
        String normalized2 = payee2.toLowerCase().trim().replaceAll("\\s+", " ");

        // Exact match
        if (normalized1.equals(normalized2)) {
            return true;
        }

        // Contains match (one string contains the other)
        if (normalized1.contains(normalized2) || normalized2.contains(normalized1)) {
            return true;
        }

        // Levenshtein distance similarity (85%+ threshold)
        double similarity = calculateStringSimilarity(normalized1, normalized2);
        return similarity >= 0.85;
    }

    /**
     * Suggest categories for imported transactions using intelligent matching. -
     * Exact match on
     * category name (case-insensitive) - Fuzzy match with 80%+ similarity - Marks
     * unknown
     * categories for user review/creation
     *
     * @param transactions list of imported transactions
     * @param userId       the user ID
     *                     <p>
     *                     Requirement: REQ-2.10.3 (Category mapping during import)
     */
    private void suggestCategories(List<ImportedTransaction> transactions, Long userId) {
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

            // Preserve original payee before any modification
            if (tx.getOriginalPayee() == null) {
                tx.setOriginalPayee(tx.getPayee());
            }

            // Requirement REQ-TR-4.2: Skip auto-categorization for rule-matched
            // transactions
            if (ruleMatchedIndices.contains(txIndex)) {
                continue;
            }

            // 1. Try Auto-Categorization based on user history
            Optional<AutoCategorizationService.Prediction> predictionOpt = autoCategorizationService
                    .predictCategoryAndPayee(tx, userId);

            if (predictionOpt.isPresent()) {
                AutoCategorizationService.Prediction prediction = predictionOpt.get();
                tx.setCategory(prediction.suggestedCategoryName());
                tx.setCategorizationConfidence(prediction.confidenceScore());
                if (prediction.suggestedPayee() != null) {
                    tx.setPayee(prediction.suggestedPayee());
                }

                tx.addValidationError(
                        String.format(
                                "AUTO-MATCH: Category and Payee assigned based on past transaction history (Confidence: %.0f%%)",
                                prediction.confidenceScore() * 100));
                log.debug(
                        "Auto-matched transaction: category='{}', payee='{}', confidence={}",
                        prediction.suggestedCategoryName(),
                        prediction.suggestedPayee(),
                        prediction.confidenceScore());
                continue; // Move to next transaction
            }

            if (tx.getCategory() == null || tx.getCategory().trim().isEmpty()) {
                continue; // Skip transactions without category
            }

            String importedCategory = tx.getCategory().trim();
            String normalizedCategory = importedCategory.toLowerCase().trim();

            // Try exact match first
            if (categoryMapExact.containsKey(normalizedCategory)) {
                Category matched = categoryMapExact.get(normalizedCategory);
                String displayName = resolveDisplayName(matched, locale);
                tx.setCategory(displayName); // Store translated category name
                log.debug("Exact match: '{}' ΓåÆ category ID {}", importedCategory, matched.getId());
                continue;
            }

            // Try fuzzy match with 80%+ similarity
            Category bestMatch = null;
            double bestSimilarity = 0.0;

            for (Category cat : userCategories) {
                String displayName = resolveDisplayName(cat, locale);
                double similarity = calculateStringSimilarity(normalizedCategory, displayName.toLowerCase());
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
     * Resolve the display name for a category in the given locale. System
     * categories use their
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
     * Auto-create an account for import when none was selected by the user. Uses
     * the session's
     * suggestedAccountName as the account name, falling back to the session's
     * fileName (without
     * extension) if none is available.
     *
     * @param userId         the user ID
     * @param session        the import session
     * @param encryptionKey  the user's encryption key
     * @param initialBalance the starting balance derived from the imported file
     * @param currency       the currency derived from the imported file
     * @return the ID of the newly created account
     */
    private Long createAccountForImport(
            Long userId,
            ImportSession session,
            SecretKey encryptionKey,
            BigDecimal initialBalance,
            String currency) {
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

        AccountRequest accountRequest = AccountRequest.builder()
                .name(name.trim())
                .type(AccountType.CHECKING)
                .currency(currency != null ? currency : "USD")
                .initialBalance(initialBalance != null ? initialBalance : BigDecimal.ZERO)
                .openingDate(LocalDate.now())
                .build();

        AccountResponse created = accountService.createAccount(userId, accountRequest, encryptionKey);
        log.info(
                "Auto-created account id={} name='{}' for import session {}",
                created.getId(),
                name,
                session.getId());
        return created.getId();
    }

    /**
     * Create a new category for the user during import. Determines category type
     * (INCOME/EXPENSE)
     * based on transaction amount.
     *
     * @param categoryName    the name of the category to create
     * @param userId          the user ID
     * @param transactionType the transaction type (INCOME or EXPENSE)
     * @param encryptionKey   the user's encryption key
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
        CategoryType categoryType = (transactionType == TransactionType.INCOME)
                ? CategoryType.INCOME
                : CategoryType.EXPENSE;

        // Create category using builder pattern
        Category category = Category.builder()
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
            SecretKey encryptionKey) {
        session.setStatus(ImportStatus.IMPORTING);
        importSessionRepository.save(session);

        List<ImportedTransaction> transactions = deserializeTransactions(session.getMetadata());
        List<ImportedTransaction> errorTxs = transactions.stream()
                .filter(ImportedTransaction::hasErrors)
                .collect(Collectors.toList());
        List<ImportedTransaction> validTxs = transactions.stream().filter(tx -> !tx.hasErrors())
                .collect(Collectors.toList());
        List<ImportedTransaction> duplicateTxs = validTxs.stream().filter(this::isDuplicate)
                .collect(Collectors.toList());
        List<ImportedTransaction> toImport = validTxs.stream()
                .filter(tx -> !skipDuplicates || !isDuplicate(tx))
                .collect(Collectors.toList());

        SkroogeImportMetadata skroogeMetadata = extractSkroogeMetadata(session.getMetadata());
        Map<Long, Long> institutionIdsBySource = ensureInstitutions(skroogeMetadata);
        Map<Long, Long> accountIdsBySource = ensureAccounts(skroogeMetadata, institutionIdsBySource, userId,
                encryptionKey);
        Map<Long, Long> categoryIdsBySource = ensureCategories(skroogeMetadata, userId, categoryMappings);

        int imported = 0;
        int saveFailed = 0;
        Set<String> processedTransferGroups = new java.util.HashSet<>();
        Set<Long> affectedAccountIds = new java.util.HashSet<>(accountIdsBySource.values());

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
                    TransactionRequest transferRequest = TransactionRequest.builder()
                            .accountId(fromAccountId)
                            .toAccountId(toAccountId)
                            .type(TransactionType.TRANSFER)
                            .amount(normalizeAmount(importedTx.getAmount()))
                            .currency(resolveTransactionCurrency(importedTx, fromAccountId))
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
                            .tags(
                                    importedTx.getTags() != null
                                            && !importedTx.getTags().isEmpty()
                                                    ? truncate(
                                                            String.join(",", importedTx.getTags()),
                                                            TRANSACTION_TAGS_MAX_LENGTH)
                                                    : null)
                            .build();
                    transactionService.createTransfer(userId, transferRequest, encryptionKey);
                    imported++;
                    continue;
                }

                Long accountId = accountIdsBySource.get(importedTx.getSourceAccountId());
                if (accountId == null) {
                    throw new IllegalStateException(
                            "Unable to resolve account for source account "
                                    + importedTx.getSourceAccountId());
                }
                Transaction transaction = convertToTransaction(
                        importedTx,
                        accountId,
                        userId,
                        categoryMappings,
                        categoryIdsBySource);
                Transaction saved = transactionRepository.save(transaction);
                if (importedTx.isSplitTransaction()) {
                    List<TransactionSplitRequest> splitRequests = buildSplitRequests(
                            importedTx, userId, categoryMappings, categoryIdsBySource);
                    transactionSplitService.validateSplits(
                            saved.getAmount(), saved.getType(), splitRequests);
                    transactionSplitService.saveSplits(
                            saved.getId(), splitRequests, encryptionKey);
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
        session.setImportedCount(imported);
        session.setDuplicateCount(duplicateTxs.size());
        session.setErrorCount(errorTxs.size());
        session.setSkippedCount(duplicatesSkipped + errorTxs.size() + saveFailed);
        session.setStatus(ImportStatus.COMPLETED);
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
                            maxDate -> netWorthRepository.deleteByUserIdAndSnapshotDateBefore(
                                    userId, maxDate));
        } catch (Exception ex) {
            log.warn(
                    "Failed to invalidate net worth snapshots after Skrooge import for user {}: {}",
                    userId,
                    ex.getMessage());
        }

        importSessionRepository.save(session);
        return session;
    }

    private ImportSession confirmImportedAccountImport(
            ImportSession session,
            Long userId,
            Long requestedAccountId,
            Map<String, Long> categoryMappings,
            boolean skipDuplicates,
            SecretKey encryptionKey,
            List<ImportedTransaction> transactions) {
        session.setStatus(ImportStatus.IMPORTING);
        importSessionRepository.save(session);

        List<ImportedTransaction> errorTxs = transactions.stream()
                .filter(ImportedTransaction::hasErrors)
                .collect(Collectors.toList());
        List<ImportedTransaction> validTxs = transactions.stream().filter(tx -> !tx.hasErrors())
                .collect(Collectors.toList());
        List<ImportedTransaction> duplicateTxs = validTxs.stream().filter(this::isDuplicate)
                .collect(Collectors.toList());
        List<ImportedTransaction> toImport = validTxs.stream()
                .filter(tx -> !skipDuplicates || !isDuplicate(tx))
                .collect(Collectors.toList());

        String fileCurrency = extractFileCurrency(session.getMetadata(), userId);
        final Long initialFallbackAccountId = requestedAccountId != null ? requestedAccountId : session.getAccountId();
        Account fallbackAccount = initialFallbackAccountId != null
                ? accountRepository
                        .findByIdAndUserId(initialFallbackAccountId, userId)
                        .orElseThrow(
                                () -> new ResourceNotFoundException(
                                        "Account not found: "
                                                + initialFallbackAccountId))
                : null;

        Map<String, ImportedAccountDescriptor> descriptorsByKey = collectImportedAccounts(toImport, fileCurrency);
        Map<String, Long> accountIdsByKey = ensureImportedAccounts(descriptorsByKey, fallbackAccount, userId,
                encryptionKey);

        Long resolvedFallbackAccountId = initialFallbackAccountId;
        if (resolvedFallbackAccountId == null
                && fallbackAccount == null
                && accountIdsByKey.size() == 1) {
            resolvedFallbackAccountId = accountIdsByKey.values().iterator().next();
        }
        if (resolvedFallbackAccountId == null && requiresFallbackAccount(toImport)) {
            resolvedFallbackAccountId = createAccountForImport(
                    userId, session, encryptionKey, BigDecimal.ZERO, fileCurrency);
        }

        int imported = 0;
        int saveFailed = 0;
        Set<Long> affectedAccountIds = new java.util.HashSet<>(accountIdsByKey.values());
        if (resolvedFallbackAccountId != null) {
            affectedAccountIds.add(resolvedFallbackAccountId);
        }
        Set<String> processedTransferKeys = new java.util.HashSet<>();

        for (ImportedTransaction importedTx : toImport) {
            try {
                Long sourceAccountId = resolveImportedAccountId(
                        importedTx, accountIdsByKey, resolvedFallbackAccountId);

                if (importedTx.isTransfer()
                        && importedTx.getToAccountName() != null
                        && !importedTx.getToAccountName().isBlank()) {
                    Long destinationAccountId = resolveImportedDestinationAccountId(
                            importedTx, accountIdsByKey, resolvedFallbackAccountId);
                    if (sourceAccountId == null || destinationAccountId == null) {
                        throw new IllegalStateException(
                                "Unable to resolve transfer accounts for imported transaction");
                    }

                    String transferKey = buildImportedTransferKey(
                            importedTx, sourceAccountId, destinationAccountId);
                    if (!processedTransferKeys.add(transferKey)) {
                        continue;
                    }

                    TransactionRequest transferRequest = TransactionRequest.builder()
                            .accountId(sourceAccountId)
                            .toAccountId(destinationAccountId)
                            .type(TransactionType.TRANSFER)
                            .amount(normalizeAmount(importedTx.getAmount()))
                            .currency(
                                    resolveTransactionCurrency(importedTx, sourceAccountId))
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
                            .tags(
                                    importedTx.getTags() != null
                                            && !importedTx.getTags().isEmpty()
                                                    ? truncate(
                                                            String.join(",", importedTx.getTags()),
                                                            TRANSACTION_TAGS_MAX_LENGTH)
                                                    : null)
                            .build();
                    transactionService.createTransfer(userId, transferRequest, encryptionKey);
                    affectedAccountIds.add(sourceAccountId);
                    affectedAccountIds.add(destinationAccountId);
                    imported++;
                    continue;
                }

                if (sourceAccountId == null) {
                    throw new IllegalStateException(
                            "Unable to resolve account for imported transaction");
                }

                Transaction transaction = convertToTransaction(importedTx, sourceAccountId, userId, categoryMappings);
                Transaction saved = transactionRepository.save(transaction);
                if (importedTx.isSplitTransaction()) {
                    List<TransactionSplitRequest> splitRequests = buildSplitRequests(importedTx, userId,
                            categoryMappings, Map.of());
                    transactionSplitService.validateSplits(
                            saved.getAmount(), saved.getType(), splitRequests);
                    transactionSplitService.saveSplits(saved.getId(), splitRequests, encryptionKey);
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
        session.setImportedCount(imported);
        session.setDuplicateCount(duplicateTxs.size());
        session.setErrorCount(errorTxs.size());
        session.setSkippedCount(duplicatesSkipped + errorTxs.size() + saveFailed);
        session.setStatus(ImportStatus.COMPLETED);
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
                            maxDate -> netWorthRepository.deleteByUserIdAndSnapshotDateBefore(
                                    userId, maxDate));
        } catch (Exception ex) {
            log.warn(
                    "Failed to invalidate net worth snapshots after multi-account import for user {}: {}",
                    userId,
                    ex.getMessage());
        }

        importSessionRepository.save(session);
        return session;
    }

    private Map<Long, Long> ensureInstitutions(SkroogeImportMetadata metadata) {
        Map<Long, Long> institutionIdsBySource = new HashMap<>();
        List<Institution> existingInstitutions = institutionRepository.findAll();
        for (SkroogeImportMetadata.SkroogeInstitution institution : metadata.getInstitutions()) {
            if (institution.getName() == null || institution.getName().isBlank()) {
                log.warn(
                        "Skipping Skrooge institution {} because it has no usable name",
                        institution.getSourceId());
                continue;
            }
            Institution existing = existingInstitutions.stream()
                    .filter(
                            candidate -> candidate.getName() != null
                                    && candidate
                                            .getName()
                                            .equalsIgnoreCase(
                                                    institution.getName()))
                    .findFirst()
                    .orElseGet(
                            () -> institutionRepository.save(
                                    Institution.builder()
                                            .name(institution.getName())
                                            .country(institution.getCountry())
                                            .logo(institution.getLogo())
                                            .isSystem(false)
                                            .build()));
            institutionIdsBySource.put(institution.getSourceId(), existing.getId());
        }
        return institutionIdsBySource;
    }

    private Map<Long, Long> ensureAccounts(
            SkroogeImportMetadata metadata,
            Map<Long, Long> institutionIdsBySource,
            Long userId,
            SecretKey encryptionKey) {
        Map<Long, Long> accountIdsBySource = new HashMap<>();
        List<Account> existingAccounts = accountRepository.findByUserId(userId);
        for (SkroogeImportMetadata.SkroogeAccount account : metadata.getAccounts()) {
            Account matchingAccount = findMatchingAccount(existingAccounts, account, encryptionKey);
            if (matchingAccount == null) {
                AccountRequest accountRequest = AccountRequest.builder()
                        .name(account.getName())
                        .type(
                                account.getAccountType() != null
                                        ? account.getAccountType()
                                        : AccountType.OTHER)
                        .currency(
                                account.getCurrency() != null
                                        ? account.getCurrency()
                                        : "USD")
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
                AccountResponse created = accountService.createAccount(userId, accountRequest, encryptionKey);
                matchingAccount = accountRepository
                        .findById(created.getId())
                        .orElseThrow(
                                () -> new ResourceNotFoundException(
                                        "Account not found: " + created.getId()));
                existingAccounts.add(matchingAccount);
            }
            accountIdsBySource.put(account.getSourceId(), matchingAccount.getId());
        }
        return accountIdsBySource;
    }

    private Account findMatchingAccount(
            List<Account> existingAccounts,
            SkroogeImportMetadata.SkroogeAccount importedAccount,
            SecretKey encryptionKey) {
        for (Account existingAccount : existingAccounts) {
            if (matchesImportedAccount(
                    existingAccount,
                    importedAccount.getName(),
                    importedAccount.getAccountNumber(),
                    encryptionKey)) {
                return existingAccount;
            }
        }
        return null;
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
                    defaultCurrency);
            registerImportedAccountDescriptor(
                    descriptors,
                    transaction.getToAccountName(),
                    null,
                    transaction.getCurrency(),
                    transaction.getTransactionDate(),
                    defaultCurrency);
        }
        return descriptors;
    }

    private void registerImportedAccountDescriptor(
            Map<String, ImportedAccountDescriptor> descriptors,
            String accountName,
            String accountNumber,
            String currency,
            LocalDate transactionDate,
            String defaultCurrency) {
        String descriptorKey = buildImportedAccountKey(accountName, accountNumber);
        if (descriptorKey == null) {
            return;
        }

        ImportedAccountDescriptor existing = descriptors.get(descriptorKey);
        String resolvedName = accountName != null && !accountName.isBlank()
                ? accountName.trim()
                : existing != null && existing.name() != null
                        ? existing.name()
                        : formatAccountSuggestion(accountNumber);
        String resolvedNumber = accountNumber != null && !accountNumber.isBlank()
                ? accountNumber.trim()
                : existing != null ? existing.accountNumber() : null;
        String resolvedCurrency = currency != null && !currency.isBlank()
                ? currency.trim()
                : existing != null && existing.currency() != null
                        ? existing.currency()
                        : defaultCurrency;
        LocalDate openingDate = existing != null && existing.openingDate() != null
                ? existing.openingDate()
                : transactionDate;
        if (openingDate != null
                && transactionDate != null
                && transactionDate.isBefore(openingDate)) {
            openingDate = transactionDate;
        }

        descriptors.put(
                descriptorKey,
                new ImportedAccountDescriptor(
                        descriptorKey,
                        resolvedName,
                        resolvedNumber,
                        resolvedCurrency,
                        openingDate));
    }

    private Map<String, Long> ensureImportedAccounts(
            Map<String, ImportedAccountDescriptor> descriptorsByKey,
            Account fallbackAccount,
            Long userId,
            SecretKey encryptionKey) {
        Map<String, Long> accountIdsByKey = new HashMap<>();
        List<Account> existingAccounts = accountRepository.findByUserId(userId);
        for (ImportedAccountDescriptor descriptor : descriptorsByKey.values()) {
            Account matchingAccount = fallbackAccount != null
                    && matchesImportedAccount(
                            fallbackAccount,
                            descriptor.name(),
                            descriptor.accountNumber(),
                            encryptionKey)
                                    ? fallbackAccount
                                    : findMatchingAccount(existingAccounts, descriptor, encryptionKey);
            if (matchingAccount == null
                    && fallbackAccount != null
                    && descriptorsByKey.size() == 1) {
                matchingAccount = fallbackAccount;
            }
            if (matchingAccount == null) {
                AccountRequest accountRequest = AccountRequest.builder()
                        .name(descriptor.name())
                        .type(AccountType.CHECKING)
                        .currency(
                                descriptor.currency() != null
                                        ? descriptor.currency()
                                        : "USD")
                        .initialBalance(BigDecimal.ZERO)
                        .openingDate(
                                descriptor.openingDate() != null
                                        ? descriptor.openingDate()
                                        : LocalDate.now())
                        .accountNumber(descriptor.accountNumber())
                        .build();
                AccountResponse created = accountService.createAccount(userId, accountRequest, encryptionKey);
                matchingAccount = accountRepository
                        .findById(created.getId())
                        .orElseThrow(
                                () -> new ResourceNotFoundException(
                                        "Account not found: " + created.getId()));
                existingAccounts.add(matchingAccount);
            }
            accountIdsByKey.put(descriptor.key(), matchingAccount.getId());
        }
        return accountIdsByKey;
    }

    private Account findMatchingAccount(
            List<Account> existingAccounts,
            ImportedAccountDescriptor descriptor,
            SecretKey encryptionKey) {
        for (Account existingAccount : existingAccounts) {
            if (matchesImportedAccount(
                    existingAccount,
                    descriptor.name(),
                    descriptor.accountNumber(),
                    encryptionKey)) {
                return existingAccount;
            }
        }
        return null;
    }

    private boolean matchesImportedAccount(
            Account existingAccount,
            String importedName,
            String importedAccountNumber,
            SecretKey encryptionKey) {
        String existingName = decryptQuietly(existingAccount.getName(), encryptionKey);
        String existingAccountNumber = decryptQuietly(existingAccount.getAccountNumber(), encryptionKey);
        boolean sameName = existingName != null
                && importedName != null
                && existingName.equalsIgnoreCase(importedName);
        boolean sameNumber = importedAccountNumber != null
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
        String descriptorKey = buildImportedAccountKey(importedTx.getAccountName(), importedTx.getAccountNumber());
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
                        transaction -> !transaction.isTransfer()
                                && (transaction.getAccountName() == null
                                        || transaction.getAccountName().isBlank())
                                && (transaction.getAccountNumber() == null
                                        || transaction.getAccountNumber().isBlank()));
    }

    private String buildImportedTransferKey(
            ImportedTransaction transaction, Long sourceAccountId, Long destinationAccountId) {
        long lowerAccountId = Math.min(sourceAccountId, destinationAccountId);
        long higherAccountId = Math.max(sourceAccountId, destinationAccountId);
        return String.join(
                "|",
                transaction.getTransactionDate() != null
                        ? transaction.getTransactionDate().toString()
                        : "",
                normalizeAmount(transaction.getAmount()).toPlainString(),
                String.valueOf(lowerAccountId),
                String.valueOf(higherAccountId));
    }

    private String decryptQuietly(String encryptedValue, SecretKey encryptionKey) {
        if (encryptedValue == null || encryptedValue.isBlank()) {
            return null;
        }
        try {
            return encryptionService.decrypt(encryptedValue, encryptionKey);
        } catch (Exception ex) {
            return null;
        }
    }

    private Map<Long, Long> ensureCategories(
            SkroogeImportMetadata metadata, Long userId, Map<String, Long> categoryMappings) {
        Map<Long, Long> categoryIdsBySource = new HashMap<>();
        List<Category> existingCategories = categoryRepository.findByUserId(userId);
        Map<Long, String> existingPathsById = buildCategoryPaths(existingCategories);

        for (SkroogeImportMetadata.SkroogeCategory category : metadata.getCategories()) {
            Long mappedId = categoryMappings != null ? categoryMappings.get(category.getFullName()) : null;
            if (mappedId == null && categoryMappings != null) {
                mappedId = categoryMappings.get(category.getName());
            }
            if (mappedId != null) {
                categoryIdsBySource.put(category.getSourceId(), mappedId);
                continue;
            }

            Long parentId = category.getParentSourceId() != null
                    ? categoryIdsBySource.get(category.getParentSourceId())
                    : null;
            Category matchingCategory = existingCategories.stream()
                    .filter(
                            existingCategory -> existingCategory.getType() == category.getType())
                    .filter(
                            existingCategory -> Objects.equals(
                                    existingCategory.getParentId(), parentId))
                    .filter(
                            existingCategory -> category.getName()
                                    .equalsIgnoreCase(existingCategory.getName()))
                    .findFirst()
                    .orElse(null);

            if (matchingCategory == null) {
                matchingCategory = categoryRepository.save(
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
        Map<Long, Category> categoriesById = categories.stream()
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
            current = current.getParentId() != null
                    ? categoriesById.get(current.getParentId())
                    : null;
        }
        return String.join(":", segments);
    }

    private List<TransactionSplitRequest> buildSplitRequests(
            ImportedTransaction importedTx,
            Long userId,
            Map<String, Long> categoryMappings,
            Map<Long, Long> categoryIdsBySource) {
        List<Category> userCategories = categoryRepository.findByUserId(userId);
        return importedTx.getSplits().stream()
                .map(
                        splitEntry -> {
                            Long categoryId = splitEntry.getSourceCategoryId() != null
                                    ? categoryIdsBySource.get(
                                            splitEntry.getSourceCategoryId())
                                    : null;
                            if (categoryId == null
                                    && splitEntry.getCategory() != null
                                    && categoryMappings != null) {
                                categoryId = categoryMappings.get(splitEntry.getCategory());
                            }
                            if (categoryId == null && splitEntry.getCategory() != null) {
                                String categoryName = splitEntry.getCategory().contains(":")
                                        ? splitEntry
                                                .getCategory()
                                                .substring(
                                                        splitEntry
                                                                .getCategory()
                                                                .lastIndexOf(':')
                                                                + 1)
                                                .trim()
                                        : splitEntry.getCategory().trim();
                                categoryId = userCategories.stream()
                                        .filter(
                                                category -> category.getName()
                                                        .equalsIgnoreCase(
                                                                categoryName))
                                        .findFirst()
                                        .map(Category::getId)
                                        .orElse(null);
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

    private Map<String, Object> readMetadataMap(String metadata) {
        if (metadata == null || metadata.trim().isEmpty()) {
            return new HashMap<>();
        }
        try {
            return objectMapper.readValue(metadata, new TypeReference<Map<String, Object>>() {
            });
        } catch (JsonProcessingException ex) {
            log.warn("Failed to read import metadata: {}", ex.getMessage());
            return new HashMap<>();
        }
    }

    private String resolveTransactionCurrency(ImportedTransaction importedTx, Long accountId) {
        if (importedTx.getCurrency() != null && !importedTx.getCurrency().isBlank()) {
            return importedTx.getCurrency();
        }
        return accountRepository.findById(accountId).map(Account::getCurrency).orElse("USD");
    }

    private String extractFileCurrency(String metadata, Long userId) {
        Map<String, Object> metadataMap = readMetadataMap(metadata);
        Object fileCurrency = metadataMap.get("fileCurrency");
        if (fileCurrency != null && !fileCurrency.toString().isBlank()) {
            return fileCurrency.toString();
        }
        return userRepository
                .findById(userId)
                .map(User::getBaseCurrency)
                .filter(currency -> currency != null && !currency.isBlank())
                .orElse("USD");
    }

    /**
     * Convert ImportedTransaction to Transaction entity. The
     * {@code referenceNumber} from the
     * imported file is persisted as {@code externalReference} so that future
     * imports can perform
     * fast, authoritative duplicate detection (Tier 1 in
     * {@link #detectDuplicates}).
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
        // Determine transaction type based on amount
        TransactionType transactionType;
        BigDecimal amount = normalizeAmount(importedTx.getAmount());

        if (importedTx.getAmount().compareTo(BigDecimal.ZERO) >= 0) {
            transactionType = TransactionType.INCOME;
        } else {
            transactionType = TransactionType.EXPENSE;
        }

        // Resolve currency code and link to Currency entity
        String currencyCode = importedTx.getCurrency() != null && !importedTx.getCurrency().isBlank()
                ? importedTx.getCurrency()
                : "USD";
        Long currencyId = currencyRepository.findByCode(currencyCode).map(c -> c.getId()).orElse(null);

        // Resolve or create Payee entity
        String payeeName = truncate(importedTx.getPayee(), TRANSACTION_PAYEE_MAX_LENGTH);
        Long payeeId = resolveOrCreatePayeeId(payeeName, userId);

        // Build transaction using builder pattern
        Transaction.TransactionBuilder builder = Transaction.builder()
                .userId(userId)
                .accountId(accountId)
                .date(importedTx.getTransactionDate())
                .amount(amount)
                .currency(currencyCode)
                .currencyId(currencyId)
                .description(
                        truncate(importedTx.getPayee(), TRANSACTION_DESCRIPTION_MAX_LENGTH))
                .notes(truncate(importedTx.getMemo(), TRANSACTION_NOTES_MAX_LENGTH))
                .payee(payeeName)
                .payeeId(payeeId)
                .type(transactionType)
                .externalReference(
                        importedTx.getReferenceNumber()) // Persist for future dedup
                .isDeleted(false);

        // Map category
        if (importedTx.getCategory() != null && !importedTx.getCategory().trim().isEmpty()) {
            String categoryName = importedTx.getCategory().trim();
            Long categoryId = importedTx.getSourceCategoryId() != null
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
                builder.categoryId(categoryId);
            } else {
                // Try to find category by name
                log.debug("No explicit mapping for category '{}', searching by name", categoryName);
                categoryRepository.findByUserId(userId).stream()
                        .filter(cat -> cat.getName().equalsIgnoreCase(categoryName))
                        .findFirst()
                        .ifPresentOrElse(
                                cat -> {
                                    log.debug(
                                            "Found matching category '{}' by name, ID={}",
                                            cat.getName(),
                                            cat.getId());
                                    builder.categoryId(cat.getId());
                                },
                                () -> log.warn(
                                        "Target category '{}' not found and no mapping provided for user {}",
                                        categoryName,
                                        userId));
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
     * Resolve an existing Payee by name (case-insensitive) or create a new one.
     * Returns the payee
     * ID, or null if the payee name is blank.
     */
    private Long resolveOrCreatePayeeId(String payeeName, Long userId) {
        if (payeeName == null || payeeName.isBlank()) {
            return null;
        }
        String trimmed = payeeName.trim();
        // Try to find existing payee visible to this user
        org.openfinance.entity.Payee existing = payeeRepository.findByNameIgnoreCaseAndUser(trimmed, userId);
        if (existing != null) {
            return existing.getId();
        }
        // Create a new user-scoped payee
        org.openfinance.entity.Payee newPayee = org.openfinance.entity.Payee.builder()
                .name(trimmed)
                .userId(userId)
                .isSystem(false)
                .isActive(true)
                .build();
        return payeeRepository.save(newPayee).getId();
    }

    private BigDecimal normalizeAmount(BigDecimal amount) {
        if (amount == null) {
            return null;
        }

        BigDecimal normalized = amount.abs().setScale(4, RoundingMode.HALF_UP);
        if (normalized.compareTo(new BigDecimal("0.01")) < 0) {
            throw new IllegalArgumentException(
                    "Imported amount is below minimum supported value: " + amount);
        }
        int integerDigits = normalized.precision() - normalized.scale();
        if (integerDigits > 15) {
            throw new IllegalArgumentException(
                    "Imported amount exceeds supported precision: " + amount);
        }
        return normalized;
    }

    /**
     * Serialize transactions to JSON string for metadata storage. Uses Jackson
     * ObjectMapper for
     * proper JSON serialization.
     *
     * @param transactions  list of imported transactions
     * @param ledgerBalance starting balance from the file
     * @param fileCurrency  currency from the file
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
     * Deserialize transactions from JSON metadata. Uses Jackson ObjectMapper for
     * proper JSON
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
            Map<String, Object> metadataMap = objectMapper.readValue(metadata,
                    new TypeReference<Map<String, Object>>() {
                    });

            if (!metadataMap.containsKey("transactions")) {
                log.warn("Metadata does not contain transactions field");
                return new ArrayList<>();
            }

            // Deserialize transactions list
            Object transactionsObj = metadataMap.get("transactions");
            return objectMapper.convertValue(
                    transactionsObj, new TypeReference<List<ImportedTransaction>>() {
                    });

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
            LocalDate openingDate) {
    }

    /**
     * Calculate similarity between two strings using Levenshtein distance. Returns
     * similarity ratio
     * from 0.0 (completely different) to 1.0 (identical).
     *
     * @param s1 first string
     * @param s2 second string
     * @return similarity ratio (0.0 to 1.0)
     */
    private double calculateStringSimilarity(String s1, String s2) {
        if (s1 == null || s2 == null) {
            return 0.0;
        }

        // Normalize strings
        String normalized1 = s1.toLowerCase().trim();
        String normalized2 = s2.toLowerCase().trim();

        if (normalized1.equals(normalized2)) {
            return 1.0;
        }

        // Calculate Levenshtein distance
        int distance = levenshteinDistance(normalized1, normalized2);
        int maxLength = Math.max(normalized1.length(), normalized2.length());

        if (maxLength == 0) {
            return 1.0;
        }

        return 1.0 - ((double) distance / maxLength);
    }

    /**
     * Calculate Levenshtein distance between two strings. The Levenshtein distance
     * is the minimum
     * number of single-character edits (insertions, deletions, or substitutions)
     * required to change
     * one string into the other.
     *
     * @param s1 first string
     * @param s2 second string
     * @return the Levenshtein distance
     */
    private int levenshteinDistance(String s1, String s2) {
        int len1 = s1.length();
        int len2 = s2.length();

        // Create DP table
        int[][] dp = new int[len1 + 1][len2 + 1];

        // Initialize base cases
        for (int i = 0; i <= len1; i++) {
            dp[i][0] = i;
        }
        for (int j = 0; j <= len2; j++) {
            dp[0][j] = j;
        }

        // Fill DP table
        for (int i = 1; i <= len1; i++) {
            for (int j = 1; j <= len2; j++) {
                if (s1.charAt(i - 1) == s2.charAt(j - 1)) {
                    dp[i][j] = dp[i - 1][j - 1];
                } else {
                    dp[i][j] = 1 + Math.min(Math.min(dp[i - 1][j], dp[i][j - 1]), dp[i - 1][j - 1]);
                }
            }
        }

        return dp[len1][len2];
    }
}
