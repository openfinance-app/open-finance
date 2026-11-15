package org.openfinance.service.parser;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.Locale;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.openfinance.dto.ImportedTransaction;

@DisplayName(
        "Investment source records require both a valid holding and an attributable cash value")
class InvestmentParseSupportTest {
    @ParameterizedTest
    @CsvSource({"div,,", "div,-5,5", "miscexp,5,-5", "xout,-5,-5", "xin,-5,5"})
    void incomeAndExpenseActionsNormalizeCashWithoutInventingAMissingAmount(
            String action, String amount, String expected) {
        ImportedTransaction.ImportedTransactionBuilder builder =
                ImportedTransaction.builder().referenceNumber(action).amount(decimal(amount));
        InvestmentParseSupport.qif(builder, null, null, Locale.ENGLISH);
        ImportedTransaction row = builder.build();
        if (expected == null) assertThat(row.getAmount()).isNull();
        else assertThat(row.getAmount()).isEqualByComparingTo(expected);
        assertThat(row.getInvestmentQuantity()).isNull();
        assertThat(row.getAuthoritativeReference()).isFalse();
    }

    @ParameterizedTest
    @CsvSource({"true,", "false,Cash account"})
    void externallyFundedTradesNeedBothTheTransferFlagAndTheCounterAccount(
            boolean transfer, String destination) {
        ImportedTransaction.ImportedTransactionBuilder builder =
                ImportedTransaction.builder()
                        .referenceNumber("buyx")
                        .investmentName("Fund")
                        .amount(BigDecimal.TEN)
                        .transfer(transfer)
                        .toAccountName(destination);
        InvestmentParseSupport.qif(builder, BigDecimal.ONE, BigDecimal.TEN, Locale.ENGLISH);
        assertThat(builder.build().getValidationErrors()).isNotEmpty();
        assertThat(builder.build().getInvestmentQuantity()).isNull();
    }

    @ParameterizedTest
    @CsvSource({
        ",10,Fund,10",
        "0,10,Fund,10",
        "1,10,,10",
        "1,10,' ',10",
        "1,,Fund,",
        "1,10,Fund,",
        "1,0,Fund,10",
        "1,-1,Fund,10",
        "1,10,Fund,0"
    })
    void incompleteTradeMetadataStaysAnErrorInsteadOfCreatingAnIncorrectPosition(
            String quantity, String price, String name, String amount) {
        ImportedTransaction.ImportedTransactionBuilder builder =
                ImportedTransaction.builder()
                        .referenceNumber("buy")
                        .investmentName(name)
                        .amount(decimal(amount));
        InvestmentParseSupport.qif(builder, decimal(quantity), decimal(price), Locale.ENGLISH);
        assertThat(builder.build().getValidationErrors()).isNotEmpty();
        assertThat(builder.build().getInvestmentQuantity()).isNull();
        assertThat(builder.build().getAmount()).isEqualTo(decimal(amount));
    }

    private BigDecimal decimal(String value) {
        return value == null ? null : new BigDecimal(value);
    }
}
