package org.openfinance.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Configuration for the dynamic crypto-list feature, bound to {@code application.crypto-list.*}.
 *
 * <p>Both providers are keyless by default; an API key only raises rate limits. The provider chain
 * tries {@link #provider} first, then (when {@link #fallback} is {@code "enabled"}) the other
 * enabled providers on failure.
 */
@Component
@ConfigurationProperties(prefix = "application.crypto-list")
public class CryptoListProperties {

    /** Master switch for the whole feature. */
    private boolean enabled = true;

    /** Number of top-by-market-cap coins to fetch/upsert. */
    private int limit = 250;

    /**
     * {@code "enabled"} to walk the provider chain on failure; {@code "disabled"} to use only the
     * primary.
     */
    private String fallback = "enabled";

    /** Primary provider name (matches {@code CryptoListProvider.name()}). */
    private String provider = "coingecko";

    private CoinGecko coingecko = new CoinGecko();
    private CoinMarketCap coinmarketcap = new CoinMarketCap();

    public boolean isFallbackEnabled() {
        return !"disabled".equalsIgnoreCase(fallback);
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public int getLimit() {
        return limit;
    }

    public void setLimit(int limit) {
        this.limit = limit;
    }

    public String getFallback() {
        return fallback;
    }

    public void setFallback(String fallback) {
        this.fallback = fallback;
    }

    public String getProvider() {
        return provider;
    }

    public void setProvider(String provider) {
        this.provider = provider;
    }

    public CoinGecko getCoingecko() {
        return coingecko;
    }

    public void setCoingecko(CoinGecko coingecko) {
        this.coingecko = coingecko;
    }

    public CoinMarketCap getCoinmarketcap() {
        return coinmarketcap;
    }

    public void setCoinmarketcap(CoinMarketCap coinmarketcap) {
        this.coinmarketcap = coinmarketcap;
    }

    /**
     * CoinGecko settings. Keyless base works for demo keys too; Pro needs a base + header override.
     */
    public static class CoinGecko {
        private boolean enabled = true;
        private String baseUrl = "https://api.coingecko.com/api/v3";
        private String apiKey = "";
        private String apiKeyHeader = "x-cg-demo-api-key";

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public String getBaseUrl() {
            return baseUrl;
        }

        public void setBaseUrl(String baseUrl) {
            this.baseUrl = baseUrl;
        }

        public String getApiKey() {
            return apiKey;
        }

        public void setApiKey(String apiKey) {
            this.apiKey = apiKey;
        }

        public String getApiKeyHeader() {
            return apiKeyHeader;
        }

        public void setApiKeyHeader(String apiKeyHeader) {
            this.apiKeyHeader = apiKeyHeader;
        }
    }

    /** CoinMarketCap settings. Keyless uses the {@code /public-api} path prefix; a key drops it. */
    public static class CoinMarketCap {
        private boolean enabled = true;
        private String baseUrl = "https://pro-api.coinmarketcap.com";
        private String apiKey = "";

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public String getBaseUrl() {
            return baseUrl;
        }

        public void setBaseUrl(String baseUrl) {
            this.baseUrl = baseUrl;
        }

        public String getApiKey() {
            return apiKey;
        }

        public void setApiKey(String apiKey) {
            this.apiKey = apiKey;
        }
    }
}
