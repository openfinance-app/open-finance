package org.openfinance.service;

import java.util.List;
import java.util.Map;
import org.openfinance.dto.ImportedTransaction;
import org.openfinance.dto.SkroogeImportMetadata;

public interface ImportedInvestmentService {
    void prepare(org.openfinance.entity.Transaction transaction, ImportedTransaction source);

    void importHoldings(
            SkroogeImportMetadata metadata,
            List<ImportedTransaction> reviewed,
            Map<Long, Long> accountIds,
            Long userId);

    void recalculate(Long assetId, Long userId);
}
