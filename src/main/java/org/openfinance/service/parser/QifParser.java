package org.openfinance.service.parser;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.openfinance.dto.ImportedTransaction;
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
 * <p>Supported Field Codes: - D = Date (formats: MM/DD/YYYY, DD/MM/YYYY, MM/DD/YY, DD/MM/YY, and
 * variants with - or . separators) - T = Amount (negative for expenses, positive for income) - U =
 * Amount (duplicate of T, used in newer Quicken versions - fallback if T absent) - P =
 * Payee/Description - M = Memo/Notes - L = Category (may contain class suffix after '/' and/or
 * transfer syntax [AccountName]) - N = Check/Reference number - C = Cleared status (c/cleared,
 * *=cleared, X=reconciled) - A = Address (multiple lines, ignored) - S = Split category - E = Split
 * memo - $ = Split amount - % = Split percentage (recorded but not validated) - F = Reimbursable
 * flag (ignored) - ^ = End of entry
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
     * Date formats to try when parsing dates from QIF files. Includes variants with '/', '-', and
     * '.' separators. Order matters — try most specific / common formats first.
     */
    private static final DateTimeFormatter[] DATE_FORMATS = {
        DateTimeFormatter.ofPattern("MM/dd/yyyy"), // US with 4-digit year
        DateTimeFormatter.ofPattern("dd/MM/yyyy"), // International with 4-digit year
        DateTimeFormatter.ofPattern("yyyy-MM-dd"), // ISO format
        DateTimeFormatter.ofPattern("MM/dd/yy"), // US with 2-digit year
        DateTimeFormatter.ofPattern("dd/MM/yy"), // International with 2-digit year
        DateTimeFormatter.ofPattern("M/d/yyyy"), // US no leading zeros
        DateTimeFormatter.ofPattern("d/M/yyyy"), // International no leading zeros
        DateTimeFormatter.ofPattern("M/d/yy"), // US 2-digit year no leading zeros
        DateTimeFormatter.ofPattern("d/M/yy"), // International 2-digit year no leading zeros
        // Dash-separated variants
        DateTimeFormatter.ofPattern("MM-dd-yyyy"),
        DateTimeFormatter.ofPattern("dd-MM-yyyy"),
        DateTimeFormatter.ofPattern("MM-dd-yy"),
        DateTimeFormatter.ofPattern("dd-MM-yy"),
        DateTimeFormatter.ofPattern("M-d-yyyy"),
        DateTimeFormatter.ofPattern("d-M-yyyy"),
        // Dot-separated variants
        DateTimeFormatter.ofPattern("MM.dd.yyyy"),
        DateTimeFormatter.ofPattern("dd.MM.yyyy"),
        DateTimeFormatter.ofPattern("MM.dd.yy"),
        DateTimeFormatter.ofPattern("dd.MM.yy"),
        DateTimeFormatter.ofPattern("M.d.yyyy"),
        DateTimeFormatter.ofPattern("d.M.yyyy"),
    };

    /**
     * Set of !Type values that should be skipped entirely (no transaction records parsed from
     * them).
     */
    private static final List<String> SKIP_TYPES =
            List.of("memorized", "prices", "bill", "invoice", "tax");

    /** Tolerance for split-sum validation (±0.01), consistent with TransactionSplitService. */
    private static final BigDecimal SPLIT_SUM_TOLERANCE = new BigDecimal("0.01");

    /**
     * Parse QIF file and extract transactions.
     *
     * @param inputStream Input stream of QIF file content
     * @param fileName Original file name for error reporting
     * @return List of imported transactions with validation errors
     * @throws IOException if file reading fails
     */
    public List<ImportedTransaction> parseFile(InputStream inputStream, String fileName)
            throws IOException {
        log.info("Starting QIF file parsing: {}", fileName);

        List<ImportedTransaction> transactions = new ArrayList<>();

        try (BufferedReader reader =
                new BufferedReader(new InputStreamReader(inputStream, StandardCharsets.UTF_8))) {

            ImportedTransaction.ImportedTransactionBuilder currentTransaction = null;
            String currentAccountType = null;
            // Track the current account name from !Account sections
            String currentAccountName = null;
            // When true, the next N line inside an !Account block is the account name
            boolean inAccountBlock = false;
            boolean skipCurrentType = false;
            boolean isInvestmentType = false;
            int lineNumber = 0;
            int transactionStartLine = 0;

            // Variables for split transactions
            List<ImportedTransaction.SplitEntry> currentSplits = new ArrayList<>();
            ImportedTransaction.SplitEntry.SplitEntryBuilder currentSplit = null;

            // U field (alternative amount) — used only if T was not provided
            BigDecimal uAmount = null;

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
                        String typeLower = typeValue.toLowerCase();
                        skipCurrentType = SKIP_TYPES.stream().anyMatch(typeLower::startsWith);
                        isInvestmentType = typeLower.equalsIgnoreCase("invst");
                        inAccountBlock = false;
                        log.debug("Found account type: {}", currentAccountType);
                    } else if (line.equalsIgnoreCase("!Account")) {
                        inAccountBlock = true;
                        log.debug("Entering !Account block");
                    } else if (line.toLowerCase().startsWith("!option:")) {
                        log.debug("QIF option directive (ignored): {}", line);
                    } else {
                        log.debug("Unknown QIF directive (ignored): {}", line);
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
                    } else if (code == '^') {
                        inAccountBlock = false;
                        log.debug("Exiting !Account block, account={}", currentAccountName);
                    }
                    // All other lines inside !Account (T=type, D=description, $=balance…)
                    // are irrelevant for transaction parsing — ignore.
                    continue;
                }

                // Investment transaction handling — minimal: capture key fields
                if (isInvestmentType) {
                    switch (code) {
                        case 'D':
                            if (currentTransaction != null) {
                                finalizeSplits(currentTransaction, currentSplits);
                                transactions.add(
                                        buildTransaction(
                                                currentTransaction,
                                                transactionStartLine,
                                                fileName));
                                currentSplits.clear();
                                uAmount = null;
                            }
                            currentTransaction = ImportedTransaction.builder();
                            if (currentAccountName != null) {
                                currentTransaction.accountName(currentAccountName);
                            }
                            transactionStartLine = lineNumber;
                            uAmount = null;
                            LocalDate invDate = parseDate(value, lineNumber);
                            currentTransaction.transactionDate(invDate);
                            break;
                        case 'T': // total amount
                            if (currentTransaction == null) {
                                currentTransaction = ImportedTransaction.builder();
                                if (currentAccountName != null) {
                                    currentTransaction.accountName(currentAccountName);
                                }
                                transactionStartLine = lineNumber;
                            }
                            currentTransaction.amount(
                                    parseAmount(value, lineNumber, currentTransaction));
                            uAmount = null; // T wins over U
                            break;
                        case 'U': // alternative amount — only set if T not present
                            uAmount = parseAmount(value, lineNumber, null);
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
                        case 'P': // first line of payee/description (some exporters use P here)
                            if (currentTransaction != null) {
                                currentTransaction.payee(value);
                            }
                            break;
                        case '^':
                            if (currentTransaction != null) {
                                // Apply U amount if T was never set
                                applyUAmountIfNeeded(currentTransaction, uAmount);
                                finalizeSplits(currentTransaction, currentSplits);
                                transactions.add(
                                        buildTransaction(
                                                currentTransaction,
                                                transactionStartLine,
                                                fileName));
                                currentTransaction = null;
                                currentSplits.clear();
                                uAmount = null;
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
                            applyUAmountIfNeeded(currentTransaction, uAmount);
                            finalizeSplits(currentTransaction, currentSplits);
                            transactions.add(
                                    buildTransaction(
                                            currentTransaction, transactionStartLine, fileName));
                            currentSplits.clear();
                            uAmount = null;
                        }
                        currentTransaction = ImportedTransaction.builder();
                        if (currentAccountName != null) {
                            currentTransaction.accountName(currentAccountName);
                        }
                        transactionStartLine = lineNumber;
                        uAmount = null;
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
                            applyUAmountIfNeeded(currentTransaction, uAmount);
                            finalizeSplits(currentTransaction, currentSplits);
                            transactions.add(
                                    buildTransaction(
                                            currentTransaction, transactionStartLine, fileName));
                            currentTransaction = null;
                            currentSplits.clear();
                            uAmount = null;
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
                applyUAmountIfNeeded(currentTransaction, uAmount);
                finalizeSplits(currentTransaction, currentSplits);
                transactions.add(
                        buildTransaction(currentTransaction, transactionStartLine, fileName));
            }
        }

        log.info(
                "QIF parsing complete: {} transactions parsed from {}",
                transactions.size(),
                fileName);
        return transactions;
    }

    /** Apply U-field amount to the builder when T was never provided. */
    private void applyUAmountIfNeeded(
            ImportedTransaction.ImportedTransactionBuilder builder, BigDecimal uAmount) {
        if (uAmount == null) {
            return;
        }
        // Peek: if amount is already set via T, skip
        ImportedTransaction peek = builder.build();
        if (peek.getAmount() == null) {
            builder.amount(uAmount);
        }
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
            builder.category("Transfer");
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
            transaction.splits(new ArrayList<>(splits));
        }
    }

    /** Build final ImportedTransaction and perform validation. */
    private ImportedTransaction buildTransaction(
            ImportedTransaction.ImportedTransactionBuilder builder,
            int lineNumber,
            String fileName) {
        builder.lineNumber(lineNumber);
        builder.sourceFileName(fileName);

        ImportedTransaction transaction = builder.build();
        validateTransaction(transaction);

        return transaction;
    }

    /** Validate imported transaction and add errors. */
    private void validateTransaction(ImportedTransaction transaction) {
        if (transaction.getTransactionDate() == null) {
            transaction.addValidationError("Transaction date is required");
        }

        if (transaction.getAmount() == null) {
            transaction.addValidationError("Transaction amount is required");
        } else if (transaction.getAmount().compareTo(BigDecimal.ZERO) == 0) {
            transaction.addValidationError("Transaction amount cannot be zero");
        }

        if (transaction.getTransactionDate() != null
                && transaction.getTransactionDate().isAfter(LocalDate.now())) {
            transaction.addValidationError("Transaction date cannot be in the future");
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
                if (difference.compareTo(SPLIT_SUM_TOLERANCE) > 0) {
                    transaction.addValidationError(
                            String.format(
                                    "Split amounts (%s) do not match transaction amount (%s)",
                                    totalSplitAmount, transaction.getAmount()));
                }
            }
        }
    }

    /**
     * Parse date from QIF file, trying multiple date formats.
     *
     * <p>Handles: - Apostrophe-notation for post-2000 years (e.g., 01/15'00 → 01/1500 → parsed with
     * 2-digit year) - Separator variants: '/', '-', '.' - European number format dates are
     * disambiguated by trying all formats in order
     */
    private LocalDate parseDate(String dateStr, int lineNumber) {
        if (dateStr == null || dateStr.isEmpty()) {
            log.warn("Line {}: Empty date string", lineNumber);
            return null;
        }

        // Remove apostrophes (Quicken post-2000 year notation, e.g., '00 → 00)
        dateStr = dateStr.replace("'", "");

        for (DateTimeFormatter formatter : DATE_FORMATS) {
            try {
                return LocalDate.parse(dateStr, formatter);
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
     * <p>Handles: - Standard US format: 1,234.56 - European format: 1.234,56 (detected when comma
     * comes after dot) - Currency symbol stripping: $, €, £
     */
    private BigDecimal parseAmount(
            String amountStr,
            int lineNumber,
            ImportedTransaction.ImportedTransactionBuilder transaction) {
        if (amountStr == null || amountStr.isEmpty()) {
            log.warn("Line {}: Empty amount string", lineNumber);
            return null;
        }

        try {
            // Remove currency symbols and whitespace
            String cleaned =
                    amountStr.replace("$", "").replace("€", "").replace("£", "").replace(" ", "");

            // Detect European format: dot used as thousands separator, comma as decimal
            // e.g., "1.234,56" → "1234.56"
            // If the string contains both a dot and a comma, and the comma appears AFTER
            // the last dot, it is European format.
            int lastDot = cleaned.lastIndexOf('.');
            int lastComma = cleaned.lastIndexOf(',');
            if (lastDot >= 0 && lastComma > lastDot) {
                // European: remove dots (thousands sep), replace comma with dot (decimal sep)
                cleaned = cleaned.replace(".", "").replace(",", ".");
            } else {
                // Standard: remove commas (thousands sep); dot is already decimal sep
                cleaned = cleaned.replace(",", "");
            }

            return new BigDecimal(cleaned);
        } catch (NumberFormatException e) {
            log.warn("Line {}: Unable to parse amount '{}'", lineNumber, amountStr);
            return null;
        }
    }

    /** Map QIF cleared status codes to readable values. */
    private String mapClearedStatus(String status) {
        if (status == null || status.isEmpty()) {
            return "uncleared";
        }
        switch (status.toLowerCase()) {
            case "c":
            case "*":
                return "cleared";
            case "x":
                return "reconciled";
            default:
                return "uncleared";
        }
    }
}
