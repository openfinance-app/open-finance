package org.openfinance.service.parser;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.Locale;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.openfinance.dto.ImportedTransaction;
import org.openfinance.entity.AssetType;

@DisplayName("OFX investments retain distinct cash, holdings, date and currency meanings")
class OfxInvestmentValidationTest {
    @ParameterizedTest
    @ValueSource(
            strings = {
                "<CURRENCY>USD</CURRENCY>",
                "<CURRENCY><CURRATE>1.25</CURRATE><CURSYM>USD</CURSYM></CURRENCY>"
            })
    void investmentCurrencyDoesNotConcatenateTheExchangeRate(String currency) throws IOException {
        ImportedTransaction row =
                parse(
                        "BUYSTOCK",
                        "<DTTRADE>20240110</DTTRADE><UNIQUEID>SEC1</UNIQUEID><UNITS>2</UNITS><UNITPRICE>5</UNITPRICE><TOTAL>-10</TOTAL>"
                                + currency);
        assertThat(row.getCurrency()).isEqualTo("USD");
        assertThat(row.getAmount()).isEqualByComparingTo("-10");
    }

    @ParameterizedTest
    @CsvSource({
        "BUYSTOCK,-250,5,STOCK,true",
        "BUYMF,-250,5,MUTUAL_FUND,true",
        "BUYOTHER,-250,5,OTHER,true",
        "SELLSTOCK,250,-5,STOCK,true",
        "SELLMF,250,-5,MUTUAL_FUND,true",
        "SELLOTHER,250,-5,OTHER,true",
        "REINVEST,250,5,OTHER,false"
    })
    void mapsHoldingsAndCashIndependently(
            String action, String amount, String quantity, AssetType type, boolean cash)
            throws IOException {
        ImportedTransaction row =
                parse(
                        action,
                        "<DTSETTLE>20240112</DTSETTLE><DTTRADE>20240110</DTTRADE>"
                                + "<UNIQUEID>SEC1</UNIQUEID><UNIQUEIDTYPE>ISIN</UNIQUEIDTYPE><UNITS>-5</UNITS><UNITPRICE>50</UNITPRICE><TOTAL>250</TOTAL>");
        assertThat(row.getAmount()).isEqualByComparingTo(amount);
        assertThat(row.getInvestmentQuantity()).isEqualByComparingTo(quantity);
        assertThat(row.getInvestmentType()).isEqualTo(type);
        assertThat(row.isInvestmentCash()).isEqualTo(cash);
        assertThat(row.getInvestmentSecurity()).isEqualTo("ofx:ISIN:SEC1");
        assertThat(row.getTransactionDate()).isEqualTo(LocalDate.of(2024, 1, 12));
        assertThat(row.getCategory()).isNull();
        assertThat(row.hasErrors()).isFalse();
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "<TOTAL/>", "<TOTAL>invalid</TOTAL>"})
    void derivesExactCashFromUnitsAndPriceWhenTotalIsUnavailable(String total) throws IOException {
        ImportedTransaction row =
                parse(
                        "BUYSTOCK",
                        "<DTSETTLE/><DTTRADE>20240110</DTTRADE><UNIQUEID>SEC1</UNIQUEID>"
                                + "<UNITS>3</UNITS><UNITPRICE>0.000000000000000001</UNITPRICE>"
                                + total);
        assertThat(row.getAmount()).isEqualByComparingTo("-0.000000000000000003");
        assertThat(row.getInvestmentPrice()).isEqualByComparingTo("0.000000000000000001");
        assertThat(row.getTransactionDate()).isEqualTo(LocalDate.of(2024, 1, 10));
        assertThat(row.hasErrors()).isFalse();
    }

    @ParameterizedTest
    @ValueSource(
            strings = {
                "<UNITS>2</UNITS>",
                "<UNITPRICE>5</UNITPRICE>",
                "<UNITS>bad</UNITS><UNITPRICE>5</UNITPRICE>",
                "<UNITS>2</UNITS><UNITPRICE>bad</UNITPRICE>",
                "<UNITS/><UNITPRICE/>",
                "<UNITS>0</UNITS><UNITPRICE>5</UNITPRICE>",
                "<UNITS>2</UNITS><UNITPRICE>0</UNITPRICE>"
            })
    void retainsIncompleteInvestmentsForReviewInsteadOfInventingAValidTrade(String fields)
            throws IOException {
        ImportedTransaction row =
                parse("BUYSTOCK", "<DTTRADE>20240110</DTTRADE><UNIQUEID>SEC1</UNIQUEID>" + fields);
        assertThat(row.hasErrors()).isTrue();
        assertThat(row.getValidationErrors()).isNotEmpty();
        assertThat(row.getInvestmentQuantity()).isNull();
    }

    @ParameterizedTest
    @CsvSource({"IN,5,250", "OUT,-5,-250"})
    void transfersHoldingsWithoutClaimingInvestmentCash(
            String direction, String units, String amount) throws IOException {
        ImportedTransaction row =
                parse(
                        "TRANSFER",
                        "<INVTRAN><DTTRADE>20240110</DTTRADE><FITID>TR1</FITID></INVTRAN>"
                                + "<UNIQUEID>SEC1</UNIQUEID><UNITS>5</UNITS><UNITPRICE>50</UNITPRICE><TOTAL>250</TOTAL>"
                                + "<TFERACTION>"
                                + direction
                                + "</TFERACTION><INVACCTFROM><ACCTID>OTHER</ACCTID></INVACCTFROM>");
        assertThat(row.getInvestmentQuantity()).isEqualByComparingTo(units);
        assertThat(row.getAmount()).isEqualByComparingTo(amount);
        assertThat(row.isInvestmentCash()).isFalse();
        assertThat(row.isHoldingTransfer()).isTrue();
        assertThat(row.isOfxTransfer()).isTrue();
        assertThat(row.getOfxCounterpartyAccount()).isEqualTo("OTHER");
        assertThat(row.getReferenceNumber()).isEqualTo("TR1");
    }

    @ParameterizedTest
    @ValueSource(
            strings = {
                "<DTSETTLE/>",
                "<DTSETTLE>invalid</DTSETTLE>",
                "<INVTRAN><DTTRADE/><FITID/></INVTRAN>",
                "<DTTRADE>29990101</DTTRADE>"
            })
    void missingInvalidOrFutureDatesCannotBecomeImportable(String date) throws IOException {
        ImportedTransaction row = parse("INCOME", date + "<TOTAL>10</TOTAL><MEMO>Dividend</MEMO>");
        assertThat(row.hasErrors()).isTrue();
        assertThat(row.getPayee()).isEqualTo("Dividend");
        assertThat(row.getAmount()).isEqualByComparingTo("10");
    }

    @ParameterizedTest
    @ValueSource(
            strings = {
                "<CURRENCY>EUR</CURRENCY>",
                "<CURRENCY/><CURSYM>EUR</CURSYM>",
                "<CURSYM>EUR</CURSYM>"
            })
    void retainsInvestmentCurrencyInsteadOfReplacingItWithTheStatementCurrency(String currency)
            throws IOException {
        ImportedTransaction row =
                parse(
                        "INCOME",
                        "<DTTRADE>20240110</DTTRADE><TOTAL>10</TOTAL><MEMO>Dividend</MEMO>"
                                + currency);
        assertThat(row.getCurrency()).isEqualTo("EUR");
        assertThat(row.hasErrors()).isFalse();
    }

    @ParameterizedTest
    @ValueSource(
            strings = {
                "BUYDEBT",
                "BUYOPT",
                "SELLDEBT",
                "SELLOPT",
                "JRNLFUND",
                "JRNLSEC",
                "MARGININTEREST",
                "CLOSUREOPT",
                "SPLIT",
                "RETOFCAP"
            })
    void unsupportedInvestmentActionsAreFlaggedRatherThanImportedAsOrdinaryCash(String action)
            throws IOException {
        ImportedTransaction row =
                parse(
                        action,
                        "<DTTRADE>20240110</DTTRADE><TOTAL>10</TOTAL><UNITS>2</UNITS><UNITPRICE>5</UNITPRICE><UNIQUEID>SEC1</UNIQUEID>");
        assertThat(row.hasErrors()).isTrue();
        assertThat(row.getInvestmentQuantity()).isNull();
        assertThat(row.getCategory()).isNull();
    }

    private static ImportedTransaction parse(String type, String fields) throws IOException {
        String xml =
                "<?xml version=\"1.0\"?><OFX><INVSTMTMSGSRSV1><INVSTMTTRNRS><INVSTMTRS><CURDEF>USD</CURDEF>"
                        + "<INVACCTFROM><ACCTID>INVESTMENT</ACCTID></INVACCTFROM><INVTRANLIST><"
                        + type
                        + ">"
                        + fields
                        + "</"
                        + type
                        + "></INVTRANLIST></INVSTMTRS></INVSTMTTRNRS></INVSTMTMSGSRSV1></OFX>";
        return new OfxParser()
                .parseFileToResult(
                        new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8)),
                        "investments.ofx",
                        new ImportParseContext(Locale.ENGLISH, true))
                .getTransactions()
                .getFirst();
    }
}
