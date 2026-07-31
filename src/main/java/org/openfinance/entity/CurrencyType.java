package org.openfinance.entity;

/**
 * Classification of a {@link Currency}. Single source of truth for distinguishing government-issued
 * fiat currencies from cryptocurrencies.
 */
public enum CurrencyType {
    /** Government-issued fiat currency (ISO 4217), e.g. USD, EUR. */
    FIAT,
    /** Cryptocurrency, e.g. BTC, ETH. */
    CRYPTO
}
