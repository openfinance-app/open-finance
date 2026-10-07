package org.openfinance.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.openfinance.entity.Category;
import org.openfinance.entity.Transaction;
import org.openfinance.entity.TransactionSplit;
import org.openfinance.entity.TransactionType;
import org.openfinance.repository.TransactionRepository;
import org.openfinance.repository.TransactionSplitRepository;

@ExtendWith(MockitoExtension.class)
class CategoryActivityServiceTest {
    @Mock private TransactionRepository transactions;
    @Mock private TransactionSplitRepository splits;
    @Mock private ExchangeRateService exchangeRates;
    @InjectMocks private CategoryActivityServiceImpl service;

    @Test
    void uncategorizedForeignTransactionsDoNotRequireAnUnrelatedExchangeRate() {
        Transaction transaction = transaction(1, TransactionType.EXPENSE, "100", "USD", null);
        when(transactions.findByUserId(7L)).thenReturn(List.of(transaction));

        assertThat(service.summarize(7L, List.of(category(1L, null)), "EUR")).isEmpty();
        verifyNoInteractions(exchangeRates);
    }

    @Test
    void reconcilesRefundsSplitAllocationsAndDatedFxWithoutCountingParentsTwice() {
        List<Category> categories = List.of(category(1L, null), category(2L, 1L), category(3L, 1L));
        Transaction purchase = transaction(1, TransactionType.EXPENSE, "100", "EUR", 2L);
        Transaction refund = transaction(2, TransactionType.INCOME, "30", "EUR", 2L);
        Transaction foreignSplit = transaction(3, TransactionType.EXPENSE, "50", "USD", 1L);
        Transaction splitRefund = transaction(4, TransactionType.INCOME, "20", "EUR", 1L);
        Transaction transfer = transaction(5, TransactionType.EXPENSE, "900", "EUR", 2L);
        transfer.setTransferId("internal");
        Transaction deleted = transaction(6, TransactionType.INCOME, "900", "EUR", 2L);
        deleted.setIsDeleted(true);
        when(transactions.findByUserId(7L))
                .thenReturn(
                        List.of(purchase, refund, foreignSplit, splitRefund, transfer, deleted));
        when(splits.findByTransactionIdIn(List.of(1L, 2L, 3L, 4L)))
                .thenReturn(
                        List.of(
                                split(3, 2, "20"),
                                split(3, 3, "30"),
                                split(4, 2, "10"),
                                split(4, 3, "10")));
        when(exchangeRates.convert(new BigDecimal("50"), "USD", "EUR", LocalDate.of(2026, 10, 1)))
                .thenReturn(new BigDecimal("40"));

        java.util.Map<Long, CategoryActivityService.Activity> totals =
                service.summarize(7L, categories, "EUR");

        assertThat(totals.get(1L).income()).isEqualByComparingTo("50");
        assertThat(totals.get(1L).expenses()).isEqualByComparingTo("140");
        assertThat(totals.get(1L).net()).isEqualByComparingTo("-90");
        assertThat(totals.get(1L).transactionCount()).isEqualTo(4);
        assertThat(totals.get(2L).net()).isEqualByComparingTo("-76");
        assertThat(totals.get(3L).net()).isEqualByComparingTo("-14");
        assertThat(totals.get(2L).transactionCount()).isEqualTo(4);
        verify(exchangeRates)
                .convert(new BigDecimal("50"), "USD", "EUR", LocalDate.of(2026, 10, 1));
    }

    private Category category(Long id, Long parent) {
        return Category.builder().id(id).userId(7L).name("Category " + id).parentId(parent).build();
    }

    private Transaction transaction(
            long id, TransactionType type, String amount, String currency, Long category) {
        return Transaction.builder()
                .id(id)
                .userId(7L)
                .type(type)
                .amount(new BigDecimal(amount))
                .currency(currency)
                .date(LocalDate.of(2026, 10, 1))
                .categoryId(category)
                .isDeleted(false)
                .build();
    }

    private TransactionSplit split(long transaction, long category, String amount) {
        return TransactionSplit.builder()
                .transactionId(transaction)
                .categoryId(category)
                .amount(new BigDecimal(amount))
                .build();
    }
}
