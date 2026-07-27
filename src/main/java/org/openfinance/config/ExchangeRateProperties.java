package org.openfinance.config;

import java.util.ArrayList;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Configuration properties for multi-currency conversion.
 *
 * <p>Bound to {@code application.exchange-rates.*} in {@code application.yml}. Follows the same
 * pattern as {@link LogoFetchProperties}.
 *
 * <p>The {@code base-currency} value is the application-wide default currency, used as the single
 * source of truth whenever a user has not chosen a preferred base currency. See {@code
 * org.openfinance.service.DefaultCurrencyProvider}.
 */
@Component
@ConfigurationProperties(prefix = "application.exchange-rates")
public class ExchangeRateProperties {

    /**
     * Application-wide default base currency (ISO 4217, e.g. {@code EUR}). Used whenever a user has
     * no preferred base currency configured. Defaults to {@code EUR}.
     */
    private String baseCurrency = "EUR";

    /**
     * Currency codes treated as cryptocurrencies. There is no ISO 4217 registry for crypto (unlike
     * fiat, which {@link java.util.Currency} exposes), so this curated list is externalized here:
     * adding a coin is a configuration change, not a code change. Defaults to the previously
     * hardcoded list so behavior is unchanged until overridden.
     */
    private List<String> cryptoCodes =
            new ArrayList<>(
                    List.of(
                            "BTC", "ETH", "BNB", "XRP", "ADA", "SOL", "DOT", "DOGE", "USDT", "USDC",
                            "MATIC", "AVAX", "LINK", "UNI"));

    public String getBaseCurrency() {
        return baseCurrency;
    }

    public void setBaseCurrency(String baseCurrency) {
        this.baseCurrency = baseCurrency;
    }

    public List<String> getCryptoCodes() {
        return cryptoCodes;
    }

    public void setCryptoCodes(List<String> cryptoCodes) {
        this.cryptoCodes = cryptoCodes;
    }
}
