package org.openfinance.service.parser;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.StringReader;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.openfinance.dto.ImportedTransaction;
import org.openfinance.util.SplitValidationConstants;
import org.springframework.stereotype.Component;

/**
 * Parser for Quicken Interchange Format (QIF) files.
 *
 * <p>QIF is a text-based format used by Quicken and other financial software for importing and
 * exporting financial transactions.
 *
 * <p>Format Specification: - Each line starts with a single-character code - Transactions are
 * separated by '^' (end-of-entry marker) - File sections start with !Type: header (e.g.,
 * !Type:Bank, !Type:CCard)
 *
 * <p>Supported Field Codes: - D = Date. The QIF convention is US-style MM/DD/YYYY (Quicken is a US
 * product and its documentation uses month-first), so month-first formats are tried first;
 * international day-first variants are still accepted when unambiguous. Two-digit years (including
 * the apostrophe notation 01/15'00) are resolved with a sliding pivot so legacy 19xx dates stay in
 * the past - T = Amount (negative for expenses, positive for income) - U = Amount (duplicate of T,
 * used in newer Quicken versions - fallback if T absent) - P = Payee/Description - M = Memo/Notes -
 * L = Category (may contain class suffix after '/' and/or transfer syntax [AccountName]) - N =
 * Check/Reference number - C = Cleared status (c/cleared, *=cleared, X=reconciled) - A = Address
 * (multiple lines, ignored) - S = Split category - E = Split memo - $ = Split amount - % = Split
 * percentage (recorded but not validated) - F = Reimbursable flag (ignored) - ^ = End of entry
 *
 * <p>Investment Field Codes (!Type:Invst only, per QIF specification): - N = Action (Buy, Sell,
 * ShrsIn, ShrsOut, Div, Interest, etc.) - Y = Security name - I = Price per share - Q = Quantity
 * (number of shares) - T = Total amount - P = Payee/Description - M = Memo - L = Category or
 * transfer syntax [AccountName] - C = Cleared status - $ = Amount transferred (cash leg) - ^ = End
 * of entry
 *
 * <p>!Type directives handled: - Bank, CCard, Cash, Oth A, Oth L, Oth S - standard transaction
 * parsing - Invst - investment transactions (parsed with key fields: N/action, Y/security, I/price,
 * Q/quantity, T/amount) - Memorized - skipped gracefully - Prices - skipped gracefully - Bill,
 * Invoice, Tax - skipped gracefully - !Account - multi-account context: captures account name for
 * subsequent transactions - !Option:AllXfr - logged and ignored
 *
 * <p>Example QIF file:
 *
 * <pre>
 * !Type:Bank
 * D01/15/2024
 * T-45.67
 * PStarbucks
 * MCoffee and snack
 * LFood:Dining/Business
 * ^
 * </pre>
 *
 * Requirements: - REQ-2.5.1.1: File Format Support (QIF parsing) - REQ-2.5.1.3: Import Validation
 * (line numbers, error reporting)
 *
 * @author Open Finance Team
 * @since Sprint 7 - Transaction Import
 */
@Slf4j
@Component
public class QifParser {

    /**
     * Date formats to try when parsing dates from QIF files, in QIF-convention order: month-first
     * (US Quicken convention) before day-first (international Quicken variants). Two-digit years
     * are expanded to four digits via a sliding pivot before parsing, so only 4-digit-year formats
     * are needed here.
     */
    private static final DateTimeFormatter[] DATE_FORMATS = {
        DateTimeFormatter.ofPattern("MM/dd/yyyy", Locale.ROOT), // US convention with 4-digit year
        DateTimeFormatter.ofPattern("dd/MM/yyyy", Locale.ROOT), // International with 4-digit year
        DateTimeFormatter.ofPattern("yyyy-MM-dd", Locale.ROOT), // ISO format
        DateTimeFormatter.ofPattern("M/d/yyyy", Locale.ROOT), // US no leading zeros
        DateTimeFormatter.ofPattern("d/M/yyyy", Locale.ROOT), // International no leading zeros
        // Dash-separated variants
        DateTimeFormatter.ofPattern("MM-dd-yyyy", Locale.ROOT),
        DateTimeFormatter.ofPattern("dd-MM-yyyy", Locale.ROOT),
        DateTimeFormatter.ofPattern("M-d-yyyy", Locale.ROOT),
        DateTimeFormatter.ofPattern("d-M-yyyy", Locale.ROOT),
        // Dot-separated variants
        DateTimeFormatter.ofPattern("MM.dd.yyyy", Locale.ROOT),
        DateTimeFormatter.ofPattern("dd.MM.yyyy", Locale.ROOT),
        DateTimeFormatter.ofPattern("M.d.yyyy", Locale.ROOT),
        DateTimeFormatter.ofPattern("d.M.yyyy", Locale.ROOT),
    };

