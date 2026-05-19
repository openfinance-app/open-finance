package org.openfinance.exception;

/**
 * Exception thrown when market data API operations fail.
 *
 * <p>This exception is thrown in scenarios such as:
 *
 * <ul>
 *   <li>Invalid or unknown trading symbols
 *   <li>API rate limiting or quota exceeded
 *   <li>Network connectivity issues
 *   <li>API service unavailability
 *   <li>Malformed API responses
 * </ul>
 *
 * @author Open Finance Team
 * @version 1.0
 * @since 2024-01-20
 */
public class MarketDataException extends RuntimeException {

    /** The trading symbol that caused the exception, if applicable. */
    private final String symbol;

    /** HTTP status code from the API response, if applicable. */
    private final Integer statusCode;

    /**
     * Constructs a new MarketDataException with the specified detail message.
     *
     * @param message the detail message
     */
    public MarketDataException(String message) {
        super(message);
        this.symbol = null;
        this.statusCode = null;
    }

    /**
     * Constructs a new MarketDataException with the specified detail message and cause.
     *
     * @param message the detail message
     * @param cause the cause of the exception
     */
    public MarketDataException(String message, Throwable cause) {
        super(message, cause);
        this.symbol = null;
        this.statusCode = null;
    }

    /**
     * Constructs a new MarketDataException with symbol information.
     *
     * @param message the detail message
     * @param symbol the trading symbol that caused the exception
     */
    public MarketDataException(String message, String symbol) {
        super(message);
        this.symbol = symbol;
        this.statusCode = null;
    }

    /**
     * Constructs a new MarketDataException with symbol and HTTP status code.
     *
     * @param message the detail message
     * @param symbol the trading symbol that caused the exception
     * @param statusCode the HTTP status code from the API response
     */
    public MarketDataException(String message, String symbol, Integer statusCode) {
        super(message);
        this.symbol = symbol;
        this.statusCode = statusCode;
    }

    /**
     * Constructs a new MarketDataException with symbol, status code, and cause.
     *
     * @param message the detail message
     * @param symbol the trading symbol that caused the exception
     * @param statusCode the HTTP status code from the API response
     * @param cause the cause of the exception
     */
    public MarketDataException(String message, String symbol, Integer statusCode, Throwable cause) {
        super(message, cause);
        this.symbol = symbol;
        this.statusCode = statusCode;
    }

    /**
     * Gets the trading symbol that caused the exception.
     *
     * @return the symbol, or null if not applicable
     */
    public String getSymbol() {
        return symbol;
    }

    /**
     * Gets the HTTP status code from the API response.
     *
     * @return the status code, or null if not applicable
     */
    public Integer getStatusCode() {
        return statusCode;
    }
}
