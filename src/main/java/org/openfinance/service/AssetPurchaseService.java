package org.openfinance.service;

import org.openfinance.dto.AssetPurchaseRequest;

public interface AssetPurchaseService {
    Long purchase(Long userId, AssetPurchaseRequest request);

    Long buyMore(Long userId, Long assetId, org.openfinance.dto.AssetBuyRequest request);
}
