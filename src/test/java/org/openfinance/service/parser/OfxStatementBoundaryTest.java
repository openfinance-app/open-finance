package org.openfinance.service.parser;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.Locale;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.openfinance.dto.ImportParseResult;
import org.openfinance.dto.ImportedTransaction;

@DisplayName("OFX statements retain account boundaries, native currencies and reference precedence")
class OfxStatementBoundaryTest {
    @ParameterizedTest
    @CsvSource(
            value = {
                "<FITID>bank-id</FITID><CHECKNUM>check</CHECKNUM><REFNUM>reference</REFNUM>|bank-id|true",
                "<FITID/><CHECKNUM>check</CHECKNUM><REFNUM>reference</REFNUM>|check|false",
                "<FITID> </FITID><CHECKNUM/><REFNUM>reference</REFNUM>|reference|false",
                "<REFNUM>reference</REFNUM>|reference|false",
                "<FITID/><CHECKNUM/><REFNUM/>|NULL|false",
                "''|NULL|false"
            },
            delimiter = '|',
            nullValues = "NULL")
    void onlyBankIssuedFitIdsAreAuthoritative(
            String fields, String reference, boolean authoritative) throws IOException {
        ImportedTransaction row = bankRow(fields);
        assertThat(row.getReferenceNumber()).isEqualTo(reference);
        assertThat(row.getAuthoritativeReference()).isEqualTo(authoritative);
    }

    @ParameterizedTest
    @CsvSource(
            value = {
                "<NAME> Store </NAME><MEMO>Note</MEMO>|Store|Note",
                "<NAME/><MEMO> Memo payee </MEMO>|Memo payee|Memo payee",
                "<NAME> </NAME><MEMO/>|NULL|''",
                "<MEMO>Fallback</MEMO>|Fallback|Fallback",
                "''|NULL|NULL"
            },
            delimiter = '|',
            nullValues = "NULL")
    void keepsPayeeAndMemoSeparateAndUsesMemoOnlyWhenTheNameIsMissing(
            String fields, String payee, String memo) throws IOException {
        ImportedTransaction row = bankRow(fields);
        assertThat(row.getPayee()).isEqualTo(payee);
        assertThat(row.getMemo()).isEqualTo(memo);
    }

    @ParameterizedTest
    @CsvSource(
            value = {
                "<DTPOSTED>20250110</DTPOSTED><DTUSER>20250109</DTUSER>|2025-01-10",
                "<DTPOSTED/><DTUSER>20250109</DTUSER>|2025-01-09",
                "<DTPOSTED>bad</DTPOSTED><DTUSER>20250109</DTUSER>|2025-01-09",
                "<DTUSER>20250109</DTUSER>|2025-01-09",
                "<DTPOSTED/><DTUSER/>|NULL",
                "''|NULL"
            },
            delimiter = '|',
            nullValues = "NULL")
    void dateFallbackDoesNotOverwriteAValidPostedDate(String fields, String date)
            throws IOException {
        ImportedTransaction row =
                parse(
                                statement(
                                        "STMTRS",
                                        "BANKACCTFROM",
                                        "A",
                                        "<CURDEF>EUR</CURDEF>",
                                        "<STMTTRN><TRNAMT>-1</TRNAMT>" + fields + "</STMTTRN>",
                                        ""))
                        .getTransactions()
                        .getFirst();
        assertThat(row.getTransactionDate()).isEqualTo(date == null ? null : LocalDate.parse(date));
        assertThat(row.hasErrors()).isEqualTo(date == null);
    }

    @ParameterizedTest
    @ValueSource(strings = {"STMTRS", "CCSTMTRS", "INVSTMTRS"})
    void keepsMissingBlankAndZeroLedgerBalancesDistinctForEveryStatementKind(String kind)
            throws IOException {
        String accountTag =
                kind.equals("STMTRS")
                        ? "BANKACCTFROM"
                        : kind.equals("CCSTMTRS") ? "CCACCTFROM" : "INVACCTFROM";
        for (String ledger :
                new String[] {
                    "",
                    "<LEDGERBAL><BALAMT/></LEDGERBAL>",
                    "<LEDGERBAL><BALAMT>0</BALAMT></LEDGERBAL>"
                }) {
            ImportParseResult parsed =
                    parse(statement(kind, accountTag, "A", "<CURDEF>EUR</CURDEF>", "", ledger));
            if (ledger.contains(">0<")) assertThat(parsed.getLedgerBalances()).containsKey("A");
            else assertThat(parsed.getLedgerBalances()).doesNotContainKey("A");
            assertThat(parsed.getTransactions()).isEmpty();
        }
    }

    @ParameterizedTest
    @ValueSource(
            strings = {
                "<CURRENCY>USD</CURRENCY>",
                "<CURRENCY/><CURSYM>USD</CURSYM>",
                "<CURSYM>USD</CURSYM>",
                "<CURRENCY><CURRATE>1.25</CURRATE><CURSYM>USD</CURSYM></CURRENCY>"
            })
    void transactionCurrencyContainsOnlyTheCurrencyCode(String currency) throws IOException {
        assertThat(bankRow(currency).getCurrency()).isEqualTo("USD");
    }

    @ParameterizedTest
    @ValueSource(
            strings = {
                "",
                "<CURRENCY/>",
                "<CURSYM/>",
                "<CURRENCY><CURRATE>1.25</CURRATE></CURRENCY>",
                "<CURRENCY><CURRATE>1.25</CURRATE><CURSYM/></CURRENCY>"
            })
    void absentTransactionCurrencyUsesTheStatementCurrency(String fields) throws IOException {
        assertThat(bankRow(fields).getCurrency()).isEqualTo("EUR");
    }

