package org.openfinance.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.openfinance.service.InsightMessages.message;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.util.Locale;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.openfinance.config.LocalizationConfig;
import org.openfinance.dto.InsightResponse;
import org.openfinance.entity.Category;
import org.openfinance.entity.Insight;
import org.openfinance.entity.InsightType;
import org.springframework.context.MessageSource;

class InsightLocalizationTest {
    private final MessageSource messages = new LocalizationConfig().messageSource();
    private final InsightLocalization localization =
            new InsightLocalization(messages, new ObjectMapper());

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void newAndPreviouslySavedPayeeAlertsFollowTheReaderLanguage(boolean legacy) {
        String payee = "L'Épicerie {Nord}";
        Insight stored =
                InsightMessages.withText(
                                messages,
                                Locale.ENGLISH,
                                message("insight.unusual.transaction.new.payee.title", payee),
                                message(
                                        "insight.unusual.transaction.new.payee.description",
                                        payee,
                                        new BigDecimal("19.95"),
                                        "EUR"))
                        .build();
        InsightResponse snapshot =
                InsightResponse.builder()
                        .id(17L)
                        .type(InsightType.UNUSUAL_TRANSACTION)
                        .title(stored.getTitle())
                        .description(stored.getDescription())
                        .messageData(legacy ? null : stored.getMessageData())
                        .build();
        InsightResponse french = localization.localize(snapshot, Locale.FRENCH);
        assertThat(french.getTitle()).isEqualTo("Nouveau bénéficiaire détecté : " + payee);
        assertThat(french.getDescription())
                .startsWith("Une transaction de 19,95 EUR")
                .contains(payee);
        assertThat(french.getId()).isEqualTo(17L);
        InsightResponse english = localization.localize(french, Locale.ENGLISH);
        assertThat(english.getTitle()).isEqualTo(stored.getTitle());
        assertThat(english.getDescription()).isEqualTo(stored.getDescription());
        assertThat(snapshot.getTitle()).startsWith("New Payee Detected");
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void translatesNestedEstimateAndCountryWithoutChangingItsSource(boolean legacy) {
        Insight stored =
                InsightMessages.withText(
                                messages,
                                Locale.ENGLISH,
                                message("insight.region.networth.title"),
                                message(
                                        "insight.model.estimate.notice",
                                        message(
                                                "insight.region.networth.description",
                                                new BigDecimal("1234.56"),
                                                "EUR",
                                                InsightMessages.countryName("DE"),
                                                7000,
                                                message("insight.region.networth.below")),
                                        "https://example.test/de"))
                        .build();
        InsightResponse snapshot =
                InsightResponse.builder()
                        .type(InsightType.REGION_COMPARISON)
                        .title(stored.getTitle())
                        .description(stored.getDescription())
                        .messageData(legacy ? null : stored.getMessageData())
                        .build();
        assertThat(localization.localize(snapshot, Locale.FRENCH).getDescription())
                .startsWith("Estimation IA non vérifiée")
                .contains("1234,56", "Allemagne", "en dessous")
                .endsWith("https://example.test/de");
    }

    @Test
    void localizesSystemCategoryLabelsAndPreservesCustomNames() {
        Category system =
                Category.builder().isSystem(true).nameKey("category.salary").name("Salary").build();
        Category custom = Category.builder().name("My Salary").build();
        assertThat(
                        InsightMessages.render(
                                message(
                                        "insight.budget.exceeded.title",
                                        InsightMessages.categoryName(system)),
                                messages,
                                Locale.FRENCH))
                .isEqualTo("Budget Salaire dépassé");
        assertThat(
                        InsightMessages.render(
                                message(
                                        "insight.budget.exceeded.title",
                                        InsightMessages.categoryName(custom)),
                                messages,
                                Locale.FRENCH))
                .isEqualTo("Budget My Salary dépassé");
    }

    @Test
    void retainsUnrecognizedLegacyProse() {
        InsightResponse custom =
                InsightResponse.builder()
                        .type(InsightType.UNUSUAL_TRANSACTION)
                        .title("Internet")
                        .description("My saved observation.")
                        .build();
        assertThat(localization.localize(custom, Locale.FRENCH)).isEqualTo(custom);
    }
}
