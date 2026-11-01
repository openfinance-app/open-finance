package org.openfinance.service;

import java.math.BigDecimal;
import java.util.List;
import org.openfinance.dto.AssetFinancingLink;

public interface AssetFinancingService {
    List<AssetFinancingLink> forLiability(Long userId, Long liabilityId);

    List<AssetFinancingLink> forAsset(Long userId, Long assetId);

    List<AssetFinancingLink> replace(Long userId, Long liabilityId, List<AssetFinancingLink> links);

    BigDecimal propertyDebt(Long userId, Long assetId, Long mortgageId, String currency);

    BigDecimal outstandingBalance(Long userId, Long liabilityId);

    boolean hasFinancingAllocations(Long userId, Long liabilityId);

    void synchronizePrimary(Long userId, Long assetId, Long previousLoanId, Long loanId);

    void ensureDirectFinancing(Long userId, Long liabilityId, Long assetId);
}
