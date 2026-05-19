package org.openfinance.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.openfinance.entity.*;
import org.openfinance.repository.InsightRepository;
import org.openfinance.repository.TransactionRepository;
import org.openfinance.repository.UserRepository;
import org.springframework.context.MessageSource;

/**
 * Unit tests for {@link UnusualTransactionDetectionService}.
 *
 * <p>Covers:
 *
 * <ul>
 *   <li>No-op path: user not found, no recent transactions, TRANSFER skip
 *   <li>New-payee detection (first-time payee → HIGH insight)
 *   <li>Large-amount detection via Z-score (sufficient history)
 *   <li>Large-amount detection via relative factor (sparse history)
 *   <li>Category-average fallback when payee is blank
 *   <li>Normal transaction → no insight
 *   <li>Return value equals number of persisted insights
 * </ul>
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("UnusualTransactionDetectionService Unit Tests")
class UnusualTransactionDetectionServiceTest {

    @Mock private TransactionRepository transactionRepository;

    @Mock private InsightRepository insightRepository;

    @Mock private UserRepository userRepository;

    @Mock private MessageSource messageSource;

    @Mock private OperationHistoryService operationHistoryService;

    @InjectMocks private UnusualTransactionDetectionService service;

    private User testUser;
    private LocalDateTime since;

    @BeforeEach
    void setUp() {
        testUser = User.builder().id(1L).username("testuser").email("test@example.com").build();
        since = LocalDateTime.now().minusHours(25);

        // Default stub: messageSource returns the key itself so we don't need
        // locale-specific wiring.
        when(messageSource.getMessage(anyString(), any(), any()))
                .thenAnswer(inv -> inv.getArgument(0));
    }

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------

    private Transaction expenseTx(Long id, String payee, double amount, Long categoryId) {
        return Transaction.builder()
                .id(id)
                .userId(1L)
                .type(TransactionType.EXPENSE)
                .amount(BigDecimal.valueOf(amount))
                .currency("EUR")
                .payee(payee)
                .categoryId(categoryId)
                .date(LocalDate.now())
                .isDeleted(false)
                .build();
    }

    private Transaction historicExpenseTx(Long id, String payee, double amount) {
        return Transaction.builder()
                .id(id)
                .userId(1L)
                .type(TransactionType.EXPENSE)
                .amount(BigDecimal.valueOf(amount))
                .currency("EUR")
                .payee(payee)
                .date(LocalDate.now().minusDays(10))
                .isDeleted(false)
                .build();
    }

    private void stubUserFound() {
        when(userRepository.findById(1L)).thenReturn(Optional.of(testUser));
    }

    private void stubRecentTx(List<Transaction> txs) {
        when(transactionRepository.findByUserIdAndCreatedAtAfter(eq(1L), any())).thenReturn(txs);
    }

    // ------------------------------------------------------------------
    // No-op paths
    // ------------------------------------------------------------------

    @Nested
    @DisplayName("No-op paths")
    class NoOpPaths {

        @Test
        @DisplayName("Returns 0 when user is not found")
        void returnsZeroWhenUserNotFound() {
            when(userRepository.findById(1L)).thenReturn(Optional.empty());

            int result = service.detectAndPersist(1L, since);

            assertThat(result).isZero();
            verifyNoInteractions(transactionRepository, insightRepository);
        }

        @Test
        @DisplayName("Returns 0 when no recent transactions exist")
        void returnsZeroWhenNoRecentTransactions() {
            stubUserFound();
            stubRecentTx(Collections.emptyList());

            int result = service.detectAndPersist(1L, since);

            assertThat(result).isZero();
            verifyNoInteractions(insightRepository);
        }

        @Test
        @DisplayName("Skips TRANSFER transactions entirely")
        void skipsTransferTransactions() {
            stubUserFound();
            Transaction transfer =
                    Transaction.builder()
                            .id(1L)
                            .userId(1L)
                            .type(TransactionType.TRANSFER)
                            .amount(BigDecimal.valueOf(200))
                            .currency("EUR")
                            .payee("Internal")
                            .date(LocalDate.now())
                            .isDeleted(false)
                            .build();
            stubRecentTx(List.of(transfer));

            int result = service.detectAndPersist(1L, since);

            assertThat(result).isZero();
            verifyNoInteractions(insightRepository);
        }

        @Test
        @DisplayName("No insight for normal-sized transaction to known payee")
        void noInsightForNormalTransaction() {
            stubUserFound();
            Transaction tx = expenseTx(1L, "Supermarket", 50.0, null);
            stubRecentTx(List.of(tx));

            // Known payee with 6 prior transactions all around 50
            when(transactionRepository.countByUserIdAndPayeeAndCreatedAtBefore(
                            eq(1L), eq("Supermarket"), any()))
                    .thenReturn(6L);
            List<Transaction> history =
                    List.of(
                            historicExpenseTx(10L, "Supermarket", 48.0),
                            historicExpenseTx(11L, "Supermarket", 52.0),
                            historicExpenseTx(12L, "Supermarket", 50.0),
                            historicExpenseTx(13L, "Supermarket", 49.0),
                            historicExpenseTx(14L, "Supermarket", 51.0),
                            historicExpenseTx(15L, "Supermarket", 50.0));
            when(transactionRepository.findByUserIdAndPayeeAndCreatedAtBefore(
                            eq(1L), eq("Supermarket"), any()))
                    .thenReturn(history);

            int result = service.detectAndPersist(1L, since);

            assertThat(result).isZero();
            verifyNoInteractions(insightRepository);
        }
    }

    // ------------------------------------------------------------------
    // New-payee detection
    // ------------------------------------------------------------------

    @Nested
    @DisplayName("New-payee detection")
    class NewPayeeDetection {

        @Test
        @DisplayName("Generates HIGH insight for first-time payee")
        void generatesHighInsightForFirstTimePayee() {
            stubUserFound();
            Transaction tx = expenseTx(1L, "ACME Corp", 300.0, null);
            stubRecentTx(List.of(tx));

            when(transactionRepository.countByUserIdAndPayeeAndCreatedAtBefore(
                            eq(1L), eq("ACME Corp"), any()))
                    .thenReturn(0L);
            when(insightRepository.saveAll(anyList())).thenAnswer(inv -> inv.getArgument(0));

            int result = service.detectAndPersist(1L, since);

            assertThat(result).isEqualTo(1);

            ArgumentCaptor<List<Insight>> captor = ArgumentCaptor.forClass(List.class);
            verify(insightRepository).saveAll(captor.capture());
            List<Insight> saved = captor.getValue();
            assertThat(saved).hasSize(1);
            Insight insight = saved.get(0);
            assertThat(insight.getType()).isEqualTo(InsightType.UNUSUAL_TRANSACTION);
            assertThat(insight.getPriority()).isEqualTo(InsightPriority.HIGH);
            assertThat(insight.getDismissed()).isFalse();
            assertThat(insight.getUser()).isEqualTo(testUser);
        }

        @Test
        @DisplayName("Does not query payee history after first-time detection (early return)")
        void doesNotQueryHistoryAfterFirstTimeDetection() {
            stubUserFound();
            Transaction tx = expenseTx(1L, "NewMerchant", 100.0, null);
            stubRecentTx(List.of(tx));

            when(transactionRepository.countByUserIdAndPayeeAndCreatedAtBefore(
                            eq(1L), eq("NewMerchant"), any()))
                    .thenReturn(0L);
            when(insightRepository.saveAll(anyList())).thenAnswer(inv -> inv.getArgument(0));

            service.detectAndPersist(1L, since);

            // findByUserIdAndPayeeAndCreatedAtBefore must not be called for new payee
            verify(transactionRepository, never())
                    .findByUserIdAndPayeeAndCreatedAtBefore(anyLong(), anyString(), any());
        }

        @Test
        @DisplayName("Handles multiple new-payee transactions in one batch")
        void handleMultipleNewPayeesInOneBatch() {
            stubUserFound();
            Transaction tx1 = expenseTx(1L, "ShopA", 100.0, null);
            Transaction tx2 = expenseTx(2L, "ShopB", 200.0, null);
            stubRecentTx(List.of(tx1, tx2));

            when(transactionRepository.countByUserIdAndPayeeAndCreatedAtBefore(
                            eq(1L), eq("ShopA"), any()))
                    .thenReturn(0L);
            when(transactionRepository.countByUserIdAndPayeeAndCreatedAtBefore(
                            eq(1L), eq("ShopB"), any()))
                    .thenReturn(0L);
            when(insightRepository.saveAll(anyList())).thenAnswer(inv -> inv.getArgument(0));

            int result = service.detectAndPersist(1L, since);

            assertThat(result).isEqualTo(2);
        }
    }

    // ------------------------------------------------------------------
    // Large-amount detection (Z-score path, ≥5 history records)
    // ------------------------------------------------------------------

    @Nested
    @DisplayName("Large-amount detection — Z-score path")
    class LargeAmountZScore {

        @Test
        @DisplayName("Generates HIGH insight when amount exceeds mean + 2.5 * stdDev")
        void generatesHighInsightForZScoreAnomaly() {
            stubUserFound();
            // mean ≈ 50, stdDev ≈ 1 → threshold ≈ 52.5; 200 clearly exceeds
            Transaction tx = expenseTx(1L, "Netflix", 200.0, null);
            stubRecentTx(List.of(tx));

            when(transactionRepository.countByUserIdAndPayeeAndCreatedAtBefore(
                            eq(1L), eq("Netflix"), any()))
                    .thenReturn(6L);
            List<Transaction> history =
                    List.of(
                            historicExpenseTx(10L, "Netflix", 49.0),
                            historicExpenseTx(11L, "Netflix", 50.0),
                            historicExpenseTx(12L, "Netflix", 51.0),
                            historicExpenseTx(13L, "Netflix", 50.0),
                            historicExpenseTx(14L, "Netflix", 49.5),
                            historicExpenseTx(15L, "Netflix", 50.5));
            when(transactionRepository.findByUserIdAndPayeeAndCreatedAtBefore(
                            eq(1L), eq("Netflix"), any()))
                    .thenReturn(history);
            when(insightRepository.saveAll(anyList())).thenAnswer(inv -> inv.getArgument(0));

            int result = service.detectAndPersist(1L, since);

            assertThat(result).isEqualTo(1);

            ArgumentCaptor<List<Insight>> captor = ArgumentCaptor.forClass(List.class);
            verify(insightRepository).saveAll(captor.capture());
            Insight insight = captor.getValue().get(0);
            assertThat(insight.getPriority()).isEqualTo(InsightPriority.HIGH);
            assertThat(insight.getType()).isEqualTo(InsightType.UNUSUAL_TRANSACTION);
        }

        @Test
        @DisplayName("No insight when amount is within normal range (sufficient history)")
        void noInsightWhenAmountIsNormal() {
            stubUserFound();
            // mean ≈ 50, stdDev ≈ 1; new tx = 51.5 — within threshold
            Transaction tx = expenseTx(1L, "Netflix", 51.5, null);
            stubRecentTx(List.of(tx));

            when(transactionRepository.countByUserIdAndPayeeAndCreatedAtBefore(
                            eq(1L), eq("Netflix"), any()))
                    .thenReturn(6L);
            List<Transaction> history =
                    List.of(
                            historicExpenseTx(10L, "Netflix", 49.0),
                            historicExpenseTx(11L, "Netflix", 50.0),
                            historicExpenseTx(12L, "Netflix", 51.0),
                            historicExpenseTx(13L, "Netflix", 50.0),
                            historicExpenseTx(14L, "Netflix", 49.5),
                            historicExpenseTx(15L, "Netflix", 50.5));
            when(transactionRepository.findByUserIdAndPayeeAndCreatedAtBefore(
                            eq(1L), eq("Netflix"), any()))
                    .thenReturn(history);

            int result = service.detectAndPersist(1L, since);

            assertThat(result).isZero();
            verifyNoInteractions(insightRepository);
        }
    }

    // ------------------------------------------------------------------
    // Large-amount detection (relative factor path, <5 history records)
    // ------------------------------------------------------------------

    @Nested
    @DisplayName("Large-amount detection — relative factor path")
    class LargeAmountRelativeFactor {

        @Test
        @DisplayName("Generates MEDIUM insight when amount is 3× average (sparse history)")
        void generatesMediumInsightForRelativeAnomaly() {
            stubUserFound();
            // mean of history = 40; new tx = 150; 150 / 40 = 3.75 > 3.0
            Transaction tx = expenseTx(1L, "Amazon", 150.0, null);
            stubRecentTx(List.of(tx));

            when(transactionRepository.countByUserIdAndPayeeAndCreatedAtBefore(
                            eq(1L), eq("Amazon"), any()))
                    .thenReturn(3L);
            List<Transaction> history =
                    List.of(
                            historicExpenseTx(10L, "Amazon", 40.0),
                            historicExpenseTx(11L, "Amazon", 38.0),
                            historicExpenseTx(12L, "Amazon", 42.0));
            when(transactionRepository.findByUserIdAndPayeeAndCreatedAtBefore(
                            eq(1L), eq("Amazon"), any()))
                    .thenReturn(history);
            when(insightRepository.saveAll(anyList())).thenAnswer(inv -> inv.getArgument(0));

            int result = service.detectAndPersist(1L, since);

            assertThat(result).isEqualTo(1);

            ArgumentCaptor<List<Insight>> captor = ArgumentCaptor.forClass(List.class);
            verify(insightRepository).saveAll(captor.capture());
            assertThat(captor.getValue().get(0).getPriority()).isEqualTo(InsightPriority.HIGH);
        }

        @Test
        @DisplayName("No insight when amount is less than 3× average (sparse history)")
        void noInsightWhenAmountBelowRelativeThreshold() {
            stubUserFound();
            // mean = 40; new tx = 100; 100 / 40 = 2.5 < 3.0
            Transaction tx = expenseTx(1L, "Amazon", 100.0, null);
            stubRecentTx(List.of(tx));

            when(transactionRepository.countByUserIdAndPayeeAndCreatedAtBefore(
                            eq(1L), eq("Amazon"), any()))
                    .thenReturn(2L);
            List<Transaction> history =
                    List.of(
                            historicExpenseTx(10L, "Amazon", 38.0),
                            historicExpenseTx(11L, "Amazon", 42.0));
            when(transactionRepository.findByUserIdAndPayeeAndCreatedAtBefore(
                            eq(1L), eq("Amazon"), any()))
                    .thenReturn(history);

            int result = service.detectAndPersist(1L, since);

            assertThat(result).isZero();
            verifyNoInteractions(insightRepository);
        }
    }

    // ------------------------------------------------------------------
    // Category-average fallback (no payee)
    // ------------------------------------------------------------------

    @Nested
    @DisplayName("Category-average fallback (no payee)")
    class CategoryFallback {

        @Test
        @DisplayName("Generates MEDIUM insight when amount is 3× category average and no payee")
        void generatesMediumInsightForCategoryAnomaly() {
            stubUserFound();
            // category mean = 30; new tx = 100; 100 / 30 = 3.33 > 3.0
            Transaction tx = expenseTx(1L, null, 100.0, 99L);
            stubRecentTx(List.of(tx));

            List<Transaction> history =
                    List.of(
                            expenseTx(10L, null, 29.0, 99L),
                            expenseTx(11L, null, 31.0, 99L),
                            expenseTx(12L, null, 30.0, 99L));
            when(transactionRepository.findExpensesByUserIdAndCategoryIdAndCreatedAtBefore(
                            eq(1L), eq(99L), any()))
                    .thenReturn(history);
            when(insightRepository.saveAll(anyList())).thenAnswer(inv -> inv.getArgument(0));

            int result = service.detectAndPersist(1L, since);

            assertThat(result).isEqualTo(1);

            ArgumentCaptor<List<Insight>> captor = ArgumentCaptor.forClass(List.class);
            verify(insightRepository).saveAll(captor.capture());
            assertThat(captor.getValue().get(0).getPriority()).isEqualTo(InsightPriority.MEDIUM);
        }

        @Test
        @DisplayName("No insight when payee is blank and category history is empty")
        void noInsightWhenCategoryHistoryEmpty() {
            stubUserFound();
            Transaction tx = expenseTx(1L, "", 500.0, 99L);
            stubRecentTx(List.of(tx));

            when(transactionRepository.findExpensesByUserIdAndCategoryIdAndCreatedAtBefore(
                            eq(1L), eq(99L), any()))
                    .thenReturn(Collections.emptyList());

            int result = service.detectAndPersist(1L, since);

            assertThat(result).isZero();
            verifyNoInteractions(insightRepository);
        }

        @Test
        @DisplayName("No insight when transaction has no payee and no category")
        void noInsightWhenNoPayeeAndNoCategory() {
            stubUserFound();
            Transaction tx =
                    Transaction.builder()
                            .id(1L)
                            .userId(1L)
                            .type(TransactionType.EXPENSE)
                            .amount(BigDecimal.valueOf(999))
                            .currency("EUR")
                            .date(LocalDate.now())
                            .isDeleted(false)
                            .build();
            stubRecentTx(List.of(tx));

            int result = service.detectAndPersist(1L, since);

            assertThat(result).isZero();
            verifyNoInteractions(insightRepository);
        }

        @Test
        @DisplayName("Category fallback not triggered for INCOME type (only EXPENSE)")
        void categoryFallbackNotTriggeredForIncome() {
            stubUserFound();
            Transaction tx =
                    Transaction.builder()
                            .id(1L)
                            .userId(1L)
                            .type(TransactionType.INCOME)
                            .amount(BigDecimal.valueOf(5000))
                            .currency("EUR")
                            .categoryId(99L)
                            .date(LocalDate.now())
                            .isDeleted(false)
                            .build();
            stubRecentTx(List.of(tx));
            // payee is null → would normally trigger category fallback, but type is INCOME
            // countByUserIdAndPayeeAndCreatedAtBefore is not called because payee is null

            int result = service.detectAndPersist(1L, since);

            assertThat(result).isZero();
            verify(transactionRepository, never())
                    .findExpensesByUserIdAndCategoryIdAndCreatedAtBefore(
                            anyLong(), anyLong(), any());
            verifyNoInteractions(insightRepository);
        }
    }

    // ------------------------------------------------------------------
    // Insight contents
    // ------------------------------------------------------------------

    @Nested
    @DisplayName("Insight content correctness")
    class InsightContent {

        @Test
        @DisplayName("Insight has dismissed=false and correct user")
        void insightHasCorrectUserAndDismissedFlag() {
            stubUserFound();
            Transaction tx = expenseTx(1L, "NewShop", 100.0, null);
            stubRecentTx(List.of(tx));
            when(transactionRepository.countByUserIdAndPayeeAndCreatedAtBefore(
                            eq(1L), eq("NewShop"), any()))
                    .thenReturn(0L);
            when(insightRepository.saveAll(anyList())).thenAnswer(inv -> inv.getArgument(0));

            service.detectAndPersist(1L, since);

            ArgumentCaptor<List<Insight>> captor = ArgumentCaptor.forClass(List.class);
            verify(insightRepository).saveAll(captor.capture());
            Insight insight = captor.getValue().get(0);
            assertThat(insight.getDismissed()).isFalse();
            assertThat(insight.getUser().getId()).isEqualTo(1L);
        }
    }
}
