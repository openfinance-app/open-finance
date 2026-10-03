package org.openfinance.service;

import java.util.List;
import org.openfinance.entity.Insight;

public interface InsightWriter {
    default List<Insight> replaceGenerated(Long userId, List<Insight> generated) {
        return replaceGenerated(userId, generated, java.util.Set.of());
    }

    List<Insight> replaceGenerated(
            Long userId,
            List<Insight> generated,
            java.util.Set<org.openfinance.entity.InsightType> unavailableTypes);
}
