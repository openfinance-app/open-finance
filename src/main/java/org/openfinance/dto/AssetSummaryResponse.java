package org.openfinance.dto;

import java.math.BigDecimal;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Lightweight Data Transfer Object for asset list responses.
 *
 * <p>This is a sparse-fieldset / summary projection of {@link AssetResponse} intended for
 * high-volume list endpoints where the full payload (including all calculated fields, currency
 * conversion metadata, and physical-asset fields) is unnecessary.
 *
 * <p>Returned when the caller passes {@code ?summary=true} on {@code GET /api/v1/assets}.
 *
 * <p>Fields included:
 *
 * <ul>
 *   <li>{@code id} – unique asset identifier
 *   <li>{@code name} – decrypted asset name
 *   <li>{@code symbol} – ticker symbol or identifier (e.g., "AAPL"), may be null
 *   <li>{@code type} – asset type as string (STOCK, ETF, CRYPTO, etc.)
 *   <li>{@code quantity} – number of units held
 *   <li>{@code currency} – ISO 4217 currency code
 *   <li>{@code currentPrice} – current market price per unit, may be null
 *   <li>{@code totalValue} – quantity * currentPrice, may be null
 * </ul>
 *
 * <p>Requirement TASK-14.1.3: Sparse fieldsets for optimised API response times.
 *
 * <p>Requirement REQ-3.1: API response optimization - sparse fieldsets.
 *
 * @see AssetResponse
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AssetSummaryResponse {

    /** Unique identifier of the asset. */
    private Long id;

    /** Name of the asset (decrypted). */
    private String name;

    /**
     * Ticker symbol or other identifier (e.g., "AAPL", "BTC-USD").
     *
     * <p>May be {@code null} for non-tradable assets (e.g., real estate, collectibles).
     */
    private String symbol;

    /**
     * Type of asset as a string (STOCK, ETF, MUTUAL_FUND, BOND, CRYPTO, COMMODITY, REAL_ESTATE,
     * OTHER).
     */
    private String type;

    /**
     * Quantity or number of units owned.
     *
     * <p>Uses {@link BigDecimal} to avoid floating-point precision issues.
     */
    private BigDecimal quantity;

    /** Currency code in ISO 4217 format (e.g., "USD", "EUR"). */
    private String currency;

    /**
     * Current market price per unit.
     *
     * <p>Uses {@link BigDecimal} to avoid floating-point precision issues in financial
     * calculations. May be {@code null} when no price data is available.
     */
    private BigDecimal currentPrice;

    /**
     * Total current value of this asset position (quantity * currentPrice).
     *
     * <p>Uses {@link BigDecimal} to avoid floating-point precision issues. May be {@code null} when
     * {@code currentPrice} is not available.
     */
    private BigDecimal totalValue;
}
