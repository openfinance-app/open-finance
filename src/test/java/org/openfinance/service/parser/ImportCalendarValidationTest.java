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
import org.openfinance.dto.ImportedTransaction;

@DisplayName("Import dates must be real calendar dates, never silently moved to month end")
class ImportCalendarValidationTest {
    private static final ImportParseContext CONTEXT = new ImportParseContext(Locale.ENGLISH, true);

    @ParameterizedTest
    @CsvSource({
        "20230229,02/29/2023",
        "20240431,04/31/2024",
        "20240230,02/30/2024",
        "20241301,13/32/2024",
        "20240010,00/10/2024"
    })
    void rejectsInvalidCalendarDatesInOfxAndQif(String ofx, String qif) throws IOException {
        ImportedTransaction ofxRow = ofx(ofx);
        assertThat(ofxRow.getTransactionDate()).as("OFX %s", ofx).isNull();
        assertThat(ofxRow.hasErrors()).isTrue();
        ImportedTransaction qifRow = qif(qif);
        assertThat(qifRow.getTransactionDate()).as("QIF %s", qif).isNull();
        assertThat(qifRow.hasErrors()).isTrue();
    }

    @ParameterizedTest
    @CsvSource({
        "20240229,02/29/2024,2024-02-29",
        "20240131120000,01/31/2024,2024-01-31",
        "20240131120000.123,01/31'24,2024-01-31",
        "20240131120000[-5:EST],01/31'2024,2024-01-31",
        "2024-01-31,01/31/2024,2024-01-31"
    })
    void preservesLeapDaysAndSupportedDateRepresentations(
            String ofx, String qif, LocalDate expected) throws IOException {
        assertThat(ofx(ofx).getTransactionDate()).isEqualTo(expected);
        assertThat(qif(qif).getTransactionDate()).isEqualTo(expected);
    }

    private static ImportedTransaction ofx(String date) throws IOException {
        String xml =
                "<?xml version=\"1.0\"?><OFX><BANKMSGSRSV1><STMTTRNRS><STMTRS>"
                        + "<CURDEF>USD</CURDEF><BANKTRANLIST><STMTTRN><TRNTYPE>DEBIT</TRNTYPE><DTPOSTED>"
                        + date
                        + "</DTPOSTED><TRNAMT>-10</TRNAMT><FITID>one</FITID><NAME>Shop</NAME>"
                        + "</STMTTRN></BANKTRANLIST></STMTRS></STMTTRNRS></BANKMSGSRSV1></OFX>";
        return new OfxParser()
                .parseFileToResult(stream(xml), "dates.ofx", CONTEXT)
                .getTransactions()
                .getFirst();
    }

    private static ImportedTransaction qif(String date) throws IOException {
        return new QifParser()
                .parseFile(
                        stream("!Type:Bank\nD" + date + "\nT-10\nPShop\n^\n"), "dates.qif", CONTEXT)
                .getFirst();
    }

    private static ByteArrayInputStream stream(String content) {
        return new ByteArrayInputStream(content.getBytes(StandardCharsets.UTF_8));
    }
}
