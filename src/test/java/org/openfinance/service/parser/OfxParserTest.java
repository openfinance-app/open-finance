package org.openfinance.service.parser;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

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
 * Comprehensive test suite for OFX/QFX parser. Tests both SGML (OFX 1.x) and XML (OFX 2.x) formats.
 */
class OfxParserTest {

    private OfxParser parser;

    @BeforeEach
    void setUp() {
        parser = new OfxParser();
    }

    /** Helper method to parse OFX content from a string. */
    private List<ImportedTransaction> parseOfx(String content) throws IOException {
        InputStream inputStream =
                new ByteArrayInputStream(content.getBytes(StandardCharsets.UTF_8));
        return parser.parseFileToResult(inputStream, "test.ofx").getTransactions();
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

    // ========================================
    // Basic SGML Parsing Tests
    // ========================================

    @Test
    @DisplayName("Should parse SGML format with single bank transaction")
    void testParseSGMLSingleTransaction() throws IOException {
        String ofx =
                """
                OFXHEADER:100
                DATA:OFXSGML
                VERSION:102
                SECURITY:NONE
                ENCODING:USASCII
                CHARSET:1252
                COMPRESSION:NONE
                OLDFILEUID:NONE
                NEWFILEUID:NONE

                <OFX>
                <SIGNONMSGSRSV1>
                <SONRS>
                <STATUS>
                <CODE>0
                <SEVERITY>INFO
                </STATUS>
                <DTSERVER>20240115120000
                <LANGUAGE>ENG
                </SONRS>
                </SIGNONMSGSRSV1>
                <BANKMSGSRSV1>
                <STMTTRNRS>
                <TRNUID>1
                <STATUS>
                <CODE>0
                <SEVERITY>INFO
                </STATUS>
                <STMTRS>
                <CURDEF>USD
                <BANKACCTFROM>
                <BANKID>121000248
                <ACCTID>123456789
                <ACCTTYPE>CHECKING
                </BANKACCTFROM>
                <BANKTRANLIST>
                <DTSTART>20240101120000
                <DTEND>20240131120000
                <STMTTRN>
                <TRNTYPE>DEBIT
                <DTPOSTED>20240115120000
                <TRNAMT>-45.67
                <FITID>202401150001
                <NAME>STARBUCKS
                <MEMO>Coffee purchase
                </STMTTRN>
                </BANKTRANLIST>
                <LEDGERBAL>
                <BALAMT>1234.56
                <DTASOF>20240131120000
                </LEDGERBAL>
                </STMTRS>
                </STMTTRNRS>
                </BANKMSGSRSV1>
                </OFX>
                """;

        List<ImportedTransaction> transactions = parseOfx(ofx);

        assertThat(transactions).hasSize(1);
        ImportedTransaction tx = transactions.get(0);
        assertThat(tx.getTransactionDate()).isEqualTo(LocalDate.of(2024, 1, 15));
        assertThat(tx.getAmount()).isEqualByComparingTo(new BigDecimal("-45.67"));
        assertThat(tx.getPayee()).isEqualTo("STARBUCKS - Coffee purchase");
        assertThat(tx.getMemo()).contains("DEBIT");
        assertThat(tx.getReferenceNumber()).isEqualTo("202401150001");
        assertThat(tx.hasErrors()).isFalse();
    }

    @Test
    @DisplayName("Should parse SGML format with multiple bank transactions")
    void testParseSGMLMultipleTransactions() throws IOException {
        String ofx =
                """
                OFXHEADER:100
                DATA:OFXSGML
                <OFX>
                <BANKMSGSRSV1>
                <STMTTRNRS>
                <STMTRS>
                <BANKTRANLIST>
                <STMTTRN>
                <TRNTYPE>DEBIT
                <DTPOSTED>20240115120000
                <TRNAMT>-45.67
                <FITID>202401150001
                <NAME>STARBUCKS
                <MEMO>Coffee purchase
                </STMTTRN>
                <STMTTRN>
                <TRNTYPE>CREDIT
                <DTPOSTED>20240116120000
                <TRNAMT>1500.00
                <FITID>202401160001
                <NAME>PAYROLL DEPOSIT
                <MEMO>Salary
                </STMTTRN>
                <STMTTRN>
                <TRNTYPE>DEBIT
                <DTPOSTED>20240117120000
                <TRNAMT>-120.50
                <FITID>202401170001
                <NAME>GROCERY STORE
                </STMTTRN>
                </BANKTRANLIST>
                </STMTRS>
                </STMTTRNRS>
                </BANKMSGSRSV1>
                </OFX>
                """;

        List<ImportedTransaction> transactions = parseOfx(ofx);

        assertThat(transactions).hasSize(3);

        ImportedTransaction tx1 = transactions.get(0);
        assertThat(tx1.getTransactionDate()).isEqualTo(LocalDate.of(2024, 1, 15));
        assertThat(tx1.getAmount()).isEqualByComparingTo(new BigDecimal("-45.67"));
        assertThat(tx1.getPayee()).isEqualTo("STARBUCKS - Coffee purchase");

        ImportedTransaction tx2 = transactions.get(1);
        assertThat(tx2.getTransactionDate()).isEqualTo(LocalDate.of(2024, 1, 16));
        assertThat(tx2.getAmount()).isEqualByComparingTo(new BigDecimal("1500.00"));
        assertThat(tx2.getPayee()).isEqualTo("PAYROLL DEPOSIT - Salary");

        ImportedTransaction tx3 = transactions.get(2);
        assertThat(tx3.getTransactionDate()).isEqualTo(LocalDate.of(2024, 1, 17));
        assertThat(tx3.getAmount()).isEqualByComparingTo(new BigDecimal("-120.50"));
        assertThat(tx3.getPayee()).isEqualTo("GROCERY STORE");
    }

    @Test
    @DisplayName("Should keep statement account IDs separate in multi-account OFX files")
    void testParseMultipleStatementsWithDistinctAccountIds() throws IOException {
        String ofx =
                """
                <?xml version="1.0" encoding="UTF-8"?>
                <OFX>
                    <BANKMSGSRSV1>
                        <STMTTRNRS>
                            <STMTRS>
                                <CURDEF>USD</CURDEF>
                                <BANKACCTFROM>
                                    <BANKID>111000025</BANKID>
                                    <ACCTID>CHK-001</ACCTID>
                                    <ACCTTYPE>CHECKING</ACCTTYPE>
                                </BANKACCTFROM>
                                <BANKTRANLIST>
                                    <STMTTRN>
                                        <TRNTYPE>DEBIT</TRNTYPE>
                                        <DTPOSTED>20240110</DTPOSTED>
                                        <TRNAMT>-25.00</TRNAMT>
                                        <FITID>CHK-TX-1</FITID>
                                        <NAME>Grocer</NAME>
                                    </STMTTRN>
                                </BANKTRANLIST>
                            </STMTRS>
                        </STMTTRNRS>
                        <STMTTRNRS>
                            <STMTRS>
                                <CURDEF>USD</CURDEF>
                                <BANKACCTFROM>
                                    <BANKID>111000025</BANKID>
                                    <ACCTID>SAV-002</ACCTID>
                                    <ACCTTYPE>SAVINGS</ACCTTYPE>
                                </BANKACCTFROM>
                                <BANKTRANLIST>
                                    <STMTTRN>
                                        <TRNTYPE>CREDIT</TRNTYPE>
                                        <DTPOSTED>20240111</DTPOSTED>
                                        <TRNAMT>5.25</TRNAMT>
                                        <FITID>SAV-TX-1</FITID>
                                        <NAME>Interest</NAME>
                                    </STMTTRN>
                                </BANKTRANLIST>
                            </STMTRS>
                        </STMTTRNRS>
                    </BANKMSGSRSV1>
                </OFX>
                """;

        List<ImportedTransaction> transactions = parseOfx(ofx);

        assertThat(transactions).hasSize(2);
        assertThat(transactions.get(0).getAccountName()).isEqualTo("CHK-001");
        assertThat(transactions.get(1).getAccountName()).isEqualTo("SAV-002");
        assertThat(transactions.get(0).getReferenceNumber()).isEqualTo("CHK-TX-1");
        assertThat(transactions.get(1).getReferenceNumber()).isEqualTo("SAV-TX-1");
    }

    @Test
    @DisplayName("Should parse SGML format with credit card transactions")
    void testParseSGMLCreditCardTransactions() throws IOException {
        String ofx =
                """
                OFXHEADER:100
                DATA:OFXSGML
                <OFX>
                <CREDITCARDMSGSRSV1>
                <CCSTMTTRNRS>
                <TRNUID>1
                <STATUS>
                <CODE>0
                <SEVERITY>INFO
                </STATUS>
                <CCSTMTRS>
                <CURDEF>USD
                <CCACCTFROM>
                <ACCTID>4111111111111111
                </CCACCTFROM>
                <BANKTRANLIST>
                <DTSTART>20240101120000
                <DTEND>20240131120000
                <CCSTMTTRN>
                <TRNTYPE>DEBIT
                <DTPOSTED>20240115120000
                <TRNAMT>-89.99
                <FITID>CC202401150001
                <NAME>AMAZON.COM
                <MEMO>Online purchase
                </CCSTMTTRN>
                <CCSTMTTRN>
                <TRNTYPE>CREDIT
                <DTPOSTED>20240120120000
                <TRNAMT>50.00
                <FITID>CC202401200001
                <NAME>REFUND - AMAZON.COM
                <MEMO>Return credit
                </CCSTMTTRN>
                </BANKTRANLIST>
                <LEDGERBAL>
                <BALAMT>-234.56
                <DTASOF>20240131120000
                </LEDGERBAL>
                </CCSTMTRS>
                </CCSTMTTRNRS>
                </CREDITCARDMSGSRSV1>
                </OFX>
                """;

        List<ImportedTransaction> transactions = parseOfx(ofx);

        assertThat(transactions).hasSize(2);

        ImportedTransaction tx1 = transactions.get(0);
        assertThat(tx1.getTransactionDate()).isEqualTo(LocalDate.of(2024, 1, 15));
        assertThat(tx1.getAmount()).isEqualByComparingTo(new BigDecimal("-89.99"));
        assertThat(tx1.getPayee()).isEqualTo("AMAZON.COM - Online purchase");
        assertThat(tx1.getReferenceNumber()).isEqualTo("CC202401150001");

        ImportedTransaction tx2 = transactions.get(1);
        assertThat(tx2.getTransactionDate()).isEqualTo(LocalDate.of(2024, 1, 20));
        assertThat(tx2.getAmount()).isEqualByComparingTo(new BigDecimal("50.00"));
        assertThat(tx2.getPayee()).isEqualTo("REFUND - AMAZON.COM - Return credit");
    }

    @Test
    @DisplayName("Should detect SGML format from header")
    void testDetectSGMLFormat() throws IOException {
        String ofx =
                """
                OFXHEADER:100
                DATA:OFXSGML
                <OFX>
                <BANKMSGSRSV1>
                <STMTTRNRS>
                <STMTRS>
                <BANKTRANLIST>
                <STMTTRN>
                <DTPOSTED>20240115
                <TRNAMT>-45.67
                <NAME>TEST
                </STMTTRN>
                </BANKTRANLIST>
                </STMTRS>
                </STMTTRNRS>
                </BANKMSGSRSV1>
                </OFX>
                """;

        List<ImportedTransaction> transactions = parseOfx(ofx);

        assertThat(transactions).hasSize(1);
        // Should parse successfully as SGML
        assertThat(transactions.get(0).getPayee()).isEqualTo("TEST");
    }

    @Test
    @DisplayName("Should handle SGML with CHECKNUM field")
    void testParseSGMLWithCheckNum() throws IOException {
        String ofx =
                """
                OFXHEADER:100
                DATA:OFXSGML
                <OFX>
                <BANKMSGSRSV1>
                <STMTTRNRS>
                <STMTRS>
                <BANKTRANLIST>
                <STMTTRN>
                <TRNTYPE>DEBIT
                <DTPOSTED>20240115120000
                <TRNAMT>-250.00
                <FITID>202401150001
                <CHECKNUM>1234
                <NAME>UTILITY COMPANY
                <MEMO>Electric bill
                </STMTTRN>
                </BANKTRANLIST>
                </STMTRS>
                </STMTTRNRS>
                </BANKMSGSRSV1>
                </OFX>
                """;

        List<ImportedTransaction> transactions = parseOfx(ofx);

        assertThat(transactions).hasSize(1);
        ImportedTransaction tx = transactions.get(0);
        assertThat(tx.getReferenceNumber()).isEqualTo("1234");
        assertThat(tx.getMemo()).contains("Electric bill");
    }

    // ========================================
    // Basic XML Parsing Tests
    // ========================================

    @Test
    @DisplayName("Should parse XML format (OFX 2.x)")
    void testParseXMLFormat() throws IOException {
        String ofx =
                """
                <?xml version="1.0" encoding="UTF-8"?>
                <OFX>
                <SIGNONMSGSRSV1>
                <SONRS>
                <STATUS>
                <CODE>0</CODE>
                <SEVERITY>INFO</SEVERITY>
                </STATUS>
                <DTSERVER>20240115120000</DTSERVER>
                <LANGUAGE>ENG</LANGUAGE>
                </SONRS>
                </SIGNONMSGSRSV1>
                <BANKMSGSRSV1>
                <STMTTRNRS>
                <TRNUID>1</TRNUID>
                <STATUS>
                <CODE>0</CODE>
                <SEVERITY>INFO</SEVERITY>
                </STATUS>
                <STMTRS>
                <CURDEF>USD</CURDEF>
                <BANKACCTFROM>
                <BANKID>121000248</BANKID>
                <ACCTID>123456789</ACCTID>
                <ACCTTYPE>CHECKING</ACCTTYPE>
                </BANKACCTFROM>
                <BANKTRANLIST>
                <DTSTART>20240101120000</DTSTART>
                <DTEND>20240131120000</DTEND>
                <STMTTRN>
                <TRNTYPE>DEBIT</TRNTYPE>
                <DTPOSTED>20240115120000</DTPOSTED>
                <TRNAMT>-45.67</TRNAMT>
                <FITID>202401150001</FITID>
                <NAME>STARBUCKS</NAME>
                <MEMO>Coffee purchase</MEMO>
                </STMTTRN>
                </BANKTRANLIST>
                <LEDGERBAL>
                <BALAMT>1234.56</BALAMT>
                <DTASOF>20240131120000</DTASOF>
                </LEDGERBAL>
                </STMTRS>
                </STMTTRNRS>
                </BANKMSGSRSV1>
                </OFX>
                """;

        List<ImportedTransaction> transactions = parseOfx(ofx);

        assertThat(transactions).hasSize(1);
        ImportedTransaction tx = transactions.get(0);
        assertThat(tx.getTransactionDate()).isEqualTo(LocalDate.of(2024, 1, 15));
        assertThat(tx.getAmount()).isEqualByComparingTo(new BigDecimal("-45.67"));
        assertThat(tx.getPayee()).isEqualTo("STARBUCKS - Coffee purchase");
        assertThat(tx.hasErrors()).isFalse();
    }

    @Test
    @DisplayName("Should parse XML format with namespace")
    void testParseXMLWithNamespace() throws IOException {
        String ofx =
                """
                <?xml version="1.0" encoding="UTF-8"?>
                <OFX xmlns="http://www.ofx.net/2001/ofx">
                <BANKMSGSRSV1>
                <STMTTRNRS>
                <STMTRS>
                <BANKTRANLIST>
                <STMTTRN>
                <TRNTYPE>CREDIT</TRNTYPE>
                <DTPOSTED>20240116120000</DTPOSTED>
                <TRNAMT>1500.00</TRNAMT>
                <FITID>202401160001</FITID>
                <NAME>PAYROLL DEPOSIT</NAME>
                </STMTTRN>
                </BANKTRANLIST>
                </STMTRS>
                </STMTTRNRS>
                </BANKMSGSRSV1>
                </OFX>
                """;

        List<ImportedTransaction> transactions = parseOfx(ofx);

        assertThat(transactions).hasSize(1);
        ImportedTransaction tx = transactions.get(0);
        assertThat(tx.getAmount()).isEqualByComparingTo(new BigDecimal("1500.00"));
        assertThat(tx.getPayee()).isEqualTo("PAYROLL DEPOSIT");
    }

    @Test
    @DisplayName("Should parse XML with credit card transactions")
    void testParseXMLCreditCard() throws IOException {
        String ofx =
                """
                <?xml version="1.0" encoding="UTF-8"?>
                <OFX>
                <CREDITCARDMSGSRSV1>
                <CCSTMTTRNRS>
                <CCSTMTRS>
                <BANKTRANLIST>
                <CCSTMTTRN>
                <TRNTYPE>DEBIT</TRNTYPE>
                <DTPOSTED>20240115120000</DTPOSTED>
                <TRNAMT>-89.99</TRNAMT>
                <FITID>CC202401150001</FITID>
                <NAME>AMAZON.COM</NAME>
                <MEMO>Online purchase</MEMO>
                </CCSTMTTRN>
                </BANKTRANLIST>
                </CCSTMTRS>
                </CCSTMTTRNRS>
                </CREDITCARDMSGSRSV1>
                </OFX>
                """;

        List<ImportedTransaction> transactions = parseOfx(ofx);

        assertThat(transactions).hasSize(1);
        ImportedTransaction tx = transactions.get(0);
        assertThat(tx.getAmount()).isEqualByComparingTo(new BigDecimal("-89.99"));
        assertThat(tx.getPayee()).isEqualTo("AMAZON.COM - Online purchase");
    }

    // ========================================
    // Date Format Tests
    // ========================================

    @Test
    @DisplayName("Should parse date with full timestamp (YYYYMMDDHHMMSS)")
    void testParseDateWithTimestamp() throws IOException {
        String ofx =
                """
                OFXHEADER:100
                DATA:OFXSGML
                <OFX>
                <BANKMSGSRSV1>
                <STMTTRNRS>
                <STMTRS>
                <BANKTRANLIST>
                <STMTTRN>
                <DTPOSTED>20240115143025
                <TRNAMT>-45.67
                <NAME>TEST
                </STMTTRN>
                </BANKTRANLIST>
                </STMTRS>
                </STMTTRNRS>
                </BANKMSGSRSV1>
                </OFX>
                """;

        List<ImportedTransaction> transactions = parseOfx(ofx);

        assertThat(transactions).hasSize(1);
        assertThat(transactions.get(0).getTransactionDate()).isEqualTo(LocalDate.of(2024, 1, 15));
    }

    @Test
    @DisplayName("Should parse date without timestamp (YYYYMMDD)")
    void testParseDateWithoutTimestamp() throws IOException {
        String ofx =
                """
                OFXHEADER:100
                DATA:OFXSGML
                <OFX>
                <BANKMSGSRSV1>
                <STMTTRNRS>
                <STMTRS>
                <BANKTRANLIST>
                <STMTTRN>
                <DTPOSTED>20240115
                <TRNAMT>-45.67
                <NAME>TEST
                </STMTTRN>
                </BANKTRANLIST>
                </STMTRS>
                </STMTTRNRS>
                </BANKMSGSRSV1>
                </OFX>
                """;

        List<ImportedTransaction> transactions = parseOfx(ofx);

        assertThat(transactions).hasSize(1);
        assertThat(transactions.get(0).getTransactionDate()).isEqualTo(LocalDate.of(2024, 1, 15));
    }

    @Test
    @DisplayName("Should parse date with timezone offset")
    void testParseDateWithTimezone() throws IOException {
        String ofx =
                """
                OFXHEADER:100
                DATA:OFXSGML
                <OFX>
                <BANKMSGSRSV1>
                <STMTTRNRS>
                <STMTRS>
                <BANKTRANLIST>
                <STMTTRN>
                <DTPOSTED>20240115120000[0:GMT]
                <TRNAMT>-45.67
                <NAME>TEST
                </STMTTRN>
                </BANKTRANLIST>
                </STMTRS>
                </STMTTRNRS>
                </BANKMSGSRSV1>
                </OFX>
                """;

        List<ImportedTransaction> transactions = parseOfx(ofx);

        assertThat(transactions).hasSize(1);
        assertThat(transactions.get(0).getTransactionDate()).isEqualTo(LocalDate.of(2024, 1, 15));
    }

    // ========================================
    // Field Mapping Tests
    // ========================================

    @Test
    @DisplayName("Should map DTPOSTED to transaction date")
    void testMapDtposted() throws IOException {
        String ofx =
                """
                OFXHEADER:100
                DATA:OFXSGML
                <OFX>
                <BANKMSGSRSV1>
                <STMTTRNRS>
                <STMTRS>
                <BANKTRANLIST>
                <STMTTRN>
                <DTPOSTED>20231225
                <TRNAMT>-100.00
                <NAME>TEST
                </STMTTRN>
                </BANKTRANLIST>
                </STMTRS>
                </STMTTRNRS>
                </BANKMSGSRSV1>
                </OFX>
                """;

        List<ImportedTransaction> transactions = parseOfx(ofx);

        assertThat(transactions).hasSize(1);
        assertThat(transactions.get(0).getTransactionDate()).isEqualTo(LocalDate.of(2023, 12, 25));
    }

    @Test
    @DisplayName("Should map TRNAMT to amount")
    void testMapTrnamt() throws IOException {
        String ofx =
                """
                OFXHEADER:100
                DATA:OFXSGML
                <OFX>
                <BANKMSGSRSV1>
                <STMTTRNRS>
                <STMTRS>
                <BANKTRANLIST>
                <STMTTRN>
                <DTPOSTED>20240115
                <TRNAMT>-123.45
                <NAME>TEST
                </STMTTRN>
                </BANKTRANLIST>
                </STMTRS>
                </STMTTRNRS>
                </BANKMSGSRSV1>
                </OFX>
                """;

        List<ImportedTransaction> transactions = parseOfx(ofx);

        assertThat(transactions).hasSize(1);
        assertThat(transactions.get(0).getAmount()).isEqualByComparingTo(new BigDecimal("-123.45"));
    }

    @Test
    @DisplayName("Should map NAME to payee")
    void testMapName() throws IOException {
        String ofx =
                """
                OFXHEADER:100
                DATA:OFXSGML
                <OFX>
                <BANKMSGSRSV1>
                <STMTTRNRS>
                <STMTRS>
                <BANKTRANLIST>
                <STMTTRN>
                <DTPOSTED>20240115
                <TRNAMT>-50.00
                <NAME>WHOLE FOODS MARKET
                </STMTTRN>
                </BANKTRANLIST>
                </STMTRS>
                </STMTTRNRS>
                </BANKMSGSRSV1>
                </OFX>
                """;

        List<ImportedTransaction> transactions = parseOfx(ofx);

        assertThat(transactions).hasSize(1);
        assertThat(transactions.get(0).getPayee()).isEqualTo("WHOLE FOODS MARKET");
    }

    @Test
    @DisplayName("Should map MEMO to memo")
    void testMapMemo() throws IOException {
        String ofx =
                """
                OFXHEADER:100
                DATA:OFXSGML
                <OFX>
                <BANKMSGSRSV1>
                <STMTTRNRS>
                <STMTRS>
                <BANKTRANLIST>
                <STMTTRN>
                <DTPOSTED>20240115
                <TRNAMT>-50.00
                <NAME>TEST
                <MEMO>Weekly groceries
                </STMTTRN>
                </BANKTRANLIST>
                </STMTRS>
                </STMTTRNRS>
                </BANKMSGSRSV1>
                </OFX>
                """;

        List<ImportedTransaction> transactions = parseOfx(ofx);

        assertThat(transactions).hasSize(1);
        assertThat(transactions.get(0).getMemo()).contains("Weekly groceries");
    }

    @Test
    @DisplayName("Should map FITID to reference number")
    void testMapFitid() throws IOException {
        String ofx =
                """
                OFXHEADER:100
                DATA:OFXSGML
                <OFX>
                <BANKMSGSRSV1>
                <STMTTRNRS>
                <STMTRS>
                <BANKTRANLIST>
                <STMTTRN>
                <DTPOSTED>20240115
                <TRNAMT>-50.00
                <FITID>UNIQUEID123456
                <NAME>TEST
                </STMTTRN>
                </BANKTRANLIST>
                </STMTRS>
                </STMTTRNRS>
                </BANKMSGSRSV1>
                </OFX>
                """;

        List<ImportedTransaction> transactions = parseOfx(ofx);

        assertThat(transactions).hasSize(1);
        assertThat(transactions.get(0).getReferenceNumber()).isEqualTo("UNIQUEID123456");
    }

    @Test
    @DisplayName("Should prefer CHECKNUM over FITID for reference number")
    void testPreferChecknumOverFitid() throws IOException {
        String ofx =
                """
                OFXHEADER:100
                DATA:OFXSGML
                <OFX>
                <BANKMSGSRSV1>
                <STMTTRNRS>
                <STMTRS>
                <BANKTRANLIST>
                <STMTTRN>
                <DTPOSTED>20240115
                <TRNAMT>-50.00
                <FITID>FITID123
                <CHECKNUM>5678
                <NAME>TEST
                </STMTTRN>
                </BANKTRANLIST>
                </STMTRS>
                </STMTTRNRS>
                </BANKMSGSRSV1>
                </OFX>
                """;

        List<ImportedTransaction> transactions = parseOfx(ofx);

        assertThat(transactions).hasSize(1);
        assertThat(transactions.get(0).getReferenceNumber()).isEqualTo("5678");
    }

    // ========================================
    // Transaction Type Tests
    // ========================================

    @Test
    @DisplayName("Should handle all transaction types")
    void testHandleAllTransactionTypes() throws IOException {
        String ofx =
                """
                OFXHEADER:100
                DATA:OFXSGML
                <OFX>
                <BANKMSGSRSV1>
                <STMTTRNRS>
                <STMTRS>
                <BANKTRANLIST>
                <STMTTRN>
                <TRNTYPE>DEBIT
                <DTPOSTED>20240115
                <TRNAMT>-50.00
                <NAME>DEBIT TEST
                </STMTTRN>
                <STMTTRN>
                <TRNTYPE>CREDIT
                <DTPOSTED>20240115
                <TRNAMT>100.00
                <NAME>CREDIT TEST
                </STMTTRN>
                <STMTTRN>
                <TRNTYPE>INT
                <DTPOSTED>20240115
                <TRNAMT>5.25
                <NAME>INTEREST TEST
                </STMTTRN>
                <STMTTRN>
                <TRNTYPE>DIV
                <DTPOSTED>20240115
                <TRNAMT>12.50
                <NAME>DIVIDEND TEST
                </STMTTRN>
                <STMTTRN>
                <TRNTYPE>FEE
                <DTPOSTED>20240115
                <TRNAMT>-2.50
                <NAME>FEE TEST
                </STMTTRN>
                </BANKTRANLIST>
                </STMTRS>
                </STMTTRNRS>
                </BANKMSGSRSV1>
                </OFX>
                """;

        List<ImportedTransaction> transactions = parseOfx(ofx);

        assertThat(transactions).hasSize(5);
        assertThat(transactions.get(0).getMemo()).contains("DEBIT");
        assertThat(transactions.get(1).getMemo()).contains("CREDIT");
        assertThat(transactions.get(2).getMemo()).contains("INT");
        assertThat(transactions.get(3).getMemo()).contains("DIV");
        assertThat(transactions.get(4).getMemo()).contains("FEE");
    }

    @Test
    @DisplayName("Should append transaction type to memo")
    void testAppendTransactionTypeToMemo() throws IOException {
        String ofx =
                """
                OFXHEADER:100
                DATA:OFXSGML
                <OFX>
                <BANKMSGSRSV1>
                <STMTTRNRS>
                <STMTRS>
                <BANKTRANLIST>
                <STMTTRN>
                <TRNTYPE>DEBIT
                <DTPOSTED>20240115
                <TRNAMT>-50.00
                <NAME>TEST
                <MEMO>Purchase
                </STMTTRN>
                </BANKTRANLIST>
                </STMTRS>
                </STMTTRNRS>
                </BANKMSGSRSV1>
                </OFX>
                """;

        List<ImportedTransaction> transactions = parseOfx(ofx);

        assertThat(transactions).hasSize(1);
        String memo = transactions.get(0).getMemo();
        assertThat(memo).contains("Purchase");
        assertThat(memo).contains("DEBIT");
    }

    // ========================================
    // Edge Cases and Error Handling
    // ========================================

    @Test
    @DisplayName("Should add validation error for missing date")
    void testMissingDate() throws IOException {
        String ofx =
                """
                OFXHEADER:100
                DATA:OFXSGML
                <OFX>
                <BANKMSGSRSV1>
                <STMTTRNRS>
                <STMTRS>
                <BANKTRANLIST>
                <STMTTRN>
                <TRNAMT>-50.00
                <NAME>TEST
                </STMTTRN>
                </BANKTRANLIST>
                </STMTRS>
                </STMTTRNRS>
                </BANKMSGSRSV1>
                </OFX>
                """;

        List<ImportedTransaction> transactions = parseOfx(ofx);

        assertThat(transactions).hasSize(1);
        ImportedTransaction tx = transactions.get(0);
        assertThat(tx.hasErrors()).isTrue();
        assertThat(tx.getValidationErrors()).contains("Transaction date is required");
    }

    @Test
    @DisplayName("Should add validation error for missing amount")
    void testMissingAmount() throws IOException {
        String ofx =
                """
                OFXHEADER:100
                DATA:OFXSGML
                <OFX>
                <BANKMSGSRSV1>
                <STMTTRNRS>
                <STMTRS>
                <BANKTRANLIST>
                <STMTTRN>
                <DTPOSTED>20240115
                <NAME>TEST
                </STMTTRN>
                </BANKTRANLIST>
                </STMTRS>
                </STMTTRNRS>
                </BANKMSGSRSV1>
                </OFX>
                """;

        List<ImportedTransaction> transactions = parseOfx(ofx);

        assertThat(transactions).hasSize(1);
        ImportedTransaction tx = transactions.get(0);
        assertThat(tx.hasErrors()).isTrue();
        assertThat(tx.getValidationErrors()).contains("Transaction amount is required");
    }

    @Test
    @DisplayName("Should add validation error for invalid date format")
    void testInvalidDateFormat() throws IOException {
        String ofx =
                """
                OFXHEADER:100
                DATA:OFXSGML
                <OFX>
                <BANKMSGSRSV1>
                <STMTTRNRS>
                <STMTRS>
                <BANKTRANLIST>
                <STMTTRN>
                <DTPOSTED>INVALID_DATE
                <TRNAMT>-50.00
                <NAME>TEST
                </STMTTRN>
                </BANKTRANLIST>
                </STMTRS>
                </STMTTRNRS>
                </BANKMSGSRSV1>
                </OFX>
                """;

        List<ImportedTransaction> transactions = parseOfx(ofx);

        assertThat(transactions).hasSize(1);
        ImportedTransaction tx = transactions.get(0);
        assertThat(tx.hasErrors()).isTrue();
        assertThat(tx.getValidationErrors())
                .anyMatch(
                        error ->
                                error.contains("Invalid date format")
                                        || error.contains("date is required"));
    }

    @Test
    @DisplayName("Should add validation error for invalid amount format")
    void testInvalidAmountFormat() throws IOException {
        String ofx =
                """
                OFXHEADER:100
                DATA:OFXSGML
                <OFX>
                <BANKMSGSRSV1>
                <STMTTRNRS>
                <STMTRS>
                <BANKTRANLIST>
                <STMTTRN>
                <DTPOSTED>20240115
                <TRNAMT>NOT_A_NUMBER
                <NAME>TEST
                </STMTTRN>
                </BANKTRANLIST>
                </STMTRS>
                </STMTTRNRS>
                </BANKMSGSRSV1>
                </OFX>
                """;

        List<ImportedTransaction> transactions = parseOfx(ofx);

        assertThat(transactions).hasSize(1);
        ImportedTransaction tx = transactions.get(0);
        assertThat(tx.hasErrors()).isTrue();
        assertThat(tx.getValidationErrors())
                .anyMatch(
                        error ->
                                error.contains("Invalid amount format")
                                        || error.contains("amount is required"));
    }

    @Test
    @DisplayName("Should add validation error for zero amount")
    void testZeroAmount() throws IOException {
        String ofx =
                """
                OFXHEADER:100
                DATA:OFXSGML
                <OFX>
                <BANKMSGSRSV1>
                <STMTTRNRS>
                <STMTRS>
                <BANKTRANLIST>
                <STMTTRN>
                <DTPOSTED>20240115
                <TRNAMT>0.00
                <NAME>TEST
                </STMTTRN>
                </BANKTRANLIST>
                </STMTRS>
                </STMTTRNRS>
                </BANKMSGSRSV1>
                </OFX>
                """;

        List<ImportedTransaction> transactions = parseOfx(ofx);

        assertThat(transactions).hasSize(1);
        ImportedTransaction tx = transactions.get(0);
        assertThat(tx.hasErrors()).isTrue();
        assertThat(tx.getValidationErrors()).contains("Transaction amount cannot be zero");
    }

    @Test
    @DisplayName("Should add validation error for future dates")
    void testFutureDates() throws IOException {
        LocalDate futureDate = LocalDate.now().plusDays(10);
        String futureDateStr =
                futureDate.format(java.time.format.DateTimeFormatter.ofPattern("yyyyMMdd"));

        String ofx =
                String.format(
                        """
                OFXHEADER:100
                DATA:OFXSGML
                <OFX>
                <BANKMSGSRSV1>
                <STMTTRNRS>
                <STMTRS>
                <BANKTRANLIST>
                <STMTTRN>
                <DTPOSTED>%s
                <TRNAMT>-50.00
                <NAME>TEST
                </STMTTRN>
                </BANKTRANLIST>
                </STMTRS>
                </STMTTRNRS>
                </BANKMSGSRSV1>
                </OFX>
                """,
                        futureDateStr);

        List<ImportedTransaction> transactions = parseOfx(ofx);

        assertThat(transactions).hasSize(1);
        ImportedTransaction tx = transactions.get(0);
        assertThat(tx.hasErrors()).isTrue();
        assertThat(tx.getValidationErrors()).contains("Transaction date cannot be in the future");
    }

    @Test
    @DisplayName("Should return empty list for empty file")
    void testEmptyFile() throws IOException {
        String ofx =
                """
                OFXHEADER:100
                DATA:OFXSGML
                <OFX>
                <BANKMSGSRSV1>
                <STMTTRNRS>
                <STMTRS>
                <BANKTRANLIST>
                </BANKTRANLIST>
                </STMTRS>
                </STMTTRNRS>
                </BANKMSGSRSV1>
                </OFX>
                """;

        List<ImportedTransaction> transactions = parseOfx(ofx);

        assertThat(transactions).isEmpty();
    }

    @Test
    @DisplayName("Should handle malformed XML gracefully by throwing IOException")
    void testMalformedXML() {
        String ofx =
                """
                <?xml version="1.0" encoding="UTF-8"?>
                <OFX>
                <BANKMSGSRSV1>
                <STMTTRNRS>
                <STMTRS>
                <BANKTRANLIST>
                <STMTTRN>
                <DTPOSTED>20240115
                <TRNAMT>-50.00
                <NAME>TEST
                <!-- Missing closing tag for STMTTRN -->
                </BANKTRANLIST>
                </STMTRS>
                </STMTTRNRS>
                </BANKMSGSRSV1>
                </OFX>
                """;

        // Should throw IOException for malformed XML
        assertThatThrownBy(() -> parseOfx(ofx))
                .isInstanceOf(IOException.class)
                .hasMessageContaining("Failed to parse OFX file");
    }

    @Test
    @DisplayName("Should parse multi-account OFX sample fixture")
    void testParseMultiAccountOfxSampleFixture() throws IOException {
        List<ImportedTransaction> transactions = parseOfx(readFixture("samples/multi_account.ofx"));

        assertThat(transactions).hasSize(3);
        assertThat(transactions.get(0).getAccountName()).isEqualTo("CHK-001");
        assertThat(transactions.get(1).getAccountName()).isEqualTo("CHK-001");
        assertThat(transactions.get(2).getAccountName()).isEqualTo("SAV-002");
        transactions.forEach(tx -> assertThat(tx.hasErrors()).isFalse());
    }

    // ========================================
    // Real-World Scenario Tests
    // ========================================

    @Test
    @DisplayName("Should parse realistic bank OFX export (SGML)")
    void testRealisticBankExportSGML() throws IOException {
        String ofx =
                """
                OFXHEADER:100
                DATA:OFXSGML
                VERSION:102
                SECURITY:NONE
                ENCODING:USASCII
                CHARSET:1252
                COMPRESSION:NONE
                OLDFILEUID:NONE
                NEWFILEUID:NONE

                <OFX>
                <SIGNONMSGSRSV1>
                <SONRS>
                <STATUS>
                <CODE>0
                <SEVERITY>INFO
                </STATUS>
                <DTSERVER>20240131235959
                <LANGUAGE>ENG
                </SONRS>
                </SIGNONMSGSRSV1>
                <BANKMSGSRSV1>
                <STMTTRNRS>
                <TRNUID>20240131-001
                <STATUS>
                <CODE>0
                <SEVERITY>INFO
                </STATUS>
                <STMTRS>
                <CURDEF>USD
                <BANKACCTFROM>
                <BANKID>121000248
                <ACCTID>987654321
                <ACCTTYPE>CHECKING
                </BANKACCTFROM>
                <BANKTRANLIST>
                <DTSTART>20240101000000
                <DTEND>20240131235959
                <STMTTRN>
                <TRNTYPE>CREDIT
                <DTPOSTED>20240101120000
                <TRNAMT>2500.00
                <FITID>20240101001
                <NAME>PAYROLL DEPOSIT
                <MEMO>Direct Deposit Salary
                </STMTTRN>
                <STMTTRN>
                <TRNTYPE>DEBIT
                <DTPOSTED>20240105143000
                <TRNAMT>-1200.00
                <FITID>20240105001
                <CHECKNUM>2456
                <NAME>LANDLORD INC
                <MEMO>Rent January
                </STMTTRN>
                <STMTTRN>
                <TRNTYPE>DEBIT
                <DTPOSTED>20240107091500
                <TRNAMT>-85.43
                <FITID>20240107001
                <NAME>SAFEWAY STORE #1234
                <MEMO>GROCERIES
                </STMTTRN>
                <STMTTRN>
                <TRNTYPE>DEBIT
                <DTPOSTED>20240110170000
                <TRNAMT>-45.00
                <FITID>20240110001
                <NAME>SHELL OIL
                <MEMO>GAS PURCHASE
                </STMTTRN>
                <STMTTRN>
                <TRNTYPE>FEE
                <DTPOSTED>20240115120000
                <TRNAMT>-12.00
                <FITID>20240115001
                <NAME>MONTHLY SERVICE FEE
                <MEMO>Account Maintenance
                </STMTTRN>
                <STMTTRN>
                <TRNTYPE>CREDIT
                <DTPOSTED>20240115120000
                <TRNAMT>2500.00
                <FITID>20240115002
                <NAME>PAYROLL DEPOSIT
                <MEMO>Direct Deposit Salary
                </STMTTRN>
                <STMTTRN>
                <TRNTYPE>INT
                <DTPOSTED>20240131235959
                <TRNAMT>3.25
                <FITID>20240131001
                <NAME>INTEREST PAID
                <MEMO>Monthly Interest
                </STMTTRN>
                </BANKTRANLIST>
                <LEDGERBAL>
                <BALAMT>3660.82
                <DTASOF>20240131235959
                </LEDGERBAL>
                <AVAILBAL>
                <BALAMT>3660.82
                <DTASOF>20240131235959
                </AVAILBAL>
                </STMTRS>
                </STMTTRNRS>
                </BANKMSGSRSV1>
                </OFX>
                """;

        List<ImportedTransaction> transactions = parseOfx(ofx);

        assertThat(transactions).hasSize(7);

        // Verify first transaction (salary)
        ImportedTransaction tx1 = transactions.get(0);
        assertThat(tx1.getTransactionDate()).isEqualTo(LocalDate.of(2024, 1, 1));
        assertThat(tx1.getAmount()).isEqualByComparingTo(new BigDecimal("2500.00"));
        assertThat(tx1.getPayee()).isEqualTo("PAYROLL DEPOSIT - Direct Deposit Salary");
        assertThat(tx1.hasErrors()).isFalse();

        // Verify check transaction
        ImportedTransaction tx2 = transactions.get(1);
        assertThat(tx2.getReferenceNumber()).isEqualTo("2456");
        assertThat(tx2.getPayee()).isEqualTo("LANDLORD INC - Rent January");
        assertThat(tx2.getAmount()).isEqualByComparingTo(new BigDecimal("-1200.00"));

        // Verify grocery transaction
        ImportedTransaction tx3 = transactions.get(2);
        assertThat(tx3.getPayee()).isEqualTo("SAFEWAY STORE #1234 - GROCERIES");
        assertThat(tx3.getAmount()).isEqualByComparingTo(new BigDecimal("-85.43"));

        // Verify all transactions are valid
        assertThat(transactions).allMatch(tx -> !tx.hasErrors());
    }

    @Test
    @DisplayName("Should parse realistic credit card OFX export (XML)")
    void testRealisticCreditCardExportXML() throws IOException {
        String ofx =
                """
                <?xml version="1.0" encoding="UTF-8"?>
                <OFX>
                <SIGNONMSGSRSV1>
                <SONRS>
                <STATUS>
                <CODE>0</CODE>
                <SEVERITY>INFO</SEVERITY>
                </STATUS>
                <DTSERVER>20240131235959</DTSERVER>
                <LANGUAGE>ENG</LANGUAGE>
                </SONRS>
                </SIGNONMSGSRSV1>
                <CREDITCARDMSGSRSV1>
                <CCSTMTTRNRS>
                <TRNUID>CC-20240131-001</TRNUID>
                <STATUS>
                <CODE>0</CODE>
                <SEVERITY>INFO</SEVERITY>
                </STATUS>
                <CCSTMTRS>
                <CURDEF>USD</CURDEF>
                <CCACCTFROM>
                <ACCTID>4111111111111111</ACCTID>
                </CCACCTFROM>
                <BANKTRANLIST>
                <DTSTART>20240101000000</DTSTART>
                <DTEND>20240131235959</DTEND>
                <CCSTMTTRN>
                <TRNTYPE>DEBIT</TRNTYPE>
                <DTPOSTED>20240103120000</DTPOSTED>
                <TRNAMT>-89.99</TRNAMT>
                <FITID>CC20240103001</FITID>
                <NAME>AMAZON.COM</NAME>
                <MEMO>AMZN Marketplace Purchase</MEMO>
                </CCSTMTTRN>
                <CCSTMTTRN>
                <TRNTYPE>DEBIT</TRNTYPE>
                <DTPOSTED>20240107183000</DTPOSTED>
                <TRNAMT>-45.67</TRNAMT>
                <FITID>CC20240107001</FITID>
                <NAME>STARBUCKS #5678</NAME>
                <MEMO>COFFEE PURCHASE</MEMO>
                </CCSTMTTRN>
                <CCSTMTTRN>
                <TRNTYPE>DEBIT</TRNTYPE>
                <DTPOSTED>20240112140000</DTPOSTED>
                <TRNAMT>-125.00</TRNAMT>
                <FITID>CC20240112001</FITID>
                <NAME>WHOLE FOODS MARKET</NAME>
                <MEMO>GROCERY PURCHASE</MEMO>
                </CCSTMTTRN>
                <CCSTMTTRN>
                <TRNTYPE>CREDIT</TRNTYPE>
                <DTPOSTED>20240115120000</DTPOSTED>
                <TRNAMT>89.99</TRNAMT>
                <FITID>CC20240115001</FITID>
                <NAME>AMAZON.COM</NAME>
                <MEMO>RETURN CREDIT</MEMO>
                </CCSTMTTRN>
                <CCSTMTTRN>
                <TRNTYPE>DEBIT</TRNTYPE>
                <DTPOSTED>20240120190000</DTPOSTED>
                <TRNAMT>-55.00</TRNAMT>
                <FITID>CC20240120001</FITID>
                <NAME>RESTAURANT XYZ</NAME>
                <MEMO>DINING</MEMO>
                </CCSTMTTRN>
                </BANKTRANLIST>
                <LEDGERBAL>
                <BALAMT>-135.68</BALAMT>
                <DTASOF>20240131235959</DTASOF>
                </LEDGERBAL>
                </CCSTMTRS>
                </CCSTMTTRNRS>
                </CREDITCARDMSGSRSV1>
                </OFX>
                """;

        List<ImportedTransaction> transactions = parseOfx(ofx);

        assertThat(transactions).hasSize(5);

        // Verify first purchase
        ImportedTransaction tx1 = transactions.get(0);
        assertThat(tx1.getTransactionDate()).isEqualTo(LocalDate.of(2024, 1, 3));
        assertThat(tx1.getAmount()).isEqualByComparingTo(new BigDecimal("-89.99"));
        assertThat(tx1.getPayee()).isEqualTo("AMAZON.COM - AMZN Marketplace Purchase");
        assertThat(tx1.hasErrors()).isFalse();

        // Verify return credit
        ImportedTransaction tx4 = transactions.get(3);
        assertThat(tx4.getAmount()).isEqualByComparingTo(new BigDecimal("89.99"));
        assertThat(tx4.getPayee()).isEqualTo("AMAZON.COM - RETURN CREDIT");

        // Verify all transactions are valid
        assertThat(transactions).allMatch(tx -> !tx.hasErrors());
    }

    // ========================================
    // New Feature Tests — DTUSER, REFNUM, CURRENCY, Investment, Charset
    // ========================================

    @Test
    @DisplayName("Should use DTUSER as fallback date when DTPOSTED is absent")
    void testDtuserFallbackWhenDtpostedAbsent() throws IOException {
        String ofx =
                """
                OFXHEADER:100
                DATA:OFXSGML
                <OFX>
                <BANKMSGSRSV1>
                <STMTTRNRS>
                <STMTRS>
                <BANKTRANLIST>
                <STMTTRN>
                <TRNTYPE>DEBIT
                <DTUSER>20240320120000
                <TRNAMT>-75.00
                <FITID>DTU001
                <NAME>DTUSER TEST
                </STMTTRN>
                </BANKTRANLIST>
                </STMTRS>
                </STMTTRNRS>
                </BANKMSGSRSV1>
                </OFX>
                """;

        List<ImportedTransaction> transactions = parseOfx(ofx);

        assertThat(transactions).hasSize(1);
        assertThat(transactions.get(0).getTransactionDate()).isEqualTo(LocalDate.of(2024, 3, 20));
        assertThat(transactions.get(0).hasErrors()).isFalse();
    }

    @Test
    @DisplayName("Should use REFNUM as reference number when CHECKNUM is absent")
    void testRefnumUsedWhenChecknumAbsent() throws IOException {
        String ofx =
                """
                OFXHEADER:100
                DATA:OFXSGML
                <OFX>
                <BANKMSGSRSV1>
                <STMTTRNRS>
                <STMTRS>
                <BANKTRANLIST>
                <STMTTRN>
                <TRNTYPE>DEBIT
                <DTPOSTED>20240115
                <TRNAMT>-50.00
                <FITID>FITID999
                <REFNUM>REF-12345
                <NAME>REFNUM TEST
                </STMTTRN>
                </BANKTRANLIST>
                </STMTRS>
                </STMTTRNRS>
                </BANKMSGSRSV1>
                </OFX>
                """;

        List<ImportedTransaction> transactions = parseOfx(ofx);

        assertThat(transactions).hasSize(1);
        assertThat(transactions.get(0).getReferenceNumber()).isEqualTo("REF-12345");
    }

    @Test
    @DisplayName("Should parse CURRENCY field and store on transaction")
    void testCurrencyFieldParsed() throws IOException {
        String ofx =
                """
                OFXHEADER:100
                DATA:OFXSGML
                <OFX>
                <BANKMSGSRSV1>
                <STMTTRNRS>
                <STMTRS>
                <BANKTRANLIST>
                <STMTTRN>
                <TRNTYPE>DEBIT
                <DTPOSTED>20240115
                <TRNAMT>-88.00
                <FITID>CUR001
                <NAME>CURRENCY TEST
                <CURRENCY>EUR
                </STMTTRN>
                </BANKTRANLIST>
                </STMTRS>
                </STMTTRNRS>
                </BANKMSGSRSV1>
                </OFX>
                """;

        List<ImportedTransaction> transactions = parseOfx(ofx);

        assertThat(transactions).hasSize(1);
        assertThat(transactions.get(0).getCurrency()).isEqualTo("EUR");
    }

    @Test
    @DisplayName("Should parse investment BUYMF transaction from INVSTMTTRNRS")
    void testInvestmentBuymfTransaction() throws IOException {
        String ofx =
                """
                OFXHEADER:100
                DATA:OFXSGML
                <OFX>
                <INVSTMTMSGSRSV1>
                <INVSTMTTRNRS>
                <INVSTMTRS>
                <INVTRANLIST>
                <BUYMF>
                <INVBUY>
                <INVTRAN>
                <FITID>INV001
                <DTTRADE>20240310120000
                </INVTRAN>
                <SECID>
                <UNIQUEID>FUND123
                </SECID>
                <UNITS>100
                <UNITPRICE>50.00
                <TOTAL>-5000.00
                </INVBUY>
                </BUYMF>
                </INVTRANLIST>
                </INVSTMTRS>
                </INVSTMTTRNRS>
                </INVSTMTMSGSRSV1>
                </OFX>
                """;

        List<ImportedTransaction> transactions = parseOfx(ofx);

        assertThat(transactions).hasSize(1);
        ImportedTransaction tx = transactions.get(0);
        assertThat(tx.getCategory()).isEqualTo("[BUYMF]");
        assertThat(tx.getAmount()).isEqualByComparingTo(new BigDecimal("-5000.00"));
    }

    @Test
    @DisplayName("Should detect and parse CHARSET:1252 header gracefully (ASCII-safe content)")
    void testCharsetDeclaration1252() throws IOException {
        // Use ASCII-safe content to avoid actual encoding issues in the test string
        String ofx =
                """
                OFXHEADER:100
                DATA:OFXSGML
                VERSION:102
                CHARSET:1252

                <OFX>
                <BANKMSGSRSV1>
                <STMTTRNRS>
                <STMTRS>
                <BANKTRANLIST>
                <STMTTRN>
                <TRNTYPE>DEBIT
                <DTPOSTED>20240115
                <TRNAMT>-30.00
                <FITID>CS001
                <NAME>CHARSET TEST
                </STMTTRN>
                </BANKTRANLIST>
                </STMTRS>
                </STMTTRNRS>
                </BANKMSGSRSV1>
                </OFX>
                """;

        List<ImportedTransaction> transactions = parseOfx(ofx);

        assertThat(transactions).hasSize(1);
        assertThat(transactions.get(0).getPayee()).isEqualTo("CHARSET TEST");
        assertThat(transactions.get(0).hasErrors()).isFalse();
    }

    @Test
    @DisplayName(
            "Should fall back to XML parse when SGML header present but no bare <OFX> tag found")
    void testSgmlFallbackToXmlWhenNoOfxTag() throws IOException {
        // Has OFXHEADER (triggers SGML detection) but also an <?xml> declaration,
        // meaning the body is OFX 2.x XML — the parser should route to the XML path and
        // succeed.
        String ofx =
                """
                OFXHEADER:100
                DATA:OFXSGML
                <?xml version="1.0" encoding="UTF-8"?>
                <OFX>
                <BANKMSGSRSV1>
                <STMTTRNRS>
                <STMTRS>
                <BANKTRANLIST>
                <STMTTRN>
                <TRNTYPE>CREDIT</TRNTYPE>
                <DTPOSTED>20240220120000</DTPOSTED>
                <TRNAMT>200.00</TRNAMT>
                <FITID>FALLBACK001</FITID>
                <NAME>FALLBACK TEST</NAME>
                </STMTTRN>
                </BANKTRANLIST>
                </STMTRS>
                </STMTTRNRS>
                </BANKMSGSRSV1>
                </OFX>
                """;

        List<ImportedTransaction> transactions = parseOfx(ofx);

        assertThat(transactions).hasSize(1);
        assertThat(transactions.get(0).getPayee()).isEqualTo("FALLBACK TEST");
        assertThat(transactions.get(0).getAmount()).isEqualByComparingTo(new BigDecimal("200.00"));
    }
}
