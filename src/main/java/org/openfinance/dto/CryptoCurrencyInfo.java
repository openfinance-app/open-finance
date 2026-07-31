package org.openfinance.dto;

import java.math.BigDecimal;

/**
 * A single cryptocurrency as returned by a {@code CryptoListProvider}.
 *
 * @param code upper-cased ticker (e.g. {@code "BTC"})
 * @param name display name (e.g. {@code "Bitcoin"})
 * @param symbol display symbol; providers rarely supply one, so this falls back to {@code code}
 * @param marketCapRank 1-based market-cap rank, or {@code null} if unknown
 * @param priceUsd current price in USD, or {@code null} if unavailable
 */
public record CryptoCurrencyInfo(
        String code, String name, String symbol, Integer marketCapRank, BigDecimal priceUsd) {}
