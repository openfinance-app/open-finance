package org.openfinance.service.parser;

import com.opencsv.CSVParserBuilder;
import com.opencsv.CSVReader;
import com.opencsv.CSVReaderBuilder;
import com.opencsv.exceptions.CsvValidationException;
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
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import lombok.extern.slf4j.Slf4j;
import org.openfinance.dto.ImportedTransaction;
import org.springframework.stereotype.Component;

/**
 * Parser for Comma Separated Values (CSV) files.
 *
 * <p>Requirements: - REQ-2.5.1.1: File Format Support (CSV parsing) - REQ-2.5.1.3: Import
 * Validation (line numbers, error reporting) Header format:
 * date,amount,transactionamount,debit,credit,payee,memo,description,category,referencenumber,accountnumber
 */
@Slf4j
@Component
public class CsvParser {

    private static final DateTimeFormatter[] DATE_FORMATS = {
        DateTimeFormatter.ofPattern("MM/dd/yyyy"),
        DateTimeFormatter.ofPattern("dd/MM/yyyy"),
        DateTimeFormatter.ofPattern("yyyy-MM-dd"),
        DateTimeFormatter.ofPattern("M/d/yyyy"),
        DateTimeFormatter.ofPattern("d/M/yyyy"),
        DateTimeFormatter.ofPattern("yyyy/MM/dd"),
        DateTimeFormatter.ofPattern("dd-MM-yyyy"),
        DateTimeFormatter.ofPattern("MM-dd-yyyy"),
        DateTimeFormatter.ofPattern("M-d-yyyy"),
        DateTimeFormatter.ofPattern("d-M-yyyy"),
        DateTimeFormatter.ofPattern("MMM dd, yyyy", Locale.ENGLISH),
        DateTimeFormatter.ofPattern("dd MMM yyyy", Locale.ENGLISH),
        DateTimeFormatter.ofPattern("MMM d, yyyy", Locale.ENGLISH),
        DateTimeFormatter.ofPattern("d MMM yyyy", Locale.ENGLISH),
        DateTimeFormatter.ofPattern("MMMM dd, yyyy", Locale.ENGLISH),
        DateTimeFormatter.ofPattern("dd MMMM yyyy", Locale.ENGLISH),
    };

    private static final Pattern SEMICOLON_PATTERN = Pattern.compile(";");

    /**
     * Pattern matching numeric dates like dd/MM/yyyy or MM-dd-yyyy (1-2 digit parts separated by /
     * or -)
     */
    private static final Pattern NUMERIC_DATE_PATTERN =
            Pattern.compile("^(\\d{1,2})[/\\-](\\d{1,2})[/\\-](\\d{2,4})$");

    /** Date formats with day-first variants prioritised (dd/MM before MM/dd). */
    private static final DateTimeFormatter[] DATE_FORMATS_DAY_FIRST = {
        DateTimeFormatter.ofPattern("dd/MM/yyyy"),
        DateTimeFormatter.ofPattern("d/M/yyyy"),
        DateTimeFormatter.ofPattern("dd-MM-yyyy"),
        DateTimeFormatter.ofPattern("d-M-yyyy"),
        DateTimeFormatter.ofPattern("yyyy-MM-dd"),
        DateTimeFormatter.ofPattern("yyyy/MM/dd"),
        DateTimeFormatter.ofPattern("MM/dd/yyyy"),
        DateTimeFormatter.ofPattern("M/d/yyyy"),
        DateTimeFormatter.ofPattern("MM-dd-yyyy"),
        DateTimeFormatter.ofPattern("M-d-yyyy"),
        DateTimeFormatter.ofPattern("MMM dd, yyyy", Locale.ENGLISH),
        DateTimeFormatter.ofPattern("dd MMM yyyy", Locale.ENGLISH),
        DateTimeFormatter.ofPattern("MMM d, yyyy", Locale.ENGLISH),
        DateTimeFormatter.ofPattern("d MMM yyyy", Locale.ENGLISH),
        DateTimeFormatter.ofPattern("MMMM dd, yyyy", Locale.ENGLISH),
        DateTimeFormatter.ofPattern("dd MMMM yyyy", Locale.ENGLISH),
    };

