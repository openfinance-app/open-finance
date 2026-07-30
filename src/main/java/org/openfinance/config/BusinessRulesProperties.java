package org.openfinance.config;

import java.math.BigDecimal;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Externally-configurable business-rule thresholds.
 *
 * <p>Bound to {@code application.business-rules.*} in {@code application.yml}. Every default here
 * is intentionally equal to the literal it replaced, so enabling this configuration changes no
 * behavior until an operator overrides a value. Follows the same pattern as {@link
 * ExchangeRateProperties} / {@code SchedulerProperties}.
 *
 * <p>Example {@code application.yml}:
 *
 * <pre>{@code
 * application:
 *   business-rules:
 *     insights:
 *       spending-anomaly-threshold: 0.40
 *       budget-warning-threshold: 0.75
 *       recurring-expense-high-ratio: 0.50
 *       min-subscription-amount: 20
 *       low-balance-threshold: 100
 *     accounts:
 *       low-balance-threshold: 1000
 *     debt-to-income:
 *       excellent-max-percent: 20
 *       good-max-percent: 35
 *       fair-max-percent: 50
 *       borrowing-term-years: 10
 * }</pre>
 */
@Component
@ConfigurationProperties(prefix = "application.business-rules")
@Data
public class BusinessRulesProperties {

    private Insights insights = new Insights();
    private Accounts accounts = new Accounts();
    private DebtToIncome debtToIncome = new DebtToIncome();

    /**
     * Default withdrawal rate used by FinancialFreedomService when the request does not supply one
     * (the "4% rule"). Mirrored as a Jackson-deserialization default in {@code
     * FreedomCalculatorRequest.withdrawalRate}.
     */
    private BigDecimal defaultWithdrawalRate = new BigDecimal("4.0");

    /**
     * Default inflation rate used by FinancialFreedomService when the request does not supply one.
     * Mirrored as a Jackson-deserialization default in {@code
     * FreedomCalculatorRequest.inflationRate}.
     */
    private BigDecimal defaultInflationRate = new BigDecimal("2.5");

    /** Thresholds driving {@code InsightService} generation. */
    @Data
    public static class Insights {
        /** Fraction increase in category spending that flags a spending anomaly (0.40 = 40%). */
        private BigDecimal spendingAnomalyThreshold = new BigDecimal("0.40");

        /** Fraction of a budget spent that triggers a warning insight (0.75 = 75%). */
        private BigDecimal budgetWarningThreshold = new BigDecimal("0.75");

        /** Recurring-expense-to-income ratio that flags a high fixed-cost burden (0.50 = 50%). */
        private BigDecimal recurringExpenseHighRatio = new BigDecimal("0.50");

        /** Minimum monthly spend on a category to treat it as a subscription worth surfacing. */
        private BigDecimal minSubscriptionAmount = new BigDecimal("20");

        /** Balance below which a checking/savings account triggers a low-balance insight. */
        private BigDecimal lowBalanceThreshold = new BigDecimal("100");
    }

    /** Thresholds for account-level rules (notifications and search filters). */
    @Data
    public static class Accounts {
        /**
         * Balance below which an account is considered "low balance" (notifications and the {@code
         * lowBalance} account-search filter must agree on this value).
         */
        private BigDecimal lowBalanceThreshold = new BigDecimal("1000");
    }

    /** Debt-to-income health bounds and borrowing-capacity assumptions (percentages 0-100). */
    @Data
    public static class DebtToIncome {
        /** Upper DTI percent (inclusive) for an EXCELLENT financial-health rating. */
        private BigDecimal excellentMaxPercent = new BigDecimal("20");

        /** Upper DTI percent (inclusive) for a GOOD financial-health rating. */
        private BigDecimal goodMaxPercent = new BigDecimal("35");

        /** Upper DTI percent (inclusive) for a FAIR financial-health rating; above this is POOR. */
        private BigDecimal fairMaxPercent = new BigDecimal("50");

        /** Assumed loan term (years) used to estimate available borrowing capacity. */
        private int borrowingTermYears = 10;
    }
}