    /**
     * Set of !Type values that should be skipped entirely (no transaction records parsed from
     * them).
     */
    private static final List<String> SKIP_TYPES =
            List.of("memorized", "prices", "bill", "invoice", "tax");

    /**
     * Parse QIF file and extract transactions using the default parsing context.
     *
     * @param inputStream Input stream of QIF file content
     * @param fileName Original file name for error reporting
     * @return List of imported transactions with validation errors
     * @throws IOException if file reading fails
     */
    public List<ImportedTransaction> parseFile(InputStream inputStream, String fileName)
            throws IOException {
        return parseFile(inputStream, fileName, ImportParseContext.defaults());
    }

    /**
     * Parse QIF file and extract transactions.
     *
     * @param inputStream Input stream of QIF file content
     * @param fileName Original file name for error reporting
     * @param context user-specific parsing preferences (validation message locale)
     * @return List of imported transactions with validation errors
     * @throws IOException if file reading fails
     */
    public List<ImportedTransaction> parseFile(
            InputStream inputStream, String fileName, ImportParseContext context)
            throws IOException {
        log.info("Starting QIF file parsing: {}", fileName);

        List<ImportedTransaction> transactions = new ArrayList<>();

        // Decode once, honouring BOM and legacy single-byte (windows-1252) Quicken exports
        String content = ImportParseSupport.decode(inputStream.readAllBytes());

        try (BufferedReader reader = new BufferedReader(new StringReader(content))) {

            ImportedTransaction.ImportedTransactionBuilder currentTransaction = null;
            String currentAccountType = null;
            // Track the current account name from !Account sections
            String currentAccountName = null;
            // QIF account type from !Account T field (e.g., "Bank", "CCard", "Oth A")
            String currentQifAccountType = null;
            // When true, the next N line inside an !Account block is the account name
            boolean inAccountBlock = false;
            boolean inCategoryType = false;
            boolean skipCurrentType = false;
            boolean isInvestmentType = false;
            String currentCategoryName = null;
            Map<String, Character> categoryTypes = new HashMap<>();
            int lineNumber = 0;
            int transactionStartLine = 0;

            // Variables for split transactions
            List<ImportedTransaction.SplitEntry> currentSplits = new ArrayList<>();
            ImportedTransaction.SplitEntry.SplitEntryBuilder currentSplit = null;

            // U field (alternative amount) — used only if T was not provided
            BigDecimal uAmount = null;
            // Q (quantity) and I (price) for investment transactions — used to compute
            // amount when T is absent (spec-conformant !Type:Invst records).
            BigDecimal invQuantity = null;
            BigDecimal invPrice = null;

            String line;
            while ((line = reader.readLine()) != null) {
                lineNumber++;
                line = line.trim();

                if (line.isEmpty()) {
                    continue;
                }

                char code = line.charAt(0);
                String value = line.length() > 1 ? line.substring(1).trim() : "";

                // --- Directives (! lines) ---
                if (code == '!') {
                    if (line.startsWith("!Type:")) {
                        String typeValue = line.substring("!Type:".length()).trim();
                        currentAccountType = typeValue;
                        String typeLower = typeValue.toLowerCase(Locale.ROOT);
                        inCategoryType = typeLower.startsWith("cat");
                        currentCategoryName = null;
                        skipCurrentType = SKIP_TYPES.stream().anyMatch(typeLower::startsWith);
                        isInvestmentType = typeLower.equals("invst");
                        inAccountBlock = false;
                        log.debug("Found account type: {}", currentAccountType);
                    } else if (line.equalsIgnoreCase("!Account")) {
                        inAccountBlock = true;
                        inCategoryType = false;
                        currentCategoryName = null;
                        skipCurrentType = false; // !Account ends any prior skip section
                        log.debug("Entering !Account block");
                    } else if (line.toLowerCase(Locale.ROOT).startsWith("!option:")) {
                        log.debug("QIF option directive (ignored): {}", line);
                    } else {
                        log.debug("Unknown QIF directive (ignored): {}", line);
                    }
                    continue;
                }

                if (inCategoryType) {
                    if (code == 'N') {
                        currentCategoryName = value;
                    } else if ((code == 'E' || code == 'I') && currentCategoryName != null) {
                        categoryTypes.put(currentCategoryName, code);
                    } else if (code == '^') {
                        currentCategoryName = null;
                    }
                    continue;
                }

                // Skip entire record types we don't process
                if (skipCurrentType) {
                    if (code == '^') {
                        // end of a record in a skipped section — reset nothing special
                    }
                    continue;
                }

                // Handle !Account block: read N (name), T (type), ^ to close it
                if (inAccountBlock) {
                    if (code == 'N') {
                        currentAccountName = value;
                        log.debug("!Account name: {}", currentAccountName);
                    } else if (code == 'T') {
                        currentQifAccountType = value;
                        log.debug("!Account type: {}", currentQifAccountType);
                    } else if (code == '^') {
                        inAccountBlock = false;
                        log.debug(
                                "Exiting !Account block, account={}, type={}",
                                currentAccountName,
                                currentQifAccountType);
                    }
                    // All other lines inside !Account (T=type, D=description, $=balance…)
                    // are irrelevant for transaction parsing — ignore.
                    continue;
                }

                // Investment transaction handling — capture key fields including Q/I for
                // amount computation when T is absent (spec-conformant !Type:Invst records)
                if (isInvestmentType) {
                    switch (code) {
                        case 'D':
                            if (currentTransaction != null) {
                                applyComputedAmountIfNeeded(
                                        currentTransaction,
                                        uAmount,
                                        invQuantity,
                                        invPrice,
                                        true,
                                        categoryTypes);
                                finalizeSplits(currentTransaction, currentSplits);
                                transactions.add(
                                        buildTransaction(
                                                currentTransaction,
                                                transactionStartLine,
                                                fileName,
                                                context.locale()));
                                currentSplits.clear();
                                uAmount = null;
                                invQuantity = null;
                                invPrice = null;
                            }
                            currentTransaction = ImportedTransaction.builder();
                            if (currentAccountName != null) {
                                currentTransaction.accountName(currentAccountName);
                            }
                            String qifType =
                                    currentQifAccountType != null
                                            ? currentQifAccountType
                                            : currentAccountType;
                            if (qifType != null) {
                                currentTransaction.qifAccountType(qifType);
                            }
                            transactionStartLine = lineNumber;
                            uAmount = null;
                            invQuantity = null;
                            invPrice = null;
                            LocalDate invDate = parseDate(value, lineNumber);
                            currentTransaction.transactionDate(invDate);
                            break;
                        case 'T': // total amount
                            if (currentTransaction == null) {
                                currentTransaction = ImportedTransaction.builder();
                                if (currentAccountName != null) {
                                    currentTransaction.accountName(currentAccountName);
                                }
                                String qifAcctType =
                                        currentQifAccountType != null
                                                ? currentQifAccountType
                                                : currentAccountType;
                                if (qifAcctType != null) {
                                    currentTransaction.qifAccountType(qifAcctType);
                                }
                                transactionStartLine = lineNumber;
                            }
                            currentTransaction.amount(
                                    parseAmount(value, lineNumber, currentTransaction));
                            uAmount = null; // T wins over U and Q×I
                            break;
                        case 'U': // alternative amount — only set if T not present
                            uAmount = parseAmount(value, lineNumber, null);
                            break;
                        case 'Q': // quantity (number of shares/units)
                            invQuantity = parseAmount(value, lineNumber, null);
                            break;
                        case 'I': // price per unit
                            invPrice = parseAmount(value, lineNumber, null);
                            break;
                        case 'N': // action (Buy, Sell, …)
                            if (currentTransaction != null) {
                                currentTransaction.referenceNumber(value);
                            }
                            break;
                        case 'Y': // security name → payee
                            if (currentTransaction != null) {
                                currentTransaction.payee(value);
                            }
                            break;
                        case 'M': // memo
                            if (currentTransaction != null) {
                                currentTransaction.memo(value);
                            }
                            break;
                        case 'P': // payee/description — only set if Y (security) hasn't
                            if (currentTransaction != null) {
                                ImportedTransaction peek = currentTransaction.build();
                                if (peek.getPayee() == null || peek.getPayee().isEmpty()) {
                                    currentTransaction.payee(value);
                                }
                            }
                            break;
                        case 'L': // category or transfer syntax [AccountName]
                            if (currentTransaction != null) {
                                parseCategoryField(value, currentTransaction);
                            }
                            break;
                        case 'C': // cleared status
                            if (currentTransaction != null) {
                                currentTransaction.clearedStatus(mapClearedStatus(value));
                            }
                            break;
                        case '$': // amount transferred (cash leg) — stored as a single split
                            if (currentTransaction != null) {
                                BigDecimal transferAmount = parseAmount(value, lineNumber, null);
                                if (transferAmount != null) {
                                    currentSplits.add(
                                            ImportedTransaction.SplitEntry.builder()
                                                    .amount(transferAmount)
                                                    .build());
                                }
                            }
                            break;
                        case '^':
                            if (currentTransaction != null) {
                                applyComputedAmountIfNeeded(
                                        currentTransaction,
                                        uAmount,
                                        invQuantity,
                                        invPrice,
                                        true,
                                        categoryTypes);
                                finalizeSplits(currentTransaction, currentSplits);
                                transactions.add(
                                        buildTransaction(
                                                currentTransaction,
                                                transactionStartLine,
                                                fileName,
                                                context.locale()));
                                currentTransaction = null;
                                currentSplits.clear();
                                uAmount = null;
                                invQuantity = null;
                                invPrice = null;
                            }
                            break;
                        default:
                            log.debug("Line {}: Investment field '{}' — ignored", lineNumber, code);
                            break;
                    }
                    continue;
                }

                // --- Standard transaction fields ---
                switch (code) {
                    case 'D': // Date — starts a new transaction
                        if (currentTransaction != null) {
                            applyComputedAmountIfNeeded(
                                    currentTransaction,
                                    uAmount,
                                    invQuantity,
                                    invPrice,
                                    false,
                                    categoryTypes);
                            finalizeSplits(currentTransaction, currentSplits);
                            transactions.add(
                                    buildTransaction(
                                            currentTransaction,
                                            transactionStartLine,
                                            fileName,
                                            context.locale()));
                            currentSplits.clear();
                            uAmount = null;
                            invQuantity = null;
                            invPrice = null;
                        }
                        currentTransaction = ImportedTransaction.builder();
                        if (currentAccountName != null) {
                            currentTransaction.accountName(currentAccountName);
                        }
                        String standardQifType =
                                currentQifAccountType != null
                                        ? currentQifAccountType
                                        : currentAccountType;
                        if (standardQifType != null) {
                            currentTransaction.qifAccountType(standardQifType);
                        }
                        transactionStartLine = lineNumber;
                        uAmount = null;
                        invQuantity = null;
                        invPrice = null;
                        LocalDate parsedDate = parseDate(value, lineNumber);
                        currentTransaction.transactionDate(parsedDate);
                        if (parsedDate == null) {
                            currentTransaction.lineNumber(lineNumber);
                        }
                        break;

                    case 'T': // Amount
                        if (currentTransaction == null) {
                            currentTransaction = ImportedTransaction.builder();
                            if (currentAccountName != null) {
                                currentTransaction.accountName(currentAccountName);
                            }
                            String qifType =
                                    currentQifAccountType != null
                                            ? currentQifAccountType
                                            : currentAccountType;
                            if (qifType != null) {
                                currentTransaction.qifAccountType(qifType);
                            }
                            transactionStartLine = lineNumber;
                            log.warn("Line {}: Transaction started without date 'D'", lineNumber);
                        }
                        BigDecimal amount = parseAmount(value, lineNumber, currentTransaction);
                        currentTransaction.amount(amount);
                        uAmount = null; // T takes priority over U
                        break;

                    case 'U': // Alternative amount (newer Quicken) — used only if T absent
                        uAmount = parseAmount(value, lineNumber, null);
                        break;

                    case 'P': // Payee
                        if (currentTransaction != null) {
                            currentTransaction.payee(value);
                        } else {
                            log.warn(
                                    "Line {}: Payee 'P' found before transaction start 'D'",
                                    lineNumber);
                        }
                        break;

                    case 'M': // Memo
                        if (currentTransaction != null) {
                            currentTransaction.memo(value);
                        } else {
                            log.warn(
                                    "Line {}: Memo 'M' found before transaction start 'D'",
                                    lineNumber);
                        }
                        break;

                    case 'L': // Category (with optional class and/or transfer)
                        if (currentTransaction != null) {
                            parseCategoryField(value, currentTransaction);
                        } else {
                            log.warn(
                                    "Line {}: Category 'L' found before transaction start 'D'",
                                    lineNumber);
                        }
                        break;

                    case 'N': // Check/Reference number
                        if (currentTransaction != null) {
                            currentTransaction.referenceNumber(value);
                        } else {
                            log.warn(
                                    "Line {}: Reference 'N' found before transaction start 'D'",
                                    lineNumber);
                        }
                        break;

                    case 'C': // Cleared status
                        if (currentTransaction != null) {
                            currentTransaction.clearedStatus(mapClearedStatus(value));
                        } else {
                            log.warn(
                                    "Line {}: Cleared status 'C' found before transaction start 'D'",
                                    lineNumber);
                        }
                        break;

                    case 'S': // Split category
                        if (currentTransaction != null) {
                            if (currentSplit != null) {
                                currentSplits.add(currentSplit.build());
                            }
                            currentSplit = ImportedTransaction.SplitEntry.builder();
                            // Strip class suffix from split category
                            currentSplit.category(stripClassSuffix(value));
                        } else {
                            log.warn(
                                    "Line {}: Split category 'S' found before transaction start 'D'",
                                    lineNumber);
                        }
                        break;

                    case 'E': // Split memo
                        if (currentSplit != null) {
                            currentSplit.memo(value);
                        } else {
                            log.warn(
                                    "Line {}: Split memo 'E' found before split category 'S'",
                                    lineNumber);
                        }
                        break;

                    case '$': // Split amount
                        if (currentSplit != null) {
                            BigDecimal splitAmount = parseAmount(value, lineNumber, null);
                            currentSplit.amount(splitAmount);
                        } else {
                            log.warn(
                                    "Line {}: Split amount '$' found before split category 'S'",
                                    lineNumber);
                        }
                        break;

                    case '%': // Split percentage — record in split memo for transparency, skip
                        // validation
                        log.debug(
                                "Line {}: Split percentage '%' field (recorded, not validated)",
                                lineNumber);
                        break;

                    case 'F': // Reimbursable business expense flag — informational only
                        log.debug("Line {}: Reimbursable flag 'F' (ignored)", lineNumber);
                        break;

                    case '^': // End of entry
                        if (currentTransaction != null) {
                            if (currentSplit != null) {
                                currentSplits.add(currentSplit.build());
                                currentSplit = null;
                            }
                            applyComputedAmountIfNeeded(
                                    currentTransaction,
                                    uAmount,
                                    invQuantity,
                                    invPrice,
                                    false,
                                    categoryTypes);
                            finalizeSplits(currentTransaction, currentSplits);
                            transactions.add(
                                    buildTransaction(
                                            currentTransaction,
                                            transactionStartLine,
                                            fileName,
                                            context.locale()));
                            currentTransaction = null;
                            currentSplits.clear();
                            uAmount = null;
                            invQuantity = null;
                            invPrice = null;
                        }
                        break;

                    case 'A': // Address — ignored
                        break;

                    default:
                        log.debug("Line {}: Unknown QIF code '{}' - ignoring", lineNumber, code);
                        break;
                }
            }

            // Add last transaction if file doesn't end with '^'
            if (currentTransaction != null) {
                applyComputedAmountIfNeeded(
                        currentTransaction,
                        uAmount,
                        invQuantity,
                        invPrice,
                        isInvestmentType,
                        categoryTypes);
                finalizeSplits(currentTransaction, currentSplits);
                transactions.add(
                        buildTransaction(
                                currentTransaction,
                                transactionStartLine,
                                fileName,
                                context.locale()));
            }
        }

        log.info(
                "QIF parsing complete: {} transactions parsed from {}",
                transactions.size(),
                fileName);
        return transactions;
    }

