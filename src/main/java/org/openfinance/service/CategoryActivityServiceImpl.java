package org.openfinance.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.openfinance.entity.Category;
import org.openfinance.entity.Transaction;
import org.openfinance.entity.TransactionSplit;
import org.openfinance.entity.TransactionType;
import org.openfinance.repository.TransactionRepository;
import org.openfinance.repository.TransactionSplitRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CategoryActivityServiceImpl implements CategoryActivityService {
    private final TransactionRepository transactions;
    private final TransactionSplitRepository splits;
    private final ExchangeRateService exchangeRates;

    @Override
    public Map<Long, Activity> summarize(Long userId, List<Category> categories, String currency) {
        Map<Long, Category> owned =
                categories.stream()
                        .filter(category -> userId.equals(category.getUserId()))
                        .collect(Collectors.toMap(Category::getId, category -> category));
        List<Transaction> eligible =
                transactions.findByUserId(userId).stream()
                        .filter(transaction -> !Boolean.TRUE.equals(transaction.getIsDeleted()))
                        .filter(transaction -> transaction.getTransferId() == null)
                        .filter(
                                transaction ->
                                        transaction.getType() == TransactionType.INCOME
                                                || transaction.getType() == TransactionType.EXPENSE)
                        .toList();
        Map<Long, List<TransactionSplit>> allocations = allocations(eligible);
        Map<Long, Totals> totals = new HashMap<>();
        for (Transaction transaction : eligible) {
            List<TransactionSplit> lines = allocations.getOrDefault(transaction.getId(), List.of());
            if (!contributes(owned, transaction, lines)) continue;
            BigDecimal converted =
                    transaction.getCurrency().equalsIgnoreCase(currency)
                            ? transaction.getAmount()
                            : exchangeRates.convert(
                                    transaction.getAmount(),
                                    transaction.getCurrency(),
                                    currency,
                                    transaction.getDate());
            if (lines.isEmpty()) {
                add(totals, owned, transaction, transaction.getCategoryId(), converted);
            } else {
                allocate(totals, owned, transaction, lines, converted);
            }
        }
        Map<Long, Activity> result = new HashMap<>();
        totals.forEach(
                (id, value) ->
                        result.put(
                                id, new Activity(value.income, value.expenses, value.ids.size())));
        return result;
    }

    private boolean contributes(
            Map<Long, Category> owned, Transaction transaction, List<TransactionSplit> lines) {
        return lines.isEmpty()
                ? owned.containsKey(transaction.getCategoryId())
                : lines.stream().anyMatch(split -> owned.containsKey(split.getCategoryId()));
    }

    private Map<Long, List<TransactionSplit>> allocations(List<Transaction> eligible) {
        Map<Long, List<TransactionSplit>> result = new HashMap<>();
        List<Long> ids = eligible.stream().map(Transaction::getId).toList();
        for (int offset = 0; offset < ids.size(); offset += 500) {
            for (TransactionSplit split :
                    splits.findByTransactionIdIn(
                            ids.subList(offset, Math.min(ids.size(), offset + 500)))) {
                result.computeIfAbsent(
                                split.getTransactionId(), ignored -> new java.util.ArrayList<>())
                        .add(split);
            }
        }
        return result;
    }

    private void allocate(
            Map<Long, Totals> totals,
            Map<Long, Category> owned,
            Transaction transaction,
            List<TransactionSplit> lines,
            BigDecimal converted) {
        BigDecimal remaining = converted;
        for (int index = 0; index < lines.size(); index++) {
            TransactionSplit split = lines.get(index);
            BigDecimal amount =
                    index == lines.size() - 1
                            ? remaining
                            : converted
                                    .multiply(split.getAmount())
                                    .divide(
                                            transaction.getAmount(),
                                            Math.max(converted.scale(), 18),
                                            RoundingMode.HALF_UP);
            remaining = remaining.subtract(amount);
            add(totals, owned, transaction, split.getCategoryId(), amount);
        }
    }

    private void add(
            Map<Long, Totals> totals,
            Map<Long, Category> owned,
            Transaction transaction,
            Long categoryId,
            BigDecimal amount) {
        Set<Long> visited = new HashSet<>();
        while (categoryId != null && owned.containsKey(categoryId) && visited.add(categoryId)) {
            Totals value = totals.computeIfAbsent(categoryId, ignored -> new Totals());
            value.ids.add(transaction.getId());
            if (transaction.getType() == TransactionType.INCOME)
                value.income = value.income.add(amount);
            else value.expenses = value.expenses.add(amount);
            categoryId = owned.get(categoryId).getParentId();
        }
    }

    private static final class Totals {
        private BigDecimal income = BigDecimal.ZERO;
        private BigDecimal expenses = BigDecimal.ZERO;
        private final Set<Long> ids = new HashSet<>();
    }
}
