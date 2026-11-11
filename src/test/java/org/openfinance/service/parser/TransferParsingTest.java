package org.openfinance.service.parser;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Locale;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.openfinance.dto.ImportedTransaction;

class TransferParsingTest {
    private final ImportParseContext context = new ImportParseContext(Locale.ENGLISH, true);

    @Test
    void buyXUsesExternalCashAndPreservesShares() throws Exception {
        List<ImportedTransaction> rows =
                qif(
                        """
                !Account
                NBrokerage
                TInvst
                ^
                !Type:Invst
                D09/28/2026
                NBuyX
                YFund
                Q2
                I50
                T100
                L[Checking]
                $100
                ^
                """);
        assertThat(rows)
                .singleElement()
                .satisfies(
                        row -> {
                            assertThat(row.hasErrors()).isFalse();
                            assertThat(row.isTransfer()).isTrue();
                            assertThat(row.getAmount()).isEqualByComparingTo("100");
                            assertThat(row.getInvestmentQuantity()).isEqualByComparingTo("2");
                            assertThat(row.isInvestmentCash()).isFalse();
                            assertThat(row.getSplits()).isEmpty();
                        });
    }

    @Test
    void ofxTransferUsesDirectionInsteadOfSignedUnits() throws Exception {
        List<ImportedTransaction> rows =
                ofx(
                        """
                <OFX><INVSTMTMSGSRSV1><INVSTMTTRNRS><INVSTMTRS>
                <CURDEF>EUR</CURDEF><INVACCTFROM><BROKERID>broker</BROKERID><ACCTID>OLD</ACCTID></INVACCTFROM>
                <INVTRANLIST><TRANSFER>
                <INVTRAN><FITID>shares-out</FITID><DTTRADE>20260928</DTTRADE></INVTRAN>
                <SECID><UNIQUEID>FUND1</UNIQUEID><UNIQUEIDTYPE>ISIN</UNIQUEIDTYPE></SECID>
                <UNITS>2</UNITS><UNITPRICE>50</UNITPRICE><TFERACTION>OUT</TFERACTION>
                <INVACCTFROM><BROKERID>broker</BROKERID><ACCTID>NEW</ACCTID></INVACCTFROM>
                </TRANSFER></INVTRANLIST></INVSTMTRS></INVSTMTTRNRS></INVSTMTMSGSRSV1></OFX>
                """);
        assertThat(rows)
                .singleElement()
                .satisfies(
                        row -> {
                            assertThat(row.getValidationErrors()).isEmpty();
                            assertThat(row.getAccountNumber()).isEqualTo("OLD");
                            assertThat(row.getInvestmentQuantity()).isEqualByComparingTo("-2");
                            assertThat(row.getAmount()).isEqualByComparingTo("-100");
                            assertThat(row.getCurrency()).isEqualTo("EUR");
                            assertThat(row.isInvestmentCash()).isFalse();
                        });
    }

    private List<ImportedTransaction> qif(String content) throws Exception {
        return new QifParser().parseFile(stream(content), "transfers.qif", context);
    }

    @ParameterizedTest
    @ValueSource(strings = {"missing-price", "missing-direction", "unsupported-action"})
    void incompleteOrUnsupportedInvestmentMovementsRemainInvalid(String variant) throws Exception {
        String content =
                """
                <OFX><INVSTMTMSGSRSV1><INVSTMTTRNRS><INVSTMTRS>
                <CURDEF>EUR</CURDEF><INVACCTFROM><ACCTID>Brokerage</ACCTID></INVACCTFROM>
                <INVTRANLIST><TRANSFER><INVTRAN><FITID>test</FITID><DTTRADE>20260928</DTTRADE></INVTRAN>
                <SECID><UNIQUEID>Fund</UNIQUEID><UNIQUEIDTYPE>ISIN</UNIQUEIDTYPE></SECID>
                <UNITS>2</UNITS><UNITPRICE>50</UNITPRICE><TFERACTION>IN</TFERACTION>
                </TRANSFER></INVTRANLIST></INVSTMTRS></INVSTMTTRNRS></INVSTMTMSGSRSV1></OFX>
                """;
        content =
                switch (variant) {
                    case "missing-price" -> content.replace("<UNITPRICE>50</UNITPRICE>", "");
                    case "missing-direction" -> content.replace("<TFERACTION>IN</TFERACTION>", "");
                    default -> content.replace("TRANSFER", "JRNLSEC");
                };
        assertThat(ofx(content))
                .singleElement()
                .satisfies(
                        row -> {
                            assertThat(row.hasErrors()).isTrue();
                            assertThat(row.getInvestmentQuantity()).isNull();
                        });
    }

    private List<ImportedTransaction> ofx(String content) throws Exception {
        return new OfxParser()
                .parseFileToResult(stream(content), "transfers.qfx", context)
                .getTransactions();
    }

    private ByteArrayInputStream stream(String content) {
        return new ByteArrayInputStream(content.getBytes(StandardCharsets.UTF_8));
    }
}
