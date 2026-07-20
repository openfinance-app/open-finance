package org.openfinance.service.parser;

import static org.assertj.core.api.Assertions.*;
import static org.assertj.core.api.Assertions.within;

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
 * Comprehensive test suite for QIF parser.
 *
 * <p>Tests cover: - Basic transaction parsing - Multiple date formats - Split transactions - Edge
 * cases (missing fields, invalid data) - Validation and error reporting - Real-world QIF file
 * scenarios
 *
 * <p>Requirements: - REQ-2.5.1.1: File Format Support - REQ-2.5.1.3: Import Validation
 */
@DisplayName("QIF Parser Tests")
class QifParserTest {

    private QifParser parser;

    @BeforeEach
    void setUp() {
        parser = new QifParser();
    }

    // ========== Basic Parsing Tests ==========

    @Test
    @DisplayName("Should parse single transaction with all fields")
    void testParseSingleTransactionWithAllFields() throws IOException {
        String qif =
                """
                !Type:Bank
                D01/15/2024
                T-45.67
                PStarbucks
                MCoffee and snack
                LFood:Dining
                N1001
                Cc
                ^
                """;

        List<ImportedTransaction> transactions = parseQif(qif);

        assertThat(transactions).hasSize(1);
        ImportedTransaction tx = transactions.get(0);

        assertThat(tx.getTransactionDate()).isEqualTo(LocalDate.of(2024, 1, 15));
        assertThat(tx.getAmount()).isEqualByComparingTo(new BigDecimal("-45.67"));
        assertThat(tx.getPayee()).isEqualTo("Starbucks");
        assertThat(tx.getMemo()).isEqualTo("Coffee and snack");
        assertThat(tx.getCategory()).isEqualTo("Food:Dining");
        assertThat(tx.getReferenceNumber()).isEqualTo("1001");
        assertThat(tx.getClearedStatus()).isEqualTo("cleared");
        assertThat(tx.hasErrors()).isFalse();
    }

    @Test
    @DisplayName("Should parse multiple transactions")
    void testParseMultipleTransactions() throws IOException {
        String qif =
                """
                !Type:Bank
                D01/15/2024
                T-45.67
                PStarbucks
                ^
                D01/16/2024
                T-100.00
                PGrocery Store
                ^
                D01/17/2024
                T2500.00
                PSalary
                ^
                """;

        List<ImportedTransaction> transactions = parseQif(qif);

        assertThat(transactions).hasSize(3);
        assertThat(transactions.get(0).getAmount()).isEqualByComparingTo(new BigDecimal("-45.67"));
        assertThat(transactions.get(1).getAmount()).isEqualByComparingTo(new BigDecimal("-100.00"));
        assertThat(transactions.get(2).getAmount()).isEqualByComparingTo(new BigDecimal("2500.00"));
    }

    @Test
    @DisplayName("Should parse transaction without end marker at EOF")
    void testParseTransactionWithoutEndMarker() throws IOException {
        String qif =
                """
                !Type:Bank
                D01/15/2024
                T-45.67
                PStarbucks
                """;

        List<ImportedTransaction> transactions = parseQif(qif);

        assertThat(transactions).hasSize(1);
        assertThat(transactions.get(0).getPayee()).isEqualTo("Starbucks");
    }

    // ========== Date Format Tests ==========

    @Test
    @DisplayName("Should parse US date format (MM/DD/YYYY)")
    void testParseUSDateFormatLong() throws IOException {
        String qif =
                """
                !Type:Bank
                D12/31/2023
                T-50.00
                PTest
                ^
                """;

        List<ImportedTransaction> transactions = parseQif(qif);

        assertThat(transactions).hasSize(1);
        assertThat(transactions.get(0).getTransactionDate()).isEqualTo(LocalDate.of(2023, 12, 31));
    }

    @Test
    @DisplayName("Should parse international date format (DD/MM/YYYY)")
    void testParseInternationalDateFormat() throws IOException {
        String qif =
                """
                !Type:Bank
                D31/12/2023
                T-50.00
                PTest
                ^
                """;

        List<ImportedTransaction> transactions = parseQif(qif);

        assertThat(transactions).hasSize(1);
        assertThat(transactions.get(0).getTransactionDate()).isEqualTo(LocalDate.of(2023, 12, 31));
    }

    @Test
    @DisplayName("Should parse ISO date format (YYYY-MM-DD)")
    void testParseISODateFormat() throws IOException {
        String qif =
                """
                !Type:Bank
                D2023-12-31
                T-50.00
                PTest
                ^
                """;

        List<ImportedTransaction> transactions = parseQif(qif);

        assertThat(transactions).hasSize(1);
        assertThat(transactions.get(0).getTransactionDate()).isEqualTo(LocalDate.of(2023, 12, 31));
    }

    @Test
    @DisplayName("Should parse short year format (MM/DD/YY)")
    void testParseShortYearFormat() throws IOException {
        String qif =
                """
                !Type:Bank
                D01/15/24
                T-50.00
                PTest
                ^
                """;

        List<ImportedTransaction> transactions = parseQif(qif);

        assertThat(transactions).hasSize(1);
        assertThat(transactions.get(0).getTransactionDate()).isEqualTo(LocalDate.of(2024, 1, 15));
    }

