package org.openfinance.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Stream;
import org.openfinance.dto.TransactionSearchCriteria;
import org.openfinance.dto.TransactionSplitResponse;
import org.openfinance.entity.Transaction;

/** Selects split amounts without changing the parent transaction or its booked exchange value. */
final class TransactionSummaryAmounts {
    private TransactionSummaryAmounts() {}

    static boolean filtersAllocations(TransactionSearchCriteria criteria) {
        return criteria.getCategoryId() != null
                || Boolean.TRUE.equals(criteria.getNoCategory())
                || (criteria.getTag() != null && !criteria.getTag().isBlank())
                || (criteria.getTags() != null && !criteria.getTags().isBlank());
    }

    static boolean matches(
            Transaction transaction,
            List<TransactionSplitResponse> splits,
            TransactionSearchCriteria criteria,
            Set<Long> categoryIds) {
        return splits.isEmpty()
                ? matchesCategory(transaction.getCategoryId(), criteria, categoryIds)
                        && matchesTags(transaction.getTags(), List.of(), criteria)
                : splits.stream()
                        .anyMatch(split -> matchesSplit(transaction, split, criteria, categoryIds));
    }

    static BigDecimal filteredAmount(
            Transaction transaction,
            List<TransactionSplitResponse> splits,
            TransactionSearchCriteria criteria,
            Set<Long> categoryIds,
            BigDecimal convertedTotal,
            String currency) {
        if (splits.isEmpty() || !filtersAllocations(criteria)) return convertedTotal;
        BigDecimal selected =
                splits.stream()
                        .filter(split -> matchesSplit(transaction, split, criteria, categoryIds))
                        .map(TransactionSplitResponse::getAmount)
                        .reduce(BigDecimal.ZERO, BigDecimal::add);
        if (selected.compareTo(transaction.getAmount()) == 0) return convertedTotal;
        if (currency.equalsIgnoreCase(transaction.getCurrency())) return selected;
        // Use the parent's booked/dated conversion, never a current rate for a split.
        return convertedTotal
                .multiply(selected)
                .divide(
                        transaction.getAmount(),
                        Math.max(18, convertedTotal.scale()),
                        RoundingMode.HALF_UP);
    }

    private static boolean matchesSplit(
            Transaction transaction,
            TransactionSplitResponse split,
            TransactionSearchCriteria criteria,
            Set<Long> categoryIds) {
        return matchesCategory(split.getCategoryId(), criteria, categoryIds)
                && matchesTags(transaction.getTags(), split.getTags(), criteria);
    }

    private static boolean matchesCategory(
            Long categoryId, TransactionSearchCriteria criteria, Set<Long> categoryIds) {
        return (criteria.getCategoryId() == null
                        || (categoryId != null && categoryIds.contains(categoryId)))
                && (!Boolean.TRUE.equals(criteria.getNoCategory()) || categoryId == null);
    }

    private static boolean matchesTags(
            String parentTags, List<String> splitTags, TransactionSearchCriteria criteria) {
        List<String> tags =
                Stream.concat(
                                parentTags == null
                                        ? Stream.empty()
                                        : Arrays.stream(parentTags.split(",")),
                                splitTags.stream())
                        .map(String::trim)
                        .filter(tag -> !tag.isEmpty())
                        .toList();
        boolean exact =
                criteria.getTag() == null
                        || criteria.getTag().isBlank()
                        || tags.stream()
                                .anyMatch(tag -> tag.equalsIgnoreCase(criteria.getTag().trim()));
        boolean partial =
                criteria.getTags() == null
                        || criteria.getTags().isBlank()
                        || String.join(",", tags)
                                .toLowerCase(Locale.ROOT)
                                .contains(criteria.getTags().trim().toLowerCase(Locale.ROOT));
        return exact && partial;
    }
}
