package org.openfinance.service;

import java.util.List;
import org.openfinance.entity.Insight;

public interface InsightWriter {
    List<Insight> replaceGenerated(Long userId, List<Insight> generated);
}