    @Test
    @DisplayName("Should parse date without leading zeros (M/D/YYYY)")
    void testParseDateWithoutLeadingZeros() throws IOException {
        String qif =
                """
                !Type:Bank
                D1/5/2024
                T-50.00
                PTest
                ^
                """;

        List<ImportedTransaction> transactions = parseQif(qif);

        assertThat(transactions).hasSize(1);
        assertThat(transactions.get(0).getTransactionDate()).isEqualTo(LocalDate.of(2024, 1, 5));
    }

    // ========== Amount Parsing Tests ==========

    @Test
    @DisplayName("Should parse negative amounts")
    void testParseNegativeAmount() throws IOException {
        String qif =
                """
                !Type:Bank
                D01/15/2024
                T-123.45
                PExpense
                ^
                """;

        List<ImportedTransaction> transactions = parseQif(qif);

        assertThat(transactions.get(0).getAmount()).isEqualByComparingTo(new BigDecimal("-123.45"));
    }

    @Test
    @DisplayName("Should parse positive amounts")
    void testParsePositiveAmount() throws IOException {
        String qif =
                """
                !Type:Bank
                D01/15/2024
                T1500.00
                PIncome
                ^
                """;

        List<ImportedTransaction> transactions = parseQif(qif);

        assertThat(transactions.get(0).getAmount()).isEqualByComparingTo(new BigDecimal("1500.00"));
    }

    @Test
    @DisplayName("Should parse amount with currency symbols")
    void testParseAmountWithCurrencySymbols() throws IOException {
        String qif =
                """
                !Type:Bank
                D01/15/2024
                T$50.00
                PTest
                ^
                """;

        List<ImportedTransaction> transactions = parseQif(qif);

        assertThat(transactions.get(0).getAmount()).isEqualByComparingTo(new BigDecimal("50.00"));
    }

    @Test
    @DisplayName("Should parse amount with thousands separator")
    void testParseAmountWithThousandsSeparator() throws IOException {
        String qif =
                """
                !Type:Bank
                D01/15/2024
                T1,500.00
                PTest
                ^
                """;

        List<ImportedTransaction> transactions = parseQif(qif);

        assertThat(transactions.get(0).getAmount()).isEqualByComparingTo(new BigDecimal("1500.00"));
    }

    // ========== Category and Transfer Tests ==========

    @Test
    @DisplayName("Should parse category with subcategory")
    void testParseCategoryWithSubcategory() throws IOException {
        String qif =
                """
                !Type:Bank
                D01/15/2024
                T-50.00
                PTest
                LFood:Dining:Restaurants
                ^
                """;

        List<ImportedTransaction> transactions = parseQif(qif);

        assertThat(transactions.get(0).getCategory()).isEqualTo("Food:Dining:Restaurants");
    }

    @Test
    @DisplayName("Should detect transfer transactions")
    void testParseTransferTransaction() throws IOException {
        String qif =
                """
                !Type:Bank
                D01/15/2024
                T-500.00
                PTransfer
                L[Savings Account]
                ^
                """;

        List<ImportedTransaction> transactions = parseQif(qif);

        ImportedTransaction tx = transactions.get(0);
        // Transfer semantics live in the transfer flag and target account — no synthetic category
        assertThat(tx.getCategory()).isNull();
        assertThat(tx.getToAccountName()).isEqualTo("Savings Account");
        assertThat(tx.isTransfer()).isTrue();
    }

    @Test
    @DisplayName("Should detect transfer syntax with class suffix (L[Account]/Class)")
    void testParseTransferTransactionWithClassSuffix() throws IOException {
        String qif =
                """
                !Type:Bank
                D01/15/2024
                T-500.00
                PTransfer
                L[00000463202]/Transfert
                ^
                """;

        List<ImportedTransaction> transactions = parseQif(qif);

        ImportedTransaction tx = transactions.get(0);
        assertThat(tx.getCategory()).isNull();
        assertThat(tx.getToAccountName()).isEqualTo("00000463202");
        assertThat(tx.isTransfer()).isTrue();
        assertThat(tx.getTags()).contains("Transfert");
    }

    // ========== Cleared Status Tests ==========

    @Test
    @DisplayName("Should parse cleared status 'c'")
    void testParseClearedStatusC() throws IOException {
        String qif =
                """
                !Type:Bank
                D01/15/2024
                T-50.00
                PTest
                Cc
                ^
                """;

        List<ImportedTransaction> transactions = parseQif(qif);

        assertThat(transactions.get(0).getClearedStatus()).isEqualTo("cleared");
    }

    @Test
    @DisplayName("Should parse reconciled status 'X'")
    void testParseReconciledStatus() throws IOException {
        String qif =
                """
                !Type:Bank
                D01/15/2024
                T-50.00
                PTest
                CX
                ^
                """;

        List<ImportedTransaction> transactions = parseQif(qif);

        assertThat(transactions.get(0).getClearedStatus()).isEqualTo("reconciled");
    }

    @Test
    @DisplayName("Should parse cleared status '*'")
    void testParseClearedStatusAsterisk() throws IOException {
        String qif =
                """
                !Type:Bank
                D01/15/2024
                T-50.00
                PTest
                C*
                ^
                """;

        List<ImportedTransaction> transactions = parseQif(qif);

        assertThat(transactions.get(0).getClearedStatus()).isEqualTo("cleared");
    }

    // ========== Split Transaction Tests ==========

