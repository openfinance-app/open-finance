package org.openfinance.service;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.openfinance.dto.TransactionSplitRequest;
import org.openfinance.dto.TransactionSplitResponse;
import org.openfinance.entity.Category;
import org.openfinance.entity.TransactionSplit;
import org.openfinance.entity.TransactionType;
import org.openfinance.exception.InvalidTransactionException;
import org.openfinance.repository.CategoryRepository;
import org.openfinance.repository.TransactionSplitRepository;
import org.openfinance.security.EncryptionService;

/** Unit tests for TransactionSplitService covering validation, persistence, and retrieval. */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("TransactionSplitService Unit Tests")
class TransactionSplitServiceTest {

    @Mock private TransactionSplitRepository splitRepository;

    @Mock private CategoryRepository categoryRepository;

    @Mock private EncryptionService encryptionService;

    @Mock private OperationHistoryService operationHistoryService;

    @InjectMocks private TransactionSplitService transactionSplitService;

    private SecretKey testKey;

    @BeforeEach
    void setUp() {
        byte[] keyBytes = new byte[16];
        for (int i = 0; i < keyBytes.length; i++) keyBytes[i] = (byte) (i + 1);
        testKey = new SecretKeySpec(keyBytes, "AES");
    }

    // ---------- Helpers ----------
    private TransactionSplitRequest createSplitRequest(
            Long categoryId, BigDecimal amount, String description) {
        return TransactionSplitRequest.builder()
                .categoryId(categoryId)
                .amount(amount)
                .description(description)
                .build();
    }

    private TransactionSplit createSplitEntity(
            Long id, Long transactionId, Long categoryId, BigDecimal amount, String description) {
        TransactionSplit split = new TransactionSplit();
        split.setId(id);
        split.setTransactionId(transactionId);
        split.setCategoryId(categoryId);
        split.setAmount(amount);
        split.setDescription(description);
        return split;
    }

    private Category createCategory(Long id, String name, String icon, String color) {
        return Category.builder().id(id).name(name).icon(icon).color(color).build();
    }

    // ---------- validateSplits tests ----------

