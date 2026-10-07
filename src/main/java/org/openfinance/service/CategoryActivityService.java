package org.openfinance.service;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import org.openfinance.entity.Category;

/** Signed category activity, including descendants, in a single reporting currency. */
public interface CategoryActivityService {
    record Activity(BigDecimal income, BigDecimal expenses, long transactionCount) {
        public BigDecimal net() {
            return income.subtract(expenses);
        }
    }

    Map<Long, Activity> summarize(Long userId, List<Category> categories, String currency);
}