    @ParameterizedTest
    @ValueSource(
            strings = {
                "<ORG>Example bank</ORG>",
                "<FID>123</FID><ORG>Example bank</ORG>",
                "<FID/>",
                ""
            })
    void transferReferencesUseInstitutionScopeAndCreditCardCounterparties(String institution)
            throws IOException {
        String row =
                "<STMTTRN><TRNTYPE>XFER</TRNTYPE><DTPOSTED>20250110</DTPOSTED><TRNAMT>-10</TRNAMT><CCACCTTO><ACCTID>CARD</ACCTID></CCACCTTO><SRVRTID>42</SRVRTID></STMTTRN>";
        ImportedTransaction transfer =
                parse(
                                institution
                                        + statement(
                                                "STMTRS",
                                                "BANKACCTFROM",
                                                "BANK",
                                                "<CURDEF>EUR</CURDEF>",
                                                row,
                                                ""))
                        .getTransactions()
                        .getFirst();
        assertThat(transfer.isOfxTransfer()).isTrue();
        assertThat(transfer.getOfxCounterpartyAccount()).isEqualTo("CARD");
        assertThat(transfer.getOfxTransferReference())
                .isEqualTo(
                        institution.contains(">123<")
                                ? "fid:123:42"
                                : institution.contains("<ORG>") ? "Example bank:42" : ":42");
    }

    @Test
    void aMultiStatementFileKeepsEachAccountsCurrencyAndIndependentClosingBalance()
            throws IOException {
        String row = "<STMTTRN><DTPOSTED>20250110</DTPOSTED><TRNAMT>-10</TRNAMT></STMTTRN>";
        ImportParseResult parsed =
                parse(
                        statement(
                                        "STMTRS",
                                        "BANKACCTFROM",
                                        "BANK",
                                        "<CURDEF>EUR</CURDEF>",
                                        row,
                                        "<LEDGERBAL><BALAMT>1.01</BALAMT></LEDGERBAL>")
                                + statement(
                                        "CCSTMTRS",
                                        "CCACCTFROM",
                                        "CARD",
                                        "<CURDEF>USD</CURDEF>",
                                        row,
                                        "<LEDGERBAL><BALAMT>-2.02</BALAMT></LEDGERBAL>")
                                + statement(
                                        "INVSTMTRS",
                                        "INVACCTFROM",
                                        "INVEST",
                                        "<CURDEF>GBP</CURDEF>",
                                        row,
                                        "<LEDGERBAL><BALAMT>3.03</BALAMT></LEDGERBAL>"));
        assertThat(parsed.getCurrency()).isEqualTo("EUR");
        assertThat(parsed.getTransactions())
                .extracting(ImportedTransaction::getCurrency)
                .containsExactly("EUR", "USD", "GBP");
        assertThat(parsed.getTransactions())
                .extracting(ImportedTransaction::getAccountNumber)
                .containsExactly("BANK", "CARD", "INVEST");
        assertThat(parsed.getLedgerBalances().get("BANK")).isEqualByComparingTo("1.01");
        assertThat(parsed.getLedgerBalances().get("CARD")).isEqualByComparingTo("-2.02");
        assertThat(parsed.getLedgerBalances().get("INVEST")).isEqualByComparingTo("3.03");
    }

    @Test
    void statementCurrencyAggregateAlsoKeepsOnlyTheSymbol() throws IOException {
        ImportParseResult result =
                parse(
                        statement(
                                "STMTRS",
                                "BANKACCTFROM",
                                "A",
                                "<CURRENCY><CURRATE>1.25</CURRATE><CURSYM>USD</CURSYM></CURRENCY>",
                                "<STMTTRN><DTPOSTED>20250110</DTPOSTED><TRNAMT>-1</TRNAMT></STMTTRN>",
                                ""));
        assertThat(result.getCurrency()).isEqualTo("USD");
        assertThat(result.getTransactions().getFirst().getCurrency()).isEqualTo("USD");
    }

    private ImportedTransaction bankRow(String fields) throws IOException {
        return parse(
                        statement(
                                "STMTRS",
                                "BANKACCTFROM",
                                "A",
                                "<CURDEF>EUR</CURDEF>",
                                "<STMTTRN><DTPOSTED>20250110</DTPOSTED><TRNAMT>-1</TRNAMT>"
                                        + fields
                                        + "</STMTTRN>",
                                ""))
                .getTransactions()
                .getFirst();
    }

    private String statement(
            String kind,
            String accountTag,
            String account,
            String currency,
            String rows,
            String ledger) {
        return "<"
                + kind
                + "><"
                + accountTag
                + "><ACCTID>"
                + account
                + "</ACCTID></"
                + accountTag
                + ">"
                + currency
                + "<BANKTRANLIST>"
                + rows
                + "</BANKTRANLIST>"
                + ledger
                + "</"
                + kind
                + ">";
    }

    private ImportParseResult parse(String content) throws IOException {
        String xml = "<?xml version=\"1.0\"?><OFX>" + content + "</OFX>";
        return new OfxParser()
                .parseFileToResult(
                        new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8)),
                        "boundary.ofx",
                        new ImportParseContext(Locale.ENGLISH, true));
    }
}
