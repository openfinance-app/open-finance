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
import org.openfinance.entity.CategoryType;
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
    void rejectsIncomeCategoriesForAnExpense() {
        ImportedTransaction expense =
                ImportedTransaction.builder().amount(new BigDecimal("-25")).build();
        when(aiProvider.sendPrompt(anyString(), anyString()))
                .thenReturn(Mono.just("[{\"index\":1,\"category\":\"Salary\"}]"));
        service.categorizeWithAI(
                List.of(expense),
                List.of(
                        category(1L, "Salary", CategoryType.INCOME),
                        category(2L, "Food", CategoryType.EXPENSE)));
        assertThat(expense.getCategory()).isNull();
        assertThat(expense.getCategorizationConfidence()).isNull();
    }

    @Test
    void selectsOnlyTheCategoryTypeMatchingEachTransaction() {
        ImportedTransaction income =
                ImportedTransaction.builder().amount(new BigDecimal("25")).build();
        ImportedTransaction expense =
                ImportedTransaction.builder().amount(new BigDecimal("-25")).build();
        when(aiProvider.sendPrompt(anyString(), anyString()))
                .thenReturn(
                        Mono.just(
                                "[{\"index\":1,\"category\":\"Other\"},{\"index\":2,\"category\":\"Food\"}]"));
        service.categorizeWithAI(
                List.of(income, expense),
                List.of(
                        category(1L, "Other", CategoryType.INCOME),
                        category(2L, "Other", CategoryType.EXPENSE),
                        category(3L, "Food", CategoryType.EXPENSE)));
        assertThat(income.getCategory()).isEqualTo("Other");
        assertThat(expense.getCategory()).isEqualTo("Food");
    }

    @Test
    void rejectsWrongTypePositionalFallbackAsWell() {
        ImportedTransaction expense =
                ImportedTransaction.builder().amount(new BigDecimal("-25")).build();
        when(aiProvider.sendPrompt(anyString(), anyString())).thenReturn(Mono.just("[\"Salary\"]"));
        service.categorizeWithAI(
                List.of(expense), List.of(category(1L, "Salary", CategoryType.INCOME)));
        assertThat(expense.getCategory()).isNull();
    }

    private Category category(Long id, String name, CategoryType type) {
        return Category.builder().id(id).name(name).type(type).isSystem(false).build();
    }
}