    /**
     * Apply amount from U field or Q × I computation when T was never provided. Priority: T
     * (already set) > U > Q × I. The Q × I path is spec-conformant for !Type:Invst records where
     * the total (T) may be omitted and must be derived from quantity × price.
     */
    private void applyComputedAmountIfNeeded(
            ImportedTransaction.ImportedTransactionBuilder builder,
            BigDecimal uAmount,
            BigDecimal invQuantity,
            BigDecimal invPrice,
            boolean investmentTransaction,
            Map<String, Character> categoryTypes) {
        ImportedTransaction peek = builder.build();
        if (peek.getAmount() != null) {
            return; // T was set
        }
        if (uAmount != null) {
            builder.amount(uAmount);
            return;
        }
        // Q × I amount derivation is only valid for investment transactions (!Type:Invst) per
        // the QIF specification. Standard Bank/Cash/CCard records must carry T or U.
        if (investmentTransaction && invQuantity != null && invPrice != null) {
            try {
                BigDecimal computed = invQuantity.multiply(invPrice);
                if (isSellAction(peek.getReferenceNumber())) {
                    computed = computed.abs().negate();
                }
                builder.amount(computed);
            } catch (ArithmeticException e) {
                log.debug("Could not compute investment amount from Q × I: {}", e.getMessage());
            }
        }
    }