    @Test
    @DisplayName("Should parse split transactions")
    void testParseSplitTransaction() throws IOException {
        String qif =
                """
                !Type:Bank
                D01/15/2024
                T-100.00
                PGrocery Store
                SFood:Groceries
                EMilk and eggs
                $-60.00
                SHousehold:Supplies
                ECleaning products
                $-40.00
                ^
                """;

        List<ImportedTransaction> transactions = parseQif(qif);

        assertThat(transactions).hasSize(1);
        ImportedTransaction tx = transactions.get(0);

        assertThat(tx.isSplitTransaction()).isTrue();
        assertThat(tx.getSplits()).hasSize(2);

        ImportedTransaction.SplitEntry split1 = tx.getSplits().get(0);
        assertThat(split1.getCategory()).isEqualTo("Food:Groceries");
        assertThat(split1.getMemo()).isEqualTo("Milk and eggs");
        assertThat(split1.getAmount()).isEqualByComparingTo(new BigDecimal("-60.00"));

        ImportedTransaction.SplitEntry split2 = tx.getSplits().get(1);
        assertThat(split2.getCategory()).isEqualTo("Household:Supplies");
        assertThat(split2.getMemo()).isEqualTo("Cleaning products");
        assertThat(split2.getAmount()).isEqualByComparingTo(new BigDecimal("-40.00"));
    }

    @Test
    @DisplayName("Should infer missing transaction amount from split totals")
    void shouldInferMissingTransactionAmountFromSplitTotals() throws IOException {
        String qif =
                """
                !Type:Cash
                D2026-05-01
                PDépenses diverses
                SDivers:Achat Divers
                $-2100
                SAlimentation:Épicerie
                $-2625
                SDons:Cadeaux
                $-10000
                ^
                """;

        List<ImportedTransaction> transactions = parseQif(qif);

        assertThat(transactions).hasSize(1);
        ImportedTransaction tx = transactions.get(0);
        assertThat(tx.getAmount()).isEqualByComparingTo(new BigDecimal("-14725"));
        assertThat(tx.isSplitTransaction()).isTrue();
        assertThat(tx.hasErrors()).isFalse();
    }

    @Test
    @DisplayName("Should validate split amounts match transaction amount")
    void testValidateSplitAmountsMatch() throws IOException {
        String qif =
                """
                !Type:Bank
                D01/15/2024
                T-100.00
                PGrocery Store
                SFood:Groceries
                $-60.00
                SHousehold:Supplies
                $-30.00
                ^
                """;

        List<ImportedTransaction> transactions = parseQif(qif);

        ImportedTransaction tx = transactions.get(0);
        assertThat(tx.hasErrors()).isTrue();
        assertThat(tx.getValidationErrors())
                .anyMatch(err -> err.contains("Split amounts") && err.contains("do not match"));
    }

    @Test
    @DisplayName("Should accept split amounts within ±0.01 rounding tolerance")
    void testSplitAmountsWithinRoundingTolerance() throws IOException {
        // Three-way split of 100.00 → 33.33 + 33.33 + 33.33 = 99.99 (off by 0.01)
        String qif =
                """
                !Type:Bank
                D01/15/2024
                T-100.00
                PThree-way split
                SPart 1
                $-33.33
                SPart 2
                $-33.33
                SPart 3
                $-33.33
                ^
                """;

        List<ImportedTransaction> transactions = parseQif(qif);

        ImportedTransaction tx = transactions.get(0);
        assertThat(tx.hasErrors()).isFalse();
        assertThat(tx.isSplitTransaction()).isTrue();
        assertThat(tx.getSplits()).hasSize(3);
    }

    @Test
    @DisplayName("Should reject split amounts exceeding ±0.01 rounding tolerance")
    void testSplitAmountsExceedingRoundingTolerance() throws IOException {
        // Splits sum to 98.00 but parent is 100.00 (off by 2.00)
        String qif =
                """
                !Type:Bank
                D01/15/2024
                T-100.00
                PBad split
                SPart 1
                $-50.00
                SPart 2
                $-48.00
                ^
                """;

        List<ImportedTransaction> transactions = parseQif(qif);

        ImportedTransaction tx = transactions.get(0);
        assertThat(tx.hasErrors()).isTrue();
        assertThat(tx.getValidationErrors())
                .anyMatch(err -> err.contains("Split amounts") && err.contains("do not match"));
    }

    // ========== Edge Case Tests ==========

    @Test
    @DisplayName("Should handle missing date")
    void testHandleMissingDate() throws IOException {
        String qif =
                """
                !Type:Bank
                T-50.00
                PTest Payee
                ^
                """;

        List<ImportedTransaction> transactions = parseQif(qif);

        assertThat(transactions).hasSize(1);
        ImportedTransaction tx = transactions.get(0);
        assertThat(tx.hasErrors()).isTrue();
        assertThat(tx.getValidationErrors()).contains("Transaction date is required");
    }

    @Test
    @DisplayName("Should handle missing amount")
    void testHandleMissingAmount() throws IOException {
        String qif =
                """
                !Type:Bank
                D01/15/2024
                PTest Payee
                ^
                """;

        List<ImportedTransaction> transactions = parseQif(qif);

        assertThat(transactions).hasSize(1);
        ImportedTransaction tx = transactions.get(0);
        assertThat(tx.hasErrors()).isTrue();
        assertThat(tx.getValidationErrors()).contains("Transaction amount is required");
    }

    @Test
    @DisplayName("Should handle invalid date format")
    void testHandleInvalidDateFormat() throws IOException {
        String qif =
                """
                !Type:Bank
                D2024-15-35
                T-50.00
                PTest
                ^
                """;

        List<ImportedTransaction> transactions = parseQif(qif);

        assertThat(transactions).hasSize(1);
        ImportedTransaction tx = transactions.get(0);
        assertThat(tx.getTransactionDate()).isNull();
        assertThat(tx.hasErrors()).isTrue();
    }

