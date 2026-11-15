package org.openfinance.mapper;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Locale;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.openfinance.entity.Budget;
import org.openfinance.entity.BudgetAlert;
import org.openfinance.entity.Category;
import org.springframework.context.support.ResourceBundleMessageSource;

class BudgetAlertMapperTest {
    @Test
    void rendersSystemCategoriesAndEverySeverityInTheRequestLanguage() {
        ResourceBundleMessageSource messages = new ResourceBundleMessageSource();
        messages.setBasename("i18n/messages");
        messages.setDefaultEncoding("UTF-8");
        messages.setFallbackToSystemLocale(false);
        BudgetAlertMapper mapper = new BudgetAlertMapper(messages);
        Category category =
                Category.builder()
                        .name("Groceries")
                        .nameKey("category.groceries")
                        .isSystem(true)
                        .build();
        BudgetAlert alert =
                BudgetAlert.builder()
                        .budget(
                                Budget.builder()
                                        .id(1L)
                                        .category(category)
                                        .period(org.openfinance.entity.BudgetPeriod.MONTHLY)
                                        .startDate(java.time.LocalDate.now().withDayOfMonth(1))
                                        .endDate(
                                                java.time.LocalDate.now()
                                                        .withDayOfMonth(1)
                                                        .plusMonths(1)
                                                        .minusDays(1))
                                        .build())
                        .threshold(new BigDecimal("75"))
                        .lastTriggered(LocalDateTime.now())
                        .build();
        org.openfinance.dto.BudgetAlertResponse french =
                mapper.toResponseWithProgress(alert, new BigDecimal("78.45"), Locale.FRENCH);
        assertThat(french.getCategoryName())
                .isEqualTo(messages.getMessage(category.getNameKey(), null, Locale.FRENCH));
        assertThat(french.getBudgetName()).isEqualTo("Budget " + french.getCategoryName());
        assertThat(french.getMessage())
                .contains("Attention", "78,5 %", french.getCategoryName())
                .doesNotContain("spent");
        alert.setThreshold(new BigDecimal("90"));
        assertThat(
                        mapper.toResponseWithProgress(alert, new BigDecimal("95"), Locale.FRENCH)
                                .getMessage())
                .startsWith("Seuil critique");
        alert.setThreshold(new BigDecimal("100"));
        assertThat(
                        mapper.toResponseWithProgress(alert, new BigDecimal("110"), Locale.FRENCH)
                                .getMessage())
                .startsWith("Budget dépassé");
        category.setIsSystem(false);
        category.setName("My own café");
        assertThat(
                        mapper.toResponseWithProgress(alert, new BigDecimal("110"), Locale.ENGLISH)
                                .getMessage())
                .contains("You've spent 110.0%", "My own café");
    }

    private BudgetAlertMapper mapper() {
        ResourceBundleMessageSource messages = new ResourceBundleMessageSource();
        messages.setBasename("i18n/messages");
        messages.setDefaultEncoding("UTF-8");
        messages.setFallbackToSystemLocale(false);
        return new BudgetAlertMapper(messages);
    }

    private BudgetAlert alert() {
        return BudgetAlert.builder()
                .budget(
                        Budget.builder()
                                .id(7L)
                                .amount("100")
                                .period(org.openfinance.entity.BudgetPeriod.MONTHLY)
                                .recurring(true)
                                .startDate(java.time.LocalDate.now().withDayOfMonth(1))
                                .endDate(
                                        java.time.LocalDate.now()
                                                .withDayOfMonth(1)
                                                .plusMonths(1)
                                                .minusDays(1))
                                .category(
                                        Category.builder()
                                                .name("Custom food")
                                                .isSystem(false)
                                                .build())
                                .build())
                .threshold(new BigDecimal("100"))
                .lastTriggered(LocalDateTime.now())
                .isRead(false)
                .build();
    }

    @ParameterizedTest
    @ValueSource(
            strings = {"null-alert", "missing-budget", "missing-category", "system-without-key"})
    void mapsUnavailableOptionalAlertContextWithoutInventingNames(String missing) {
        BudgetAlert alert = alert();
        switch (missing) {
            case "null-alert" -> alert = null;
            case "missing-budget" -> alert.setBudget(null);
            case "missing-category" -> alert.getBudget().setCategory(null);
            case "system-without-key" -> alert.getBudget().getCategory().setIsSystem(true);
            default -> throw new AssertionError(missing);
        }
        org.openfinance.dto.BudgetAlertResponse response =
                mapper().toResponseWithProgress(alert, new BigDecimal("105"), Locale.ENGLISH);
        if (alert == null) assertThat(response).isNull();
        else if (missing.equals("system-without-key"))
            assertThat(response.getMessage()).contains("Custom food");
        else {
            assertThat(response.getCategoryName()).isNull();
            assertThat(response.getBudgetName()).isNull();
            assertThat(response.getMessage()).isNull();
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"never-triggered", "previous-period", "current-no-allowance"})
    void staleOrUntriggeredAlertsDoNotLeakIntoCurrentSpendingMessages(String state) {
        BudgetAlert alert = alert();
        if (state.equals("never-triggered")) alert.setLastTriggered(null);
        if (state.equals("previous-period"))
            alert.setLastTriggered(LocalDateTime.now().minusMonths(1));
        org.openfinance.dto.BudgetAlertResponse response =
                mapper().toResponseWithProgress(alert, null, Locale.ENGLISH);
        if (state.equals("current-no-allowance")) {
            assertThat(response.getMessage()).contains("Custom food");
            assertThat(response.isRead()).isFalse();
        } else {
            assertThat(response.getMessage()).isNull();
            assertThat(response.getLastTriggered()).isNull();
        }
        if (state.equals("previous-period")) assertThat(response.isRead()).isTrue();
    }

    @ParameterizedTest
    @CsvSource({
        "100,99.95,Critical",
        "100,100,Alert: Budget exceeded",
        "90,90,Critical",
        "75,75,Warning"
    })
    void choosesSeverityFromExactThresholdsBeforeRoundingTheDisplayedPercentage(
            String threshold, String spent, String prefix) {
        BudgetAlert alert = alert();
        alert.setThreshold(new BigDecimal(threshold));
        assertThat(
                        mapper().toResponseWithProgress(
                                        alert, new BigDecimal(spent), Locale.ENGLISH)
                                .getMessage())
                .startsWith(prefix);
    }
}