    /**
     * QIF investment actions are English keywords per the format specification (Buy, Sell, ShrsIn,
     * ShrsOut, …), so an English "sell" check is spec-based, not locale-based.
     */
    private boolean isSellAction(String action) {
        return action != null && action.trim().toLowerCase(Locale.ROOT).startsWith("sell");
    }

    /**
     * Parse the L (category) field, handling: - Transfer syntax: [AccountName] - Category class
     * separator: Category/Class or Category:SubCat/Class
     */
    private void parseCategoryField(
            String value, ImportedTransaction.ImportedTransactionBuilder builder) {
        int slashPos = value.indexOf('/');
        String categoryValue = slashPos >= 0 ? value.substring(0, slashPos).trim() : value;
        String classValue = slashPos >= 0 ? value.substring(slashPos + 1).trim() : null;

        if (categoryValue.startsWith("[") && categoryValue.endsWith("]")) {
            String accountName = categoryValue.substring(1, categoryValue.length() - 1).trim();
            builder.transfer(true);
            builder.toAccountName(accountName);
            // QIF transfer syntax [Account] carries its semantics in the transfer flag and
            // target account — no synthetic category is invented for it.
            if (classValue != null && !classValue.isEmpty()) {
                ImportedTransaction peek = builder.build();
                List<String> tags = new ArrayList<>(peek.getTags());
                tags.add(classValue);
                builder.tags(tags);
            }
        } else {
            if (slashPos >= 0) {
                String category = categoryValue;
                builder.category(category.isEmpty() ? null : category);
                if (classValue != null && !classValue.isEmpty()) {
                    // Store class as a tag on the transaction
                    ImportedTransaction peek = builder.build();
                    List<String> tags = new ArrayList<>(peek.getTags());
                    tags.add(classValue);
                    builder.tags(tags);
                }
            } else {
                builder.category(value);
            }
        }
    }

