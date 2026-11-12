package org.openfinance.service;

import java.util.Locale;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.openfinance.config.LogoFetchProperties;
import org.openfinance.dto.SymbolSearchResult;
import org.openfinance.entity.AssetType;
import org.openfinance.provider.MarketDataProvider;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class AssetLogoServiceImpl implements AssetLogoService {

    private final MarketDataProvider marketDataProvider;
    private final LogoFetchService logoFetchService;
    private final LogoFetchProperties properties;

    @Override
    @Cacheable(value = "assetLogos", key = "{#type, #symbol, #name}", sync = true)
    public String resolveLogo(AssetType type, String symbol, String name) {
        String quoteType = quoteType(type);
        if (!properties.isEnabled() || quoteType == null) {
            return null;
        }
        // Unlisted companies can still use the existing name/domain resolver.
        if (symbol == null || symbol.isBlank()) {
            return type == AssetType.STOCK && name != null && !name.isBlank()
                    ? logoFetchService.fetchLogo(name).orElse(null)
                    : null;
        }
        String lookupSymbol = symbol.trim().toUpperCase(Locale.ROOT);
        if (type == AssetType.CRYPTO) {
            if (!lookupSymbol.matches("[A-Z0-9]+(?:-[A-Z0-9]+)?")) return null;
            // The coin's identity is independent of quote currency. Some pairs are not indexed.
            lookupSymbol = lookupSymbol.split("-", 2)[0] + "-USD";
        }
        try {
            for (SymbolSearchResult result : marketDataProvider.searchSymbol(lookupSymbol)) {
                // A fuzzy search hit or the same ticker on another exchange is not this asset.
                if (lookupSymbol.equalsIgnoreCase(result.getSymbol())
                        && quoteType.equalsIgnoreCase(result.getType())) {
                    return resolveMatchedLogo(result, type, name);
                }
            }
        } catch (Exception e) {
            log.debug("Asset logo lookup unavailable: {}", e.getMessage());
        }
        return null;
    }

    private String resolveMatchedLogo(SymbolSearchResult result, AssetType type, String name) {
        String logo = logoFetchService.fetchMarketLogo(result.getLogoUrl()).orElse(null);
        if (logo == null && type == AssetType.STOCK) {
            String brandName = name != null && !name.isBlank() ? name : result.getName();
            return logoFetchService.fetchLogo(brandName).orElse(null);
        }
        return logo;
    }

    private String quoteType(AssetType type) {
        if (type == null) return null;
        return switch (type) {
            case STOCK -> "EQUITY";
            case ETF -> "ETF";
            case CRYPTO -> "CRYPTOCURRENCY";
            case MUTUAL_FUND -> "MUTUALFUND";
            default -> null;
        };
    }
}