    /** Known header names (normalised) that help distinguish a header row from data */
    private static final List<String> KNOWN_HEADERS =
            List.of(
                    "date",
                    "amount",
                    "transactionamount",
                    "debit",
                    "credit",
                    "payee",
                    "memo",
                    "description",
                    "category",
                    "referencenumber",
                    "accountnumber",
                    "transactiondate",
                    "posteddate",
                    "dateposted",
                    "withdrawal",
                    "deposit",
                    "merchant",
                    "name",
                    "notes",
                    "type",
                    "checknumber",
                    "checknum",
                    "fitid",
                    "transactionid",
                    "currency",
                    "account",
                    "accountname",
                    "tags",
                    "tag",
                    "labels",
                    "label",
                    "out",
                    "in",
                    "paidout",
                    "paidin",
                    "title",
                    // French synonyms
                    "montant",
                    "beneficiaire",
                    "libelle",
                    "categorie",
                    "note",
                    "solde",
                    "tiers",
                    "operation",
                    "intitule",
                    "datevaleur",
                    "datecomptable",
                    "sens",
                    "devise",
                    // Skrooge CSV export columns
                    "bank",
                    "mode",
                    "comment",
                    "quantity",
                    "unit",
                    "sign",
                    "status",
                    "tracker",
                    "bookmarked",
                    "id",
                    "idtransaction",
                    "idgroup");

    /**
     * Parse CSV file and extract transactions.
     *
     * @param inputStream Input stream of CSV file content
     * @param fileName Original file name for error reporting
     * @return List of imported transactions with validation errors
     * @throws IOException if file reading fails
     */
    public List<ImportedTransaction> parseFile(InputStream inputStream, String fileName)
            throws IOException {
        log.info("Starting CSV file parsing: {}", fileName);
        List<ImportedTransaction> transactions = new ArrayList<>();

        BufferedReader bufferedReader =
                new BufferedReader(new InputStreamReader(inputStream, StandardCharsets.UTF_8));

        // Peek at the first line to detect the separator (comma vs semicolon)
        bufferedReader.mark(8192);
        String firstLine = bufferedReader.readLine();
        bufferedReader.reset();

        if (firstLine == null) {
            log.warn("CSV file is empty: {}", fileName);
            return transactions;
        }

        char separator = detectSeparator(firstLine);
        log.debug("Detected CSV separator: '{}'", separator);

        // Read ALL rows first so we can infer the date format from the full data set
        List<String[]> allRows = new ArrayList<>();
        try (CSVReader reader =
                new CSVReaderBuilder(bufferedReader)
                        .withCSVParser(new CSVParserBuilder().withSeparator(separator).build())
                        .build()) {
            String[] row;
            while ((row = reader.readNext()) != null) {
                allRows.add(row);
            }
        } catch (CsvValidationException e) {
            log.error("CSV validation error: {}", e.getMessage(), e);
            throw new IOException("Failed to parse CSV file: " + e.getMessage(), e);
        }

        if (allRows.isEmpty()) {
            log.warn("CSV file is empty: {}", fileName);
            return transactions;
        }

        // Detect whether the first row is a header or data
        String[] firstRow = allRows.get(0);
        String[] headers;
        int dataStartIndex;
        if (isHeaderRow(firstRow)) {
            headers = firstRow;
            dataStartIndex = 1;
        } else {
            log.info("No header row detected in {}; using positional column mapping", fileName);
            headers = generatePositionalHeaders(firstRow.length);
            dataStartIndex = 0;
        }

        Map<String, Integer> headerMap = mapHeaders(headers);

        // Collect all date strings to infer whether day-first or month-first
        List<String> dateStrings = new ArrayList<>();
        for (int i = dataStartIndex; i < allRows.size(); i++) {
            String[] row = allRows.get(i);
            if (row.length == 0 || (row.length == 1 && row[0].trim().isEmpty())) continue;
            String dateStr =
                    getValue(row, headerMap, "date", "transactiondate", "posteddate", "dateposted");
            if (dateStr != null) dateStrings.add(dateStr);
        }
        DateTimeFormatter[] dateFormats = inferDateFormats(dateStrings);

        // Parse all data rows
        for (int i = dataStartIndex; i < allRows.size(); i++) {
            String[] row = allRows.get(i);
            if (row.length == 0 || (row.length == 1 && row[0].trim().isEmpty())) continue;
            int lineNumber = i + 1;
            ImportedTransaction transaction =
                    parseTransaction(row, headerMap, lineNumber, fileName, dateFormats);
            if (transaction != null) {
                transactions.add(transaction);
            }
        }

        // Post-process: merge split suboperations sharing the same idtransaction
        transactions = mergeSplitTransactions(transactions);

        // Post-process: link transfer pairs sharing the same non-zero idgroup
        linkTransferTransactions(transactions);

        log.info(
                "CSV parsing complete: {} transactions parsed from {}",
                transactions.size(),
                fileName);
        return transactions;
    }

