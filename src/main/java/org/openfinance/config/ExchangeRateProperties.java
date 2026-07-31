package org.openfinance.config;

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

    public String getBaseCurrency() {
        return baseCurrency;
    }

    public void setBaseCurrency(String baseCurrency) {
        this.baseCurrency = baseCurrency;
    }
}
