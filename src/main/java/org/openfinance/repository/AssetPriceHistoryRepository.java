package org.openfinance.repository;

import java.util.List;
import org.openfinance.entity.AssetPriceHistory;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AssetPriceHistoryRepository extends JpaRepository<AssetPriceHistory, Long> {
    List<AssetPriceHistory> findByAssetIdAndUserIdOrderByPriceDateAsc(Long assetId, Long userId);
}
