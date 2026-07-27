package org.openfinance.config;

import static org.assertj.core.api.Assertions.*;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.source.MapConfigurationPropertySource;

/**
 * Unit tests for {@link BusinessRulesProperties}.
 *
 * <p>Pins the built-in defaults so that externalizing these thresholds to configuration does not
 * silently change the historical hardcoded values (the defaults must equal the literals they
 * replaced).
 */
@DisplayName("BusinessRulesProperties Tests")
class BusinessRulesPropertiesTest {

    @Test
    @DisplayName("Insight thresholds default to the previously-hardcoded values")
    void insightDefaults() {
        BusinessRulesProperties props = new BusinessRulesProperties();

        assertThat(props.getInsights().getSpendingAnomalyThreshold())
                .isEqualByComparingTo(new BigDecimal("0.40"));
        assertThat(props.getInsights().getBudgetWarningThreshold())
                .isEqualByComparingTo(new BigDecimal("0.75"));
        assertThat(props.getInsights().getRecurringExpenseHighRatio())
                .isEqualByComparingTo(new BigDecimal("0.50"));
        assertThat(props.getInsights().getMinSubscriptionAmount())
                .isEqualByComparingTo(new BigDecimal("20"));
        assertThat(props.getInsights().getLowBalanceThreshold())
                .isEqualByComparingTo(new BigDecimal("100"));
    }

    @Test
    @DisplayName("Account low-balance threshold defaults to 1000")
    void accountDefaults() {
        BusinessRulesProperties props = new BusinessRulesProperties();

        assertThat(props.getAccounts().getLowBalanceThreshold())
                .isEqualByComparingTo(new BigDecimal("1000"));
    }

    @Test
    @DisplayName("Debt-to-income health bounds and borrowing term default to prior literals")
    void debtToIncomeDefaults() {
        BusinessRulesProperties props = new BusinessRulesProperties();

        assertThat(props.getDebtToIncome().getExcellentMaxPercent())
                .isEqualByComparingTo(new BigDecimal("20"));
        assertThat(props.getDebtToIncome().getGoodMaxPercent())
                .isEqualByComparingTo(new BigDecimal("35"));
        assertThat(props.getDebtToIncome().getFairMaxPercent())
                .isEqualByComparingTo(new BigDecimal("50"));
        assertThat(props.getDebtToIncome().getBorrowingTermYears()).isEqualTo(10);
    }

    @Test
    @DisplayName("Values are overridable from application.business-rules.* configuration")
    void bindsOverridesFromConfiguration() {
        Map<String, Object> config = new HashMap<>();
        config.put("application.business-rules.insights.min-subscription-amount", "50");
        config.put("application.business-rules.insights.spending-anomaly-threshold", "0.60");
        config.put("application.business-rules.accounts.low-balance-threshold", "2000");
        config.put("application.business-rules.debt-to-income.excellent-max-percent", "15");
        config.put("application.business-rules.debt-to-income.borrowing-term-years", "25");

        BusinessRulesProperties bound =
                new Binder(new MapConfigurationPropertySource(config))
                        .bind("application.business-rules", BusinessRulesProperties.class)
                        .get();

        assertThat(bound.getInsights().getMinSubscriptionAmount())
                .isEqualByComparingTo(new BigDecimal("50"));
        assertThat(bound.getInsights().getSpendingAnomalyThreshold())
                .isEqualByComparingTo(new BigDecimal("0.60"));
        assertThat(bound.getAccounts().getLowBalanceThreshold())
                .isEqualByComparingTo(new BigDecimal("2000"));
        assertThat(bound.getDebtToIncome().getExcellentMaxPercent())
                .isEqualByComparingTo(new BigDecimal("15"));
        assertThat(bound.getDebtToIncome().getBorrowingTermYears()).isEqualTo(25);
    }
}
