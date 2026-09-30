package org.openfinance.validation;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.openfinance.dto.ImportedTransaction;
import org.openfinance.dto.TransactionRequest;
import org.openfinance.dto.TransactionSplitRequest;
import org.openfinance.dto.TransferUpdateRequest;
import org.openfinance.entity.Transaction;
import org.openfinance.entity.TransactionSplit;

class TransactionAmountValidationTest {
    private static final ValidatorFactory FACTORY = Validation.buildDefaultValidatorFactory();
    private static final Validator VALIDATOR = FACTORY.getValidator();

    @Test
    void importReviewRoundTripUsesDecimalStringsForFinancialFields() throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        BigDecimal amount = new BigDecimal("0.123456789012345678");
        ImportedTransaction transaction =
                ImportedTransaction.builder()
                        .amount(amount)
                        .sourceAccountBalanceDelta(amount)
                        .splits(
                                List.of(
                                        ImportedTransaction.SplitEntry.builder()
                                                .amount(amount)
                                                .build()))
                        .build();

        String json = mapper.writeValueAsString(transaction);
        JsonNode payload = mapper.readTree(json);
        assertThat(payload.get("amount").isTextual()).isTrue();
        assertThat(payload.get("amount").asText()).isEqualTo("0.123456789012345678");
        assertThat(payload.get("sourceAccountBalanceDelta").isTextual()).isTrue();
        assertThat(payload.get("splits").get(0).get("amount").isTextual()).isTrue();
        assertThat(mapper.readValue(json, ImportedTransaction.class).getAmount())
                .isEqualByComparingTo(amount);
    }

    @AfterAll
    static void closeValidator() {
        FACTORY.close();
    }

    @ParameterizedTest
    @ValueSource(strings = {"0.12345678", "0.00001234", "0.00000001", "0.000000000000000001"})
    void acceptsPositiveCryptoAmountsAtEveryTransactionBoundary(String value) {
        for (Class<?> type : amountTypes()) {
            assertThat(VALIDATOR.validateValue(type, "amount", new BigDecimal(value)))
                    .as("%s amount %s", type.getSimpleName(), value)
                    .isEmpty();
        }
    }

    @ParameterizedTest
    @ValueSource(
            strings = {"0", "-0.00000001", "0.0000000000000000001", "100000000000000000000000000"})
    void rejectsNonPositiveAndUnrepresentableAmounts(String value) {
        for (Class<?> type : amountTypes()) {
            assertThat(VALIDATOR.validateValue(type, "amount", new BigDecimal(value)))
                    .as("%s amount %s", type.getSimpleName(), value)
                    .isNotEmpty();
        }
    }

    private List<Class<?>> amountTypes() {
        return List.of(
                Transaction.class,
                TransactionSplit.class,
                TransactionRequest.class,
                TransactionSplitRequest.class,
                TransferUpdateRequest.class);
    }
}
