package org.openfinance.service;

import org.openfinance.dto.AssetSaleRequest;

public interface AssetSaleService {
    Long sell(Long userId, Long assetId, AssetSaleRequest request);
}
