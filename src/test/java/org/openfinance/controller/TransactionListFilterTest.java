package org.openfinance.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.openfinance.dto.TransactionResponse;
import org.openfinance.entity.TransactionType;
import org.openfinance.entity.User;
import org.openfinance.service.TransactionService;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;

@ExtendWith(MockitoExtension.class)
class TransactionListFilterTest {
    @Mock private TransactionService service;
    @InjectMocks private TransactionController controller;
    private Authentication authentication;
    private List<TransactionResponse> transactions;

    @BeforeEach
    void setUp() {
        authentication =
                new UsernamePasswordAuthenticationToken(User.builder().id(7L).build(), null);
        transactions =
                List.of(
                        transaction(1L, 9, TransactionType.INCOME),
                        transaction(2L, 10, TransactionType.EXPENSE),
                        transaction(3L, 15, TransactionType.INCOME),
                        transaction(4L, 20, TransactionType.EXPENSE),
                        transaction(5L, 21, TransactionType.INCOME));
    }

    static Stream<Arguments> filters() {
        return Stream.of(false, true)
                .flatMap(
                        account ->
                                Stream.of("none", "start", "end", "both")
                                        .flatMap(
                                                range ->
                                                        Stream.of("all", "INCOME", "EXPENSE")
                                                                .map(
                                                                        type ->
                                                                                Arguments.of(
                                                                                        account,
                                                                                        range,
                                                                                        type))));
    }

    @ParameterizedTest(name = "account={0}, dates={1}, type={2}")
    @MethodSource("filters")
    @DisplayName(
            "Account, one-sided inclusive dates, and type filters compose without exposing excluded rows")
    void composesFilters(boolean account, String range, String typeName) {
        LocalDate from =
                range.equals("start") || range.equals("both") ? LocalDate.of(2026, 1, 10) : null;
        LocalDate to =
                range.equals("end") || range.equals("both") ? LocalDate.of(2026, 1, 20) : null;
        TransactionType type = typeName.equals("all") ? null : TransactionType.valueOf(typeName);
        if (account) when(service.getTransactionsByAccount(7L, 20L)).thenReturn(transactions);
        else if (range.equals("both"))
            when(service.getTransactionsByDateRange(7L, from, to))
                    .thenReturn(transactions.subList(1, 4));
        else when(service.getAllTransactions(7L)).thenReturn(transactions);
        List<Long> expected =
                switch (range + ":" + typeName) {
                    case "none:all" -> List.of(1L, 2L, 3L, 4L, 5L);
                    case "none:INCOME" -> List.of(1L, 3L, 5L);
                    case "none:EXPENSE", "start:EXPENSE", "end:EXPENSE", "both:EXPENSE" -> List.of(
                            2L, 4L);
                    case "start:all" -> List.of(2L, 3L, 4L, 5L);
                    case "start:INCOME" -> List.of(3L, 5L);
                    case "end:all" -> List.of(1L, 2L, 3L, 4L);
                    case "end:INCOME" -> List.of(1L, 3L);
                    case "both:all" -> List.of(2L, 3L, 4L);
                    case "both:INCOME" -> List.of(3L);
                    default -> throw new AssertionError("Unhandled fixture");
                };
        assertThat(
                        controller
                                .getTransactions(
                                        account ? 20L : null, from, to, type, authentication)
                                .getBody())
                .extracting(TransactionResponse::getId)
                .containsExactlyElementsOf(expected);
        if (account) verify(service).getTransactionsByAccount(7L, 20L);
    }

    private TransactionResponse transaction(long id, int day, TransactionType type) {
        return TransactionResponse.builder()
                .id(id)
                .date(LocalDate.of(2026, 1, day))
                .type(type)
                .build();
    }
}
