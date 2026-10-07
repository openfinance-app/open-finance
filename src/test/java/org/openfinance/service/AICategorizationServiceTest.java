package org.openfinance.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.openfinance.dto.ImportedTransaction;
import org.openfinance.entity.Category;
import org.openfinance.repository.CategoryRepository;
import org.openfinance.repository.ImportSessionRepository;
import org.openfinance.service.ai.AIProvider;
import org.springframework.context.MessageSource;
import reactor.core.publisher.Mono;

@ExtendWith(MockitoExtension.class)
class AICategorizationServiceTest {
    @Mock AIProvider aiProvider;
    @Mock ImportSessionRepository importSessionRepository;
    @Mock CategoryRepository categoryRepository;
    @Mock MessageSource messageSource;
    @Mock DefaultCurrencyProvider defaultCurrencyProvider;
    @Spy ObjectMapper objectMapper = new ObjectMapper();
    @InjectMocks AICategorizationService service;

    @BeforeEach
    void setup() {
        when(aiProvider.isAvailable()).thenReturn(Mono.just(true));
    }

    @Test
    void retriesAnUnknownCategoryAtomicallyUsingSharedCandidates() {
        ImportedTransaction expense =
                ImportedTransaction.builder()
                        .amount(new BigDecimal("-25"))
                        .payee("Netflix")
                        .memo("Streaming subscription")
                        .build();
        ImportedTransaction income =
                ImportedTransaction.builder()
                        .amount(new BigDecimal("2500"))
                        .payee("Employer")
                        .memo("Monthly salary")
                        .build();
        when(aiProvider.sendStructuredPrompt(anyString(), anyString(), any()))
                .thenReturn(
                        Mono.just(
                                "{\"results\":[{\"index\":1,\"category\":\"Subscriptions\"},{\"index\":2,\"category\":\"Unknown\"}]}"))
                .thenAnswer(
                        invocation -> {
                            assertThat(expense.getCategory()).isNull();
                            assertThat(income.getCategory()).isNull();
                            return Mono.just(
                                    "{\"results\":[{\"index\":2,\"category\":\"Salary\"},{\"index\":1,\"category\":\"Subscriptions\"}]}");
                        });
        service.categorizeWithAI(
                List.of(expense, income),
                List.of(category(1L, "Subscriptions"), category(2L, "Salary")));
        assertThat(expense.getCategory()).isEqualTo("Subscriptions");
        assertThat(income.getCategory()).isEqualTo("Salary");
        org.mockito.ArgumentCaptor<com.fasterxml.jackson.databind.JsonNode> schema =
                org.mockito.ArgumentCaptor.forClass(com.fasterxml.jackson.databind.JsonNode.class);
        verify(aiProvider, times(2))
                .sendStructuredPrompt(anyString(), anyString(), schema.capture());
        assertThat(
                        schema.getValue()
                                .at("/properties/results/items/properties/category/enum")
                                .toString())
                .isEqualTo("[\"\",\"Subscriptions\",\"Salary\"]");
    }

    @Test
    void doesNotRetryATransportFailure() {
        ImportedTransaction expense =
                ImportedTransaction.builder().amount(new BigDecimal("-25")).build();
        when(aiProvider.sendStructuredPrompt(anyString(), anyString(), any()))
                .thenReturn(Mono.error(new IllegalStateException("offline")));
        service.categorizeWithAI(List.of(expense), List.of(category(1L, "Food")));
        verify(aiProvider).sendStructuredPrompt(anyString(), anyString(), any());
        assertThat(expense.getCategory()).isNull();
        assertThat(expense.getValidationErrors())
                .anyMatch(value -> value.startsWith("AI_UNAVAILABLE:"));
    }

    @Test
    void acceptsAnyKnownCategoryRegardlessOfDirection() {
        ImportedTransaction expense =
                ImportedTransaction.builder()
                        .amount(new BigDecimal("-25"))
                        .memo("Grocery shopping")
                        .build();
        when(aiProvider.sendStructuredPrompt(anyString(), anyString(), any()))
                .thenReturn(Mono.just("{\"results\":[{\"index\":1,\"category\":\"Salary\"}]}"));
        service.categorizeWithAI(
                List.of(expense), List.of(category(1L, "Salary"), category(2L, "Food")));
        assertThat(expense.getCategory()).isEqualTo("Salary");
        assertThat(expense.getCategorizationConfidence()).isNotNull();
    }

    @Test
    void usesTheSameCategoryForPurchaseAndRefund() {
        ImportedTransaction income =
                ImportedTransaction.builder()
                        .amount(new BigDecimal("25"))
                        .memo("Grocery refund")
                        .build();
        ImportedTransaction expense =
                ImportedTransaction.builder()
                        .amount(new BigDecimal("-25"))
                        .memo("Grocery shopping")
                        .build();
        when(aiProvider.sendStructuredPrompt(anyString(), anyString(), any()))
                .thenReturn(
                        Mono.just(
                                "{\"results\":[{\"index\":1,\"category\":\"Food\"},{\"index\":2,\"category\":\"Food\"}]}"));
        service.categorizeWithAI(List.of(income, expense), List.of(category(1L, "Food")));
        assertThat(income.getCategory()).isEqualTo("Food");
        assertThat(expense.getCategory()).isEqualTo("Food");
    }

