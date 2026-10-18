package org.openfinance.service;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import org.openfinance.entity.Category;

/** Signed category activity, including descendants, in a single reporting currency. */
public interface CategoryActivityService {
    record Activity(
            BigDecimal income,
            BigDecimal expenses,
            long transactionCount,
            BigDecimal directIncome,
            BigDecimal directExpenses,
            long directTransactionCount) {
        public Activity(BigDecimal income, BigDecimal expenses, long transactionCount) {
            this(income, expenses, transactionCount, income, expenses, transactionCount);
        }

        public BigDecimal net() {
            return income.subtract(expenses);
        }

        public BigDecimal directNet() {
            return directIncome.subtract(directExpenses);
        }
    }

    Map<Long, Activity> summarize(Long userId, List<Category> categories, String currency);
}
