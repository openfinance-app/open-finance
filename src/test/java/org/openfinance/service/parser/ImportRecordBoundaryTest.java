package org.openfinance.service.parser;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;
import java.util.Locale;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.openfinance.dto.ImportedTransaction;

@DisplayName(
        "Bank import boundaries preserve record identity, exact money and incomplete-row errors")
class ImportRecordBoundaryTest {
    private static final ImportParseContext CONTEXT = new ImportParseContext(Locale.ENGLISH, true);

    private static ByteArrayInputStream stream(String content) {
        return new ByteArrayInputStream(content.getBytes(StandardCharsets.UTF_8));
    }

    private List<ImportedTransaction> qif(String content) throws IOException {
        return new QifParser().parseFile(stream(content), "boundary.qif", CONTEXT);
    }

    private List<ImportedTransaction> csv(String content) throws IOException {
        return new CsvParser().parseFile(stream(content), "boundary.csv", CONTEXT);
    }

    @ParameterizedTest
    @ValueSource(
            strings = {
                "NBuy",
                "YIgnored security",
                "MIgnored memo",
                "PIgnored payee",
                "L[Ignored account]",
                "C*",
                "$broken"
            })
    void investmentFieldsBeforeARecordCannotLeakIntoTheNextInvestment(String orphan)
            throws IOException {
        List<ImportedTransaction> rows =
                qif(
                        "!Type:Invst\n"
                                + orphan
                                + "\n^\nD01/15/2024\nNBuy\nYActual security\nQ2\nI5\nT10\n^\n");
        assertThat(rows)
                .singleElement()
                .satisfies(
                        row -> {
                            assertThat(row.getPayee()).isEqualTo("Actual security");
                            assertThat(row.getMemo()).isNull();
                            assertThat(row.getToAccountName()).isNull();
                            assertThat(row.getAmount().abs()).isEqualByComparingTo("10");
                            assertThat(row.getInvestmentQuantity()).isEqualByComparingTo("2");
                        });
    }

    @ParameterizedTest
    @ValueSource(
            strings = {
                "",
                "!Type:Bank\n",
                "!Account\nNChecking\n^\n",
                "!Account\nNChecking\nTBank\n^\n!Type:Invst\n"
            })
    void anAmountBeforeADateRemainsVisibleAsAnInvalidRow(String header) throws IOException {
        List<ImportedTransaction> rows =
                qif(header + "T10\nPFirst\n^\nD01/15/2024\nT20\nPSecond\n^\n");
        assertThat(rows).hasSize(2);
        assertThat(rows.getFirst().getTransactionDate()).isNull();
        assertThat(rows.getFirst().hasErrors()).isTrue();
        assertThat(rows.getFirst().getAmount()).isEqualByComparingTo("10");
        assertThat(rows.getLast().getTransactionDate()).isEqualTo(LocalDate.of(2024, 1, 15));
        assertThat(rows.getLast().getAmount()).isEqualByComparingTo("20");
    }

    @ParameterizedTest
    @CsvSource(
            value = {
                "1-15'2024,2024-01-15",
                "1.15'2024,2024-01-15",
                "1/ 6'01,2001-01-06",
                "01/15/99,1999-01-15"
            })
    void acceptsQuickenYearSeparatorsAndPaddedDateParts(String input, LocalDate expected)
            throws IOException {
        assertThat(qif("!Type:Bank\nD" + input + "\nT10\n^\n").getFirst().getTransactionDate())
                .isEqualTo(expected);
    }

    @ParameterizedTest
    @ValueSource(strings = {"D\nT10", "D01/15/2024\nT", "D01/15/2024\nTabc", "D15'2024\nT10"})
    void malformedDateOrAmountProducesAnExplicitReviewError(String fields) throws IOException {
        assertThat(qif("!Type:Bank\n" + fields + "\nPReview me\n^\n"))
                .singleElement()
                .satisfies(
                        row -> {
                            assertThat(row.hasErrors()).isTrue();
                            assertThat(row.getPayee()).isEqualTo("Review me");
                        });
    }