    @Test
    void rejectsUnstructuredPositionalFallback() {
        ImportedTransaction expense =
                ImportedTransaction.builder()
                        .amount(new BigDecimal("-25"))
                        .memo("Grocery shopping")
                        .build();
        when(aiProvider.sendStructuredPrompt(anyString(), anyString(), any()))
                .thenReturn(Mono.just("[\"Salary\"]"));
        service.categorizeWithAI(List.of(expense), List.of(category(1L, "Salary")));
        assertThat(expense.getCategory()).isNull();
    }

    @Test
    void rejectsMalformedAndDuplicateResultsWithoutPartialMutation() {
        for (String response :
                List.of(
                        "broken JSON",
                        "{\"results\":[{\"index\":1,\"category\":\"Food\"},{\"index\":1,\"category\":\"Food\"}]}")) {
            ImportedTransaction first =
                    ImportedTransaction.builder().amount(new BigDecimal("-20")).build();
            ImportedTransaction second =
                    ImportedTransaction.builder().amount(new BigDecimal("-30")).build();
            when(aiProvider.sendStructuredPrompt(anyString(), anyString(), any()))
                    .thenReturn(Mono.just(response));
            service.categorizeWithAI(List.of(first, second), List.of(category(1L, "Food")));
            assertThat(first.getCategory()).isNull();
            assertThat(second.getCategory()).isNull();
            assertThat(first.getValidationErrors())
                    .anyMatch(value -> value.startsWith("AI_UNAVAILABLE:"));
            assertThat(first.hasErrors()).isFalse();
        }
    }

    @Test
    void anExplicitAbstentionIsNotReportedAsAProviderFailure() {
        ImportedTransaction expense =
                ImportedTransaction.builder().amount(new BigDecimal("-20")).build();
        when(aiProvider.sendStructuredPrompt(anyString(), anyString(), any()))
                .thenReturn(Mono.just("{\"results\":[{\"index\":1,\"category\":\"\"}]}"));
        service.categorizeWithAI(List.of(expense), List.of(category(1L, "Food")));
        assertThat(expense.getCategory()).isNull();
        assertThat(expense.getValidationErrors())
                .noneMatch(value -> value.startsWith("AI_UNAVAILABLE:"));
    }

    @Test
    void genericCategoriesRemainForManualReview() {
        ImportedTransaction expense =
                ImportedTransaction.builder()
                        .amount(new BigDecimal("-12.45"))
                        .payee("City Pharmacy")
                        .memo("Prescription medicine")
                        .build();
        Category other = category(1L, "Other Expenses");
        other.setNameKey("category.other.expenses");
        when(aiProvider.sendStructuredPrompt(anyString(), anyString(), any()))
                .thenReturn(
                        Mono.just("{\"results\":[{\"index\":1,\"category\":\"Other Expenses\"}]}"));
        service.categorizeWithAI(List.of(expense), List.of(other));
        assertThat(expense.getCategory()).isNull();
        assertThat(expense.getCategorizationConfidence()).isNull();
        assertThat(expense.getValidationErrors()).noneMatch(value -> value.startsWith("AI_MATCH:"));
    }

    @Test
    void acceptsAParentCategoryForDescribedPurchasesButNotBareReferences() {
        Category groceries = category(1L, "Groceries");
        Category organic = category(2L, "Organic Foods");
        organic.setParentId(1L);
        ImportedTransaction described =
                ImportedTransaction.builder()
                        .amount(new BigDecimal("-37.82"))
                        .payee("Green Basket Supermarket")
                        .memo("Weekly grocery shopping")
                        .build();
        ImportedTransaction reference =
                ImportedTransaction.builder()
                        .amount(new BigDecimal("-9.99"))
                        .payee("ABX582")
                        .build();
        when(aiProvider.sendStructuredPrompt(anyString(), anyString(), any()))
                .thenReturn(
                        Mono.just(
                                "{\"results\":[{\"index\":1,\"category\":\"Groceries\"},{\"index\":2,\"category\":\"Groceries\"}]}"));
        service.categorizeWithAI(List.of(described, reference), List.of(groceries, organic));
        assertThat(described.getCategory()).isEqualTo("Groceries");
        assertThat(described.getValidationErrors())
                .anyMatch(value -> value.startsWith("AI_MATCH:"));
        assertThat(reference.getCategory()).isNull();
        assertThat(reference.getCategorizationConfidence()).isNull();
    }

    private Category category(Long id, String name) {
        return Category.builder().id(id).name(name).isSystem(false).build();
    }
}
