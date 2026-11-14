package org.openfinance.service;

import java.util.List;
import org.openfinance.dto.TransactionSplitResponse;
import org.openfinance.entity.Transaction;
import org.openfinance.util.RegexSearchUtil;

/** Searches decrypted parent and split text without returning duplicate parent transactions. */
final class TransactionSearchText {
    private TransactionSearchText() {}

    static boolean matches(
            Transaction transaction,
            List<TransactionSplitResponse> splits,
            String query,
            boolean regex) {
        return RegexSearchUtil.matches(transaction.getDescription(), query, regex)
                || RegexSearchUtil.matches(transaction.getNotes(), query, regex)
                || RegexSearchUtil.matches(transaction.getPayee(), query, regex)
                || RegexSearchUtil.matches(transaction.getTags(), query, regex)
                || splits.stream()
                        .anyMatch(
                                split ->
                                        RegexSearchUtil.matches(
                                                        split.getDescription(), query, regex)
                                                || split.getTags().stream()
                                                        .anyMatch(
                                                                tag ->
                                                                        RegexSearchUtil.matches(
                                                                                tag, query,
                                                                                regex)));
    }

    static boolean matchesTags(
            Transaction transaction, List<TransactionSplitResponse> splits, List<String> tags) {
        if (tags == null || tags.isEmpty()) return true;
        return tags.stream()
                .anyMatch(
                        tag ->
                                RegexSearchUtil.matches(transaction.getTags(), tag, false)
                                        || splits.stream()
                                                .flatMap(split -> split.getTags().stream())
                                                .anyMatch(
                                                        splitTag ->
                                                                RegexSearchUtil.matches(
                                                                        splitTag, tag, false)));
    }
}
