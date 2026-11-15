package org.openfinance.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.openfinance.entity.Insight;
import org.openfinance.entity.Transaction;
import org.openfinance.entity.TransactionType;
import org.openfinance.entity.User;
import org.openfinance.repository.InsightRepository;
import org.openfinance.repository.TransactionRepository;
import org.openfinance.repository.UserRepository;
import org.springframework.context.MessageSource;

@ExtendWith(MockitoExtension.class)
@DisplayName("Spending anomaly detection compares only comparable cash movements and payees")
class UnusualTransactionBoundaryTest {
    @Mock private TransactionRepository transactions;
    @Mock private InsightRepository insights;
    @Mock private InsightWriter writer;
    @Mock private UserRepository users;
    @Mock private MessageSource messages;
    @Mock private ExchangeRateService rates;
    @InjectMocks private UnusualTransactionDetectionService service;
    @Captor private ArgumentCaptor<List<Insight>> saved;
    private static final LocalDateTime SINCE = LocalDateTime.of(2025, 1, 1, 0, 0);

    @BeforeEach
    void setUp() {
        when(users.findById(1L)).thenReturn(Optional.of(User.builder().id(1L).build()));
        lenient()
                .when(writer.appendUnusual(eq(1L), anyList()))
                .thenAnswer(call -> ((List<?>) call.getArgument(1)).size());
        lenient()
                .when(rates.convert(any(), anyString(), anyString(), any()))
                .thenAnswer(call -> call.getArgument(0));
        lenient()
                .when(messages.getMessage(anyString(), any(), any()))
                .thenAnswer(call -> call.getArgument(0));
    }

    private Transaction row(long id, String amount) {
        return Transaction.builder()
                .id(id)
                .userId(1L)
                .type(TransactionType.EXPENSE)
                .amount(new BigDecimal(amount))
                .currency("EUR")
                .payee("Shop")
                .categoryId(9L)
                .date(LocalDate.of(2025, 1, 2))
                .createdAt(SINCE.plusDays(1))
                .isDeleted(false)
                .build();
    }

    @ParameterizedTest
    @ValueSource(strings = {"transfer-type", "paired-transfer", "noncash", "deleted"})
    void excludedRecentMovementsNeverCreateNewPayeeOrAmountAlerts(String kind) {
        Transaction tx = row(1, "1000");
        switch (kind) {
            case "transfer-type" -> tx.setType(TransactionType.TRANSFER);
            case "paired-transfer" -> tx.setTransferId("pair");
            case "noncash" -> {
                tx.setInvestmentQuantity(BigDecimal.ONE);
                tx.setInvestmentCash(false);
            }
            case "deleted" -> tx.setIsDeleted(true);
        }
        when(transactions.findByUserIdAndCreatedAtAfter(1L, SINCE)).thenReturn(List.of(tx));
        assertThat(service.detectAndPersist(1L, SINCE)).isZero();
        verify(writer, never()).appendUnusual(any(), anyList());
    }

    @ParameterizedTest
    @ValueSource(strings = {"opposite-direction", "paired-transfer", "noncash"})
    void incomparableHistoryDoesNotDiluteARealSpendingSpike(String kind) {
        Transaction current = row(1, "40"), old = row(2, "10"), excluded = row(3, "1000");
        switch (kind) {
            case "opposite-direction" -> excluded.setType(TransactionType.INCOME);
            case "paired-transfer" -> excluded.setTransferId("pair");
            case "noncash" -> {
                excluded.setInvestmentQuantity(BigDecimal.ONE);
                excluded.setInvestmentCash(false);
            }
        }
        when(transactions.findByUserIdAndCreatedAtAfter(1L, SINCE)).thenReturn(List.of(current));
        when(transactions.findByUserIdAndCreatedAtBefore(1L, SINCE))
                .thenReturn(List.of(old, excluded));
        assertThat(service.detectAndPersist(1L, SINCE)).isEqualTo(1);
        verify(writer).appendUnusual(eq(1L), saved.capture());
        assertThat(saved.getValue())
                .extracting(Insight::getSourceKey)
                .containsExactly("unusual:amount:1");
        verify(rates, never()).convert(eq(new BigDecimal("1000")), any(), any(), any());
    }

