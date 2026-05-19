package org.openfinance.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.openfinance.dto.ImportedTransaction;
import org.openfinance.entity.Category;
import org.openfinance.entity.CategoryType;
import org.openfinance.entity.Transaction;
import org.openfinance.repository.CategoryRepository;
import org.openfinance.repository.TransactionRepository;

@ExtendWith(MockitoExtension.class)
class AutoCategorizationServiceTest {

    @Mock private TransactionRepository transactionRepository;

    @Mock private CategoryRepository categoryRepository;

    @Mock private OperationHistoryService operationHistoryService;

    @InjectMocks private AutoCategorizationService autoCategorizationService;

    private Category shoppingCategory;
    private Category fastFoodCategory;
    private Transaction historicAmazon;
    private Transaction historicUberEats;

    @BeforeEach
    void setUp() {
        // Setup mock categories
        shoppingCategory =
                Category.builder().id(1L).name("Shopping").type(CategoryType.EXPENSE).build();

        fastFoodCategory =
                Category.builder().id(2L).name("Fast Food").type(CategoryType.EXPENSE).build();

        // Setup mock historical transactions
        historicAmazon =
                Transaction.builder()
                        .id(100L)
                        .categoryId(1L)
                        .payee("Amazon")
                        .description("AMAZON MKTPLACE PMTS") // Historic raw text might be stored in
                        // description
                        .build();

        historicUberEats =
                Transaction.builder()
                        .id(101L)
                        .categoryId(2L)
                        .payee("Uber Eats")
                        .description("UBER EATS AMSTERDAM")
                        .build();
    }

    @Test
    void predictCategoryAndPayee_ShouldMatchSimilarAmazonTransaction() {
        // Arrange
        when(transactionRepository.findByUserId(1L))
                .thenReturn(List.of(historicAmazon, historicUberEats));
        when(categoryRepository.findById(1L)).thenReturn(Optional.of(shoppingCategory));

        ImportedTransaction importedTx =
                ImportedTransaction.builder()
                        .transactionDate(LocalDate.now())
                        .amount(BigDecimal.valueOf(-15.99))
                        .payee("AMAZON MKTPLACE PMTS AMZN.COM/BILL WA") // Similar to historical,
                        // slightly
                        // mutated
                        .memo(null)
                        .build();

        // Act
        Optional<AutoCategorizationService.Prediction> result =
                autoCategorizationService.predictCategoryAndPayee(importedTx, 1L);

        // Assert
        assertTrue(result.isPresent());
        assertEquals("Shopping", result.get().suggestedCategoryName());
        assertEquals("Amazon", result.get().suggestedPayee());
        assertTrue(result.get().confidenceScore() >= 0.4); // Confidence should be reasonably high
    }

    @Test
    void predictCategoryAndPayee_ShouldMatchSimilarUberEatsTransaction() {
        // Arrange
        when(transactionRepository.findByUserId(1L))
                .thenReturn(List.of(historicAmazon, historicUberEats));
        when(categoryRepository.findById(2L)).thenReturn(Optional.of(fastFoodCategory));

        ImportedTransaction importedTx =
                ImportedTransaction.builder()
                        .transactionDate(LocalDate.now())
                        .amount(BigDecimal.valueOf(-25.50))
                        .payee("UBER EATS AMSTERDAM NOORD") // intersection 3, union 4 => 0.75 >
                        // 0.4 threshold
                        .memo(null)
                        .build();

        // Act
        Optional<AutoCategorizationService.Prediction> result =
                autoCategorizationService.predictCategoryAndPayee(importedTx, 1L);

        // Assert
        assertTrue(result.isPresent());
        assertEquals("Fast Food", result.get().suggestedCategoryName());
        assertEquals("Uber Eats", result.get().suggestedPayee());
        assertTrue(result.get().confidenceScore() >= 0.4);
    }

    @Test
    void predictCategoryAndPayee_NoMatchWhenSimilarityIsLow() {
        // Arrange
        when(transactionRepository.findByUserId(1L))
                .thenReturn(List.of(historicAmazon, historicUberEats));

        ImportedTransaction importedTx =
                ImportedTransaction.builder()
                        .transactionDate(LocalDate.now())
                        .amount(BigDecimal.valueOf(-50.00))
                        .payee("STARBUCKS STORE 12345") // Completely unrelated to Amazon/Uber Eats
                        .memo("COFFEE")
                        .build();

        // Act
        Optional<AutoCategorizationService.Prediction> result =
                autoCategorizationService.predictCategoryAndPayee(importedTx, 1L);

        // Assert
        assertFalse(result.isPresent());
    }
}
