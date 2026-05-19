package org.openfinance.dto;

import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.openfinance.entity.AssetCondition;
import org.openfinance.entity.AssetType;
import org.openfinance.validation.ValidCurrency;

/**
 * Data Transfer Object for creating or updating an asset.
 *
 * <p>This DTO is used for both POST (create) and PUT (update) operations. Validation annotations
 * ensure data integrity before processing.
 *
 * <p>Requirement REQ-2.6: Asset creation and management
 *
 * <p>Requirement REQ-2.6.2: Asset tracking with name, type, quantity, and prices
 *
 * @see org.openfinance.entity.Asset
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AssetRequest {

    /**
     * Optional account ID this asset belongs to (e.g., brokerage account).
     *
     * <p>If null, the asset is tracked independently without account association.
     *
     * <p>Requirement REQ-2.6.2: Assets can be linked to accounts
     */
    private Long accountId;

    /**
     * Name of the asset (e.g., "Apple Inc.", "Bitcoin", "S&P 500 ETF").
     *
     * <p>This field will be encrypted before storing in the database.
     *
     * <p>Requirement REQ-2.6.2: Asset must have a descriptive name
     */
    @NotBlank(message = "{asset.name.required}")
    @Size(min = 1, max = 255, message = "{asset.name.between}")
    private String name;

    /**
     * Type of asset (STOCK, ETF, MUTUAL_FUND, BOND, CRYPTO, COMMODITY, REAL_ESTATE, OTHER).
     *
     * <p>Requirement REQ-2.6: Asset type categorization
     */
    @NotNull(message = "{asset.type.required}")
    private AssetType type;

    /**
     * Ticker symbol or identifier (e.g., "AAPL", "BTC-USD", "SPY").
     *
     * <p>Optional field. Used for fetching market data and price updates. Required for assets that
     * support real-time pricing (STOCK, ETF, CRYPTO).
     *
     * <p>Requirement REQ-2.6.4: Symbol for market data integration
     */
    @Size(max = 20, message = "{asset.symbol.max}")
    private String symbol;

    /**
     * Quantity or number of units owned.
     *
     * <p>Examples:
     *
     * <ul>
     *   <li>100 shares of AAPL
     *   <li>0.5 Bitcoin
     *   <li>250 units of mutual fund
     * </ul>
     *
     * <p>Requirement REQ-2.6.2: Track quantity of assets
     */
    @NotNull(message = "{asset.quantity.required}")
    @DecimalMin(value = "0.00000001", message = "{asset.quantity.min}")
    @Digits(integer = 15, fraction = 8, message = "{asset.quantity.digits}")
    private BigDecimal quantity;

    /**
     * Purchase price per unit in the specified currency.
     *
     * <p>Requirement REQ-2.6.2: Track purchase price for gain/loss calculation
     */
    @NotNull(message = "{asset.purchase.price.required}")
    @DecimalMin(value = "0.00", message = "{asset.purchase.price.min}")
    @Digits(integer = 15, fraction = 4, message = "{asset.purchase.price.digits}")
    private BigDecimal purchasePrice;

    /**
     * Current market price per unit in the specified currency.
     *
     * <p>This field is used when creating an asset. For updates, prices can be refreshed via market
     * data integration or manually updated.
     *
     * <p>Requirement REQ-2.6.4: Track current price for portfolio valuation
     */
    @NotNull(message = "{asset.current.price.required}")
    @DecimalMin(value = "0.00", message = "{asset.current.price.min}")
    @Digits(integer = 15, fraction = 4, message = "{asset.current.price.digits}")
    private BigDecimal currentPrice;

    /**
     * Currency code in ISO 4217 format (e.g., "USD", "EUR", "GBP").
     *
     * <p>Requirement REQ-2.8: Multi-currency support for assets
     */
    @NotBlank(message = "{account.currency.required}")
    @ValidCurrency
    private String currency;

    /**
     * Date when the asset was purchased.
     *
     * <p>Used for calculating holding period and tax implications.
     *
     * <p>Requirement REQ-2.6.2: Track purchase date
     */
    @NotNull(message = "{asset.purchase.date.required}")
    @PastOrPresent(message = "{asset.purchase.date.future}")
    private LocalDate purchaseDate;

    /**
     * Optional notes about the asset (e.g., investment thesis, broker info).
     *
     * <p>This field will be encrypted before storing in the database.
     */
    @Size(max = 1000, message = "{asset.notes.max}")
    private String notes;

    // ===== Physical Asset Fields (for VEHICLE, JEWELRY, COLLECTIBLE, ELECTRONICS, FURNITURE) =====

    /**
     * Serial number or identification number for physical assets.
     *
     * <p>Examples: VIN for vehicles, serial number for electronics, certificate number for jewelry.
     *
     * <p>This field will be encrypted before storing in the database.
     *
     * <p>Optional field, only relevant for physical asset types.
     */
    @Size(max = 255, message = "{asset.serial.max}")
    private String serialNumber;

    /**
     * Brand or manufacturer name for physical assets.
     *
     * <p>Examples: Tesla, Apple, Rolex, IKEA.
     *
     * <p>This field will be encrypted before storing in the database.
     *
     * <p>Optional field, only relevant for physical asset types.
     */
    @Size(max = 255, message = "{asset.brand.max}")
    private String brand;

    /**
     * Model name or number for physical assets.
     *
     * <p>Examples: Model 3, iPhone 15 Pro, Submariner, MALM.
     *
     * <p>This field will be encrypted before storing in the database.
     *
     * <p>Optional field, only relevant for physical asset types.
     */
    @Size(max = 255, message = "{asset.model.max}")
    private String model;

    /**
     * Physical condition of the asset.
     *
     * <p>Valid values: NEW, EXCELLENT, GOOD, FAIR, POOR
     *
     * <p>Used for depreciation calculation and value assessment.
     *
     * <p>Optional field, only relevant for physical asset types.
     */
    private AssetCondition condition;

    /**
     * Warranty expiration date for physical assets.
     *
     * <p>Used to track coverage and factor into resale value.
     *
     * <p>Optional field, only relevant for physical asset types.
     */
    @Future(message = "{asset.warranty.past}")
    private LocalDate warrantyExpiration;

    /**
     * Expected useful life in years (for depreciation calculation).
     *
     * <p>Typical values:
     *
     * <ul>
     *   <li>Vehicles: 10-15 years
     *   <li>Electronics: 3-5 years
     *   <li>Furniture: 7-10 years
     *   <li>Jewelry/Collectibles: N/A (may appreciate)
     * </ul>
     *
     * <p>Optional field, only relevant for physical asset types that depreciate.
     */
    @Min(value = 1, message = "{asset.useful.life.min}")
    @Max(value = 50, message = "{asset.useful.life.max}")
    private Integer usefulLifeYears;

    /**
     * Path to photo or image file for the asset.
     *
     * <p>Stored as relative path to the uploads directory.
     *
     * <p>Examples: assets/photos/IMG_1234.jpg, assets/photos/vehicle_vin12345.png
     *
     * <p>Optional field, can be used for any asset type but especially useful for physical assets.
     */
    @Size(max = 500, message = "{asset.photo.max}")
    private String photoPath;
}
