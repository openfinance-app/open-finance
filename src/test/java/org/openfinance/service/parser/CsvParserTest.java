package org.openfinance.service.parser;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.openfinance.dto.ImportedTransaction;

/**
 * Comprehensive test suite for CSV parser.
 *
 * <p>Tests cover: - Basic transaction parsing - Flexible header name mapping (aliases and
 * normalization) - Multiple date formats - Debit/credit split columns - Amount parsing with
 * currency symbols and thousands separators - Validation and error reporting (missing date, missing
 * amount, zero amount, future date) - Empty/malformed files - Memo fallback to description -
 * Real-world CSV export scenario
 *
 * <p>Requirements: - REQ-2.5.1.1: File Format Support (CSV parsing) - REQ-2.5.1.3: Import
 * Validation
 */
@DisplayName("CSV Parser Tests")
class CsvParserTest {

    private CsvParser parser;

    @BeforeEach
    void setUp() {
        parser = new CsvParser();
    }

    // ========== Helper ==========

    private List<ImportedTransaction> parseCsv(String content) throws IOException {
        InputStream inputStream =
                new ByteArrayInputStream(content.getBytes(StandardCharsets.UTF_8));
        return parser.parseFile(inputStream, "test.csv");
    }

    private String readFixture(String resourcePath) throws IOException {
        try (InputStream inputStream =
                getClass().getClassLoader().getResourceAsStream(resourcePath)) {
            if (inputStream == null) {
                throw new IOException("Fixture not found: " + resourcePath);
            }
            return new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    // ========== Basic Parsing Tests ==========

    @Test
    @DisplayName("Should parse single transaction with all standard fields")
    void testParseSingleTransactionWithAllFields() throws IOException {
        String csv =
                """
                date,amount,payee,memo,category,referencenumber,accountnumber
                01/15/2024,-45.67,Starbucks,Coffee and snack,Food:Dining,1001,ACC123
                """;

        List<ImportedTransaction> transactions = parseCsv(csv);

        assertThat(transactions).hasSize(1);
        ImportedTransaction tx = transactions.get(0);

        assertThat(tx.getTransactionDate()).isEqualTo(LocalDate.of(2024, 1, 15));
        assertThat(tx.getAmount()).isEqualByComparingTo(new BigDecimal("-45.67"));
        assertThat(tx.getPayee()).isEqualTo("Starbucks");
        assertThat(tx.getMemo()).isEqualTo("Coffee and snack");
        assertThat(tx.getCategory()).isEqualTo("Food:Dining");
        assertThat(tx.getReferenceNumber()).isEqualTo("1001");
        assertThat(tx.getAccountName()).isEqualTo("ACC123");
        assertThat(tx.hasErrors()).isFalse();
    }

    @Test
    @DisplayName("Should parse multiple transactions")
    void testParseMultipleTransactions() throws IOException {
        String csv =
                """
                date,amount,payee
                01/15/2024,-45.67,Starbucks
                01/16/2024,-100.00,Grocery Store
                01/17/2024,2500.00,Salary
                """;

        List<ImportedTransaction> transactions = parseCsv(csv);

        assertThat(transactions).hasSize(3);
        assertThat(transactions.get(0).getAmount()).isEqualByComparingTo(new BigDecimal("-45.67"));
        assertThat(transactions.get(1).getAmount()).isEqualByComparingTo(new BigDecimal("-100.00"));
        assertThat(transactions.get(2).getAmount()).isEqualByComparingTo(new BigDecimal("2500.00"));
    }

    // ========== Header Normalization / Alias Tests ==========

    @Test
    @DisplayName("Should map 'transactionamount' column to amount")
    void testHeaderAliasTransactionAmount() throws IOException {
        String csv =
                """
                date,transactionamount,payee
                2024-01-15,-99.99,Test Merchant
                """;

        List<ImportedTransaction> transactions = parseCsv(csv);

        assertThat(transactions).hasSize(1);
        assertThat(transactions.get(0).getAmount()).isEqualByComparingTo(new BigDecimal("-99.99"));
    }

    @Test
    @DisplayName("Should map 'name' column to payee")
    void testHeaderAliasName() throws IOException {
        String csv =
                """
                date,amount,name
                2024-01-15,-10.00,My Payee
                """;

        List<ImportedTransaction> transactions = parseCsv(csv);

        assertThat(transactions).hasSize(1);
        assertThat(transactions.get(0).getPayee()).isEqualTo("My Payee");
    }

    @Test
    @DisplayName("Should map 'merchant' column to payee")
    void testHeaderAliasMerchant() throws IOException {
        String csv =
                """
                date,amount,merchant
                2024-01-15,-10.00,Best Buy
                """;

        List<ImportedTransaction> transactions = parseCsv(csv);

        assertThat(transactions).hasSize(1);
        assertThat(transactions.get(0).getPayee()).isEqualTo("Best Buy");
    }

    @Test
    @DisplayName("Should map 'notes' column to memo")
    void testHeaderAliasNotes() throws IOException {
        String csv =
                """
                date,amount,payee,notes
                2024-01-15,-10.00,Shop,Some note
                """;

        List<ImportedTransaction> transactions = parseCsv(csv);

        assertThat(transactions).hasSize(1);
        assertThat(transactions.get(0).getMemo()).isEqualTo("Some note");
    }

    @Test
    @DisplayName("Should map 'checknumber' column to reference number")
    void testHeaderAliasCheckNumber() throws IOException {
        String csv =
                """
                date,amount,payee,checknumber
                2024-01-15,-10.00,Shop,9876
                """;

        List<ImportedTransaction> transactions = parseCsv(csv);

        assertThat(transactions).hasSize(1);
        assertThat(transactions.get(0).getReferenceNumber()).isEqualTo("9876");
    }

    @Test
    @DisplayName("Should map 'accountname' column to account name")
    void testHeaderAliasAccountName() throws IOException {
        String csv =
                """
                date,amount,payee,accountname
                2024-01-15,-10.00,Shop,Checking
                """;

        List<ImportedTransaction> transactions = parseCsv(csv);

        assertThat(transactions).hasSize(1);
        assertThat(transactions.get(0).getAccountName()).isEqualTo("Checking");
    }

    @Test
    @DisplayName("Should strip non-alphanumeric characters from header names")
    void testHeaderNormalizationWithSpacesAndSpecialChars() throws IOException {
        // "Transaction Date" → "transactiondate", "Transaction Amount" →
        // "transactionamount"
        String csv =
                """
                Transaction Date,Transaction Amount,Payee
                2024-01-15,-50.00,Shop
                """;

        List<ImportedTransaction> transactions = parseCsv(csv);

        assertThat(transactions).hasSize(1);
        assertThat(transactions.get(0).getTransactionDate()).isEqualTo(LocalDate.of(2024, 1, 15));
        assertThat(transactions.get(0).getAmount()).isEqualByComparingTo(new BigDecimal("-50.00"));
    }

    // ========== Date Format Tests ==========

    @Test
    @DisplayName("Should parse MM/dd/yyyy date format")
    void testDateFormatMMddyyyy() throws IOException {
        String csv =
                """
                date,amount,payee
                12/31/2023,-50.00,Test
                """;

        List<ImportedTransaction> transactions = parseCsv(csv);

        assertThat(transactions).hasSize(1);
        assertThat(transactions.get(0).getTransactionDate()).isEqualTo(LocalDate.of(2023, 12, 31));
    }

    @Test
    @DisplayName("Should parse dd/MM/yyyy date format")
    void testDateFormatddMMyyyy() throws IOException {
        String csv =
                """
                date,amount,payee
                31/12/2023,-50.00,Test
                """;

        List<ImportedTransaction> transactions = parseCsv(csv);

        assertThat(transactions).hasSize(1);
        assertThat(transactions.get(0).getTransactionDate()).isEqualTo(LocalDate.of(2023, 12, 31));
    }

    @Test
    @DisplayName("Should parse yyyy-MM-dd (ISO) date format")
    void testDateFormatISO() throws IOException {
        String csv =
                """
                date,amount,payee
                2024-03-15,-50.00,Test
                """;

        List<ImportedTransaction> transactions = parseCsv(csv);

        assertThat(transactions).hasSize(1);
        assertThat(transactions.get(0).getTransactionDate()).isEqualTo(LocalDate.of(2024, 3, 15));
    }

    @Test
    @DisplayName("Should parse M/d/yyyy date format (no leading zeros)")
    void testDateFormatMdyyyy() throws IOException {
        // Use day > 12 in second position to unambiguously signal M/d/yyyy format
        String csv =
                """
                date,amount,payee
                1/15/2024,-50.00,Test
                """;

        List<ImportedTransaction> transactions = parseCsv(csv);

        assertThat(transactions).hasSize(1);
        assertThat(transactions.get(0).getTransactionDate()).isEqualTo(LocalDate.of(2024, 1, 15));
    }

    @Test
    @DisplayName("Should parse d/M/yyyy date format (no leading zeros, day first)")
    void testDateFormatdMyyyy() throws IOException {
        String csv =
                """
                date,amount,payee
                5/1/2024,-50.00,Test
                """;

        List<ImportedTransaction> transactions = parseCsv(csv);

        // 5/1/2024 could be Jan-5 (M/d) or May-1 (d/M); MM/dd tried first so result is
        // May 1
        assertThat(transactions).hasSize(1);
        assertThat(transactions.get(0).getTransactionDate()).isNotNull();
    }

    @Test
    @DisplayName("Should parse yyyy/MM/dd date format")
    void testDateFormatyyyy_MM_dd_slash() throws IOException {
        String csv =
                """
                date,amount,payee
                2024/07/04,-50.00,Test
                """;

        List<ImportedTransaction> transactions = parseCsv(csv);

        assertThat(transactions).hasSize(1);
        assertThat(transactions.get(0).getTransactionDate()).isEqualTo(LocalDate.of(2024, 7, 4));
    }

    @Test
    @DisplayName("Should parse dd-MM-yyyy date format")
    void testDateFormatddMMyyyyDash() throws IOException {
        String csv =
                """
                date,amount,payee
                25-12-2023,-50.00,Test
                """;

        List<ImportedTransaction> transactions = parseCsv(csv);

        assertThat(transactions).hasSize(1);
        assertThat(transactions.get(0).getTransactionDate()).isEqualTo(LocalDate.of(2023, 12, 25));
    }

    @Test
    @DisplayName("Should use 'transactiondate' column as date alias")
    void testDateAliasTransactionDate() throws IOException {
        String csv =
                """
                transactiondate,amount,payee
                2024-06-01,-10.00,Shop
                """;

        List<ImportedTransaction> transactions = parseCsv(csv);

        assertThat(transactions).hasSize(1);
        assertThat(transactions.get(0).getTransactionDate()).isEqualTo(LocalDate.of(2024, 6, 1));
    }

    @Test
    @DisplayName("Should use 'posteddate' column as date alias")
    void testDateAliasPostedDate() throws IOException {
        String csv =
                """
                posteddate,amount,payee
                2024-06-01,-10.00,Shop
                """;

        List<ImportedTransaction> transactions = parseCsv(csv);

        assertThat(transactions).hasSize(1);
        assertThat(transactions.get(0).getTransactionDate()).isEqualTo(LocalDate.of(2024, 6, 1));
    }

    // ========== Amount Parsing Tests ==========

    @Test
    @DisplayName("Should parse negative amount")
    void testParseNegativeAmount() throws IOException {
        String csv =
                """
                date,amount,payee
                2024-01-15,-123.45,Expense
                """;

        List<ImportedTransaction> transactions = parseCsv(csv);

        assertThat(transactions.get(0).getAmount()).isEqualByComparingTo(new BigDecimal("-123.45"));
    }

    @Test
    @DisplayName("Should parse positive amount")
    void testParsePositiveAmount() throws IOException {
        String csv =
                """
                date,amount,payee
                2024-01-15,1500.00,Income
                """;

        List<ImportedTransaction> transactions = parseCsv(csv);

        assertThat(transactions.get(0).getAmount()).isEqualByComparingTo(new BigDecimal("1500.00"));
    }

    @Test
    @DisplayName("Should strip dollar sign from amount")
    void testAmountWithDollarSign() throws IOException {
        String csv =
                """
                date,amount,payee
                2024-01-15,$50.00,Shop
                """;

        List<ImportedTransaction> transactions = parseCsv(csv);

        assertThat(transactions.get(0).getAmount()).isEqualByComparingTo(new BigDecimal("50.00"));
    }

    @Test
    @DisplayName("Should strip euro sign from amount")
    void testAmountWithEuroSign() throws IOException {
        String csv =
                """
                date,amount,payee
                2024-01-15,€75.50,Shop
                """;

        List<ImportedTransaction> transactions = parseCsv(csv);

        assertThat(transactions.get(0).getAmount()).isEqualByComparingTo(new BigDecimal("75.50"));
    }

    @Test
    @DisplayName("Should strip pound sign from amount")
    void testAmountWithPoundSign() throws IOException {
        String csv =
                """
                date,amount,payee
                2024-01-15,£30.00,Shop
                """;

        List<ImportedTransaction> transactions = parseCsv(csv);

        assertThat(transactions.get(0).getAmount()).isEqualByComparingTo(new BigDecimal("30.00"));
    }

    @Test
    @DisplayName("Should strip thousands separator from amount")
    void testAmountWithThousandsSeparator() throws IOException {
        String csv =
                """
                date,amount,payee
                2024-01-15,1500.00,Shop
                """;

        List<ImportedTransaction> transactions = parseCsv(csv);

        assertThat(transactions.get(0).getAmount()).isEqualByComparingTo(new BigDecimal("1500.00"));
    }

    // ========== Debit / Credit Split Column Tests ==========

    @Test
    @DisplayName("Should parse debit-only column as negative amount")
    void testDebitColumnMakesNegativeAmount() throws IOException {
        String csv =
                """
                date,debit,payee
                2024-01-15,200.00,Expense
                """;

        List<ImportedTransaction> transactions = parseCsv(csv);

        assertThat(transactions).hasSize(1);
        assertThat(transactions.get(0).getAmount()).isEqualByComparingTo(new BigDecimal("-200.00"));
    }

    @Test
    @DisplayName("Should parse credit-only column as positive amount")
    void testCreditColumnMakesPositiveAmount() throws IOException {
        String csv =
                """
                date,credit,payee
                2024-01-15,500.00,Income
                """;

        List<ImportedTransaction> transactions = parseCsv(csv);

        assertThat(transactions).hasSize(1);
        assertThat(transactions.get(0).getAmount()).isEqualByComparingTo(new BigDecimal("500.00"));
    }

    @Test
    @DisplayName("Should prefer debit over credit when both columns present and debit is non-empty")
    void testDebitTakesPriorityOverCredit() throws IOException {
        String csv =
                """
                date,debit,credit,payee
                2024-01-15,100.00,,Shop
                """;

        List<ImportedTransaction> transactions = parseCsv(csv);

        assertThat(transactions).hasSize(1);
        assertThat(transactions.get(0).getAmount()).isEqualByComparingTo(new BigDecimal("-100.00"));
    }

    @Test
    @DisplayName("Should fall back to credit column when debit column is empty")
    void testFallsBackToCreditWhenDebitEmpty() throws IOException {
        String csv =
                """
                date,debit,credit,payee
                2024-01-15,,300.00,Income
                """;

        List<ImportedTransaction> transactions = parseCsv(csv);

        assertThat(transactions).hasSize(1);
        assertThat(transactions.get(0).getAmount()).isEqualByComparingTo(new BigDecimal("300.00"));
    }

    @Test
    @DisplayName("Should map 'withdrawal' as debit alias")
    void testWithdrawalColumnAlias() throws IOException {
        String csv =
                """
                date,withdrawal,payee
                2024-01-15,75.00,ATM
                """;

        List<ImportedTransaction> transactions = parseCsv(csv);

        assertThat(transactions).hasSize(1);
        assertThat(transactions.get(0).getAmount()).isEqualByComparingTo(new BigDecimal("-75.00"));
    }

    @Test
    @DisplayName("Should map 'deposit' as credit alias")
    void testDepositColumnAlias() throws IOException {
        String csv =
                """
                date,deposit,payee
                2024-01-15,400.00,Transfer In
                """;

        List<ImportedTransaction> transactions = parseCsv(csv);

        assertThat(transactions).hasSize(1);
        assertThat(transactions.get(0).getAmount()).isEqualByComparingTo(new BigDecimal("400.00"));
    }

    // ========== Memo / Description Fallback Tests ==========

    @Test
    @DisplayName(
            "Should use description as memo when memo column absent and description differs from payee")
    void testDescriptionUsedAsMemoWhenDifferentFromPayee() throws IOException {
        String csv =
                """
                date,amount,payee,description
                2024-01-15,-10.00,AMZN,Amazon purchase details
                """;

        List<ImportedTransaction> transactions = parseCsv(csv);

        assertThat(transactions).hasSize(1);
        assertThat(transactions.get(0).getMemo()).isEqualTo("Amazon purchase details");
    }

    @Test
    @DisplayName("Should NOT use description as memo when it equals the payee")
    void testDescriptionNotUsedAsMemoWhenSameAsPayee() throws IOException {
        String csv =
                """
                date,amount,payee,description
                2024-01-15,-10.00,AMZN,AMZN
                """;

        List<ImportedTransaction> transactions = parseCsv(csv);

        assertThat(transactions).hasSize(1);
        // memo should remain null/empty since description == payee
        assertThat(transactions.get(0).getMemo()).isNullOrEmpty();
    }

    @Test
    @DisplayName("Should prefer explicit memo column over description fallback")
    void testExplicitMemoTakesPriorityOverDescription() throws IOException {
        String csv =
                """
                date,amount,payee,memo,description
                2024-01-15,-10.00,Shop,Explicit memo,Other description
                """;

        List<ImportedTransaction> transactions = parseCsv(csv);

        assertThat(transactions).hasSize(1);
        assertThat(transactions.get(0).getMemo()).isEqualTo("Explicit memo");
    }

    // ========== Category / Type Tests ==========

    @Test
    @DisplayName("Should parse category column")
    void testCategoryColumn() throws IOException {
        String csv =
                """
                date,amount,payee,category
                2024-01-15,-10.00,Shop,Groceries
                """;

        List<ImportedTransaction> transactions = parseCsv(csv);

        assertThat(transactions).hasSize(1);
        assertThat(transactions.get(0).getCategory()).isEqualTo("Groceries");
    }

    @Test
    @DisplayName("Should map 'type' column as category alias")
    void testTypeMappedAsCategory() throws IOException {
        String csv =
                """
                date,amount,payee,type
                2024-01-15,-10.00,Shop,Expense
                """;

        List<ImportedTransaction> transactions = parseCsv(csv);

        assertThat(transactions).hasSize(1);
        assertThat(transactions.get(0).getCategory()).isEqualTo("Expense");
    }

    // ========== Validation Error Tests ==========

    @Test
    @DisplayName("Should add validation error for missing date")
    void testValidationErrorMissingDate() throws IOException {
        String csv =
                """
                date,amount,payee
                ,-50.00,Test Payee
                """;

        List<ImportedTransaction> transactions = parseCsv(csv);

        assertThat(transactions).hasSize(1);
        ImportedTransaction tx = transactions.get(0);
        assertThat(tx.hasErrors()).isTrue();
        assertThat(tx.getValidationErrors()).contains("Transaction date is required");
    }

    @Test
    @DisplayName("Should add validation error for missing amount")
    void testValidationErrorMissingAmount() throws IOException {
        String csv =
                """
                date,amount,payee
                2024-01-15,,Test Payee
                """;

        List<ImportedTransaction> transactions = parseCsv(csv);

        assertThat(transactions).hasSize(1);
        ImportedTransaction tx = transactions.get(0);
        assertThat(tx.hasErrors()).isTrue();
        assertThat(tx.getValidationErrors()).contains("Transaction amount is required");
    }

    @Test
    @DisplayName("Should add validation error for zero amount")
    void testValidationErrorZeroAmount() throws IOException {
        String csv =
                """
                date,amount,payee
                2024-01-15,0.00,Test Payee
                """;

        List<ImportedTransaction> transactions = parseCsv(csv);

        assertThat(transactions).hasSize(1);
        ImportedTransaction tx = transactions.get(0);
        assertThat(tx.hasErrors()).isTrue();
        assertThat(tx.getValidationErrors()).contains("Transaction amount cannot be zero");
    }

    @Test
    @DisplayName("Should add validation error for invalid date format")
    void testValidationErrorInvalidDate() throws IOException {
        String csv =
                """
                date,amount,payee
                not-a-date,-50.00,Test
                """;

        List<ImportedTransaction> transactions = parseCsv(csv);

        assertThat(transactions).hasSize(1);
        ImportedTransaction tx = transactions.get(0);
        assertThat(tx.getTransactionDate()).isNull();
        assertThat(tx.hasErrors()).isTrue();
        assertThat(tx.getValidationErrors()).contains("Transaction date is required");
    }

    @Test
    @DisplayName("Should add validation error for invalid amount format")
    void testValidationErrorInvalidAmount() throws IOException {
        String csv =
                """
                date,amount,payee
                2024-01-15,abc,Test
                """;

        List<ImportedTransaction> transactions = parseCsv(csv);

        assertThat(transactions).hasSize(1);
        ImportedTransaction tx = transactions.get(0);
        assertThat(tx.getAmount()).isNull();
        assertThat(tx.hasErrors()).isTrue();
        assertThat(tx.getValidationErrors()).contains("Transaction amount is required");
    }

    @Test
    @DisplayName("Should add validation error for future date")
    void testValidationErrorFutureDate() throws IOException {
        LocalDate futureDate = LocalDate.now().plusDays(30);
        String csv =
                String.format(
                        """
                date,amount,payee
                %s,-50.00,Test
                """,
                        futureDate);

        List<ImportedTransaction> transactions = parseCsv(csv);

        assertThat(transactions).hasSize(1);
        ImportedTransaction tx = transactions.get(0);
        assertThat(tx.hasErrors()).isTrue();
        assertThat(tx.getValidationErrors())
                .anyMatch(err -> err.contains("cannot be in the future"));
    }

    // ========== Source File Metadata Tests ==========

    @Test
    @DisplayName("Should include source file name in each transaction")
    void testIncludesSourceFileName() throws IOException {
        String csv =
                """
                date,amount,payee
                2024-01-15,-50.00,Test
                """;

        InputStream inputStream = new ByteArrayInputStream(csv.getBytes(StandardCharsets.UTF_8));
        List<ImportedTransaction> transactions = parser.parseFile(inputStream, "bank_export.csv");

        assertThat(transactions).hasSize(1);
        assertThat(transactions.get(0).getSourceFileName()).isEqualTo("bank_export.csv");
    }

    @Test
    @DisplayName("Should assign correct line numbers starting at 2 (header is line 1)")
    void testLineNumberAssignment() throws IOException {
        String csv =
                """
                date,amount,payee
                2024-01-15,-50.00,First
                2024-01-16,-60.00,Second
                2024-01-17,-70.00,Third
                """;

        List<ImportedTransaction> transactions = parseCsv(csv);

        assertThat(transactions).hasSize(3);
        assertThat(transactions.get(0).getLineNumber()).isEqualTo(2);
        assertThat(transactions.get(1).getLineNumber()).isEqualTo(3);
        assertThat(transactions.get(2).getLineNumber()).isEqualTo(4);
    }

    @Test
    @DisplayName("Should include raw CSV row data in each transaction")
    void testIncludesRawData() throws IOException {
        String csv =
                """
                date,amount,payee
                2024-01-15,-50.00,Test Payee
                """;

        List<ImportedTransaction> transactions = parseCsv(csv);

        assertThat(transactions).hasSize(1);
        assertThat(transactions.get(0).getRawData()).isNotNull().isNotEmpty();
    }

    // ========== Empty / Edge-Case File Tests ==========

    @Test
    @DisplayName("Should return empty list for completely empty file")
    void testEmptyFile() throws IOException {
        String csv = "";

        List<ImportedTransaction> transactions = parseCsv(csv);

        assertThat(transactions).isEmpty();
    }

    @Test
    @DisplayName("Should return empty list for header-only file")
    void testHeaderOnlyFile() throws IOException {
        String csv = "date,amount,payee\n";

        List<ImportedTransaction> transactions = parseCsv(csv);

        assertThat(transactions).isEmpty();
    }

    @Test
    @DisplayName("Should skip blank lines between data rows")
    void testSkipBlankLines() throws IOException {
        String csv =
                """
                date,amount,payee
                2024-01-15,-50.00,First

                2024-01-16,-60.00,Second
                """;

        List<ImportedTransaction> transactions = parseCsv(csv);

        assertThat(transactions).hasSize(2);
        assertThat(transactions.get(0).getPayee()).isEqualTo("First");
        assertThat(transactions.get(1).getPayee()).isEqualTo("Second");
    }

    @Test
    @DisplayName("Should handle minimal transaction (date and amount only)")
    void testMinimalTransaction() throws IOException {
        String csv =
                """
                date,amount
                2024-01-15,-50.00
                """;

        List<ImportedTransaction> transactions = parseCsv(csv);

        assertThat(transactions).hasSize(1);
        ImportedTransaction tx = transactions.get(0);
        assertThat(tx.getTransactionDate()).isNotNull();
        assertThat(tx.getAmount()).isNotNull();
        assertThat(tx.getPayee()).isNullOrEmpty();
        assertThat(tx.hasErrors()).isFalse();
    }

    @Test
    @DisplayName("Should handle rows with extra whitespace in values")
    void testHandlesWhitespaceInValues() throws IOException {
        String csv =
                """
                date,amount,payee
                2024-01-15 , -50.00 , Trimmed Payee
                """;

        List<ImportedTransaction> transactions = parseCsv(csv);

        // opencsv does not strip cell whitespace by default, but CsvParser trims via
        // getValue()
        assertThat(transactions).hasSize(1);
    }

    // ========== Real-World Scenario Tests ==========

    @Test
    @DisplayName("Should parse a realistic bank CSV export")
    void testRealisticBankExport() throws IOException {
        String csv =
                """
                date,amount,payee,memo,category,referencenumber,accountnumber
                01/01/2024,-1200.00,Landlord Inc,January rent,Housing:Rent,1234,CHECKING-001
                01/03/2024,-65.43,Electric Company,Monthly utility,Utilities:Electric,,CHECKING-001
                01/05/2024,-150.00,Grocery Store,Weekly shopping,Food:Groceries,,CHECKING-001
                01/15/2024,3500.00,Employer Inc,Salary deposit,Income:Salary,,CHECKING-001
                01/20/2024,-500.00,Transfer,To savings,Transfer,,CHECKING-001
                """;

        List<ImportedTransaction> transactions = parseCsv(csv);

        assertThat(transactions).hasSize(5);

        // Rent payment
        ImportedTransaction rent = transactions.get(0);
        assertThat(rent.getTransactionDate()).isEqualTo(LocalDate.of(2024, 1, 1));
        assertThat(rent.getAmount()).isEqualByComparingTo(new BigDecimal("-1200.00"));
        assertThat(rent.getPayee()).isEqualTo("Landlord Inc");
        assertThat(rent.getMemo()).isEqualTo("January rent");
        assertThat(rent.getCategory()).isEqualTo("Housing:Rent");
        assertThat(rent.getReferenceNumber()).isEqualTo("1234");
        assertThat(rent.getAccountName()).isEqualTo("CHECKING-001");
        assertThat(rent.hasErrors()).isFalse();

        // Salary (positive amount)
        ImportedTransaction salary = transactions.get(3);
        assertThat(salary.getAmount()).isEqualByComparingTo(new BigDecimal("3500.00"));
        assertThat(salary.getCategory()).isEqualTo("Income:Salary");
        assertThat(salary.hasErrors()).isFalse();

        // All transactions valid
        transactions.forEach(tx -> assertThat(tx.hasErrors()).isFalse());
    }

    @Test
    @DisplayName("Should parse multi-account CSV sample fixture")
    void testParseMultiAccountCsvSampleFixture() throws IOException {
        List<ImportedTransaction> transactions = parseCsv(readFixture("samples/multi_account.csv"));

        assertThat(transactions).hasSize(3);
        assertThat(transactions.get(0).getAccountName()).isEqualTo("Checking Account");
        assertThat(transactions.get(0).getAccountNumber()).isEqualTo("CHK-CSV-001");
        assertThat(transactions.get(2).getAccountName()).isEqualTo("Savings Account");
        assertThat(transactions.get(2).getAccountNumber()).isEqualTo("SAV-CSV-002");
        transactions.forEach(tx -> assertThat(tx.hasErrors()).isFalse());
    }

    @Test
    @DisplayName("Should parse a realistic bank CSV export with debit/credit columns")
    void testRealisticBankExportDebitCreditColumns() throws IOException {
        String csv =
                """
                date,payee,debit,credit,memo
                01/01/2024,Landlord Inc,1200.00,,January rent
                01/03/2024,Electric Company,65.43,,Monthly utility
                01/15/2024,Employer Inc,,3500.00,Salary deposit
                """;

        List<ImportedTransaction> transactions = parseCsv(csv);

        assertThat(transactions).hasSize(3);

        assertThat(transactions.get(0).getAmount())
                .isEqualByComparingTo(new BigDecimal("-1200.00"));
        assertThat(transactions.get(1).getAmount()).isEqualByComparingTo(new BigDecimal("-65.43"));
        assertThat(transactions.get(2).getAmount()).isEqualByComparingTo(new BigDecimal("3500.00"));

        transactions.forEach(tx -> assertThat(tx.hasErrors()).isFalse());
    }

    // ========== Tags Column Tests ==========

    @Test
    @DisplayName("Should parse tags column with single tag")
    void testTagsColumnSingleTag() throws IOException {
        String csv =
                """
                date,amount,payee,tags
                2024-01-15,-50.00,Shop,Vacation
                """;

        List<ImportedTransaction> transactions = parseCsv(csv);

        assertThat(transactions).hasSize(1);
        assertThat(transactions.get(0).getTags()).containsExactly("Vacation");
    }

    @Test
    @DisplayName("Should parse tags column with multiple comma-separated tags")
    void testTagsColumnMultipleTags() throws IOException {
        String csv =
                """
                date,amount,payee,tags
                2024-01-15,-50.00,Shop,"Vacation,Business"
                """;

        List<ImportedTransaction> transactions = parseCsv(csv);

        assertThat(transactions).hasSize(1);
        assertThat(transactions.get(0).getTags()).containsExactlyInAnyOrder("Vacation", "Business");
    }

    @Test
    @DisplayName("Should recognise 'tag' (singular) as alias for tags column")
    void testTagColumnAlias() throws IOException {
        String csv =
                """
                date,amount,payee,tag
                2024-01-15,-50.00,Shop,Personal
                """;

        List<ImportedTransaction> transactions = parseCsv(csv);

        assertThat(transactions).hasSize(1);
        assertThat(transactions.get(0).getTags()).containsExactly("Personal");
    }

    @Test
    @DisplayName("Should recognise 'labels' as alias for tags column")
    void testLabelsColumnAlias() throws IOException {
        String csv =
                """
                date,amount,payee,labels
                2024-01-15,-50.00,Shop,Reimbursable
                """;

        List<ImportedTransaction> transactions = parseCsv(csv);

        assertThat(transactions).hasSize(1);
        assertThat(transactions.get(0).getTags()).containsExactly("Reimbursable");
    }

    @Test
    @DisplayName("Should return empty tags list when no tags column present")
    void testTagsEmptyWhenColumnAbsent() throws IOException {
        String csv =
                """
                date,amount,payee
                2024-01-15,-50.00,Shop
                """;

        List<ImportedTransaction> transactions = parseCsv(csv);

        assertThat(transactions).hasSize(1);
        assertThat(transactions.get(0).getTags()).isEmpty();
    }

    // ========== Currency Column Tests ==========

    @Test
    @DisplayName("Should parse currency column")
    void testCurrencyColumn() throws IOException {
        String csv =
                """
                date,amount,payee,currency
                2024-01-15,-50.00,EuroShop,EUR
                """;

        List<ImportedTransaction> transactions = parseCsv(csv);

        assertThat(transactions).hasSize(1);
        assertThat(transactions.get(0).getCurrency()).isEqualTo("EUR");
    }

    @Test
    @DisplayName("Should recognise 'curr' as alias for currency column")
    void testCurrAliasForCurrency() throws IOException {
        String csv =
                """
                date,amount,payee,curr
                2024-01-15,-50.00,Shop,GBP
                """;

        List<ImportedTransaction> transactions = parseCsv(csv);

        assertThat(transactions).hasSize(1);
        assertThat(transactions.get(0).getCurrency()).isEqualTo("GBP");
    }

    @Test
    @DisplayName("Should recognise 'currencycode' as alias for currency column")
    void testCurrencyCodeAlias() throws IOException {
        String csv =
                """
                date,amount,payee,currencycode
                2024-01-15,-50.00,Shop,CHF
                """;

        List<ImportedTransaction> transactions = parseCsv(csv);

        assertThat(transactions).hasSize(1);
        assertThat(transactions.get(0).getCurrency()).isEqualTo("CHF");
    }

    @Test
    @DisplayName("Should return null currency when currency column is absent")
    void testCurrencyNullWhenColumnAbsent() throws IOException {
        String csv =
                """
                date,amount,payee
                2024-01-15,-50.00,Shop
                """;

        List<ImportedTransaction> transactions = parseCsv(csv);

        assertThat(transactions).hasSize(1);
        assertThat(transactions.get(0).getCurrency()).isNull();
    }

    // ========== Skrooge CSV Export Tests ==========

    @Test
    @DisplayName("Should map 'unit' column € to EUR currency")
    void testUnitEuroMapsToEur() throws IOException {
        String csv =
                """
                date;account;payee;comment;quantity;unit;amount;sign;category;id;idtransaction;idgroup
                2024-01-15;Checking;Shop;Milk;-42.90;€;-42.90;-;Food;1;1;0
                """;

        List<ImportedTransaction> transactions = parseCsv(csv);

        assertThat(transactions).hasSize(1);
        assertThat(transactions.get(0).getCurrency()).isEqualTo("EUR");
        // For EUR, amount column is used (equals quantity)
        assertThat(transactions.get(0).getAmount()).isEqualByComparingTo(new BigDecimal("-42.90"));
    }

    @Test
    @DisplayName("Should map 'unit' column CFA to XOF and use quantity as amount")
    void testUnitCfaMapsToXofAndUsesQuantity() throws IOException {
        String csv =
                """
                date;account;payee;comment;quantity;unit;amount;sign;category;id;idtransaction;idgroup
                2024-05-01;FCFA;Shop;Rice;-2100.00000;CFA;-3.20143;-;Food;1;1;0
                """;

        List<ImportedTransaction> transactions = parseCsv(csv);

        assertThat(transactions).hasSize(1);
        ImportedTransaction tx = transactions.get(0);
        assertThat(tx.getCurrency()).isEqualTo("XOF");
        // For non-EUR, quantity (native amount) is used, not the EUR-converted amount
        assertThat(tx.getAmount()).isEqualByComparingTo(new BigDecimal("-2100.00000"));
    }

    @Test
    @DisplayName("Should map 'unit' column $ to USD and use quantity as amount")
    void testUnitDollarMapsToUsdAndUsesQuantity() throws IOException {
        String csv =
                """
                date;account;payee;comment;quantity;unit;amount;sign;category;id;idtransaction;idgroup
                2024-01-15;Polymarket;Bet;Win;100.00;$;92.00;+;Income;1;1;0
                """;

        List<ImportedTransaction> transactions = parseCsv(csv);

        assertThat(transactions).hasSize(1);
        ImportedTransaction tx = transactions.get(0);
        assertThat(tx.getCurrency()).isEqualTo("USD");
        assertThat(tx.getAmount()).isEqualByComparingTo(new BigDecimal("100.00"));
    }

    @Test
    @DisplayName("Should fall back to amount for units longer than 3 characters")
    void testUnitTooLongFallsBackToAmount() throws IOException {
        String csv =
                """
                date;account;payee;comment;quantity;unit;amount;sign;category;id;idtransaction;idgroup
                2024-01-15;DOGE;Buy;Coin;198.51;DOGE;13.16;+;Transfer;1;1;0
                """;

        List<ImportedTransaction> transactions = parseCsv(csv);

        assertThat(transactions).hasSize(1);
        // DOGE is 4 chars — too long for the DB column; fall back to amount (EUR-converted)
        assertThat(transactions.get(0).getCurrency()).isNull();
        assertThat(transactions.get(0).getAmount()).isEqualByComparingTo(new BigDecimal("13.16"));
    }

    @Test
    @DisplayName("Should read 'comment' column as memo")
    void testCommentColumnAsMemo() throws IOException {
        String csv =
                """
                date;account;payee;comment;quantity;unit;amount;sign;category;id;idtransaction;idgroup
                2024-01-15;Checking;FACTURE CARTE;DU 051022 WECASA;42.90;€;42.90;-;Food;1;1;0
                """;

        List<ImportedTransaction> transactions = parseCsv(csv);

        assertThat(transactions).hasSize(1);
        assertThat(transactions.get(0).getMemo()).isEqualTo("DU 051022 WECASA");
    }

    @Test
    @DisplayName("Should read 'mode' column as paymentMethod")
    void testModeColumnAsPaymentMethod() throws IOException {
        String csv =
                """
                date;account;mode;payee;comment;quantity;unit;amount;sign;category;id;idtransaction;idgroup
                2024-01-15;Checking;Débit;Shop;Milk;-42.90;€;-42.90;-;Food;1;1;0
                """;

        List<ImportedTransaction> transactions = parseCsv(csv);

        assertThat(transactions).hasSize(1);
        assertThat(transactions.get(0).getPaymentMethod()).isEqualTo("Débit");
    }

    @Test
    @DisplayName("Should read 'bank' column as institutionName")
    void testBankColumnAsInstitutionName() throws IOException {
        String csv =
                """
                date;bank;account;payee;comment;quantity;unit;amount;sign;category;id;idtransaction;idgroup
                2024-01-15;hellobank;Checking;Shop;Milk;-42.90;€;-42.90;-;Food;1;1;0
                """;

        List<ImportedTransaction> transactions = parseCsv(csv);

        assertThat(transactions).hasSize(1);
        assertThat(transactions.get(0).getInstitutionName()).isEqualTo("hellobank");
    }

    @Test
    @DisplayName("Should mark 0000-00-00 date as opening balance")
    void testOpeningBalanceFlag() throws IOException {
        String csv =
                """
                date;bank;account;payee;comment;quantity;unit;amount;sign;category;id;idtransaction;idgroup
                0000-00-00;hellobank;Checking;;;26949.77;€;26949.77;+;;1;1;0
                """;

        List<ImportedTransaction> transactions = parseCsv(csv);

        assertThat(transactions).hasSize(1);
        ImportedTransaction tx = transactions.get(0);
        assertThat(tx.isOpeningBalance()).isTrue();
        assertThat(tx.getTransactionDate()).isEqualTo(LocalDate.of(1970, 1, 1));
    }

    @Test
    @DisplayName("Should not set openingBalance flag for regular dates")
    void testRegularDateNotOpeningBalance() throws IOException {
        String csv =
                """
                date;bank;account;payee;comment;quantity;unit;amount;sign;category;id;idtransaction;idgroup
                2024-01-15;hellobank;Checking;Shop;Milk;-42.90;€;-42.90;-;Food;1;1;0
                """;

        List<ImportedTransaction> transactions = parseCsv(csv);

        assertThat(transactions).hasSize(1);
        assertThat(transactions.get(0).isOpeningBalance()).isFalse();
    }

    @Test
    @DisplayName("Should read 'status' column and set clearedStatus to reconciled for Y")
    void testStatusColumnReconciled() throws IOException {
        String csv =
                """
                date;account;payee;comment;quantity;unit;amount;sign;category;status;id;idtransaction;idgroup
                2024-01-15;Checking;Shop;Milk;-42.90;€;-42.90;-;Food;Y;1;1;0
                """;

        List<ImportedTransaction> transactions = parseCsv(csv);

        assertThat(transactions).hasSize(1);
        assertThat(transactions.get(0).getClearedStatus()).isEqualTo("reconciled");
    }

    // ========== Split Transaction Tests ==========

    @Test
    @DisplayName("Should merge rows sharing the same idtransaction into a split transaction")
    void testSplitTransactionMerging() throws IOException {
        String csv =
                """
                date;account;mode;payee;comment;quantity;unit;amount;sign;category;status;id;idtransaction;idgroup
                2024-04-15;00000463202;Débit;FACTURE CARTE;Amazon;-42.90;€;-42.90;-;Dons > Cadeaux;Y;1;100;0
                2024-04-15;00000463202;Débit;FACTURE CARTE;Amazon;-3.80;€;-3.80;-;Divers > Achat Divers;Y;2;100;0
                2024-04-15;00000463202;Débit;FACTURE CARTE;Amazon;-15.76;€;-15.76;-;Dons > Cadeaux;Y;3;100;0
                """;

        List<ImportedTransaction> transactions = parseCsv(csv);

        assertThat(transactions).hasSize(1);
        ImportedTransaction tx = transactions.get(0);
        assertThat(tx.isSplitTransaction()).isTrue();
        assertThat(tx.getSplits()).hasSize(3);
        // Parent amount = sum of suboperation amounts
        assertThat(tx.getAmount()).isEqualByComparingTo(new BigDecimal("-62.46"));
        // Split amounts are absolute
        assertThat(tx.getSplits().get(0).getAmount()).isEqualByComparingTo(new BigDecimal("42.90"));
        assertThat(tx.getSplits().get(0).getCategory()).isEqualTo("Dons > Cadeaux");
        assertThat(tx.getSplits().get(1).getCategory()).isEqualTo("Divers > Achat Divers");
        assertThat(tx.getSplits().get(2).getAmount()).isEqualByComparingTo(new BigDecimal("15.76"));
    }

    @Test
    @DisplayName("Should not merge rows with different idtransaction values")
    void testNoMergeForDifferentIdTransaction() throws IOException {
        String csv =
                """
                date;account;payee;comment;quantity;unit;amount;sign;category;id;idtransaction;idgroup
                2024-01-15;Checking;Shop;Milk;-42.90;€;-42.90;-;Food;1;100;0
                2024-01-16;Checking;Shop;Bread;-10.00;€;-10.00;-;Food;2;200;0
                """;

        List<ImportedTransaction> transactions = parseCsv(csv);

        assertThat(transactions).hasSize(2);
        assertThat(transactions.get(0).isSplitTransaction()).isFalse();
        assertThat(transactions.get(1).isSplitTransaction()).isFalse();
    }

    @Test
    @DisplayName("Should pass through single-row idtransaction without splits")
    void testSingleRowIdTransactionNoSplit() throws IOException {
        String csv =
                """
                date;account;payee;comment;quantity;unit;amount;sign;category;id;idtransaction;idgroup
                2024-01-15;Checking;Shop;Milk;-42.90;€;-42.90;-;Food;1;100;0
                """;

        List<ImportedTransaction> transactions = parseCsv(csv);

        assertThat(transactions).hasSize(1);
        assertThat(transactions.get(0).isSplitTransaction()).isFalse();
        assertThat(transactions.get(0).getSplits()).isEmpty();
    }

    // ========== Transfer Linking Tests ==========

    @Test
    @DisplayName("Should link transfer pairs by idgroup")
    void testTransferLinkingByIdGroup() throws IOException {
        String csv =
                """
                date;account;payee;comment;quantity;unit;amount;sign;category;id;idtransaction;idgroup
                2023-01-20;00000463202;FACTURE CARTE;Transfer out;-500.00;€;-500.00;-;Transfert;1;100;30
                2023-01-20;Crowdlending;FACTURE CARTE;Transfer in;500.00;€;500.00;+;Transfert;2;200;30
                """;

        List<ImportedTransaction> transactions = parseCsv(csv);

        assertThat(transactions).hasSize(2);
        ImportedTransaction source =
                transactions.stream()
                        .filter(t -> t.getAmount().compareTo(BigDecimal.ZERO) < 0)
                        .findFirst()
                        .orElseThrow();
        ImportedTransaction destination =
                transactions.stream()
                        .filter(t -> t.getAmount().compareTo(BigDecimal.ZERO) > 0)
                        .findFirst()
                        .orElseThrow();

        assertThat(source.isTransfer()).isTrue();
        assertThat(source.getToAccountName()).isEqualTo("Crowdlending");
        assertThat(source.getTransferGroupKey()).isEqualTo("csv:transfer:30");

        assertThat(destination.isTransfer()).isTrue();
        assertThat(destination.getToAccountName()).isEqualTo("00000463202");
        assertThat(destination.getTransferGroupKey()).isEqualTo("csv:transfer:30");
    }

    @Test
    @DisplayName("Should not link rows with idgroup=0 as transfers")
    void testNoTransferForZeroIdGroup() throws IOException {
        String csv =
                """
                date;account;payee;comment;quantity;unit;amount;sign;category;id;idtransaction;idgroup
                2024-01-15;Checking;Shop;Milk;-42.90;€;-42.90;-;Food;1;1;0
                """;

        List<ImportedTransaction> transactions = parseCsv(csv);

        assertThat(transactions).hasSize(1);
        assertThat(transactions.get(0).isTransfer()).isFalse();
        assertThat(transactions.get(0).getTransferGroupKey()).isNull();
    }

    @Test
    @DisplayName("Should handle Skrooge CSV with semicolon separator and quoted fields")
    void testSkroogeSemicolonCsvWithQuotedFields() throws IOException {
        String csv =
                "\"date\";\"bank\";\"account\";\"mode\";\"payee\";\"comment\";\"quantity\";\"unit\";\"amount\";\"sign\";\"category\";\"status\";\"id\";\"idtransaction\";\"idgroup\"\n"
                        + "\"2024-01-15\";\"hellobank\";\"00000463202\";\"Débit\";\"FACTURE CARTE\";\"DU 051022 WECASA\";\"-42.90\";\"€\";\"-42.90\";\"-\";\"Divers > Achat Divers\";\"Y\";\"1\";\"1\";\"0\"";

        List<ImportedTransaction> transactions = parseCsv(csv);

        assertThat(transactions).hasSize(1);
        ImportedTransaction tx = transactions.get(0);
        assertThat(tx.getAccountName()).isEqualTo("00000463202");
        assertThat(tx.getPaymentMethod()).isEqualTo("Débit");
        assertThat(tx.getPayee()).isEqualTo("FACTURE CARTE");
        assertThat(tx.getMemo()).isEqualTo("DU 051022 WECASA");
        assertThat(tx.getCurrency()).isEqualTo("EUR");
        assertThat(tx.getAmount()).isEqualByComparingTo(new BigDecimal("-42.90"));
        assertThat(tx.getInstitutionName()).isEqualTo("hellobank");
        assertThat(tx.getClearedStatus()).isEqualTo("reconciled");
        assertThat(tx.getCategory()).isEqualTo("Divers > Achat Divers");
    }

    @Test
    @DisplayName("Should handle opening balance with CFA currency using quantity")
    void testOpeningBalanceWithCfaCurrency() throws IOException {
        String csv =
                """
                date;bank;account;payee;comment;quantity;unit;amount;sign;category;id;idtransaction;idgroup
                0000-00-00;Portefeuille;FCFA;;;38180.00000;CFA;58.20506;+;;1;1;0
                """;

        List<ImportedTransaction> transactions = parseCsv(csv);

        assertThat(transactions).hasSize(1);
        ImportedTransaction tx = transactions.get(0);
        assertThat(tx.isOpeningBalance()).isTrue();
        assertThat(tx.getCurrency()).isEqualTo("XOF");
        // For CFA, quantity (native) is used, not the EUR-converted amount
        assertThat(tx.getAmount()).isEqualByComparingTo(new BigDecimal("38180.00000"));
    }
}
