package org.openfinance.service;

import java.math.BigDecimal;
import java.util.Comparator;
import java.util.List;
import org.openfinance.dto.AmountCurrencyMode;
import org.openfinance.dto.TransactionResponse;
import org.openfinance.util.AmountComparison;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

/** Amount ordering must run on decrypted, converted values before pagination. */
final class TransactionSearchOrdering {
    private TransactionSearchOrdering() {}

    static Page<TransactionResponse> page(
            List<TransactionResponse> responses, Pageable pageable, AmountCurrencyMode mode) {
        Comparator<TransactionResponse> comparator = (a, b) -> 0;
        for (Sort.Order order : pageable.getSort()) {
            Comparator<TransactionResponse> next;
            if (order.getProperty().equals("amount")) {
                next =
                        AmountComparison.comparator(
                                response -> amount(response, mode), order.isAscending());
            } else {
                next =
                        switch (order.getProperty()) {
                            case "date" -> Comparator.comparing(
                                    TransactionResponse::getDate,
                                    Comparator.nullsLast(Comparator.naturalOrder()));
                            case "createdAt" -> Comparator.comparing(
                                    TransactionResponse::getCreatedAt,
                                    Comparator.nullsLast(Comparator.naturalOrder()));
                            case "type" -> Comparator.comparing(
                                    TransactionResponse::getType,
                                    Comparator.nullsLast(Comparator.naturalOrder()));
                            default -> Comparator.comparing(TransactionResponse::getId);
                        };
                if (order.isDescending()) next = next.reversed();
            }
            comparator = comparator.thenComparing(next);
        }
        responses.sort(comparator.thenComparing(TransactionResponse::getId));
        int start = (int) Math.min(pageable.getOffset(), responses.size());
        int end = Math.min(start + pageable.getPageSize(), responses.size());
        return new PageImpl<>(responses.subList(start, end), pageable, responses.size());
    }

    private static BigDecimal amount(TransactionResponse response, AmountCurrencyMode mode) {
        BigDecimal amount =
                AmountComparison.select(
                        response.getAmount(),
                        response.getCurrency(),
                        response.getAmountInBaseCurrency(),
                        response.getBaseCurrency(),
                        response.getIsConverted(),
                        mode);
        return amount == null ? null : amount.abs();
    }
}