    @ParameterizedTest
    @ValueSource(strings = {"0", "29.99", "30", "30.000000000000000001"})
    void sparseHistoryUsesAStrictExactThreeTimesThreshold(String amount) {
        Transaction current = row(1, amount);
        when(transactions.findByUserIdAndCreatedAtAfter(1L, SINCE)).thenReturn(List.of(current));
        when(transactions.findByUserIdAndCreatedAtBefore(1L, SINCE))
                .thenReturn(List.of(row(2, "10")));
        assertThat(service.detectAndPersist(1L, SINCE))
                .isEqualTo(new BigDecimal(amount).compareTo(new BigDecimal("30")) > 0 ? 1 : 0);
    }

    @ParameterizedTest
    @ValueSource(
            strings = {
                "same-id-renamed",
                "one-id-missing",
                "distinct-ids-same-name",
                "missing-old-name"
            })
    void payeeIdentityUsesIdsWhenBothAreKnownAndNamesOnlyAsAFallback(String kind) {
        Transaction current = row(1, "10"), old = row(2, "10");
        current.setPayeeId(7L);
        switch (kind) {
            case "same-id-renamed" -> {
                old.setPayeeId(7L);
                old.setPayee("Old Shop");
            }
            case "one-id-missing" -> old.setPayee("  SHOP ");
            case "distinct-ids-same-name" -> old.setPayeeId(8L);
            case "missing-old-name" -> old.setPayee(null);
        }
        when(transactions.findByUserIdAndCreatedAtAfter(1L, SINCE)).thenReturn(List.of(current));
        when(transactions.findByUserIdAndCreatedAtBefore(1L, SINCE)).thenReturn(List.of(old));
        boolean newPayee = kind.equals("distinct-ids-same-name") || kind.equals("missing-old-name");
        assertThat(service.detectAndPersist(1L, SINCE)).isEqualTo(newPayee ? 1 : 0);
        if (newPayee) {
            verify(writer).appendUnusual(eq(1L), saved.capture());
            assertThat(saved.getValue())
                    .extracting(Insight::getSourceKey)
                    .containsExactly("unusual:new-payee:1");
        }
    }

    @Test
    void zeroHistoricalPaymentsDoNotProduceDivisionByZeroOrAnInventedPercentage() {
        when(transactions.findByUserIdAndCreatedAtAfter(1L, SINCE))
                .thenReturn(List.of(row(1, "10")));
        when(transactions.findByUserIdAndCreatedAtBefore(1L, SINCE))
                .thenReturn(List.of(row(2, "0")));
        assertThat(service.detectAndPersist(1L, SINCE)).isZero();
        verify(writer, never()).appendUnusual(any(), anyList());
    }

    @Test
    void payeeHistoryContainingOnlyNonCashActivityHasNoComparableAmountBaseline() {
        Transaction old = row(2, "10");
        old.setTransferId("transfer");
        when(transactions.findByUserIdAndCreatedAtAfter(1L, SINCE))
                .thenReturn(List.of(row(1, "1000")));
        when(transactions.findByUserIdAndCreatedAtBefore(1L, SINCE)).thenReturn(List.of(old));
        assertThat(service.detectAndPersist(1L, SINCE)).isZero();
    }

    @ParameterizedTest
    @ValueSource(strings = {"10", "30", "30.01"})
    void categoryOnlyAnomaliesAlsoRespectTheStrictThreshold(String amount) {
        Transaction current = row(1, amount);
        current.setPayee(null);
        when(transactions.findByUserIdAndCreatedAtAfter(1L, SINCE)).thenReturn(List.of(current));
        when(transactions.findByUserIdAndCreatedAtBefore(1L, SINCE))
                .thenReturn(List.of(row(2, "10")));
        assertThat(service.detectAndPersist(1L, SINCE)).isEqualTo(amount.equals("30.01") ? 1 : 0);
    }
}