    @Test
    @DisplayName("Should handle invalid amount format")
    void testHandleInvalidAmountFormat() throws IOException {
        String qif =
                """
                !Type:Bank
                D01/15/2024
                Tabc.xyz
                PTest
                ^
                """;

        List<ImportedTransaction> transactions = parseQif(qif);

        assertThat(transactions).hasSize(1);
        ImportedTransaction tx = transactions.get(0);
        assertThat(tx.getAmount()).isNull();
        assertThat(tx.hasErrors()).isTrue();
    }

    @Test
    @DisplayName("Should handle zero amount")
    void testHandleZeroAmount() throws IOException {
        String qif =
                """
                !Type:Bank
                D01/15/2024
                T0.00
                PTest
                ^
                """;

        List<ImportedTransaction> transactions = parseQif(qif);

        assertThat(transactions).hasSize(1);
        ImportedTransaction tx = transactions.get(0);
        assertThat(tx.hasErrors()).isTrue();
        assertThat(tx.getValidationErrors()).contains("Transaction amount cannot be zero");
    }

    @Test
    @DisplayName("Should handle future dates")
    void testHandleFutureDate() throws IOException {
        LocalDate futureDate = LocalDate.now().plusDays(30);
        String qif =
                String.format(
                        """
                        !Type:Bank
                        D%02d/%02d/%d
                        T-50.00
                        PTest
                        ^
                        """,
                        futureDate.getMonthValue(),
                        futureDate.getDayOfMonth(),
                        futureDate.getYear());

        List<ImportedTransaction> transactions = parseQif(qif);

        assertThat(transactions).hasSize(1);
        ImportedTransaction tx = transactions.get(0);
        assertThat(tx.hasErrors()).isTrue();
        assertThat(tx.getValidationErrors())
                .anyMatch(err -> err.contains("cannot be in the future"));
    }

    @Test
    @DisplayName("Should handle empty lines and whitespace")
    void testHandleEmptyLinesAndWhitespace() throws IOException {
        String qif =
                """
                !Type:Bank

                D01/15/2024

                T-50.00

                PTest
                ^
                """;

        List<ImportedTransaction> transactions = parseQif(qif);

        assertThat(transactions).hasSize(1);
        assertThat(transactions.get(0).hasErrors()).isFalse();
    }

    @Test
    @DisplayName("Should handle minimal transaction (date and amount only)")
    void testHandleMinimalTransaction() throws IOException {
        String qif =
                """
                !Type:Bank
                D01/15/2024
                T-50.00
                ^
                """;

        List<ImportedTransaction> transactions = parseQif(qif);

        assertThat(transactions).hasSize(1);
        ImportedTransaction tx = transactions.get(0);
        assertThat(tx.getTransactionDate()).isNotNull();
        assertThat(tx.getAmount()).isNotNull();
        assertThat(tx.getPayee()).isNullOrEmpty();
        assertThat(tx.hasErrors()).isFalse();
    }

    @Test
    @DisplayName("Should handle different account types")
    void testHandleDifferentAccountTypes() throws IOException {
        String qif =
                """
                !Type:CCard
                D01/15/2024
                T-50.00
                PCredit Card Purchase
                ^
                !Type:Cash
                D01/16/2024
                T-20.00
                PCash Payment
                ^
                """;

        List<ImportedTransaction> transactions = parseQif(qif);

        assertThat(transactions).hasSize(2);
        assertThat(transactions.get(0).getPayee()).isEqualTo("Credit Card Purchase");
        assertThat(transactions.get(1).getPayee()).isEqualTo("Cash Payment");
    }

    @Test
    @DisplayName("Should include line numbers for error reporting")
    void testIncludeLineNumbers() throws IOException {
        String qif =
                """
                !Type:Bank
                D01/15/2024
                T-50.00
                PTransaction 1
                ^
                D01/16/2024
                PTransaction 2 - missing amount
                ^
                """;

        List<ImportedTransaction> transactions = parseQif(qif);

        assertThat(transactions).hasSize(2);
        assertThat(transactions.get(0).getLineNumber()).isEqualTo(2);
        assertThat(transactions.get(1).getLineNumber()).isEqualTo(6);
    }

    @Test
    @DisplayName("Should include source file name")
    void testIncludeSourceFileName() throws IOException {
        String qif =
                """
                !Type:Bank
                D01/15/2024
                T-50.00
                PTest
                ^
                """;

        List<ImportedTransaction> transactions =
                parser.parseFile(
                        new ByteArrayInputStream(qif.getBytes(StandardCharsets.UTF_8)),
                        "test_file.qif");

        assertThat(transactions).hasSize(1);
        assertThat(transactions.get(0).getSourceFileName()).isEqualTo("test_file.qif");
    }

    @Test
    @DisplayName("Should handle empty file")
    void testHandleEmptyFile() throws IOException {
        String qif = "";

        List<ImportedTransaction> transactions = parseQif(qif);

        assertThat(transactions).isEmpty();
    }

    @Test
    @DisplayName("Should handle file with only header")
    void testHandleFileWithOnlyHeader() throws IOException {
        String qif = "!Type:Bank\n";

        List<ImportedTransaction> transactions = parseQif(qif);

        assertThat(transactions).isEmpty();
    }

    // ========== Real-World Scenario Tests ==========