    /** Strip class suffix from a category string (text after '/'). */
    private String stripClassSuffix(String category) {
        int slashPos = category.indexOf('/');
        return slashPos >= 0 ? category.substring(0, slashPos).trim() : category;
    }

    /** Finalize split transaction entries and add to transaction builder. */
    private void finalizeSplits(
            ImportedTransaction.ImportedTransactionBuilder transaction,
            List<ImportedTransaction.SplitEntry> splits) {
        if (!splits.isEmpty()) {
            ImportedTransaction peek = transaction.build();
            boolean amountFromSplits = peek.getAmount() == null;
            transaction.splits(new ArrayList<>(splits));
            if (amountFromSplits) {
                BigDecimal totalSplitAmount =
                        splits.stream()
                                .map(ImportedTransaction.SplitEntry::getAmount)
                                .filter(amount -> amount != null)
                                .reduce(BigDecimal.ZERO, BigDecimal::add);
                transaction.amount(totalSplitAmount);
            }
        }
    }

    /** Build final ImportedTransaction and perform validation. */
    private ImportedTransaction buildTransaction(
            ImportedTransaction.ImportedTransactionBuilder builder,
            int lineNumber,
            String fileName,
            Locale locale) {
        builder.lineNumber(lineNumber);
        builder.sourceFileName(fileName);

        ImportedTransaction transaction = builder.build();
        validateTransaction(transaction, locale);

        return transaction;
    }