    /** Detect the field separator by counting commas vs semicolons in the first line. */
    private char detectSeparator(String line) {
        long commaCount = line.chars().filter(c -> c == ',').count();
        long semicolonCount = line.chars().filter(c -> c == ';').count();
        return semicolonCount > commaCount ? ';' : ',';
    }

    /**
     * Check whether a row looks like a header row by matching normalised cell values against known
     * header names.
     */
    private boolean isHeaderRow(String[] row) {
        int matchCount = 0;
        for (String cell : row) {
            if (cell == null) continue;
            String normalised = cell.toLowerCase().replaceAll("[^a-z0-9]", "");
            if (KNOWN_HEADERS.contains(normalised)) {
                matchCount++;
            }
        }
        // If at least 2 cells (or >40% of cells) match known headers, treat as header
        // row
        return matchCount >= 2
                || (row.length > 0 && matchCount > 0 && (double) matchCount / row.length > 0.4);
    }

    /**
     * Generate positional column headers when the file has no header row. Uses a heuristic mapping:
     * col0=date, col1=amount, col2=payee, col3=memo, col4=category.
     */
    private String[] generatePositionalHeaders(int columnCount) {
        String[] defaultNames = {"date", "amount", "payee", "memo", "category", "referencenumber"};
        String[] headers = new String[columnCount];
        for (int i = 0; i < columnCount; i++) {
            headers[i] = i < defaultNames.length ? defaultNames[i] : "col" + i;
        }
        return headers;
    }

    /** Map normalized header names to their column indices. */
    private Map<String, Integer> mapHeaders(String[] headers) {
        Map<String, Integer> map = new HashMap<>();
        for (int i = 0; i < headers.length; i++) {
            if (headers[i] != null) {
                String header = headers[i].toLowerCase().replaceAll("[^a-z0-9]", "");
                map.put(header, i);
            }
        }
        return map;
    }

