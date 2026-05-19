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
                    "devise");

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

        // Date
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
        builder.transactionDate(parseDate(dateStr, lineNumber, dateFormats));

        // Amount
        String amountStr = getValue(row, headerMap, "amount", "transactionamount", "montant");
        if (amountStr != null) {
            builder.amount(parseAmount(amountStr, lineNumber));
        } else {
            // Check for debit/credit split
            String debitStr = getValue(row, headerMap, "debit", "withdrawal", "out", "paidout");
            String creditStr = getValue(row, headerMap, "credit", "deposit", "in", "paidin");
            boolean foundAmount = false;

            if (debitStr != null && !debitStr.isEmpty()) {
                BigDecimal amount = parseAmount(debitStr, lineNumber);
                if (amount != null) {
                    builder.amount(amount.abs().negate()); // Debits are negative
                    foundAmount = true;
                }
            }

            if (!foundAmount && creditStr != null && !creditStr.isEmpty()) {
                BigDecimal amount = parseAmount(creditStr, lineNumber);
                if (amount != null) {
                    builder.amount(amount.abs()); // Credits are positive
                }
            }
        }

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

        // Memo / Notes
        String memo =
                getValue(
                        row,
                        headerMap,
                        "memo",
                        "notes",
                        "referencememo",
                        "details",
                        "note",
                        "intitule");
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

        // Currency (ISO code, e.g., USD, EUR, GBP)
        String currency = getValue(row, headerMap, "currency", "curr", "currencycode", "iso");
        builder.currency(currency);

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
}