    /** Validate imported transaction and add errors. */
    private void validateTransaction(ImportedTransaction transaction, Locale locale) {
        if (transaction.getTransactionDate() == null) {
            transaction.addValidationError(
                    ImportParseSupport.message("import.validation.date.required", locale));
        }

        if (transaction.getAmount() == null) {
            transaction.addValidationError(
                    ImportParseSupport.message("import.validation.amount.required", locale));
        } else if (transaction.getAmount().compareTo(BigDecimal.ZERO) == 0) {
            transaction.addValidationError(
                    ImportParseSupport.message("import.validation.amount.zero", locale));
        }

        if (transaction.getTransactionDate() != null
                && transaction.getTransactionDate().isAfter(LocalDate.now())) {
            transaction.addValidationError(
                    ImportParseSupport.message("import.validation.date.future", locale));
        }

        if (transaction.isSplitTransaction()) {
            BigDecimal totalSplitAmount =
                    transaction.getSplits().stream()
                            .map(ImportedTransaction.SplitEntry::getAmount)
                            .filter(amt -> amt != null)
                            .reduce(BigDecimal.ZERO, BigDecimal::add);

            if (transaction.getAmount() != null) {
                // QIF split amounts are stored as positive values regardless of the parent
                // transaction sign — compare absolute values to handle expense splits
                // correctly.
                // Allow ±0.01 tolerance to match the service-layer validation and handle
                // rounding differences (e.g. three-way splits of non-divisible amounts).
                BigDecimal difference =
                        totalSplitAmount.abs().subtract(transaction.getAmount().abs()).abs();
                if (difference.compareTo(SplitValidationConstants.SPLIT_SUM_TOLERANCE) > 0) {
                    transaction.addValidationError(
                            ImportParseSupport.message(
                                    "import.validation.split.mismatch",
                                    locale,
                                    totalSplitAmount,
                                    transaction.getAmount()));
                }
            }
        }
    }