    @Test
    @DisplayName("Should pass validation when splits are null or empty")
    void shouldPassValidationWhenSplitsNullOrEmpty() {
        // Arrange
        BigDecimal totalAmount = new BigDecimal("100.00");

        // Act & Assert
        assertThatCode(
                        () ->
                                transactionSplitService.validateSplits(
                                        totalAmount, TransactionType.INCOME, null))
                .doesNotThrowAnyException();
        assertThatCode(
                        () ->
                                transactionSplitService.validateSplits(
                                        totalAmount, TransactionType.EXPENSE, List.of()))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName(
            "Should throw InvalidTransactionException when splits are provided for TRANSFER type")
    void shouldThrowWhenSplitsForTransferType() {
        // Arrange
        BigDecimal totalAmount = new BigDecimal("100.00");
        List<TransactionSplitRequest> splits =
                List.of(createSplitRequest(1L, new BigDecimal("50.00"), "desc"));

        // Act & Assert
        assertThatThrownBy(
                        () ->
                                transactionSplitService.validateSplits(
                                        totalAmount, TransactionType.TRANSFER, splits))
                .isInstanceOf(InvalidTransactionException.class)
                .hasMessageContaining(
                        "Split transactions are not supported for TRANSFER type transactions");
    }

    @Test
    @DisplayName("Should throw InvalidTransactionException when only one split is provided")
    void shouldThrowWhenOnlyOneSplit() {
        // Arrange
        BigDecimal totalAmount = new BigDecimal("100.00");
        List<TransactionSplitRequest> splits =
                List.of(createSplitRequest(1L, new BigDecimal("100.00"), "desc"));

        // Act & Assert
        assertThatThrownBy(
                        () ->
                                transactionSplitService.validateSplits(
                                        totalAmount, TransactionType.INCOME, splits))
                .isInstanceOf(InvalidTransactionException.class)
                .hasMessageContaining("A split transaction must have at least 2 split entries");
    }

    @Test
    @DisplayName("Should pass validation when splits sum exactly matches total amount")
    void shouldPassValidationWhenSumsMatchExactly() {
        // Arrange
        BigDecimal totalAmount = new BigDecimal("100.00");
        List<TransactionSplitRequest> splits =
                List.of(
                        createSplitRequest(1L, new BigDecimal("40.00"), "desc1"),
                        createSplitRequest(2L, new BigDecimal("60.00"), "desc2"));

        // Act & Assert
        assertThatCode(
                        () ->
                                transactionSplitService.validateSplits(
                                        totalAmount, TransactionType.EXPENSE, splits))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("Should pass validation when splits sum matches within tolerance")
    void shouldPassValidationWhenSumsWithinTolerance() {
        // Arrange
        BigDecimal totalAmount = new BigDecimal("100.00");
        List<TransactionSplitRequest> splits =
                List.of(
                        createSplitRequest(1L, new BigDecimal("40.005"), "desc1"),
                        createSplitRequest(2L, new BigDecimal("59.995"), "desc2"));

        // Act & Assert
        assertThatCode(
                        () ->
                                transactionSplitService.validateSplits(
                                        totalAmount, TransactionType.INCOME, splits))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("Should throw InvalidTransactionException when splits sum exceeds tolerance above")
    void shouldThrowWhenSumExceedsToleranceAbove() {
        // Arrange
        BigDecimal totalAmount = new BigDecimal("100.00");
        List<TransactionSplitRequest> splits =
                List.of(
                        createSplitRequest(1L, new BigDecimal("50.02"), "desc1"),
                        createSplitRequest(2L, new BigDecimal("50.00"), "desc2"));

        // Act & Assert
        assertThatThrownBy(
                        () ->
                                transactionSplitService.validateSplits(
                                        totalAmount, TransactionType.EXPENSE, splits))
                .isInstanceOf(InvalidTransactionException.class)
                .hasMessageContaining(
                        "Split amounts sum to 100.02 but parent transaction amount is 100.0000")
                .hasMessageContaining("difference 0.0200 exceeds allowed tolerance of 0.01");
    }

    @Test
    @DisplayName("Should throw InvalidTransactionException when splits sum exceeds tolerance below")
    void shouldThrowWhenSumExceedsToleranceBelow() {
        // Arrange
        BigDecimal totalAmount = new BigDecimal("100.00");
        List<TransactionSplitRequest> splits =
                List.of(
                        createSplitRequest(1L, new BigDecimal("49.98"), "desc1"),
                        createSplitRequest(2L, new BigDecimal("50.00"), "desc2"));

        // Act & Assert
        assertThatThrownBy(
                        () ->
                                transactionSplitService.validateSplits(
                                        totalAmount, TransactionType.INCOME, splits))
                .isInstanceOf(InvalidTransactionException.class)
                .hasMessageContaining(
                        "Split amounts sum to 99.98 but parent transaction amount is 100.0000")
                .hasMessageContaining("difference 0.0200 exceeds allowed tolerance of 0.01");
    }

    // ---------- saveSplits tests ----------

    @Test
    @DisplayName("Should save splits successfully with encryption")
    void shouldSaveSplitsSuccessfully() {
        // Arrange
        Long transactionId = 100L;
        List<TransactionSplitRequest> splits =
                List.of(
                        createSplitRequest(1L, new BigDecimal("40.00"), "desc1"),
                        createSplitRequest(2L, new BigDecimal("60.00"), "desc2"));

        when(encryptionService.encrypt("desc1", testKey)).thenReturn("enc-desc1");
        when(encryptionService.encrypt("desc2", testKey)).thenReturn("enc-desc2");

        ArgumentCaptor<List<TransactionSplit>> captor = ArgumentCaptor.forClass(List.class);

        // Act
        transactionSplitService.saveSplits(transactionId, splits, testKey);

        // Assert
        verify(splitRepository).deleteByTransactionId(transactionId);
        verify(splitRepository).flush();
        verify(splitRepository).saveAll(captor.capture());

        List<TransactionSplit> saved = captor.getValue();
        assertThat(saved).hasSize(2);

        TransactionSplit first = saved.get(0);
        assertThat(first.getTransactionId()).isEqualTo(transactionId);
        assertThat(first.getCategoryId()).isEqualTo(1L);
        assertThat(first.getAmount()).isEqualByComparingTo(new BigDecimal("40.00"));
        assertThat(first.getDescription()).isEqualTo("enc-desc1");

        TransactionSplit second = saved.get(1);
        assertThat(second.getTransactionId()).isEqualTo(transactionId);
        assertThat(second.getCategoryId()).isEqualTo(2L);
        assertThat(second.getAmount()).isEqualByComparingTo(new BigDecimal("60.00"));
        assertThat(second.getDescription()).isEqualTo("enc-desc2");
    }

    @Test
    @DisplayName("Should handle null description in splits")
    void shouldHandleNullDescriptionInSplits() {
        // Arrange
        Long transactionId = 101L;
        List<TransactionSplitRequest> splits =
                List.of(
                        createSplitRequest(1L, new BigDecimal("50.00"), null),
                        createSplitRequest(2L, new BigDecimal("50.00"), ""));

        ArgumentCaptor<List<TransactionSplit>> captor = ArgumentCaptor.forClass(List.class);

        // Act
        transactionSplitService.saveSplits(transactionId, splits, testKey);

        // Assert
        verify(splitRepository).deleteByTransactionId(transactionId);
        verify(splitRepository).flush();
        verify(splitRepository).saveAll(captor.capture());
        verify(encryptionService, never()).encrypt(anyString(), eq(testKey));

        List<TransactionSplit> saved = captor.getValue();
        assertThat(saved).hasSize(2);
        assertThat(saved.get(0).getDescription()).isNull();
        assertThat(saved.get(1).getDescription()).isNull();
    }

    @Test
    @DisplayName("Should clear splits when null splits provided")
    void shouldClearSplitsWhenNullProvided() {
        // Arrange
        Long transactionId = 102L;

        // Act
        transactionSplitService.saveSplits(transactionId, null, testKey);

        // Assert
        verify(splitRepository).deleteByTransactionId(transactionId);
        verify(splitRepository).flush();
        verify(splitRepository, never()).saveAll(any());
        verify(encryptionService, never()).encrypt(anyString(), eq(testKey));
    }

    @Test
    @DisplayName("Should clear splits when empty list provided")
    void shouldClearSplitsWhenEmptyListProvided() {
        // Arrange
        Long transactionId = 103L;

        // Act
        transactionSplitService.saveSplits(transactionId, List.of(), testKey);

        // Assert
        verify(splitRepository).deleteByTransactionId(transactionId);
        verify(splitRepository).flush();
        verify(splitRepository, never()).saveAll(any());
        verify(encryptionService, never()).encrypt(anyString(), eq(testKey));
    }

    // ---------- getSplitsForTransaction tests ----------

    @Test
    @DisplayName("Should return decrypted splits with category denormalization")
    void shouldReturnDecryptedSplitsWithCategoryDenormalization() {
        // Arrange
        Long transactionId = 200L;
        List<TransactionSplit> splits =
                List.of(
                        createSplitEntity(
                                1L, transactionId, 1L, new BigDecimal("40.00"), "enc-desc1"),
                        createSplitEntity(
                                2L, transactionId, 2L, new BigDecimal("60.00"), "enc-desc2"));

        Category cat1 = createCategory(1L, "Food", "ic-food", "#ff0000");
        Category cat2 = createCategory(2L, "Transport", "ic-car", "#00ff00");

        when(splitRepository.findByTransactionIdOrderById(transactionId)).thenReturn(splits);
        when(encryptionService.decrypt("enc-desc1", testKey)).thenReturn("desc1");
        when(encryptionService.decrypt("enc-desc2", testKey)).thenReturn("desc2");

        // Simulate lazy loading by setting category on entities
        splits.get(0).setCategory(cat1);
        splits.get(1).setCategory(cat2);

        // Act
        List<TransactionSplitResponse> responses =
                transactionSplitService.getSplitsForTransaction(transactionId, testKey);

        // Assert
        assertThat(responses).hasSize(2);

        TransactionSplitResponse resp1 = responses.get(0);
        assertThat(resp1.getId()).isEqualTo(1L);
        assertThat(resp1.getTransactionId()).isEqualTo(transactionId);
        assertThat(resp1.getCategoryId()).isEqualTo(1L);
        assertThat(resp1.getCategoryName()).isEqualTo("Food");
        assertThat(resp1.getCategoryIcon()).isEqualTo("ic-food");
        assertThat(resp1.getCategoryColor()).isEqualTo("#ff0000");
        assertThat(resp1.getAmount()).isEqualByComparingTo(new BigDecimal("40.00"));
        assertThat(resp1.getDescription()).isEqualTo("desc1");

        TransactionSplitResponse resp2 = responses.get(1);
        assertThat(resp2.getId()).isEqualTo(2L);
        assertThat(resp2.getTransactionId()).isEqualTo(transactionId);
        assertThat(resp2.getCategoryId()).isEqualTo(2L);
        assertThat(resp2.getCategoryName()).isEqualTo("Transport");
        assertThat(resp2.getCategoryIcon()).isEqualTo("ic-car");
        assertThat(resp2.getCategoryColor()).isEqualTo("#00ff00");
        assertThat(resp2.getAmount()).isEqualByComparingTo(new BigDecimal("60.00"));
        assertThat(resp2.getDescription()).isEqualTo("desc2");
    }

    @Test
    @DisplayName("Should return empty list when no splits exist")
    void shouldReturnEmptyListWhenNoSplits() {
        // Arrange
        Long transactionId = 201L;
        when(splitRepository.findByTransactionIdOrderById(transactionId)).thenReturn(List.of());

        // Act
        List<TransactionSplitResponse> responses =
                transactionSplitService.getSplitsForTransaction(transactionId, testKey);

        // Assert
        assertThat(responses).isEmpty();
        verify(encryptionService, never()).decrypt(anyString(), eq(testKey));
    }

    @Test
    @DisplayName("Should handle null description in splits")
    void shouldHandleNullDescriptionInSplitsRetrieval() {
        // Arrange
        Long transactionId = 202L;
        List<TransactionSplit> splits =
                List.of(createSplitEntity(1L, transactionId, 1L, new BigDecimal("100.00"), null));

        Category cat = createCategory(1L, "Misc", "ic-misc", "#000000");
        splits.get(0).setCategory(cat);

        when(splitRepository.findByTransactionIdOrderById(transactionId)).thenReturn(splits);

        // Act
        List<TransactionSplitResponse> responses =
                transactionSplitService.getSplitsForTransaction(transactionId, testKey);

        // Assert
        assertThat(responses).hasSize(1);
        assertThat(responses.get(0).getDescription()).isNull();
        verify(encryptionService, never()).decrypt(anyString(), eq(testKey));
    }

    @Test
    @DisplayName("Should handle decryption failure gracefully")
    void shouldHandleDecryptionFailureGracefully() {
        // Arrange
        Long transactionId = 203L;
        List<TransactionSplit> splits =
                List.of(
                        createSplitEntity(
                                1L, transactionId, 1L, new BigDecimal("100.00"), "enc-desc"));

        Category cat = createCategory(1L, "Misc", "ic-misc", "#000000");
        splits.get(0).setCategory(cat);

        when(splitRepository.findByTransactionIdOrderById(transactionId)).thenReturn(splits);
        when(encryptionService.decrypt("enc-desc", testKey))
                .thenThrow(new RuntimeException("Decryption failed"));

        // Act
        List<TransactionSplitResponse> responses =
                transactionSplitService.getSplitsForTransaction(transactionId, testKey);

        // Assert
        assertThat(responses).hasSize(1);
        assertThat(responses.get(0).getDescription()).isNull();
    }

    @Test
    @DisplayName("Should fall back to repository lookup when category not loaded")
    void shouldFallbackToRepositoryWhenCategoryNotLoaded() {
        // Arrange
        Long transactionId = 204L;
        List<TransactionSplit> splits =
                List.of(createSplitEntity(1L, transactionId, 1L, new BigDecimal("100.00"), null));

        // Category not set on entity, so fallback to repository
        Category cat = createCategory(1L, "Fallback", "ic-fallback", "#ffffff");

        when(splitRepository.findByTransactionIdOrderById(transactionId)).thenReturn(splits);
        when(categoryRepository.findById(1L)).thenReturn(Optional.of(cat));

        // Act
        List<TransactionSplitResponse> responses =
                transactionSplitService.getSplitsForTransaction(transactionId, testKey);

        // Assert
        assertThat(responses).hasSize(1);
        assertThat(responses.get(0).getCategoryName()).isEqualTo("Fallback");
        assertThat(responses.get(0).getCategoryIcon()).isEqualTo("ic-fallback");
        assertThat(responses.get(0).getCategoryColor()).isEqualTo("#ffffff");
    }

    @Test
    @DisplayName("Should handle splits without category")
    void shouldHandleSplitsWithoutCategory() {
        // Arrange
        Long transactionId = 205L;
        List<TransactionSplit> splits =
                List.of(createSplitEntity(1L, transactionId, null, new BigDecimal("100.00"), null));

        when(splitRepository.findByTransactionIdOrderById(transactionId)).thenReturn(splits);

        // Act
        List<TransactionSplitResponse> responses =
                transactionSplitService.getSplitsForTransaction(transactionId, testKey);

        // Assert
        assertThat(responses).hasSize(1);
        assertThat(responses.get(0).getCategoryId()).isNull();
        assertThat(responses.get(0).getCategoryName()).isNull();
        assertThat(responses.get(0).getCategoryIcon()).isNull();
        assertThat(responses.get(0).getCategoryColor()).isNull();
    }
}