    @Test
    @DisplayName("Should parse realistic bank export")
    void testParseRealisticBankExport() throws IOException {
        String qif =
                """
                !Type:Bank
                D01/01/2024
                T-1200.00
                PRent Payment
                MJanuary rent
                LHousing:Rent
                N1234
                CX
                ^
                D01/03/2024
                T-65.43
                PElectric Company
                MMonthly utility bill
                LUtilities:Electric
                C*
                ^
                D01/05/2024
                T-150.00
                PGrocery Store
                MWeekly shopping
                SFood:Groceries
                $-120.00
                SHousehold:Supplies
                $-30.00
                ^
                D01/15/2024
                T3500.00
                PEmployer Inc.
                MSalary deposit
                LIncome:Salary
                CX
                ^
                D01/20/2024
                T-500.00
                PTransfer to Savings
                L[Savings Account]
                ^
                """;

        List<ImportedTransaction> transactions = parseQif(qif);

        assertThat(transactions).hasSize(5);

        // Verify rent payment
        assertThat(transactions.get(0).getPayee()).isEqualTo("Rent Payment");
        assertThat(transactions.get(0).getAmount())
                .isEqualByComparingTo(new BigDecimal("-1200.00"));
        assertThat(transactions.get(0).getClearedStatus()).isEqualTo("reconciled");

        // Verify split transaction
        assertThat(transactions.get(2).isSplitTransaction()).isTrue();
        assertThat(transactions.get(2).getSplits()).hasSize(2);

        // Verify income
        assertThat(transactions.get(3).getAmount()).isEqualByComparingTo(new BigDecimal("3500.00"));
        assertThat(transactions.get(3).getCategory()).isEqualTo("Income:Salary");

        // Verify transfer
        assertThat(transactions.get(4).getCategory()).isNull();
        assertThat(transactions.get(4).getToAccountName()).isEqualTo("Savings Account");

        // All should be valid
        transactions.forEach(tx -> assertThat(tx.hasErrors()).isFalse());
    }

    @Test
    @DisplayName("Should parse multi-account QIF sample fixture")
    void testParseMultiAccountQifSampleFixture() throws IOException {
        List<ImportedTransaction> transactions = parseQifFixture("samples/multi_account.qif");

        assertThat(transactions).hasSize(3);
        assertThat(transactions.get(0).getAccountName()).isEqualTo("Checking Account");
        assertThat(transactions.get(1).isTransfer()).isTrue();
        assertThat(transactions.get(1).getToAccountName()).isEqualTo("Savings Account");
        assertThat(transactions.get(2).getAccountName()).isEqualTo("Savings Account");
        transactions.forEach(tx -> assertThat(tx.hasErrors()).isFalse());
    }

    @Test
    @DisplayName("Should parse paired-transfer QIF fixture with mirrored entries")
    void testParsePairedTransferQifFixture() throws IOException {
        List<ImportedTransaction> transactions =
                parseQifFixture("samples/multi_account_paired_transfer.qif");

        assertThat(transactions).hasSize(2);
        assertThat(transactions.get(0).isTransfer()).isTrue();
        assertThat(transactions.get(1).isTransfer()).isTrue();
        assertThat(transactions.get(0).getAccountName()).isEqualTo("Checking Account");
        assertThat(transactions.get(0).getToAccountName()).isEqualTo("Savings Account");
        assertThat(transactions.get(1).getAccountName()).isEqualTo("Savings Account");
        assertThat(transactions.get(1).getToAccountName()).isEqualTo("Checking Account");
    }

    @Test
    @DisplayName("Should parse extracted QIF sample fixture with transfers and splits")
    void testParseExtractedSkroogeQifFixture() throws IOException {
        List<ImportedTransaction> transactions = parseQifFixture("samples/skrooge_extracted.qif");

        assertThat(transactions).hasSize(3);

        ImportedTransaction categorizedExpense = transactions.get(0);
        assertThat(categorizedExpense.getAccountName()).isEqualTo("00000463202");
        assertThat(categorizedExpense.getTransactionDate()).isEqualTo(LocalDate.of(2022, 10, 6));
        assertThat(categorizedExpense.getCategory()).isEqualTo("Maison:Ménage");
        assertThat(categorizedExpense.getPayee()).isEqualTo("FACTURE CARTE");
        assertThat(categorizedExpense.hasErrors()).isFalse();

        ImportedTransaction splitExpense = transactions.get(1);
        assertThat(splitExpense.getAccountName()).isEqualTo("00000463202");
        assertThat(splitExpense.isSplitTransaction()).isTrue();
        assertThat(splitExpense.getSplits()).hasSize(3);
        assertThat(splitExpense.getSplits().get(0).getCategory())
                .isEqualTo("Alimentation:Épicerie");
        assertThat(splitExpense.getSplits().get(0).getMemo()).contains("AFRO-EXOTIQUE.C");
        assertThat(splitExpense.getSplits().get(0).getAmount())
                .isEqualByComparingTo(new BigDecimal("-17.7"));
        assertThat(splitExpense.getSplits().get(2).getCategory()).isEqualTo("Divers:Achat Divers");
        assertThat(splitExpense.getSplits().get(2).getMemo()).isEqualTo("Expédition");
        assertThat(splitExpense.hasErrors()).isFalse();

        ImportedTransaction transfer = transactions.get(2);
        assertThat(transfer.getAccountName()).isEqualTo("00035387058");
        assertThat(transfer.isTransfer()).isTrue();
        assertThat(transfer.getCategory()).isNull();
        assertThat(transfer.getToAccountName()).isEqualTo("00000463202");
        assertThat(transfer.getTags()).contains("Transfert");
        assertThat(transfer.hasErrors()).isFalse();
    }