    /** Parse a single row into an ImportedTransaction. */
    private ImportedTransaction parseTransaction(
            String[] row,
            Map<String, Integer> headerMap,
            int lineNumber,
            String fileName,
            DateTimeFormatter[] dateFormats) {
        ImportedTransaction.ImportedTransactionBuilder builder =
                ImportedTransaction.builder()
                        .lineNumber(lineNumber)
                        .sourceFileName(fileName)
                        .rawData(String.join(",", row));

        // Date — detect Skrooge synthetic "0000-00-00" opening-balance marker
        String dateStr =
                getValue(
                        row,
                        headerMap,
                        "date",
                        "transactiondate",
                        "posteddate",
                        "dateposted",
                        "datevaleur",
                        "datecomptable");
        boolean isOpeningBalance = "0000-00-00".equals(dateStr);
        builder.transactionDate(parseDate(dateStr, lineNumber, dateFormats));
        builder.openingBalance(isOpeningBalance);

        // Currency — check the "unit" column (Skrooge) before generic "currency" lookups
        String unit = getValue(row, headerMap, "unit");
        String mappedCurrency = mapUnitToCurrencyCode(unit);
        String currency =
                mappedCurrency != null
                        ? mappedCurrency
                        : getValue(row, headerMap, "currency", "curr", "currencycode", "iso");
        builder.currency(currency);

        // Amount — when the unit maps to a non-EUR currency, prefer the native "quantity"
        // column (Skrooge stores EUR-converted amounts in "amount" and native amounts in
        // "quantity"). For EUR or absent units, fall back to the standard "amount" column.
        BigDecimal amount = null;
        if (mappedCurrency != null && !"EUR".equals(mappedCurrency)) {
            String quantityStr = getValue(row, headerMap, "quantity");
            if (quantityStr != null) {
                amount = parseAmount(quantityStr, lineNumber);
            }
        }
        if (amount == null) {
            String amountStr = getValue(row, headerMap, "amount", "transactionamount", "montant");
            if (amountStr != null) {
                amount = parseAmount(amountStr, lineNumber);
            } else {
                // Check for debit/credit split
                String debitStr = getValue(row, headerMap, "debit", "withdrawal", "out", "paidout");
                String creditStr = getValue(row, headerMap, "credit", "deposit", "in", "paidin");
                boolean foundAmount = false;

                if (debitStr != null && !debitStr.isEmpty()) {
                    BigDecimal debitAmount = parseAmount(debitStr, lineNumber);
                    if (debitAmount != null) {
                        amount = debitAmount.abs().negate(); // Debits are negative
                        foundAmount = true;
                    }
                }

                if (!foundAmount && creditStr != null && !creditStr.isEmpty()) {
                    BigDecimal creditAmount = parseAmount(creditStr, lineNumber);
                    if (creditAmount != null) {
                        amount = creditAmount.abs(); // Credits are positive
                    }
                }
            }
        }
        builder.amount(amount);

        // Payee / Name / Description
        String payee =
                getValue(
                        row,
                        headerMap,
                        "payee",
                        "name",
                        "description",
                        "merchant",
                        "title",
                        "beneficiaire",
                        "tiers",
                        "libelle");
        builder.payee(payee);

        // Memo / Notes — "comment" is the Skrooge CSV column for detailed descriptions
        String memo =
                getValue(
                        row,
                        headerMap,
                        "memo",
                        "notes",
                        "referencememo",
                        "details",
                        "note",
                        "intitule",
                        "comment");
        // If no separate memo, but we have both Payee and Description, use Description
        // as Memo
        if (memo == null || memo.isEmpty()) {
            String desc = getValue(row, headerMap, "description", "libelle");
            if (desc != null && payee != null && !desc.equalsIgnoreCase(payee)) {
                memo = desc;
            }
        }
        builder.memo(memo);

        // Category
        String category = getValue(row, headerMap, "category", "type", "categorie");
        builder.category(category);

        // Reference Number
        String refNum =
                getValue(
                        row,
                        headerMap,
                        "referencenumber",
                        "checknumber",
                        "checknum",
                        "fitid",
                        "id",
                        "transactionid");
        builder.referenceNumber(refNum);

        // Account Name / Number
        String accountName = getValue(row, headerMap, "account", "accountname");
        String accountNumber = getValue(row, headerMap, "accountnumber", "acctid");
        if ((accountName == null || accountName.isEmpty())
                && accountNumber != null
                && !accountNumber.isEmpty()) {
            accountName = accountNumber;
        }
        builder.accountName(accountName);
        builder.accountNumber(accountNumber);

        // Payment Method (Skrooge "mode" column — e.g., "Débit", "Crédit")
        String mode = getValue(row, headerMap, "mode");
        builder.paymentMethod(mode);

        // Institution / Bank name (Skrooge "bank" column)
        String bankName = getValue(row, headerMap, "bank");
        builder.institutionName(bankName);

        // Reconciliation status (Skrooge "status" column: "Y" = reconciled)
        String status = getValue(row, headerMap, "status");
        if ("Y".equalsIgnoreCase(status)) {
            builder.clearedStatus("reconciled");
        }

        // Skrooge "idtransaction" groups suboperations (for split-merge post-processing)
        String idTransaction = getValue(row, headerMap, "idtransaction");
        builder.sourceOperationId(idTransaction);

        // Skrooge "idgroup" links transfer pairs (non-zero → transfer)
        String idGroup = getValue(row, headerMap, "idgroup");
        if (idGroup != null && !"0".equals(idGroup.trim())) {
            builder.transferGroupKey("csv:transfer:" + idGroup.trim());
        }

        // Tags (comma-separated within the cell, or from dedicated tag/label columns)
        String tagsRaw = getValue(row, headerMap, "tags", "tag", "labels", "label");
        if (tagsRaw != null && !tagsRaw.isEmpty()) {
            List<String> tagList = new ArrayList<>();
            for (String t : tagsRaw.split(",")) {
                String trimmed = t.trim();
                if (!trimmed.isEmpty()) {
                    tagList.add(trimmed);
                }
            }
            if (!tagList.isEmpty()) {
                builder.tags(tagList);
            }
        }

        ImportedTransaction transaction = builder.build();
        validateTransaction(transaction);

        return transaction;
    }