    @ParameterizedTest
    @CsvSource(
            value = {
                "/Work,NULL,Work",
                "Food/,Food,NULL",
                "Food/Work,Food,Work",
                "[Savings]/Work,NULL,Work",
                "[Savings, [Savings,NULL"
            },
            nullValues = "NULL")
    void classTagsDoNotBecomeCategoriesOrLoseTransferSemantics(
            String category, String expectedCategory, String tag) throws IOException {
        ImportedTransaction row =
                qif("!Type:Bank\nD01/15/2024\nT10\nL" + category + "\nL" + category + "\n^\n")
                        .getFirst();
        assertThat(row.getCategory()).isEqualTo(expectedCategory);
        assertThat(row.getTags()).containsExactlyElementsOf(tag == null ? List.of() : List.of(tag));
        assertThat(row.isTransfer()).isEqualTo(category.startsWith("[Savings]"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"different-account", "split", "not-transfer", "self-transfer"})
    void anOpeningBalanceLabelOnlyInitializesItsOwnUnsplitAccount(String variant)
            throws IOException {
        String category =
                variant.equals("not-transfer")
                        ? "Equity"
                        : variant.equals("different-account") ? "[Other]" : "[Checking]";
        String split = variant.equals("split") ? "SPart\n$10\n" : "";
        ImportedTransaction row =
                qif("!Account\nNChecking\nTBank\n^\n!Type:Bank\nD01/15/2024\nT10\nPOpening Balance\nL"
                                + category
                                + "\n"
                                + split
                                + "^\n")
                        .getFirst();
        assertThat(row.isOpeningBalance()).isEqualTo(variant.equals("self-transfer"));
        assertThat(row.getAccountName()).isEqualTo("Checking");
        assertThat(row.getAmount()).isEqualByComparingTo("10");
    }

    @Test
    void incompleteSplitAmountsRemainAvailableForConfirmationValidation() throws IOException {
        ImportedTransaction row =
                qif("!Type:Bank\nD01/15/2024\nSFood\n$\nSTravel\n$10\n^\n").getFirst();
        assertThat(row.getSplits()).hasSize(2);
        assertThat(row.getAmount()).isEqualByComparingTo("10");
        assertThat(row.getSplits().getFirst().getAmount()).isNull();
    }

    @Test
    void emptyClearedStatusDoesNotMarkATransactionReconciled() throws IOException {
        assertThat(qif("!Type:Bank\nD01/15/2024\nT10\nC\n^\n").getFirst().getClearedStatus())
                .isEqualTo("uncleared");
    }

    @Test
    void positionalCsvKeepsExtraColumnsAndDoesNotTreatAPayeeNamedDateAsAHeader()
            throws IOException {
        ImportedTransaction row =
                csv("2024-01-15,9007199254740993.25,Date,Invoice,Food,ref,extra").getFirst();
        assertThat(row.getPayee()).isEqualTo("Date");
        assertThat(row.getReferenceNumber()).isEqualTo("ref");
        assertThat(row.getAmount()).isEqualByComparingTo("9007199254740993.25");
        assertThat(row.hasErrors()).isFalse();
    }

    @ParameterizedTest
    @CsvSource(
            value = {"10,20,-10", "0,20,20", "-10,, -10", ",20,20", ",invalid,NULL"},
            nullValues = "NULL")
    void debitAndCreditColumnsUseNonzeroDebitBeforeCredit(
            String debit, String credit, String expected) throws IOException {
        ImportedTransaction row =
                csv("date,debit,credit,payee\n2024-01-15,"
                                + (debit == null ? "" : debit)
                                + ","
                                + (credit == null ? "" : credit)
                                + ",Shop")
                        .getFirst();
        if (expected == null) {
            assertThat(row.getAmount()).isNull();
            assertThat(row.hasErrors()).isTrue();
        } else assertThat(row.getAmount()).isEqualByComparingTo(expected);
    }

    @ParameterizedTest
    @CsvSource(
            value = {"Shop,SHOP,NULL", "Shop,Invoice,Invoice", ",Invoice,NULL"},
            nullValues = "NULL")
    void descriptionOnlyBecomesAMemoWhenItAddsInformation(
            String payee, String description, String expectedMemo) throws IOException {
        ImportedTransaction row =
                csv("date,amount,payee,description\n2024-01-15,10,"
                                + (payee == null ? "" : payee)
                                + ","
                                + description)
                        .getFirst();
        assertThat(row.getMemo()).isEqualTo(expectedMemo);
        assertThat(row.getPayee()).isEqualTo(payee == null ? description : payee);
    }

    @ParameterizedTest
    @ValueSource(strings = {",, ", "Work, ,Travel"})
    void csvTagsIgnoreBlankEntries(String tags) throws IOException {
        ImportedTransaction row =
                csv("date;amount;tags;account;accountnumber\n2024-01-15;10;" + tags + ";;1234")
                        .getFirst();
        assertThat(row.getTags())
                .containsExactlyElementsOf(
                        tags.startsWith("Work") ? List.of("Work", "Travel") : List.of());
        assertThat(row.getAccountName()).isEqualTo("1234");
        assertThat(row.getAccountNumber()).isEqualTo("1234");
    }

    @Test
    void blankRowsDoNotShiftErrorLineNumbersForIncompleteCsvRecords() throws IOException {
        List<ImportedTransaction> rows =
                csv("date,amount,payee\n\n2024-01-15,10,Shop\n2024-01-16,20\n\n");
        assertThat(rows).hasSize(2);
        assertThat(rows.getFirst().getLineNumber()).isEqualTo(3);
        assertThat(rows.getLast().getLineNumber()).isEqualTo(4);
        assertThat(rows.getLast().hasErrors()).isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = {"date", "amount", "payee", "memo", "reference", "currency"})
    void emptyPrimaryColumnsFallBackToTheirPopulatedAliases(String field) throws IOException {
        String header =
                "date,posteddate,amount,transactionamount,payee,merchant,memo,notes,referencenumber,fitid,currency,devise\n";
        String[] values = {
            "2024-01-15",
            "2024-01-16",
            "10",
            "11",
            "Primary shop",
            "Fallback shop",
            "Primary memo",
            "Fallback memo",
            "primary-ref",
            "fallback-ref",
            "EUR",
            "USD"
        };
        int blank =
                switch (field) {
                    case "date" -> 0;
                    case "amount" -> 2;
                    case "payee" -> 4;
                    case "memo" -> 6;
                    case "reference" -> 8;
                    default -> 10;
                };
        values[blank] = " ";
        ImportedTransaction row = csv(header + String.join(",", values)).getFirst();
        assertThat(row.getTransactionDate())
                .isEqualTo(LocalDate.of(2024, 1, field.equals("date") ? 16 : 15));
        assertThat(row.getAmount()).isEqualByComparingTo(field.equals("amount") ? "11" : "10");
        assertThat(row.getPayee())
                .isEqualTo(field.equals("payee") ? "Fallback shop" : "Primary shop");
        assertThat(row.getMemo())
                .isEqualTo(field.equals("memo") ? "Fallback memo" : "Primary memo");
        assertThat(row.getReferenceNumber())
                .isEqualTo(field.equals("reference") ? "fallback-ref" : "primary-ref");
        assertThat(row.getCurrency()).isEqualTo(field.equals("currency") ? "USD" : "EUR");
        assertThat(row.hasErrors()).isFalse();
    }
}