    // ========== U Field Tests ==========

    @Test
    @DisplayName("Should use U field amount when T field is absent")
    void testUFieldFallbackWhenTAbsent() throws IOException {
        String qif =
                """
                !Type:Bank
                D01/15/2024
                U-99.50
                PTest Payee
                ^
                """;

        List<ImportedTransaction> transactions = parseQif(qif);

        assertThat(transactions).hasSize(1);
        assertThat(transactions.get(0).getAmount()).isEqualByComparingTo(new BigDecimal("-99.50"));
        assertThat(transactions.get(0).hasErrors()).isFalse();
    }

    @Test
    @DisplayName("Should prefer T field amount over U field when both present")
    void testTFieldWinsOverUField() throws IOException {
        String qif =
                """
                !Type:Bank
                D01/15/2024
                T-45.00
                U-99.50
                PTest Payee
                ^
                """;

        List<ImportedTransaction> transactions = parseQif(qif);

        assertThat(transactions).hasSize(1);
        assertThat(transactions.get(0).getAmount()).isEqualByComparingTo(new BigDecimal("-45.00"));
    }

    // ========== !Account Block Tests ==========

    @Test
    @DisplayName("Should propagate account name from !Account block to subsequent transactions")
    void testAccountBlockPropagation() throws IOException {
        String qif =
                """
                !Account
                NChecking Account
                TBank
                ^
                !Type:Bank
                D01/15/2024
                T-50.00
                PStore A
                ^
                D01/16/2024
                T-75.00
                PStore B
                ^
                """;

        List<ImportedTransaction> transactions = parseQif(qif);

        assertThat(transactions).hasSize(2);
        assertThat(transactions.get(0).getAccountName()).isEqualTo("Checking Account");
        assertThat(transactions.get(1).getAccountName()).isEqualTo("Checking Account");
    }

    @Test
    @DisplayName("Should propagate QIF account type from !Account block to standard transactions")
    void shouldPropagateQifAccountTypeToStandardTransactions() throws IOException {
        String qif =
                """
                !Account
                NMobile Money
                TCCard
                ^
                !Type:CCard
                D2026-04-28
                T-10.00
                PPayment
                ^
                """;

        List<ImportedTransaction> transactions = parseQif(qif);

        assertThat(transactions).hasSize(1);
        assertThat(transactions.get(0).getAccountName()).isEqualTo("Mobile Money");
        assertThat(transactions.get(0).getQifAccountType()).isEqualTo("CCard");
    }

    // ========== Investment Transaction Tests ==========

    @Test
    @DisplayName("Should parse investment transaction (!Type:Invst) with key fields")
    void testInvestmentTransactionParsing() throws IOException {
        String qif =
                """
                !Type:Invst
                D03/10/2024
                NBuy
                YACME Corp
                T-5000.00
                MBought 100 shares
                ^
                """;

        List<ImportedTransaction> transactions = parseQif(qif);

        assertThat(transactions).hasSize(1);
        ImportedTransaction tx = transactions.get(0);
        assertThat(tx.getTransactionDate()).isEqualTo(LocalDate.of(2024, 3, 10));
        assertThat(tx.getAmount()).isEqualByComparingTo(new BigDecimal("-5000.00"));
        assertThat(tx.getReferenceNumber()).isEqualTo("Buy");
        assertThat(tx.getPayee()).isEqualTo("ACME Corp");
    }

    @Test
    @DisplayName("Should compute amount from Q × I when T is missing in investment transaction")
    void testInvestmentAmountFromQuantityTimesPrice() throws IOException {
        String qif =
                """
                !Type:Invst
                D2024-11-14
                NBuy
                YADA
                Q100.6
                I0.1653551854
                ^
                """;

        List<ImportedTransaction> transactions = parseQif(qif);

        assertThat(transactions).hasSize(1);
        ImportedTransaction tx = transactions.get(0);
        assertThat(tx.getAmount()).isNotNull();
        assertThat(tx.hasErrors()).isFalse();
        // 100.6 * 0.1653551854 ≈ 16.631...
        assertThat(tx.getAmount().doubleValue()).isCloseTo(16.63, within(0.01));
    }

    @Test
    @DisplayName("Should negate inferred investment amount for Sell actions")
    void shouldNegateInferredInvestmentAmountForSellActions() throws IOException {
        String qif =
                """
                !Type:Invst
                D2025-06-18
                NSell
                YB504
                PFrais d’EDD / RC (304€)
                CR
                Q0.00135111
                I225000
                ^
                """;

        List<ImportedTransaction> transactions = parseQif(qif);

        assertThat(transactions).hasSize(1);
        ImportedTransaction tx = transactions.get(0);
        assertThat(tx.getAmount()).isEqualByComparingTo(new BigDecimal("-303.99975000"));
        assertThat(tx.hasErrors()).isFalse();
    }

    @Test
    @DisplayName("Should handle investment opening balance with Q=0 and I=nan gracefully")
    void testInvestmentZeroQuantityOpeningBalance() throws IOException {
        String qif =
                """
                !Type:Invst
                D2026-07-05
                NSell
                YADA
                CR
                Q0
                Inan
                ^
                """;

        List<ImportedTransaction> transactions = parseQif(qif);

        assertThat(transactions).hasSize(1);
        ImportedTransaction tx = transactions.get(0);
        // I=nan is not a valid number, so amount can't be computed — transaction
        // should have errors but not crash
        assertThat(tx.hasErrors()).isTrue();
    }