    /**
     * Parse date from QIF file, trying the known formats in QIF-convention order (month-first
     * first).
     *
     * <p>Handles Quicken's legacy notations before parsing: - Space-padded single-digit parts
     * (e.g., "1/ 6'01") - Apostrophe-notation years (e.g., 01/15'00 → 01/15/00) - Two-digit years,
     * expanded with a sliding pivot so 19xx legacy dates stay in the past - Separator variants:
     * '/', '-', '.'
     */
    private LocalDate parseDate(String dateStr, int lineNumber) {
        if (dateStr == null || dateStr.isEmpty()) {
            log.warn("Line {}: Empty date string", lineNumber);
            return null;
        }

        // Quicken pads single-digit date parts with spaces ("D1/ 6'01")
        String normalized = dateStr.replace(" ", "");
        // Apostrophe year notation ("01/15'00") — the apostrophe acts as the year separator
        if (normalized.indexOf('\'') >= 0) {
            char separator =
                    normalized.indexOf('/') >= 0
                            ? '/'
                            : normalized.indexOf('-') >= 0
                                    ? '-'
                                    : normalized.indexOf('.') >= 0 ? '.' : '/';
            normalized = normalized.replace('\'', separator);
        }
        // Expand a trailing two-digit year via the sliding pivot (legacy 19xx dates)
        normalized = ImportParseSupport.expandTwoDigitYear(normalized);

        for (DateTimeFormatter formatter : DATE_FORMATS) {
            try {
                return LocalDate.parse(normalized, formatter);
            } catch (DateTimeParseException e) {
                // Try next format
            }
        }

        log.warn(
                "Line {}: Unable to parse date '{}' - tried all known formats",
                lineNumber,
                dateStr);
        return null;
    }

    /**
     * Parse amount from QIF file.
     *
     * <p>Delegates to the shared lenient amount parser, which handles US ("1,234.56") and European
     * ("1.234,56" / "1234,56") formats and strips currency symbols ($, €, £).
     */
    private BigDecimal parseAmount(
            String amountStr,
            int lineNumber,
            ImportedTransaction.ImportedTransactionBuilder transaction) {
        if (amountStr == null || amountStr.isEmpty()) {
            log.warn("Line {}: Empty amount string", lineNumber);
            return null;
        }
        BigDecimal amount = ImportParseSupport.parseLenientAmount(amountStr);
        if (amount == null) {
            log.warn("Line {}: Unable to parse amount '{}'", lineNumber, amountStr);
        }
        return amount;
    }

    /** Map QIF cleared status codes to readable values. */
    private String mapClearedStatus(String status) {
        if (status == null || status.isEmpty()) {
            return "uncleared";
        }
        switch (status.toLowerCase(Locale.ROOT)) {
            case "c":
            case "*":
                return "cleared";
            case "x":
            case "r":
                return "reconciled";
            default:
                return "uncleared";
        }
    }
}
