package org.openfinance.service;

import java.util.List;
import org.openfinance.entity.Insight;

public interface InsightWriter {
    /** Publishes one batch of detector alerts, preserving existing alerts and their dismissals. */
    int appendUnusual(Long userId, List<Insight> detected);

    default List<Insight> replaceGenerated(Long userId, List<Insight> generated) {
        return replaceGenerated(userId, generated, java.util.Set.of());
    }

    List<Insight> replaceGenerated(
            Long userId,
            List<Insight> generated,
            java.util.Set<org.openfinance.entity.InsightType> unavailableTypes);
}