    @Test
    @DisplayName("Should parse 'R' cleared status as reconciled")
    void testReconciledStatusR() throws IOException {
        String qif =
                """
                !Type:Bank
                D01/15/2024
                T-45.67
                PStarbucks
                CR
                ^
                """;

        List<ImportedTransaction> transactions = parseQif(qif);

        assertThat(transactions).hasSize(1);
        assertThat(transactions.get(0).getClearedStatus()).isEqualTo("reconciled");
    }

    @Test
    @DisplayName("Should skip !Type:Cat section without generating transaction warnings")
    void testSkipCatType() throws IOException {
        String qif =
                """
                !Type:Cat
                NAlimentation
                E
                ^
                NAlimentation:Restaurant
                E
                ^
                !Type:Bank
                D01/15/2024
                T-50.00
                PReal Transaction
                ^
                """;

        List<ImportedTransaction> transactions = parseQif(qif);

        assertThat(transactions).hasSize(1);
        assertThat(transactions.get(0).getPayee()).isEqualTo("Real Transaction");
        assertThat(transactions.get(0).getAmount()).isEqualByComparingTo(new BigDecimal("-50.00"));
    }

    // ========== Skip !Type Tests ==========

    @Test
    @DisplayName("Should skip transactions in !Type:Memorized section")
    void testSkipMemorizedType() throws IOException {
        String qif =
                """
                !Type:Memorized
                D01/15/2024
                T-50.00
                PMemorized Payee
                ^
                !Type:Bank
                D01/15/2024
                T-100.00
                PReal Transaction
                ^
                """;

        List<ImportedTransaction> transactions = parseQif(qif);

        assertThat(transactions).hasSize(1);
        assertThat(transactions.get(0).getPayee()).isEqualTo("Real Transaction");
    }

    @Test
    @DisplayName("Should skip records in !Type:Prices section")
    void testSkipPricesType() throws IOException {
        String qif =
                """
                !Type:Prices
                "ACME",45.50,"01/15/2024"
                ^
                !Type:Bank
                D01/15/2024
                T-20.00
                PShop
                ^
                """;

        List<ImportedTransaction> transactions = parseQif(qif);

        assertThat(transactions).hasSize(1);
        assertThat(transactions.get(0).getAmount()).isEqualByComparingTo(new BigDecimal("-20.00"));
    }

    // ========== Category Class Separator Tests ==========

    @Test
    @DisplayName("Should parse category class separator and store class as tag")
    void testCategoryClassSeparator() throws IOException {
        String qif =
                """
                !Type:Bank
                D01/15/2024
                T-50.00
                PRestaurant
                LFood:Dining/Business
                ^
                """;

        List<ImportedTransaction> transactions = parseQif(qif);

        assertThat(transactions).hasSize(1);
        ImportedTransaction tx = transactions.get(0);
        assertThat(tx.getCategory()).isEqualTo("Food:Dining");
        assertThat(tx.getTags()).containsExactly("Business");
    }

    // ========== Date Separator Variant Tests ==========

    @Test
    @DisplayName("Should parse date with dash separator (MM-DD-YYYY)")
    void testDateWithDashSeparator() throws IOException {
        String qif =
                """
                !Type:Bank
                D01-15-2024
                T-50.00
                PTest
                ^
                """;

        List<ImportedTransaction> transactions = parseQif(qif);

        assertThat(transactions).hasSize(1);
        assertThat(transactions.get(0).getTransactionDate()).isEqualTo(LocalDate.of(2024, 1, 15));
    }

    @Test
    @DisplayName("Should parse date with dot separator (MM.DD.YYYY)")
    void testDateWithDotSeparator() throws IOException {
        String qif =
                """
                !Type:Bank
                D01.15.2024
                T-50.00
                PTest
                ^
                """;

        List<ImportedTransaction> transactions = parseQif(qif);

        assertThat(transactions).hasSize(1);
        assertThat(transactions.get(0).getTransactionDate()).isEqualTo(LocalDate.of(2024, 1, 15));
    }

    // ========== European Amount Format Tests ==========

    @Test
    @DisplayName("Should parse European amount format (1.234,56)")
    void testEuropeanAmountFormat() throws IOException {
        String qif =
                """
                !Type:Bank
                D01/15/2024
                T1.234,56
                PTest
                ^
                """;

        List<ImportedTransaction> transactions = parseQif(qif);

        assertThat(transactions).hasSize(1);
        assertThat(transactions.get(0).getAmount()).isEqualByComparingTo(new BigDecimal("1234.56"));
    }

    // ========== Graceful Field Tests ==========

    @Test
    @DisplayName("Should not error on % split percentage field")
    void testSplitPercentageFieldIgnored() throws IOException {
        String qif =
                """
                !Type:Bank
                D01/15/2024
                T-100.00
                PShop
                SFood:Groceries
                $-60.00
                %60
                SHousehold:Supplies
                $-40.00
                %40
                ^
                """;

        List<ImportedTransaction> transactions = parseQif(qif);

        assertThat(transactions).hasSize(1);
        assertThat(transactions.get(0).hasErrors()).isFalse();
        assertThat(transactions.get(0).getSplits()).hasSize(2);
    }