    /** Get value from row by checking multiple possible header names. */
    private String getValue(String[] row, Map<String, Integer> headerMap, String... possibleNames) {
        for (String name : possibleNames) {
            Integer index = headerMap.get(name);
            if (index != null && index < row.length) {
                String value = row[index].trim();
                return value.isEmpty() ? null : value;
            }
        }
        return null; // Not found
    }

    /** Parse date from CSV file. */
    private LocalDate parseDate(String dateStr, int lineNumber, DateTimeFormatter[] dateFormats) {
        if (dateStr == null || dateStr.isEmpty()) {
            return null;
        }
        if ("0000-00-00".equals(dateStr)) {
            return LocalDate.of(1970, 1, 1);
        }
        for (DateTimeFormatter formatter : dateFormats) {
            try {
                return LocalDate.parse(dateStr, formatter);
            } catch (DateTimeParseException e) {
                // Try next
            }
        }
        log.warn("Line {}: Unable to parse CSV date '{}'", lineNumber, dateStr);
        return null;
    }

    /**
     * Infer date format order by examining all date strings from the file. If any date has its
     * first numeric part > 12, it must be the day (dd/MM). If any date has its second numeric part
     * > 12, it must be the day (MM/dd). When ambiguous, defaults to day-first (European) since the
     * app supports French.
     */
    private DateTimeFormatter[] inferDateFormats(List<String> dateStrings) {
        boolean firstPartExceeds12 = false;
        boolean secondPartExceeds12 = false;

        for (String ds : dateStrings) {
            if (ds == null) continue;
            Matcher m = NUMERIC_DATE_PATTERN.matcher(ds.trim());
            if (m.matches()) {
                int first = Integer.parseInt(m.group(1));
                int second = Integer.parseInt(m.group(2));
                if (first > 12) firstPartExceeds12 = true;
                if (second > 12) secondPartExceeds12 = true;
            }
        }

        if (secondPartExceeds12 && !firstPartExceeds12) {
            log.debug(
                    "Inferred date format: month-first (MM/dd) — values > 12 found in second position");
            return DATE_FORMATS;
        }
        // day-first when first > 12, or when fully ambiguous (European default)
        log.debug("Inferred date format: day-first (dd/MM)");
        return DATE_FORMATS_DAY_FIRST;
    }

