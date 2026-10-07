package org.openfinance.mapper;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Locale;
import org.junit.jupiter.api.Test;
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
                        .budget(Budget.builder().id(1L).category(category).build())
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
}
