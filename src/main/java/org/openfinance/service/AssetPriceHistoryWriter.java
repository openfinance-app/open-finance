package org.openfinance.service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.openfinance.dto.HistoricalPrice;
import org.openfinance.entity.Asset;
import org.openfinance.entity.AssetPriceHistory;
import org.openfinance.repository.AssetPriceHistoryRepository;
import org.openfinance.repository.NetWorthRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Quote writes run separately from network requests and historical valuation reads. */
@Service
@RequiredArgsConstructor
public class AssetPriceHistoryWriter {
    private final AssetPriceHistoryRepository prices;
    private final NetWorthRepository snapshots;

    @Transactional
    public void record(
            Long userId,
            Asset asset,
            LocalDate date,
            BigDecimal price,
            String currency,
            String source) {
        recordAll(
                userId,
                asset,
                List.of(
                        HistoricalPrice.builder()
                                .date(date)
                                .close(price)
                                .currency(currency)
                                .build()),
                source);
    }

    @Transactional
    public void recordAll(Long userId, Asset asset, List<HistoricalPrice> quotes, String source) {
        if (!userId.equals(asset.getUserId()))
            throw new IllegalArgumentException("Asset owner mismatch");
        if (asset.isPhysical()
                || asset.getType() == org.openfinance.entity.AssetType.REAL_ESTATE
                || asset.getId() == null
                || quotes.isEmpty()) return;
        Map<LocalDate, AssetPriceHistory> existing = new HashMap<>();
        prices.findByAssetIdAndUserIdOrderByPriceDateAsc(asset.getId(), userId)
                .forEach(row -> existing.put(row.getPriceDate(), row));
        List<AssetPriceHistory> changed = new ArrayList<>();
        LocalDate earliest = null;
        for (HistoricalPrice quote : quotes) {
            if (!valid(quote)) continue;
            AssetPriceHistory row = existing.get(quote.getDate());
            if (row != null
                    && (priority(row.getSource()) > priority(source)
                            || (row.getPrice().compareTo(quote.getClose()) == 0
                                    && row.getCurrency().equals(quote.getCurrency())
                                    && row.getSource().equals(source)))) continue;
            if (row == null) {
                row =
                        AssetPriceHistory.builder()
                                .assetId(asset.getId())
                                .userId(asset.getUserId())
                                .priceDate(quote.getDate())
                                .build();
                existing.put(quote.getDate(), row);
            }
            row.setPrice(quote.getClose());
            row.setCurrency(quote.getCurrency());
            row.setSource(source);
            changed.add(row);
            if (earliest == null || quote.getDate().isBefore(earliest)) earliest = quote.getDate();
        }
        if (!changed.isEmpty()) {
            prices.saveAll(changed);
            snapshots.deleteReconstructedFrom(asset.getUserId(), earliest);
        }
    }

    private static boolean valid(HistoricalPrice quote) {
        return quote.getDate() != null
                && !quote.getDate().isAfter(LocalDate.now())
                && quote.getClose() != null
                && quote.getClose().signum() >= 0
                && quote.getCurrency() != null
                && !quote.getCurrency().isBlank();
    }

    static int priority(String source) {
        return switch (source) {
            case "MANUAL" -> 4;
            case "SKROOGE" -> 3;
            case "MARKET" -> 2;
            default -> 1;
        };
    }
}
