package org.openfinance.config;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.openfinance.dto.AccountResponse;
import org.openfinance.dto.AssetResponse;
import org.openfinance.dto.LiabilityResponse;
import org.openfinance.dto.LiabilityTrancheResponse;
import org.openfinance.dto.RealEstatePropertyResponse;
import org.openfinance.dto.RecurringTransactionResponse;
import org.openfinance.dto.TransactionResponse;
import org.openfinance.dto.TransactionSplitResponse;
import org.springframework.beans.BeanWrapperImpl;

class ExactDecimalJsonConfigTest {
    private final ObjectMapper mapper =
            new ObjectMapper().registerModule(ExactDecimalJsonConfig.module());

    @Test
    void keepsExactEditableAmountsAlongsideBackwardCompatibleNumericFields() throws Exception {
        BigDecimal value = new BigDecimal("10006858.9112567483");
        for (Object response :
                List.of(
                        new AccountResponse(),
                        new AssetResponse(),
                        new LiabilityResponse(),
                        new LiabilityTrancheResponse(),
                        new RealEstatePropertyResponse(),
                        new RecurringTransactionResponse(),
                        new TransactionResponse())) {
            BeanWrapperImpl bean = new BeanWrapperImpl(response);
            for (java.beans.PropertyDescriptor field : bean.getPropertyDescriptors()) {
                if (field.getPropertyType() == BigDecimal.class && field.getWriteMethod() != null) {
                    bean.setPropertyValue(field.getName(), value);
                }
            }
            JsonNode json = mapper.readTree(mapper.writeValueAsBytes(response));
            assertThat(json.get("exactValues").size()).isPositive();
            json.get("exactValues")
                    .fields()
                    .forEachRemaining(
                            field -> {
                                assertThat(field.getValue().asText())
                                        .isEqualTo(value.toPlainString());
                                assertThat(json.get(field.getKey()).isNumber()).isTrue();
                            });
        }
    }

    @Test
    void preservesNestedSplitsAndPlainNotationForTinyAndLargeDecimals() throws Exception {
        TransactionResponse transaction = new TransactionResponse();
        transaction.setAmount(new BigDecimal("99999999999999999999999999.123456789012345678"));
        TransactionSplitResponse split = new TransactionSplitResponse();
        split.setAmount(new BigDecimal("0.000000000000000001"));
        transaction.setSplits(List.of(split));
        JsonNode json = mapper.readTree(mapper.writeValueAsBytes(transaction));
        assertThat(json.at("/exactValues/amount").asText())
                .isEqualTo(transaction.getAmount().toPlainString());
        assertThat(json.at("/splits/0/exactValues/amount").asText())
                .isEqualTo("0.000000000000000001");
        assertThat(json.at("/exactValues/originalAmount").isMissingNode()).isTrue();
    }

    @Test
    void doesNotChangeThePersistenceMapper() throws Exception {
        AccountResponse response = new AccountResponse();
        response.setBalance(new BigDecimal("1.23"));
        assertThat(new ObjectMapper().writeValueAsString(response)).doesNotContain("exactValues");
    }
}
