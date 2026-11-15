package org.openfinance.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.util.List;
import java.util.Locale;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.openfinance.config.AICategorizationProperties;
import org.openfinance.dto.ImportedTransaction;
import org.openfinance.entity.Category;
import org.openfinance.repository.CategoryRepository;
import org.openfinance.repository.ImportSessionRepository;
import org.openfinance.service.ai.AIProvider;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import reactor.core.publisher.Mono;

@ExtendWith(MockitoExtension.class)
@DisplayName("AI suggestions must be valid, specific and unambiguous")
class AICategorizationBoundaryTest {
    @Mock AIProvider provider;
    @Mock ImportSessionRepository sessions;
    @Mock CategoryRepository categories;
    @Mock MessageSource messages;
    @Mock DefaultCurrencyProvider currencies;
    @Spy ObjectMapper mapper = new ObjectMapper();
    @Spy AICategorizationProperties properties = new AICategorizationProperties();
    @InjectMocks AICategorizationService service;

    @AfterEach
    void resetLocale() {
        LocaleContextHolder.resetLocaleContext();
    }

    @ParameterizedTest
    @ValueSource(
            strings = {
                "{}",
                "{\"results\":null}",
                "{\"results\":{}}",
                "{\"results\":[]}",
                "{\"results\":[{\"index\":0,\"category\":\"Food\"}]}",
                "{\"results\":[{\"index\":2,\"category\":\"Food\"}]}",
                "{\"results\":[{\"index\":2147483648,\"category\":\"Food\"}]}",
                "{\"results\":[{\"index\":1.0,\"category\":\"Food\"}]}",
                "{\"results\":[{\"index\":\"1\",\"category\":\"Food\"}]}",
                "{\"results\":[{\"category\":\"Food\"}]}",
                "{\"results\":[{\"index\":1,\"category\":null}]}",
                "{\"results\":[{\"index\":1,\"category\":1}]}",
                "{\"results\":[{\"index\":1}]}",
                "{\"results\":[null]}"
            })
    void invalidResponsesAreRetriedOnceAndLeaveTheRecordEditable(String response) {
        when(provider.isAvailable()).thenReturn(Mono.just(true));
        when(provider.sendStructuredPrompt(anyString(), anyString(), any()))
                .thenReturn(Mono.just(response));
        ImportedTransaction row = row("Market");
        service.categorizeWithAI(List.of(row), List.of(category(1L, "Food")));
        assertThat(row.getCategory()).isNull();
        assertThat(row.getCategorizationConfidence()).isNull();
        assertThat(row.hasErrors()).isFalse();
        assertThat(row.getValidationErrors())
                .anyMatch(error -> error.startsWith("AI_UNAVAILABLE:"));
        verify(provider, times(2)).sendStructuredPrompt(anyString(), anyString(), any());
    }

    @ParameterizedTest
    @ValueSource(strings = {"offline", "empty", "error"})
    void unavailableProvidersDoNotTouchExistingCategories(String readiness) {
        when(provider.isAvailable())
                .thenReturn(
                        switch (readiness) {
                            case "offline" -> Mono.just(false);
                            case "empty" -> Mono.empty();
                            default -> Mono.error(new IllegalStateException("connection reset"));
                        });
        ImportedTransaction blank = row("Market");
        blank.setCategory("  ");
        ImportedTransaction known = row("Store");
        known.setCategory("User choice");
        service.categorizeWithAI(List.of(blank, known), List.of(category(1L, "Food")));
        assertThat(blank.getValidationErrors())
                .anyMatch(error -> error.startsWith("AI_UNAVAILABLE:"));
        assertThat(known.getCategory()).isEqualTo("User choice");
        assertThat(known.getValidationErrors()).isEmpty();
        verify(provider, never()).sendStructuredPrompt(anyString(), anyString(), any());
    }

    @Test
    void emptyCategoryListsAndAlreadyAssignedRowsDoNotCallTheProvider() {
        ImportedTransaction row = row("Market");
        service.categorizeWithAI(List.of(row), List.of());
        row.setCategory("Food");
        service.categorizeWithAI(List.of(row), List.of(category(1L, "Food")));
        verifyNoInteractions(provider);
        assertThat(row.getCategory()).isEqualTo("Food");
    }

