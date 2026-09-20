package org.openfinance.service.parser;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class ImportAmountPrecisionTest {
    @ParameterizedTest
    @CsvSource(
            delimiter = '|',
            value = {
                "-12,3456|-12.3456", "-0,00000001|-0.00000001", "1,234|1234", "1,234,567|1234567",
                "1.234,56|1234.56", "1,234.56|1234.56", "0,001|0.001",
                        "12,345678901234567890|12.345678901234567890"
            })
    void preservesDecimalPrecisionAndValidThousandsGrouping(String raw, String expected) {
        assertThat(ImportParseSupport.parseLenientAmount(raw)).isEqualByComparingTo(expected);
    }
}
