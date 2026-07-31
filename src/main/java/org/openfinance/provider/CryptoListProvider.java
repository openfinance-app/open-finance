package org.openfinance.provider;

import java.util.List;
import org.openfinance.dto.CryptoCurrencyInfo;

/**
 * Provider that lists the top cryptocurrencies by market cap. Implementations are keyless by
 * default (an optional API key only raises rate limits). Selection/fallback across implementations
 * is orchestrated by {@code CryptoListService}.
 */
public interface CryptoListProvider {

    /**
     * Stable identifier used to pin/order providers via configuration, e.g. {@code "coingecko"}.
     */
    String name();

    /** Whether this provider is enabled in configuration. Disabled providers are skipped. */
    boolean isEnabled();

    /**
     * Fetches up to {@code limit} cryptocurrencies ordered by market-cap rank (ascending).
     *
     * @param limit maximum number of coins to return
     * @return the coins; never {@code null} (may be empty)
     * @throws org.openfinance.exception.MarketDataException if the upstream request fails
     */
    List<CryptoCurrencyInfo> fetchTopCryptocurrencies(int limit);
}