    @Test
    void duplicateTranslatedNamesCannotSelectAnArbitraryCategory() {
        when(provider.isAvailable()).thenReturn(Mono.just(true));
        when(provider.sendStructuredPrompt(anyString(), anyString(), any()))
                .thenReturn(Mono.just("{\"results\":[{\"index\":1,\"category\":\"FOOD\"}]}"));
        ImportedTransaction row = row("Food store");
        service.categorizeWithAI(
                List.of(row),
                List.of(category(1L, "Food"), category(2L, "food"), category(3L, "Food")));
        assertThat(row.getCategory()).isNull();
        assertThat(row.getCategorizationConfidence()).isNull();
        verify(provider, times(2)).sendStructuredPrompt(anyString(), anyString(), any());
    }

    @Test
    void repeatedReferencesToTheSameCategoryAreNotAmbiguous() {
        when(provider.isAvailable()).thenReturn(Mono.just(true));
        when(provider.sendStructuredPrompt(anyString(), anyString(), any()))
                .thenReturn(Mono.just("{\"results\":[{\"index\":1,\"category\":\" food \"}]}"));
        ImportedTransaction row = row("Food store");
        Category food = category(1L, "Food");
        service.categorizeWithAI(List.of(row), List.of(food, food));
        assertThat(row.getCategory()).isEqualTo("Food");
        verify(provider).sendStructuredPrompt(anyString(), anyString(), any());
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" "})
    void systemCategoriesWithoutTranslationKeysUseStoredNames(String key) {
        when(provider.isAvailable()).thenReturn(Mono.just(true));
        when(provider.sendStructuredPrompt(anyString(), anyString(), any()))
                .thenReturn(Mono.just("{\"results\":[{\"index\":1,\"category\":\"Food\"}]}"));
        Category food = category(1L, "Food");
        food.setIsSystem(true);
        food.setNameKey(key);
        ImportedTransaction row = row("Food store");
        service.categorizeWithAI(List.of(row), List.of(food));
        assertThat(row.getCategory()).isEqualTo("Food");
        verifyNoInteractions(messages);
    }

    @Test
    void localizedSuggestionsStoreTheCanonicalCategoryName() {
        LocaleContextHolder.setLocale(Locale.FRENCH);
        when(provider.isAvailable()).thenReturn(Mono.just(true));
        when(messages.getMessage(
                        eq("category.groceries"), isNull(), eq("Groceries"), any(Locale.class)))
                .thenAnswer(
                        call ->
                                Locale.FRENCH.equals(call.getArgument(3))
                                        ? "Épicerie"
                                        : "Groceries");
        when(provider.sendStructuredPrompt(anyString(), anyString(), any()))
                .thenReturn(Mono.just("{\"results\":[{\"index\":1,\"category\":\" ÉPICERIE \"}]}"));
        Category food = category(1L, "Groceries");
        food.setIsSystem(true);
        food.setNameKey("category.groceries");
        ImportedTransaction row = row("Épicerie locale");
        service.categorizeWithAI(List.of(row), List.of(food));
        assertThat(row.getCategory()).isEqualTo("Groceries");
        ArgumentCaptor<JsonNode> schema = ArgumentCaptor.forClass(JsonNode.class);
        verify(provider).sendStructuredPrompt(anyString(), anyString(), schema.capture());
        assertThat(
                        schema.getValue()
                                .at("/properties/results/items/properties/category/enum")
                                .toString())
                .isEqualTo("[\"\",\"Épicerie\"]");
    }

    @ParameterizedTest
    @ValueSource(strings = {"category.other", "category.other.shopping"})
    void genericCategoryFamiliesAreAbsentFromTheAllowedSuggestions(String key) {
        when(provider.isAvailable()).thenReturn(Mono.just(true));
        when(provider.sendStructuredPrompt(anyString(), anyString(), any()))
                .thenReturn(Mono.just("{\"results\":[{\"index\":1,\"category\":\"\"}]}"));
        Category generic = category(1L, "Other");
        generic.setNameKey(key);
        ImportedTransaction row = row("Uncertain merchant");
        service.categorizeWithAI(List.of(row), List.of(generic));
        ArgumentCaptor<JsonNode> schema = ArgumentCaptor.forClass(JsonNode.class);
        verify(provider).sendStructuredPrompt(anyString(), anyString(), schema.capture());
        assertThat(
                        schema.getValue()
                                .at("/properties/results/items/properties/category/enum")
                                .toString())
                .isEqualTo("[\"\"]");
        assertThat(row.getCategory()).isNull();
        assertThat(row.getValidationErrors()).isEmpty();
    }

    private ImportedTransaction row(String payee) {
        return ImportedTransaction.builder().payee(payee).amount(new BigDecimal("-10")).build();
    }

    private Category category(Long id, String name) {
        return Category.builder().id(id).name(name).isSystem(false).build();
    }
}
