package org.openfinance.exception;

import java.io.Serial;

/** A complete monetary total cannot be produced without this conversion. */
public final class ExchangeRateUnavailableException extends RuntimeException {
    @Serial private static final long serialVersionUID = 1L;
    private final String fromCurrency;
    private final String toCurrency;

    public ExchangeRateUnavailableException(
            String fromCurrency, String toCurrency, Throwable cause) {
        super("Exchange rate unavailable: " + fromCurrency + " to " + toCurrency, cause);
        this.fromCurrency = fromCurrency;
        this.toCurrency = toCurrency;
    }

    public String getFromCurrency() {
        return fromCurrency;
    }

    public String getToCurrency() {
        return toCurrency;
    }
}