    @Test
    @DisplayName("Should not error on F reimbursable flag field")
    void testReimbursableFlagIgnored() throws IOException {
        String qif =
                """
                !Type:Bank
                D01/15/2024
                T-75.00
                PBusiness Lunch
                LMeals:Business
                FY
                ^
                """;

        List<ImportedTransaction> transactions = parseQif(qif);

        assertThat(transactions).hasSize(1);
        assertThat(transactions.get(0).hasErrors()).isFalse();
        assertThat(transactions.get(0).getCategory()).isEqualTo("Meals:Business");
    }

    @Test
    @DisplayName("Should ignore !Option:AllXfr directive and still parse transactions")
    void testOptionDirectiveIgnored() throws IOException {
        String qif =
                """
                !Option:AllXfr
                !Type:Bank
                D01/15/2024
                T-30.00
                PGas Station
                ^
                """;

        List<ImportedTransaction> transactions = parseQif(qif);

        assertThat(transactions).hasSize(1);
        assertThat(transactions.get(0).getAmount()).isEqualByComparingTo(new BigDecimal("-30.00"));
        assertThat(transactions.get(0).getPayee()).isEqualTo("Gas Station");
    }

    // ========== Helper Methods ==========

    private List<ImportedTransaction> parseQif(String qifContent) throws IOException {
        InputStream inputStream =
                new ByteArrayInputStream(qifContent.getBytes(StandardCharsets.UTF_8));
        // Pin an English context so validation-message assertions are locale-independent
        return parser.parseFile(
                inputStream, "test.qif", new ImportParseContext(java.util.Locale.ENGLISH, true));
    }

    // ========== Convention & Legacy Format Tests ==========

    @Test
    @DisplayName("Should parse European comma-decimal amount without thousands separator")
    void testCommaDecimalAmount() throws IOException {
        String qif =
                """
                !Type:Bank
                D01/15/2024
                T1234,56
                PEuropean Export
                ^
                """;

        List<ImportedTransaction> transactions = parseQif(qif);

        assertThat(transactions).hasSize(1);
        assertThat(transactions.get(0).getAmount()).isEqualByComparingTo(new BigDecimal("1234.56"));
    }

    @Test
    @DisplayName("Should parse European thousands-separated comma-decimal amount")
    void testEuropeanThousandsAndCommaDecimalAmount() throws IOException {
        String qif =
                """
                !Type:Bank
                D01/15/2024
                T-1.234,56
                PEuropean Export
                ^
                """;

        List<ImportedTransaction> transactions = parseQif(qif);

        assertThat(transactions).hasSize(1);
        assertThat(transactions.get(0).getAmount())
                .isEqualByComparingTo(new BigDecimal("-1234.56"));
    }

    @Test
    @DisplayName("Should parse apostrophe-notation year (01/15'00)")
    void testApostropheYearNotation() throws IOException {
        String qif =
                """
                !Type:Bank
                D01/15'00
                T-50.00
                PLegacy
                ^
                """;

        List<ImportedTransaction> transactions = parseQif(qif);

        assertThat(transactions).hasSize(1);
        assertThat(transactions.get(0).getTransactionDate()).isEqualTo(LocalDate.of(2000, 1, 15));
        assertThat(transactions.get(0).hasErrors()).isFalse();
    }

    @Test
    @DisplayName("Should parse space-padded apostrophe date (1/ 6'01)")
    void testSpacePaddedApostropheDate() throws IOException {
        String qif =
                """
                !Type:Bank
                D1/ 6'01
                T-50.00
                PLegacy
                ^
                """;

        List<ImportedTransaction> transactions = parseQif(qif);

        assertThat(transactions).hasSize(1);
        assertThat(transactions.get(0).getTransactionDate()).isEqualTo(LocalDate.of(2001, 1, 6));
        assertThat(transactions.get(0).hasErrors()).isFalse();
    }

    @Test
    @DisplayName("Should resolve legacy two-digit year into the past via sliding pivot")
    void testTwoDigitYearPivotKeepsLegacyDateInPast() throws IOException {
        String qif =
                """
                !Type:Bank
                D01/15/95
                T-50.00
                PLegacy
                ^
                """;

        List<ImportedTransaction> transactions = parseQif(qif);

        assertThat(transactions).hasSize(1);
        assertThat(transactions.get(0).getTransactionDate()).isEqualTo(LocalDate.of(1995, 1, 15));
        // The resolved date must not trip the future-date validation
        assertThat(transactions.get(0).getValidationErrors())
                .noneMatch(error -> error.contains("future"));
    }

    @Test
    @DisplayName("Should decode windows-1252 encoded Quicken export")
    void testWindows1252Charset() throws IOException {
        // "Débit €" encoded in windows-1252 (é = 0xE9, € = 0x80)
        byte[] cp1252Bytes;
        {
            String text = "!Type:Bank\nD01/15/2024\nT-50.00\nPCaf\u00E9 \u20AC\n^\n";
            java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
            for (char c : text.toCharArray()) {
                if (c == '\u00E9') {
                    out.write(0xE9);
                } else if (c == '\u20AC') {
                    out.write(0x80);
                } else {
                    out.write((byte) c);
                }
            }
            cp1252Bytes = out.toByteArray();
        }

        List<ImportedTransaction> transactions =
                parser.parseFile(new ByteArrayInputStream(cp1252Bytes), "legacy.qif");

        assertThat(transactions).hasSize(1);
        assertThat(transactions.get(0).getPayee()).isEqualTo("Caf\u00E9 \u20AC");
    }

    private List<ImportedTransaction> parseQifFixture(String resourcePath) throws IOException {
        return parseQif(readFixture(resourcePath));
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
}
