package org.openfinance.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mapstruct.factory.Mappers;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.openfinance.dto.TransactionResponse;
import org.openfinance.entity.Transaction;
import org.openfinance.entity.TransactionType;
import org.openfinance.mapper.TransactionMapper;
import org.openfinance.repository.AccountRepository;
import org.openfinance.repository.TransactionRepository;

@ExtendWith(MockitoExtension.class)
class TransactionTagBoundaryTest {
    @Mock private TransactionRepository repository;
    @Mock private AccountRepository accounts;
    @Mock private TransactionSplitService splits;
    @Mock private CurrencyConversionHelper conversion;
    @Spy private TransactionMapper mapper = Mappers.getMapper(TransactionMapper.class);
    @InjectMocks private TransactionService service;

    @ParameterizedTest
    @ValueSource(
            strings = {
                "all-owner",
                "popular-owner",
                "popular-zero",
                "popular-negative",
                "tag-owner",
                "tag-null",
                "tag-empty",
                "tag-blank",
                "list-owner",
                "split-owner"
            })
    @DisplayName("Missing ownership and invalid tag requests fail before querying private data")
    void validatesTagQueryScope(String invalid) {
        assertThatThrownBy(
                        () -> {
                            switch (invalid) {
                                case "all-owner" -> service.getAllTagsForUser(null);
                                case "popular-owner" -> service.getPopularTags(null, 1);
                                case "popular-zero" -> service.getPopularTags(7L, 0);
                                case "popular-negative" -> service.getPopularTags(7L, -1);
                                case "tag-owner" -> service.getTransactionsByTag(null, "tag");
                                case "tag-null" -> service.getTransactionsByTag(7L, null);
                                case "tag-empty" -> service.getTransactionsByTag(7L, "");
                                case "tag-blank" -> service.getTransactionsByTag(7L, " ");
                                case "list-owner" -> service.getAllTransactions(null);
                                case "split-owner" -> service.getSplitsForTransaction(1L, null);
                                default -> throw new AssertionError("Unknown fixture");
                            }
                        })
                .isInstanceOf(IllegalArgumentException.class);
        verifyNoInteractions(repository, splits, accounts);
    }

    @Test
    @DisplayName(
            "Popular tags count each transaction once across parent and split tags and ignore deleted rows")
    void countsDistinctTagsPerLiveTransaction() {
        when(repository.findByUserId(7L))
                .thenReturn(
                        List.of(
                                transaction(1L, "trip, trip, ,food", false),
                                transaction(2L, null, null),
                                transaction(3L, "trip, secret", true)));
        when(splits.getTagsForTransactions(List.of(1L, 2L)))
                .thenReturn(Map.of(1L, List.of("trip", "food"), 2L, List.of("trip")));
        assertThat(service.getAllTagsForUser(7L))
                .containsExactly(
                        new TransactionService.TagInfo("trip", 2L),
                        new TransactionService.TagInfo("food", 1L));
        assertThat(service.getPopularTags(7L, 1)).containsExactly("trip");
    }

    @ParameterizedTest
    @ValueSource(strings = {"en", "tr-TR"})
    @DisplayName(
            "Case-insensitive tag lookup is locale independent, matches complete tags and includes split-only tags")
    void matchesCompleteTagsRegardlessOfServerLocale(String locale) {
        Locale original = Locale.getDefault();
        Locale.setDefault(Locale.forLanguageTag(locale));
        try {
            when(repository.findByUserId(7L))
                    .thenReturn(
                            List.of(
                                    transaction(1L, "INCOME", false),
                                    transaction(2L, null, false),
                                    transaction(3L, "income-extra", false),
                                    transaction(4L, "income", true)));
            when(splits.getTagsForTransactions(List.of(1L, 2L, 3L)))
                    .thenReturn(Map.of(2L, List.of(" income ")));
            when(conversion.convertTransaction(any(), any(), any()))
                    .thenReturn(
                            new CurrencyConversionHelper.ConversionResult(
                                    "EUR",
                                    BigDecimal.ONE,
                                    BigDecimal.ONE,
                                    false,
                                    null,
                                    null,
                                    null));
            assertThat(service.getTransactionsByTag(7L, " income "))
                    .extracting(TransactionResponse::getId)
                    .containsExactly(1L, 2L);
            verify(splits).getTagsForTransactions(List.of(1L, 2L, 3L));
        } finally {
            Locale.setDefault(original);
        }
    }

    private Transaction transaction(long id, String tags, Boolean deleted) {
        return Transaction.builder()
                .id(id)
                .userId(7L)
                .accountId(10L)
                .currency("EUR")
                .amount(BigDecimal.ONE)
                .type(TransactionType.INCOME)
                .tags(tags)
                .isDeleted(deleted)
                .build();
    }
}