    /** Parse amount from CSV file. */
    private BigDecimal parseAmount(String amountStr, int lineNumber) {
        if (amountStr == null || amountStr.isEmpty()) {
            return null;
        }
        try {
            // Remove currency symbols and whitespace
            String cleaned =
                    amountStr.replace("$", "").replace("€", "").replace("£", "").replace(" ", "");

            // Detect European format: dot as thousands separator, comma as decimal
            // e.g., "1.200,50" → "1200.50"
            if (cleaned.contains(",") && cleaned.contains(".")) {
                int lastComma = cleaned.lastIndexOf(',');
                int lastDot = cleaned.lastIndexOf('.');
                if (lastComma > lastDot) {
                    // European format: 1.200,50
                    cleaned = cleaned.replace(".", "").replace(",", ".");
                } else {
                    // US format: 1,200.50
                    cleaned = cleaned.replace(",", "");
                }
            } else if (cleaned.contains(",") && !cleaned.contains(".")) {
                // Could be European decimal (e.g., "200,50") or US thousands (e.g., "1,200")
                // If digits after comma are exactly 1 or 2, treat comma as decimal separator
                int commaPos = cleaned.lastIndexOf(',');
                String afterComma = cleaned.substring(commaPos + 1);
                if (afterComma.length() <= 2) {
                    cleaned = cleaned.replace(",", ".");
                } else {
                    cleaned = cleaned.replace(",", "");
                }
            } else {
                // No comma — just remove any grouping issues
                cleaned = cleaned.replace(",", "");
            }

            return new BigDecimal(cleaned);
        } catch (NumberFormatException e) {
            log.warn("Line {}: Unable to parse amount '{}'", lineNumber, amountStr);
            return null;
        }
    }

    /** Validate imported transaction. */
    private void validateTransaction(ImportedTransaction transaction) {
        if (transaction.getTransactionDate() == null) {
            transaction.addValidationError("Transaction date is required");
        }
        if (transaction.getAmount() == null) {
            transaction.addValidationError("Transaction amount is required");
        } else if (transaction.getAmount().compareTo(BigDecimal.ZERO) == 0) {
            // Only flag zero amount if strictly required to be non-zero.
            // Often dummy transactions or verifications are $0.
            transaction.addValidationError("Transaction amount cannot be zero");
        }
        if (transaction.getTransactionDate() != null
                && transaction.getTransactionDate().isAfter(LocalDate.now())) {
            transaction.addValidationError("Transaction date cannot be in the future");
        }
    }

    /**
     * Map Skrooge unit symbols/names to ISO 4217 currency codes.
     *
     * <p>Returns {@code null} for units that cannot be mapped to a 3-character code (e.g., "DOGE",
     * "USDT", "B504", "TCALAVI"), causing the caller to fall back to the standard amount column
     * (EUR-converted) and the account's default currency.
     *
     * @param unit raw unit string from the CSV (e.g., "€", "$", "CFA", "BTC")
     * @return ISO 4217 code, or null when the unit is unrecognised or too long
     */
    private String mapUnitToCurrencyCode(String unit) {
        if (unit == null || unit.isBlank()) {
            return null;
        }
        String trimmed = unit.trim();
        switch (trimmed) {
            case "€":
                return "EUR";
            case "$":
                return "USD";
            case "CFA":
                return "XOF";
            default:
                // Only accept 3-character uppercase codes that fit the DB column (length = 3)
                String upper = trimmed.toUpperCase(Locale.ROOT);
                if (upper.length() == 3 && upper.matches("[A-Z]{3}")) {
                    return upper;
                }
                return null;
        }
    }

