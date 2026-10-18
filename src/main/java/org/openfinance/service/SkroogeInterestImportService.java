package org.openfinance.service;

import java.util.Map;
import org.openfinance.dto.SkroogeImportMetadata;

public interface SkroogeInterestImportService {
    void importInterest(SkroogeImportMetadata metadata, Map<Long, Long> accountIds, Long userId);
}