    /**
     * Merge rows sharing the same {@code sourceOperationId} (Skrooge "idtransaction") into a single
     * ImportedTransaction with splits.
     *
     * <p>When multiple CSV rows share the same idtransaction, they represent suboperations of a
     * single logical operation. The first row becomes the parent transaction; subsequent rows are
     * converted to {@link ImportedTransaction.SplitEntry} objects. The parent amount is the sum of
     * all suboperation amounts.
     *
     * <p>Rows without an idtransaction (or with a unique one) are passed through unchanged.
     *
     * @param transactions parsed transactions, potentially with duplicate sourceOperationIds
     * @return merged list with split transactions collapsed into single entries
     */
    private List<ImportedTransaction> mergeSplitTransactions(
            List<ImportedTransaction> transactions) {
        Map<String, List<ImportedTransaction>> grouped = new LinkedHashMap<>();
        List<ImportedTransaction> ungrouped = new ArrayList<>();

        for (ImportedTransaction tx : transactions) {
            String key = tx.getSourceOperationId();
            if (key == null || key.isBlank() || "0".equals(key.trim())) {
                ungrouped.add(tx);
                continue;
            }
            grouped.computeIfAbsent(key, k -> new ArrayList<>()).add(tx);
        }

        List<ImportedTransaction> result = new ArrayList<>(ungrouped);
        for (List<ImportedTransaction> group : grouped.values()) {
            if (group.size() == 1) {
                result.add(group.get(0));
                continue;
            }

            ImportedTransaction parent = group.get(0);
            BigDecimal totalAmount =
                    group.stream()
                            .map(ImportedTransaction::getAmount)
                            .filter(java.util.Objects::nonNull)
                            .reduce(BigDecimal.ZERO, BigDecimal::add);

            List<ImportedTransaction.SplitEntry> splits = new ArrayList<>();
            for (ImportedTransaction sub : group) {
                BigDecimal splitAmount =
                        sub.getAmount() != null ? sub.getAmount().abs() : BigDecimal.ZERO;
                splits.add(
                        ImportedTransaction.SplitEntry.builder()
                                .category(sub.getCategory())
                                .memo(sub.getMemo())
                                .amount(splitAmount)
                                .build());
            }

            ImportedTransaction merged =
                    ImportedTransaction.builder()
                            .transactionDate(parent.getTransactionDate())
                            .payee(parent.getPayee())
                            .originalPayee(parent.getOriginalPayee())
                            .amount(totalAmount)
                            .memo(parent.getMemo())
                            .category(parent.getCategory())
                            .clearedStatus(parent.getClearedStatus())
                            .referenceNumber(parent.getReferenceNumber())
                            .accountName(parent.getAccountName())
                            .accountNumber(parent.getAccountNumber())
                            .currency(parent.getCurrency())
                            .lineNumber(parent.getLineNumber())
                            .sourceFileName(parent.getSourceFileName())
                            .rawData(parent.getRawData())
                            .paymentMethod(parent.getPaymentMethod())
                            .institutionName(parent.getInstitutionName())
                            .openingBalance(parent.isOpeningBalance())
                            .sourceOperationId(parent.getSourceOperationId())
                            .transferGroupKey(parent.getTransferGroupKey())
                            .splits(splits)
                            .build();
            result.add(merged);
        }

        return result;
    }

    /**
     * Link transfer pairs by their {@code transferGroupKey} (derived from Skrooge "idgroup").
     *
     * <p>For each non-null transferGroupKey, exactly two transactions form a transfer pair: one
     * with a negative amount (source) and one with a positive amount (destination). This method
     * sets {@code transfer = true} and {@code toAccountName} on both sides so that {@code
     * ImportService} can create a proper TRANSFER transaction.
     *
     * @param transactions parsed (and split-merged) transactions
     */
    private void linkTransferTransactions(List<ImportedTransaction> transactions) {
        Map<String, List<ImportedTransaction>> grouped = new LinkedHashMap<>();
        for (ImportedTransaction tx : transactions) {
            String key = tx.getTransferGroupKey();
            if (key == null || key.isBlank()) {
                continue;
            }
            grouped.computeIfAbsent(key, k -> new ArrayList<>()).add(tx);
        }

        for (List<ImportedTransaction> group : grouped.values()) {
            if (group.size() != 2) {
                continue;
            }
            ImportedTransaction first = group.get(0);
            ImportedTransaction second = group.get(1);

            BigDecimal firstAmount =
                    first.getAmount() != null ? first.getAmount() : BigDecimal.ZERO;
            BigDecimal secondAmount =
                    second.getAmount() != null ? second.getAmount() : BigDecimal.ZERO;

            // Determine source (negative) and destination (positive) by amount sign
            ImportedTransaction source;
            ImportedTransaction destination;
            if (firstAmount.compareTo(BigDecimal.ZERO) < 0
                    && secondAmount.compareTo(BigDecimal.ZERO) >= 0) {
                source = first;
                destination = second;
            } else if (secondAmount.compareTo(BigDecimal.ZERO) < 0
                    && firstAmount.compareTo(BigDecimal.ZERO) >= 0) {
                source = second;
                destination = first;
            } else {
                // Both same sign — cannot determine direction, skip
                continue;
            }

            source.setTransfer(true);
            source.setToAccountName(destination.getAccountName());

            destination.setTransfer(true);
            destination.setToAccountName(source.getAccountName());
        }
    }
}
